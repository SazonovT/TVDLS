import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhitespaceAndCommentsTest {

    @Test
    void spacesAreSkipped() throws Exception {
        assertEquals(List.of(), LexerTestSupport.types("     "));
    }

    @Test
    void tabsAndNewlinesAreSkipped() throws Exception {
        assertEquals(List.of(), LexerTestSupport.types("\t\r\n \t"));
    }

    @Test
    void singleLineCommentIsSkipped() throws Exception {
        LexerTestSupport.assertTypes("// just a comment\nx",
                Lexer.TokenType.IDENT);
    }

    @Test
    void commentAtEndOfFileWithoutNewline() throws Exception {
        LexerTestSupport.assertTypes("x // trailing",
                Lexer.TokenType.IDENT);
    }

    @Test
    void commentBetweenTokens() throws Exception {
        LexerTestSupport.assertTypes("a // skip\n b",
                Lexer.TokenType.IDENT,
                Lexer.TokenType.IDENT);
    }

    @Test
    void multipleComments() throws Exception {
        String src = """
                // line one
                a // after a
                // line three
                b
                """;
        LexerTestSupport.assertTexts(src, "a", "b");
    }

    @Test
    void commentContainingOperatorsDoesNotLeakTokens() throws Exception {
        LexerTestSupport.assertTypes("x // == -> => int[] function\n y",
                Lexer.TokenType.IDENT,
                Lexer.TokenType.IDENT);
    }

    @Test
    void newlineIsSeparatorBetweenTokens() throws Exception {
        LexerTestSupport.assertTypes("a\nb",
                Lexer.TokenType.IDENT,
                Lexer.TokenType.IDENT);
    }

    @Test
    void crlfIsSkipped() throws Exception {
        LexerTestSupport.assertTypes("a\r\nb",
                Lexer.TokenType.IDENT,
                Lexer.TokenType.IDENT);
    }

    @Test
    void emptyInputProducesNoTokens() throws Exception {
        assertTrue(LexerTestSupport.lex("").isEmpty());
    }
}