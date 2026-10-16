package dev.mlua.loaderapi;

import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

public final class GeneratedAssetResources {
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern RESOURCE_PATH = Pattern.compile("[a-z0-9_./-]+\\.json");
    private static final Map<String, Map<String, JsonObject>> RESOURCES = new LinkedHashMap<>();

    private GeneratedAssetResources() { }

    public static synchronized void register(String owner, String path, JsonObject definition) {
        if (owner == null || !NAMESPACE.matcher(owner).matches()) {
            throw new IllegalArgumentException("Asset resource owner must be a valid namespace");
        }
        String normalizedPath = normalizePath(path);
        if (definition == null) throw new IllegalArgumentException("Asset resource definition is required");
        Map<String, JsonObject> files = RESOURCES.computeIfAbsent(owner, ignored -> new LinkedHashMap<>());
        JsonObject current = files.get(normalizedPath);
        JsonObject merged = current == null ? new JsonObject() : current.deepCopy();
        for (Map.Entry<String, com.google.gson.JsonElement> entry : definition.entrySet()) {
            if (merged.has(entry.getKey())) {
                throw new IllegalStateException("Asset resource key is already registered: " + owner + ":" + normalizedPath + "#" + entry.getKey());
            }
            merged.add(entry.getKey(), entry.getValue().deepCopy());
        }
        files.put(normalizedPath, merged);
    }

    public static synchronized Map<String, JsonObject> snapshot() {
        Map<String, JsonObject> result = new TreeMap<>();
        RESOURCES.forEach((owner, files) -> files.forEach((path, definition) ->
            result.put(owner + ":" + path, definition.deepCopy())));
        return Map.copyOf(result);
    }

    public static synchronized boolean removeOwner(String owner) {
        return owner != null && RESOURCES.remove(owner) != null;
    }

    public static synchronized boolean unregister(String owner, String path, JsonObject contribution) {
        if (owner == null || !NAMESPACE.matcher(owner).matches() || contribution == null) return false;
        String normalizedPath = normalizePath(path);
        Map<String, JsonObject> files = RESOURCES.get(owner);
        JsonObject current = files == null ? null : files.get(normalizedPath);
        if (current == null) return false;

        JsonObject restored = current.deepCopy();
        boolean changed = false;
        for (Map.Entry<String, com.google.gson.JsonElement> entry : contribution.entrySet()) {
            if (entry.getValue().equals(restored.get(entry.getKey()))) {
                restored.remove(entry.getKey());
                changed = true;
            }
        }
        if (!changed) return false;
        if (restored.size() == 0) files.remove(normalizedPath);
        else files.put(normalizedPath, restored);
        if (files.isEmpty()) RESOURCES.remove(owner);
        return true;
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank() || path.startsWith("/") || path.endsWith("/")
                || path.contains("\\") || !RESOURCE_PATH.matcher(path).matches()) {
            throw new IllegalArgumentException("Asset resource path must be a relative JSON path");
        }
        for (String segment : path.split("/")) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Asset resource path contains an invalid path segment");
            }
        }
        return path;
    }
}
