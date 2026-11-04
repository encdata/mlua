package dev.mlua.mcapi.core;

public interface MCResourceLocation {
    String getNamespace();
    String getPath();
    String toString();

    static MCResourceLocation of(String namespace, String path) {
        return new MCResourceLocation() {
            private final String ns = namespace;
            private final String p = path;

            @Override
            public String getNamespace() {
                return ns;
            }

            @Override
            public String getPath() {
                return p;
            }

            @Override
            public String toString() {
                return ns + ":" + p;
            }
        };
    }

    static MCResourceLocation parse(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid resource location: " + id);
        }
        String value = id.trim();
        int split = value.indexOf(':');
        if (split < 0) {
            return of("minecraft", value);
        }
        String namespace = value.substring(0, split);
        String path = value.substring(split + 1);
        if (namespace.isEmpty() || path.isEmpty()) {
            throw new IllegalArgumentException("Invalid resource location: " + id);
        }
        return of(namespace, path);
    }
}
