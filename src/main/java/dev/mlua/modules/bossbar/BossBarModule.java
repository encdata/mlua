package dev.mlua.modules.bossbar;

import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.common.enums.Side;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.modules.bossbar.Service.MLuaBossBarService;
import java.util.List;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;

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
        return List.of(ServiceEntry.builder("BossBar", MLuaBossBarService::new)
            .reflective(MLuaBossBarService.class)
            .build());
    }
    @Override public void registerClasses(MLuaRegistration registration) {
        registration.registerClass("BossBarHandle", registration.requireInstanceBaseClassName(),
            "A live handle for reading, updating, and removing a server boss bar.");
        registration.registerClassProperty("BossBarHandle", "Name", new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) { return self.checktable().rawget("__name"); }
        }, LuaValue.NIL, "string", "server:Server", "The registered name of this boss bar.");
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
        MLuaClassMethods.registerInstanceProperty(registration, "BossBarHandle", "Title", "GetTitle", "SetTitle",
            "string", "server:Server", "Gets or changes the boss bar title.");
        MLuaClassMethods.registerInstanceProperty(registration, "BossBarHandle", "Health", "GetHealth", "SetHealth",
            "number", "server:Server", "Gets or changes the displayed progress from zero to one.");
        MLuaClassMethods.registerInstanceProperty(registration, "BossBarHandle", "Color", "GetColor", "SetColor",
            "string", "server:Server", "Gets or changes the boss bar color.");
        MLuaClassMethods.registerInstanceProperty(registration, "BossBarHandle", "Style", "GetStyle", "SetStyle",
            "string", "server:Server", "Gets or changes the boss bar overlay style.");
        MLuaClassMethods.registerInstanceProperty(registration, "BossBarHandle", "Visible", "IsVisible", "SetVisible",
            "boolean", "server:Server", "Gets or changes boss bar visibility.");
    }
    @Override public void contributeDocs(MLuaDocBuilder docs) {
        docs.register(MLuaDocBuilder.Registration.service("BossBar", "MLuaBossBarService", "Creates and manages server boss bars.", "services/boss-bar.html"));
        docs.register(MLuaDocBuilder.Registration.clazz("BossBarHandle", "MLuaBossBarService", "A live handle for reading, updating, and removing a server boss bar.", "classes/boss-bar-handle.html", "Instance"));
        docs.register(MLuaDocBuilder.Registration.property("BossBarHandle", "BossBarHandle.Name", "string", "The registered name of this boss bar.", "server:Server"));
        docs.register(MLuaDocBuilder.Registration.property("BossBarHandle", "BossBarHandle.Title", "string", "Gets or changes the boss bar title.", "server:Server"));
        docs.register(MLuaDocBuilder.Registration.property("BossBarHandle", "BossBarHandle.Health", "number", "Gets or changes the displayed progress from zero to one.", "server:Server"));
        docs.register(MLuaDocBuilder.Registration.property("BossBarHandle", "BossBarHandle.Color", "string", "Gets or changes the boss bar color.", "server:Server"));
        docs.register(MLuaDocBuilder.Registration.property("BossBarHandle", "BossBarHandle.Style", "string", "Gets or changes the boss bar overlay style.", "server:Server"));
        docs.register(MLuaDocBuilder.Registration.property("BossBarHandle", "BossBarHandle.Visible", "boolean", "Gets or changes boss bar visibility.", "server:Server"));
    }
}
