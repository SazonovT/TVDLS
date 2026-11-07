import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public final class Main {
    private static final int STATUS_ERROR = 1;
    private static final int STATUS_USAGE = 2;
    private static final int REQUIRED_ARGUMENTS = 1;
    private static final int OPTION_OFFSET = 1;

    private record Compilation(String mermaid, boolean hasErrors) {}

    public static void main(String[] args) {
        if (args.length < REQUIRED_ARGUMENTS) {
            System.err.println("Usage: java Main <source.funny> [-o out.mmd]");
            System.exit(STATUS_USAGE);
            return;
        }

        Path input = Paths.get(args[0]);
        Path output = null;
        for (int i = OPTION_OFFSET; i < args.length - OPTION_OFFSET; i++) {
            if ("-o".equals(args[i])) {
                output = Paths.get(args[i + OPTION_OFFSET]);
            }
        }

        if (!Files.isReadable(input)) {
            System.err.println("Cannot read file: " + input);
            System.exit(STATUS_USAGE);
            return;
        }

        Compilation compilation;
        try {
            compilation = compile(input);
        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
            System.exit(STATUS_ERROR);
            return;
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e.getMessage());
            System.exit(STATUS_ERROR);
            return;
        }

        if (compilation.mermaid() == null) {
            System.exit(STATUS_ERROR);
            return;
        }

        if (output != null) {
            try {
                Files.writeString(output, compilation.mermaid(), StandardCharsets.UTF_8);
                System.out.println("Wrote " + output);
            } catch (IOException e) {
                System.err.println("Cannot write " + output + ": " + e.getMessage());
                System.exit(STATUS_ERROR);
                return;
            }
        } else {
            System.out.println(compilation.mermaid());
        }

        if (compilation.hasErrors()) {
            System.exit(STATUS_ERROR);
        }
    }

    private static Compilation compile(Path input) throws IOException {
        try (Reader r = Files.newBufferedReader(input, StandardCharsets.UTF_8)) {
            Lexer lexer = new Lexer(r);
            TokenStream stream = new TokenStream(lexer);

            Parser parser = new Parser(stream);
            Ast.Module module = parser.parseModule();

            if (!parser.getDiagnostics().isEmpty()) {
                System.err.println("Parse errors:");
                for (Parser.Diagnostic d : parser.getDiagnostics()) {
                    System.err.println(" " + d);
                }

                return new Compilation(null, true);
            }

            SemanticAnalyzer analyzer = new SemanticAnalyzer();
            List<SemanticAnalyzer.Diagnostic> errors = analyzer.analyze(module);
            if (!errors.isEmpty()) {
                System.err.println("Semantic errors:");
                for (SemanticAnalyzer.Diagnostic diagnostic : errors) {
                    System.err.println(" " + diagnostic);
                }
            }

            return new Compilation(new MermaidAstPrinter().toMermaid(module), !errors.isEmpty());
        }
    }

}
