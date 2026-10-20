package dev.mlua.loaderapi;

import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class GeneratedDataResources {
    public static final int MAX_TOTAL_RESOURCES = 65_536;
    public static final int MAX_RESOURCES_PER_OWNER = 16_384;
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern RESOURCE_PATH = Pattern.compile("[a-z0-9_./-]+");
    private static final Map<String, Entry> RESOURCES = new ConcurrentHashMap<>();
    private static final Map<String, Integer> RESOURCES_BY_OWNER = new ConcurrentHashMap<>();

    private GeneratedDataResources() { }

    public static synchronized void register(String owner, String resourceType, String id, JsonObject definition) {
        if (owner == null || !NAMESPACE.matcher(owner).matches()) {
            throw new IllegalArgumentException("Data resource owner must be a valid namespace");
        }
        String type = normalizePath(resourceType, "resource type");
        String[] location = normalizeId(owner, id);
        if (definition == null) throw new IllegalArgumentException("Data resource definition is required");
        GeneratedResourceJson.validate(definition);
        String key = location[0] + ":" + type + "/" + location[1];
        Entry entry = new Entry(owner, definition.deepCopy());
        if (RESOURCES.containsKey(key)) {
            throw new IllegalStateException("Data resource is already registered: " + key);
        }
        int ownerCount = RESOURCES_BY_OWNER.getOrDefault(owner, 0);
        if (RESOURCES.size() >= MAX_TOTAL_RESOURCES) {
            throw new IllegalStateException("MLua generated data resource limit reached (" + MAX_TOTAL_RESOURCES + ")");
        }
        if (ownerCount >= MAX_RESOURCES_PER_OWNER) {
            throw new IllegalStateException("Generated data resource limit reached for addon '" + owner
                    + "' (" + MAX_RESOURCES_PER_OWNER + ")");
        }
        RESOURCES.put(key, entry);
        RESOURCES_BY_OWNER.put(owner, ownerCount + 1);
    }

    public static synchronized Map<String, JsonObject> snapshot() {
        Map<String, JsonObject> result = new TreeMap<>();
        RESOURCES.forEach((key, entry) -> result.put(key, entry.definition().deepCopy()));
        return Collections.unmodifiableMap(result);
    }

    /** Returns an isolated definition copy for one resource, or null when it is absent. */
    public static JsonObject get(String resourceType, String id) {
        String type = normalizePath(resourceType, "resource type");
        String namespace = namespaceOf(id);
        String[] location = normalizeId(namespace, id);
        Entry entry = RESOURCES.get(resourceKey(location, type));
        return entry == null ? null : entry.definition().deepCopy();
    }

    public static boolean contains(String resourceType, String id) {
        String type = normalizePath(resourceType, "resource type");
        String namespace = namespaceOf(id);
        String[] location = normalizeId(namespace, id);
        return RESOURCES.containsKey(resourceKey(location, type));
    }

    /** Returns sorted full resource identifiers without copying their JSON definitions. */
    public static java.util.List<String> ids(String resourceType, String namespace) {
        String type = normalizePath(resourceType, "resource type");
        if (namespace != null && !NAMESPACE.matcher(namespace).matches()) {
            throw new IllegalArgumentException("Data resource namespace is invalid");
        }
        String marker = ":" + type + "/";
        java.util.List<String> result = new java.util.ArrayList<>();
        for (String key : RESOURCES.keySet()) {
            int separator = key.indexOf(':');
            if (separator < 0 || !key.startsWith(marker, separator)) continue;
            String id = key.substring(0, separator + 1) + key.substring(separator + marker.length());
            if (namespace == null || id.startsWith(namespace + ":")) result.add(id);
        }
        result.sort(String::compareTo);
        return java.util.List.copyOf(result);
    }

    public static synchronized boolean removeOwner(String owner) {
        if (owner == null || owner.isBlank()) return false;
        boolean removed = RESOURCES.entrySet().removeIf(entry -> owner.equals(entry.getValue().owner()));
        if (removed) RESOURCES_BY_OWNER.remove(owner);
        return removed;
    }

    public static synchronized boolean unregister(String owner, String resourceType, String id) {
        if (owner == null || !NAMESPACE.matcher(owner).matches()) return false;
        String type = normalizePath(resourceType, "resource type");
        String[] location = normalizeId(owner, id);
        String key = location[0] + ":" + type + "/" + location[1];
        Entry entry = RESOURCES.get(key);
        return entry != null && owner.equals(entry.owner()) && removeResource(key, entry);
    }

    public static synchronized boolean unregister(String owner, String resourceType, String id, JsonObject expectedDefinition) {
        if (owner == null || !NAMESPACE.matcher(owner).matches()) return false;
        String type = normalizePath(resourceType, "resource type");
        String[] location = normalizeId(owner, id);
        String key = location[0] + ":" + type + "/" + location[1];
        Entry entry = RESOURCES.get(key);
        return entry != null && owner.equals(entry.owner())
                && entry.definition().equals(expectedDefinition)
                && removeResource(key, entry);
    }

    private static boolean removeResource(String key, Entry expected) {
        if (!RESOURCES.remove(key, expected)) return false;
        int remaining = RESOURCES_BY_OWNER.getOrDefault(expected.owner(), 1) - 1;
        if (remaining <= 0) RESOURCES_BY_OWNER.remove(expected.owner());
        else RESOURCES_BY_OWNER.put(expected.owner(), remaining);
        return true;
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

    private static String namespaceOf(String id) {
        if (id == null) throw new IllegalArgumentException("Data resource id must be non-empty");
        int separator = id.indexOf(':');
        String namespace = separator < 0 ? "" : id.substring(0, separator);
        if (!NAMESPACE.matcher(namespace).matches()) throw new IllegalArgumentException("Data resource id has an invalid namespace");
        return namespace;
    }

    private static String resourceKey(String[] location, String type) {
        return location[0] + ":" + type + "/" + location[1];
    }

    private record Entry(String owner, JsonObject definition) { }
}
