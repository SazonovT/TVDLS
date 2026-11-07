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

    private int braceDepth;
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
                module.getItems().add(parseItem());
            } catch (ParseError e) {
                diagnostics.add(e.diagnostic);
                recoverTopLevel();
                if (ts.peek() == before) {
                    nextToken();
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
        f.setParams(params);
        expect(Lexer.TokenType.FATARROW);
        f.setBody(parsePredicate());
        if (at(Lexer.TokenType.SEMICOLON)) {
            nextToken();
        }
        return f;
    }

    private Ast.Function parseFunctionBody(String name, List<Ast.VariableDef> params, Lexer.Token start) {
        Ast.Function fn = new Ast.Function(name, start.line, start.column);
        fn.setParams(params);

        if (at(Lexer.TokenType.REQUIRES)) {
            nextToken();
            fn.setRequires(parsePredicate());
        }

        expect(Lexer.TokenType.RETURNS);
        fn.setReturns(parseVariableDefs());

        if (at(Lexer.TokenType.ENSURES)) {
            nextToken();
            fn.setEnsures(parsePredicate());
        }

        if (at(Lexer.TokenType.USES)) {
            nextToken();
            fn.setLocals(parseLocalVarDefs());
        }

        fn.setBody(parseStatement());
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
            nextToken();
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
            nextToken();
            res.add(parseLocalVarDef());
        }
        return res;
    }

    private Ast.VariableDef parseLocalVarDef() {
        Lexer.Token start = ts.peek();
        String name = expectIdent();
        Ast.Type type = Ast.Type.UNKNOWN;
        if (at(Lexer.TokenType.COLON)) {
            nextToken();
            type = parseType();
        }
        return new Ast.VariableDef(name, type, start.line, start.column);
    }

    private Ast.Type parseType() {
        if (at(Lexer.TokenType.INT)) {
            nextToken();
            return Ast.Type.INT;
        }
        if (at(Lexer.TokenType.INT_ARRAY)) {
            nextToken();
            return Ast.Type.INT_ARRAY;
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
        int blockDepth = braceDepth;
        while (!at(Lexer.TokenType.RBRACE) && !at(Lexer.TokenType.EOF)) {
            Lexer.Token before = ts.peek();
            try {
                block.getStatements().add(parseStatement());
            } catch (ParseError e) {
                diagnostics.add(e.diagnostic);
                recoverInBlock(blockDepth);
                if (ts.peek() == before) nextToken();
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
            nextToken();
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
            nextToken();
            w.setInvariant(parsePredicate());
        }
        w.setBody(parseStatement());
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
                nextToken();
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
                nextToken();
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
                nextToken();
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
            nextToken();
            left = new Ast.Arith(op, left, parseProduct(), ((Ast.Node) left).getLine(), ((Ast.Node) left).getColumn());
        }
        return left;
    }

    private Ast.IntExpr parseProduct() {
        Ast.IntExpr left = parseUnary();
        while (at(Lexer.TokenType.STAR) || at(Lexer.TokenType.SLASH)) {
            Ast.Arith.Op op = at(Lexer.TokenType.STAR) ? Ast.Arith.Op.MUL : Ast.Arith.Op.DIV;
            nextToken();
            left = new Ast.Arith(op, left, parseUnary(), ((Ast.Node) left).getLine(), ((Ast.Node) left).getColumn());
        }
        return left;
    }

    private Ast.IntExpr parseUnary() {
        if (at(Lexer.TokenType.MINUS)) {
            Lexer.Token start = ts.peek();
            nextToken();
            return new Ast.Neg(parseUnary(), start.line, start.column);
        }
        return parseAtom();
    }

    private Ast.IntExpr parseAtom() {
        Lexer.TokenType t = ts.peek().type;
        if (t == Lexer.TokenType.NUMBER) {
            Lexer.Token tok = nextToken();
            try {
                return new Ast.NumLit(Long.parseLong(tok.text), tok.line, tok.column);
            } catch (NumberFormatException e) {
                throw new ParseError(new Diagnostic(tok.line, tok.column,
                        "invalid number literal: " + tok.text));
            }
        }
        if (t == Lexer.TokenType.LPAREN) {
            nextToken();
            Ast.IntExpr e = parseExpr();
            expect(Lexer.TokenType.RPAREN);
            return e;
        }
        if (t == Lexer.TokenType.IDENT) {
            Lexer.Token start = nextToken();
            String name = start.text;
            if (at(Lexer.TokenType.LPAREN)) {
                return parseFunctionCallFromName(name, start);
            }
            if (at(Lexer.TokenType.LBRACKET)) {
                List<Ast.IntExpr> indices = new ArrayList<>();
                while (at(Lexer.TokenType.LBRACKET)) {
                    nextToken();
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
                nextToken();
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
            nextToken();
            return new Ast.Implies(left, parseCondition(), start.line, start.column);
        }
        return left;
    }

    private Ast.Condition parseConditionOr() {
        Ast.Condition left = parseConditionAnd();
        while (at(Lexer.TokenType.OR)) {
            nextToken();
            left = new Ast.AndOr(Ast.AndOr.Op.OR, left, parseConditionAnd(),
                    ((Ast.Node) left).getLine(), ((Ast.Node) left).getColumn());
        }
        return left;
    }

    private Ast.Condition parseConditionAnd() {
        Ast.Condition left = parseConditionNot();
        while (at(Lexer.TokenType.AND)) {
            nextToken();
            left = new Ast.AndOr(Ast.AndOr.Op.AND, left, parseConditionNot(),
                    ((Ast.Node) left).getLine(), ((Ast.Node) left).getColumn());
        }
        return left;
    }

    private Ast.Condition parseConditionNot() {
        if (at(Lexer.TokenType.NOT)) {
            Lexer.Token start = ts.peek();
            nextToken();
            return new Ast.Not(parseConditionNot(), start.line, start.column);
        }
        return parseConditionAtom();
    }

    private Ast.Condition parseConditionAtom() {
        if (at(Lexer.TokenType.TRUE)) { Lexer.Token start = nextToken(); return new Ast.BoolLit(true, start.line, start.column); }
        if (at(Lexer.TokenType.FALSE)) { Lexer.Token start = nextToken(); return new Ast.BoolLit(false, start.line, start.column); }
        if (at(Lexer.TokenType.LPAREN) && !parenthesesStartExpression()) {
            nextToken();
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
            nextToken();
            return new Ast.Implies(left, parsePredicate(), start.line, start.column);
        }
        return left;
    }

    private Ast.Predicate parsePredicateOr() {
        Ast.Predicate left = parsePredicateAnd();
        while (at(Lexer.TokenType.OR)) {
            nextToken();
            left = new Ast.AndOr(Ast.AndOr.Op.OR, left, parsePredicateAnd(),
                    ((Ast.Node) left).getLine(), ((Ast.Node) left).getColumn());
        }
        return left;
    }

    private Ast.Predicate parsePredicateAnd() {
        Ast.Predicate left = parsePredicateNot();
        while (at(Lexer.TokenType.AND)) {
            nextToken();
            left = new Ast.AndOr(Ast.AndOr.Op.AND, left, parsePredicateNot(),
                    ((Ast.Node) left).getLine(), ((Ast.Node) left).getColumn());
        }
        return left;
    }

    private Ast.Predicate parsePredicateNot() {
        if (at(Lexer.TokenType.NOT)) {
            Lexer.Token start = ts.peek();
            nextToken();
            return new Ast.Not(parsePredicateNot(), start.line, start.column);
        }
        return parsePredicateAtom();
    }

    private Ast.Predicate parsePredicateAtom() {
        Lexer.TokenType t = ts.peek().type;
        if (t == Lexer.TokenType.TRUE) { Lexer.Token start = nextToken(); return new Ast.BoolLit(true, start.line, start.column); }
        if (t == Lexer.TokenType.FALSE) { Lexer.Token start = nextToken(); return new Ast.BoolLit(false, start.line, start.column); }

        if (t == Lexer.TokenType.FORALL || t == Lexer.TokenType.EXISTS) {
            return parseQuantifier();
        }

        if (t == Lexer.TokenType.LPAREN && !parenthesesStartExpression()) {
            nextToken();
            Ast.Predicate p = parsePredicate();
            expect(Lexer.TokenType.RPAREN);
            return p;
        }

        boolean startsWithCall = t == Lexer.TokenType.IDENT
                && ts.peek(1).type == Lexer.TokenType.LPAREN;
        Ast.IntExpr left = parseExpr();
        if (startsWithCall && left instanceof Ast.Call && !isCmpOp(ts.peek().type)) {
            Ast.Call call = (Ast.Call) left;
            return new Ast.FormulaRef(call.getName(), call.getArgs(), call.getLine(), call.getColumn());
        }
        Ast.Comparison.Op op = parseCmpOp();
        Ast.IntExpr right = parseExpr();
        return new Ast.Comparison(op, left, right, ((Ast.Node) left).getLine(), ((Ast.Node) left).getColumn());
    }

    private Ast.Predicate parseQuantifier() {
        Lexer.Token start = ts.peek();
        Ast.Quantifier.Kind kind = at(Lexer.TokenType.FORALL) ? Ast.Quantifier.Kind.FORALL : Ast.Quantifier.Kind.EXISTS;
        nextToken();
        expect(Lexer.TokenType.LPAREN);
        Ast.VariableDef var = parseVariableDef();
        expect(Lexer.TokenType.PIPE);
        Ast.Predicate body = parsePredicate();
        expect(Lexer.TokenType.RPAREN);
        return new Ast.Quantifier(kind, var, body, start.line, start.column);
    }

    // Arithmetic parentheses continue with arithmetic or a comparison.
    private boolean parenthesesStartExpression() {
        int depth = 0;
        for (int offset = 0; ; offset++) {
            switch (ts.peek(offset).type) {
                case EOF: case SEMICOLON: case LBRACE: case RBRACE: case ASSIGN:
                    return false;
                case LPAREN:
                    depth++;
                    break;
                case RPAREN:
                    if (--depth == 0) {
                        Lexer.TokenType following = ts.peek(offset + 1).type;
                        return isCmpOp(following) || following == Lexer.TokenType.PLUS
                                || following == Lexer.TokenType.MINUS
                                || following == Lexer.TokenType.STAR
                                || following == Lexer.TokenType.SLASH;
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private static boolean isCmpOp(Lexer.TokenType t) {
        return t == Lexer.TokenType.EQ || t == Lexer.TokenType.NEQ ||
                t == Lexer.TokenType.GE || t == Lexer.TokenType.LE ||
                t == Lexer.TokenType.GT || t == Lexer.TokenType.LT;
    }

    private Ast.Comparison.Op parseCmpOp() {
        switch (ts.peek().type) {
            case EQ:  nextToken(); return Ast.Comparison.Op.EQ;
            case NEQ: nextToken(); return Ast.Comparison.Op.NEQ;
            case GE:  nextToken(); return Ast.Comparison.Op.GE;
            case LE:  nextToken(); return Ast.Comparison.Op.LE;
            case GT:  nextToken(); return Ast.Comparison.Op.GT;
            case LT:  nextToken(); return Ast.Comparison.Op.LT;
            default:
                throw err("expected comparison operator, got " + describe(ts.peek()));
        }
    }

    // ---------------- Primitives ----------------
    private Lexer.Token nodeToken(Ast.Node node) {
        return new Lexer.Token(Lexer.TokenType.UNKNOWN, "", node.getLine(), node.getColumn());
    }

    private Lexer.Token nextToken() {
        Lexer.Token token = ts.next();
        if (token.type == Lexer.TokenType.LBRACE) braceDepth++;
        if (token.type == Lexer.TokenType.RBRACE) braceDepth--;
        return token;
    }

    private boolean at(Lexer.TokenType t) {
        return ts.peek().type == t;
    }

    private Lexer.Token expect(Lexer.TokenType t) {
        Lexer.Token cur = ts.peek();
        if (cur.type != t) {
            throw err("expected " + t + ", got " + describe(cur));
        }
        return nextToken();
    }

    private String expectIdent() {
        Lexer.Token cur = ts.peek();
        if (cur.type != Lexer.TokenType.IDENT) {
            throw err("expected identifier, got " + describe(cur));
        }
        nextToken();
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
            if (braceDepth <= 0 && t == Lexer.TokenType.IDENT && ts.peek(1).type == Lexer.TokenType.LPAREN) {
                return;
            }
            nextToken();
        }
    }

    private void recoverInBlock(int blockDepth) {
        while (!at(Lexer.TokenType.EOF)) {
            if (at(Lexer.TokenType.RBRACE) && braceDepth == blockDepth) return;
            if (at(Lexer.TokenType.SEMICOLON) && braceDepth == blockDepth) {
                nextToken();
                return;
            }
            Lexer.Token skipped = nextToken();
            if (skipped.type == Lexer.TokenType.RBRACE && braceDepth == blockDepth
                    && !at(Lexer.TokenType.ELSE)) return;
        }
    }
}
