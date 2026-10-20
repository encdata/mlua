package dev.mlua.loaderapi;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayDeque;
import java.util.Map;

/** Shared defensive limits for JSON resources supplied through the loader API. */
final class GeneratedResourceJson {
    static final int MAX_DEPTH = 64;
    static final int MAX_ENTRIES = 16_384;
    static final int MAX_CHARACTERS = 1_048_576;

    private GeneratedResourceJson() { }

    static void validate(JsonObject root) {
        if (root == null) throw new IllegalArgumentException("Resource definition is required");
        ArrayDeque<Node> pending = new ArrayDeque<>();
        pending.push(new Node(root, 0));
        long entries = 0;
        long characters = 0;
        while (!pending.isEmpty()) {
            Node node = pending.pop();
            JsonElement value = node.value();
            if (node.depth() > MAX_DEPTH) {
                throw new IllegalArgumentException("Generated resource JSON exceeds " + MAX_DEPTH + " nested levels");
            }
            if (value.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
                    entries++;
                    characters += entry.getKey().length();
                    checkBudget(entries, characters);
                    pending.push(new Node(entry.getValue(), node.depth() + 1));
                }
            } else if (value.isJsonArray()) {
                JsonArray array = value.getAsJsonArray();
                entries += array.size();
                checkBudget(entries, characters);
                for (JsonElement child : array) pending.push(new Node(child, node.depth() + 1));
            } else if (value.isJsonPrimitive()) {
                JsonPrimitive primitive = value.getAsJsonPrimitive();
                if (primitive.isString()) characters += primitive.getAsString().length();
                else characters += primitive.toString().length();
                checkBudget(entries, characters);
            }
        }
    }

    private static void checkBudget(long entries, long characters) {
        if (entries > MAX_ENTRIES) {
            throw new IllegalArgumentException("Generated resource JSON exceeds " + MAX_ENTRIES + " entries");
        }
        if (characters > MAX_CHARACTERS) {
            throw new IllegalArgumentException("Generated resource JSON exceeds " + MAX_CHARACTERS + " characters");
        }
    }

    private record Node(JsonElement value, int depth) { }
}
