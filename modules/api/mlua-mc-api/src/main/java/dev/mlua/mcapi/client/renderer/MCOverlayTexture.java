package dev.mlua.mcapi.client.renderer;

public interface MCOverlayTexture {
    int NO_OVERLAY = pack(0, 10);

    static int pack(int u, int v) {
        return u | v << 16;
    }
}
