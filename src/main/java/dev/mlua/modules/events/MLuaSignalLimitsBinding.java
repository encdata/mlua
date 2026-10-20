package dev.mlua.modules.events;

import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.module.MLuaRegistration;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;

/** Registers the typed, immutable usage snapshot returned by Events:GetSignalLimits. */
final class MLuaSignalLimitsBinding {
    private MLuaSignalLimitsBinding() { }

    static void register(MLuaRegistration registration) {
        registration.registerClass("SignalLimits", registration.requireInstanceBaseClassName(),
            "A point-in-time snapshot of signal usage and capacity limits for the current addon.");
        property(registration, "ActiveCustomSignals", "number", "Custom signals currently registered across MLua.");
        property(registration, "AddonSignals", "number", "Custom signals owned by the current addon.");
        property(registration, "MaxCustomSignals", "number", "Maximum custom signals across MLua.");
        property(registration, "MaxCustomSignalsPerAddon", "number", "Maximum custom signals owned by one addon.");
        property(registration, "ActiveListeners", "number", "Listeners currently connected across all live signals.");
        property(registration, "AddonListeners", "number", "Listeners owned by the current addon across all live signals.");
        property(registration, "MaxListenersPerSignal", "number", "Maximum listeners connected to one signal.");
        property(registration, "MaxListenersPerAddonPerSignal", "number", "Maximum listeners one addon can connect to one signal.");
        property(registration, "ActiveWaiters", "number", "Waiters currently suspended across all live signals.");
        property(registration, "AddonWaiters", "number", "Waiters owned by the current addon across all live signals.");
        property(registration, "MaxWaitersPerSignal", "number", "Maximum waiters suspended on one signal.");
        property(registration, "MaxWaitersPerAddonPerSignal", "number", "Maximum waiters one addon can suspend on one signal.");
    }

    private static void property(MLuaRegistration registration, String name, String type, String description) {
        registration.registerClassFunction("SignalLimits", "Get" + name, new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) { return self.checktable().get(name); }
        }, description);
        MLuaClassMethods.registerInstanceProperty(registration, "SignalLimits", name, "Get" + name,
            null, type, "shared:Both", description);
    }
}
