import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class KeywordsTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "function,  FUNCTION",
            "requires,  REQUIRES",
            "returns,   RETURNS",
            "ensures,   ENSURES",
            "uses,      USES",
            "if,        IF",
            "else,      ELSE",
            "while,     WHILE",
            "invariant, INVARIANT",
            "assert,    ASSERT",
            "assume,    ASSUME",
            "true,      TRUE",
            "false,     FALSE",
            "not,       NOT",
            "and,       AND",
            "or,        OR",
            "forall,    FORALL",
            "exists,    EXISTS"
    })
    void keywordRecognized(String text, String expectedType) throws Exception {
        var tokens = LexerTestSupport.lex(text);
        assertEquals(1, tokens.size());
        assertEquals(Lexer.TokenType.valueOf(expectedType), tokens.get(0).type);
        assertEquals(text, tokens.get(0).text);
    }

    @Test
    void keywordPrefixBecomesIdentifier() throws Exception {
        LexerTestSupport.assertTypes("functionX",
                Lexer.TokenType.IDENT);
    }

    @Test
    void keywordInsideIdentifier() throws Exception {
        LexerTestSupport.assertTypes("myif",
                Lexer.TokenType.IDENT);
    }

    @Test
    void keywordCaseSensitive() throws Exception {
        LexerTestSupport.assertTypes("If",
                Lexer.TokenType.IDENT);
        assertNotEquals(Lexer.TokenType.IF,
                LexerTestSupport.lex("If").get(0).type);
    }

    @Test
    void sequenceOfKeywords() throws Exception {
        LexerTestSupport.assertTypes("if else while",
                Lexer.TokenType.IF,
                Lexer.TokenType.ELSE,
                Lexer.TokenType.WHILE);
    }
}