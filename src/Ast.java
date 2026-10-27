import java.util.ArrayList;
import java.util.List;

/**
 * AST языка Funny.
 */
public final class Ast {

    public abstract static class Node {
        public final int line;
        public final int column;

        protected Node(int line, int column) {
            this.line = line;
            this.column = column;
        }

        public int getLine() { return line; }
        public int getColumn() { return column; }
    }

    public abstract static class IntExpr extends Node {
        protected IntExpr(int line, int column) { super(line, column); }
    }
    public abstract static class PredNode extends Node {
        protected PredNode(int line, int column) { super(line, column); }
    }

    public static final class Module extends Node {
        public final List<Node> items = new ArrayList<>(); // Function | Formula
        public Module(int line, int column) { super(line, column); }
    }

    public static final class VariableDef extends Node {
        public final String name;
        public final String type; // "int" | "int[]" | null
        public VariableDef(String name, String type, int line, int column) { super(line, column); this.name = name; this.type = type; }
    }

    public static final class Function extends Node {
        public final String name;
        public List<VariableDef> params = new ArrayList<>();
        public PredNode requires;
        public List<VariableDef> returns = new ArrayList<>();
        public PredNode ensures;
        public List<VariableDef> locals = new ArrayList<>();
        public Statement body;
        public Function(String name, int line, int column) { super(line, column); this.name = name; }
    }

    public static final class Formula extends Node {
        public final String name;
        public List<VariableDef> params = new ArrayList<>();
        public PredNode body;
        public Formula(String name, int line, int column) { super(line, column); this.name = name; }
    }

    public abstract static class Statement extends Node {
        protected Statement(int line, int column) { super(line, column); }
    }

    public static final class Assign extends Statement {
        public final String target;
        public final List<IntExpr> indices;
        public final IntExpr value;
        public Assign(String target, List<IntExpr> indices, IntExpr value, int line, int column) {
            super(line, column);
            this.target = target; this.indices = indices; this.value = value;
        }
    }

    public static final class TupleAssign extends Statement {
        public final List<String> targets;
        public final Call call;
        public TupleAssign(List<String> targets, Call call, int line, int column) {
            super(line, column);
            this.targets = targets; this.call = call;
        }
    }

    public static final class If extends Statement {
        public final Condition cond;
        public final Statement thenBranch;
        public final Statement elseBranch;
        public If(Condition cond, Statement thenBranch, Statement elseBranch, int line, int column) {
            super(line, column);
            this.cond = cond; this.thenBranch = thenBranch; this.elseBranch = elseBranch;
        }
    }

    public static final class While extends Statement {
        public final Condition cond;
        public PredNode invariant;
        public Statement body;
        public While(Condition cond, Statement body, int line, int column) {
            super(line, column);
            this.cond = cond; this.body = body;
        }
    }

    public static final class Block extends Statement {
        public final List<Statement> statements = new ArrayList<>();
        public Block(int line, int column) { super(line, column); }
    }

    public static final class Assert extends Statement {
        public final Predicate predicate;
        public Assert(Predicate predicate, int line, int column) {
            super(line, column); this.predicate = predicate;
        }
    }

    public static final class Assume extends Statement {
        public final Predicate predicate;
        public Assume(Predicate predicate, int line, int column) {
            super(line, column); this.predicate = predicate;
        }
    }

    public static final class NumLit extends IntExpr {
        public final long value;
        public NumLit(long value, int line, int column) {
            super(line, column); this.value = value;
        }
    }

    public static final class Var extends IntExpr {
        public final String name;
        public Var(String name, int line, int column) {
            super(line, column); this.name = name;
        }
    }

    public static final class ArrayAccess extends IntExpr {
        public final String array;
        public final List<IntExpr> indices;
        public ArrayAccess(String array, List<IntExpr> indices, int line, int column) {
            super(line, column);
            this.array = array; this.indices = indices;
        }
    }

    public static final class Call extends IntExpr {
        public final String name;
        public final List<IntExpr> args;
        public Call(String name, List<IntExpr> args, int line, int column) {
            super(line, column); this.name = name; this.args = args;
        }
    }

    public static final class Neg extends IntExpr {
        public final IntExpr operand;
        public Neg(IntExpr operand, int line, int column) {
            super(line, column); this.operand = operand;
        }
    }

    public static final class Arith extends IntExpr {
        public enum Op { ADD, SUB, MUL, DIV }
        public final Op op;
        public final IntExpr left, right;
        public Arith(Op op, IntExpr left, IntExpr right, int line, int column) {
            super(line, column);
            this.op = op; this.left = left; this.right = right;
        }
    }

    public abstract static class Condition extends PredNode {
        protected Condition(int line, int column) { super(line, column); }
    }

    public abstract static class Predicate extends Condition {
        protected Predicate(int line, int column) { super(line, column); }
    }

    public static final class BoolLit extends Predicate {
        public final boolean value;
        public BoolLit(boolean value, int line, int column) {
            super(line, column); this.value = value;
        }
    }

    public static final class Comparison extends Predicate {
        public enum Op { EQ, NEQ, GE, LE, GT, LT }
        public final Op op;
        public final IntExpr left, right;
        public Comparison(Op op, IntExpr left, IntExpr right, int line, int column) {
            super(line, column);
            this.op = op; this.left = left; this.right = right;
        }
    }

    public static final class Not extends Predicate {
        public final PredNode operand;
        public Not(PredNode operand, int line, int column) {
            super(line, column); this.operand = operand;
        }
    }

    public static final class AndOr extends Predicate {
        public enum Op { AND, OR }
        public final Op op;
        public final PredNode left, right;
        public AndOr(Op op, PredNode left, PredNode right, int line, int column) {
            super(line, column);
            this.op = op; this.left = left; this.right = right;
        }
    }

    public static final class Implies extends Predicate {
        public final Condition left, right;
        public Implies(Condition left, Condition right, int line, int column) {
            super(line, column); this.left = left; this.right = right;
        }
    }

    public static final class Quantifier extends Predicate {
        public enum Kind { FORALL, EXISTS }
        public final Kind kind;
        public final VariableDef variable;
        public final Predicate body;
        public Quantifier(Kind kind, VariableDef variable, Predicate body, int line, int column) {
            super(line, column);
            this.kind = kind; this.variable = variable; this.body = body;
        }
    }

    public static final class FormulaRef extends Predicate {
        public final String name;
        public final List<IntExpr> args;
        public FormulaRef(String name, List<IntExpr> args, int line, int column) {
            super(line, column); this.name = name; this.args = args;
        }
    }

}