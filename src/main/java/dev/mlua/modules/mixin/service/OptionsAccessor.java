package dev.mlua.modules.mixin.service;

import net.minecraft.client.Options;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Options.class)
public interface OptionsAccessor {
    @Accessor("keyMappings")
    KeyMapping[] mlua$getKeyMappings();

    @Mutable
    @Accessor("keyMappings")
    void mlua$setKeyMappings(KeyMapping[] keyMappings);
}
