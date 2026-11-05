import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small JSON reader and writer so the project needs no external library.
 * parse() returns Map (object), List (array), String, Double, Boolean or null.
 */
public final class Json {
    private static final int MAX_DEPTH = 32;

    private Json() {
    }

    public static Object parse(String text) {
        if (text == null) {
            throw Validator.fail("Request body is empty.");
        }
        Parser p = new Parser(text);
        Object value = p.parseValue(0);
        p.skipWhitespace();
        if (!p.atEnd()) {
            throw p.fail("Unexpected text after the JSON value");
        }
        return value;
    }

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value);
        return sb.toString();
    }

    private static void writeValue(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String s) {
            writeString(sb, s);
        } else if (v instanceof Boolean b) {
            sb.append(b.booleanValue());
        } else if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            sb.append(Double.isFinite(d) ? BigDecimal.valueOf(d).stripTrailingZeros().toPlainString() : "null");
        } else if (v instanceof Number n) {
            sb.append(n);
        } else if (v instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeString(sb, String.valueOf(e.getKey()));
                sb.append(':');
                writeValue(sb, e.getValue());
            }
            sb.append('}');
        } else if (v instanceof Iterable<?> it) {
            sb.append('[');
            boolean first = true;
            for (Object o : it) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeValue(sb, o);
            }
            sb.append(']');
        } else {
            writeString(sb, v.toString());
        }
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    private static final class Parser {
        private final String s;
        private int i = 0;

        Parser(String s) {
            this.s = s;
        }

        boolean atEnd() {
            return i >= s.length();
        }

        Validator.InvalidInputException fail(String message) {
            return Validator.fail("Invalid JSON: " + message + ".");
        }

        void skipWhitespace() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        Object parseValue(int depth) {
            if (depth > MAX_DEPTH) {
                throw fail("nesting is too deep");
            }
            skipWhitespace();
            if (atEnd()) {
                throw fail("unexpected end of input");
            }
            char c = s.charAt(i);
            if (c == '{') {
                return parseObject(depth);
            }
            if (c == '[') {
                return parseArray(depth);
            }
            if (c == '"') {
                return parseString();
            }
            if (s.startsWith("true", i)) {
                i += 4;
                return Boolean.TRUE;
            }
            if (s.startsWith("false", i)) {
                i += 5;
                return Boolean.FALSE;
            }
            if (s.startsWith("null", i)) {
                i += 4;
                return null;
            }
            return parseNumber();
        }

        Map<String, Object> parseObject(int depth) {
            Map<String, Object> map = new LinkedHashMap<>();
            i++;
            skipWhitespace();
            if (peek('}')) {
                i++;
                return map;
            }
            while (true) {
                skipWhitespace();
                if (!peek('"')) {
                    throw fail("expected a property name");
                }
                String key = parseString();
                skipWhitespace();
                if (!peek(':')) {
                    throw fail("expected ':'");
                }
                i++;
                map.put(key, parseValue(depth + 1));
                skipWhitespace();
                if (peek(',')) {
                    i++;
                } else if (peek('}')) {
                    i++;
                    return map;
                } else {
                    throw fail("expected ',' or '}'");
                }
            }
        }

        List<Object> parseArray(int depth) {
            List<Object> list = new ArrayList<>();
            i++;
            skipWhitespace();
            if (peek(']')) {
                i++;
                return list;
            }
            while (true) {
                list.add(parseValue(depth + 1));
                skipWhitespace();
                if (peek(',')) {
                    i++;
                } else if (peek(']')) {
                    i++;
                    return list;
                } else {
                    throw fail("expected ',' or ']'");
                }
            }
        }

        String parseString() {
            StringBuilder sb = new StringBuilder();
            i++;
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c < 0x20) {
                    throw fail("control character in string");
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                if (atEnd()) {
                    break;
                }
                char e = s.charAt(i++);
                switch (e) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (i + 4 > s.length()) {
                            throw fail("bad unicode escape");
                        }
                        try {
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw fail("bad unicode escape");
                        }
                        i += 4;
                    }
                    default -> throw fail("bad escape sequence");
                }
            }
            throw fail("unterminated string");
        }

        Double parseNumber() {
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
                i++;
            }
            if (start == i) {
                throw fail("unexpected character '" + s.charAt(i) + "'");
            }
            try {
                double d = Double.parseDouble(s.substring(start, i));
                if (!Double.isFinite(d)) {
                    throw fail("number out of range");
                }
                return d;
            } catch (NumberFormatException e) {
                throw fail("bad number");
            }
        }

        boolean peek(char c) {
            return i < s.length() && s.charAt(i) == c;
        }
    }
}
