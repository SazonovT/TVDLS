import java.io.StringReader;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public final class AstTypesTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        Parser parser = new Parser(new TokenStream(new Lexer(new StringReader("""
                P(a:int[]) => forall(i:int | i >= 0)
                f(a:int[]) returns r:int[], n:int uses copy {
                    copy = a;
                    r = copy;
                    n = length(r);
                    assert P(r);
                }
                """))));
        Ast.Module module = parser.parseModule();
        check(parser.getDiagnostics().isEmpty(), parser.getDiagnostics().toString());
        Ast.Function function = (Ast.Function) module.getItems().get(1);
        Ast.VariableDef local = function.getLocals().get(0);
        check(!local.hasTypeAnnotation() && local.getType() == Ast.Type.UNKNOWN,
                "An unannotated local must remain unresolved after parsing");
        local.setInferredType(Ast.Type.INT_ARRAY);
        check(local.getType() == Ast.Type.INT_ARRAY && local.getDeclaredType() == Ast.Type.UNKNOWN,
                "Inference must preserve the source annotation");
        Ast.VariableDef parameter = function.getParams().get(0);
        check(parameter.hasTypeAnnotation() && parameter.getDeclaredType() == Ast.Type.INT_ARRAY,
                "Explicit array annotation must survive parsing");
        Ast.Block body = (Ast.Block) function.getBody();
        Ast.IntExpr arrayValue = ((Ast.Assign) body.getStatements().get(0)).getValue();
        check(arrayValue.getInferredType() == Ast.Type.UNKNOWN, "Parser must not pretend to type-check");
        arrayValue.setInferredType(Ast.Type.INT_ARRAY);
        Ast.Call call = (Ast.Call) ((Ast.Assign) body.getStatements().get(2)).getValue();
        call.setInferredType(Ast.Type.INT);
        Ast.Predicate predicate = ((Ast.Assert) body.getStatements().get(3)).getPredicate();
        predicate.setInferredType(Ast.Type.BOOL);
        check(predicate.getInferredType() == Ast.Type.BOOL, "Predicates need the same annotation API");
        predicate.setInferredType(Ast.Type.ERROR);
        check(predicate.getInferredType() == Ast.Type.ERROR, "Error type must be representable");

        List<Ast.Type> results = new ArrayList<>(List.of(Ast.Type.INT_ARRAY, Ast.Type.INT));
        Ast.Type tuple = Ast.Type.tuple(results);
        results.clear();
        check(tuple.equals(Ast.Type.tuple(List.of(Ast.Type.INT_ARRAY, Ast.Type.INT))), "Tuple equality");
        check(tuple.hashCode() == Ast.Type.tuple(tuple.getElements()).hashCode(), "Tuple hash code");
        check(tuple.toString().equals("(int[], int)"), "Tuple display and defensive copy");
        call.setInferredType(tuple);
        try {
            tuple.getElements().clear();
            throw new AssertionError("Tuple elements must be immutable");
        } catch (UnsupportedOperationException expected) { }
        try {
            arrayValue.setInferredType(null);
            throw new AssertionError("Null is not a semantic type");
        } catch (NullPointerException expected) { }
        try {
            new Ast.VariableDef("bad", Ast.Type.BOOL, 0, 0);
            throw new AssertionError("Funny has no bool variable annotation");
        } catch (IllegalArgumentException expected) { }

        String mermaid = new MermaidAstPrinter().toMermaid(module);
        check(mermaid.contains("Call: length<br/>type: (int[], int)"), "Annotated call output");
        check(mermaid.contains("Variable: copy<br/>type: int[]"), "Inferred declaration output");
        check(arrayValue.getLine() == 2 && arrayValue.getColumn() == 11, "Source positions preserved");
        for (Class<?> type : Ast.class.getDeclaredClasses()) {
            if (Ast.Node.class.isAssignableFrom(type) && !Modifier.isAbstract(type.getModifiers())
                    && (Ast.IntExpr.class.isAssignableFrom(type) || Ast.PredNode.class.isAssignableFrom(type))) {
                check(Ast.Expr.class.isAssignableFrom(type), "Missing expression annotation: " + type);
            }
        }
        System.out.println("AST type preparation checks passed");
    }
}
