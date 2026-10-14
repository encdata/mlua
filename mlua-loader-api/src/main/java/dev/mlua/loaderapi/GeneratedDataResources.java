package dev.mlua.loaderapi;

import com.google.gson.JsonObject;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class GeneratedDataResources {
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern RESOURCE_PATH = Pattern.compile("[a-z0-9_./-]+");
    private static final Map<String, Entry> RESOURCES = new ConcurrentHashMap<>();

    private GeneratedDataResources() { }

    public static void register(String owner, String resourceType, String id, JsonObject definition) {
        if (owner == null || !NAMESPACE.matcher(owner).matches()) {
            throw new IllegalArgumentException("Data resource owner must be a valid namespace");
        }
        String type = normalizePath(resourceType, "resource type");
        String[] location = normalizeId(owner, id);
        if (definition == null) throw new IllegalArgumentException("Data resource definition is required");
        String key = location[0] + ":" + type + "/" + location[1];
        Entry entry = new Entry(owner, definition.deepCopy());
        if (RESOURCES.putIfAbsent(key, entry) != null) {
            throw new IllegalStateException("Data resource is already registered: " + key);
        }
    }

    public static Map<String, JsonObject> snapshot() {
        Map<String, JsonObject> result = new TreeMap<>();
        RESOURCES.forEach((key, entry) -> result.put(key, entry.definition().deepCopy()));
        return Map.copyOf(result);
    }

    public static boolean removeOwner(String owner) {
        if (owner == null || owner.isBlank()) return false;
        return RESOURCES.entrySet().removeIf(entry -> owner.equals(entry.getValue().owner()));
    }

    public static boolean unregister(String owner, String resourceType, String id) {
        if (owner == null || !NAMESPACE.matcher(owner).matches()) return false;
        String type = normalizePath(resourceType, "resource type");
        String[] location = normalizeId(owner, id);
        String key = location[0] + ":" + type + "/" + location[1];
        Entry entry = RESOURCES.get(key);
        return entry != null && owner.equals(entry.owner()) && RESOURCES.remove(key, entry);
    }

    private static String normalizePath(String value, String label) {
        if (value == null || value.isBlank() || value.startsWith("/") || value.endsWith("/")
                || value.contains("\\") || value.endsWith(".json") || !RESOURCE_PATH.matcher(value).matches()) {
            throw new IllegalArgumentException("Data " + label + " must be a relative resource path");
        }
        for (String segment : value.split("/")) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Data " + label + " contains an invalid path segment");
            }
        }
        return value;
    }

    private static String[] normalizeId(String owner, String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Data resource id must be non-empty");
        int separator = id.indexOf(':');
        String namespace = separator < 0 ? owner : id.substring(0, separator);
        String path = separator < 0 ? id : id.substring(separator + 1);
        if (!NAMESPACE.matcher(namespace).matches()) throw new IllegalArgumentException("Data resource id has an invalid namespace");
        if (!owner.equals(namespace)) throw new IllegalArgumentException("Data resource id must use the addon's namespace");
        return new String[]{namespace, normalizePath(path, "resource id")};
    }

    private record Entry(String owner, JsonObject definition) { }
}
