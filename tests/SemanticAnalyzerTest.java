import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class SemanticAnalyzerTest {
    private static final int EXPECTED_INDEX_ERRORS = 2;
    private static final int EXPECTED_INDEPENDENT_ERRORS = 2;

    private record NegativeCase(String source, String message) {}

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static Ast.Module parse(String source) {
        Parser parser = new Parser(new TokenStream(new Lexer(new StringReader(source))));
        Ast.Module module = parser.parseModule();
        check(parser.getDiagnostics().isEmpty(), "Unexpected parse errors: " + parser.getDiagnostics());
        return module;
    }

    private static void positive() {
        Ast.Module module = parse("""
                Sorted(a:int[]) => forall(i:int | exists(j:int | i == j) and i >= 0)
                First(a:int[]) requires length(a)>0 returns r:int { r=a[0]; }
                Recursive(n:int) returns r:int {
                    if (n==0) { r=0; } else { r=Recursive(n-0); }
                }
                Use(a:int[]) requires Sorted(a) returns r:int[], n:int
                    ensures n >= 0 uses copy, k, lo, hi {
                    copy=a;
                    k=0;
                    copy[k]=First(a);
                    r=copy;
                    lo,hi=Pair(k);
                    n=length(copy)+lo-hi;
                    while (k<n) invariant 0<=k and k<=n { k=k+0; }
                    if (not false and true or false -> true) { assert Sorted(copy); }
                    else { assume forall(i:int | i == i); }
                    assert exists(j:int | j == j);
                }
                Pair(x:int) returns lo:int, hi:int { lo=-x; hi=x*0/0; }
                """);

        SemanticAnalyzer analyzer = new SemanticAnalyzer();
        check(analyzer.analyze(module).isEmpty(), analyzer.getDiagnostics().toString());
        Ast.Function function = (Ast.Function) module.getItems().stream()
                .filter(item -> item instanceof Ast.Function f && f.getName().equals("Use")).findFirst().orElseThrow();

        check(function.getLocals().getFirst().getType() == Ast.Type.INT_ARRAY, "Array local inference");
        check(function.getLocals().getLast().getType() == Ast.Type.INT, "Tuple local inference");
        Ast.Block block = (Ast.Block) function.getBody();
        Ast.TupleAssign tuple = (Ast.TupleAssign) block.getStatements().stream()
                .filter(statement -> statement instanceof Ast.TupleAssign).findFirst().orElseThrow();

        check(tuple.getCall().getInferredType().equals(Ast.Type.tuple(List.of(Ast.Type.INT, Ast.Type.INT))),
                "Multi-result call annotation");

        Ast.Formula formula = (Ast.Formula) module.getItems().getFirst();
        check(formula.getBody().getInferredType() == Ast.Type.BOOL, "Quantifier annotation");
        check(new MermaidAstPrinter().toMermaid(module).contains("type: (int, int)"), "Annotated output");
        check(analyzer.analyze(module).isEmpty(), "Analysis must be repeatable");
    }

    private static void negative() {
        List<NegativeCase> cases = List.of(
                new NegativeCase("f() returns r:int { r=missing; }", "Unknown variable 'missing'"),
                new NegativeCase("f(x:int, x:int) returns r:int { r=x; }", "Duplicate variable 'x'"),
                new NegativeCase("f(x:int) returns x:int { x=0; }", "Duplicate variable 'x'"),
                new NegativeCase("f() returns r:int uses r:int { r=0; }", "Duplicate variable 'r'"),
                new NegativeCase("f() returns r:int {} f() returns x:int {}", "Duplicate definition of 'f'"),
                new NegativeCase("length() returns r:int {}", "Duplicate definition of 'length'"),
                new NegativeCase("f(x:int) returns r:int { x=0; r=x; }", "read-only variable 'x'"),
                new NegativeCase("f(a:int[]) returns r:int { a[0]=0; r=0; }", "read-only variable 'a'"),
                new NegativeCase("f(a:int[]) returns r:int { r=a; }", "expected int, got int[]"),
                new NegativeCase("f(a:int[]) returns r:int { r=a+0; }", "Left operand: expected int"),
                new NegativeCase("f(a:int[]) returns r:int { r=-a; }", "Operand: expected int"),
                new NegativeCase("P(a:int[]) => a==a", "expected int, got int[]"),
                new NegativeCase("f() returns r:int { r=unknown(0); }", "Unknown callable 'unknown'"),
                new NegativeCase("f() returns r:int { r=length(0); }", "Argument 1 of 'length'"),
                new NegativeCase("f() returns r:int { r=length(); }", "Argument count mismatch"),
                new NegativeCase("f() returns r:int { r=length(0,0); }", "Argument count mismatch"),
                new NegativeCase("P() => true f() returns r:int { r=P(); }", "Wrong callable kind"),
                new NegativeCase("f() returns r:int { r=0; } P() => f()", "Wrong callable kind"),
                new NegativeCase("P(x:int) => true f() returns r:int { assert P(); }", "Argument count mismatch"),
                new NegativeCase("f() returns r:int { assert length(); }", "Wrong callable kind"),
                new NegativeCase("f() returns r:int { r=r[0]; }", "Cannot index value of type int"),
                new NegativeCase("f(a:int[]) returns r:int { r=a[a]; }", "Array index: expected int"),
                new NegativeCase("f(a:int[]) returns r:int { r=a[0][0]; }", "Cannot index value of type int"),
                new NegativeCase("f(a:int[]) returns r:int uses b { b=a; b[0]=a; }", "Array assignment"),
                new NegativeCase("f() requires r>0 returns r:int {}", "Unknown variable 'r'"),
                new NegativeCase("f() returns r:int ensures x>0 uses x:int {}", "Unknown variable 'x'"),
                new NegativeCase("P() => forall(i:int | i==i) and i==0", "Unknown variable 'i'"),
                new NegativeCase("P(x:int) => forall(x:int | x==0)", "Duplicate variable 'x'"),
                new NegativeCase("P() => forall(i:int | exists(i:int | i==0))", "Duplicate variable 'i'"),
                new NegativeCase("f() returns r:int uses x { r=x; x=0; }", "before its type is inferred"),
                new NegativeCase("f() returns r:int uses x { r=0; }", "Cannot infer type of local variable 'x'"),
                new NegativeCase("f(a:int[]) returns r:int uses x { x=0; x=a; }", "expected int, got int[]"),
                new NegativeCase("f(a:int[]) returns r:int uses x { if (true) { x=0; } else { x=a; } }",
                        "expected int, got int[]"),

                new NegativeCase("p() returns x:int,y:int {} f() returns r:int { r=p()+0; }", "require a tuple"),
                new NegativeCase("p() returns x:int {} f() returns r:int uses a,b { a,b=p(); }", "Result count"),
                new NegativeCase("p() returns x:int,y:int {} f() returns r:int uses a { a,a=p(); }",
                        "Duplicate tuple target"),

                new NegativeCase("p() returns x:int,y:int {} f(a:int) returns r:int { a,r=p(); }", "read-only"),
                new NegativeCase("p() returns x:int[],y:int {} f() returns a:int,b:int { a,b=p(); }",
                        "expected int, got int[]"));

        SemanticAnalyzer analyzer = new SemanticAnalyzer();
        for (NegativeCase test : cases) {
            List<SemanticAnalyzer.Diagnostic> errors = analyzer.analyze(parse(test.source()));
            String expected = test.message();
            check(errors.stream().anyMatch(error -> error.getMessage().contains(expected)),
                    "Missing '" + expected + "' for " + test.source() + ": " + errors);

            for (SemanticAnalyzer.Diagnostic error : errors) {
                check(error.getLine() >= 0 && error.getColumn() >= 0, "Diagnostic location");
            }
        }

        check(analyzer.analyze(parse("f() returns r:int { r=0; }")).isEmpty(), "Old errors must be cleared");
    }

    private static void recoveryAndAnnotations() {
        Ast.Module module = parse("""
                f(a:int[]) returns r:int uses x, b {
                    x=missing;
                    x=0;
                    b[0]=0;
                    r=length(b);
                    assert unknown(a);
                }
                """);

        SemanticAnalyzer analyzer = new SemanticAnalyzer();
        List<SemanticAnalyzer.Diagnostic> errors = analyzer.analyze(module);
        check(errors.size() == EXPECTED_INDEPENDENT_ERRORS, "Independent errors must accumulate: " + errors);
        Ast.Function function = (Ast.Function) module.getItems().getFirst();
        check(function.getLocals().getFirst().getType() == Ast.Type.INT, "Infer after first failed assignment");
        check(function.getLocals().getLast().getType() == Ast.Type.INT_ARRAY, "Infer from indexed assignment");
        Ast.Block block = (Ast.Block) function.getBody();
        Ast.Assign first = (Ast.Assign) block.getStatements().getFirst();
        check(first.getValue().getInferredType() == Ast.Type.ERROR, "Error annotation");
        SemanticAnalyzer.Diagnostic error = errors.getFirst();
        check(error.toString().startsWith("line 2, col 7:"), "Diagnostic must point at the unknown name");
        List<SemanticAnalyzer.Diagnostic> snapshot = analyzer.getDiagnostics();
        analyzer.analyze(parse("f() returns r:int {}"));
        check(snapshot.size() == EXPECTED_INDEPENDENT_ERRORS, "Diagnostics must be returned as a snapshot");
    }

    public static void main(String[] args) throws Exception {
        positive();
        negative();
        recoveryAndAnnotations();

        SemanticAnalyzer analyzer = new SemanticAnalyzer();
        List<SemanticAnalyzer.Diagnostic> errors = analyzer.analyze(parse(Files.readString(Path.of("in/test.funny"))));
        check(errors.size() == EXPECTED_INDEX_ERRORS, "Only the two documented multi-index cases may fail: " + errors);
        check(errors.stream().allMatch(error -> error.getMessage().contains("Cannot index value of type int")),
                "Unexpected errors in the syntax fixture: " + errors);

        System.out.println("Semantic analyzer checks passed");
    }
}
