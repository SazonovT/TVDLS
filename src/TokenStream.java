import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Поток токенов с произвольным lookahead, лениво читающий токены из Lexer.
 *
 * Особенности интеграции с JFlex-лексером из Lexer.flex:
 *  - Lexer.Token — нестатический внутренний класс, поэтому экземпляры
 *    создаются через lexer.new Token(...); извне это допустимо, т.к.
 *    конструктор объявлен public.
 *  - Lexer.yylex() бросает java.io.IOException — здесь она перехватывается
 *    и трактуется как конец потока (после печати сообщения).
 *  - Токены TokenType.UNKNOWN автоматически пропускаются с диагностикой,
 *    чтобы Parser никогда не видел их и не ломался.
 */
public final class TokenStream {

    private final Lexer lexer;
    private final List<Lexer.Token> buffer = new ArrayList<>();
    private boolean eofReached = false;
    private Lexer.Token eofToken;
    private int position = 0;
    private final List<Integer> marks = new ArrayList<>();

    public TokenStream(Lexer lexer) {
        if (lexer == null) throw new IllegalArgumentException("lexer == null");
        this.lexer = lexer;
    }

    // ---------- публичный API ----------

    public Lexer.Token peek() { return peek(0); }

    public Lexer.Token peek(int i) {
        if (i < 0) throw new IllegalArgumentException("lookahead must be >= 0");
        ensure(position + i);
        return buffer.get(position + i);
    }

    public Lexer.Token next() {
        Lexer.Token t = peek();
        if (t.type != Lexer.TokenType.EOF) position++;
        return t;
    }

    /** Синоним next(); оставлен для совместимости с существующим Parser. */
    public Lexer.Token advance() { return next(); }

    public boolean isEof() { return peek().type == Lexer.TokenType.EOF; }

    public void mark() { marks.add(position); }

    public void rewind() {
        if (marks.isEmpty()) throw new IllegalStateException("no mark to rewind");
        position = marks.remove(marks.size() - 1);
    }

    public void commit() {
        if (marks.isEmpty()) throw new IllegalStateException("no mark to commit");
        marks.remove(marks.size() - 1);
    }

    // ---------- внутреннее ----------

    private void ensure(int idx) {
        while (buffer.size() <= idx) {
            if (eofReached) {
                // после EOF любой запрос обслуживается ссылкой на один и тот же токен
                buffer.add(eofToken);
                continue;
            }

            Lexer.Token t = readNext();

            if (t == null || t.type == Lexer.TokenType.EOF) {
                eofToken = (t != null) ? t : makeEof();
                eofReached = true;
                buffer.add(eofToken);
                continue;
            }
            if (t.type == Lexer.TokenType.UNKNOWN) {
                System.err.printf("lex: unknown token '%s' at line %d, col %d%n",
                        t.text, t.line + 1, t.column + 1);
                continue;
            }
            buffer.add(t);
        }
    }

    private Lexer.Token readNext() {
        try {
            return lexer.yylex();
        } catch (IOException e) {
            System.err.println("lexer I/O error: " + e.getMessage());
            return null;
        }
    }

    /**
     * Аварийный EOF-токен (только если yylex() вернул null из-за IOException).
     * Создаётся через внешний экземпляр Lexer, т.к. Token — inner class;
     * конструктор Token использует текущие yytext()/yyline/yycolumn лексера.
     */
    private Lexer.Token makeEof() {
        return lexer.new Token(Lexer.TokenType.EOF);
    }
}