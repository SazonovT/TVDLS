/**
 * Обход AST языка Funny и генерация Mermaid-диаграммы (graph TD).
 * Каждому узлу присваивается уникальный id вида n0, n1, ...
 * На выходе — готовая Markdown-вставка с блоком ```mermaid```.
 */
public final class MermaidAstPrinter {

    private final StringBuilder nodes = new StringBuilder();
    private final StringBuilder edges = new StringBuilder();
    private int counter = 0;

    /** Точка входа: печатает модуль целиком. */
    public String toMermaid(Ast.Module module) {
        nodes.setLength(0);
        edges.setLength(0);
        counter = 0;

        int root = node("Module", null);
        if (module != null) {
            for (Ast.Node item : module.items) {
                addEdge(root, emitTop(item));
            }
        }

        return "```mermaid\n"
                + "graph TD\n"
                + nodes
                + edges
                + "```\n";
    }

    // ---------- верхний уровень ----------

    private int emitTop(Ast.Node item) {
        if (item instanceof Ast.Function) return emitFunction((Ast.Function) item);
        if (item instanceof Ast.Formula)  return emitFormula((Ast.Formula) item);
        return node("Node", item == null ? null : item.getClass().getSimpleName());
    }

    private int emitFunction(Ast.Function f) {
        int id = node("Function", f.name);
        for (Ast.VariableDef p : f.params)  addEdge(id, emitVarDef(p, "param"));
        if (f.requires != null)             addEdge(id, emitPredNode(f.requires, "requires"));
        for (Ast.VariableDef r : f.returns) addEdge(id, emitVarDef(r, "returns"));
        if (f.ensures != null)              addEdge(id, emitPredNode(f.ensures, "ensures"));
        for (Ast.VariableDef l : f.locals)  addEdge(id, emitVarDef(l, "local"));
        if (f.body != null)                 addEdge(id, emitStmt(f.body, "body"));
        return id;
    }

    private int emitFormula(Ast.Formula f) {
        int id = node("Formula", f.name);
        for (Ast.VariableDef p : f.params) addEdge(id, emitVarDef(p, "param"));
        if (f.body != null)                addEdge(id, emitPredNode(f.body, "body"));
        return id;
    }

    private int emitVarDef(Ast.VariableDef v, String label) {
        int id = node("Var", join(label, v.name));
        if (v.type != null) addEdge(id, node("Type", v.type));
        return id;
    }

    // ---------- операторы ----------

    private int emitStmt(Ast.Statement s, String label) {
        if (s == null) return node("Stmt", null);

        if (s instanceof Ast.Block) {
            Ast.Block b = (Ast.Block) s;
            int id = node("Block", label);
            for (Ast.Statement st : b.statements) addEdge(id, emitStmt(st, null));
            return id;
        }
        if (s instanceof Ast.Assign) {
            Ast.Assign a = (Ast.Assign) s;
            int id = node("Assign", join(label, a.target));
            for (Ast.IntExpr idx : a.indices) addEdge(id, emitExpr(idx, "index"));
            addEdge(id, emitExpr(a.value, "="));
            return id;
        }
        if (s instanceof Ast.TupleAssign) {
            Ast.TupleAssign a = (Ast.TupleAssign) s;
            int id = node("TupleAssign", join(label, String.join(", ", a.targets)));
            addEdge(id, emitExpr(a.call, "call"));
            return id;
        }
        if (s instanceof Ast.If) {
            Ast.If i = (Ast.If) s;
            int id = node("If", label);
            addEdge(id, emitPredNode(i.cond, "cond"));
            addEdge(id, emitStmt(i.thenBranch, "then"));
            if (i.elseBranch != null) addEdge(id, emitStmt(i.elseBranch, "else"));
            return id;
        }
        if (s instanceof Ast.While) {
            Ast.While w = (Ast.While) s;
            int id = node("While", label);
            addEdge(id, emitPredNode(w.cond, "cond"));
            if (w.invariant != null) addEdge(id, emitPredNode(w.invariant, "invariant"));
            if (w.body != null)      addEdge(id, emitStmt(w.body, "body"));
            return id;
        }
        if (s instanceof Ast.Assert) {
            Ast.Assert a = (Ast.Assert) s;
            int id = node("Assert", label);
            addEdge(id, emitPredNode(a.predicate, "pred"));
            return id;
        }
        if (s instanceof Ast.Assume) {
            Ast.Assume a = (Ast.Assume) s;
            int id = node("Assume", label);
            addEdge(id, emitPredNode(a.predicate, "pred"));
            return id;
        }
        return node("Stmt", s.getClass().getSimpleName());
    }

    // ---------- выражения ----------

    private int emitExpr(Ast.IntExpr e, String label) {
        if (e == null) return node("Expr", null);

        if (e instanceof Ast.NumLit) {
            return node("NumLit", join(label, Long.toString(((Ast.NumLit) e).value)));
        }
        if (e instanceof Ast.Var) {
            return node("Var", join(label, ((Ast.Var) e).name));
        }
        if (e instanceof Ast.ArrayAccess) {
            Ast.ArrayAccess a = (Ast.ArrayAccess) e;
            int id = node("ArrayAccess", join(label, a.array));
            for (Ast.IntExpr idx : a.indices) addEdge(id, emitExpr(idx, "index"));
            return id;
        }
        if (e instanceof Ast.Call) {
            Ast.Call c = (Ast.Call) e;
            int id = node("Call", join(label, c.name));
            for (Ast.IntExpr arg : c.args) addEdge(id, emitExpr(arg, null));
            return id;
        }
        if (e instanceof Ast.Neg) {
            Ast.Neg n = (Ast.Neg) e;
            int id = node("Neg", label);
            addEdge(id, emitExpr(n.operand, null));
            return id;
        }
        if (e instanceof Ast.Arith) {
            Ast.Arith a = (Ast.Arith) e;
            int id = node("Arith", join(label, a.op.name()));
            addEdge(id, emitExpr(a.left,  "L"));
            addEdge(id, emitExpr(a.right, "R"));
            return id;
        }
        return node("Expr", e.getClass().getSimpleName());
    }

    // ---------- условия / предикаты ----------

    private int emitPredNode(Ast.PredNode p, String label) {
        if (p == null) return node("Pred", null);

        if (p instanceof Ast.BoolLit) {
            return node("BoolLit", join(label, Boolean.toString(((Ast.BoolLit) p).value)));
        }
        if (p instanceof Ast.Comparison) {
            Ast.Comparison c = (Ast.Comparison) p;
            int id = node("Comparison", join(label, c.op.name()));
            addEdge(id, emitExpr(c.left,  "L"));
            addEdge(id, emitExpr(c.right, "R"));
            return id;
        }
        if (p instanceof Ast.Not) {
            Ast.Not n = (Ast.Not) p;
            int id = node("Not", label);
            addEdge(id, emitPredNode(n.operand, null));
            return id;
        }
        if (p instanceof Ast.AndOr) {
            Ast.AndOr a = (Ast.AndOr) p;
            int id = node("AndOr", join(label, a.op.name()));
            addEdge(id, emitPredNode(a.left,  "L"));
            addEdge(id, emitPredNode(a.right, "R"));
            return id;
        }
        if (p instanceof Ast.Implies) {
            Ast.Implies im = (Ast.Implies) p;
            int id = node("Implies", label);
            addEdge(id, emitPredNode(im.left,  "L"));
            addEdge(id, emitPredNode(im.right, "R"));
            return id;
        }
        if (p instanceof Ast.Quantifier) {
            Ast.Quantifier q = (Ast.Quantifier) p;
            String sig = q.kind.name() + " " + q.variable.name
                    + (q.variable.type != null ? ": " + q.variable.type : "");
            int id = node("Quantifier", join(label, sig));
            addEdge(id, emitPredNode(q.body, "body"));
            return id;
        }
        if (p instanceof Ast.FormulaRef) {
            Ast.FormulaRef fr = (Ast.FormulaRef) p;
            int id = node("FormulaRef", join(label, fr.name));
            for (Ast.IntExpr arg : fr.args) addEdge(id, emitExpr(arg, null));
            return id;
        }
        return node("Pred", p.getClass().getSimpleName());
    }

    // ---------- низкоуровневая эмиссия ----------

    private int node(String kind, String label) {
        int id = counter++;
        nodes.append("    n").append(id).append("[\"")
                .append(escape(kind));
        if (label != null && !label.isEmpty()) {
            nodes.append("<br/><i>").append(escape(label)).append("</i>");
        }
        nodes.append("\"]\n");
        return id;
    }

    private void addEdge(int from, int to) {
        edges.append("    n").append(from).append(" --> n").append(to).append("\n");
    }

    private static String join(String a, String b) {
        if (a == null || a.isEmpty()) return b;
        if (b == null || b.isEmpty()) return a;
        return a + " " + b;
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}