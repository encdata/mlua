package dev.mlua.mcapi.client.renderer;

import dev.mlua.mcapi.core.MCResourceLocation;

public interface MCRenderType {
    /** Stable name for resolving this render type on the active Minecraft version. */
    default String getKind() { return "custom"; }

    /** Texture used by texture-backed render types, or {@code null} when not applicable. */
    default MCResourceLocation getTexture() { return null; }

    static MCRenderType solid() {
        return new MCRenderType() {
            @Override public String getKind() { return "solid"; }
            @Override public String toString() { return "solid"; }
        };
    }

    static MCRenderType cutout() {
        return new MCRenderType() {
            @Override public String getKind() { return "cutout"; }
            @Override public String toString() { return "cutout"; }
        };
    }

    static MCRenderType cutoutMipped() {
        return new MCRenderType() {
            @Override public String getKind() { return "cutout_mipped"; }
            @Override public String toString() { return "cutout_mipped"; }
        };
    }

    static MCRenderType translucent() {
        return new MCRenderType() {
            @Override public String getKind() { return "translucent"; }
            @Override public String toString() { return "translucent"; }
        };
    }

    static MCRenderType entityTranslucent(MCResourceLocation texture) {
        return new MCRenderType() {
            @Override public String getKind() { return "entity_translucent"; }
            @Override public MCResourceLocation getTexture() { return texture; }
            @Override public String toString() { return "entity_translucent(" + texture + ")"; }
        };
    }
}
