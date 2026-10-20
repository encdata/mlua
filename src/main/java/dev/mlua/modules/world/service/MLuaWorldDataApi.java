//--version 1.20.1,1.21.1
package dev.mlua.modules.world.service;

import dev.mlua.core.service.MLuaService;
import dev.mlua.runtime.data.MLuaDataManager;
import java.util.function.Supplier;
import net.minecraft.world.level.Level;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Binds persistent and synchronized per-dimension data operations. */
final class MLuaWorldDataApi {
    private MLuaWorldDataApi() { }

    static void bind(LuaTable table, Supplier<Level> level, MLuaService.Context context) {
        table.rawset("GetPersistentData", MLuaWorldApiSupport.levelFn(level,
            (world, args) -> MLuaDataManager.worldPersistentData(world)));
        table.rawset("GetSyncedData", MLuaWorldApiSupport.levelFn(level,
            (world, args) -> MLuaDataManager.worldSyncedData(world)));
        table.rawset("GetData", MLuaWorldApiSupport.levelFn(level, (world, args) -> {
            LuaTable persistent = MLuaDataManager.worldPersistentData(world);
            return persistent.get("Get").call(persistent, LuaValue.valueOf(args.checkjstring(2)));
        }));
        table.rawset("SetData", MLuaWorldApiSupport.serverLevelFn(context, level, (world, args) -> {
            LuaTable persistent = MLuaDataManager.worldPersistentData(world);
            persistent.get("Set").call(persistent, LuaValue.valueOf(args.checkjstring(2)), args.arg(3));
            return LuaValue.NONE;
        }));
        table.rawset("RemoveData", MLuaWorldApiSupport.serverLevelFn(context, level, (world, args) -> {
            LuaTable persistent = MLuaDataManager.worldPersistentData(world);
            persistent.get("Remove").call(persistent, LuaValue.valueOf(args.checkjstring(2)));
            return LuaValue.NONE;
        }));
    }
}
//--endversion
