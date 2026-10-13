package dev.mlua.modules.mixin.Service;

import dev.mlua.runtime.client.MLuaPackFinder;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(PackRepository.class)
public abstract class PackRepositoryMixin {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static RepositorySource[] mlua$addGeneratedPackFinder(RepositorySource[] sources) {
        return MLuaPackFinder.withRepositorySource(sources);
    }
}
