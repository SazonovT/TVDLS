import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TokenStream {
    private final Lexer lexer;
    private final List<Lexer.Token> buffer = new ArrayList<>();
    private int pos = 0;

    public TokenStream(Lexer lexer) {
        this.lexer = lexer;
        fetchToken();
    }

    private void fetchToken() {
        try {
            Lexer.Token token = lexer.yylex();
            if (token == null) {
                token = createSyntheticEof();
            }
            buffer.add(token);
        } catch (IOException e) {
            throw new RuntimeException("Lexer I/O error: " + e.getMessage(), e);
        }
    }

    private Lexer.Token createSyntheticEof() {
        Lexer.Token last = buffer.isEmpty() ? null : buffer.get(buffer.size() - 1);
        int line = last != null ? last.line : 0;
        int col = last != null ? last.column : 0;
        return new Lexer.Token(Lexer.TokenType.EOF, "", line, col);
    }

    public Lexer.Token peek() {
        return peek(0);
    }

    public Lexer.Token peek(int offset) {
        int targetIndex = pos + offset;
        while (targetIndex >= buffer.size()) {
            fetchToken();
        }
        return buffer.get(targetIndex);
    }

    public Lexer.Token next() {
        Lexer.Token current = peek(0);

        if (current.type == Lexer.TokenType.UNKNOWN) {
            System.err.printf("lex: unknown token '%s' at line %d, col %d%n",
                    current.text, current.line + 1, current.column + 1);
        }

        if (current.type != Lexer.TokenType.EOF) {
            pos++;
            if (pos > 1000) {
                buffer.subList(0, pos).clear();
                pos = 0;
            }
        }
        return current;
    }
}