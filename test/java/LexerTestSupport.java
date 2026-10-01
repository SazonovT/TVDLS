import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class LexerTestSupport {

    private LexerTestSupport() {}

    public static List<Lexer.Token> lex(String input) throws Exception {
        Lexer lexer = new Lexer(new StringReader(input));
        List<Lexer.Token> result = new ArrayList<>();
        Lexer.Token t;
        while ((t = lexer.yylex()) != null) {
            if (t.type == Lexer.TokenType.EOF) break;
            result.add(t);
        }
        return result;
    }

    public static List<Lexer.TokenType> types(String input) throws Exception {
        List<Lexer.TokenType> ts = new ArrayList<>();
        for (Lexer.Token t : lex(input)) ts.add(t.type);
        return ts;
    }

    public static List<String> texts(String input) throws Exception {
        List<String> ts = new ArrayList<>();
        for (Lexer.Token t : lex(input)) ts.add(t.text);
        return ts;
    }

    public static void assertTypes(String input, Lexer.TokenType... expected)
            throws Exception {
        assertEquals(List.of(expected), types(input), "types for: " + input);
    }

    public static void assertTexts(String input, String... expected)
            throws Exception {
        assertEquals(List.of(expected), texts(input), "texts for: " + input);
    }
}