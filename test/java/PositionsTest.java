import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PositionsTest {

    @Test
    void firstTokenAtOrigin() throws Exception {
        var t = LexerTestSupport.lex("x").get(0);
        assertEquals(0, t.line);
        assertEquals(0, t.column);
    }

    @Test
    void columnAdvancesWithinLine() throws Exception {
        List<Lexer.Token> ts = LexerTestSupport.lex("a b c");
        assertEquals(0, ts.get(0).column);
        assertEquals(2, ts.get(1).column);
        assertEquals(4, ts.get(2).column);
    }

    @Test
    void lineAdvancesAfterNewline() throws Exception {
        List<Lexer.Token> ts = LexerTestSupport.lex("a\nb\nc");
        assertEquals(0, ts.get(0).line);
        assertEquals(1, ts.get(1).line);
        assertEquals(2, ts.get(2).line);
    }

    @Test
    void columnResetsOnEachLine() throws Exception {
        List<Lexer.Token> ts = LexerTestSupport.lex("aa\nbb");
        assertEquals(0, ts.get(0).line);
        assertEquals(0, ts.get(0).column);
        assertEquals(1, ts.get(1).line);
        assertEquals(0, ts.get(1).column);
    }

    @Test
    void crlfCountsAsOneLineBreak() throws Exception {
        List<Lexer.Token> ts = LexerTestSupport.lex("a\r\nb");
        assertEquals(0, ts.get(0).line);
        assertEquals(1, ts.get(1).line);
        assertEquals(0, ts.get(1).column);
    }

    @Test
    void commentDoesNotAffectFollowingTokenPosition() throws Exception {
        List<Lexer.Token> ts = LexerTestSupport.lex("// hi\n y");
        assertEquals(1, ts.get(0).line);
        assertEquals(1, ts.get(0).column);
    }

    @Test
    void tokenTextMatchesInput() throws Exception {
        List<Lexer.Token> ts = LexerTestSupport.lex("foo bar");
        assertEquals("foo", ts.get(0).text);
        assertEquals("bar", ts.get(1).text);
    }
}