import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OperatorsTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "==,  EQ",
            "!=,  NEQ",
            ">=,  GE",
            "<=,  LE",
            ">,   GT",
            "<,   LT",
            "->,  IMPLIES",
            "=>,  FATARROW",
            "=,   ASSIGN",
            "+,   PLUS",
            "-,   MINUS",
            "*,   STAR",
            "/,   SLASH"
    })
    void operatorRecognized(String text, String expectedType) throws Exception {
        var tokens = LexerTestSupport.lex(text);
        Assertions.assertEquals(1, tokens.size());
        Assertions.assertEquals(
                Lexer.TokenType.valueOf(expectedType), tokens.get(0).type);
        Assertions.assertEquals(text, tokens.get(0).text);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "(,  LPAREN",
            "),  RPAREN",
            "{,  LBRACE",
            "},  RBRACE",
            "[,  LBRACKET",
            "],  RBRACKET",
            "',' , COMMA",
            ":,  COLON",
            ";,  SEMICOLON"
    })
    void delimiterRecognized(String text, String expectedType) throws Exception {
        var tokens = LexerTestSupport.lex(text);
        Assertions.assertEquals(1, tokens.size());
        Assertions.assertEquals(
                Lexer.TokenType.valueOf(expectedType), tokens.get(0).type);
    }

    @Test
    void multiCharWinsOverSingle() throws Exception {
        LexerTestSupport.assertTypes("==", Lexer.TokenType.EQ);
    }

    @Test
    void implicationVsMinusAndGt() throws Exception {
        LexerTestSupport.assertTypes("->", Lexer.TokenType.IMPLIES);
    }

    @Test
    void fatArrowVsAssignAndGt() throws Exception {
        LexerTestSupport.assertTypes("=>", Lexer.TokenType.FATARROW);
    }

    @Test
    void spacedOperators() throws Exception {
        LexerTestSupport.assertTypes("= >",
                Lexer.TokenType.ASSIGN,
                Lexer.TokenType.GT);
    }

    @Test
    void arrowWithoutSpaces() throws Exception {
        LexerTestSupport.assertTypes("a->b",
                Lexer.TokenType.IDENT,
                Lexer.TokenType.IMPLIES,
                Lexer.TokenType.IDENT);
    }

    @Test
    void notEqualVsNotAndAssign() throws Exception {
        LexerTestSupport.assertTypes("!=", Lexer.TokenType.NEQ);
    }

    @Test
    void standaloneExclamationIsUnknown() throws Exception {
        LexerTestSupport.assertTypes("!", Lexer.TokenType.UNKNOWN);
    }
}