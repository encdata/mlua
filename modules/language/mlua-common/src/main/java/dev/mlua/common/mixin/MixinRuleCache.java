package dev.mlua.common.mixin;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class MixinRuleCache {
    private static final Gson GSON = new Gson();

    public static void save(Path path, List<MLuaMixinRule> rules) throws IOException {
        try (FileWriter writer = new FileWriter(path.toFile())) {
            GSON.toJson(rules, writer);
        }
    }

    public static List<MLuaMixinRule> load(Path path) throws IOException {
        if (!path.toFile().exists()) {
            return new ArrayList<>();
        }
        try (FileReader reader = new FileReader(path.toFile())) {
            return GSON.fromJson(reader, new TypeToken<List<MLuaMixinRule>>(){}.getType());
        }
    }
}
