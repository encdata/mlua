package dev.mlua.runtime.client;

/** Shared, allocation-free character filters for numeric configuration text fields. */
final class MLuaConfigInputFilter {
    private MLuaConfigInputFilter() { }

    static boolean accepts(String type, String text) {
        if (type == null) return false;
        return switch (type) {
            case "int", "long" -> integerText(text);
            case "double" -> decimalText(text);
            case "string", "enum" -> true;
            default -> false;
        };
    }

    private static boolean integerText(String text) {
        if (text == null) return false;
        int index = text.startsWith("-") ? 1 : 0;
        for (; index < text.length(); index++) {
            char value = text.charAt(index);
            if (value < '0' || value > '9') return false;
        }
        return true;
    }

    private static boolean decimalText(String text) {
        if (text == null) return false;
        int index = text.startsWith("-") ? 1 : 0;
        while (index < text.length() && isDigit(text.charAt(index))) index++;
        if (index < text.length() && text.charAt(index) == '.') {
            index++;
            while (index < text.length() && isDigit(text.charAt(index))) index++;
        }
        return index == text.length();
    }

    private static boolean isDigit(char value) {
        return value >= '0' && value <= '9';
    }
}
