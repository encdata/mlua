package dev.mlua.mc.impl.biome;

import dev.mlua.mcapi.biome.MCBiome;
import dev.mlua.mcapi.core.MCBlockPos;
import dev.mlua.mcapi.core.MCResourceLocation;
import dev.mlua.mc.impl.core.MCResourceLocationImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;

public class MCBiomeImpl implements MCBiome {
    private final Biome inner;

    public MCBiomeImpl(Biome inner) {
        this.inner = inner;
    }

    public static MCBiomeImpl fromReal(Biome inner) {
        return inner == null ? null : new MCBiomeImpl(inner);
    }

    public Biome inner() {
        return inner;
    }

    @Override
    public int getBaseTemperature() {
        return (int) inner.getBaseTemperature();
    }

    @Override
    public float getTemperature(MCBlockPos pos) {
        return inner.getBaseTemperature();
    }

    @Override
    public MCResourceLocation getBiomeLocation() {
        return null;
    }
}
