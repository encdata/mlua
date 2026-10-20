package dev.mlua.runtime.client;

/** Reuses temporary animation vectors on the render thread without sharing them across threads. */
final class MLuaAnimationScratch {
    private static final ThreadLocal<MLuaAnimationScratch> CURRENT = ThreadLocal.withInitial(MLuaAnimationScratch::new);

    final float[] position = new float[3];
    final float[] rotation = new float[3];
    final float[] scale = new float[3];

    private MLuaAnimationScratch() { }

    static MLuaAnimationScratch current() {
        return CURRENT.get();
    }
}
