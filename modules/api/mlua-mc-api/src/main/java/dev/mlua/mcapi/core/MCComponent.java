package dev.mlua.mcapi.core;

public interface MCComponent {
    String getString();

    static MCMutableComponent literal(String text) {
        return new MCMutableComponent() {
            private String value = text;

            @Override
            public String getString() {
                return value;
            }

            @Override
            public void setString(String text) {
                this.value = text;
            }
        };
    }

    static String toJson(MCComponent component) {
        return "{\"text\":\"" + component.getString().replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
    }

    static MCMutableComponent fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return literal("");
        }
        String trimmed = json.trim();
        if (trimmed.startsWith("{\"text\":") && trimmed.endsWith("}")) {
            int start = trimmed.indexOf("\"text\":\"") + 8;
            int end = trimmed.lastIndexOf("\"");
            if (start > 7 && end > start) {
                return literal(trimmed.substring(start, end));
            }
        }
        return literal(trimmed);
    }
}
