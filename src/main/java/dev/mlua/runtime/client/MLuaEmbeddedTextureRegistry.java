package dev.mlua.runtime.client;

import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.runtime.client.model.MLuaBBModelAsset;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
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

    /** Decodes and registers model data-URI textures, preserving null entries for missing images. */
    public List<ResourceLocation> registerModelTextures(MLuaBBModelAsset asset) {
        List<ResourceLocation> result = new ArrayList<>(asset.textures().size());
        for (int index = 0; index < asset.textures().size(); index++) {
            String data = index < asset.embeddedTextures().size() ? asset.embeddedTextures().get(index) : "";
            if (data == null || !data.startsWith("data:")) {
                result.add(null);
                continue;
            }
            try {
                int comma = data.indexOf(',');
                byte[] bytes = Base64.getDecoder().decode(data.substring(comma + 1));
                ResourceLocation location = embeddedTextureLocation(bytes, index);
                if (this.acquire(location)) {
                    try {
                        NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
                        //--version 26.1
                        Minecraft.getInstance().getTextureManager().register(location,
                            new DynamicTexture(() -> location.toString(), image));
                        //--endversion
                        //--version 1.20.1,1.21.1
                        Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(image));
                        //--endversion
                    } catch (RuntimeException | IOException failure) {
                        if (this.release(location)) this.releaseUnreferenced(location);
                        throw failure;
                    }
                }
                result.add(location);
            } catch (RuntimeException | IOException ignored) {
                result.add(null);
            }
        }
        return result;
    }

    /** Resolves model texture paths while preferring successfully registered embedded images. */
    public List<ResourceLocation> resolveModelTextures(MLuaBBModelAsset asset,
                                                       List<ResourceLocation> embeddedTextures) {
        List<ResourceLocation> result = new ArrayList<>(asset.textures().size());
        for (int index = 0; index < asset.textures().size(); index++) {
            ResourceLocation embedded = index < embeddedTextures.size() ? embeddedTextures.get(index) : null;
            result.add(embedded != null ? embedded : resolveModelTexture(asset.textures().get(index)));
        }
        return Collections.unmodifiableList(result);
    }

    /** Acquires a deferred-render lease and returns an idempotent release callback. */
    public Runnable acquireLease(ResourceLocation location) {
        this.acquire(location);
        AtomicBoolean released = new AtomicBoolean();
        return () -> {
            if (released.compareAndSet(false, true) && this.release(location)) {
                this.releaseUnreferenced(location);
            }
        };
    }

    /** Releases each model-owned reference and unloads textures whose last owner is gone. */
    public void releaseModelTextures(Iterable<ResourceLocation> locations) {
        for (ResourceLocation location : locations) {
            if (location != null && this.release(location)) this.releaseUnreferenced(location);
        }
    }

    /** Drops every remaining reference and schedules texture-manager cleanup. */
    public void releaseAll() {
        for (ResourceLocation location : this.drain()) this.releaseUnreferenced(location);
    }

    private void releaseUnreferenced(ResourceLocation location) {
        Minecraft minecraft = Minecraft.getInstance();
        Runnable release = () -> {
            if (this.isReferenced(location)) return;
            try {
                minecraft.getTextureManager().release(location);
            } catch (RuntimeException failure) {
                dev.mlua.core.MLuaLogger.LOGGER.warn("Could not release embedded MLua render texture {}", location, failure);
            }
        };
        if (MLuaClientThread.isClientThread()) release.run();
        else minecraft.execute(release);
    }

    public List<ResourceLocation> drain() {
        List<ResourceLocation> locations = new ArrayList<>(references.keySet());
        references.clear();
        return locations;
    }

    static ResourceLocation embeddedTextureLocation(byte[] imageBytes, int index) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(imageBytes);
            return MLuaResourceLocations.build("mlua", "bbmodel/" + java.util.HexFormat.of().formatHex(digest) + "/" + index);
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    private static ResourceLocation resolveModelTexture(String path) {
        if (path == null || path.isBlank()) return null;
        String normalized = path.replace('\\', '/');
        if (normalized.startsWith("data:")) return null;
        if (normalized.startsWith("assets/")) {
            String remainder = normalized.substring("assets/".length());
            int slash = remainder.indexOf('/');
            if (slash > 0) {
                return MLuaResourceLocations.tryParseOrNull(remainder.substring(0, slash) + ":" + remainder.substring(slash + 1));
            }
        }
        int namespaceSlash = normalized.indexOf('/');
        if (namespaceSlash > 0 && normalized.substring(namespaceSlash + 1).startsWith("textures/")) {
            return MLuaResourceLocations.tryParseOrNull(normalized.substring(0, namespaceSlash) + ":" + normalized.substring(namespaceSlash + 1));
        }
        return MLuaResourceLocations.tryParseOrNull(normalized);
    }
}
