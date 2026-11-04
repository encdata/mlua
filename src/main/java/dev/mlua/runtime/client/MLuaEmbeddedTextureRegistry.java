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
//--version 26.1
import net.minecraft.resources.Identifier;
//--endversion
//--version 1.20.1,1.21.1
import net.minecraft.resources.ResourceLocation;
//--endversion

/** Tracks embedded textures shared by active render objects. */
public final class MLuaEmbeddedTextureRegistry {
    //--version 26.1
    private final ConcurrentHashMap<Identifier, Integer> references = new ConcurrentHashMap<>();
    //--endversion
    //--version 1.20.1,1.21.1
    private final ConcurrentHashMap<ResourceLocation, Integer> references = new ConcurrentHashMap<>();
    //--endversion

    /** Returns true when the caller must register the texture. */
    //--version 26.1
    public boolean acquire(Identifier location) {
    //--endversion
    //--version 1.20.1,1.21.1
    public boolean acquire(ResourceLocation location) {
    //--endversion
        return references.compute(location, (key, count) -> count == null ? 1 : count + 1) == 1;
    }

    /** Returns true when the caller must release the texture. */
    //--version 26.1
    public boolean release(Identifier location) {
    //--endversion
    //--version 1.20.1,1.21.1
    public boolean release(ResourceLocation location) {
    //--endversion
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
    //--version 26.1
    public boolean isReferenced(Identifier location) {
    //--endversion
    //--version 1.20.1,1.21.1
    public boolean isReferenced(ResourceLocation location) {
    //--endversion
        return references.containsKey(location);
    }

    /** Decodes and registers model data-URI textures, preserving null entries for missing images. */
    //--version 26.1
    public List<Identifier> registerModelTextures(MLuaBBModelAsset asset) {
        List<Identifier> result = new ArrayList<>(asset.textures().size());
    //--endversion
    //--version 1.20.1,1.21.1
    public List<ResourceLocation> registerModelTextures(MLuaBBModelAsset asset) {
        List<ResourceLocation> result = new ArrayList<>(asset.textures().size());
    //--endversion
        for (int index = 0; index < asset.textures().size(); index++) {
            String data = index < asset.embeddedTextures().size() ? asset.embeddedTextures().get(index) : "";
            if (data == null || !data.startsWith("data:")) {
                result.add(null);
                continue;
            }
            try {
                int comma = data.indexOf(',');
                byte[] bytes = Base64.getDecoder().decode(data.substring(comma + 1));
                var location = embeddedTextureLocation(bytes, index);
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
    //--version 26.1
    public List<Identifier> resolveModelTextures(MLuaBBModelAsset asset, List<Identifier> embeddedTextures) {
        List<Identifier> result = new ArrayList<>(asset.textures().size());
    //--endversion
    //--version 1.20.1,1.21.1
    public List<ResourceLocation> resolveModelTextures(MLuaBBModelAsset asset,
                                                       List<ResourceLocation> embeddedTextures) {
        List<ResourceLocation> result = new ArrayList<>(asset.textures().size());
    //--endversion
        for (int index = 0; index < asset.textures().size(); index++) {
            var embedded = index < embeddedTextures.size() ? embeddedTextures.get(index) : null;
            result.add(embedded != null ? embedded : resolveModelTexture(asset.textures().get(index)));
        }
        return Collections.unmodifiableList(result);
    }

    /** Acquires a deferred-render lease and returns an idempotent release callback. */
    //--version 26.1
    public Runnable acquireLease(Identifier location) {
    //--endversion
    //--version 1.20.1,1.21.1
    public Runnable acquireLease(ResourceLocation location) {
    //--endversion
        this.acquire(location);
        AtomicBoolean released = new AtomicBoolean();
        return () -> {
            if (released.compareAndSet(false, true) && this.release(location)) {
                this.releaseUnreferenced(location);
            }
        };
    }

    /** Releases each model-owned reference and unloads textures whose last owner is gone. */
    //--version 26.1
    public void releaseModelTextures(Iterable<Identifier> locations) {
    //--endversion
    //--version 1.20.1,1.21.1
    public void releaseModelTextures(Iterable<ResourceLocation> locations) {
    //--endversion
        for (var location : locations) {
            if (location != null && this.release(location)) this.releaseUnreferenced(location);
        }
    }

    /** Drops every remaining reference and schedules texture-manager cleanup. */
    public void releaseAll() {
        for (var location : this.drain()) this.releaseUnreferenced(location);
    }

    //--version 26.1
    private void releaseUnreferenced(Identifier location) {
    //--endversion
    //--version 1.20.1,1.21.1
    private void releaseUnreferenced(ResourceLocation location) {
    //--endversion
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

    //--version 26.1
    public List<Identifier> drain() {
        List<Identifier> locations = new ArrayList<>(references.keySet());
    //--endversion
    //--version 1.20.1,1.21.1
    public List<ResourceLocation> drain() {
        List<ResourceLocation> locations = new ArrayList<>(references.keySet());
    //--endversion
        references.clear();
        return locations;
    }

    //--version 26.1
    static Identifier embeddedTextureLocation(byte[] imageBytes, int index) {
    //--endversion
    //--version 1.20.1,1.21.1
    static ResourceLocation embeddedTextureLocation(byte[] imageBytes, int index) {
    //--endversion
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(imageBytes);
            return MLuaResourceLocations.build("mlua", "bbmodel/" + java.util.HexFormat.of().formatHex(digest) + "/" + index);
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    //--version 26.1
    private static Identifier resolveModelTexture(String path) {
    //--endversion
    //--version 1.20.1,1.21.1
    private static ResourceLocation resolveModelTexture(String path) {
    //--endversion
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
