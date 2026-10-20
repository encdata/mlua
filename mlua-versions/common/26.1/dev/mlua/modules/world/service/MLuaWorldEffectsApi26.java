package dev.mlua.modules.world.service;

import dev.mlua.core.service.MLuaService;
import dev.mlua.loaderapi.RegistryHandle;
import dev.mlua.loaderapi.RegistryKind;
import dev.mlua.modules.players.classes.MLuaEntityWrapper;
import dev.mlua.modules.world.MLuaPositionArguments;
import dev.mlua.runtime.registry.MLuaLoaderRegistry;
import java.util.Map;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Binds world sounds, particles, drops, explosions, and entity spawning for 26.x Worlds. */
final class MLuaWorldEffectsApi26 {
    private MLuaWorldEffectsApi26() { }

    static void bind(MLuaWorldService owner, MLuaService.Context context, LuaTable worldTable,
                     Map<String, LuaValue> implementations, Level level) {
        owner.bindWorldMethod(worldTable, implementations, "PlaySound", MLuaWorldService.levelFunction(level, (world, args) -> {
            ServerLevel serverLevel = MLuaWorldService.requireServerWorld(world, "PlaySound");
            int soundIndex = 2 + MLuaPositionArguments.argumentWidth(args, 2);
            Identifier id = Identifier.tryParse(args.checkjstring(soundIndex));
            SoundEvent sound = id == null ? null : BuiltInRegistries.SOUND_EVENT.getOptional(id).orElse(null);
            if (sound == null) throw new LuaError("[MLua] Unknown sound: " + args.checkjstring(soundIndex));
            double[] position = MLuaPositionArguments.vector3(args, 2);
            serverLevel.playSound((Player) null, position[0], position[1], position[2], sound,
                SoundSource.PLAYERS, (float) args.optdouble(soundIndex + 1, 1.0),
                (float) args.optdouble(soundIndex + 2, 1.0));
            return LuaValue.NONE;
        }));
        owner.bindWorldMethod(worldTable, implementations, "DropItem", MLuaWorldService.levelFunction(level, (world, args) -> {
            ServerLevel serverLevel = MLuaWorldService.requireServerWorld(world, "DropItem");
            int itemIndex = 2 + MLuaPositionArguments.argumentWidth(args, 2);
            String itemId = args.checkjstring(itemIndex);
            Identifier id = Identifier.tryParse(itemId.contains(":") ? itemId : "minecraft:" + itemId);
            Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
            if (item == null) throw new LuaError("[MLua] Unknown item: " + itemId);
            double[] position = MLuaPositionArguments.vector3(args, 2);
            ItemEntity dropped = new ItemEntity(serverLevel, position[0], position[1], position[2],
                new ItemStack(item, Math.max(1, args.optint(itemIndex + 1, 1))));
            serverLevel.addFreshEntity(dropped);
            return MLuaEntityWrapper.entityTable(dropped, context.mlua().registration());
        }));
        owner.bindWorldMethod(worldTable, implementations, "Explode", MLuaWorldService.levelFunction(level, (world, args) -> {
            ServerLevel serverLevel = MLuaWorldService.requireServerWorld(world, "Explode");
            double[] position = MLuaPositionArguments.vector3(args, 2);
            int powerIndex = 2 + MLuaPositionArguments.argumentWidth(args, 2);
            serverLevel.explode(null, position[0], position[1], position[2],
                (float) args.optdouble(powerIndex, 4.0), args.optboolean(powerIndex + 1, false),
                Level.ExplosionInteraction.BLOCK);
            return LuaValue.NONE;
        }));
        owner.bindWorldMethod(worldTable, implementations, "SpawnLightning", MLuaWorldService.levelFunction(level, (world, args) -> {
            ServerLevel serverLevel = MLuaWorldService.requireServerWorld(world, "SpawnLightning");
            Entity lightning = EntityType.LIGHTNING_BOLT.create(serverLevel, EntitySpawnReason.COMMAND);
            if (lightning == null) throw new LuaError("[MLua] Failed to create lightning entity.");
            double[] position = MLuaPositionArguments.vector3(args, 2);
            lightning.setPos(position[0], position[1], position[2]);
            serverLevel.addFreshEntity(lightning);
            return MLuaEntityWrapper.entityTable(lightning, context.mlua().registration());
        }));
        owner.bindWorldMethod(worldTable, implementations, "SpawnEntity", MLuaWorldService.levelFunction(level, (world, args) -> {
            ServerLevel serverLevel = MLuaWorldService.requireServerWorld(world, "SpawnEntity");
            String entityId = args.checkjstring(2);
            if (!entityId.contains(":")) entityId = context.mlua().ownerModId() + ":" + entityId;
            Identifier id = Identifier.tryParse(entityId);
            if (id == null) throw new LuaError("[MLua] Invalid entity id: " + entityId);
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
            if (type == null) {
                String[] parts = entityId.split(":", 2);
                RegistryHandle<EntityType<?>> handle = MLuaLoaderRegistry.get(RegistryKind.ENTITY, parts[0], parts[1]);
                type = handle == null ? null : handle.get();
            }
            if (type == null) throw new LuaError("[MLua] Unknown entity: " + entityId);
            Entity entity = type.create(serverLevel, EntitySpawnReason.COMMAND);
            if (entity == null) throw new LuaError("[MLua] Failed to spawn entity: " + entityId);
            int positionIndex = 3;
            double[] position = MLuaPositionArguments.vector3(args, positionIndex);
            int rotationIndex = positionIndex + MLuaPositionArguments.argumentWidth(args, positionIndex);
            entity.setPos(position[0], position[1], position[2]);
            entity.setYRot(MLuaPositionArguments.yaw(args, positionIndex, rotationIndex));
            entity.setXRot(MLuaPositionArguments.pitch(args, positionIndex, rotationIndex + 1));
            serverLevel.addFreshEntity(entity);
            return MLuaEntityWrapper.entityTable(entity, context.mlua().registration());
        }));
        owner.bindWorldMethod(worldTable, implementations, "SpawnParticle", MLuaWorldService.levelFunction(level, (world, args) -> {
            ServerLevel serverLevel = MLuaWorldService.requireServerWorld(world, "SpawnParticle");
            int particleIndex = 2 + MLuaPositionArguments.argumentWidth(args, 2);
            String particleId = args.checkjstring(particleIndex);
            Identifier id = Identifier.tryParse(particleId);
            ParticleType<?> particle = id == null ? null : BuiltInRegistries.PARTICLE_TYPE.getOptional(id).orElse(null);
            if (particle == null) throw new LuaError("[MLua] Unknown particle: " + particleId);
            if (!(particle instanceof ParticleOptions options)) {
                throw new LuaError("[MLua] Particle type " + particleId + " does not have default options.");
            }
            double[] position = MLuaPositionArguments.vector3(args, 2);
            serverLevel.sendParticles(options, position[0], position[1], position[2],
                args.optint(particleIndex + 1, 1), 0.5, 0.5, 0.5, 0.0);
            return LuaValue.NONE;
        }));
    }
}
