package dev.mlua.modules.world.service;

import dev.mlua.core.service.MLuaService;
import dev.mlua.loaderapi.RegistryHandle;
import dev.mlua.loaderapi.RegistryKind;
import dev.mlua.minecraft.binding.MLuaBlockStateProperties;
import dev.mlua.minecraft.binding.MLuaBlockStateWrapper;
import dev.mlua.modules.world.MLuaBlockUpdateFlags;
import dev.mlua.modules.world.MLuaChunkWrapper;
import dev.mlua.modules.world.MLuaPositionArguments;
import dev.mlua.runtime.registry.MLuaLoaderRegistry;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.resources.Identifier;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Binds block placement, state updates, and breaking for 26.x Worlds. */
final class MLuaWorldBlockMutationApi26 {
    private MLuaWorldBlockMutationApi26() { }

    static void bind(MLuaWorldService owner, MLuaService.Context context, LuaTable worldTable,
                     Map<String, LuaValue> implementations, Level level) {
        owner.bindWorldMethod(worldTable, implementations, "SetBlock", MLuaWorldService.levelFunction(level,
            (world, args) -> setBlock(context, world, args)));
        owner.bindWorldMethod(worldTable, implementations, "SetBlockState", MLuaWorldService.levelFunction(level,
            (world, args) -> setBlockState(context, world, args)));
        owner.bindWorldMethod(worldTable, implementations, "BreakBlock", MLuaWorldService.levelFunction(level,
            (world, args) -> {
                ServerLevel serverLevel = MLuaWorldService.requireServerWorld(world, "BreakBlock");
                BlockPos position = MLuaPositionArguments.blockPosition(args, 2);
                int dropIndex = 2 + MLuaPositionArguments.argumentWidth(args, 2);
                return LuaValue.valueOf(serverLevel.destroyBlock(position, args.optboolean(dropIndex, true)));
            }));
        owner.bindWorldAlias(worldTable, implementations, "DestroyBlock", "BreakBlock");
    }

    private static LuaValue setBlock(MLuaService.Context context, Level world, org.luaj.vm2.Varargs args) {
        ServerLevel serverLevel = MLuaWorldService.requireServerWorld(world, "SetBlock");
        BlockPos position = MLuaPositionArguments.blockPosition(args, 2);
        int blockIndex = 2 + MLuaPositionArguments.argumentWidth(args, 2);
        LuaValue blockValue = args.arg(blockIndex);
        BlockState state;
        if (blockValue.isstring()) {
            String blockId = blockValue.tojstring();
            if (!blockId.contains(":")) blockId = context.mlua().ownerModId() + ":" + blockId;
            Identifier identifier = Identifier.tryParse(blockId);
            if (identifier == null) throw new LuaError("[MLua] Invalid block id: " + blockId);
            Block block = BuiltInRegistries.BLOCK.getOptional(identifier).orElse(null);
            if (block == null) {
                String[] parts = blockId.split(":", 2);
                RegistryHandle<Block> handle = MLuaLoaderRegistry.get(RegistryKind.BLOCK, parts[0], parts[1]);
                block = handle == null ? null : handle.get();
            }
            if (block == null) throw new LuaError("[MLua] Unknown block: " + blockId);
            state = block.defaultBlockState();
        } else {
            state = MLuaBlockStateWrapper.blockStateFromLua(blockValue, context.mlua().registration());
        }

        int propertiesIndex = blockIndex + 1;
        LuaValue propertiesArgument = args.arg(propertiesIndex);
        LuaValue properties = propertiesArgument.istable() ? propertiesArgument : LuaValue.NIL;
        LuaValue loadOrFlags;
        boolean loadChunk = true;
        LuaValue flagsArgument;
        if (propertiesArgument.istable()) {
            loadOrFlags = args.arg(propertiesIndex + 1);
            if (loadOrFlags.isboolean()) {
                loadChunk = loadOrFlags.toboolean();
                flagsArgument = args.arg(propertiesIndex + 2);
            } else {
                flagsArgument = loadOrFlags;
            }
        } else if (propertiesArgument.isboolean()) {
            loadChunk = propertiesArgument.toboolean();
            flagsArgument = args.arg(propertiesIndex + 1);
        } else if (propertiesArgument.isnumber()) {
            flagsArgument = propertiesArgument;
        } else if (propertiesArgument.isnil()) {
            loadOrFlags = args.arg(propertiesIndex + 1);
            if (loadOrFlags.isboolean()) {
                loadChunk = loadOrFlags.toboolean();
                flagsArgument = args.arg(propertiesIndex + 2);
            } else {
                flagsArgument = loadOrFlags;
            }
        } else {
            throw new LuaError("[MLua] World:SetBlock expects a block id or BlockState followed by properties, a loadChunk boolean, or update flags.");
        }
        int flags = MLuaBlockUpdateFlags.read(flagsArgument, "World:SetBlock");
        state = MLuaBlockStateProperties.apply(state, properties);
        if (!loadChunk && !serverLevel.hasChunkAt(position)) return LuaValue.FALSE;
        if (loadChunk && !MLuaChunkWrapper.ensureLoaded(serverLevel, position)) return LuaValue.FALSE;
        return LuaValue.valueOf(serverLevel.setBlock(position, state, flags));
    }

    private static LuaValue setBlockState(MLuaService.Context context, Level world, org.luaj.vm2.Varargs args) {
        ServerLevel serverLevel = MLuaWorldService.requireServerWorld(world, "SetBlockState");
        BlockPos position = MLuaPositionArguments.blockPosition(args, 2);
        int stateIndex = 2 + MLuaPositionArguments.argumentWidth(args, 2);
        BlockState state = MLuaBlockStateWrapper.blockStateFromLua(args.arg(stateIndex), context.mlua().registration());
        LuaValue loadArgument = args.arg(stateIndex + 1);
        boolean loadChunk = !loadArgument.isboolean() || loadArgument.toboolean();
        int flags = MLuaBlockUpdateFlags.read(loadArgument.isboolean() ? args.arg(stateIndex + 2) : loadArgument,
            "World:SetBlockState");
        if (!loadChunk && !serverLevel.hasChunkAt(position)) return LuaValue.FALSE;
        if (loadChunk && !MLuaChunkWrapper.ensureLoaded(serverLevel, position)) return LuaValue.FALSE;
        return LuaValue.valueOf(serverLevel.setBlock(position, state, flags));
    }
}
