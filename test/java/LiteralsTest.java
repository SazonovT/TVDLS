import org.junit.jupiter.api.Test;

class LiteralsTest {

    @Test
    void singleDigit() throws Exception {
        LexerTestSupport.assertTypes("7", Lexer.TokenType.NUMBER);
    }

    @Test
    void multiDigit() throws Exception {
        LexerTestSupport.assertTexts("12345", "12345");
    }

    @Test
    void leadingZero() throws Exception {
        LexerTestSupport.assertTexts("007", "007");
    }

    @Test
    void largeNumber() throws Exception {
        LexerTestSupport.assertTexts("2147483647", "2147483647");
    }

    @Test
    void simpleIdentifier() throws Exception {
        LexerTestSupport.assertTypes("x", Lexer.TokenType.IDENT);
    }

    @Test
    void identifierWithDigitsAndUnderscore() throws Exception {
        LexerTestSupport.assertTexts("a1_b2", "a1_b2");
    }

    @Test
    void identifierStartsOnlyWithLetter() throws Exception {
        LexerTestSupport.assertTypes("_x",
                Lexer.TokenType.UNKNOWN,
                Lexer.TokenType.IDENT);
    }

    @Test
    void numberThenIdentifier() throws Exception {
        LexerTestSupport.assertTypes("42x",
                Lexer.TokenType.NUMBER,
                Lexer.TokenType.IDENT);
    }

    @Test
    void identifierThenNumber() throws Exception {
        LexerTestSupport.assertTypes("x42",
                Lexer.TokenType.IDENT);
    }

    @Test
    void mixedSequence() throws Exception {
        LexerTestSupport.assertTexts("a 1 b 22 cc",
                "a", "1", "b", "22", "cc");
    }
}