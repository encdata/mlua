package dev.mlua.runtime.client;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;

/** Tracks embedded textures shared by active render objects. */
public final class MLuaEmbeddedTextureRegistry {
    private final ConcurrentHashMap<ResourceLocation, Integer> references = new ConcurrentHashMap<>();

    /** Returns true when the caller must register the texture. */
    public boolean acquire(ResourceLocation location) {
        return references.compute(location, (key, count) -> count == null ? 1 : count + 1) == 1;
    }

    /** Returns true when the caller must release the texture. */
    public boolean release(ResourceLocation location) {
        boolean[] lastReference = {false};
        references.computeIfPresent(location, (key, count) -> {
            if (count <= 1) {
                lastReference[0] = true;
                return null;
            }
            return count - 1;
        });
        return lastReference[0];
    }

    /** Returns whether a texture was reacquired before an asynchronous renderer release ran. */
    public boolean isReferenced(ResourceLocation location) {
        return references.containsKey(location);
    }

    public List<ResourceLocation> drain() {
        List<ResourceLocation> locations = new ArrayList<>(references.keySet());
        references.clear();
        return locations;
    }
}
