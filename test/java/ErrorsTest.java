import org.junit.jupiter.api.Test;

class ErrorsTest {

    @Test
    void unknownSingleCharacter() throws Exception {
        LexerTestSupport.assertTypes("@", Lexer.TokenType.UNKNOWN);
    }

    @Test
    void unknownDoesNotStopLexing() throws Exception {
        LexerTestSupport.assertTypes("@ x",
                Lexer.TokenType.UNKNOWN,
                Lexer.TokenType.IDENT);
    }

    @Test
    void multipleUnknownsInARow() throws Exception {
        LexerTestSupport.assertTypes("@@",
                Lexer.TokenType.UNKNOWN,
                Lexer.TokenType.UNKNOWN);
    }

    @Test
    void pipeIsUnknown() throws Exception {
        LexerTestSupport.assertTypes("|", Lexer.TokenType.UNKNOWN);
    }

    @Test
    void hashIsUnknown() throws Exception {
        LexerTestSupport.assertTypes("#", Lexer.TokenType.UNKNOWN);
    }

    @Test
    void underscoreAloneIsUnknown() throws Exception {
        LexerTestSupport.assertTypes("_", Lexer.TokenType.UNKNOWN);
    }

    @Test
    void inputResumesAfterUnknown() throws Exception {
        LexerTestSupport.assertTypes("a @ b",
                Lexer.TokenType.IDENT,
                Lexer.TokenType.UNKNOWN,
                Lexer.TokenType.IDENT);
    }
}
