package com.payton.touchblocker.profile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

final class StrictJsonParser {
    private final String raw;
    private int index;

    private StrictJsonParser(String raw) {
        this.raw = raw;
    }

    static Object parse(String raw) throws JSONException {
        if (raw == null) {
            throw error("Input is null", 0);
        }
        StrictJsonParser parser = new StrictJsonParser(raw);
        parser.skipWhitespace();
        if (parser.isAtEnd()) {
            throw parser.error("Input is empty");
        }
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (!parser.isAtEnd()) {
            throw parser.error("Unexpected trailing content");
        }
        return value;
    }

    private Object parseValue() throws JSONException {
        if (isAtEnd()) {
            throw error("Expected a JSON value");
        }
        char next = raw.charAt(index);
        switch (next) {
            case '{':
                return parseObject();
            case '[':
                return parseArray();
            case '"':
                return parseString();
            case 't':
                consumeLiteral("true");
                return Boolean.TRUE;
            case 'f':
                consumeLiteral("false");
                return Boolean.FALSE;
            case 'n':
                consumeLiteral("null");
                return JSONObject.NULL;
            default:
                if (next == '-' || isDigit(next)) {
                    return parseNumber();
                }
                throw error("Expected a standard JSON value");
        }
    }

    private JSONObject parseObject() throws JSONException {
        expect('{');
        JSONObject object = new JSONObject();
        Set<String> keys = new HashSet<>();
        skipWhitespace();
        if (consumeIf('}')) {
            return object;
        }

        while (true) {
            if (isAtEnd() || raw.charAt(index) != '"') {
                throw error("Object keys must use double quotes");
            }
            String key = parseString();
            if (!keys.add(key)) {
                throw error("Duplicate object key: " + key);
            }
            skipWhitespace();
            expect(':');
            skipWhitespace();
            object.put(key, parseValue());
            skipWhitespace();
            if (consumeIf('}')) {
                return object;
            }
            expect(',');
            skipWhitespace();
            if (!isAtEnd() && raw.charAt(index) == '}') {
                throw error("Trailing commas are not allowed");
            }
        }
    }

    private JSONArray parseArray() throws JSONException {
        expect('[');
        JSONArray array = new JSONArray();
        skipWhitespace();
        if (consumeIf(']')) {
            return array;
        }

        while (true) {
            array.put(parseValue());
            skipWhitespace();
            if (consumeIf(']')) {
                return array;
            }
            expect(',');
            skipWhitespace();
            if (!isAtEnd() && raw.charAt(index) == ']') {
                throw error("Trailing commas are not allowed");
            }
        }
    }

    private String parseString() throws JSONException {
        expect('"');
        StringBuilder value = new StringBuilder();
        while (!isAtEnd()) {
            char next = raw.charAt(index++);
            if (next == '"') {
                return value.toString();
            }
            if (next < 0x20) {
                throw error("Unescaped control character in string");
            }
            if (next != '\\') {
                value.append(next);
                continue;
            }
            if (isAtEnd()) {
                throw error("Unterminated escape sequence");
            }
            char escaped = raw.charAt(index++);
            switch (escaped) {
                case '"':
                case '\\':
                case '/':
                    value.append(escaped);
                    break;
                case 'b':
                    value.append('\b');
                    break;
                case 'f':
                    value.append('\f');
                    break;
                case 'n':
                    value.append('\n');
                    break;
                case 'r':
                    value.append('\r');
                    break;
                case 't':
                    value.append('\t');
                    break;
                case 'u':
                    value.append(parseUnicodeEscape());
                    break;
                default:
                    throw error("Invalid string escape: \\" + escaped);
            }
        }
        throw error("Unterminated string");
    }

    private char parseUnicodeEscape() throws JSONException {
        if (raw.length() - index < 4) {
            throw error("Incomplete unicode escape");
        }
        int value = 0;
        for (int count = 0; count < 4; count++) {
            char digit = raw.charAt(index++);
            int hex = asciiHexValue(digit);
            if (hex < 0) {
                throw error("Invalid unicode escape");
            }
            value = (value << 4) | hex;
        }
        return (char) value;
    }

    private BigDecimal parseNumber() throws JSONException {
        int start = index;
        consumeIf('-');
        if (isAtEnd()) {
            throw error("Incomplete JSON number");
        }

        char firstDigit = raw.charAt(index);
        if (firstDigit == '0') {
            index++;
            if (!isAtEnd() && isDigit(raw.charAt(index))) {
                throw error("Leading zeroes are not allowed");
            }
        } else if (firstDigit >= '1' && firstDigit <= '9') {
            consumeDigits();
        } else {
            throw error("Expected a digit in JSON number");
        }

        if (consumeIf('.')) {
            if (isAtEnd() || !isDigit(raw.charAt(index))) {
                throw error("Fraction must contain a digit");
            }
            consumeDigits();
        }

        if (!isAtEnd() && (raw.charAt(index) == 'e' || raw.charAt(index) == 'E')) {
            index++;
            if (!isAtEnd() && (raw.charAt(index) == '+' || raw.charAt(index) == '-')) {
                index++;
            }
            if (isAtEnd() || !isDigit(raw.charAt(index))) {
                throw error("Exponent must contain a digit");
            }
            consumeDigits();
        }

        String number = raw.substring(start, index);
        try {
            return new BigDecimal(number);
        } catch (NumberFormatException invalid) {
            throw error("Invalid JSON number");
        }
    }

    private void consumeLiteral(String literal) throws JSONException {
        if (!raw.regionMatches(index, literal, 0, literal.length())) {
            throw error("Invalid JSON literal");
        }
        index += literal.length();
    }

    private void consumeDigits() {
        while (!isAtEnd() && isDigit(raw.charAt(index))) {
            index++;
        }
    }

    private void skipWhitespace() {
        while (!isAtEnd()) {
            char next = raw.charAt(index);
            if (next != ' ' && next != '\t' && next != '\n' && next != '\r') {
                return;
            }
            index++;
        }
    }

    private void expect(char expected) throws JSONException {
        if (isAtEnd() || raw.charAt(index) != expected) {
            throw error("Expected '" + expected + "'");
        }
        index++;
    }

    private boolean consumeIf(char expected) {
        if (isAtEnd() || raw.charAt(index) != expected) {
            return false;
        }
        index++;
        return true;
    }

    private boolean isAtEnd() {
        return index >= raw.length();
    }

    private JSONException error(String message) {
        return error(message, index);
    }

    private static JSONException error(String message, int index) {
        return new JSONException(message + " at character " + index);
    }

    private static boolean isDigit(char value) {
        return value >= '0' && value <= '9';
    }

    private static int asciiHexValue(char value) {
        if (value >= '0' && value <= '9') {
            return value - '0';
        }
        if (value >= 'a' && value <= 'f') {
            return value - 'a' + 10;
        }
        if (value >= 'A' && value <= 'F') {
            return value - 'A' + 10;
        }
        return -1;
    }
}
