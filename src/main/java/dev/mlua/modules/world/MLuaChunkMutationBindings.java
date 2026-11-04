package dev.mlua.modules.world;

import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.minecraft.binding.MLuaBlockStateProperties;
import dev.mlua.minecraft.binding.MLuaBlockStateWrapper;
import dev.mlua.module.MLuaRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Owns server-authoritative mutations requested through a live Chunk instance. */
final class MLuaChunkMutationBindings {
    private MLuaChunkMutationBindings() { }

    static void bind(LuaTable chunk, MLuaRegistration registration) {
        MLuaChunkWrapper.bind(chunk, "SetBlockAt", args -> {
            Level world = MLuaChunkWrapper.level(args.arg1());
            if (!(world instanceof ServerLevel server)) {
                throw new LuaError("[MLua] Chunk:SetBlockAt is server-side only.");
            }
            BlockPos pos = MLuaChunkWrapper.requirePosition(args, 2, args.arg1());
            int valueIndex = 2 + MLuaChunkWrapper.coordinateWidth(args, 2);
            LuaValue blockValue = args.arg(valueIndex);
            BlockState state;
            int updateFlagsIndex;
            if (registration.isInstanceOf(blockValue, "BlockState")) {
                state = MLuaBlockStateWrapper.blockStateFromLua(blockValue, registration);
                updateFlagsIndex = valueIndex + 1;
            } else {
                String id = blockValue.checkjstring();
                Block block = BuiltInRegistries.BLOCK.getOptional(MLuaResourceLocations.parse(id)).orElse(null);
                if (block == null) throw new LuaError("[MLua] Unknown block: " + id);
                LuaValue properties = args.arg(valueIndex + 1);
                if (!properties.isnil() && !properties.istable() && !properties.isnumber()) {
                    throw new LuaError("[MLua] Chunk:SetBlockAt expects a property table followed by optional update flags.");
                }
                state = MLuaBlockStateProperties.apply(block.defaultBlockState(),
                    properties.istable() ? properties : LuaValue.NIL);
                updateFlagsIndex = properties.istable() ? valueIndex + 2 : valueIndex + 1;
            }
            int updateFlags = MLuaBlockUpdateFlags.read(args.arg(updateFlagsIndex), "Chunk:SetBlockAt");
            if (!MLuaChunkWrapper.ensureLoaded(server, args.arg1())) return LuaValue.FALSE;
            return LuaValue.valueOf(server.setBlock(pos, state, updateFlags));
        });
    }
}
