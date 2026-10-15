public class MermaidAstPrinter {
    private int nodeId = 0;
    private StringBuilder sb = new StringBuilder();

    public String toMermaid(Ast.Module module) {
        // Открывающий markdown-блок для Mermaid
        sb.append("```mermaid\n");

        sb.append("flowchart TD\n");
        String rootId = nextId();
        sb.append(rootId).append("[\"Module\"]\n");

        for (Ast.Node item : module.items) {
            if (item instanceof Ast.Function) {
                printFunction((Ast.Function) item, rootId);
            } else if (item instanceof Ast.Formula) {
                printFormula((Ast.Formula) item, rootId);
            }
        }

        // Закрывающий markdown-блок
        sb.append("```\n");

        return sb.toString();
    }

    private void printFunction(Ast.Function fn, String parentId) {
        String fnId = nextId();
        sb.append(fnId).append("[\"Function: ").append(fn.name).append("\"]\n");
        sb.append(parentId).append(" --> ").append(fnId).append("\n");

        if (fn.requires != null) {
            String reqId = nextId();
            sb.append(reqId).append("[\"requires\"]\n");
            sb.append(fnId).append(" --> ").append(reqId).append("\n");
        }
        if (fn.ensures != null) {
            String ensId = nextId();
            sb.append(ensId).append("[\"ensures\"]\n");
            sb.append(fnId).append(" --> ").append(ensId).append("\n");
        }

        if (fn.body != null) {
            printStatement(fn.body, fnId);
        }
    }

    private void printFormula(Ast.Formula f, String parentId) {
        String fId = nextId();
        sb.append(fId).append("[\"Formula: ").append(f.name).append("\"]\n");
        sb.append(parentId).append(" --> ").append(fId).append("\n");
    }

    private void printStatement(Ast.Statement stmt, String parentId) {
        String stmtId = nextId();
        if (stmt instanceof Ast.Block) {
            sb.append(stmtId).append("[\"Block\"]\n");
            for (Ast.Statement s : ((Ast.Block) stmt).statements) {
                printStatement(s, stmtId);
            }
        } else if (stmt instanceof Ast.Assign) {
            Ast.Assign a = (Ast.Assign) stmt;
            sb.append(stmtId).append("[\"Assign: ").append(a.target).append("\"]\n");
        } else if (stmt instanceof Ast.If) {
            sb.append(stmtId).append("[\"If\"]\n");
            printStatement(((Ast.If) stmt).thenBranch, stmtId);
            if (((Ast.If) stmt).elseBranch != null) {
                printStatement(((Ast.If) stmt).elseBranch, stmtId);
            }
        } else if (stmt instanceof Ast.While) {
            sb.append(stmtId).append("[\"While\"]\n");
            if (((Ast.While) stmt).body != null) {
                printStatement(((Ast.While) stmt).body, stmtId);
            }
        } else if (stmt instanceof Ast.Assert) {
            sb.append(stmtId).append("[\"Assert\"]\n");
        } else if (stmt instanceof Ast.Assume) {
            sb.append(stmtId).append("[\"Assume\"]\n");
        } else if (stmt instanceof Ast.TupleAssign) {
            sb.append(stmtId).append("[\"TupleAssign\"]\n");
        } else {
            sb.append(stmtId).append("[\"").append(stmt.getClass().getSimpleName()).append("\"]\n");
        }
        sb.append(parentId).append(" --> ").append(stmtId).append("\n");
    }

    private String nextId() {
        return "node" + (nodeId++);
    }

}