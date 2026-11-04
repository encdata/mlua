package dev.mlua.minecraft.scripted;

import dev.mlua.minecraft.binding.MLuaBlockWrapper;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Binds scripted entity interaction with doors and door navigation. */
final class MLuaScriptedEntityDoorApi {
    private MLuaScriptedEntityDoorApi() { }

    static void bind(LuaTable methods, MLuaScriptedEntity entity) {
        methods.set("OpenDoor", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                entity.requireServerSide("OpenDoor");
                BlockPos pos = entity.blockArg(args, 2);
                BlockState state = MLuaBlockWrapper.doorBaseState(entity.level(), pos);
                if (!(state.getBlock() instanceof DoorBlock doorBlock) || !state.hasProperty(DoorBlock.OPEN)) {
                    return LuaValue.valueOf(false);
                }
                pos = MLuaBlockWrapper.doorBasePos(entity.level(), pos);
                boolean isOpen = state.getValue(DoorBlock.OPEN);
                doorBlock.setOpen(entity, entity.level(), state, pos, !isOpen);
                return LuaValue.valueOf(true);
            }
        });
        methods.set("CloseDoor", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                entity.requireServerSide("CloseDoor");
                BlockPos pos = entity.blockArg(args, 2);
                BlockState state = MLuaBlockWrapper.doorBaseState(entity.level(), pos);
                if (!(state.getBlock() instanceof DoorBlock doorBlock) || !state.hasProperty(DoorBlock.OPEN)) {
                    return LuaValue.valueOf(false);
                }
                pos = MLuaBlockWrapper.doorBasePos(entity.level(), pos);
                if (state.getValue(DoorBlock.OPEN)) {
                    doorBlock.setOpen(entity, entity.level(), state, pos, false);
                }
                return LuaValue.valueOf(true);
            }
        });
        methods.set("IsDoorOpen", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                BlockPos pos = entity.blockArg(args, 2);
                BlockState state = MLuaBlockWrapper.doorBaseState(entity.level(), pos);
                if (!(state.getBlock() instanceof DoorBlock) || !state.hasProperty(DoorBlock.OPEN)) {
                    return LuaValue.valueOf(false);
                }
                return LuaValue.valueOf(state.getValue(DoorBlock.OPEN));
            }
        });
        methods.set("SetDoorBreakProgress", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                entity.requireServerSide("SetDoorBreakProgress");
                BlockPos pos = MLuaBlockWrapper.doorBasePos(entity.level(), entity.blockArg(args, 2));
                BlockState state = entity.level().getBlockState(pos);
                int progressIndex = args.arg(2).istable() ? 3 : 5;
                int progress = MLuaLuaArguments.wholeInt(args, progressIndex,
                    "ScriptedEntity:SetDoorBreakProgress", "progress");
                if (progress < -1 || progress > 9) {
                    throw new LuaError("[MLua] ScriptedEntity:SetDoorBreakProgress progress must be -1 to clear or 0 through 9.");
                }
                if (!(state.getBlock() instanceof DoorBlock) || !DoorBlock.isWoodenDoor(state)) {
                    return LuaValue.valueOf(false);
                }
                entity.level().destroyBlockProgress(entity.getId(), pos, progress);
                return LuaValue.valueOf(true);
            }
        });
        methods.set("BreakDoor", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                entity.requireServerSide("BreakDoor");
                BlockPos pos = MLuaBlockWrapper.doorBasePos(entity.level(), entity.blockArg(args, 2));
                Level level = entity.level();
                BlockState state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof DoorBlock) || !DoorBlock.isWoodenDoor(state)) {
                    return LuaValue.valueOf(false);
                }
                BlockPos upperPos = pos.above();
                level.destroyBlockProgress(entity.getId(), pos, -1);
                level.removeBlock(pos, false);
                if (level.getBlockState(upperPos).getBlock() == state.getBlock()) {
                    level.removeBlock(upperPos, false);
                }
                level.levelEvent(1021, pos, 0);
                level.levelEvent(2001, pos, Block.getId(state));
                return LuaValue.valueOf(true);
            }
        });
        methods.set("SetCanOpenDoors", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                entity.requireServerSide("SetCanOpenDoors");
                if (entity.getNavigation() instanceof GroundPathNavigation groundNavigation) {
                    boolean enabled = MLuaLuaArguments.optionalBoolean(args, 2, true,
                        "ScriptedEntity:SetCanOpenDoors", "enabled");
                    groundNavigation.setCanOpenDoors(enabled);
                    return LuaValue.valueOf(true);
                }
                return LuaValue.valueOf(false);
            }
        });
        methods.set("SetCanPassDoors", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                entity.requireServerSide("SetCanPassDoors");
                if (entity.getNavigation() instanceof GroundPathNavigation groundNavigation) {
                    boolean enabled = MLuaLuaArguments.optionalBoolean(args, 2, true,
                        "ScriptedEntity:SetCanPassDoors", "enabled");
                    entity.configureDoorPassage(groundNavigation, enabled);
                    return LuaValue.valueOf(true);
                }
                return LuaValue.valueOf(false);
            }
        });
    }
}
