package dev.mlua.mcapi.core;

public interface MCAABB {
    double minX();
    double minY();
    double minZ();
    double maxX();
    double maxY();
    double maxZ();

    MCAABB inflate(double value);

    static MCAABB of(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return new MCAABB() {
            @Override public double minX() { return minX; }
            @Override public double minY() { return minY; }
            @Override public double minZ() { return minZ; }
            @Override public double maxX() { return maxX; }
            @Override public double maxY() { return maxY; }
            @Override public double maxZ() { return maxZ; }

            @Override
            public MCAABB inflate(double value) {
                return of(minX - value, minY - value, minZ - value,
                          maxX + value, maxY + value, maxZ + value);
            }

            @Override
            public String toString() {
                return "MCAABB[" + minX + ", " + minY + ", " + minZ + " -> " + maxX + ", " + maxY + ", " + maxZ + "]";
            }
        };
    }
}
