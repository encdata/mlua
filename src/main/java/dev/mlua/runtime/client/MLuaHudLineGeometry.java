package dev.mlua.runtime.client;

/** Pixel geometry used to draw a 2D HUD line as one transformed rectangle. */
record MLuaHudLineGeometry(float angleRadians, int lengthPixels, int thicknessPixels) {
    static MLuaHudLineGeometry from(double x1, double y1, double x2, double y2, double thickness) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double length = Math.hypot(dx, dy);
        if (!Double.isFinite(length) || !Double.isFinite(thickness)) return null;
        return new MLuaHudLineGeometry((float) Math.atan2(dy, dx),
            Math.max(1, (int) (Math.ceil(length) + 1.0)),
            Math.max(1, (int) Math.round(thickness)));
    }
}
