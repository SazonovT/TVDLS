import java.io.Reader;

%%

%public
%class Lexer
%unicode
%line
%column
%type Lexer.Token

%{
    public enum TokenType {
        REQUIRES, RETURNS, ENSURES, USES,
        IF, ELSE, WHILE, INVARIANT, ASSERT, ASSUME,
        TRUE, FALSE, NOT, AND, OR, FORALL, EXISTS,
        INT, INT_ARRAY, NUMBER, IDENT,
        LPAREN, RPAREN, LBRACE, RBRACE, LBRACKET, RBRACKET,
        COMMA, COLON, SEMICOLON, PIPE,
        ASSIGN, EQ, NEQ, GE, LE, GT, LT, IMPLIES, FATARROW,
        PLUS, MINUS, STAR, SLASH,
        EOF, UNKNOWN
    }

    public static class Token {
        public final TokenType type;
        public final String text;
        public final int line;
        public final int column;

        public Token(TokenType type, String text, int line, int column) {
            this.type = type; this.text = text; this.line = line; this.column = column;
        }

        @Override
        public String toString() {
            return String.format("%s \"%s\" at %d:%d", type, text, line + 1, column + 1);
        }
    }

    private Token token(TokenType type) {
        return new Token(type, yytext(), yyline, yycolumn);
    }
%}

LineTerminator = \r|\n|\r\n
InputCharacter = [^\r\n]
WhiteSpace = {LineTerminator} | [ \t\f]
Comment = "//" {InputCharacter} * {LineTerminator}?

%%

{Comment}       { }
{WhiteSpace}+   { }

"requires"      { return token(TokenType.REQUIRES); }
"returns"       { return token(TokenType.RETURNS); }
"ensures"       { return token(TokenType.ENSURES); }
"uses"          { return token(TokenType.USES); }
"if"            { return token(TokenType.IF); }
"else"          { return token(TokenType.ELSE); }
"while"         { return token(TokenType.WHILE); }
"invariant"     { return token(TokenType.INVARIANT); }
"assert"        { return token(TokenType.ASSERT); }
"assume"        { return token(TokenType.ASSUME); }
"true"          { return token(TokenType.TRUE); }
"false"         { return token(TokenType.FALSE); }
"not"           { return token(TokenType.NOT); }
"and"           { return token(TokenType.AND); }
"or"            { return token(TokenType.OR); }
"forall"        { return token(TokenType.FORALL); }
"exists"        { return token(TokenType.EXISTS); }

"int[]"         { return token(TokenType.INT_ARRAY); }
"int"           { return token(TokenType.INT); }

[0-9]+          { return token(TokenType.NUMBER); }
[a-zA-Z][a-zA-Z0-9_]* { return token(TokenType.IDENT); }

"=="            { return token(TokenType.EQ); }
"!="            { return token(TokenType.NEQ); }
">="            { return token(TokenType.GE); }
"<="            { return token(TokenType.LE); }
"->"            { return token(TokenType.IMPLIES); }
"=>"            { return token(TokenType.FATARROW); }
"="             { return token(TokenType.ASSIGN); }
"+"             { return token(TokenType.PLUS); }
"-"             { return token(TokenType.MINUS); }
"*"             { return token(TokenType.STAR); }
"/"             { return token(TokenType.SLASH); }
">"             { return token(TokenType.GT); }
"<"             { return token(TokenType.LT); }
"|"             { return token(TokenType.PIPE); }

"("             { return token(TokenType.LPAREN); }
")"             { return token(TokenType.RPAREN); }
"{"             { return token(TokenType.LBRACE); }
"}"             { return token(TokenType.RBRACE); }
"["             { return token(TokenType.LBRACKET); }
"]"             { return token(TokenType.RBRACKET); }
","             { return token(TokenType.COMMA); }
":"             { return token(TokenType.COLON); }
";"             { return token(TokenType.SEMICOLON); }

<<EOF>>         { return new Token(TokenType.EOF, "", yyline, yycolumn); }
.               { return token(TokenType.UNKNOWN); }