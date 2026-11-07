import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * AST языка Funny.
 */
public final class Ast {

    /** Semantic types; UNKNOWN means unchecked/unresolved, ERROR suppresses cascades. */
    public static final class Type {
        public enum Kind { INT, INT_ARRAY, BOOL, TUPLE, UNKNOWN, ERROR }

        public static final Type INT = new Type(Kind.INT, List.of());
        public static final Type INT_ARRAY = new Type(Kind.INT_ARRAY, List.of());
        public static final Type BOOL = new Type(Kind.BOOL, List.of());
        public static final Type UNKNOWN = new Type(Kind.UNKNOWN, List.of());
        public static final Type ERROR = new Type(Kind.ERROR, List.of());

        private final Kind kind;
        private final List<Type> elements;

        private Type(Kind kind, List<Type> elements) {
            this.kind = kind;
            this.elements = List.copyOf(elements);
        }

        public Kind getKind() { return kind; }
        public List<Type> getElements() { return elements; }

        public static Type tuple(List<Type> elements) {
            if (elements.size() < 2) throw new IllegalArgumentException("Tuple needs at least two types");
            return new Type(Kind.TUPLE, elements);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Type type && kind == type.kind && elements.equals(type.elements);
        }

        @Override
        public int hashCode() { return Objects.hash(kind, elements); }

        @Override
        public String toString() {
            return switch (kind) {
                case INT -> "int";
                case INT_ARRAY -> "int[]";
                case BOOL -> "bool";
                case UNKNOWN -> "unknown";
                case ERROR -> "error";
                case TUPLE -> elements.stream().map(Type::toString)
                        .collect(Collectors.joining(", ", "(", ")"));
            };
        }
    }

    public abstract static class Node {
        private final int line;
        private final int column;

        protected Node(int line, int column) {
            this.line = line;
            this.column = column;
        }

        public int getLine() { return line; }
        public int getColumn() { return column; }
    }

    /** Common annotation API for value expressions and logical expressions. */
    public abstract static class Expr extends Node {
        private Type inferredType = Type.UNKNOWN;

        protected Expr(int line, int column) { super(line, column); }
        public Type getInferredType() { return inferredType; }
        public void setInferredType(Type type) { inferredType = Objects.requireNonNull(type); }
    }

    // Historical syntax category: may also denote an array or a multi-result call.
    public abstract static class IntExpr extends Expr {
        protected IntExpr(int line, int column) { super(line, column); }
    }
    public abstract static class PredNode extends Expr {
        protected PredNode(int line, int column) { super(line, column); }
    }

    public static final class Module extends Node {
        private final List<Node> items = new ArrayList<>(); // Function | Formula

        public List<Node> getItems() { return items; }
        public Module(int line, int column) { super(line, column); }
    }

    public static final class VariableDef extends Node {
        private final String name;

        public String getName() { return name; }
        private final Type declaredType;
        private Type inferredType = Type.UNKNOWN;

        public Type getDeclaredType() { return declaredType; }
        public boolean hasTypeAnnotation() { return declaredType != Type.UNKNOWN; }
        public Type getInferredType() { return inferredType; }
        public void setInferredType(Type type) { inferredType = Objects.requireNonNull(type); }
        public Type getType() { return hasTypeAnnotation() ? declaredType : inferredType; }
        public VariableDef(String name, Type declaredType, int line, int column) {
            super(line, column);
            this.name = name;
            this.declaredType = Objects.requireNonNull(declaredType);
            if (declaredType != Type.INT && declaredType != Type.INT_ARRAY && declaredType != Type.UNKNOWN)
                throw new IllegalArgumentException("Unsupported variable annotation: " + declaredType);
        }
    }

    public static final class Function extends Node {
        private final String name;

        public String getName() { return name; }
        private List<VariableDef> params = new ArrayList<>();

        public List<VariableDef> getParams() { return params; }

        public void setParams(List<VariableDef> params) { this.params = params; }
        private PredNode requires;

        public PredNode getRequires() { return requires; }

        public void setRequires(PredNode requires) { this.requires = requires; }
        private List<VariableDef> returns = new ArrayList<>();

        public List<VariableDef> getReturns() { return returns; }

        public void setReturns(List<VariableDef> returns) { this.returns = returns; }
        private PredNode ensures;

        public PredNode getEnsures() { return ensures; }

        public void setEnsures(PredNode ensures) { this.ensures = ensures; }
        private List<VariableDef> locals = new ArrayList<>();

        public List<VariableDef> getLocals() { return locals; }

        public void setLocals(List<VariableDef> locals) { this.locals = locals; }
        private Statement body;

        public Statement getBody() { return body; }

        public void setBody(Statement body) { this.body = body; }
        public Function(String name, int line, int column) { super(line, column); this.name = name; }
    }

    public static final class Formula extends Node {
        private final String name;

        public String getName() { return name; }
        private List<VariableDef> params = new ArrayList<>();

        public List<VariableDef> getParams() { return params; }

        public void setParams(List<VariableDef> params) { this.params = params; }
        private PredNode body;

        public PredNode getBody() { return body; }

        public void setBody(PredNode body) { this.body = body; }
        public Formula(String name, int line, int column) { super(line, column); this.name = name; }
    }

    public abstract static class Statement extends Node {
        protected Statement(int line, int column) { super(line, column); }
    }

    public static final class Assign extends Statement {
        private final String target;

        public String getTarget() { return target; }
        private final List<IntExpr> indices;

        public List<IntExpr> getIndices() { return indices; }
        private final IntExpr value;

        public IntExpr getValue() { return value; }
        public Assign(String target, List<IntExpr> indices, IntExpr value, int line, int column) {
            super(line, column);
            this.target = target; this.indices = indices; this.value = value;
        }
    }

    public static final class TupleAssign extends Statement {
        private final List<String> targets;

        public List<String> getTargets() { return targets; }
        private final Call call;

        public Call getCall() { return call; }
        public TupleAssign(List<String> targets, Call call, int line, int column) {
            super(line, column);
            this.targets = targets; this.call = call;
        }
    }

    public static final class If extends Statement {
        private final Condition cond;

        public Condition getCond() { return cond; }
        private final Statement thenBranch;

        public Statement getThenBranch() { return thenBranch; }
        private final Statement elseBranch;

        public Statement getElseBranch() { return elseBranch; }
        public If(Condition cond, Statement thenBranch, Statement elseBranch, int line, int column) {
            super(line, column);
            this.cond = cond; this.thenBranch = thenBranch; this.elseBranch = elseBranch;
        }
    }

    public static final class While extends Statement {
        private final Condition cond;

        public Condition getCond() { return cond; }
        private PredNode invariant;

        public PredNode getInvariant() { return invariant; }

        public void setInvariant(PredNode invariant) { this.invariant = invariant; }
        private Statement body;

        public Statement getBody() { return body; }

        public void setBody(Statement body) { this.body = body; }
        public While(Condition cond, Statement body, int line, int column) {
            super(line, column);
            this.cond = cond; this.body = body;
        }
    }

    public static final class Block extends Statement {
        private final List<Statement> statements = new ArrayList<>();

        public List<Statement> getStatements() { return statements; }
        public Block(int line, int column) { super(line, column); }
    }

    public static final class Assert extends Statement {
        private final Predicate predicate;

        public Predicate getPredicate() { return predicate; }
        public Assert(Predicate predicate, int line, int column) {
            super(line, column); this.predicate = predicate;
        }
    }

    public static final class Assume extends Statement {
        private final Predicate predicate;

        public Predicate getPredicate() { return predicate; }
        public Assume(Predicate predicate, int line, int column) {
            super(line, column); this.predicate = predicate;
        }
    }

    public static final class NumLit extends IntExpr {
        private final long value;

        public long getValue() { return value; }
        public NumLit(long value, int line, int column) {
            super(line, column); this.value = value;
        }
    }

    public static final class Var extends IntExpr {
        private final String name;

        public String getName() { return name; }
        public Var(String name, int line, int column) {
            super(line, column); this.name = name;
        }
    }

    public static final class ArrayAccess extends IntExpr {
        private final String array;

        public String getArray() { return array; }
        private final List<IntExpr> indices;

        public List<IntExpr> getIndices() { return indices; }
        public ArrayAccess(String array, List<IntExpr> indices, int line, int column) {
            super(line, column);
            this.array = array; this.indices = indices;
        }
    }

    public static final class Call extends IntExpr {
        private final String name;

        public String getName() { return name; }
        private final List<IntExpr> args;

        public List<IntExpr> getArgs() { return args; }
        public Call(String name, List<IntExpr> args, int line, int column) {
            super(line, column); this.name = name; this.args = args;
        }
    }

    public static final class Neg extends IntExpr {
        private final IntExpr operand;

        public IntExpr getOperand() { return operand; }
        public Neg(IntExpr operand, int line, int column) {
            super(line, column); this.operand = operand;
        }
    }

    public static final class Arith extends IntExpr {
        public enum Op { ADD, SUB, MUL, DIV }
        private final Op op;

        public Op getOp() { return op; }
        private final IntExpr left, right;

        public IntExpr getLeft() { return left; }

        public IntExpr getRight() { return right; }
        public Arith(Op op, IntExpr left, IntExpr right, int line, int column) {
            super(line, column);
            this.op = op; this.left = left; this.right = right;
        }
    }

    public abstract static class Condition extends PredNode {
        protected Condition(int line, int column) { super(line, column); }
    }

    public abstract static class Predicate extends Condition {
        protected Predicate(int line, int column) { super(line, column); }
    }

    public static final class BoolLit extends Predicate {
        private final boolean value;

        public boolean getValue() { return value; }
        public BoolLit(boolean value, int line, int column) {
            super(line, column); this.value = value;
        }
    }

    public static final class Comparison extends Predicate {
        public enum Op { EQ, NEQ, GE, LE, GT, LT }
        private final Op op;

        public Op getOp() { return op; }
        private final IntExpr left, right;

        public IntExpr getLeft() { return left; }

        public IntExpr getRight() { return right; }
        public Comparison(Op op, IntExpr left, IntExpr right, int line, int column) {
            super(line, column);
            this.op = op; this.left = left; this.right = right;
        }
    }

    public static final class Not extends Predicate {
        private final PredNode operand;

        public PredNode getOperand() { return operand; }
        public Not(PredNode operand, int line, int column) {
            super(line, column); this.operand = operand;
        }
    }

    public static final class AndOr extends Predicate {
        public enum Op { AND, OR }
        private final Op op;

        public Op getOp() { return op; }
        private final PredNode left, right;

        public PredNode getLeft() { return left; }

        public PredNode getRight() { return right; }
        public AndOr(Op op, PredNode left, PredNode right, int line, int column) {
            super(line, column);
            this.op = op; this.left = left; this.right = right;
        }
    }

    public static final class Implies extends Predicate {
        private final Condition left, right;

        public Condition getLeft() { return left; }

        public Condition getRight() { return right; }
        public Implies(Condition left, Condition right, int line, int column) {
            super(line, column); this.left = left; this.right = right;
        }
    }

    public static final class Quantifier extends Predicate {
        public enum Kind { FORALL, EXISTS }
        private final Kind kind;

        public Kind getKind() { return kind; }
        private final VariableDef variable;

        public VariableDef getVariable() { return variable; }
        private final Predicate body;

        public Predicate getBody() { return body; }
        public Quantifier(Kind kind, VariableDef variable, Predicate body, int line, int column) {
            super(line, column);
            this.kind = kind; this.variable = variable; this.body = body;
        }
    }

    public static final class FormulaRef extends Predicate {
        private final String name;

        public String getName() { return name; }
        private final List<IntExpr> args;

        public List<IntExpr> getArgs() { return args; }
        public FormulaRef(String name, List<IntExpr> args, int line, int column) {
            super(line, column); this.name = name; this.args = args;
        }
    }

}
