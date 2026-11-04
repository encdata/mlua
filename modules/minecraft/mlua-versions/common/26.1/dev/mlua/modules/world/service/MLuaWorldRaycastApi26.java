package dev.mlua.modules.world.service;

import dev.mlua.core.service.MLuaService;
import dev.mlua.math.MLuaVector;
import dev.mlua.modules.world.MLuaPositionArguments;
import dev.mlua.minecraft.binding.MLuaBlockWrapper;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Binds block and entity raycasts on 26.x World instances. */
final class MLuaWorldRaycastApi26 {
    private MLuaWorldRaycastApi26() { }

    static void bind(MLuaWorldService owner, MLuaService.Context context, LuaTable worldTable,
                     Map<String, LuaValue> implementations, Level level) {
        owner.bindWorldMethod(worldTable, implementations, "Raycast", MLuaWorldService.levelFunction(level, (world, args) -> {
            double[] originValues = MLuaPositionArguments.vector3(args, 2);
            int directionIndex = 2 + MLuaPositionArguments.argumentWidth(args, 2);
            double[] directionValues = MLuaVector.components3(args.arg(directionIndex));
            int distanceIndex = directionIndex + MLuaPositionArguments.argumentWidth(args, directionIndex);
            double distance = dev.mlua.minecraft.binding.MLuaLuaArguments.optionalFiniteNumber(
                args, distanceIndex, 100.0D, "World:Raycast", "distance");
            if (!Double.isFinite(distance) || distance < 0.0) {
                throw new LuaError("[MLua] World:Raycast distance must be finite and non-negative.");
            }
            for (double component : originValues) {
                if (!Double.isFinite(component)) throw new LuaError("[MLua] World:Raycast origin must be finite.");
            }
            for (double component : directionValues) {
                if (!Double.isFinite(component)) throw new LuaError("[MLua] World:Raycast direction must be finite.");
            }
            boolean includeFluids = dev.mlua.minecraft.binding.MLuaLuaArguments.optionalBoolean(
                args, distanceIndex + 1, false, "World:Raycast", "includeFluids");
            Vec3 origin = new Vec3(originValues[0], originValues[1], originValues[2]);
            double directionScale = Math.max(Math.abs(directionValues[0]),
                Math.max(Math.abs(directionValues[1]), Math.abs(directionValues[2])));
            if (directionScale == 0.0) return LuaValue.NIL;
            Vec3 direction = new Vec3(directionValues[0] / directionScale,
                directionValues[1] / directionScale, directionValues[2] / directionScale).normalize();
            Vec3 end = origin.add(direction.scale(distance));
            if (!Double.isFinite(end.x) || !Double.isFinite(end.y) || !Double.isFinite(end.z)) {
                throw new LuaError("[MLua] World:Raycast endpoint must be finite.");
            }
            BlockHitResult hit = world.clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER,
                includeFluids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE, (Entity) null));
            if (hit.getType() == HitResult.Type.MISS) return LuaValue.NIL;
            LuaTable result = context.mlua().registration().createClassInstance("BlockRaycastResult");
            result.set("HitType", LuaValue.valueOf("block"));
            result.set("Type", LuaValue.valueOf("block"));
            result.set("Position", MLuaVector.vector3(hit.getLocation().x, hit.getLocation().y, hit.getLocation().z));
            result.set("Normal", MLuaVector.vector3(hit.getDirection().getStepX(),
                hit.getDirection().getStepY(), hit.getDirection().getStepZ()));
            result.set("Distance", LuaValue.valueOf(Math.sqrt(origin.distanceToSqr(hit.getLocation()))));
            var blockId = BuiltInRegistries.BLOCK.getKey(world.getBlockState(hit.getBlockPos()).getBlock());
            result.set("Block", blockId == null ? LuaValue.NIL : LuaValue.valueOf(blockId.toString()));
            result.set("Face", LuaValue.valueOf(hit.getDirection().getName()));
            result.set("BlockInstance", MLuaBlockWrapper.blockTable(world, hit.getBlockPos(), context.mlua().registration()));
            return result;
        }));
        owner.bindWorldMethod(worldTable, implementations, "RaycastEntity", MLuaWorldService.levelFunction(level,
            (world, args) -> MLuaWorldRaycastOperations26.raycastEntity(world, args, context.mlua().registration())));
        owner.bindWorldMethod(worldTable, implementations, "RaycastAABB", MLuaWorldService.levelFunction(level,
            (world, args) -> MLuaWorldRaycastOperations26.raycastBounds(world, args, context.mlua().registration())));
    }
}
