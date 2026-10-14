import java.io.Reader;

%%

%public
%class Lexer
%unicode
%line
%column
%type Token

%{
    public enum TokenType {
        // Keywords
        FUNCTION, REQUIRES, RETURNS, ENSURES, USES,
        IF, ELSE, WHILE, INVARIANT,
        ASSERT, ASSUME,
        // Boolean literals and logical connectives
        TRUE, FALSE, NOT, AND, OR,
        FORALL, EXISTS,
        // Types
        INT, INT_ARRAY,
        // Literals and identifiers
        NUMBER, IDENT,
        // Delimiters
        LPAREN, RPAREN, LBRACE, RBRACE, LBRACKET, RBRACKET,
        COMMA, COLON, SEMICOLON, BAR,
        // Operators
        ASSIGN, EQ, NEQ, GE, LE, GT, LT,
        IMPLIES, FATARROW,
        PLUS, MINUS, STAR, SLASH,
        // Service tokens
        EOF, UNKNOWN
    }

    public class Token {
        public final TokenType type;
        public final String text;
        public final int line;    // 0-based
        public final int column;  // 0-based

        public Token(TokenType type) {
            this.type   = type;
            this.text   = yytext();
            this.line   = yyline;
            this.column = yycolumn;
        }

        @Override
        public String toString() {
            return String.format("%s \"%s\" at %d: %d",
                    type, text, line + 1, column + 1);
        }
    }
%}

LineTerminator = \r|\n|\r\n
InputCharacter = [^\r\n]
WhiteSpace     = {LineTerminator} | [ \t\f]
Comment        = "//" {InputCharacter}* {LineTerminator}?

%%

// Comment and white space
{Comment}      {}
{WhiteSpace}+  {}

// Keywords
"function"     { return new Token(TokenType.FUNCTION); }
"requires"     { return new Token(TokenType.REQUIRES); }
"returns"      { return new Token(TokenType.RETURNS); }
"ensures"      { return new Token(TokenType.ENSURES); }
"uses"         { return new Token(TokenType.USES); }
"if"           { return new Token(TokenType.IF); }
"else"         { return new Token(TokenType.ELSE); }
"while"        { return new Token(TokenType.WHILE); }
"invariant"    { return new Token(TokenType.INVARIANT); }
"assert"       { return new Token(TokenType.ASSERT); }
"assume"       { return new Token(TokenType.ASSUME); }
"true"         { return new Token(TokenType.TRUE); }
"false"        { return new Token(TokenType.FALSE); }
"not"          { return new Token(TokenType.NOT); }
"and"          { return new Token(TokenType.AND); }
"or"           { return new Token(TokenType.OR); }
"forall"       { return new Token(TokenType.FORALL); }
"exists"       { return new Token(TokenType.EXISTS); }

// Types
"int[]"        { return new Token(TokenType.INT_ARRAY); }
"int"          { return new Token(TokenType.INT); }

// Literals and identifiers
[0-9]+                    { return new Token(TokenType.NUMBER); }
[a-zA-Z][a-zA-Z0-9_]*      { return new Token(TokenType.IDENT); }

// Operators
"=="           { return new Token(TokenType.EQ); }
"!="           { return new Token(TokenType.NEQ); }
">="           { return new Token(TokenType.GE); }
"<="           { return new Token(TokenType.LE); }
"->"           { return new Token(TokenType.IMPLIES); }
"=>"           { return new Token(TokenType.FATARROW); }
"="            { return new Token(TokenType.ASSIGN); }
"+"            { return new Token(TokenType.PLUS); }
"-"            { return new Token(TokenType.MINUS); }
"*"            { return new Token(TokenType.STAR); }
"/"            { return new Token(TokenType.SLASH); }
">"            { return new Token(TokenType.GT); }
"<"            { return new Token(TokenType.LT); }

// Delimiters
"("            { return new Token(TokenType.LPAREN); }
")"            { return new Token(TokenType.RPAREN); }
"{"            { return new Token(TokenType.LBRACE); }
"}"            { return new Token(TokenType.RBRACE); }
"["            { return new Token(TokenType.LBRACKET); }
"]"            { return new Token(TokenType.RBRACKET); }
","            { return new Token(TokenType.COMMA); }
"|"            { return new Token(TokenType.BAR); }
":"            { return new Token(TokenType.COLON); }
";"            { return new Token(TokenType.SEMICOLON); }

// End of input
<<EOF>>        { return new Token(TokenType.EOF); }

// Unknown
.              { return new Token(TokenType.UNKNOWN); }