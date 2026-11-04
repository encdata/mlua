package dev.mlua.mc.impl.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MLuaFabricConfigHandler {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path configFile;
    private final JsonObject defaults;
    private JsonObject data;

    public MLuaFabricConfigHandler(Path configFile) {
        this.configFile = configFile;
        this.defaults = new JsonObject();
        this.data = null;
    }

    public void define(String path, Object defaultValue) {
        String[] parts = path.split("\\.");
        JsonObject parent = defaults;
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            if (!parent.has(part) || !parent.get(part).isJsonObject()) {
                parent.add(part, new JsonObject());
            }
            parent = parent.getAsJsonObject(part);
        }
        parent.add(parts[parts.length - 1], GSON.toJsonTree(defaultValue));
    }

    public void load() {
        if (Files.exists(configFile)) {
            try (Reader reader = Files.newBufferedReader(configFile)) {
                JsonObject parsed = JsonParser.parseReader(reader).getAsJsonObject();
                data = mergeDefaults(parsed);
            } catch (IOException e) {
                data = deepCopy(defaults);
            }
        } else {
            data = deepCopy(defaults);
            save();
        }
    }

    public void save() {
        try {
            Files.createDirectories(configFile.getParent());
            try (Writer writer = Files.newBufferedWriter(configFile)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to save config: " + configFile, e);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String path, Class<T> type) {
        if (data == null) {
            load();
        }
        JsonElement element = resolve(data, path);
        if (element == null) {
            element = resolve(defaults, path);
        }
        if (element == null) {
            return null;
        }
        return GSON.fromJson(element, type);
    }

    public void set(String path, Object value) {
        if (data == null) {
            load();
        }
        String[] parts = path.split("\\.");
        JsonObject parent = data;
        for (int i = 0; i < parts.length - 1; i++) {
            if (!parent.has(parts[i]) || !parent.get(parts[i]).isJsonObject()) {
                parent.add(parts[i], new JsonObject());
            }
            parent = parent.getAsJsonObject(parts[i]);
        }
        parent.add(parts[parts.length - 1], GSON.toJsonTree(value));
    }

    private JsonObject mergeDefaults(JsonObject loaded) {
        JsonObject result = deepCopy(defaults);
        mergeInto(result, loaded);
        return result;
    }

    private void mergeInto(JsonObject base, JsonObject overlay) {
        for (String key : overlay.keySet()) {
            JsonElement overlayValue = overlay.get(key);
            if (overlayValue.isJsonObject() && base.has(key) && base.get(key).isJsonObject()) {
                mergeInto(base.getAsJsonObject(key), overlayValue.getAsJsonObject());
            } else {
                base.add(key, overlayValue.deepCopy());
            }
        }
    }

    private static JsonObject deepCopy(JsonObject obj) {
        return obj.deepCopy().getAsJsonObject();
    }

    private JsonElement resolve(JsonObject root, String path) {
        String[] parts = path.split("\\.");
        JsonObject current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            if (!current.has(parts[i]) || !current.get(parts[i]).isJsonObject()) {
                return null;
            }
            current = current.getAsJsonObject(parts[i]);
        }
        return current.get(parts[parts.length - 1]);
    }
}
