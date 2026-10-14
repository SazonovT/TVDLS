import java.util.ArrayList;
import java.util.List;

/**
 * Парсер языка Funny: рекурсивный спуск, по конструкции LL(1).
 *
 * Приведение грамматики к LL(1)-виду:
 *  1. Левая рекурсия в expr/condition/predicate устранена каскадом приоритетов
 *     с праворекурсивными «хвостами» (sumTail/prodTail и циклы while для and/or),
 *     что даёт левую ассоциативность без изменения дерева разбора.
 *  2. Импликация "->" правоассоциативна — разворачивается рекурсией вправо.
 *  3. Dangling else разрешён стандартным соглашением: else прикрепляется к ближайшему if
 *     (правило "else statement" берётся тем же самым if, ветка else не выносится наверх).
 *  4. FIRST-множества всех альтернатив различимы одним токеном, кроме трёх мест,
 *     где грамматикаFunny требует LL(k)/откат (реализованы через TokenStream.peek/mark/rewind):
 *     a) начало модуля: формула "not(...) => P" против функции (ключевое слово как имя);
 *     b) предикат: formulaRef "f(args)" против сравнения "expr op expr" (оба начинаются с IDENT);
 *     c) присваивание: v=e / v[i]=e / v1,v2=f() (различаются после IDENT,peek до '=' или '[').
 *
 * Обработка ошибок: collect-and-recover. Ошибки складываются в список с позицией,
 * синхронизация (panic mode) — пропуск до ';' / '}' / EOF.
 */
public class Parser {

    public static final class ParseError {
        public final int line, column;   // 0-based, как у лексера
        public final String message;
        ParseError(int line, int column, String message) {
            this.line = line; this.column = column; this.message = message;
        }
        @Override public String toString() {
            return String.format("Syntax error at %d:%d: %s", line + 1, column + 1, message);
        }
    }

    /** Внутренний сигнал «сорви это правило» (для отката try/rewind). Не ошибка пользователя. */
    private static final class AbortRule extends RuntimeException {
        AbortRule() { super(null, null, false, false); }
    }

    private final TokenStream in;
    private final List<ParseError> errors = new ArrayList<>();

    public Parser(TokenStream in) {
        this.in = in;
    }

    public List<ParseError> errors() {
        return errors;
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    // ------------------------------------------------------------------
    // Утилиты
    // ------------------------------------------------------------------

    private boolean check(Lexer.TokenType t) {
        return in.type() == t;
    }

    private void expect(Lexer.TokenType t) {
        if (!check(t)) {
            error("expected " + t + ", found \"" + in.current().text + "\"");
            throw new AbortRule();
        }
        in.advance();
    }

    private void error(String msg) {
        Lexer.Token cur = in.current();
        errors.add(new ParseError(cur.line, cur.column, msg));
    }

    /** Panic-mode: пропуск токенов до границы оператора/блока. */
    private void sync() {
        while (!check(Lexer.TokenType.SEMICOLON)
                && !check(Lexer.TokenType.RBRACE)
                && !check(Lexer.TokenType.EOF)) {
            in.advance();
        }
        if (check(Lexer.TokenType.SEMICOLON)) in.advance();
    }

    private String ident() {
        if (!check(Lexer.TokenType.IDENT)) {
            error("expected identifier, found \"" + in.current().text + "\"");
            throw new AbortRule();
        }
        String name = in.current().text;
        in.advance();
        return name;
    }

    private boolean startsExpr() {
        Lexer.TokenType t = in.type();
        return t == Lexer.TokenType.NUMBER || t == Lexer.TokenType.IDENT || t == Lexer.TokenType.MINUS;
    }

    // ------------------------------------------------------------------
    // module = { definition } EOF
    // definition = function | formula
    // ------------------------------------------------------------------

    public Ast.Module parseModule() {
        Ast.Module module = new Ast.Module();
        while (!check(Lexer.TokenType.EOF)) {
            try {
                if (isFormulaStart()) {
                    module.items.add(parseFormula());
                } else if (check(Lexer.TokenType.FUNCTION)) {
                    module.items.add(parseFunction());
                } else {
                    error("expected function or formula definition, found \"" + in.current().text + "\"");
                    sync();
                }
            } catch (AbortRule e) {
                sync();
            }
        }
        return module;
    }

    /**
     * Формула: IDENT "(" vardef-list ")" "=>" predicate ";".
     * Имя формулы может совпадать с ключевым словом ("not(...) => ..."), поэтому
     * FIRST(function|formula) пересекаются. Различение — по наличию "=>" сразу
     * после закрывающей скобки параметров (расширенный lookahead, LL(k)).
     */
    private boolean isFormulaStart() {
        if (check(Lexer.TokenType.EOF)) return false;
        // Кандидат на имя формулы: любой токен-слово (IDENT или ключевое слово), за которым сразу идёт '('
        if (!isWordLike(in.type()) || in.peek(1) != Lexer.TokenType.LPAREN) return false;
        // идём до ')' , соответствующей первому '(' ; глубина только из-за вложенных скобок
        int depth = 0;
        int i = 1;
        while (true) {
            Lexer.TokenType t = in.peek(i);
            if (t == Lexer.TokenType.LPAREN) depth++;
            else if (t == Lexer.TokenType.RPAREN) {
                depth--;
                if (depth == 0) break;
            } else if (t == Lexer.TokenType.EOF || t == Lexer.TokenType.SEMICOLON
                    || t == Lexer.TokenType.RETURNS || t == Lexer.TokenType.FATARROW) {
                return false;
            }
            i++;
            if (i > 8192) return false; // аварийная защита
        }
        return in.peek(i + 1) == Lexer.TokenType.FATARROW;
    }

    private Ast.Formula parseFormula() {
        // Имя формулы может быть лексически ключевым словом ("not(...) => ...") —
        // берём текст текущего токена как имя независимо от его категории.
        if (check(Lexer.TokenType.EOF)) {
            error("expected formula name");
            throw new AbortRule();
        }
        String name = in.current().text;
        in.advance();
        Ast.Formula f = new Ast.Formula(name);
        expect(Lexer.TokenType.LPAREN);
        if (!check(Lexer.TokenType.RPAREN)) {
            f.params.addAll(parseVariableDefList(true));
        }
        expect(Lexer.TokenType.RPAREN);
        expect(Lexer.TokenType.FATARROW);
        f.body = parsePredicate();
        expect(Lexer.TokenType.SEMICOLON);
        return f;
    }

    // function = "function" IDENT "(" [vardefs] ")" ["requires" pred]
    //            "returns" vardefs ["ensures" pred] ["uses" localdefs] statement ";"
    private Ast.Function parseFunction() {
        expect(Lexer.TokenType.FUNCTION);
        Ast.Function fn = new Ast.Function(ident());
        expect(Lexer.TokenType.LPAREN);
        if (!check(Lexer.TokenType.RPAREN)) {
            fn.params.addAll(parseVariableDefList(true));
        }
        expect(Lexer.TokenType.RPAREN);

        if (check(Lexer.TokenType.REQUIRES)) {
            in.advance();
            fn.requires = parsePredicate();
        }

        expect(Lexer.TokenType.RETURNS);
        fn.returns.addAll(parseVariableDefList(true));

        if (check(Lexer.TokenType.ENSURES)) {
            in.advance();
            fn.ensures = parsePredicate();
        }

        if (check(Lexer.TokenType.USES)) {
            in.advance();
            fn.locals.addAll(parseVariableDefList(false)); // тип локальной опционален
        }

        fn.body = parseStatement();
        expect(Lexer.TokenType.SEMICOLON);
        return fn;
    }

    /** variableDef = IDENT ":" type ; localVarDef = IDENT [":" type] */
    private List<Ast.VariableDef> parseVariableDefList(boolean typed) {
        List<Ast.VariableDef> list = new ArrayList<>();
        while (true) {
            String name = ident();
            String type = null;
            if (check(Lexer.TokenType.COLON)) {
                in.advance();
                type = parseTypeName();
            } else if (typed) {
                error("expected ':' and type after parameter '" + name + "'");
                throw new AbortRule();
            }
            list.add(new Ast.VariableDef(name, type));
            if (check(Lexer.TokenType.COMMA)) in.advance();
            else return list;
        }
    }

    private String parseTypeName() {
        if (check(Lexer.TokenType.INT))       { in.advance(); return "int"; }
        if (check(Lexer.TokenType.INT_ARRAY)) { in.advance(); return "int[]"; }
        error("expected type 'int' or 'int[]', found \"" + in.current().text + "\"");
        throw new AbortRule();
    }

    // ------------------------------------------------------------------
    // statement = assignment | conditional | loop | block | assertStmt | assumeStmt
    // FIRST: ASSIGN-ветка = IDENT; остальные — по ключевым словам — не пересекаются.
    // ------------------------------------------------------------------

    private Ast.Statement parseStatement() {
        switch (in.type()) {
            case IF:     return parseIf();
            case WHILE:  return parseWhile();
            case LBRACE: return parseBlock();
            case ASSERT: { in.advance(); Ast.Statement s = new Ast.Assert(parsePredicate());
                expect(Lexer.TokenType.SEMICOLON); return s; }
            case ASSUME: { in.advance(); Ast.Statement s = new Ast.Assume(parsePredicate());
                expect(Lexer.TokenType.SEMICOLON); return s; }
            default:     return parseAssignment();
        }
    }

    /**
     * assignment = IDENT "=" expr ";"
     *            | arrayAccessLHS "=" expr ";"
     *            | IDENT {"," IDENT} "=" functionCall ";"
     * Все варианты начинаются с IDENT; различаются сканированием до '=' (ограниченный lookahead).
     */
    private Ast.Statement parseAssignment() {
        String first = ident();

        if (check(Lexer.TokenType.ASSIGN)) {             // var = expr ";"
            in.advance();
            Ast.IntExpr value = parseExpr();
            return new Ast.Assign(first, new ArrayList<>(), value);
        }

        if (check(Lexer.TokenType.LBRACKET)) {           // a[i][j] = expr ";"
            List<Ast.IntExpr> indices = new ArrayList<>();
            do {
                in.advance();
                indices.add(parseExpr());
                expect(Lexer.TokenType.RBRACKET);
            } while (check(Lexer.TokenType.LBRACKET));
            expect(Lexer.TokenType.ASSIGN);
            Ast.IntExpr value = parseExpr();
            return new Ast.Assign(first, indices, value);
        }

        if (check(Lexer.TokenType.COMMA)) {              // v1, v2 = call ";"
            List<String> targets = new ArrayList<>();
            targets.add(first);
            while (check(Lexer.TokenType.COMMA)) {
                in.advance();
                targets.add(ident());
            }
            expect(Lexer.TokenType.ASSIGN);
            String callee = ident();
            expect(Lexer.TokenType.LPAREN);
            List<Ast.IntExpr> args = parseArgList();
            expect(Lexer.TokenType.RPAREN);
            return new Ast.TupleAssign(targets, new Ast.Call(callee, args));
        }

        error("expected '=', '[' or ',' after '" + first + "'");
        throw new AbortRule();
    }

    // conditional = "if" "(" condition ")" statement ["else" statement]
    private Ast.Statement parseIf() {
        expect(Lexer.TokenType.IF);
        expect(Lexer.TokenType.LPAREN);
        Ast.Condition cond = parseCondition();
        expect(Lexer.TokenType.RPAREN);
        Ast.Statement thenBranch = parseStatement();
        Ast.Statement elseBranch = null;
        // Dangling else: greedy match — else относится к ближайшему незакрытому if.
        if (check(Lexer.TokenType.ELSE)) {
            in.advance();
            elseBranch = parseStatement();
        }
        return new Ast.If(cond, thenBranch, elseBranch);
    }

    // while = "while" "(" condition ")" ["invariant" predicate] statement
    private Ast.Statement parseWhile() {
        expect(Lexer.TokenType.WHILE);
        expect(Lexer.TokenType.LPAREN);
        Ast.Condition cond = parseCondition();
        expect(Lexer.TokenType.RPAREN);
        Ast.While w = new Ast.While(cond, null);
        if (check(Lexer.TokenType.INVARIANT)) {
            in.advance();
            w.invariant = parsePredicate();
        }
        w.body = parseStatement();
        return w;
    }

    // block = "{" { statement ";" } "}"
    private Ast.Statement parseBlock() {
        expect(Lexer.TokenType.LBRACE);
        Ast.Block b = new Ast.Block();
        while (!check(Lexer.TokenType.RBRACE) && !check(Lexer.TokenType.EOF)) {
            try {
                b.statements.add(parseStatement());
                expect(Lexer.TokenType.SEMICOLON);
            } catch (AbortRule e) {
                sync();
            }
        }
        expect(Lexer.TokenType.RBRACE);
        return b;
    }

    // ------------------------------------------------------------------
    // expr — арифметика (левая рекурсия устранена хвостовыми процедурами)
    //   sum      = product { ("+"|"-") product }
    //   product  = unary  { ("*"|"/") unary }
    //   unary    = "-" unary | primary
    //   primary  = NUMBER | IDENT ("(" args ")" | "[" expr "]"* | ε) | "(" expr ")"
    // ------------------------------------------------------------------

    private Ast.IntExpr parseExpr() {
        return parseSum();
    }

    private Ast.IntExpr parseSum() {
        Ast.IntExpr left = parseProduct();
        while (check(Lexer.TokenType.PLUS) || check(Lexer.TokenType.MINUS)) {
            Ast.Arith.Op op = check(Lexer.TokenType.PLUS) ? Ast.Arith.Op.ADD : Ast.Arith.Op.SUB;
            in.advance();
            left = new Ast.Arith(op, left, parseProduct());
        }
        return left;
    }

    private Ast.IntExpr parseProduct() {
        Ast.IntExpr left = parseUnary();
        while (check(Lexer.TokenType.STAR) || check(Lexer.TokenType.SLASH)) {
            Ast.Arith.Op op = check(Lexer.TokenType.STAR) ? Ast.Arith.Op.MUL : Ast.Arith.Op.DIV;
            in.advance();
            left = new Ast.Arith(op, left, parseUnary());
        }
        return left;
    }

    private Ast.IntExpr parseUnary() {
        if (check(Lexer.TokenType.MINUS)) {
            in.advance();
            return new Ast.Neg(parseUnary());
        }
        return parsePrimary();
    }

    private Ast.IntExpr parsePrimary() {
        if (check(Lexer.TokenType.NUMBER)) {
            long v = Long.parseLong(in.current().text);
            in.advance();
            return new Ast.NumLit(v);
        }
        if (check(Lexer.TokenType.LPAREN)) {
            in.advance();
            Ast.IntExpr e = parseExpr();
            expect(Lexer.TokenType.RPAREN);
            return e;
        }
        if (check(Lexer.TokenType.IDENT)) {
            String name = in.current().text;
            in.advance();
            if (check(Lexer.TokenType.LPAREN)) {          // functionCall
                in.advance();
                List<Ast.IntExpr> args = parseArgList();
                expect(Lexer.TokenType.RPAREN);
                return new Ast.Call(name, args);
            }
            if (check(Lexer.TokenType.LBRACKET)) {        // arrayAccess (цепочка индексов)
                List<Ast.IntExpr> idx = new ArrayList<>();
                do {
                    in.advance();
                    idx.add(parseExpr());
                    expect(Lexer.TokenType.RBRACKET);
                } while (check(Lexer.TokenType.LBRACKET));
                return new Ast.ArrayAccess(name, idx);
            }
            return new Ast.Var(name);                     // variable
        }
        error("expected expression, found \"" + in.current().text + "\"");
        throw new AbortRule();
    }

    private List<Ast.IntExpr> parseArgList() {
        List<Ast.IntExpr> args = new ArrayList<>();
        if (check(Lexer.TokenType.RPAREN)) return args;
        while (true) {
            args.add(parseExpr());
            if (check(Lexer.TokenType.COMMA)) in.advance();
            else return args;
        }
    }

    // ------------------------------------------------------------------
    // condition (в if/while): приоритеты not > and > or > -> (правоассоц.)
    //   impliesC = orC ["->" impliesC]
    //   orC      = andC {"or" andC}
    //   andC     = notC {"and" notC}
    //   notC     = "not" notC | atomC
    //   atomC    = "true" | "false" | comparison | "(" condition ")"
    // ------------------------------------------------------------------

    private Ast.Condition parseCondition() {
        Ast.Condition left = parseOr();
        if (check(Lexer.TokenType.IMPLIES)) {
            in.advance();
            return new Ast.Implies(left, parseCondition()); // рекурсия вправо => правоассоциативность
        }
        return left;
    }

    private Ast.Condition parseOr() {
        Ast.PredNode left = parseAnd();
        while (check(Lexer.TokenType.OR)) {
            in.advance();
            left = new Ast.AndOr(Ast.AndOr.Op.OR, left, parseAnd());
        }
        return (Ast.Condition) left;
    }

    private Ast.Condition parseAnd() {
        Ast.PredNode left = parseNotCond();
        while (check(Lexer.TokenType.AND)) {
            in.advance();
            left = new Ast.AndOr(Ast.AndOr.Op.AND, left, parseNotCond());
        }
        return (Ast.Condition) left;
    }

    private Ast.Condition parseNotCond() {
        if (check(Lexer.TokenType.NOT)) {
            in.advance();
            return new Ast.Not(parseNotCond());
        }
        return parseConditionAtom();
    }

    private Ast.Condition parseConditionAtom() {
        if (check(Lexer.TokenType.TRUE))  { in.advance(); return new Ast.BoolLit(true); }
        if (check(Lexer.TokenType.FALSE)) { in.advance(); return new Ast.BoolLit(false); }
        if (check(Lexer.TokenType.LPAREN)) {
            in.advance();
            Ast.Condition c = parseCondition();
            expect(Lexer.TokenType.RPAREN);
            return c;
        }
        if (startsExpr()) {                 // FIRST(comparison) = FIRST(expr)
            return parseComparison();
        }
        error("expected condition, found \"" + in.current().text + "\"");
        throw new AbortRule();
    }

    // comparison = expr cmpOp expr
    private Ast.Comparison parseComparison() {
        Ast.IntExpr left = parseExpr();
        Ast.Comparison.Op op;
        switch (in.type()) {
            case EQ:  op = Ast.Comparison.Op.EQ;  break;
            case NEQ: op = Ast.Comparison.Op.NEQ; break;
            case GE:  op = Ast.Comparison.Op.GE;  break;
            case LE:  op = Ast.Comparison.Op.LE;  break;
            case GT:  op = Ast.Comparison.Op.GT;  break;
            case LT:  op = Ast.Comparison.Op.LT;  break;
            default:
                error("expected comparison operator, found \"" + in.current().text + "\"");
                throw new AbortRule();
        }
        in.advance();
        Ast.IntExpr right = parseExpr();
        return new Ast.Comparison(op, left, right);
    }

    // ------------------------------------------------------------------
    // predicate = quantifier | formulaRef | true/false | comparison
    //           | "not" predicate | predicate ("and"|"or") predicate | "(" predicate ")"
    // (в predicate-контексте импликация отсутствует по спецификации)
    // ------------------------------------------------------------------

    private Ast.Predicate parsePredicate() {
        return (Ast.Predicate) parsePredOr();
    }

    private Ast.PredNode parsePredOr() {
        Ast.PredNode left = parsePredAnd();
        while (check(Lexer.TokenType.OR)) {
            in.advance();
            left = new Ast.AndOr(Ast.AndOr.Op.OR, left, parsePredAnd());
        }
        return left;
    }

    private Ast.PredNode parsePredAnd() {
        Ast.PredNode left = parsePredNot();
        while (check(Lexer.TokenType.AND)) {
            in.advance();
            left = new Ast.AndOr(Ast.AndOr.Op.AND, left, parsePredNot());
        }
        return left;
    }

    private Ast.PredNode parsePredNot() {
        if (check(Lexer.TokenType.NOT)) {
            in.advance();
            return new Ast.Not(parsePredNot());
        }
        return parsePredAtom();
    }

    private Ast.PredNode parsePredAtom() {
        if (check(Lexer.TokenType.TRUE))  { in.advance(); return new Ast.BoolLit(true); }
        if (check(Lexer.TokenType.FALSE)) { in.advance(); return new Ast.BoolLit(false); }
        if (check(Lexer.TokenType.FORALL) || check(Lexer.TokenType.EXISTS)) {
            return parseQuantifier();
        }
        if (check(Lexer.TokenType.LPAREN)) {
            in.advance();
            Ast.PredNode inner = parsePredOr();
            expect(Lexer.TokenType.RPAREN);
            return inner;
        }
        if (check(Lexer.TokenType.IDENT)) {
            // FIRST(formulaRef) ∩ FIRST(comparison) = {IDENT}.
            // Различие: у formulaRef второй токен '(' и после ')' нет cmpOp.
            // Используем LL(2) peek + откат.
            if (in.peek(1) == Lexer.TokenType.LPAREN) {
                int m = in.mark();
                String name = in.current().text;
                in.advance();
                in.advance(); // '('
                List<Ast.IntExpr> args = parseArgList();
                expect(Lexer.TokenType.RPAREN);
                // если дальше идёт оператор сравнения — это было выражение, а не формула?
                // По грамматике "f(x) == y" — корректное сравнение с вызовом внутри expr,
                // поэтому formulaRef принимаем только если следующий токен НЕ cmpOp.
                if (isCmpOp(in.type())) {
                    in.rewind(m);
                    return parseComparison();
                }
                return new Ast.FormulaRef(name, args);
            }
            return parseComparison();
        }
        if (startsExpr()) {                 // NUMBER / MINUS — только сравнение
            return parseComparison();
        }
        error("expected predicate, found \"" + in.current().text + "\"");
        throw new AbortRule();
    }

    /** Токены, которые могут служить именем формулы (ключевые слова — тоже идентификаторы на входе). */
    private boolean isWordLike(Lexer.TokenType t) {
        switch (t) {
            case IDENT: case FUNCTION: case REQUIRES: case RETURNS: case ENSURES: case USES:
            case IF: case ELSE: case WHILE: case INVARIANT: case ASSERT: case ASSUME:
            case TRUE: case FALSE: case NOT: case AND: case OR: case FORALL: case EXISTS:
                return true;
            default:
                return false;
        }
    }

    private boolean isCmpOp(Lexer.TokenType t) {
        return t == Lexer.TokenType.EQ || t == Lexer.TokenType.NEQ || t == Lexer.TokenType.GE
                || t == Lexer.TokenType.LE || t == Lexer.TokenType.GT || t == Lexer.TokenType.LT;
    }

    // quantifier = ("forall"|"exists") "(" variableDef ["|" variableDef]* ")" predicate
    // (по спецификации — одна переменная; запятые/несколько объявлений не допускаются)
    private Ast.Quantifier parseQuantifier() {
        Ast.Quantifier.Kind kind = check(Lexer.TokenType.FORALL)
                ? Ast.Quantifier.Kind.FORALL : Ast.Quantifier.Kind.EXISTS;
        in.advance();
        expect(Lexer.TokenType.LPAREN);
        String vname = ident();
        expect(Lexer.TokenType.COLON);
        String vtype = parseTypeName();
        Ast.VariableDef vdef = new Ast.VariableDef(vname, vtype);
        Ast.Predicate body = parsePredicate();
        expect(Lexer.TokenType.RPAREN);
        return new Ast.Quantifier(kind, vdef, body);
    }
}
