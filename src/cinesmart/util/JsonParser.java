package cinesmart.util;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small strict JSON parser for the TMDb and OpenStreetMap HTTP responses. */
public final class JsonParser {

    private final String text;
    private int index;

    private JsonParser(String text) {
        this.text = text == null ? "" : text;
    }

    public static Object parse(String text) {
        JsonParser parser = new JsonParser(text);
        Object value = parser.readValue(0);
        parser.skipWhitespace();
        if (parser.index != parser.text.length()) {
            throw parser.error("Unexpected trailing content");
        }
        return value;
    }

    private Object readValue(int depth) {
        if (depth > 100) {
            throw error("JSON nesting is too deep");
        }
        skipWhitespace();
        if (index >= text.length()) {
            throw error("Expected a JSON value");
        }
        char current = text.charAt(index);
        switch (current) {
            case '{':
                return readObject(depth + 1);
            case '[':
                return readArray(depth + 1);
            case '"':
                return readString();
            case 't':
                readLiteral("true");
                return Boolean.TRUE;
            case 'f':
                readLiteral("false");
                return Boolean.FALSE;
            case 'n':
                readLiteral("null");
                return null;
            default:
                if (current == '-' || Character.isDigit(current)) {
                    return readNumber();
                }
                throw error("Unexpected character");
        }
    }

    private Map<String, Object> readObject(int depth) {
        index++;
        Map<String, Object> object = new LinkedHashMap<>();
        skipWhitespace();
        if (take('}')) {
            return object;
        }
        while (true) {
            skipWhitespace();
            if (index >= text.length() || text.charAt(index) != '"') {
                throw error("Expected an object key");
            }
            String key = readString();
            skipWhitespace();
            require(':');
            object.put(key, readValue(depth));
            skipWhitespace();
            if (take('}')) {
                return object;
            }
            require(',');
        }
    }

    private List<Object> readArray(int depth) {
        index++;
        List<Object> array = new ArrayList<>();
        skipWhitespace();
        if (take(']')) {
            return array;
        }
        while (true) {
            array.add(readValue(depth));
            skipWhitespace();
            if (take(']')) {
                return array;
            }
            require(',');
        }
    }

    private String readString() {
        require('"');
        StringBuilder result = new StringBuilder();
        while (index < text.length()) {
            char current = text.charAt(index++);
            if (current == '"') {
                return result.toString();
            }
            if (current == '\\') {
                if (index >= text.length()) {
                    throw error("Unfinished string escape");
                }
                char escaped = text.charAt(index++);
                switch (escaped) {
                    case '"':
                    case '\\':
                    case '/':
                        result.append(escaped);
                        break;
                    case 'b':
                        result.append('\b');
                        break;
                    case 'f':
                        result.append('\f');
                        break;
                    case 'n':
                        result.append('\n');
                        break;
                    case 'r':
                        result.append('\r');
                        break;
                    case 't':
                        result.append('\t');
                        break;
                    case 'u':
                        result.append(readUnicodeEscape());
                        break;
                    default:
                        throw error("Invalid string escape");
                }
            } else {
                if (current < 0x20) {
                    throw error("Unescaped control character in string");
                }
                result.append(current);
            }
        }
        throw error("Unterminated string");
    }

    private char readUnicodeEscape() {
        if (index + 4 > text.length()) {
            throw error("Incomplete Unicode escape");
        }
        int value = 0;
        for (int count = 0; count < 4; count++) {
            int digit = Character.digit(text.charAt(index++), 16);
            if (digit < 0) {
                throw error("Invalid Unicode escape");
            }
            value = (value << 4) | digit;
        }
        return (char) value;
    }

    private BigDecimal readNumber() {
        int start = index;
        take('-');
        if (take('0')) {
            if (index < text.length() && Character.isDigit(text.charAt(index))) {
                throw error("Leading zero in number");
            }
        } else {
            requireDigits();
        }
        if (take('.')) {
            requireDigits();
        }
        if (take('e') || take('E')) {
            if (!take('+')) {
                take('-');
            }
            requireDigits();
        }
        try {
            return new BigDecimal(text.substring(start, index));
        } catch (NumberFormatException e) {
            throw error("Invalid number");
        }
    }

    private void requireDigits() {
        int start = index;
        while (index < text.length() && Character.isDigit(text.charAt(index))) {
            index++;
        }
        if (start == index) {
            throw error("Expected a digit");
        }
    }

    private void readLiteral(String literal) {
        if (!text.regionMatches(index, literal, 0, literal.length())) {
            throw error("Invalid JSON literal");
        }
        index += literal.length();
    }

    private void skipWhitespace() {
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
    }

    private boolean take(char expected) {
        if (index < text.length() && text.charAt(index) == expected) {
            index++;
            return true;
        }
        return false;
    }

    private void require(char expected) {
        if (!take(expected)) {
            throw error("Expected '" + expected + "'");
        }
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at character " + index);
    }
}
