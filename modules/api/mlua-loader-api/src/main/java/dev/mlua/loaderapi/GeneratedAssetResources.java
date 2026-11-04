package dev.mlua.loaderapi;

import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

public final class GeneratedAssetResources {
    public static final int MAX_TOTAL_RESOURCES = 16_384;
    public static final int MAX_RESOURCES_PER_OWNER = 4_096;
    public static final int MAX_KEYS_PER_RESOURCE = 16_384;
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern RESOURCE_PATH = Pattern.compile("[a-z0-9_./-]+\\.json");
    private static final Map<String, Map<String, JsonObject>> RESOURCES = new LinkedHashMap<>();
    private static final Map<String, Integer> RESOURCES_BY_OWNER = new LinkedHashMap<>();
    private static int totalResources;

    private GeneratedAssetResources() { }

    public static synchronized void register(String owner, String path, JsonObject definition) {
        if (owner == null || !NAMESPACE.matcher(owner).matches()) {
            throw new IllegalArgumentException("Asset resource owner must be a valid namespace");
        }
        String normalizedPath = normalizePath(path);
        if (definition == null) throw new IllegalArgumentException("Asset resource definition is required");
        if (definition.entrySet().isEmpty()) throw new IllegalArgumentException("Asset resource definition must contain at least one top-level key");
        GeneratedResourceJson.validate(definition);
        Map<String, JsonObject> existingFiles = RESOURCES.get(owner);
        JsonObject current = existingFiles == null ? null : existingFiles.get(normalizedPath);
        JsonObject merged = current == null ? new JsonObject() : current.deepCopy();
        if ((long) merged.size() + definition.size() > MAX_KEYS_PER_RESOURCE) {
            throw new IllegalStateException("Generated asset resource '" + owner + ":" + normalizedPath
                    + "' exceeds the " + MAX_KEYS_PER_RESOURCE + " top-level key limit");
        }
        for (Map.Entry<String, com.google.gson.JsonElement> entry : definition.entrySet()) {
            if (merged.has(entry.getKey())) {
                throw new IllegalStateException("Asset resource key is already registered: " + owner + ":" + normalizedPath + "#" + entry.getKey());
            }
            merged.add(entry.getKey(), entry.getValue().deepCopy());
        }
        if (current == null) {
            int ownerCount = RESOURCES_BY_OWNER.getOrDefault(owner, 0);
            if (totalResources >= MAX_TOTAL_RESOURCES) {
                throw new IllegalStateException("MLua generated asset resource limit reached (" + MAX_TOTAL_RESOURCES + ")");
            }
            if (ownerCount >= MAX_RESOURCES_PER_OWNER) {
                throw new IllegalStateException("Generated asset resource limit reached for addon '" + owner
                        + "' (" + MAX_RESOURCES_PER_OWNER + ")");
            }
            RESOURCES.computeIfAbsent(owner, ignored -> new LinkedHashMap<>());
            RESOURCES_BY_OWNER.put(owner, ownerCount + 1);
            totalResources++;
        }
        Map<String, JsonObject> files = RESOURCES.get(owner);
        files.put(normalizedPath, merged);
    }

    public static synchronized Map<String, JsonObject> snapshot() {
        Map<String, JsonObject> result = new TreeMap<>();
        RESOURCES.forEach((owner, files) -> files.forEach((path, definition) ->
            result.put(owner + ":" + path, definition.deepCopy())));
        return Collections.unmodifiableMap(result);
    }

    public static synchronized boolean removeOwner(String owner) {
        Map<String, JsonObject> removed = owner == null ? null : RESOURCES.remove(owner);
        if (removed == null) return false;
        RESOURCES_BY_OWNER.remove(owner);
        totalResources -= removed.size();
        return true;
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
        if (restored.size() == 0) {
            files.remove(normalizedPath);
            totalResources--;
            int remaining = RESOURCES_BY_OWNER.getOrDefault(owner, 1) - 1;
            if (remaining <= 0) RESOURCES_BY_OWNER.remove(owner);
            else RESOURCES_BY_OWNER.put(owner, remaining);
        }
        else files.put(normalizedPath, restored);
        if (files.isEmpty()) {
            RESOURCES.remove(owner);
            RESOURCES_BY_OWNER.remove(owner);
        }
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
