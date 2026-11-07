public class MermaidAstPrinter {
    private int nodeId;
    private StringBuilder sb;

    public String toMermaid(Ast.Module module, boolean as_md) {
        nodeId = 0;
        sb = new StringBuilder();

        if (as_md) {
            sb.append("```mermaid\n");
        }
        sb.append("graph TD\n");

        String rootId = printNode(module, "Module");

        for (Ast.Node item : module.getItems()) {
            printTopLevel(item, rootId);
        }

        if (as_md) {
            sb.append("```\n");
        }
        return sb.toString();
    }

    public String toMermaid(Ast.Module module) {
        return toMermaid(module, false);
    }

    private void printTopLevel(Ast.Node node, String parentId) {
        String id;
        if (node instanceof Ast.Function) {
            id = printNode(node, "Function: " + ((Ast.Function) node).getName());
            printFunctionChildren((Ast.Function) node, id);
        } else if (node instanceof Ast.Formula) {
            id = printNode(node, "Formula: " + ((Ast.Formula) node).getName());
            printVariableDefs(((Ast.Formula) node).getParams(), "params", id);
            printPredicate(((Ast.Formula) node).getBody(), id);
        } else {
            id = printNode(node, node.getClass().getSimpleName());
        }
        edge(parentId, id);
    }

    private void printFunctionChildren(Ast.Function fn, String parentId) {
        printVariableDefs(fn.getParams(), "params", parentId);
        printPredicate(fn.getRequires(), "requires", parentId);
        printVariableDefs(fn.getReturns(), "returns", parentId);
        printPredicate(fn.getEnsures(), "ensures", parentId);
        printVariableDefs(fn.getLocals(), "locals", parentId);
        printStatement(fn.getBody(), parentId);
    }

    private void printVariableDefs(java.util.List<Ast.VariableDef> defs, String label, String parentId) {
        for (Ast.VariableDef def : defs) {
            String defId = printNode(def, label + ": " + variableLabel(def));
            edge(parentId, defId);
        }
    }

    private String variableLabel(Ast.VariableDef def) {
        return "Variable: " + def.getName();
    }

    private void printStatement(Ast.Statement stmt, String parentId) {
        if (stmt == null) {
            return;
        }
        String id;
        if (stmt instanceof Ast.Block) {
            id = printNode(stmt, "Block");
            for (Ast.Statement child : ((Ast.Block) stmt).getStatements()) {
                printStatement(child, id);
            }
        } else if (stmt instanceof Ast.Assign) {
            Ast.Assign assign = (Ast.Assign) stmt;
            id = printNode(assign, "Assign: " + assign.getTarget());
            for (Ast.IntExpr index : assign.getIndices()) {
                printExpression(index, id);
            }
            printExpression(assign.getValue(), id);
        } else if (stmt instanceof Ast.TupleAssign) {
            Ast.TupleAssign tuple = (Ast.TupleAssign) stmt;
            id = printNode(tuple, "TupleAssign: " + String.join(", ", tuple.getTargets()));
            printExpression(tuple.getCall(), id);
        } else if (stmt instanceof Ast.If) {
            Ast.If ifStmt = (Ast.If) stmt;
            id = printNode(ifStmt, "If");
            printPredicate(ifStmt.getCond(), id);
            printStatement(ifStmt.getThenBranch(), id);
            printStatement(ifStmt.getElseBranch(), id);
        } else if (stmt instanceof Ast.While) {
            Ast.While whileStmt = (Ast.While) stmt;
            id = printNode(whileStmt, "While");
            printPredicate(whileStmt.getCond(), id);
            printPredicate(whileStmt.getInvariant(), id);
            printStatement(whileStmt.getBody(), id);
        } else if (stmt instanceof Ast.Assert) {
            Ast.Assert assertion = (Ast.Assert) stmt;
            id = printNode(assertion, "Assert");
            printPredicate(assertion.getPredicate(), id);
        } else if (stmt instanceof Ast.Assume) {
            Ast.Assume assumption = (Ast.Assume) stmt;
            id = printNode(assumption, "Assume");
            printPredicate(assumption.getPredicate(), id);
        } else {
            id = printNode((Ast.Node) stmt, stmt.getClass().getSimpleName());
        }
        edge(parentId, id);
    }


    private String printPredicate(Ast.PredNode predicate, String parentId){
        return printPredicate(predicate, "", parentId);
    }
    private String printPredicate(Ast.PredNode predicate, String label, String parentId) {
        if (predicate == null) {
            return null;
        }
        String labelPrefix = (label == null || label.isEmpty()) ? "" : (label + ": ");
        String id;
        if (predicate instanceof Ast.BoolLit) {
            id = printNode((Ast.Node) predicate, labelPrefix + "Bool: " + ((Ast.BoolLit) predicate).getValue());
        } else if (predicate instanceof Ast.Comparison) {
            Ast.Comparison comparison = (Ast.Comparison) predicate;
            id = printNode(comparison, labelPrefix + "Comparison: " + comparison.getOp());
            printExpression(comparison.getLeft(), id);
            printExpression(comparison.getRight(), id);
        } else if (predicate instanceof Ast.Not) {
            Ast.Not not = (Ast.Not) predicate;
            id = printNode(not, labelPrefix + "Not");
            printPredicate(not.getOperand(), id);
        } else if (predicate instanceof Ast.AndOr) {
            Ast.AndOr andOr = (Ast.AndOr) predicate;
            id = printNode(andOr, labelPrefix + "Logical: " + andOr.getOp());
            printPredicate(andOr.getLeft(), id);
            printPredicate(andOr.getRight(), id);
        } else if (predicate instanceof Ast.Implies) {
            Ast.Implies implies = (Ast.Implies) predicate;
            id = printNode(implies, labelPrefix + "Implies");
            printPredicate(implies.getLeft(), id);
            printPredicate(implies.getRight(), id);
        } else if (predicate instanceof Ast.Quantifier) {
            Ast.Quantifier quantifier = (Ast.Quantifier) predicate;
            id = printNode(quantifier, labelPrefix + "Quantifier: " + quantifier.getKind());
            String variableId = printNode(quantifier.getVariable(), variableLabel(quantifier.getVariable()));
            edge(id, variableId);
            printPredicate(quantifier.getBody(), id);
        } else if (predicate instanceof Ast.FormulaRef) {
            Ast.FormulaRef ref = (Ast.FormulaRef) predicate;
            id = printNode(ref, labelPrefix + "FormulaRef: " + ref.getName());
            for (Ast.IntExpr arg : ref.getArgs()) {
                printExpression(arg, id);
            }
        } else {
            id = printNode((Ast.Node) predicate, labelPrefix + predicate.getClass().getSimpleName());
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
            id = printNode((Ast.NumLit) expression, "Number: " + ((Ast.NumLit) expression).getValue());
        } else if (expression instanceof Ast.Var) {
            id = printNode((Ast.Var) expression, "Variable: " + ((Ast.Var) expression).getName());
        } else if (expression instanceof Ast.ArrayAccess) {
            Ast.ArrayAccess access = (Ast.ArrayAccess) expression;
            id = printNode(access, "ArrayAccess: " + access.getArray());
            for (Ast.IntExpr index : access.getIndices()) {
                printExpression(index, id);
            }
        } else if (expression instanceof Ast.Call) {
            Ast.Call call = (Ast.Call) expression;
            id = printNode(call, "Call: " + call.getName());
            for (Ast.IntExpr arg : call.getArgs()) {
                printExpression(arg, id);
            }
        } else if (expression instanceof Ast.Neg) {
            Ast.Neg neg = (Ast.Neg) expression;
            id = printNode(neg, "Neg");
            printExpression(neg.getOperand(), id);
        } else if (expression instanceof Ast.Arith) {
            Ast.Arith arith = (Ast.Arith) expression;
            id = printNode(arith, "Arithmetic: " + arith.getOp());
            printExpression(arith.getLeft(), id);
            printExpression(arith.getRight(), id);
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
        if (node instanceof Ast.Expr expr) {
            label += "<br/>type: " + expr.getInferredType();
        } else if (node instanceof Ast.VariableDef def) {
            label += "<br/>type: " + def.getType();
        }
        return label  + "\\n" + (node.getLine() + 1)
                + ":" + (node.getColumn() + 1);
    }

    private String nextId() {
        return "node" + (nodeId++);
    }
}
