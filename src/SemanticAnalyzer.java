import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Name resolution and type checking for the int/int[] subset of Funny. */
public final class SemanticAnalyzer {
    private static final int DISPLAY_OFFSET = 1;
    private static final int SINGLE_RESULT = 1;

    public static final class Diagnostic {
        private final int line;
        private final int column;
        private final String message;

        private Diagnostic(Ast.Node node, String message) {
            line = node.getLine();
            column = node.getColumn();
            this.message = message;
        }

        public int getLine() {
            return line;
        }

        public int getColumn() {
            return column;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return "line " + (line + DISPLAY_OFFSET) + ", col " + (column + DISPLAY_OFFSET) + ": " + message;
        }
    }

    private record Signature(boolean formula, List<Ast.Type> parameters, List<Ast.Type> results) {
        private Signature {
            parameters = List.copyOf(parameters);
            results = List.copyOf(results);
        }
    }

    private record Variable(Ast.VariableDef declaration, boolean readOnly) {}

    private final Map<String, Signature> signatures = new LinkedHashMap<>();
    private final List<Diagnostic> diagnostics = new ArrayList<>();

    /** Annotates the supplied tree in place. Each call starts a fresh analysis. */
    public List<Diagnostic> analyze(Ast.Module module) {
        Objects.requireNonNull(module, "Module must not be null");
        diagnostics.clear();
        signatures.clear();
        signatures.put("length", new Signature(false, List.of(Ast.Type.INT_ARRAY), List.of(Ast.Type.INT)));

        for (Ast.Node item : module.getItems()) {
            if (item instanceof Ast.Function function) {
                register(function.getName(), false, function.getParams(), function.getReturns(), function);
            } else if (item instanceof Ast.Formula formula) {
                register(formula.getName(), true, formula.getParams(), List.of(), formula);
            }
        }

        for (Ast.Node item : module.getItems()) {
            if (item instanceof Ast.Function function) {
                analyzeFunction(function);
            } else if (item instanceof Ast.Formula formula) {
                Map<String, Variable> scope = new LinkedHashMap<>();
                declareAll(formula.getParams(), true, true, scope);
                checkPredicate(formula.getBody(), scope);
            } else {
                error(item, "Unsupported module item: " + item.getClass().getSimpleName());
            }
        }

        return getDiagnostics();
    }

    public List<Diagnostic> getDiagnostics() {
        return List.copyOf(diagnostics);
    }

    private void register(String name, boolean formula, List<Ast.VariableDef> parameters,
            List<Ast.VariableDef> results, Ast.Node node) {

        List<Ast.Type> parameterTypes = parameters.stream().map(Ast.VariableDef::getDeclaredType).toList();
        List<Ast.Type> resultTypes = results.stream().map(Ast.VariableDef::getDeclaredType).toList();
        if (formula) {
            resultTypes = List.of(Ast.Type.BOOL);
        } else if (results.isEmpty()) {
            error(node, "Function '" + name + "' must declare at least one result");
        }

        Signature signature = new Signature(formula, parameterTypes, resultTypes);
        if (signatures.putIfAbsent(name, signature) != null) {
            error(node, "Duplicate definition of '" + name + "'");
        }
    }

    private void analyzeFunction(Ast.Function function) {
        Map<String, Variable> scope = new LinkedHashMap<>();
        declareAll(function.getParams(), true, true, scope);
        checkPredicate(function.getRequires(), scope);

        declareAll(function.getReturns(), false, true, scope);
        checkPredicate(function.getEnsures(), scope);

        declareAll(function.getLocals(), false, false, scope);
        statement(function.getBody(), scope);
        for (Ast.VariableDef local : function.getLocals()) {
            if (local.getType() == Ast.Type.UNKNOWN) {
                error(local, "Cannot infer type of local variable '" + local.getName() + "'");
            }
        }
    }

    private void declareAll(List<Ast.VariableDef> declarations, boolean readOnly, boolean annotationRequired,
            Map<String, Variable> scope) {

        for (Ast.VariableDef declaration : declarations) {
            declare(declaration, readOnly, annotationRequired, scope);
        }
    }

    private void declare(Ast.VariableDef declaration, boolean readOnly, boolean annotationRequired,
            Map<String, Variable> scope) {

        declaration.setInferredType(Ast.Type.UNKNOWN);
        if (annotationRequired && !declaration.hasTypeAnnotation()) {
            error(declaration, "Explicit type required for '" + declaration.getName() + "'");
            declaration.setInferredType(Ast.Type.ERROR);
        }

        Variable variable = new Variable(declaration, readOnly);
        if (scope.putIfAbsent(declaration.getName(), variable) != null) {
            error(declaration, "Duplicate variable '" + declaration.getName() + "'");
        }
    }

    private void statement(Ast.Statement statement, Map<String, Variable> scope) {
        if (statement == null) {
            return;
        }

        if (statement instanceof Ast.Block block) {
            for (Ast.Statement child : block.getStatements()) {
                statement(child, scope);
            }
        } else if (statement instanceof Ast.Assign assignment) {
            Ast.Type value = expression(assignment.getValue(), scope);
            Variable target = writable(assignment.getTarget(), assignment, scope);
            if (assignment.getIndices().isEmpty()) {
                assign(target, value, assignment);
            } else {
                Ast.Type base = Ast.Type.ERROR;
                if (target != null) {
                    base = target.declaration().getType();
                    if (base == Ast.Type.UNKNOWN) {
                        base = Ast.Type.INT_ARRAY;
                    }
                }

                Ast.Type element = indexed(base, assignment.getIndices(), assignment, scope);
                boolean valid = expect(value, element, assignment.getValue(), "Array assignment");
                if (valid && element != Ast.Type.ERROR) {
                    assign(target, base, assignment);
                }
            }
        } else if (statement instanceof Ast.TupleAssign assignment) {
            tupleAssignment(assignment, scope);
        } else if (statement instanceof Ast.If conditional) {
            checkPredicate(conditional.getCond(), scope);
            statement(conditional.getThenBranch(), scope);
            statement(conditional.getElseBranch(), scope);
        } else if (statement instanceof Ast.While loop) {
            checkPredicate(loop.getCond(), scope);
            checkPredicate(loop.getInvariant(), scope);
            statement(loop.getBody(), scope);
        } else if (statement instanceof Ast.Assert assertion) {
            checkPredicate(assertion.getPredicate(), scope);
        } else if (statement instanceof Ast.Assume assumption) {
            checkPredicate(assumption.getPredicate(), scope);
        } else {
            error(statement, "Unsupported statement: " + statement.getClass().getSimpleName());
        }
    }

    private void tupleAssignment(Ast.TupleAssign assignment, Map<String, Variable> scope) {
        Ast.Call call = assignment.getCall();
        Ast.Type result = call(call.getName(), call.getArgs(), false, true, call, scope);
        call.setInferredType(result);
        List<Ast.Type> results = List.of();
        if (result.getKind() == Ast.Type.Kind.TUPLE) {
            results = result.getElements();
        } else if (result != Ast.Type.ERROR) {
            results = List.of(result);
        }

        boolean matching = result != Ast.Type.ERROR && results.size() == assignment.getTargets().size();
        if (result != Ast.Type.ERROR && !matching) {
            error(assignment, "Result count mismatch: expected " + assignment.getTargets().size()
                    + ", got " + results.size());
        }

        Set<String> seen = new HashSet<>();
        for (int index = 0; index < assignment.getTargets().size(); index++) {
            String name = assignment.getTargets().get(index);
            Variable target = writable(name, assignment, scope);
            if (!seen.add(name)) {
                error(assignment, "Duplicate tuple target '" + name + "'");
            } else if (matching) {
                assign(target, results.get(index), assignment);
            }
        }
    }

    private Variable writable(String name, Ast.Node node, Map<String, Variable> scope) {
        Variable variable = lookup(name, node, scope);
        if (variable != null && variable.readOnly()) {
            error(node, "Cannot assign to read-only variable '" + name + "'");
            return null;
        }

        return variable;
    }

    private void assign(Variable target, Ast.Type value, Ast.Node node) {
        if (target == null || value == Ast.Type.ERROR || value == Ast.Type.UNKNOWN) {
            return;
        }

        Ast.VariableDef declaration = target.declaration();
        if (declaration.getType() == Ast.Type.UNKNOWN) {
            if (value != Ast.Type.INT && value != Ast.Type.INT_ARRAY) {
                error(node, "Invalid variable type: " + value);
            } else {
                declaration.setInferredType(value);
            }
        } else {
            expect(value, declaration.getType(), node, "Assignment to '" + declaration.getName() + "'");
        }
    }

    private Variable lookup(String name, Ast.Node node, Map<String, Variable> scope) {
        Variable variable = scope.get(name);
        if (variable == null) {
            error(node, "Unknown variable '" + name + "'");
        }

        return variable;
    }

    private Ast.Type read(String name, Ast.Node node, Map<String, Variable> scope) {
        Variable variable = lookup(name, node, scope);
        if (variable == null) {
            return Ast.Type.ERROR;
        }

        Ast.Type type = variable.declaration().getType();
        if (type == Ast.Type.UNKNOWN) {
            error(node, "Variable '" + name + "' is used before its type is inferred");
            return Ast.Type.ERROR;
        }

        return type;
    }

    private Ast.Type indexed(Ast.Type base, List<Ast.IntExpr> indices, Ast.Node node, Map<String, Variable> scope) {
        Ast.Type result = base;
        boolean valid = true;
        for (Ast.IntExpr index : indices) {
            Ast.Type indexType = expression(index, scope);
            valid &= expect(indexType, Ast.Type.INT, index, "Array index");
            if (result == Ast.Type.INT_ARRAY) {
                result = Ast.Type.INT;
            } else if (result != Ast.Type.ERROR) {
                error(node, "Cannot index value of type " + result);
                result = Ast.Type.ERROR;
            }
        }

        if (!valid) {
            return Ast.Type.ERROR;
        }

        return result;
    }

    private boolean checkPredicate(Ast.PredNode predicate, Map<String, Variable> scope) {
        if (predicate == null) {
            return true;
        }

        return expect(expression(predicate, scope), Ast.Type.BOOL, predicate, "Predicate");
    }

    private Ast.Type expression(Ast.Expr expression, Map<String, Variable> scope) {
        Ast.Type type = expressionType(expression, scope);
        expression.setInferredType(type);
        return type;
    }

    private Ast.Type expressionType(Ast.Expr expression, Map<String, Variable> scope) {
        if (expression instanceof Ast.NumLit) {
            return Ast.Type.INT;
        }

        if (expression instanceof Ast.BoolLit) {
            return Ast.Type.BOOL;
        }

        if (expression instanceof Ast.Var variable) {
            return read(variable.getName(), variable, scope);
        }

        if (expression instanceof Ast.ArrayAccess access) {
            return indexed(read(access.getArray(), access, scope), access.getIndices(), access, scope);
        }

        if (expression instanceof Ast.Call call) {
            return call(call.getName(), call.getArgs(), false, false, call, scope);
        }

        if (expression instanceof Ast.FormulaRef reference) {
            return call(reference.getName(), reference.getArgs(), true, false, reference, scope);
        }

        if (expression instanceof Ast.Neg negation) {
            return unary(negation.getOperand(), Ast.Type.INT, scope);
        }

        if (expression instanceof Ast.Arith arithmetic) {
            return binary(arithmetic.getLeft(), arithmetic.getRight(), Ast.Type.INT, Ast.Type.INT, scope);
        }

        if (expression instanceof Ast.Comparison comparison) {
            return binary(comparison.getLeft(), comparison.getRight(), Ast.Type.INT, Ast.Type.BOOL, scope);
        }

        if (expression instanceof Ast.Not negation) {
            return unary(negation.getOperand(), Ast.Type.BOOL, scope);
        }

        if (expression instanceof Ast.AndOr logical) {
            return binary(logical.getLeft(), logical.getRight(), Ast.Type.BOOL, Ast.Type.BOOL, scope);
        }

        if (expression instanceof Ast.Implies implication) {
            return binary(implication.getLeft(), implication.getRight(), Ast.Type.BOOL, Ast.Type.BOOL, scope);
        }

        if (expression instanceof Ast.Quantifier quantifier) {
            Map<String, Variable> nested = new LinkedHashMap<>(scope);
            declare(quantifier.getVariable(), true, true, nested);
            if (checkPredicate(quantifier.getBody(), nested)) {
                return Ast.Type.BOOL;
            }

            return Ast.Type.ERROR;
        }

        error(expression, "Unsupported expression: " + expression.getClass().getSimpleName());
        return Ast.Type.ERROR;
    }

    private Ast.Type unary(Ast.Expr operand, Ast.Type expected, Map<String, Variable> scope) {
        if (expect(expression(operand, scope), expected, operand, "Operand")) {
            return expected;
        }

        return Ast.Type.ERROR;
    }

    private Ast.Type binary(Ast.Expr left, Ast.Expr right, Ast.Type expected, Ast.Type result,
            Map<String, Variable> scope) {

        boolean validLeft = expect(expression(left, scope), expected, left, "Left operand");
        boolean validRight = expect(expression(right, scope), expected, right, "Right operand");
        if (validLeft && validRight) {
            return result;
        }

        return Ast.Type.ERROR;
    }

    private Ast.Type call(String name, List<Ast.IntExpr> arguments, boolean formula, boolean allowTuple,
            Ast.Node node, Map<String, Variable> scope) {

        List<Ast.Type> actual = new ArrayList<>();
        for (Ast.IntExpr argument : arguments) {
            actual.add(expression(argument, scope));
        }

        Signature signature = signatures.get(name);
        if (signature == null) {
            error(node, "Unknown callable '" + name + "'");
            return Ast.Type.ERROR;
        }

        if (signature.formula() != formula) {
            error(node, "Wrong callable kind for '" + name + "': function and formula uses differ");
            return Ast.Type.ERROR;
        }

        boolean valid = true;
        if (actual.size() != signature.parameters().size()) {
            error(node, "Argument count mismatch for '" + name + "': expected " + signature.parameters().size()
                    + ", got " + actual.size());
            valid = false;
        }

        for (int index = 0; index < actual.size(); index++) {
            if (index < signature.parameters().size()) {
                valid &= expect(actual.get(index), signature.parameters().get(index), arguments.get(index),
                        "Argument " + (index + DISPLAY_OFFSET) + " of '" + name + "'");

            } else if (actual.get(index) == Ast.Type.ERROR) {
                valid = false;
            }
        }

        List<Ast.Type> results = signature.results();
        if (!allowTuple && results.size() > SINGLE_RESULT) {
            error(node, "Multiple results from '" + name + "' require a tuple assignment");
            valid = false;
        }

        if (!valid || results.isEmpty() || results.contains(Ast.Type.UNKNOWN)) {
            return Ast.Type.ERROR;
        }

        if (results.size() == SINGLE_RESULT) {
            return results.getFirst();
        }

        return Ast.Type.tuple(results);
    }

    private boolean expect(Ast.Type actual, Ast.Type expected, Ast.Node node, String context) {
        if (actual == Ast.Type.ERROR || expected == Ast.Type.ERROR) {
            return false;
        }

        if (!actual.equals(expected) || actual == Ast.Type.UNKNOWN) {
            error(node, context + ": expected " + expected + ", got " + actual);
            return false;
        }

        return true;
    }

    private void error(Ast.Node node, String message) {
        diagnostics.add(new Diagnostic(node, message));
    }
}
