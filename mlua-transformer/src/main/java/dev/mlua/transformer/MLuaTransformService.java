package dev.mlua.transformer;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.IncompatibleEnvironmentException;
import dev.mlua.common.mixin.MLuaEarlyMixinBootstrap;
import java.util.List;
import java.util.Set;

/** ModLauncher adapter for the shared early Lua mixin bootstrap and class transformer. */
public final class MLuaTransformService implements ITransformationService {
   @Override
   public String name() {
      return "mlua";
   }

   @Override
   public void initialize(IEnvironment environment) { }

   @Override
   public List<Resource> beginScanning(IEnvironment environment) {
      return List.of();
   }

   @Override
   public void onLoad(IEnvironment environment, Set<String> otherServices) throws IncompatibleEnvironmentException {
      MLuaEarlyMixinBootstrap.bootstrap();
   }

   @Override
   @SuppressWarnings("rawtypes")
   public List transformers() {
      return List.of(new MLuaTransformer());
   }
}
