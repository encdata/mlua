package dev.mlua.loaderapi;

/**
 * Frame-local perspective projection for world annotations drawn by a GUI extractor.
 * Camera values are read once when the projector is created so callers can project many
 * points without repeating reflective loader-boundary lookups.
 */
public final class WorldToScreenProjection {
    private WorldToScreenProjection() { }

    /** Returns {@code null} when the loader camera does not expose the required frame data. */
    public static Projector create(Object camera, int screenWidth, int screenHeight) {
        if (camera == null || screenWidth <= 0 || screenHeight <= 0) return null;
        Object position = invoke(camera, "position", "getPosition");
        Object forward = invoke(camera, "forwardVector", "getForwardVector", "getLookVector", "getLookAngle");
        Object up = invoke(camera, "upVector", "getUpVector");
        if (position == null || forward == null || up == null) return null;

        double px = component(position, "x"), py = component(position, "y"), pz = component(position, "z");
        double fx = component(forward, "x"), fy = component(forward, "y"), fz = component(forward, "z");
        double ux = component(up, "x"), uy = component(up, "y"), uz = component(up, "z");
        double forwardLength = Math.sqrt(fx * fx + fy * fy + fz * fz);
        double upLength = Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (!finite(px, py, pz, fx, fy, fz, ux, uy, uz)
                || forwardLength < 1.0e-6 || upLength < 1.0e-6) return null;
        fx /= forwardLength; fy /= forwardLength; fz /= forwardLength;
        ux /= upLength; uy /= upLength; uz /= upLength;

        // Minecraft's camera forward points into the scene. Forward x up is the
        // viewer's screen-right axis, matching the camera's inverse LEFT vector.
        double rx = fy * uz - fz * uy;
        double ry = fz * ux - fx * uz;
        double rz = fx * uy - fy * ux;
        double rightLength = Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (!Double.isFinite(rightLength) || rightLength < 1.0e-6) return null;
        rx /= rightLength; ry /= rightLength; rz /= rightLength;

        Object rawFov = invoke(camera, "getFov", "fov");
        double fov = rawFov instanceof Number number ? number.doubleValue() : 70.0;
        if (!Double.isFinite(fov) || fov <= 1.0 || fov >= 179.0) fov = 70.0;
        double focalLength = (screenHeight / 2.0) / Math.tan(Math.toRadians(fov) / 2.0);
        return new Projector(px, py, pz, fx, fy, fz, ux, uy, uz, rx, ry, rz,
            focalLength, screenWidth, screenHeight);
    }

    private static Object invoke(Object target, String... methodNames) {
        for (String methodName : methodNames) {
            try {
                return target.getClass().getMethod(methodName).invoke(target);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Names differ between Minecraft versions and loader camera wrappers.
            }
        }
        return null;
    }

    private static double component(Object vector, String name) {
        Object value = invoke(vector, name, "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1));
        if (value instanceof Number number) return number.doubleValue();
        try {
            Object field = vector.getClass().getField(name).get(vector);
            return field instanceof Number number ? number.doubleValue() : Double.NaN;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return Double.NaN;
        }
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }

    public record Point(double x, double y, double scale) { }

    public static final class Projector {
        private final double px, py, pz;
        private final double fx, fy, fz;
        private final double ux, uy, uz;
        private final double rx, ry, rz;
        private final double focalLength;
        private final int screenWidth, screenHeight;

        private Projector(double px, double py, double pz,
                          double fx, double fy, double fz,
                          double ux, double uy, double uz,
                          double rx, double ry, double rz,
                          double focalLength, int screenWidth, int screenHeight) {
            this.px = px; this.py = py; this.pz = pz;
            this.fx = fx; this.fy = fy; this.fz = fz;
            this.ux = ux; this.uy = uy; this.uz = uz;
            this.rx = rx; this.ry = ry; this.rz = rz;
            this.focalLength = focalLength;
            this.screenWidth = screenWidth;
            this.screenHeight = screenHeight;
        }

        /** Returns null for points behind the camera or far outside the drawable screen. */
        public Point project(double x, double y, double z) {
            double dx = x - px, dy = y - py, dz = z - pz;
            double depth = dx * fx + dy * fy + dz * fz;
            if (!Double.isFinite(depth) || depth <= 0.05) return null;
            double screenX = screenWidth / 2.0 + (dx * rx + dy * ry + dz * rz) * focalLength / depth;
            double screenY = screenHeight / 2.0 - (dx * ux + dy * uy + dz * uz) * focalLength / depth;
            if (!Double.isFinite(screenX) || !Double.isFinite(screenY)
                    || screenX < -256 || screenX > screenWidth + 256
                    || screenY < -256 || screenY > screenHeight + 256) return null;
            return new Point(screenX, screenY, focalLength / depth / 32.0);
        }
    }
}
