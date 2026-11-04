package dev.mlua.runtime.client;

import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.runtime.client.model.MLuaBBModelAsset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
//--version 26.1
import net.minecraft.resources.Identifier;
//--endversion
//--version 1.20.1,1.21.1
import net.minecraft.resources.ResourceLocation;
//--endversion

/** Immutable material batches prepared once for a deferred Blockbench render object. */
final class MLuaBBModelRenderPlan {
    private static final List<String> FACE_NAMES = List.of("north", "south", "west", "east", "up", "down");
    //--version 26.1
    private static final Identifier FALLBACK_TEXTURE =
        MLuaResourceLocations.build("minecraft", "textures/block/stone.png");
    //--endversion
    //--version 1.20.1,1.21.1
    private static final ResourceLocation FALLBACK_TEXTURE =
        MLuaResourceLocations.build("minecraft", "textures/block/stone.png");
    //--endversion
    private static final MLuaBBModelRenderPlan EMPTY = new MLuaBBModelRenderPlan(List.of());

    //--version 26.1
    record TextureBatch(Identifier texture, List<MLuaBBModelAsset.Cube> cubes) {
        TextureBatch {
            cubes = List.copyOf(cubes);
        }
    }
    //--endversion
    //--version 1.20.1,1.21.1
    record TextureBatch(ResourceLocation texture, List<MLuaBBModelAsset.Cube> cubes) {
        TextureBatch {
            cubes = List.copyOf(cubes);
        }
    }
    //--endversion

    private final List<TextureBatch> textureBatches;

    private MLuaBBModelRenderPlan(List<TextureBatch> textureBatches) {
        this.textureBatches = List.copyOf(textureBatches);
    }

    //--version 26.1
    static MLuaBBModelRenderPlan prepare(MLuaBBModelAsset asset, List<Identifier> textures) {
        Map<Identifier, LinkedHashSet<MLuaBBModelAsset.Cube>> cubesByTexture = new LinkedHashMap<>();
        for (MLuaBBModelAsset.Cube cube : asset.cubes()) {
            for (String faceName : FACE_NAMES) {
                Identifier texture = faceTexture(cube.faces().get(faceName), textures);
                cubesByTexture.computeIfAbsent(texture, ignored -> new LinkedHashSet<>()).add(cube);
            }
        }

        List<TextureBatch> batches = new ArrayList<>(cubesByTexture.size());
        cubesByTexture.forEach((texture, cubes) -> batches.add(new TextureBatch(texture, List.copyOf(cubes))));
        return new MLuaBBModelRenderPlan(batches);
    }
    //--endversion
    //--version 1.20.1,1.21.1
    static MLuaBBModelRenderPlan prepare(MLuaBBModelAsset asset, List<ResourceLocation> textures) {
        Map<ResourceLocation, LinkedHashSet<MLuaBBModelAsset.Cube>> cubesByTexture = new LinkedHashMap<>();
        for (MLuaBBModelAsset.Cube cube : asset.cubes()) {
            for (String faceName : FACE_NAMES) {
                ResourceLocation texture = faceTexture(cube.faces().get(faceName), textures);
                cubesByTexture.computeIfAbsent(texture, ignored -> new LinkedHashSet<>()).add(cube);
            }
        }

        List<TextureBatch> batches = new ArrayList<>(cubesByTexture.size());
        cubesByTexture.forEach((texture, cubes) -> batches.add(new TextureBatch(texture, List.copyOf(cubes))));
        return new MLuaBBModelRenderPlan(batches);
    }
    //--endversion

    static MLuaBBModelRenderPlan empty() {
        return EMPTY;
    }

    List<TextureBatch> textureBatches() {
        return this.textureBatches;
    }

    //--version 26.1
    private static Identifier faceTexture(MLuaBBModelAsset.Face face, List<Identifier> textures) {
        if (face != null && face.texture() >= 0 && face.texture() < textures.size()) {
            Identifier resolved = textures.get(face.texture());
            if (resolved != null) return resolved;
        }
        return FALLBACK_TEXTURE;
    }
    //--endversion
    //--version 1.20.1,1.21.1
    private static ResourceLocation faceTexture(MLuaBBModelAsset.Face face, List<ResourceLocation> textures) {
        if (face != null && face.texture() >= 0 && face.texture() < textures.size()) {
            ResourceLocation resolved = textures.get(face.texture());
            if (resolved != null) return resolved;
        }
        return FALLBACK_TEXTURE;
    }
    //--endversion
}
