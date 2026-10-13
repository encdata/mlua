package dev.mlua.mcapi.core;

public interface MCBlockPos {
    int getX();
    int getY();
    int getZ();

    MCBlockPos immutable();
    MCBlockPos below();
    MCBlockPos above();
    MCBlockPos offset(int dx, int dy, int dz);

    static MCBlockPos of(int x, int y, int z) {
        return new MCBlockPos() {
            private final int px = x;
            private final int py = y;
            private final int pz = z;

            @Override
            public int getX() { return px; }
            @Override
            public int getY() { return py; }
            @Override
            public int getZ() { return pz; }

            @Override
            public MCBlockPos immutable() { return this; }

            @Override
            public MCBlockPos below() { return new MCBlockPos() {
                @Override public int getX() { return px; }
                @Override public int getY() { return py - 1; }
                @Override public int getZ() { return pz; }
                @Override public MCBlockPos immutable() { return this; }
                @Override public MCBlockPos below() { return of(px, py - 1, pz).below(); }
                @Override public MCBlockPos above() { return of(px, py - 1, pz).above(); }
                @Override public MCBlockPos offset(int dx, int dy, int dz) { return of(px + dx, py - 1 + dy, pz + dz); }
            }; }

            @Override
            public MCBlockPos above() { return new MCBlockPos() {
                @Override public int getX() { return px; }
                @Override public int getY() { return py + 1; }
                @Override public int getZ() { return pz; }
                @Override public MCBlockPos immutable() { return this; }
                @Override public MCBlockPos below() { return of(px, py + 1, pz).below(); }
                @Override public MCBlockPos above() { return of(px, py + 1, pz).above(); }
                @Override public MCBlockPos offset(int dx, int dy, int dz) { return of(px + dx, py + 1 + dy, pz + dz); }
            }; }

            @Override
            public MCBlockPos offset(int dx, int dy, int dz) { return of(px + dx, py + dy, pz + dz); }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof MCBlockPos other)) return false;
                return px == other.getX() && py == other.getY() && pz == other.getZ();
            }

            @Override
            public int hashCode() {
                return (px * 31 + py) * 31 + pz;
            }

            @Override
            public String toString() {
                return "MCBlockPos{x=" + px + ", y=" + py + ", z=" + pz + "}";
            }
        };
    }

    static MCBlockPos containing(double x, double y, double z) {
        return of(floor(x), floor(y), floor(z));
    }

    private static int floor(double value) {
        int i = (int) value;
        return value < (double) i ? i - 1 : i;
    }
}
