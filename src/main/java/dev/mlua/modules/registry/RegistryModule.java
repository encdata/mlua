package dev.mlua.modules.registry;

import dev.mlua.core.MLuaContextView;
import dev.mlua.minecraft.binding.MLuaItemWrapper;
import dev.mlua.minecraft.binding.MLuaRegistryCatalogs;
import dev.mlua.minecraft.binding.MLuaTextureBindings;
import dev.mlua.modules.registry.service.MLuaRegistryService;
import dev.mlua.minecraft.scripted.MLuaBehaviorRegistry;
import dev.mlua.runtime.registry.MLuaContentTypeRegistry;
import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaRegistration;
import java.util.List;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;

public class RegistryModule implements MLuaModule {
    @Override
    public void clearOwnedState(MLuaRegistration runtime, Object runtimeSession,
                                dev.mlua.common.enums.Side side, String ownerModId) {
        MLuaRegistryService.clearOwnedCategories(runtimeSession, side, ownerModId);
        MLuaContentTypeRegistry.clearOwner(runtime, ownerModId);
        dev.mlua.runtime.registry.MLuaItemFactory.unregisterTypes(ownerModId);
        dev.mlua.runtime.registry.MLuaBlockFactory.unregisterTypes(ownerModId);
        MLuaBehaviorRegistry.clearOwner(ownerModId);
        dev.mlua.runtime.registry.MLuaLoaderRegistry.clearGeneratedDataResources(ownerModId);
        dev.mlua.runtime.registry.MLuaLoaderRegistry.clearGeneratedAssetResources(ownerModId);
    }

    @Override
    public String id() { return "registry"; }

    @Override
    public int priority() { return -50; }

    @Override
    public List<String> dependencies() { return List.of("core-classes", "players"); }

    @Override
    public List<ServiceEntry> services() {
        ServiceEntry.Builder registry = ServiceEntry.builder("Registry", MLuaRegistryService::new)
            .description("The central registry service for built-in Minecraft content and addon-owned categories.");
        MLuaRegistryService.serviceMethodDescriptions().forEach(registry::function);
        return List.of(registry.build());
    }

    @Override
    public void registerClasses(MLuaRegistration registration) {
        RegistryClassRegistration.register(registration);
    }

    @Override
    public void contributeGlobals(Globals globals, MLuaContextView context) {
        var registration = context.registration();
        globals.set("Block", registration.classTable("Block"));
        LuaTable itemGlobal = MLuaItemWrapper.itemGlobal(registration);
        itemGlobal.set("Properties", registration.classTable("Item").get("Properties"));
        globals.set("Item", itemGlobal);
        LuaTable items = MLuaRegistryCatalogs.items();
        globals.set("Items", items);
        globals.set("Blocks", MLuaRegistryCatalogs.blocks());
        globals.set("Texture", MLuaTextureBindings.textureGlobal());
        globals.set("ScriptedEntity", registration.classTable("ScriptedEntity"));
        globals.set("RegistryEntry", registration.classTable("RegistryEntry"));
        globals.set("RegistryDefinition", registration.classTable("RegistryDefinition"));
        globals.set("GeneratedDataResource", registration.classTable("GeneratedDataResource"));
        globals.set("GeneratedAssetResource", registration.classTable("GeneratedAssetResource"));
        globals.set("ContainerType", registration.classTable("ContainerType"));
        globals.set("RegistryCategory", registration.classTable("RegistryCategory"));
        MLuaItemWrapper.attachCatalogs(itemGlobal, items);
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        RegistryDocumentation.contribute(docs);
    }

}