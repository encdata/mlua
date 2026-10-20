package dev.mlua.modules.world.service;

import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.minecraft.binding.MLuaBlockStateProperties;
import dev.mlua.minecraft.binding.MLuaBlockStateWrapper;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import dev.mlua.modules.entity.MLuaEntitySpawner;
import dev.mlua.modules.players.classes.MLuaEntityWrapper;
import dev.mlua.modules.world.MLuaBlockUpdateFlags;
import dev.mlua.modules.world.MLuaPositionArguments;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.core.service.MLuaService;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Binds server-side world changes and entity creation operations. */
final class MLuaWorldMutationApi {
   private MLuaWorldMutationApi() {}

   static void bind(LuaTable table, Supplier<Level> supplier, MLuaService.Context context,
      MLuaRegistration registration, String ownerModId) {
      table.rawset("SetBlock", MLuaWorldApiSupport.serverLevelFn(context, supplier, (level, args) -> {
         double[] pos = MLuaPositionArguments.vector3(args, 2);
         int blockIndex = 2 + MLuaPositionArguments.argumentWidth(args, 2);
         BlockPos blockPos = BlockPos.containing(pos[0], pos[1], pos[2]);
         LuaValue blockValue = args.arg(blockIndex);
         BlockState state;
         if (registration.isInstanceOf(blockValue, "BlockState")) {
            state = MLuaBlockStateWrapper.blockStateFromLua(blockValue, registration);
         } else {
            String blockId = blockValue.checkjstring();
            Block block = BuiltInRegistries.BLOCK.getOptional(MLuaResourceLocations.parse(blockId)).orElse(null);
            if (block == null) {
               throw new LuaError("[MLua] Unknown block: " + blockId);
            }
            state = block.defaultBlockState();
         }
         LuaValue propertiesArg = args.arg(blockIndex + 1);
         if (!propertiesArg.isnil() && !propertiesArg.istable() && !propertiesArg.isboolean() && !propertiesArg.isnumber()) {
            throw new LuaError("[MLua] World:SetBlock expects block properties, a loadChunk boolean, or update flags after the block.");
         }
         LuaValue properties = propertiesArg.istable() ? propertiesArg : LuaValue.NIL;
         boolean loadChunk = true;
         LuaValue updateFlagsValue;
         if (propertiesArg.istable()) {
            LuaValue loadOrFlags = args.arg(blockIndex + 2);
            if (loadOrFlags.isboolean()) {
               loadChunk = loadOrFlags.toboolean();
               updateFlagsValue = args.arg(blockIndex + 3);
            } else {
               updateFlagsValue = loadOrFlags.isnil() ? args.arg(blockIndex + 3) : loadOrFlags;
            }
         } else if (propertiesArg.isboolean()) {
            loadChunk = propertiesArg.toboolean();
            updateFlagsValue = args.arg(blockIndex + 2);
         } else if (propertiesArg.isnumber()) {
            updateFlagsValue = propertiesArg;
         } else if (propertiesArg.isnil()) {
            LuaValue loadOrFlags = args.arg(blockIndex + 2);
            if (loadOrFlags.isboolean()) {
               loadChunk = loadOrFlags.toboolean();
               updateFlagsValue = args.arg(blockIndex + 3);
            } else {
               updateFlagsValue = loadOrFlags.isnil() ? args.arg(blockIndex + 3) : loadOrFlags;
            }
         } else {
            throw new LuaError("[MLua] World:SetBlock expects block properties, a loadChunk boolean, or integer update flags after the block.");
         }
         int updateFlags = MLuaBlockUpdateFlags.read(updateFlagsValue, "World:SetBlock");
         BlockState resolvedState = MLuaBlockStateProperties.apply(state, properties);
         if (!MLuaWorldApiSupport.ensureChunkLoaded(level, blockPos, loadChunk)) return LuaValue.FALSE;
         return LuaValue.valueOf(level.setBlock(blockPos, resolvedState, updateFlags));
      }));
      table.rawset("SetBlockState", MLuaWorldApiSupport.serverLevelFn(context, supplier, (level, args) -> {
         double[] pos = MLuaPositionArguments.vector3(args, 2);
         BlockPos blockPos = BlockPos.containing(pos[0], pos[1], pos[2]);
         int stateIndex = 2 + MLuaPositionArguments.argumentWidth(args, 2);
         BlockState state =
            MLuaBlockStateWrapper.blockStateFromLua(args.arg(stateIndex), registration);
         LuaValue loadOrFlags = args.arg(stateIndex + 1);
         boolean loadChunk = !loadOrFlags.isboolean() || loadOrFlags.toboolean();
         int flagsIndex = loadOrFlags.isboolean() ? stateIndex + 2 : stateIndex + 1;
         int updateFlags = MLuaBlockUpdateFlags.read(args.arg(flagsIndex), "World:SetBlockState");
         if (!MLuaWorldApiSupport.ensureChunkLoaded(level, blockPos, loadChunk)) return LuaValue.FALSE;
         return LuaValue.valueOf(level.setBlock(blockPos, state, updateFlags));
      }));
      table.rawset("BreakBlock", MLuaWorldApiSupport.serverLevelFn(context, supplier, (level, args) -> {
         double[] pos = MLuaPositionArguments.vector3(args, 2);
         boolean dropItems = MLuaLuaArguments.optionalBoolean(
            args, 2 + MLuaPositionArguments.argumentWidth(args, 2), true, "World:BreakBlock", "dropItems");
         level.destroyBlock(BlockPos.containing(pos[0], pos[1], pos[2]), dropItems);
         return LuaValue.TRUE;
      }));
      table.rawset("DestroyBlock", table.get("BreakBlock"));
      table.rawset("PlaySound", MLuaWorldApiSupport.serverLevelFn(context, supplier, (level, args) -> {
         double[] pos = MLuaPositionArguments.vector3(args, 2);
         int soundIndex = 2 + MLuaPositionArguments.argumentWidth(args, 2);
         String soundName = args.checkjstring(soundIndex);
         SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getOptional(MLuaResourceLocations.parse(soundName)).orElse(null);
         if (sound == null) {
            throw new LuaError("[MLua] Unknown sound: " + soundName);
         }
         float volume = MLuaLuaArguments.optionalNonNegativeFiniteFloat(
            args, soundIndex + 1, 1.0f, "World:PlaySound", "volume");
         float pitch = MLuaLuaArguments.optionalNonNegativeFiniteFloat(
            args, soundIndex + 2, 1.0f, "World:PlaySound", "pitch");
         level.playSound((Player)null, pos[0], pos[1], pos[2], sound, SoundSource.PLAYERS, volume, pitch);
         return LuaValue.NONE;
      }));
      table.rawset("SpawnParticle", MLuaWorldApiSupport.serverLevelFn(context, supplier, (level, args) -> {
         double[] pos = MLuaPositionArguments.vector3(args, 2);
         int particleIndex = 2 + MLuaPositionArguments.argumentWidth(args, 2);
         String particleName = args.checkjstring(particleIndex);
         int count = MLuaWorldApiSupport.checkedIntArgument(args, particleIndex + 1, 1, 1, Integer.MAX_VALUE, "particle count");
         ResourceLocation id = MLuaResourceLocations.tryParseOrNull(particleName);
         if (id == null) {
            throw new LuaError("[MLua] Invalid particle id: " + particleName);
         }
         ParticleType<?> particle = BuiltInRegistries.PARTICLE_TYPE.getOptional(id).orElse(null);
         if (particle == null) {
            throw new LuaError("[MLua] Unknown particle: " + particleName);
         }
         if (!(particle instanceof ParticleOptions options)) {
            throw new LuaError("[MLua] Particle type " + particleName + " does not have default options");
         }
         level.sendParticles(options, pos[0], pos[1], pos[2], count, 0.5, 0.5, 0.5, 0.0);
         return LuaValue.NONE;
      }));
      table.rawset("SpawnEntity", MLuaWorldApiSupport.serverLevelFn(context, supplier, (level, args) -> {
         String entityId = args.checkjstring(2);
         double[] pos = MLuaPositionArguments.vector3(args, 3);
         int rotationIndex = 3 + MLuaPositionArguments.argumentWidth(args, 3);
         float yaw = MLuaPositionArguments.yaw(args, 3, rotationIndex);
         float pitch = MLuaPositionArguments.pitch(args, 3, rotationIndex + 1);
         EntityType<?> entityType = MLuaEntitySpawner.resolveType(level, ownerModId, entityId);
         if (entityType == null) throw new LuaError("[MLua] Unknown entity: " + entityId);
         Entity entity = MLuaEntitySpawner.spawn(level, entityType, pos[0], pos[1], pos[2], yaw, pitch);
         return MLuaEntityWrapper.entityTable(entity, registration);
      }));
      table.rawset("DropItem", MLuaWorldApiSupport.serverLevelFn(context, supplier, (level, args) -> {
         double[] pos = MLuaPositionArguments.vector3(args, 2);
         int itemIndex = 2 + MLuaPositionArguments.argumentWidth(args, 2);
         String itemId = args.checkjstring(itemIndex);
         int count = MLuaWorldApiSupport.checkedIntArgument(args, itemIndex + 1, 1, 1, Integer.MAX_VALUE, "item count");
         Item item = BuiltInRegistries.ITEM.getOptional(MLuaResourceLocations.parse(itemId)).orElse(null);
         if (item == null) {
            throw new LuaError("[MLua] Unknown item: " + itemId);
         }
         int maxStackSize = new ItemStack(item).getMaxStackSize();
         if (count > maxStackSize) {
            throw new LuaError("[MLua] Item count " + count + " exceeds " + itemId
               + " stack limit " + maxStackSize);
         }
         if (!MLuaWorldApiSupport.ensureChunkLoaded(level, BlockPos.containing(pos[0], pos[1], pos[2]), true)) return LuaValue.NIL;
         ItemEntity entity = new ItemEntity(level, pos[0], pos[1], pos[2], new ItemStack(item, count));
         if (!level.addFreshEntity(entity)) return LuaValue.NIL;
         return MLuaEntityWrapper.entityTable(entity, registration);
      }));
      table.rawset("Explode", MLuaWorldApiSupport.serverLevelFn(context, supplier, (level, args) -> {
         double[] pos = MLuaPositionArguments.vector3(args, 2);
         int powerIndex = 2 + MLuaPositionArguments.argumentWidth(args, 2);
         float power = MLuaLuaArguments.optionalFiniteFloat(args, powerIndex, 4.0f, "World:Explode", "power");
         if (power < 0.0f) throw new LuaError("[MLua] World:Explode power must be non-negative.");
         boolean fire = MLuaLuaArguments.optionalBoolean(args, powerIndex + 1, false, "World:Explode", "fire");
         level.explode((Entity)null, pos[0], pos[1], pos[2], power, fire, Level.ExplosionInteraction.BLOCK);
         return LuaValue.NONE;
      }));
      table.rawset("SpawnLightning", MLuaWorldApiSupport.serverLevelFn(context, supplier, (level, args) -> {
         double[] pos = MLuaPositionArguments.vector3(args, 2);
         if (!MLuaWorldApiSupport.ensureChunkLoaded(level, BlockPos.containing(pos[0], pos[1], pos[2]), true)) return LuaValue.NIL;
         //--version 26.1
         Entity lightning = EntityType.LIGHTNING_BOLT.create(level,
            net.minecraft.world.entity.EntitySpawnReason.COMMAND);
         //--endversion
         //--version 1.20.1,1.21.1
         Entity lightning = EntityType.LIGHTNING_BOLT.create(level);
         //--endversion
         if (lightning == null) {
            throw new LuaError("[MLua] Failed to create lightning entity");
         }
         lightning.setPos(pos[0], pos[1], pos[2]);
         if (!level.addFreshEntity(lightning)) return LuaValue.NIL;
         return MLuaEntityWrapper.entityTable(lightning, registration);
      }));

   }
}
