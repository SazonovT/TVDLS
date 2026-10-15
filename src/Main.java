import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source.funny> [-o out.mmd]");
            System.exit(2);
        }

        Path input = Paths.get(args[0]);
        Path output = null;
        for (int i = 1; i < args.length - 1; i++) {
            if ("-o".equals(args[i])) {
                output = Paths.get(args[i + 1]);
            }
        }

        if (!Files.isReadable(input)) {
            System.err.println("Cannot read file: " + input);
            System.exit(2);
        }

        String mermaid;
        try {
            mermaid = compile(input);
        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
            System.exit(1);
            return;
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e.getMessage());
            System.exit(1);
            return;
        }

        if (output != null) {
            try {
                Files.writeString(output, mermaid, StandardCharsets.UTF_8);
                System.out.println("Wrote " + output);
            } catch (IOException e) {
                System.err.println("Cannot write " + output + ": " + e.getMessage());
                System.exit(1);
            }
        } else {
            System.out.println(mermaid);
        }
    }

    private static String compile(Path input) throws IOException {
        try (Reader r = Files.newBufferedReader(input, StandardCharsets.UTF_8)) {
            Lexer lexer = new Lexer(r);
            TokenStream stream = new TokenStream(lexer);

            Parser parser = new Parser(stream);
            Ast.Module module;

            try {
                module = parser.parseModule();
            } catch (RuntimeException e) {
                System.err.println("Parse errors:");
                for (Parser.Diagnostic d : parser.getDiagnostics()) {
                    System.err.printf(" line %d, col %d: %s%n", d.line + 1, d.column + 1, d.message);
                }
                System.exit(1);
                return "";
            }

            if (!parser.getDiagnostics().isEmpty()) {
                System.err.println("Parse warnings/errors:");
                for (Parser.Diagnostic d : parser.getDiagnostics()) {
                    System.err.printf(" line %d, col %d: %s%n", d.line + 1, d.column + 1, d.message);
                }
            }

            return new MermaidAstPrinter().toMermaid(module);
        }
    }

}