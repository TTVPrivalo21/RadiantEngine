package dev.privalo.radiantengine.animation.molang;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Small Molang compiler used for Blockbench keyframe values.
 * Supports numbers, arithmetic, comparison, logic, ternary, assignments, "return",
 * query.* / variable.* / temp.* identifiers and the common math.* functions (angles in degrees).
 */
public final class Molang {

    @FunctionalInterface
    public interface Expr {
        double eval(MolangContext ctx);
    }

    /** Compiled expression with constant detection. */
    public static final class Compiled {
        private final Expr expr;
        private final boolean constant;
        private final double constantValue;

        private Compiled(Expr expr, boolean constant, double constantValue) {
            this.expr = expr;
            this.constant = constant;
            this.constantValue = constantValue;
        }

        public double eval(MolangContext ctx) {
            return constant ? constantValue : expr.eval(ctx);
        }

        public boolean isConstant() {
            return constant;
        }

        public static Compiled constant(double v) {
            return new Compiled(c -> v, true, v);
        }
    }

    private Molang() {
    }

    /**
     * Compiles a keyframe value. Empty input yields the given default constant; invalid input yields the default
     * as well (and is reported through the returned null-safe constant).
     */
    public static Compiled compile(String source, double def) {
        if (source == null) {
            return Compiled.constant(def);
        }
        String s = source.trim();
        if (s.isEmpty()) {
            return Compiled.constant(def);
        }
        try {
            return Compiled.constant(Double.parseDouble(s));
        } catch (NumberFormatException ignored) {
            // fall through to the expression compiler
        }
        Parser parser = new Parser(tokenize(s.toLowerCase(Locale.ROOT)));
        Node node = parser.parseProgram();
        if (!node.dynamic) {
            double v = node.expr.eval(new MolangContext());
            return Compiled.constant(Double.isFinite(v) ? v : def);
        }
        Expr e = node.expr;
        return new Compiled(ctx -> {
            double v = e.eval(ctx);
            return Double.isFinite(v) ? v : def;
        }, false, 0);
    }

    /** Safe variant that never throws. */
    public static Compiled compileSafe(String source, double def, List<String> errors) {
        try {
            return compile(source, def);
        } catch (RuntimeException ex) {
            if (errors != null) {
                errors.add("Molang invalido '" + source + "': " + ex.getMessage());
            }
            return Compiled.constant(def);
        }
    }

    // ------------------------------------------------------------------ tokenizer

    private enum T { NUM, ID, OP, LP, RP, COMMA, SEMI, EOF }

    private record Token(T type, String text, double num) {
    }

    private static List<Token> tokenize(String s) {
        List<Token> out = new ArrayList<>();
        int i = 0;
        int n = s.length();
        while (i < n) {
            char ch = s.charAt(i);
            if (Character.isWhitespace(ch)) {
                i++;
                continue;
            }
            if (Character.isDigit(ch) || (ch == '.' && i + 1 < n && Character.isDigit(s.charAt(i + 1)))) {
                int start = i;
                while (i < n && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) {
                    i++;
                }
                if (i < n && (s.charAt(i) == 'e') && i + 1 < n
                        && (Character.isDigit(s.charAt(i + 1)) || s.charAt(i + 1) == '-' || s.charAt(i + 1) == '+')) {
                    i += 2;
                    while (i < n && Character.isDigit(s.charAt(i))) {
                        i++;
                    }
                }
                if (i < n && s.charAt(i) == 'f') {
                    i++;
                    out.add(new Token(T.NUM, null, Double.parseDouble(s.substring(start, i - 1))));
                } else {
                    out.add(new Token(T.NUM, null, Double.parseDouble(s.substring(start, i))));
                }
                continue;
            }
            if (Character.isLetter(ch) || ch == '_') {
                int start = i;
                while (i < n && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_' || s.charAt(i) == '.')) {
                    i++;
                }
                out.add(new Token(T.ID, s.substring(start, i), 0));
                continue;
            }
            if (ch == '\'') {
                int end = s.indexOf('\'', i + 1);
                if (end < 0) {
                    throw new IllegalArgumentException("cadena sin cerrar");
                }
                out.add(new Token(T.NUM, null, 0));
                i = end + 1;
                continue;
            }
            switch (ch) {
                case '(' -> { out.add(new Token(T.LP, "(", 0)); i++; }
                case ')' -> { out.add(new Token(T.RP, ")", 0)); i++; }
                case ',' -> { out.add(new Token(T.COMMA, ",", 0)); i++; }
                case ';' -> { out.add(new Token(T.SEMI, ";", 0)); i++; }
                default -> {
                    String two = i + 1 < n ? s.substring(i, i + 2) : "";
                    if (two.equals("<=") || two.equals(">=") || two.equals("==") || two.equals("!=")
                            || two.equals("&&") || two.equals("||") || two.equals("??")) {
                        out.add(new Token(T.OP, two, 0));
                        i += 2;
                    } else if ("+-*/<>!?:=".indexOf(ch) >= 0) {
                        out.add(new Token(T.OP, String.valueOf(ch), 0));
                        i++;
                    } else {
                        throw new IllegalArgumentException("caracter inesperado '" + ch + "'");
                    }
                }
            }
        }
        out.add(new Token(T.EOF, null, 0));
        return out;
    }

    // ------------------------------------------------------------------ parser

    private record Node(Expr expr, boolean dynamic) {
    }

    private static final class Parser {
        private final List<Token> tokens;
        private int pos;

        Parser(List<Token> tokens) {
            this.tokens = tokens;
        }

        private Token peek() {
            return tokens.get(pos);
        }

        private Token next() {
            return tokens.get(pos++);
        }

        private boolean isOp(String op) {
            Token t = peek();
            return t.type == T.OP && t.text.equals(op);
        }

        private void expect(T type) {
            Token t = next();
            if (t.type != type) {
                throw new IllegalArgumentException("se esperaba " + type + " pero se encontro " + t.type);
            }
        }

        Node parseProgram() {
            List<Node> statements = new ArrayList<>();
            int returnIndex = -1;
            while (peek().type != T.EOF) {
                if (peek().type == T.SEMI) {
                    next();
                    continue;
                }
                Token t = peek();
                if (t.type == T.ID && t.text.equals("return")) {
                    next();
                    statements.add(parseExpression());
                    returnIndex = statements.size() - 1;
                } else if (t.type == T.ID && pos + 1 < tokens.size()
                        && tokens.get(pos + 1).type == T.OP && tokens.get(pos + 1).text.equals("=")) {
                    String name = normalize(next().text);
                    next();
                    Node value = parseExpression();
                    Expr ve = value.expr;
                    statements.add(new Node(ctx -> {
                        double v = ve.eval(ctx);
                        ctx.setVariable(name, v);
                        return v;
                    }, true));
                } else {
                    statements.add(parseExpression());
                }
            }
            if (statements.isEmpty()) {
                return new Node(c -> 0, false);
            }
            if (statements.size() == 1) {
                return statements.get(0);
            }
            final int ret = returnIndex < 0 ? statements.size() - 1 : returnIndex;
            Expr[] all = statements.stream().map(Node::expr).toArray(Expr[]::new);
            boolean dyn = statements.stream().anyMatch(Node::dynamic);
            return new Node(ctx -> {
                double result = 0;
                for (int i = 0; i < all.length; i++) {
                    double v = all[i].eval(ctx);
                    if (i == ret) {
                        result = v;
                    }
                }
                return result;
            }, dyn);
        }

        Node parseExpression() {
            Node cond = parseCoalesce();
            if (isOp("?")) {
                next();
                Node a = parseExpression();
                if (isOp(":")) {
                    next();
                    Node b = parseExpression();
                    Expr c = cond.expr, ea = a.expr, eb = b.expr;
                    return new Node(ctx -> c.eval(ctx) != 0 ? ea.eval(ctx) : eb.eval(ctx),
                            cond.dynamic || a.dynamic || b.dynamic);
                }
                Expr c = cond.expr, ea = a.expr;
                return new Node(ctx -> c.eval(ctx) != 0 ? ea.eval(ctx) : 0, cond.dynamic || a.dynamic);
            }
            return cond;
        }

        Node parseCoalesce() {
            Node left = parseOr();
            while (isOp("??")) {
                next();
                Node right = parseOr();
                Expr l = left.expr, r = right.expr;
                left = new Node(ctx -> {
                    double v = l.eval(ctx);
                    return v != 0 ? v : r.eval(ctx);
                }, left.dynamic || right.dynamic);
            }
            return left;
        }

        Node parseOr() {
            Node left = parseAnd();
            while (isOp("||")) {
                next();
                Node right = parseAnd();
                Expr l = left.expr, r = right.expr;
                left = new Node(ctx -> (l.eval(ctx) != 0 || r.eval(ctx) != 0) ? 1 : 0, left.dynamic || right.dynamic);
            }
            return left;
        }

        Node parseAnd() {
            Node left = parseCompare();
            while (isOp("&&")) {
                next();
                Node right = parseCompare();
                Expr l = left.expr, r = right.expr;
                left = new Node(ctx -> (l.eval(ctx) != 0 && r.eval(ctx) != 0) ? 1 : 0, left.dynamic || right.dynamic);
            }
            return left;
        }

        Node parseCompare() {
            Node left = parseAdd();
            while (true) {
                Token t = peek();
                if (t.type != T.OP) {
                    return left;
                }
                String op = t.text;
                if (!op.equals("<") && !op.equals(">") && !op.equals("<=") && !op.equals(">=")
                        && !op.equals("==") && !op.equals("!=")) {
                    return left;
                }
                next();
                Node right = parseAdd();
                Expr l = left.expr, r = right.expr;
                Expr e = switch (op) {
                    case "<" -> ctx -> l.eval(ctx) < r.eval(ctx) ? 1 : 0;
                    case ">" -> ctx -> l.eval(ctx) > r.eval(ctx) ? 1 : 0;
                    case "<=" -> ctx -> l.eval(ctx) <= r.eval(ctx) ? 1 : 0;
                    case ">=" -> ctx -> l.eval(ctx) >= r.eval(ctx) ? 1 : 0;
                    case "==" -> ctx -> Math.abs(l.eval(ctx) - r.eval(ctx)) < 1.0E-9 ? 1 : 0;
                    default -> ctx -> Math.abs(l.eval(ctx) - r.eval(ctx)) >= 1.0E-9 ? 1 : 0;
                };
                left = new Node(e, left.dynamic || right.dynamic);
            }
        }

        Node parseAdd() {
            Node left = parseMul();
            while (isOp("+") || isOp("-")) {
                boolean plus = next().text.equals("+");
                Node right = parseMul();
                Expr l = left.expr, r = right.expr;
                left = new Node(plus ? ctx -> l.eval(ctx) + r.eval(ctx) : ctx -> l.eval(ctx) - r.eval(ctx),
                        left.dynamic || right.dynamic);
            }
            return left;
        }

        Node parseMul() {
            Node left = parseUnary();
            while (isOp("*") || isOp("/")) {
                boolean mul = next().text.equals("*");
                Node right = parseUnary();
                Expr l = left.expr, r = right.expr;
                left = new Node(mul ? ctx -> l.eval(ctx) * r.eval(ctx) : ctx -> {
                    double d = r.eval(ctx);
                    return d == 0 ? 0 : l.eval(ctx) / d;
                }, left.dynamic || right.dynamic);
            }
            return left;
        }

        Node parseUnary() {
            if (isOp("-")) {
                next();
                Node n = parseUnary();
                Expr e = n.expr;
                return new Node(ctx -> -e.eval(ctx), n.dynamic);
            }
            if (isOp("+")) {
                next();
                return parseUnary();
            }
            if (isOp("!")) {
                next();
                Node n = parseUnary();
                Expr e = n.expr;
                return new Node(ctx -> e.eval(ctx) == 0 ? 1 : 0, n.dynamic);
            }
            return parsePrimary();
        }

        Node parsePrimary() {
            Token t = next();
            switch (t.type) {
                case NUM -> {
                    double v = t.num;
                    return new Node(c -> v, false);
                }
                case LP -> {
                    Node inner = parseExpression();
                    expect(T.RP);
                    return inner;
                }
                case ID -> {
                    String id = normalize(t.text);
                    if (peek().type == T.LP) {
                        next();
                        List<Node> args = new ArrayList<>();
                        if (peek().type != T.RP) {
                            args.add(parseExpression());
                            while (peek().type == T.COMMA) {
                                next();
                                args.add(parseExpression());
                            }
                        }
                        expect(T.RP);
                        return function(id, args);
                    }
                    return identifier(id);
                }
                default -> throw new IllegalArgumentException("token inesperado " + t.type);
            }
        }

        private Node identifier(String id) {
            switch (id) {
                case "math.pi":
                    return new Node(c -> Math.PI, false);
                case "true":
                    return new Node(c -> 1, false);
                case "false":
                    return new Node(c -> 0, false);
                default:
                    break;
            }
            if (id.startsWith("query.")) {
                String q = id.substring(6);
                return new Node(ctx -> ctx.query(q), true);
            }
            if (id.startsWith("variable.") || id.startsWith("temp.") || id.startsWith("context.")) {
                return new Node(ctx -> ctx.variable(id), true);
            }
            return new Node(ctx -> ctx.variable(id), true);
        }

        private Node function(String name, List<Node> args) {
            Expr[] a = args.stream().map(Node::expr).toArray(Expr[]::new);
            boolean dyn = args.stream().anyMatch(Node::dynamic);
            if (name.startsWith("query.")) {
                String q = name.substring(6);
                return new Node(ctx -> ctx.query(q), true);
            }
            Expr e = switch (name) {
                case "math.sin" -> ctx -> Math.sin(Math.toRadians(arg(a, 0, ctx)));
                case "math.cos" -> ctx -> Math.cos(Math.toRadians(arg(a, 0, ctx)));
                case "math.asin" -> ctx -> Math.toDegrees(Math.asin(arg(a, 0, ctx)));
                case "math.acos" -> ctx -> Math.toDegrees(Math.acos(arg(a, 0, ctx)));
                case "math.atan" -> ctx -> Math.toDegrees(Math.atan(arg(a, 0, ctx)));
                case "math.atan2" -> ctx -> Math.toDegrees(Math.atan2(arg(a, 0, ctx), arg(a, 1, ctx)));
                case "math.abs" -> ctx -> Math.abs(arg(a, 0, ctx));
                case "math.ceil" -> ctx -> Math.ceil(arg(a, 0, ctx));
                case "math.floor" -> ctx -> Math.floor(arg(a, 0, ctx));
                case "math.round" -> ctx -> Math.round(arg(a, 0, ctx));
                case "math.trunc" -> ctx -> (double) (long) arg(a, 0, ctx);
                case "math.sqrt" -> ctx -> Math.sqrt(Math.max(0, arg(a, 0, ctx)));
                case "math.exp" -> ctx -> Math.exp(arg(a, 0, ctx));
                case "math.ln" -> ctx -> Math.log(arg(a, 0, ctx));
                case "math.pow" -> ctx -> Math.pow(arg(a, 0, ctx), arg(a, 1, ctx));
                case "math.min" -> ctx -> Math.min(arg(a, 0, ctx), arg(a, 1, ctx));
                case "math.max" -> ctx -> Math.max(arg(a, 0, ctx), arg(a, 1, ctx));
                case "math.mod" -> ctx -> {
                    double d = arg(a, 1, ctx);
                    return d == 0 ? 0 : arg(a, 0, ctx) % d;
                };
                case "math.clamp" -> ctx -> Math.max(arg(a, 1, ctx), Math.min(arg(a, 2, ctx), arg(a, 0, ctx)));
                case "math.lerp" -> ctx -> {
                    double from = arg(a, 0, ctx);
                    return from + (arg(a, 1, ctx) - from) * arg(a, 2, ctx);
                };
                case "math.lerprotate" -> ctx -> {
                    double from = arg(a, 0, ctx);
                    double diff = ((arg(a, 1, ctx) - from) % 360 + 540) % 360 - 180;
                    return from + diff * arg(a, 2, ctx);
                };
                case "math.hermite_blend" -> ctx -> {
                    double t = arg(a, 0, ctx);
                    return 3 * t * t - 2 * t * t * t;
                };
                case "math.sign" -> ctx -> Math.signum(arg(a, 0, ctx));
                case "math.random" -> ctx -> {
                    double lo = arg(a, 0, ctx), hi = arg(a, 1, ctx);
                    return lo + ThreadLocalRandom.current().nextDouble() * (hi - lo);
                };
                case "math.random_integer" -> ctx -> {
                    long lo = Math.round(arg(a, 0, ctx)), hi = Math.round(arg(a, 1, ctx));
                    return hi <= lo ? lo : ThreadLocalRandom.current().nextLong(lo, hi + 1);
                };
                case "math.die_roll" -> ctx -> {
                    int count = (int) arg(a, 0, ctx);
                    double lo = arg(a, 1, ctx), hi = arg(a, 2, ctx), sum = 0;
                    for (int i = 0; i < count; i++) {
                        sum += lo + ThreadLocalRandom.current().nextDouble() * (hi - lo);
                    }
                    return sum;
                };
                case "math.die_roll_integer" -> ctx -> {
                    int count = (int) arg(a, 0, ctx);
                    long lo = Math.round(arg(a, 1, ctx)), hi = Math.round(arg(a, 2, ctx));
                    double sum = 0;
                    for (int i = 0; i < count; i++) {
                        sum += hi <= lo ? lo : ThreadLocalRandom.current().nextLong(lo, hi + 1);
                    }
                    return sum;
                };
                default -> ctx -> 0;
            };
            boolean random = name.startsWith("math.random") || name.startsWith("math.die_roll");
            return new Node(e, dyn || random);
        }

        private static double arg(Expr[] a, int i, MolangContext ctx) {
            return i < a.length ? a[i].eval(ctx) : 0;
        }
    }

    private static String normalize(String id) {
        if (id.startsWith("q.")) {
            return "query." + id.substring(2);
        }
        if (id.startsWith("v.")) {
            return "variable." + id.substring(2);
        }
        if (id.startsWith("t.")) {
            return "temp." + id.substring(2);
        }
        if (id.startsWith("c.")) {
            return "context." + id.substring(2);
        }
        return id;
    }
}
