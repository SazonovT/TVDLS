import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProgramsTest {

    @Test
    void functionHeader() throws Exception {
        String src = "function gcd(a: int, b: int)";
        LexerTestSupport.assertTypes(src,
                Lexer.TokenType.FUNCTION,
                Lexer.TokenType.IDENT,
                Lexer.TokenType.LPAREN,
                Lexer.TokenType.IDENT,
                Lexer.TokenType.COLON,
                Lexer.TokenType.INT,
                Lexer.TokenType.COMMA,
                Lexer.TokenType.IDENT,
                Lexer.TokenType.COLON,
                Lexer.TokenType.INT,
                Lexer.TokenType.RPAREN);
    }

    @Test
    void fullFunctionWithRequiresAndEnsures() throws Exception {
        String src = """
                function gcd(a: int, b: int)
                    requires a >= 0 and b >= 0
                    returns r: int
                    ensures r > 0
                    uses x: int, y: int
                {
                    x = a;
                    y = b;
                }
                """;
        List<Lexer.TokenType> ts = LexerTestSupport.types(src);

        assertEquals(Lexer.TokenType.FUNCTION, ts.get(0));
        assertEquals(Lexer.TokenType.REQUIRES, ts.get(11));
        assertEquals(Lexer.TokenType.GE, ts.get(13));
        assertEquals(Lexer.TokenType.AND, ts.get(15));
        int n = ts.size();
        assertEquals(Lexer.TokenType.SEMICOLON, ts.get(n - 2));
    }

    @Test
    void whileWithInvariant() throws Exception {
        String src = """
                while (x != y)
                    invariant x >= 0
                {
                    x = x - 1;
                }
                """;
        LexerTestSupport.assertTypes(src,
                Lexer.TokenType.WHILE,
                Lexer.TokenType.LPAREN,
                Lexer.TokenType.IDENT,
                Lexer.TokenType.NEQ,
                Lexer.TokenType.IDENT,
                Lexer.TokenType.RPAREN,
                Lexer.TokenType.INVARIANT,
                Lexer.TokenType.IDENT,
                Lexer.TokenType.GE,
                Lexer.TokenType.NUMBER,
                Lexer.TokenType.LBRACE,
                Lexer.TokenType.IDENT,
                Lexer.TokenType.ASSIGN,
                Lexer.TokenType.IDENT,
                Lexer.TokenType.MINUS,
                Lexer.TokenType.NUMBER,
                Lexer.TokenType.SEMICOLON,
                Lexer.TokenType.RBRACE);
    }

    @Test
    void forallFormula() throws Exception {
        String src = "forall (i: int | i >= 0 -> a[i] <= a[i + 1])";
        LexerTestSupport.assertTypes(src,
                Lexer.TokenType.FORALL,
                Lexer.TokenType.LPAREN,
                Lexer.TokenType.IDENT,
                Lexer.TokenType.COLON,
                Lexer.TokenType.INT,
                Lexer.TokenType.UNKNOWN,
                Lexer.TokenType.IDENT,
                Lexer.TokenType.GE,
                Lexer.TokenType.NUMBER,
                Lexer.TokenType.IMPLIES,
                Lexer.TokenType.IDENT,
                Lexer.TokenType.LBRACKET,
                Lexer.TokenType.IDENT,
                Lexer.TokenType.RBRACKET,
                Lexer.TokenType.LE,
                Lexer.TokenType.IDENT,
                Lexer.TokenType.LBRACKET,
                Lexer.TokenType.IDENT,
                Lexer.TokenType.PLUS,
                Lexer.TokenType.NUMBER,
                Lexer.TokenType.RBRACKET,
                Lexer.TokenType.RPAREN);
    }

    @Test
    void formulaDefinition() throws Exception {
        String src = "formula Sorted(a: int[], n: int) => a[0] <= a[1];";
        LexerTestSupport.assertTypes(src,
                Lexer.TokenType.IDENT,
                Lexer.TokenType.IDENT,
                Lexer.TokenType.LPAREN,
                Lexer.TokenType.IDENT,
                Lexer.TokenType.COLON,
                Lexer.TokenType.INT_ARRAY,
                Lexer.TokenType.COMMA,
                Lexer.TokenType.IDENT,
                Lexer.TokenType.COLON,
                Lexer.TokenType.INT,
                Lexer.TokenType.RPAREN,
                Lexer.TokenType.FATARROW,
                Lexer.TokenType.IDENT,
                Lexer.TokenType.LBRACKET,
                Lexer.TokenType.NUMBER,
                Lexer.TokenType.RBRACKET,
                Lexer.TokenType.LE,
                Lexer.TokenType.IDENT,
                Lexer.TokenType.LBRACKET,
                Lexer.TokenType.NUMBER,
                Lexer.TokenType.RBRACKET,
                Lexer.TokenType.SEMICOLON);
    }

    @Test
    void arrayUpdateSugar() throws Exception {
        LexerTestSupport.assertTypes("a[i] = v;",
                Lexer.TokenType.IDENT,
                Lexer.TokenType.LBRACKET,
                Lexer.TokenType.IDENT,
                Lexer.TokenType.RBRACKET,
                Lexer.TokenType.ASSIGN,
                Lexer.TokenType.IDENT,
                Lexer.TokenType.SEMICOLON);
    }

    @Test
    void nestedArrayUpdate() throws Exception {
        LexerTestSupport.assertTypes("a[i][j] = v;",
                Lexer.TokenType.IDENT,
                Lexer.TokenType.LBRACKET,
                Lexer.TokenType.IDENT,
                Lexer.TokenType.RBRACKET,
                Lexer.TokenType.LBRACKET,
                Lexer.TokenType.IDENT,
                Lexer.TokenType.RBRACKET,
                Lexer.TokenType.ASSIGN,
                Lexer.TokenType.IDENT,
                Lexer.TokenType.SEMICOLON);
    }

    @Test
    void multiReturnAssignment() throws Exception {
        LexerTestSupport.assertTypes("r, q = divmod(a, b);",
                Lexer.TokenType.IDENT,
                Lexer.TokenType.COMMA,
                Lexer.TokenType.IDENT,
                Lexer.TokenType.ASSIGN,
                Lexer.TokenType.IDENT,
                Lexer.TokenType.LPAREN,
                Lexer.TokenType.IDENT,
                Lexer.TokenType.COMMA,
                Lexer.TokenType.IDENT,
                Lexer.TokenType.RPAREN,
                Lexer.TokenType.SEMICOLON);
    }

    @Test
    void assertAndAssume() throws Exception {
        LexerTestSupport.assertTypes("assert x > 0; assume y < 10;",
                Lexer.TokenType.ASSERT,
                Lexer.TokenType.IDENT,
                Lexer.TokenType.GT,
                Lexer.TokenType.NUMBER,
                Lexer.TokenType.SEMICOLON,
                Lexer.TokenType.ASSUME,
                Lexer.TokenType.IDENT,
                Lexer.TokenType.LT,
                Lexer.TokenType.NUMBER,
                Lexer.TokenType.SEMICOLON);
    }

    @Test
    void nestedBlocks() throws Exception {
        String src = "{ { x = 1; } { y = 2; } }";
        LexerTestSupport.assertTypes(src,
                Lexer.TokenType.LBRACE,
                Lexer.TokenType.LBRACE,
                Lexer.TokenType.IDENT,
                Lexer.TokenType.ASSIGN,
                Lexer.TokenType.NUMBER,
                Lexer.TokenType.SEMICOLON,
                Lexer.TokenType.RBRACE,
                Lexer.TokenType.LBRACE,
                Lexer.TokenType.IDENT,
                Lexer.TokenType.ASSIGN,
                Lexer.TokenType.NUMBER,
                Lexer.TokenType.SEMICOLON,
                Lexer.TokenType.RBRACE,
                Lexer.TokenType.RBRACE);
    }

    @Test
    void lineCommentInsideFunction() throws Exception {
        String src = """
                function f(x: int)
                    returns y: int
                {
                    // double the input
                    y = x * 2;
                }
                """;
        List<Lexer.Token> tokens = LexerTestSupport.lex(src);
        boolean sawStar = tokens.stream()
                .anyMatch(t -> t.type == Lexer.TokenType.STAR);
        Assertions.assertTrue(sawStar);
    }
}