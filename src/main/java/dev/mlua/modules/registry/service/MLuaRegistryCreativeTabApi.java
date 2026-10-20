package dev.mlua.modules.registry.service;

import java.util.Map;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.loaderapi.RegistryKind;
import dev.mlua.runtime.registry.MLuaLoaderRegistry;

/** Installs the creative-tab registration category. */
final class MLuaRegistryCreativeTabApi {
   private MLuaRegistryCreativeTabApi() { }

   static void install(MLuaRegistryService service, LuaTable tabs) {
      tabs.set("Register", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            service.ensureRegistryPhase();
            String name = args.checkjstring(2);
            String owner = service.apiContext().mlua().ownerModId();
            MLuaRegistryDefinitions.requireLocalRegistryName(owner, name, "Registry.Tabs:Register");
            Map<String, Object> props = args.arg(3).istable() ? MLuaLuaBindings.mapFromLua(args.arg(3).checktable()) : Map.of();
            MLuaLoaderRegistry.register(RegistryKind.CREATIVE_TAB, owner, name, () -> {
               return service.tabFactory.create(owner, name, props);
            });
            return service.registryEntry(RegistryKind.CREATIVE_TAB, owner, name);
         }
      });
   }
}
