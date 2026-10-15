import java.util.ArrayList;
import java.util.List;

/**
 * Рекурсивный парсер Funny.
 *
 * Принятая грамматика (на основе Ast.java и набора токенов лексера):
 *
 *   module        := item*
 *   item          := function | formula
 *   function      := 'function' IDENT '(' paramList? ')' fnClauses block
 *   fnClauses     := ('requires' condition)?
 *                    ('returns' '(' retList ')')?
 *                    ('ensures' predicate)?
 *                    ('uses' IDENT (',' IDENT)*)?
 *   formula       := IDENT '(' paramList? ')' '=>' predicate
 *   paramList     := param (',' param)*
 *   param         := IDENT ':' type
 *   type          := 'int' | 'int[]'
 *
 *   block         := '{' blockItem* '}'
 *   blockItem     := localDecl | statement
 *   localDecl     := type IDENT ';'
 *
 *   statement     := 'if' '(' condition ')' statement ('else' statement)?
 *                  | 'while' '(' condition ')' ('invariant' predicate)? statement
 *                  | block
 *                  | 'assert' predicate ';'
 *                  | 'assume' predicate ';'
 *                  | tupleAssign | assign ';'
 *   tupleAssign   := IDENT (',' IDENT)+ '=' call
 *   assign        := IDENT index* '=' expr          // index = '[' expr ']'
 *
 *   condition     := implCond
 *   implCond      := orCond ('->' implCond)?        // right-assoc
 *   orCond        := andCond ('or' andCond)*
 *   andCond       := notCond ('and' notCond)*
 *   notCond       := '!' notCond | atomCond
 *   atomCond      := 'true' | 'false' | comparison | '(' condition ')'
 *   comparison    := expr cmpOp expr
 *
 *   predicate     := implPred
 *   implPred      := orPred ('->' implPred)?
 *   orPred        := andPred ('or' andPred)*
 *   andPred       := notPred ('and' notPred)*
 *   notPred       := '!' notPred | atomPred
 *   atomPred      := 'true' | 'false'
 *                  | quantifier
 *                  | formulaRef
 *                  | comparison
 *                  | '(' predicate ')'
 *   quantifier    := ('forall' | 'exists') IDENT (':' type)? '=>'? predicate
 *   formulaRef    := IDENT '(' exprList? ')'
 *
 *   expr          := sum
 *   sum           := product (('+' | '-') product)*
 *   product       := unary (('*' | '/') unary)*
 *   unary         := '-' unary | atom
 *   atom          := NUMBER | IDENT callArgs? index* | '(' expr ')'
 */
public final class Parser {

    // ---------- диагностика ----------

    public static final class Diagnostic {
        public final int line;
        public final int column;
        public final String message;

        public Diagnostic(int line, int column, String message) {
            this.line = line;
            this.column = column;
            this.message = message;
        }

        @Override
        public String toString() {
            return String.format("line %d, col %d: %s",
                    line + 1, column + 1, message);
        }
    }

    private static final class ParseError extends RuntimeException {
        final Diagnostic diagnostic;
        ParseError(Diagnostic d) { super(d.message); this.diagnostic = d; }
    }

    // ---------- состояние ----------

    private static final int MAX_LOOKAHEAD = 512;

    private final TokenStream ts;
    private final List<Diagnostic> diagnostics = new ArrayList<>();

    public Parser(TokenStream ts) {
        if (ts == null) throw new IllegalArgumentException("ts == null");
        this.ts = ts;
    }

    public List<Diagnostic> getDiagnostics() {
        return diagnostics;
    }

    // ---------- входная точка ----------

    public Module parseModule() {
        Module module = new Module();
        while (!at(Lexer.TokenType.EOF)) {
            try {
                module.items.add(parseItem());
            } catch (ParseError e) {
                diagnostics.add(e.diagnostic);
                recoverTopLevel();
            }
        }
        return module;
    }

    // ---------- элементы верхнего уровня ----------

    private Ast.Node parseItem() {
        TokenType t = ts.peek().type;
        if (t == TokenType.FUNCTION) return parseFunction();
        if (t == TokenType.IDENT)    return parseFormula();
        throw err("expected 'function' or formula definition");
    }

    private Function parseFunction() {
        expect(TokenType.FUNCTION);
        String name = expectIdent();

        Function fn = new Function(name);
        parseParams(fn.params);

        if (at(TokenType.REQUIRES)) {
            ts.next();
            fn.requires = parseCondition();
        }
        if (at(TokenType.RETURNS)) {
            ts.next();
            parseReturns(fn.returns);
        }
        if (at(TokenType.ENSURES)) {
            ts.next();
            fn.ensures = parsePredicate();
        }
        if (at(TokenType.USES)) {
            ts.next();
            parseUses();  // информация о зависимостях — вне AST
        }

        fn.body = parseBlock(fn.locals);
        return fn;
    }

    private Formula parseFormula() {
        String name = expectIdent();
        Formula f = new Formula(name);
        parseParams(f.params);
        expect(TokenType.FATARROW);
        f.body = parsePredicate();
        if (at(TokenType.SEMICOLON)) ts.next();
        return f;
    }

    private void parseParams(List<VariableDef> out) {
        expect(TokenType.LPAREN);
        if (!at(TokenType.RPAREN)) {
            out.add(parseParam());
            while (at(TokenType.COMMA)) { ts.next(); out.add(parseParam()); }
        }
        expect(TokenType.RPAREN);
    }

    private void parseReturns(List<VariableDef> out) {
        expect(TokenType.LPAREN);
        if (!at(TokenType.RPAREN)) {
            out.add(parseParam());
            while (at(TokenType.COMMA)) { ts.next(); out.add(parseParam()); }
        }
        expect(TokenType.RPAREN);
    }

    private VariableDef parseParam() {
        String name = expectIdent();
        expect(TokenType.COLON);
        String type = parseType();
        return new VariableDef(name, type);
    }

    private String parseType() {
        if (at(TokenType.INT))       { ts.next(); return "int"; }
        if (at(TokenType.INT_ARRAY)) { ts.next(); return "int[]"; }
        throw err("expected type ('int' or 'int[]')");
    }

    private void parseUses() {
        expectIdent();
        while (at(TokenType.COMMA)) { ts.next(); expectIdent(); }
    }

    // ---------- операторы ----------

    private Block parseBlock(List<VariableDef> localsOut) {
        expect(TokenType.LBRACE);
        Block block = new Block();
        while (!at(TokenType.RBRACE) && !at(TokenType.EOF)) {
            try {
                // объявление локальной переменной: type IDENT ';'
                if (at(TokenType.INT) || at(TokenType.INT_ARRAY)) {
                    String type = parseType();
                    String name = expectIdent();
                    expect(TokenType.SEMICOLON);
                    if (localsOut != null) {
                        localsOut.add(new VariableDef(name, type));
                    }
                    continue;
                }
                block.statements.add(parseStatement());
            } catch (ParseError e) {
                diagnostics.add(e.diagnostic);
                recoverInBlock();
            }
        }
        expect(TokenType.RBRACE);
        return block;
    }

    private Statement parseStatement() {
        TokenType t = ts.peek().type;
        if (t == TokenType.LBRACE)  return parseBlock(null);
        if (t == TokenType.IF)      return parseIf();
        if (t == TokenType.WHILE)   return parseWhile();
        if (t == TokenType.ASSERT)  return parseAssert();
        if (t == TokenType.ASSUME)  return parseAssume();
        if (t == TokenType.IDENT)   return parseAssignmentOrTuple();
        throw err("expected statement");
    }

    private Statement parseIf() {
        expect(TokenType.IF);
        expect(TokenType.LPAREN);
        Condition cond = parseCondition();
        expect(TokenType.RPAREN);
        Statement thenBranch = parseStatement();
        Statement elseBranch = null;
        if (at(TokenType.ELSE)) {
            ts.next();
            elseBranch = parseStatement();
        }
        return new If(cond, thenBranch, elseBranch);
    }

    private Statement parseWhile() {
        expect(TokenType.WHILE);
        expect(TokenType.LPAREN);
        Condition cond = parseCondition();
        expect(TokenType.RPAREN);

        While w = new While(cond, null);
        if (at(TokenType.INVARIANT)) {
            ts.next();
            w.invariant = parsePredicate();
        }
        w.body = parseStatement();
        return w;
    }

    private Statement parseAssert() {
        expect(TokenType.ASSERT);
        Predicate p = parsePredicate();
        expect(TokenType.SEMICOLON);
        return new Assert(p);
    }

    private Statement parseAssume() {
        expect(TokenType.ASSUME);
        Predicate p = parsePredicate();
        expect(TokenType.SEMICOLON);
        return new Assume(p);
    }

    /**
     * Различение tuple-assign и обычного assign — LL(2):
     *   IDENT COMMA ...            -> tuple
     *   IDENT '[' | '='  ...       -> simple/indexed assign
     */
    private Statement parseAssignmentOrTuple() {
        if (ts.peek(1).type == TokenType.COMMA) {
            List<String> targets = new ArrayList<>();
            targets.add(expectIdent());
            while (at(TokenType.COMMA)) {
                ts.next();
                targets.add(expectIdent());
            }
            expect(TokenType.ASSIGN);
            Call call = parseCallExpr();
            expect(TokenType.SEMICOLON);
            return new TupleAssign(targets, call);
        }

        String target = expectIdent();
        List<IntExpr> indices = new ArrayList<>();
        while (at(TokenType.LBRACKET)) {
            ts.next();
            indices.add(parseExpr());
            expect(TokenType.RBRACKET);
        }
        expect(TokenType.ASSIGN);
        IntExpr value = parseExpr();
        expect(TokenType.SEMICOLON);
        return new Assign(target, indices, value);
    }

    private Call parseCallExpr() {
        String name = expectIdent();
        expect(TokenType.LPAREN);
        List<IntExpr> args = new ArrayList<>();
        if (!at(TokenType.RPAREN)) {
            args.add(parseExpr());
            while (at(TokenType.COMMA)) { ts.next(); args.add(parseExpr()); }
        }
        expect(TokenType.RPAREN);
        return new Call(name, args);
    }

    // ---------- выражения ----------

    private IntExpr parseExpr() { return parseSum(); }

    private IntExpr parseSum() {
        IntExpr left = parseProduct();
        while (at(TokenType.PLUS) || at(TokenType.MINUS)) {
            Arith.Op op = at(TokenType.PLUS) ? Arith.Op.ADD : Arith.Op.SUB;
            ts.next();
            IntExpr right = parseProduct();
            left = new Arith(op, left, right);
        }
        return left;
    }

    private IntExpr parseProduct() {
        IntExpr left = parseUnary();
        while (at(TokenType.STAR) || at(TokenType.SLASH)) {
            Arith.Op op = at(TokenType.STAR) ? Arith.Op.MUL : Arith.Op.DIV;
            ts.next();
            IntExpr right = parseUnary();
            left = new Arith(op, left, right);
        }
        return left;
    }

    private IntExpr parseUnary() {
        if (at(TokenType.MINUS)) {
            ts.next();
            return new Neg(parseUnary());
        }
        return parseAtom();
    }

    private IntExpr parseAtom() {
        TokenType t = ts.peek().type;
        if (t == TokenType.NUMBER) {
            Token tok = ts.next();
            try {
                return new NumLit(Long.parseLong(tok.text));
            } catch (NumberFormatException e) {
                throw err("invalid number literal: " + tok.text);
            }
        }
        if (t == TokenType.LPAREN) {
            ts.next();
            IntExpr e = parseExpr();
            expect(TokenType.RPAREN);
            return e;
        }
        if (t == TokenType.IDENT) {
            String name = ts.next().text;
            if (at(TokenType.LPAREN)) {
                ts.next();
                List<IntExpr> args = new ArrayList<>();
                if (!at(TokenType.RPAREN)) {
                    args.add(parseExpr());
                    while (at(TokenType.COMMA)) { ts.next(); args.add(parseExpr()); }
                }
                expect(TokenType.RPAREN);
                return new Call(name, args);
            }
            if (at(TokenType.LBRACKET)) {
                List<IntExpr> indices = new ArrayList<>();
                while (at(TokenType.LBRACKET)) {
                    ts.next();
                    indices.add(parseExpr());
                    expect(TokenType.RBRACKET);
                }
                return new ArrayAccess(name, indices);
            }
            return new Var(name);
        }
        throw err("expected expression");
    }

    // ---------- условия ----------

    private Condition parseCondition() {
        Condition left = parseConditionOr();
        if (at(TokenType.IMPLIES)) {
            ts.next();
            Condition right = parseCondition();  // right-assoc
            return new Implies(left, right);
        }
        return left;
    }

    private Condition parseConditionOr() {
        Condition left = parseConditionAnd();
        while (at(TokenType.OR)) {
            ts.next();
            Condition right = parseConditionAnd();
            left = new AndOr(AndOr.Op.OR, left, right);
        }
        return left;
    }

    private Condition parseConditionAnd() {
        Condition left = parseConditionNot();
        while (at(TokenType.AND)) {
            ts.next();
            Condition right = parseConditionNot();
            left = new AndOr(AndOr.Op.AND, left, right);
        }
        return left;
    }

    private Condition parseConditionNot() {
        if (at(TokenType.NOT)) {
            ts.next();
            PredNode inner = parseConditionNot();
            return new Not(inner);
        }
        return parseConditionAtom();
    }

    private Condition parseConditionAtom() {
        if (at(TokenType.TRUE))  { ts.next(); return new BoolLit(true); }
        if (at(TokenType.FALSE)) { ts.next(); return new BoolLit(false); }
        if (at(TokenType.LPAREN)) {
            ts.next();
            Condition c = parseCondition();
            expect(TokenType.RPAREN);
            return c;
        }
        IntExpr left = parseExpr();
        Comparison.Op op = parseCmpOp();
        IntExpr right = parseExpr();
        return new Comparison(op, left, right);
    }

    // ---------- предикаты ----------

    private Predicate parsePredicate() {
        Predicate left = parsePredicateOr();
        if (at(TokenType.IMPLIES)) {
            ts.next();
            Predicate right = parsePredicate();
            return new Implies(left, right);
        }
        return left;
    }

    private Predicate parsePredicateOr() {
        Predicate left = parsePredicateAnd();
        while (at(TokenType.OR)) {
            ts.next();
            Predicate right = parsePredicateAnd();
            left = new AndOr(AndOr.Op.OR, left, right);
        }
        return left;
    }

    private Predicate parsePredicateAnd() {
        Predicate left = parsePredicateNot();
        while (at(TokenType.AND)) {
            ts.next();
            Predicate right = parsePredicateNot();
            left = new AndOr(AndOr.Op.AND, left, right);
        }
        return left;
    }

    private Predicate parsePredicateNot() {
        if (at(TokenType.NOT)) {
            ts.next();
            PredNode inner = parsePredicateNot();
            return new Not(inner);
        }
        return parsePredicateAtom();
    }

    private Predicate parsePredicateAtom() {
        TokenType t = ts.peek().type;
        if (t == TokenType.TRUE)  { ts.next(); return new BoolLit(true); }
        if (t == TokenType.FALSE) { ts.next(); return new BoolLit(false); }
        if (t == TokenType.FORALL || t == TokenType.EXISTS) {
            return parseQuantifier();
        }
        if (t == TokenType.LPAREN) {
            ts.next();
            Predicate p = parsePredicate();
            expect(TokenType.RPAREN);
            return p;
        }

        // Неоднозначность IDENT '(' ... ')': сравнение или formulaRef.
        // Решается lookahead до закрывающей скобки и следующего токена.
        if (t == TokenType.IDENT && looksLikeFormulaRef()) {
            return parseFormulaRef();
        }

        IntExpr left = parseExpr();
        Comparison.Op op = parseCmpOp();
        IntExpr right = parseExpr();
        return new Comparison(op, left, right);
    }

    /**
     * IDENT '(' ... ')' с последующим токеном, не являющимся cmpOp,
     * трактуется как formulaRef. Иначе — как начало сравнения.
     */
    private boolean looksLikeFormulaRef() {
        if (!at(TokenType.IDENT)) return false;
        if (ts.peek(1).type != TokenType.LPAREN) return false;

        int depth = 0;
        for (int i = 1; i < MAX_LOOKAHEAD; i++) {
            TokenType tt = ts.peek(i).type;
            if (tt == TokenType.EOF) return false;
            if (tt == TokenType.LPAREN) depth++;
            else if (tt == TokenType.RPAREN) {
                depth--;
                if (depth == 0) {
                    TokenType after = ts.peek(i + 1).type;
                    switch (after) {
                        case EQ: case NEQ: case GE:
                        case LE: case GT:  case LT:
                            return false;
                        default:
                            return true;
                    }
                }
            }
        }
        return false;
    }

    private FormulaRef parseFormulaRef() {
        String name = expectIdent();
        expect(TokenType.LPAREN);
        List<IntExpr> args = new ArrayList<>();
        if (!at(TokenType.RPAREN)) {
            args.add(parseExpr());
            while (at(TokenType.COMMA)) { ts.next(); args.add(parseExpr()); }
        }
        expect(TokenType.RPAREN);
        return new FormulaRef(name, args);
    }

    private Predicate parseQuantifier() {
        Quantifier.Kind kind = at(TokenType.FORALL)
                ? Quantifier.Kind.FORALL
                : Quantifier.Kind.EXISTS;
        ts.next();

        String name = expectIdent();
        String type = null;
        if (at(TokenType.COLON)) {
            ts.next();
            type = parseType();
        }
        if (at(TokenType.FATARROW)) ts.next();   // опциональный разделитель

        VariableDef var = new VariableDef(name, type);
        Predicate body = parsePredicate();
        return new Quantifier(kind, var, body);
    }

    // ---------- примитивы ----------

    private boolean at(TokenType t) {
        return ts.peek().type == t;
    }

    private Token expect(TokenType t) {
        Token cur = ts.peek();
        if (cur.type != t) {
            throw err("expected " + t + ", got " + cur.type
                    + (cur.text == null || cur.text.isEmpty() ? "" : " ('" + cur.text + "')"));
        }
        return ts.next();
    }

    private String expectIdent() {
        Token cur = ts.peek();
        if (cur.type != TokenType.IDENT) {
            throw err("expected identifier, got " + cur.type);
        }
        ts.next();
        return cur.text;
    }

    private Comparison.Op parseCmpOp() {
        switch (ts.peek().type) {
            case EQ:  ts.next(); return Comparison.Op.EQ;
            case NEQ: ts.next(); return Comparison.Op.NEQ;
            case GE:  ts.next(); return Comparison.Op.GE;
            case LE:  ts.next(); return Comparison.Op.LE;
            case GT:  ts.next(); return Comparison.Op.GT;
            case LT:  ts.next(); return Comparison.Op.LT;
            default:  throw err("expected comparison operator");
        }
    }

    private ParseError err(String msg) {
        Token cur = ts.peek();
        return new ParseError(new Diagnostic(cur.line, cur.column, msg));
    }

    // ---------- восстановление ----------

    private void recoverTopLevel() {
        while (!at(TokenType.EOF)) {
            TokenType t = ts.peek().type;
            if (t == TokenType.FUNCTION) return;
            if (t == TokenType.IDENT)    return;
            ts.next();
        }
    }

    private void recoverInBlock() {
        while (!at(TokenType.EOF) && !at(TokenType.RBRACE)) {
            if (at(TokenType.SEMICOLON)) { ts.next(); return; }
            ts.next();
        }
    }
}