import org.junit.jupiter.api.Test;

class TypesTest {

    @Test
    void intType() throws Exception {
        LexerTestSupport.assertTypes("int", Lexer.TokenType.INT);
    }

    @Test
    void intArrayType() throws Exception {
        LexerTestSupport.assertTypes("int[]", Lexer.TokenType.INT_ARRAY);
    }

    @Test
    void intArrayWinsOverInt() throws Exception {
        LexerTestSupport.assertTexts("int[]", "int[]");
    }

    @Test
    void intFollowedByBracket() throws Exception {
        LexerTestSupport.assertTypes("int [ ]",
                Lexer.TokenType.INT,
                Lexer.TokenType.LBRACKET,
                Lexer.TokenType.RBRACKET);
    }

    @Test
    void intArrayInsideDeclaration() throws Exception {
        LexerTestSupport.assertTypes("a: int[]",
                Lexer.TokenType.IDENT,
                Lexer.TokenType.COLON,
                Lexer.TokenType.INT_ARRAY);
    }

    @Test
    void intArrayNoSpace() throws Exception {
        LexerTestSupport.assertTexts("int[]", "int[]");
    }

    @Test
    void intArrayMalformed() throws Exception {
        LexerTestSupport.assertTypes("int[ ]",
                Lexer.TokenType.INT,
                Lexer.TokenType.LBRACKET,
                Lexer.TokenType.RBRACKET);
    }
}