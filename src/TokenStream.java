import java.io.IOException;
import java.io.Reader;

/**
 * Поток токенов с бесконечным lookahead поверх JFlex-лексера.
 *
 * LL(1)-разбор использует только current()/advance(). Методы mark()/rewind()
 * дают ограниченный lookahead (LL(k)) для мест, где одного токена недостаточно:
 *   - различение формулы "f(...) => pred" и функции "f(...) returns ..." при f == "function";
 *   - разрешение неоднозначности dangling else (наличие "else" после then-ветки).
 */
public class TokenStream {
    private static final int INITIAL_CAPACITY = 16;

    private final Lexer lexer;
    private Lexer.Token[] buf = new Lexer.Token[INITIAL_CAPACITY];
    private int start = 0;   // индекс текущего токена
    private int end = 0;     // индекс за последним загруженным токеном
    private boolean eofSeen = false;

    public TokenStream(Reader reader) {
        this.lexer = new Lexer(reader);
        ensureLoaded(0);
    }

    private void ensureLoaded(int ahead) {
        while (end <= start + ahead) {
            if (eofSeen) return;
            Lexer.Token t;
            try {
                t = lexer.yylex();
            } catch (IOException e) {
                throw new RuntimeException("Lexer error: " + e.getMessage(), e);
            }
            if (t == null || t.type == Lexer.TokenType.EOF) {
                eofSeen = true;
                if (t == null) {
                    t = syntheticEof();
                }
            }
            if (end == buf.length) {
                grow();
            }
            buf[end++] = t;
        }
    }

    private Lexer.Token syntheticEof() {
        // Token — inner-класс лексера, создать его можно только через экземпляр,
        // поэтому вызываем yylex ещё раз: после EOF он стабильно возвращает EOF-токен.
        try {
            Lexer.Token t = lexer.yylex();
            if (t != null) return t;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        throw new IllegalStateException("Lexer returned null after EOF");
    }

    private void grow() {
        Lexer.Token[] nb = new Lexer.Token[buf.length * 2];
        System.arraycopy(buf, 0, nb, 0, buf.length);
        buf = nb;
    }

    /** Текущий токен. */
    public Lexer.Token current() {
        ensureLoaded(0);
        return buf[start];
    }

    /** Тип текущего токена. */
    public Lexer.TokenType type() {
        return current().type;
    }

    /** Сдвиг на один токен вперёд (на EOF остаётся на месте). */
    public void advance() {
        if (current().type != Lexer.TokenType.EOF) {
            start++;
        }
        ensureLoaded(0);
    }

    /** Токен через i позиций от текущего (0 == current()). */
    public Lexer.Token tokenAt(int i) {
        ensureLoaded(i);
        return buf[start + i];
    }

    /** Тип токена через i позиций от текущего. */
    public Lexer.TokenType peek(int i) {
        return tokenAt(i).type;
    }

    /** Позиция (маркер) в потоке для последующего возврата. */
    public int mark() {
        return start;
    }

    /** Возврат потока к позиции, сохранённой mark(). */
    public void rewind(int marker) {
        start = marker;
    }
}
