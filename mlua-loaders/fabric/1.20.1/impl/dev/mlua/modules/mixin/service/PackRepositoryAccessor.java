package dev.mlua.modules.mixin.service;

import java.util.Set;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PackRepository.class)
public interface PackRepositoryAccessor {
    @Accessor("sources")
    Set<RepositorySource> mlua$getSources();
}
