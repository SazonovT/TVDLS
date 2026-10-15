import java.util.ArrayList;
import java.util.List;

/**
 * AST языка Funny.
 */
public final class Ast {

    public interface Node {}

    public interface IntExpr extends Node {}
    public interface PredNode extends Node {}

    public static final class Module implements Node {
        public final List<Node> items = new ArrayList<>(); // Function | Formula
    }

    public static final class VariableDef {
        public final String name;
        public final String type; // "int" | "int[]" | null
        public VariableDef(String name, String type) { this.name = name; this.type = type; }
    }

    public static final class Function implements Node {
        public final String name;
        public final List<VariableDef> params = new ArrayList<>();
        public PredNode requires;
        public final List<VariableDef> returns = new ArrayList<>();
        public PredNode ensures;
        public final List<VariableDef> locals = new ArrayList<>();
        public Statement body;
        public Function(String name) { this.name = name; }
    }

    public static final class Formula implements Node {
        public final String name;
        public final List<VariableDef> params = new ArrayList<>();
        public PredNode body;
        public Formula(String name) { this.name = name; }
    }

    public interface Statement extends Node {}

    public static final class Assign implements Statement {
        public final String target;
        public final List<IntExpr> indices;
        public final IntExpr value;
        public Assign(String target, List<IntExpr> indices, IntExpr value) {
            this.target = target; this.indices = indices; this.value = value;
        }
    }

    public static final class TupleAssign implements Statement {
        public final List<String> targets;
        public final Call call;
        public TupleAssign(List<String> targets, Call call) {
            this.targets = targets; this.call = call;
        }
    }

    public static final class If implements Statement {
        public final Condition cond;
        public final Statement thenBranch;
        public final Statement elseBranch;
        public If(Condition cond, Statement thenBranch, Statement elseBranch) {
            this.cond = cond; this.thenBranch = thenBranch; this.elseBranch = elseBranch;
        }
    }

    public static final class While implements Statement {
        public final Condition cond;
        public PredNode invariant;
        public Statement body;
        public While(Condition cond, Statement body) {
            this.cond = cond; this.body = body;
        }
    }

    public static final class Block implements Statement {
        public final List<Statement> statements = new ArrayList<>();
    }

    public static final class Assert implements Statement {
        public final Predicate predicate;
        public Assert(Predicate predicate) { this.predicate = predicate; }
    }

    public static final class Assume implements Statement {
        public final Predicate predicate;
        public Assume(Predicate predicate) { this.predicate = predicate; }
    }

    public static final class NumLit implements IntExpr {
        public final long value;
        public NumLit(long value) { this.value = value; }
    }

    public static final class Var implements IntExpr {
        public final String name;
        public Var(String name) { this.name = name; }
    }

    public static final class ArrayAccess implements IntExpr {
        public final String array;
        public final List<IntExpr> indices;
        public ArrayAccess(String array, List<IntExpr> indices) {
            this.array = array; this.indices = indices;
        }
    }

    public static final class Call implements IntExpr {
        public final String name;
        public final List<IntExpr> args;
        public Call(String name, List<IntExpr> args) { this.name = name; this.args = args; }
    }

    public static final class Neg implements IntExpr {
        public final IntExpr operand;
        public Neg(IntExpr operand) { this.operand = operand; }
    }

    public static final class Arith implements IntExpr {
        public enum Op { ADD, SUB, MUL, DIV }
        public final Op op;
        public final IntExpr left, right;
        public Arith(Op op, IntExpr left, IntExpr right) {
            this.op = op; this.left = left; this.right = right;
        }
    }

    public interface Condition extends PredNode {}

    public interface Predicate extends Condition {}

    public static final class BoolLit implements Predicate {
        public final boolean value;
        public BoolLit(boolean value) { this.value = value; }
    }

    public static final class Comparison implements Predicate {
        public enum Op { EQ, NEQ, GE, LE, GT, LT }
        public final Op op;
        public final IntExpr left, right;
        public Comparison(Op op, IntExpr left, IntExpr right) {
            this.op = op; this.left = left; this.right = right;
        }
    }

    public static final class Not implements Predicate {
        public final PredNode operand;
        public Not(PredNode operand) { this.operand = operand; }
    }

    public static final class AndOr implements Predicate {
        public enum Op { AND, OR }
        public final Op op;
        public final PredNode left, right;
        public AndOr(Op op, PredNode left, PredNode right) {
            this.op = op; this.left = left; this.right = right;
        }
    }

    public static final class Implies implements Predicate {
        public final Condition left, right;
        public Implies(Condition left, Condition right) { this.left = left; this.right = right; }
    }

    public static final class Quantifier implements Predicate {
        public enum Kind { FORALL, EXISTS }
        public final Kind kind;
        public final VariableDef variable;
        public final Predicate body;
        public Quantifier(Kind kind, VariableDef variable, Predicate body) {
            this.kind = kind; this.variable = variable; this.body = body;
        }
    }

    public static final class FormulaRef implements Predicate {
        public final String name;
        public final List<IntExpr> args;
        public FormulaRef(String name, List<IntExpr> args) { this.name = name; this.args = args; }
    }
}