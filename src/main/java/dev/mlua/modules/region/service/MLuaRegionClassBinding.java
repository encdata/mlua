package dev.mlua.modules.region.service;

import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.module.MLuaRegistration;
import java.util.function.Function;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.VarArgFunction;

/** Owns the Lua class and property binding for Region instances. */
final class MLuaRegionClassBinding {
    private MLuaRegionClassBinding() { }

    static void register(MLuaRegistration registration) {
        registration.registerClass("Region", registration.requireInstanceBaseClassName(),
            "A live spatial region that tracks matching entities and exposes enter, exit, and within signals.");
        registration.registerClassFunction("Region", "GetName",
            regionProperty(handle -> LuaValue.valueOf(handle.key().name())),
            "Returns this region's registered name.");
        registration.registerClassFunction("Region", "GetIsActive",
            regionProperty(handle -> LuaValue.valueOf(handle.service().isRegionActive(handle.key()))),
            "Returns whether this region is currently registered.");
        registration.registerClassFunction("Region", "GetEntityCount", regionProperty(handle -> {
            int count = handle.service().regionEntityCount(handle.key());
            return LuaValue.valueOf(count);
        }), "Returns the number of entities currently inside this region.");
        registration.registerClassFunction("Region", "GetCenter",
            regionProperty(handle -> handle.service().regionCenter(handle.key())),
            "Returns the center of a spherical region, or nil for a box region.");
        registration.registerClassFunction("Region", "GetRadius",
            regionProperty(handle -> handle.service().regionRadius(handle.key())),
            "Returns the radius of a spherical region, or nil for a box region.");
        registration.registerClassFunction("Region", "GetBounds",
            regionProperty(handle -> handle.service().regionBounds(handle.key())),
            "Returns the minimum and maximum corners of a box region, or nil for a spherical region.");
        MLuaClassMethods.registerInstanceProperty(registration, "Region", "Name", "GetName", null,
            "string", "shared:Shared", "The region's registered name.");
        MLuaClassMethods.registerInstanceProperty(registration, "Region", "IsActive", "GetIsActive", null,
            "boolean", "shared:Shared", "Whether this region is currently registered.");
        MLuaClassMethods.registerInstanceProperty(registration, "Region", "EntityCount", "GetEntityCount", null,
            "number", "shared:Shared", "The number of entities currently inside the region.");
        MLuaClassMethods.registerInstanceProperty(registration, "Region", "Center", "GetCenter", null,
            "Vector3|nil", "shared:Shared", "The center of a spherical region, or nil for a box region.");
        MLuaClassMethods.registerInstanceProperty(registration, "Region", "Radius", "GetRadius", null,
            "number|nil", "shared:Shared", "The radius of a spherical region, or nil for a box region.");
        MLuaClassMethods.registerInstanceProperty(registration, "Region", "Bounds", "GetBounds", null,
            "RegionBounds|nil", "shared:Shared", "The minimum and maximum corners of a box region, or nil for a spherical region.");
        registration.registerClassFunction("Region", "Remove", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                RegionHandle handle = regionHandle(args.arg1());
                handle.service().removeRegion(handle.key());
                return LuaValue.NONE;
            }
        }, "Removes this region and clears its tracked entities and signals.");
        registration.registerClassFunction("Region", "GetEntities", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                RegionHandle handle = regionHandle(args.arg1());
                return handle.service().entitiesInRegion(handle.key());
            }
        }, "Returns the live entity instances currently inside this region.");
        registration.registerClassFunction("Region", "OnEnter", regionSignal("onEnter"),
            "Returns the signal fired when an entity enters this region.");
        registration.registerClassFunction("Region", "OnExit", regionSignal("onExit"),
            "Returns the signal fired when an entity leaves this region.");
        registration.registerClassFunction("Region", "OnWithin", regionSignal("onWithin"),
            "Returns the signal fired for entities inside this region.");
    }

    private static OneArgFunction regionProperty(Function<RegionHandle, LuaValue> getter) {
        return new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) {
                return getter.apply(regionHandle(self));
            }
        };
    }

    private static RegionHandle regionHandle(LuaValue value) {
        if (!value.istable()) throw new LuaError("[MLua] Expected a Region instance.");
        LuaValue state = value.checktable().rawget("__region_handle");
        if (!state.isuserdata(RegionHandle.class)) throw new LuaError("[MLua] Invalid Region instance.");
        return (RegionHandle) state.touserdata(RegionHandle.class);
    }

    private static VarArgFunction regionSignal(String name) {
        return new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                RegionHandle handle = regionHandle(args.arg1());
                return handle.service().signalInstance(handle.key(), name);
            }
        };
    }

    static LuaTable newHandle(MLuaRegionService service, MLuaRegionStateRegistry.RegionKey key) {
        LuaTable handle = new LuaTable();
        handle.rawset("__region_handle", LuaValue.userdataOf(new RegionHandle(service, key)));
        return service.registration().attachClassInstance(handle, "Region", null);
    }

    record RegionHandle(MLuaRegionService service, MLuaRegionStateRegistry.RegionKey key) { }
}
