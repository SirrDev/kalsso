package com.footbooking.notification;

import java.util.LinkedHashMap;
import java.util.Map;

/** Lit un objet JSON plat {"cle":"valeur",...} (valeurs texte uniquement), comme ecrit par NotificationService. */
final class PayloadJson {

    private PayloadJson() {
    }

    static Map<String, String> parse(String json) {
        Map<String, String> out = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return out;
        }
        Cursor c = new Cursor(json);
        c.skipSpaces();
        c.expect('{');
        c.skipSpaces();
        if (c.peek() == '}') {
            return out;
        }
        while (true) {
            c.skipSpaces();
            String key = c.string();
            c.skipSpaces();
            c.expect(':');
            c.skipSpaces();
            out.put(key, c.string());
            c.skipSpaces();
            char next = c.next();
            if (next == '}') {
                return out;
            }
            if (next != ',') {
                throw new IllegalArgumentException("JSON invalide");
            }
        }
    }

    private static final class Cursor {
        private final String s;
        private int i;

        Cursor(String s) {
            this.s = s;
        }

        char peek() {
            if (i >= s.length()) {
                throw new IllegalArgumentException("JSON invalide");
            }
            return s.charAt(i);
        }

        char next() {
            char c = peek();
            i++;
            return c;
        }

        void expect(char expected) {
            if (next() != expected) {
                throw new IllegalArgumentException("JSON invalide");
            }
        }

        void skipSpaces() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        String string() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                char e = next();
                switch (e) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        if (i + 4 > s.length()) {
                            throw new IllegalArgumentException("JSON invalide");
                        }
                        try {
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw new IllegalArgumentException("JSON invalide");
                        }
                        i += 4;
                    }
                    default -> throw new IllegalArgumentException("JSON invalide");
                }
            }
        }
    }
}