public class MermaidAstPrinter {
    private int nodeId;
    private StringBuilder sb;

    public String toMermaid(Ast.Module module) {
        nodeId = 0;
        sb = new StringBuilder("```mermaid\nflowchart TD\n");
        String rootId = printNode(module, "Module");

        for (Ast.Node item : module.items) {
            printTopLevel(item, rootId);
        }

        sb.append("```\n");
        return sb.toString();
    }

    private void printTopLevel(Ast.Node node, String parentId) {
        String id;
        if (node instanceof Ast.Function) {
            id = printNode(node, "Function: " + ((Ast.Function) node).name);
            printFunctionChildren((Ast.Function) node, id);
        } else if (node instanceof Ast.Formula) {
            id = printNode(node, "Formula: " + ((Ast.Formula) node).name);
            printPredicate(((Ast.Formula) node).body, id);
        } else {
            id = printNode(node, node.getClass().getSimpleName());
        }
        edge(parentId, id);
    }

    private void printFunctionChildren(Ast.Function fn, String parentId) {
        printVariableDefs(fn.params, "params", parentId);
        printPredicate(fn.requires, parentId);
        printVariableDefs(fn.returns, "returns", parentId);
        printPredicate(fn.ensures, parentId);
        printVariableDefs(fn.locals, "locals", parentId);
        printStatement(fn.body, parentId);
    }

    private void printVariableDefs(java.util.List<Ast.VariableDef> defs, String label, String parentId) {
        for (Ast.VariableDef def : defs) {
            String defId = printNode(def, label + ": " + variableLabel(def));
            edge(parentId, defId);
        }
    }

    private String variableLabel(Ast.VariableDef def) {
        return "Variable: " + def.name + " : " + def.type;
    }

    private void printStatement(Ast.Statement stmt, String parentId) {
        if (stmt == null) {
            return;
        }
        String id;
        if (stmt instanceof Ast.Block) {
            id = printNode(stmt, "Block");
            for (Ast.Statement child : ((Ast.Block) stmt).statements) {
                printStatement(child, id);
            }
        } else if (stmt instanceof Ast.Assign) {
            Ast.Assign assign = (Ast.Assign) stmt;
            id = printNode(assign, "Assign: " + assign.target);
            for (Ast.IntExpr index : assign.indices) {
                printExpression(index, id);
            }
            printExpression(assign.value, id);
        } else if (stmt instanceof Ast.TupleAssign) {
            Ast.TupleAssign tuple = (Ast.TupleAssign) stmt;
            id = printNode(tuple, "TupleAssign");
            printExpression(tuple.call, id);
        } else if (stmt instanceof Ast.If) {
            Ast.If ifStmt = (Ast.If) stmt;
            id = printNode(ifStmt, "If");
            printPredicate(ifStmt.cond, id);
            printStatement(ifStmt.thenBranch, id);
            printStatement(ifStmt.elseBranch, id);
        } else if (stmt instanceof Ast.While) {
            Ast.While whileStmt = (Ast.While) stmt;
            id = printNode(whileStmt, "While");
            printPredicate(whileStmt.cond, id);
            printPredicate(whileStmt.invariant, id);
            printStatement(whileStmt.body, id);
        } else if (stmt instanceof Ast.Assert) {
            Ast.Assert assertion = (Ast.Assert) stmt;
            id = printNode(assertion, "Assert");
            printPredicate(assertion.predicate, id);
        } else if (stmt instanceof Ast.Assume) {
            Ast.Assume assumption = (Ast.Assume) stmt;
            id = printNode(assumption, "Assume");
            printPredicate(assumption.predicate, id);
        } else {
            id = printNode((Ast.Node) stmt, stmt.getClass().getSimpleName());
        }
        edge(parentId, id);
    }

    private String printPredicate(Ast.PredNode predicate, String parentId) {
        if (predicate == null) {
            return null;
        }
        String id;
        if (predicate instanceof Ast.BoolLit) {
            id = printNode((Ast.Node) predicate, "Bool: " + ((Ast.BoolLit) predicate).value);
        } else if (predicate instanceof Ast.Comparison) {
            Ast.Comparison comparison = (Ast.Comparison) predicate;
            id = printNode(comparison, "Comparison: " + comparison.op);
            printExpression(comparison.left, id);
            printExpression(comparison.right, id);
        } else if (predicate instanceof Ast.Not) {
            Ast.Not not = (Ast.Not) predicate;
            id = printNode(not, "Not");
            printPredicate(not.operand, id);
        } else if (predicate instanceof Ast.AndOr) {
            Ast.AndOr andOr = (Ast.AndOr) predicate;
            id = printNode(andOr, "Logical: " + andOr.op);
            printPredicate(andOr.left, id);
            printPredicate(andOr.right, id);
        } else if (predicate instanceof Ast.Implies) {
            Ast.Implies implies = (Ast.Implies) predicate;
            id = printNode(implies, "Implies");
            printPredicate(implies.left, id);
            printPredicate(implies.right, id);
        } else if (predicate instanceof Ast.Quantifier) {
            Ast.Quantifier quantifier = (Ast.Quantifier) predicate;
            id = printNode(quantifier, "Quantifier: " + quantifier.kind);
            String variableId = printNode(quantifier.variable, variableLabel(quantifier.variable));
            edge(id, variableId);
            printPredicate(quantifier.body, id);
        } else if (predicate instanceof Ast.FormulaRef) {
            Ast.FormulaRef ref = (Ast.FormulaRef) predicate;
            id = printNode(ref, "FormulaRef: " + ref.name);
            for (Ast.IntExpr arg : ref.args) {
                printExpression(arg, id);
            }
        } else {
            id = printNode((Ast.Node) predicate, predicate.getClass().getSimpleName());
        }
        edge(parentId, id);
        return id;
    }

    private String printExpression(Ast.IntExpr expression, String parentId) {
        if (expression == null) {
            return null;
        }
        String id;
        if (expression instanceof Ast.NumLit) {
            id = printNode((Ast.NumLit) expression, "Number: " + ((Ast.NumLit) expression).value);
        } else if (expression instanceof Ast.Var) {
            id = printNode((Ast.Var) expression, "Variable: " + ((Ast.Var) expression).name);
        } else if (expression instanceof Ast.ArrayAccess) {
            Ast.ArrayAccess access = (Ast.ArrayAccess) expression;
            id = printNode(access, "ArrayAccess: " + access.array);
            for (Ast.IntExpr index : access.indices) {
                printExpression(index, id);
            }
        } else if (expression instanceof Ast.Call) {
            Ast.Call call = (Ast.Call) expression;
            id = printNode(call, "Call: " + call.name);
            for (Ast.IntExpr arg : call.args) {
                printExpression(arg, id);
            }
        } else if (expression instanceof Ast.Neg) {
            Ast.Neg neg = (Ast.Neg) expression;
            id = printNode(neg, "Neg");
            printExpression(neg.operand, id);
        } else if (expression instanceof Ast.Arith) {
            Ast.Arith arith = (Ast.Arith) expression;
            id = printNode(arith, "Arithmetic: " + arith.op);
            printExpression(arith.left, id);
            printExpression(arith.right, id);
        } else {
            id = printNode((Ast.Node) expression, expression.getClass().getSimpleName());
        }
        edge(parentId, id);
        return id;
    }

    private String printNode(Ast.Node node, String label) {
        String id = nextId();
        sb.append(id).append("[\"").append(nodeLabel(node, label)).append("\"]\n");
        return id;
    }

    private void edge(String parentId, String childId) {
        if (parentId != null && childId != null) {
            sb.append(parentId).append(" --> ").append(childId).append("\n");
        }
    }

    private String nodeLabel(Ast.Node node, String label) {
        return label + "\\n" + (node.getLine() + 1)
                + ":" + (node.getColumn() + 1);
    }

    private String nextId() {
        return "node" + (nodeId++);
    }
}
