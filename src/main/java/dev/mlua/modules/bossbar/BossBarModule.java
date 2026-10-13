package dev.mlua.modules.bossbar;

import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.common.enums.Side;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.modules.bossbar.Service.MLuaBossBarService;
import java.util.List;

public class BossBarModule implements MLuaModule {
    @Override public String id() { return "bossbar"; }

    @Override public void clearRuntimeState(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side) {
        if (side == Side.CLIENT) return;
        MLuaBossBarService.clearRuntime(runtimeSession);
    }

    @Override public void clearOwnedState(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side, String ownerModId) {
        MLuaBossBarService.clearOwned(runtimeSession, ownerModId);
    }

    @Override public List<String> dependencies() { return List.of("core-classes"); }
    @Override public int priority() { return 12; }
    @Override public List<ServiceEntry> services() {
        ServiceEntry.Builder bossBar = ServiceEntry.builder("BossBar", MLuaBossBarService::new)
            .description("Creates and manages server boss bars.");
        bossBar.function("Create", "Creates a boss bar with a title and optional display settings.")
            .function("Get", "Returns a live boss bar handle by identifier.")
            .function("Remove", "Removes a boss bar by identifier.")
            .function("GetAll", "Returns the live handles for boss bars owned by the calling mod.");
        return List.of(bossBar.build());
    }
    @Override public void registerClasses(MLuaRegistration registration) {
        registration.registerClass("BossBarHandle", registration.requireInstanceBaseClassName(),
            "A live handle for reading, updating, and removing a server boss bar.");
        MLuaClassMethods.registerInstanceMethod(registration, "BossBarHandle", "SetTitle", "Changes the boss bar title.");
        MLuaClassMethods.registerInstanceMethod(registration, "BossBarHandle", "SetHealth", "Sets the displayed progress from zero to one.");
        MLuaClassMethods.registerInstanceMethod(registration, "BossBarHandle", "SetColor", "Sets the boss bar color by name.");
        MLuaClassMethods.registerInstanceMethod(registration, "BossBarHandle", "SetStyle", "Sets the boss bar overlay style by name.");
        MLuaClassMethods.registerInstanceMethod(registration, "BossBarHandle", "SetVisible", "Shows or hides the boss bar.");
        MLuaClassMethods.registerInstanceMethod(registration, "BossBarHandle", "GetTitle", "Returns the current boss bar title.");
        MLuaClassMethods.registerInstanceMethod(registration, "BossBarHandle", "GetHealth", "Returns the current progress value.");
        MLuaClassMethods.registerInstanceMethod(registration, "BossBarHandle", "GetColor", "Returns the current boss bar color name.");
        MLuaClassMethods.registerInstanceMethod(registration, "BossBarHandle", "GetStyle", "Returns the current overlay style name.");
        MLuaClassMethods.registerInstanceMethod(registration, "BossBarHandle", "IsVisible", "Returns whether the boss bar is visible.");
        MLuaClassMethods.registerInstanceMethod(registration, "BossBarHandle", "AddPlayer", "Adds a player to the boss bar.");
        MLuaClassMethods.registerInstanceMethod(registration, "BossBarHandle", "RemovePlayer", "Removes a player from the boss bar.");
        MLuaClassMethods.registerInstanceMethod(registration, "BossBarHandle", "Remove", "Removes the boss bar and all attached players.");
    }
    @Override public void contributeDocs(MLuaDocBuilder docs) {
        docs.service("BossBar", "MLuaBossBarService", "Creates and manages server boss bars.", "services/boss-bar.html");
    }
}
