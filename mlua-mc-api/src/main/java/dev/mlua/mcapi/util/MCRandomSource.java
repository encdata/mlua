package dev.mlua.mcapi.util;

import java.util.Random;

public interface MCRandomSource {

    int nextInt(int bound);

    double nextDouble();

    float nextFloat();

    long nextLong();

    boolean nextBoolean();

    static MCRandomSource create(long seed) {
        Random random = new Random(seed);
        return new MCRandomSource() {
            @Override
            public int nextInt(int bound) {
                return random.nextInt(bound);
            }

            @Override
            public double nextDouble() {
                return random.nextDouble();
            }

            @Override
            public float nextFloat() {
                return random.nextFloat();
            }

            @Override
            public long nextLong() {
                return random.nextLong();
            }

            @Override
            public boolean nextBoolean() {
                return random.nextBoolean();
            }
        };
    }

}
