package dev.mlua.mcapi.biome;

import dev.mlua.mcapi.core.MCBlockPos;
import dev.mlua.mcapi.core.MCResourceLocation;

public interface MCBiome {
    int getBaseTemperature();
    float getTemperature(MCBlockPos pos);
    MCResourceLocation getBiomeLocation();
}
