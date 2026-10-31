package com.friendfinder.util;

import java.util.*;

/** Small JSON reader/writer: objects, arrays, strings, booleans, null and numbers. */
public final class Json {
    private Json() {}
    public static Object parse(String source) {
        Reader reader = new Reader(source); Object value = reader.value(0); reader.space();
        if (reader.index != source.length()) throw new IllegalArgumentException("Invalid JSON");
        return value;
    }
    public static String stringify(Object value) {
        if (value == null) return "null";
        if (value instanceof java.sql.Timestamp timestamp) return quote(timestamp.toInstant().toString());
        if (value instanceof String || value instanceof Enum<?> || value instanceof java.time.temporal.TemporalAccessor || value instanceof java.sql.Date) return quote(value.toString());
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            List<String> entries = new ArrayList<>();
            map.forEach((key, item) -> entries.add(quote(key.toString()) + ":" + stringify(item)));
            return "{" + String.join(",", entries) + "}";
        }
        if (value instanceof Iterable<?> items) {
            List<String> entries = new ArrayList<>(); for (Object item : items) entries.add(stringify(item));
            return "[" + String.join(",", entries) + "]";
        }
        throw new IllegalArgumentException("Unsupported JSON type");
    }
    private static String quote(String source) {
        StringBuilder result = new StringBuilder("\"");
        for (char c : source.toCharArray()) {
            switch (c) {
                case '"' -> result.append("\\\""); case '\\' -> result.append("\\\\");
                case '\n' -> result.append("\\n"); case '\r' -> result.append("\\r"); case '\t' -> result.append("\\t");
                default -> { if (c < 32) result.append(String.format("\\u%04x", (int)c)); else result.append(c); }
            }
        }
        return result.append('"').toString();
    }
    private static final class Reader {
        private final String source; private int index;
        Reader(String source) { this.source = source; }
        void space() { while (index < source.length() && Character.isWhitespace(source.charAt(index))) index++; }
        char take() { if (index >= source.length()) throw new IllegalArgumentException("Incomplete JSON"); return source.charAt(index++); }
        Object value(int depth) {
            if (depth > 30) throw new IllegalArgumentException("JSON nesting too deep");
            space(); char c = take();
            if (c == '"') return string();
            if (c == '{') {
                Map<String, Object> map = new LinkedHashMap<>(); space();
                if (index < source.length() && source.charAt(index) == '}') { index++; return map; }
                do {
                    space(); if (take() != '"') throw new IllegalArgumentException("Expected JSON key");
                    String key = string(); space(); if (take() != ':') throw new IllegalArgumentException("Expected colon");
                    if (map.containsKey(key)) throw new IllegalArgumentException("Duplicate JSON key");
                    map.put(key, value(depth + 1)); space(); c = take();
                } while (c == ',');
                if (c != '}') throw new IllegalArgumentException("Expected closing object"); return map;
            }
            if (c == '[') {
                List<Object> list = new ArrayList<>(); space();
                if (index < source.length() && source.charAt(index) == ']') { index++; return list; }
                do { list.add(value(depth + 1)); space(); c = take(); } while (c == ',');
                if (c != ']') throw new IllegalArgumentException("Expected closing array"); return list;
            }
            int start = index - 1;
            while (index < source.length() && ",]} \r\n\t".indexOf(source.charAt(index)) == -1) index++;
            String token = source.substring(start, index);
            return switch (token) {
                case "true" -> true; case "false" -> false; case "null" -> null;
                default -> {
                    if (!token.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) throw new IllegalArgumentException("Invalid JSON value");
                    if (token.contains(".") || token.contains("e") || token.contains("E")) yield Double.valueOf(token);
                    yield Long.valueOf(token);
                }
            };
        }
        String string() {
            StringBuilder result = new StringBuilder();
            for (;;) {
                char c = take(); if (c == '"') return result.toString();
                if (c < 32) throw new IllegalArgumentException("Invalid JSON string");
                if (c != '\\') { result.append(c); continue; }
                char escape = take();
                switch (escape) {
                    case '"', '\\', '/' -> result.append(escape);
                    case 'n' -> result.append('\n'); case 'r' -> result.append('\r'); case 't' -> result.append('\t');
                    case 'b' -> result.append('\b'); case 'f' -> result.append('\f');
                    case 'u' -> { if (index + 4 > source.length()) throw new IllegalArgumentException("Invalid Unicode"); result.append((char)Integer.parseInt(source.substring(index, index + 4), 16)); index += 4; }
                    default -> throw new IllegalArgumentException("Invalid JSON escape");
                }
            }
        }
    }
}
