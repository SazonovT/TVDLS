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
        Lexer.Token moduleStart = ts.peek();
        Ast.Module module = new Ast.Module(moduleStart.line, moduleStart.column);
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
        Lexer.Token start = ts.peek();
        String name = expectIdent();
        expect(Lexer.TokenType.LPAREN);
        List<Ast.VariableDef> params = parseVariableDefs();
        expect(Lexer.TokenType.RPAREN);

        if (at(Lexer.TokenType.FATARROW)) {
            return parseFormulaBody(name, params, start);
        } else {
            return parseFunctionBody(name, params, start);
        }
    }

    private Ast.Formula parseFormulaBody(String name, List<Ast.VariableDef> params, Lexer.Token start) {
        Ast.Formula f = new Ast.Formula(name, start.line, start.column);
        f.params = params;
        expect(Lexer.TokenType.FATARROW);
        f.body = parsePredicate();
        if (at(Lexer.TokenType.SEMICOLON)) {
            ts.next();
        }
        return f;
    }

    private Ast.Function parseFunctionBody(String name, List<Ast.VariableDef> params, Lexer.Token start) {
        Ast.Function fn = new Ast.Function(name, start.line, start.column);
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
        Lexer.Token start = ts.peek();
        String name = expectIdent();
        expect(Lexer.TokenType.COLON);
        return new Ast.VariableDef(name, parseType(), start.line, start.column);
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
        Lexer.Token start = ts.peek();
        String name = expectIdent();
        String type = null;
        if (at(Lexer.TokenType.COLON)) {
            ts.next();
            type = parseType();
        }
        return new Ast.VariableDef(name, type, start.line, start.column);
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
        Lexer.Token start = ts.peek();
        expect(Lexer.TokenType.LBRACE);
        Ast.Block block = new Ast.Block(start.line, start.column);
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
        Lexer.Token start = ts.peek();
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
        return new Ast.If(cond, thenBranch, elseBranch, start.line, start.column);
    }

    private Ast.Statement parseWhile() {
        Lexer.Token start = ts.peek();
        expect(Lexer.TokenType.WHILE);
        expect(Lexer.TokenType.LPAREN);
        Ast.Condition cond = parseCondition();
        expect(Lexer.TokenType.RPAREN);
        Ast.While w = new Ast.While(cond, null, start.line, start.column);
        if (at(Lexer.TokenType.INVARIANT)) {
            ts.next();
            w.invariant = parsePredicate();
        }
        w.body = parseStatement();
        return w;
    }

    private Ast.Statement parseAssert() {
        Lexer.Token start = ts.peek();
        expect(Lexer.TokenType.ASSERT);
        Ast.Predicate p = parsePredicate();
        expect(Lexer.TokenType.SEMICOLON);
        return new Ast.Assert(p, start.line, start.column);
    }

    private Ast.Statement parseAssume() {
        Lexer.Token start = ts.peek();
        expect(Lexer.TokenType.ASSUME);
        Ast.Predicate p = parsePredicate();
        expect(Lexer.TokenType.SEMICOLON);
        return new Ast.Assume(p, start.line, start.column);
    }

    private Ast.Statement parseAssignment() {
        Lexer.Token start = ts.peek();
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
            return new Ast.TupleAssign(targets, call, start.line, start.column);
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
            return new Ast.Assign(firstIdent, indices, value, start.line, start.column);
        }

        expect(Lexer.TokenType.ASSIGN);
        Ast.IntExpr value = parseExpr();
        expect(Lexer.TokenType.SEMICOLON);
        return new Ast.Assign(firstIdent, new ArrayList<>(), value, start.line, start.column);
    }

    private Ast.Call parseFunctionCall() {
        Lexer.Token start = ts.peek();
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
        return new Ast.Call(name, args, start.line, start.column);
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
            left = new Ast.Arith(op, left, parseProduct(), ((Ast.Node) left).getLine(), ((Ast.Node) left).getColumn());
        }
        return left;
    }

    private Ast.IntExpr parseProduct() {
        Ast.IntExpr left = parseUnary();
        while (at(Lexer.TokenType.STAR) || at(Lexer.TokenType.SLASH)) {
            Ast.Arith.Op op = at(Lexer.TokenType.STAR) ? Ast.Arith.Op.MUL : Ast.Arith.Op.DIV;
            ts.next();
            left = new Ast.Arith(op, left, parseUnary(), ((Ast.Node) left).getLine(), ((Ast.Node) left).getColumn());
        }
        return left;
    }

    private Ast.IntExpr parseUnary() {
        if (at(Lexer.TokenType.MINUS)) {
            Lexer.Token start = ts.peek();
            ts.next();
            return new Ast.Neg(parseUnary(), start.line, start.column);
        }
        return parseAtom();
    }

    private Ast.IntExpr parseAtom() {
        Lexer.TokenType t = ts.peek().type;
        if (t == Lexer.TokenType.NUMBER) {
            Lexer.Token tok = ts.next();
            try {
                return new Ast.NumLit(Long.parseLong(tok.text), tok.line, tok.column);
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
            Lexer.Token start = ts.next();
            String name = start.text;
            if (at(Lexer.TokenType.LPAREN)) {
                return parseFunctionCallFromName(name, start);
            }
            if (at(Lexer.TokenType.LBRACKET)) {
                List<Ast.IntExpr> indices = new ArrayList<>();
                while (at(Lexer.TokenType.LBRACKET)) {
                    ts.next();
                    indices.add(parseExpr());
                    expect(Lexer.TokenType.RBRACKET);
                }
                return new Ast.ArrayAccess(name, indices, start.line, start.column);
            }
            return new Ast.Var(name, start.line, start.column);
        }
        throw err("expected expression, got " + describe(ts.peek()));
    }

    private Ast.Call parseFunctionCallFromName(String name, Lexer.Token start) {
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
        return new Ast.Call(name, args, start.line, start.column);
    }

    // ---------------- Conditions & Predicates ----------------
    private Ast.Condition parseCondition() {
        Ast.Condition left = parseConditionOr();
        if (at(Lexer.TokenType.IMPLIES)) {
            Lexer.Token start = nodeToken((Ast.Node) left);
            ts.next();
            return new Ast.Implies(left, parseCondition(), start.line, start.column);
        }
        return left;
    }

    private Ast.Condition parseConditionOr() {
        Ast.Condition left = parseConditionAnd();
        while (at(Lexer.TokenType.OR)) {
            ts.next();
            left = new Ast.AndOr(Ast.AndOr.Op.OR, left, parseConditionAnd(),
                    ((Ast.Node) left).getLine(), ((Ast.Node) left).getColumn());
        }
        return left;
    }

    private Ast.Condition parseConditionAnd() {
        Ast.Condition left = parseConditionNot();
        while (at(Lexer.TokenType.AND)) {
            ts.next();
            left = new Ast.AndOr(Ast.AndOr.Op.AND, left, parseConditionNot(),
                    ((Ast.Node) left).getLine(), ((Ast.Node) left).getColumn());
        }
        return left;
    }

    private Ast.Condition parseConditionNot() {
        if (at(Lexer.TokenType.NOT)) {
            Lexer.Token start = ts.peek();
            ts.next();
            return new Ast.Not(parseConditionNot(), start.line, start.column);
        }
        return parseConditionAtom();
    }

    private Ast.Condition parseConditionAtom() {
        if (at(Lexer.TokenType.TRUE)) { Lexer.Token start = ts.next(); return new Ast.BoolLit(true, start.line, start.column); }
        if (at(Lexer.TokenType.FALSE)) { Lexer.Token start = ts.next(); return new Ast.BoolLit(false, start.line, start.column); }
        if (at(Lexer.TokenType.LPAREN)) {
            ts.next();
            Ast.Condition c = parseCondition();
            expect(Lexer.TokenType.RPAREN);
            return c;
        }
        Ast.IntExpr left = parseExpr();
        Ast.Comparison.Op op = parseCmpOp();
        Ast.IntExpr right = parseExpr();
        return new Ast.Comparison(op, left, right, ((Ast.Node) left).getLine(), ((Ast.Node) left).getColumn());
    }

    private Ast.Predicate parsePredicate() {
        Ast.Predicate left = parsePredicateOr();
        if (at(Lexer.TokenType.IMPLIES)) {
            Lexer.Token start = nodeToken((Ast.Node) left);
            ts.next();
            return new Ast.Implies(left, parsePredicate(), start.line, start.column);
        }
        return left;
    }

    private Ast.Predicate parsePredicateOr() {
        Ast.Predicate left = parsePredicateAnd();
        while (at(Lexer.TokenType.OR)) {
            ts.next();
            left = new Ast.AndOr(Ast.AndOr.Op.OR, left, parsePredicateAnd(),
                    ((Ast.Node) left).getLine(), ((Ast.Node) left).getColumn());
        }
        return left;
    }

    private Ast.Predicate parsePredicateAnd() {
        Ast.Predicate left = parsePredicateNot();
        while (at(Lexer.TokenType.AND)) {
            ts.next();
            left = new Ast.AndOr(Ast.AndOr.Op.AND, left, parsePredicateNot(),
                    ((Ast.Node) left).getLine(), ((Ast.Node) left).getColumn());
        }
        return left;
    }

    private Ast.Predicate parsePredicateNot() {
        if (at(Lexer.TokenType.NOT)) {
            Lexer.Token start = ts.peek();
            ts.next();
            return new Ast.Not(parsePredicateNot(), start.line, start.column);
        }
        return parsePredicateAtom();
    }

    private Ast.Predicate parsePredicateAtom() {
        Lexer.TokenType t = ts.peek().type;
        if (t == Lexer.TokenType.TRUE) { Lexer.Token start = ts.next(); return new Ast.BoolLit(true, start.line, start.column); }
        if (t == Lexer.TokenType.FALSE) { Lexer.Token start = ts.next(); return new Ast.BoolLit(false, start.line, start.column); }

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
        return new Ast.Comparison(op, left, right, ((Ast.Node) left).getLine(), ((Ast.Node) left).getColumn());
    }

    private Ast.Predicate parseQuantifier() {
        Lexer.Token start = ts.peek();
        Ast.Quantifier.Kind kind = at(Lexer.TokenType.FORALL) ? Ast.Quantifier.Kind.FORALL : Ast.Quantifier.Kind.EXISTS;
        ts.next();
        expect(Lexer.TokenType.LPAREN);
        Ast.VariableDef var = parseVariableDef();
        expect(Lexer.TokenType.PIPE);
        Ast.Predicate body = parsePredicate();
        expect(Lexer.TokenType.RPAREN);
        return new Ast.Quantifier(kind, var, body, start.line, start.column);
    }

    private Ast.FormulaRef parseFormulaRef() {
        Lexer.Token start = ts.peek();
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
        return new Ast.FormulaRef(name, args, start.line, start.column);
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
    private Lexer.Token nodeToken(Ast.Node node) {
        return new Lexer.Token(Lexer.TokenType.UNKNOWN, "", node.getLine(), node.getColumn());
    }

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