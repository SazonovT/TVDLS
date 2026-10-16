import java.util.ArrayList;
import java.util.List;

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
        if (ts == null) throw new IllegalArgumentException("TokenStream is null");
        this.ts = ts;
    }

    public List<Diagnostic> getDiagnostics() {
        return diagnostics;
    }

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

    private Ast.Node parseItem() {
        if (at(Lexer.TokenType.IDENT)) {
            return parseFunctionOrFormula();
        }
        throw err("expected function or formula definition (starting with identifier), got " + describe(ts.peek()));
    }

    private Ast.Node parseFunctionOrFormula() {
        String name = expectIdent();
        expect(Lexer.TokenType.LPAREN);
        List<Ast.VariableDef> params = parseVariableDefs();
        expect(Lexer.TokenType.RPAREN);

        if (at(Lexer.TokenType.FATARROW)) {
            return parseFormulaBody(name, params);
        } else {
            return parseFunctionBody(name, params);
        }
    }

    private Ast.Formula parseFormulaBody(String name, List<Ast.VariableDef> params) {
        Ast.Formula f = new Ast.Formula(name);
        f.params = params;
        expect(Lexer.TokenType.FATARROW);
        f.body = parsePredicate();
        if (at(Lexer.TokenType.SEMICOLON)) {
            ts.next();
        }
        return f;
    }

    private Ast.Function parseFunctionBody(String name, List<Ast.VariableDef> params) {
        Ast.Function fn = new Ast.Function(name);
        fn.params = params;

        if (at(Lexer.TokenType.REQUIRES)) {
            ts.next();
            fn.requires = parsePredicate();
        }

        expect(Lexer.TokenType.RETURNS);
        fn.returns = parseVariableDefs();

        if (at(Lexer.TokenType.ENSURES)) {
            ts.next();
            fn.ensures = parsePredicate();
        }

        if (at(Lexer.TokenType.USES)) {
            ts.next();
            fn.locals = parseLocalVarDefs();
        }

        fn.body = parseStatement();
        return fn;
    }

    // ---------------- Variable Definitions ----------------
    private List<Ast.VariableDef> parseVariableDefs() {
        List<Ast.VariableDef> res = new ArrayList<>();
        // КРИТИЧЕСКОЕ ИСПРАВЛЕНИЕ: обработка пустого списка параметров ()
        if (at(Lexer.TokenType.RPAREN)) {
            return res;
        }
        res.add(parseVariableDef());
        while (at(Lexer.TokenType.COMMA)) {
            ts.next();
            res.add(parseVariableDef());
        }
        return res;
    }

    private Ast.VariableDef parseVariableDef() {
        String name = expectIdent();
        expect(Lexer.TokenType.COLON);
        return new Ast.VariableDef(name, parseType());
    }

    private List<Ast.VariableDef> parseLocalVarDefs() {
        List<Ast.VariableDef> res = new ArrayList<>();
        res.add(parseLocalVarDef());
        while (at(Lexer.TokenType.COMMA)) {
            ts.next();
            res.add(parseLocalVarDef());
        }
        return res;
    }

    private Ast.VariableDef parseLocalVarDef() {
        String name = expectIdent();
        String type = null;
        if (at(Lexer.TokenType.COLON)) {
            ts.next();
            type = parseType();
        }
        return new Ast.VariableDef(name, type);
    }

    private String parseType() {
        if (at(Lexer.TokenType.INT)) {
            ts.next();
            return "int";
        }
        if (at(Lexer.TokenType.INT_ARRAY)) {
            ts.next();
            return "int[]";
        }
        throw err("expected type ('int' or 'int[]'), got " + describe(ts.peek()));
    }

    // ---------------- Statements ----------------
    private Ast.Statement parseStatement() {
        Lexer.TokenType t = ts.peek().type;
        if (t == Lexer.TokenType.LBRACE) return parseBlock();
        if (t == Lexer.TokenType.IF) return parseIf();
        if (t == Lexer.TokenType.WHILE) return parseWhile();
        if (t == Lexer.TokenType.ASSERT) return parseAssert();
        if (t == Lexer.TokenType.ASSUME) return parseAssume();
        if (t == Lexer.TokenType.IDENT) return parseAssignment();
        throw err("expected statement, got " + describe(ts.peek()));
    }

    private Ast.Block parseBlock() {
        expect(Lexer.TokenType.LBRACE);
        Ast.Block block = new Ast.Block();
        while (!at(Lexer.TokenType.RBRACE) && !at(Lexer.TokenType.EOF)) {
            Lexer.Token before = ts.peek();
            try {
                block.statements.add(parseStatement());
            } catch (ParseError e) {
                diagnostics.add(e.diagnostic);
                recoverInBlock();
                if (ts.peek() == before) ts.next();
            }
        }
        if (at(Lexer.TokenType.EOF)) {
            diagnostics.add(err("unexpected end of file: expected '}'").diagnostic);
            return block;
        }
        expect(Lexer.TokenType.RBRACE);
        return block;
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

    private Ast.Statement parseAssignment() {
        String firstIdent = expectIdent();

        if (at(Lexer.TokenType.COMMA)) {
            List<String> targets = new ArrayList<>();
            targets.add(firstIdent);
            while (at(Lexer.TokenType.COMMA)) {
                ts.next();
                targets.add(expectIdent());
            }
            expect(Lexer.TokenType.ASSIGN);
            Ast.Call call = parseFunctionCall();
            expect(Lexer.TokenType.SEMICOLON);
            return new Ast.TupleAssign(targets, call);
        }

        if (at(Lexer.TokenType.LBRACKET)) {
            List<Ast.IntExpr> indices = new ArrayList<>();
            while (at(Lexer.TokenType.LBRACKET)) {
                ts.next();
                indices.add(parseExpr());
                expect(Lexer.TokenType.RBRACKET);
            }
            expect(Lexer.TokenType.ASSIGN);
            Ast.IntExpr value = parseExpr();
            expect(Lexer.TokenType.SEMICOLON);
            return new Ast.Assign(firstIdent, indices, value);
        }

        expect(Lexer.TokenType.ASSIGN);
        Ast.IntExpr value = parseExpr();
        expect(Lexer.TokenType.SEMICOLON);
        return new Ast.Assign(firstIdent, new ArrayList<>(), value);
    }

    private Ast.Call parseFunctionCall() {
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

    // ---------------- Expressions ----------------
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
                return parseFunctionCallFromName(name);
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

    private Ast.Call parseFunctionCallFromName(String name) {
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

    // ---------------- Conditions & Predicates ----------------
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
        if (at(Lexer.TokenType.TRUE)) { ts.next(); return new Ast.BoolLit(true); }
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
        if (t == Lexer.TokenType.TRUE) { ts.next(); return new Ast.BoolLit(true); }
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

    private Ast.Predicate parseQuantifier() {
        Ast.Quantifier.Kind kind = at(Lexer.TokenType.FORALL) ? Ast.Quantifier.Kind.FORALL : Ast.Quantifier.Kind.EXISTS;
        ts.next();
        expect(Lexer.TokenType.LPAREN);
        Ast.VariableDef var = parseVariableDef();
        expect(Lexer.TokenType.PIPE);
        Ast.Predicate body = parsePredicate();
        expect(Lexer.TokenType.RPAREN);
        return new Ast.Quantifier(kind, var, body);
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

    private boolean looksLikeFormulaRef() {
        if (ts.peek().type != Lexer.TokenType.IDENT) return false;
        if (ts.peek(1).type != Lexer.TokenType.LPAREN) return false;
        int depth = 0;
        for (int i = 1; i <= MAX_LOOKAHEAD; i++) {
            Lexer.TokenType t = ts.peek(i).type;
            switch (t) {
                case EOF: case SEMICOLON: case LBRACE: case RBRACE: case ASSIGN:
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
        return t == Lexer.TokenType.EQ || t == Lexer.TokenType.NEQ ||
                t == Lexer.TokenType.GE || t == Lexer.TokenType.LE ||
                t == Lexer.TokenType.GT || t == Lexer.TokenType.LT;
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

    // ---------------- Primitives ----------------
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

    private static String describe(Lexer.Token t) {
        if (t.text == null || t.text.isEmpty()) return String.valueOf(t.type);
        return t.type + " ('" + t.text + "')";
    }

    private ParseError err(String msg) {
        Lexer.Token cur = ts.peek();
        return new ParseError(new Diagnostic(cur.line, cur.column, msg));
    }

    // ---------------- Error Recovery ----------------
    private void recoverTopLevel() {
        while (!at(Lexer.TokenType.EOF)) {
            Lexer.TokenType t = ts.peek().type;
            if (t == Lexer.TokenType.IDENT && ts.peek(1).type == Lexer.TokenType.LPAREN) {
                return;
            }
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