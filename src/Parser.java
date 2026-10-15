import java.util.ArrayList;
import java.util.List;

/**
 * Рекурсивный нисходящий парсер языка Funny (LL(1) + ограниченный lookahead).
 */
public final class Parser {

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
            return String.format("line %d, col %d: %s", line + 1, column + 1, message);
        }
    }

    private static final class ParseError extends RuntimeException {
        final Diagnostic diagnostic;
        ParseError(Diagnostic d) {
            super(d.message);
            this.diagnostic = d;
        }
    }

    private static final int MAX_LOOKAHEAD = 64;

    private final TokenStream ts;
    private final List<Diagnostic> diagnostics = new ArrayList<>();

    public Parser(TokenStream ts) {
        if (ts == null) throw new IllegalArgumentException("ts == null");
        this.ts = ts;
    }

    public List<Diagnostic> getDiagnostics() {
        return diagnostics;
    }

    // ---------------- entry point ----------------

    public Ast.Module parseModule() {
        Ast.Module module = new Ast.Module();
        while (!at(Lexer.TokenType.EOF)) {
            Lexer.Token before = ts.peek();
            try {
                module.items.add(parseItem());
            } catch (ParseError e) {
                diagnostics.add(e.diagnostic);
                recoverTopLevel();
                if (ts.peek() == before) {
                    ts.next();
                }
            }
        }
        return module;
    }

    // ---------------- top level ----------------

    private Ast.Node parseItem() {
        Lexer.TokenType t = ts.peek().type;
        if (t == Lexer.TokenType.FUNCTION) return parseFunction();
        if (t == Lexer.TokenType.IDENT) return parseFormula();
        throw err("expected 'function' or formula definition, got " + describe(ts.peek()));
    }

    private Ast.Function parseFunction() {
        expect(Lexer.TokenType.FUNCTION);
        Ast.Function fn = new Ast.Function(expectIdent());

        parseParams(fn.params);

        if (at(Lexer.TokenType.REQUIRES)) {
            ts.next();
            fn.requires = parsePredicate();
        }
        if (at(Lexer.TokenType.RETURNS)) {
            ts.next();
            parseParams(fn.returns);
        }
        if (at(Lexer.TokenType.ENSURES)) {
            ts.next();
            fn.ensures = parsePredicate();
        }
        if (at(Lexer.TokenType.USES)) {
            ts.next();
            parseUses();
        }

        fn.body = parseBlock(fn.locals);
        return fn;
    }

    private Ast.Formula parseFormula() {
        Ast.Formula f = new Ast.Formula(expectIdent());
        parseParams(f.params);
        expect(Lexer.TokenType.FATARROW);
        f.body = parsePredicate();
        if (at(Lexer.TokenType.SEMICOLON)) ts.next();
        return f;
    }

    private void parseParams(List<Ast.VariableDef> out) {
        expect(Lexer.TokenType.LPAREN);
        if (!at(Lexer.TokenType.RPAREN)) {
            out.add(parseParam());
            while (at(Lexer.TokenType.COMMA)) {
                ts.next();
                out.add(parseParam());
            }
        }
        expect(Lexer.TokenType.RPAREN);
    }

    private Ast.VariableDef parseParam() {
        String name = expectIdent();
        expect(Lexer.TokenType.COLON);
        return new Ast.VariableDef(name, parseType());
    }

    private String parseType() {
        if (at(Lexer.TokenType.INT)) { ts.next(); return "int"; }
        if (at(Lexer.TokenType.INT_ARRAY)) { ts.next(); return "int[]"; }
        throw err("expected type ('int' or 'int[]'), got " + describe(ts.peek()));
    }

    private void parseUses() {
        expectIdent();
        while (at(Lexer.TokenType.COMMA)) {
            ts.next();
            expectIdent();
        }
    }

    // ---------------- statements ----------------

    private Ast.Block parseBlock(List<Ast.VariableDef> localsOut) {
        expect(Lexer.TokenType.LBRACE);
        Ast.Block block = new Ast.Block();

        while (!at(Lexer.TokenType.RBRACE) && !at(Lexer.TokenType.EOF)) {
            Lexer.Token before = ts.peek();
            try {
                if (at(Lexer.TokenType.INT) || at(Lexer.TokenType.INT_ARRAY)) {
                    String type = parseType();
                    String name = expectIdent();
                    expect(Lexer.TokenType.SEMICOLON);
                    if (localsOut != null) {
                        localsOut.add(new Ast.VariableDef(name, type));
                    }
                    continue;
                }
                block.statements.add(parseStatement());
            } catch (ParseError e) {
                diagnostics.add(e.diagnostic);
                recoverInBlock();
                if (ts.peek() == before) {
                    ts.next();
                }
            }
        }

        if (at(Lexer.TokenType.EOF)) {
            diagnostics.add(err("unexpected end of file: expected '}'").diagnostic);
            return block;
        }
        expect(Lexer.TokenType.RBRACE);
        return block;
    }

    private Ast.Statement parseStatement() {
        Lexer.TokenType t = ts.peek().type;
        if (t == Lexer.TokenType.LBRACE)  return parseBlock(null);
        if (t == Lexer.TokenType.IF)      return parseIf();
        if (t == Lexer.TokenType.WHILE)   return parseWhile();
        if (t == Lexer.TokenType.ASSERT)  return parseAssert();
        if (t == Lexer.TokenType.ASSUME)  return parseAssume();
        if (t == Lexer.TokenType.IDENT)   return parseAssignOrTuple();
        throw err("expected statement, got " + describe(ts.peek()));
    }

    private Ast.Statement parseIf() {
        expect(Lexer.TokenType.IF);
        expect(Lexer.TokenType.LPAREN);
        Ast.Condition cond = parseCondition();
        expect(Lexer.TokenType.RPAREN);

        Ast.Statement thenBranch = parseStatement();
        Ast.Statement elseBranch = null;
        if (at(Lexer.TokenType.ELSE)) {
            ts.next();
            elseBranch = parseStatement();
        }
        return new Ast.If(cond, thenBranch, elseBranch);
    }

    private Ast.Statement parseWhile() {
        expect(Lexer.TokenType.WHILE);
        expect(Lexer.TokenType.LPAREN);
        Ast.Condition cond = parseCondition();
        expect(Lexer.TokenType.RPAREN);

        Ast.While w = new Ast.While(cond, null);
        if (at(Lexer.TokenType.INVARIANT)) {
            ts.next();
            w.invariant = parsePredicate();
        }
        w.body = parseStatement();
        return w;
    }

    private Ast.Statement parseAssert() {
        expect(Lexer.TokenType.ASSERT);
        Ast.Predicate p = parsePredicate();
        expect(Lexer.TokenType.SEMICOLON);
        return new Ast.Assert(p);
    }

    private Ast.Statement parseAssume() {
        expect(Lexer.TokenType.ASSUME);
        Ast.Predicate p = parsePredicate();
        expect(Lexer.TokenType.SEMICOLON);
        return new Ast.Assume(p);
    }

    private Ast.Statement parseAssignOrTuple() {
        if (ts.peek(1).type == Lexer.TokenType.COMMA) {
            List<String> targets = new ArrayList<>();
            targets.add(expectIdent());
            while (at(Lexer.TokenType.COMMA)) {
                ts.next();
                targets.add(expectIdent());
            }
            expect(Lexer.TokenType.ASSIGN);
            Ast.Call call = parseCallExpr();
            expect(Lexer.TokenType.SEMICOLON);
            return new Ast.TupleAssign(targets, call);
        }

        String target = expectIdent();
        List<Ast.IntExpr> indices = new ArrayList<>();
        while (at(Lexer.TokenType.LBRACKET)) {
            ts.next();
            indices.add(parseExpr());
            expect(Lexer.TokenType.RBRACKET);
        }
        expect(Lexer.TokenType.ASSIGN);
        Ast.IntExpr value = parseExpr();
        expect(Lexer.TokenType.SEMICOLON);
        return new Ast.Assign(target, indices, value);
    }

    private Ast.Call parseCallExpr() {
        String name = expectIdent();
        expect(Lexer.TokenType.LPAREN);
        List<Ast.IntExpr> args = new ArrayList<>();
        if (!at(Lexer.TokenType.RPAREN)) {
            args.add(parseExpr());
            while (at(Lexer.TokenType.COMMA)) {
                ts.next();
                args.add(parseExpr());
            }
        }
        expect(Lexer.TokenType.RPAREN);
        return new Ast.Call(name, args);
    }

    // ---------------- integer expressions ----------------

    private Ast.IntExpr parseExpr() {
        return parseSum();
    }

    private Ast.IntExpr parseSum() {
        Ast.IntExpr left = parseProduct();
        while (at(Lexer.TokenType.PLUS) || at(Lexer.TokenType.MINUS)) {
            Ast.Arith.Op op = at(Lexer.TokenType.PLUS) ? Ast.Arith.Op.ADD : Ast.Arith.Op.SUB;
            ts.next();
            left = new Ast.Arith(op, left, parseProduct());
        }
        return left;
    }

    private Ast.IntExpr parseProduct() {
        Ast.IntExpr left = parseUnary();
        while (at(Lexer.TokenType.STAR) || at(Lexer.TokenType.SLASH)) {
            Ast.Arith.Op op = at(Lexer.TokenType.STAR) ? Ast.Arith.Op.MUL : Ast.Arith.Op.DIV;
            ts.next();
            left = new Ast.Arith(op, left, parseUnary());
        }
        return left;
    }

    private Ast.IntExpr parseUnary() {
        if (at(Lexer.TokenType.MINUS)) {
            ts.next();
            return new Ast.Neg(parseUnary());
        }
        return parseAtom();
    }

    private Ast.IntExpr parseAtom() {
        Lexer.TokenType t = ts.peek().type;

        if (t == Lexer.TokenType.NUMBER) {
            Lexer.Token tok = ts.next();
            try {
                return new Ast.NumLit(Long.parseLong(tok.text));
            } catch (NumberFormatException e) {
                throw err("invalid number literal: " + tok.text);
            }
        }

        if (t == Lexer.TokenType.LPAREN) {
            ts.next();
            Ast.IntExpr e = parseExpr();
            expect(Lexer.TokenType.RPAREN);
            return e;
        }

        if (t == Lexer.TokenType.IDENT) {
            String name = ts.next().text;

            if (at(Lexer.TokenType.LPAREN)) {
                ts.next();
                List<Ast.IntExpr> args = new ArrayList<>();
                if (!at(Lexer.TokenType.RPAREN)) {
                    args.add(parseExpr());
                    while (at(Lexer.TokenType.COMMA)) {
                        ts.next();
                        args.add(parseExpr());
                    }
                }
                expect(Lexer.TokenType.RPAREN);
                return new Ast.Call(name, args);
            }

            if (at(Lexer.TokenType.LBRACKET)) {
                List<Ast.IntExpr> indices = new ArrayList<>();
                while (at(Lexer.TokenType.LBRACKET)) {
                    ts.next();
                    indices.add(parseExpr());
                    expect(Lexer.TokenType.RBRACKET);
                }
                return new Ast.ArrayAccess(name, indices);
            }

            return new Ast.Var(name);
        }

        throw err("expected expression, got " + describe(ts.peek()));
    }

    // ---------------- conditions ----------------

    private Ast.Condition parseCondition() {
        Ast.Condition left = parseConditionOr();
        if (at(Lexer.TokenType.IMPLIES)) {
            ts.next();
            return new Ast.Implies(left, parseCondition());
        }
        return left;
    }

    private Ast.Condition parseConditionOr() {
        Ast.Condition left = parseConditionAnd();
        while (at(Lexer.TokenType.OR)) {
            ts.next();
            left = new Ast.AndOr(Ast.AndOr.Op.OR, left, parseConditionAnd());
        }
        return left;
    }

    private Ast.Condition parseConditionAnd() {
        Ast.Condition left = parseConditionNot();
        while (at(Lexer.TokenType.AND)) {
            ts.next();
            left = new Ast.AndOr(Ast.AndOr.Op.AND, left, parseConditionNot());
        }
        return left;
    }

    private Ast.Condition parseConditionNot() {
        if (at(Lexer.TokenType.NOT)) {
            ts.next();
            return new Ast.Not(parseConditionNot());
        }
        return parseConditionAtom();
    }

    private Ast.Condition parseConditionAtom() {
        if (at(Lexer.TokenType.TRUE))  { ts.next(); return new Ast.BoolLit(true); }
        if (at(Lexer.TokenType.FALSE)) { ts.next(); return new Ast.BoolLit(false); }

        if (at(Lexer.TokenType.LPAREN)) {
            ts.next();
            Ast.Condition c = parseCondition();
            expect(Lexer.TokenType.RPAREN);
            return c;
        }

        Ast.IntExpr left = parseExpr();
        Ast.Comparison.Op op = parseCmpOp();
        Ast.IntExpr right = parseExpr();
        return new Ast.Comparison(op, left, right);
    }

    // ---------------- predicates ----------------

    private Ast.Predicate parsePredicate() {
        Ast.Predicate left = parsePredicateOr();
        if (at(Lexer.TokenType.IMPLIES)) {
            ts.next();
            return new Ast.Implies(left, parsePredicate());
        }
        return left;
    }

    private Ast.Predicate parsePredicateOr() {
        Ast.Predicate left = parsePredicateAnd();
        while (at(Lexer.TokenType.OR)) {
            ts.next();
            left = new Ast.AndOr(Ast.AndOr.Op.OR, left, parsePredicateAnd());
        }
        return left;
    }

    private Ast.Predicate parsePredicateAnd() {
        Ast.Predicate left = parsePredicateNot();
        while (at(Lexer.TokenType.AND)) {
            ts.next();
            left = new Ast.AndOr(Ast.AndOr.Op.AND, left, parsePredicateNot());
        }
        return left;
    }

    private Ast.Predicate parsePredicateNot() {
        if (at(Lexer.TokenType.NOT)) {
            ts.next();
            return new Ast.Not(parsePredicateNot());
        }
        return parsePredicateAtom();
    }

    private Ast.Predicate parsePredicateAtom() {
        Lexer.TokenType t = ts.peek().type;

        if (t == Lexer.TokenType.TRUE)  { ts.next(); return new Ast.BoolLit(true); }
        if (t == Lexer.TokenType.FALSE) { ts.next(); return new Ast.BoolLit(false); }
        if (t == Lexer.TokenType.FORALL || t == Lexer.TokenType.EXISTS) {
            return parseQuantifier();
        }

        if (t == Lexer.TokenType.LPAREN) {
            ts.next();
            Ast.Predicate p = parsePredicate();
            expect(Lexer.TokenType.RPAREN);
            return p;
        }

        if (t == Lexer.TokenType.IDENT && looksLikeFormulaRef()) {
            return parseFormulaRef();
        }

        Ast.IntExpr left = parseExpr();
        Ast.Comparison.Op op = parseCmpOp();
        Ast.IntExpr right = parseExpr();
        return new Ast.Comparison(op, left, right);
    }

    private boolean looksLikeFormulaRef() {
        if (ts.peek().type != Lexer.TokenType.IDENT) return false;
        if (ts.peek(1).type != Lexer.TokenType.LPAREN) return false;

        int depth = 0;
        for (int i = 1; i <= MAX_LOOKAHEAD; i++) {
            Lexer.TokenType t = ts.peek(i).type;
            switch (t) {
                case EOF:
                case SEMICOLON:
                case LBRACE:
                case RBRACE:
                case ASSIGN:
                    return false;
                case LPAREN:
                    depth++;
                    break;
                case RPAREN:
                    depth--;
                    if (depth == 0) {
                        return !isCmpOp(ts.peek(i + 1).type);
                    }
                    break;
                default:
                    break;
            }
        }
        return false;
    }

    private static boolean isCmpOp(Lexer.TokenType t) {
        switch (t) {
            case EQ: case NEQ: case GE: case LE: case GT: case LT:
                return true;
            default:
                return false;
        }
    }

    private Ast.FormulaRef parseFormulaRef() {
        String name = expectIdent();
        expect(Lexer.TokenType.LPAREN);
        List<Ast.IntExpr> args = new ArrayList<>();
        if (!at(Lexer.TokenType.RPAREN)) {
            args.add(parseExpr());
            while (at(Lexer.TokenType.COMMA)) {
                ts.next();
                args.add(parseExpr());
            }
        }
        expect(Lexer.TokenType.RPAREN);
        return new Ast.FormulaRef(name, args);
    }

    private Ast.Predicate parseQuantifier() {
        Ast.Quantifier.Kind kind = at(Lexer.TokenType.FORALL)
                ? Ast.Quantifier.Kind.FORALL
                : Ast.Quantifier.Kind.EXISTS;
        ts.next();

        String name = expectIdent();
        String type = null;
        if (at(Lexer.TokenType.COLON)) {
            ts.next();
            type = parseType();
        }
        if (at(Lexer.TokenType.FATARROW)) ts.next();

        return new Ast.Quantifier(kind, new Ast.VariableDef(name, type), parsePredicate());
    }

    // ---------------- primitives ----------------

    private boolean at(Lexer.TokenType t) {
        return ts.peek().type == t;
    }

    private Lexer.Token expect(Lexer.TokenType t) {
        Lexer.Token cur = ts.peek();
        if (cur.type != t) {
            throw err("expected " + t + ", got " + describe(cur));
        }
        return ts.next();
    }

    private String expectIdent() {
        Lexer.Token cur = ts.peek();
        if (cur.type != Lexer.TokenType.IDENT) {
            throw err("expected identifier, got " + describe(cur));
        }
        ts.next();
        return cur.text;
    }

    private Ast.Comparison.Op parseCmpOp() {
        switch (ts.peek().type) {
            case EQ:  ts.next(); return Ast.Comparison.Op.EQ;
            case NEQ: ts.next(); return Ast.Comparison.Op.NEQ;
            case GE:  ts.next(); return Ast.Comparison.Op.GE;
            case LE:  ts.next(); return Ast.Comparison.Op.LE;
            case GT:  ts.next(); return Ast.Comparison.Op.GT;
            case LT:  ts.next(); return Ast.Comparison.Op.LT;
            default:
                throw err("expected comparison operator, got " + describe(ts.peek()));
        }
    }

    private static String describe(Lexer.Token t) {
        if (t.text == null || t.text.isEmpty()) return String.valueOf(t.type);
        return t.type + " ('" + t.text + "')";
    }

    private ParseError err(String msg) {
        Lexer.Token cur = ts.peek();
        return new ParseError(new Diagnostic(cur.line, cur.column, msg));
    }

    // ---------------- error recovery ----------------

    private void recoverTopLevel() {
        while (!at(Lexer.TokenType.EOF)) {
            Lexer.TokenType t = ts.peek().type;
            if (t == Lexer.TokenType.FUNCTION) return;
            if (t == Lexer.TokenType.IDENT && ts.peek(1).type == Lexer.TokenType.LPAREN) return;
            ts.next();
        }
    }

    private void recoverInBlock() {
        while (!at(Lexer.TokenType.EOF) && !at(Lexer.TokenType.RBRACE)) {
            if (at(Lexer.TokenType.SEMICOLON)) {
                ts.next();
                return;
            }
            ts.next();
        }
    }
}