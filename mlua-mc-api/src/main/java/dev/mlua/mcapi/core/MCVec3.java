package dev.mlua.mcapi.core;

public interface MCVec3 {
    double x();
    double y();
    double z();

    MCVec3 ZERO = new MCVec3() {
        @Override public double x() { return 0.0; }
        @Override public double y() { return 0.0; }
        @Override public double z() { return 0.0; }
        @Override public String toString() { return "MCVec3(0.0, 0.0, 0.0)"; }
    };

    static MCVec3 of(double x, double y, double z) {
        if (x == 0.0 && y == 0.0 && z == 0.0) return ZERO;
        return new MCVec3() {
            @Override public double x() { return x; }
            @Override public double y() { return y; }
            @Override public double z() { return z; }
            @Override public String toString() { return "MCVec3(" + x + ", " + y + ", " + z + ")"; }
        };
    }
}
