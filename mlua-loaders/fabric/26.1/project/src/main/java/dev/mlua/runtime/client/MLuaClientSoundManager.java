package dev.mlua.runtime.client;

import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

public final class MLuaClientSoundManager {
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
    private static final ConcurrentHashMap<Integer, ManagedSoundInstance> SOUNDS = new ConcurrentHashMap<>();
    private static final MLuaSoundOwnershipRegistry OWNERS = new MLuaSoundOwnershipRegistry();

    private MLuaClientSoundManager() {
    }

    public static boolean playLocal(String soundId, float volume, float pitch) {
        return playLocal(soundId, volume, pitch, new MLuaSoundOwnershipRegistry.Owner(null, ""));
    }

    public static boolean playLocal(String soundId, float volume, float pitch, MLuaSoundOwnershipRegistry.Owner owner) {
        return playLocalId(soundId, volume, pitch, owner) >= 0;
    }

    public static int playLocalId(String soundId, float volume, float pitch, MLuaSoundOwnershipRegistry.Owner owner) {
        return play(soundId, volume, pitch, false, null, null, null, null, SoundSource.PLAYERS, 0.0, 0.0, 0.0, owner);
    }

    public static boolean playAtPlayer(String soundId, float volume, float pitch) {
        return playAtPlayer(soundId, volume, pitch, new MLuaSoundOwnershipRegistry.Owner(null, ""));
    }

    public static boolean playAtPlayer(String soundId, float volume, float pitch, MLuaSoundOwnershipRegistry.Owner owner) {
        return playAtPlayerId(soundId, volume, pitch, owner) >= 0;
    }

    public static int playAtPlayerId(String soundId, float volume, float pitch, MLuaSoundOwnershipRegistry.Owner owner) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return -1;
        }
        return play(soundId, volume, pitch, false, mc.player.getX(), mc.player.getY(), mc.player.getZ(), null,
            SoundSource.PLAYERS, 0.0, 0.0, 0.0, owner);
    }

    public static int play(String soundId, float volume, float pitch, boolean loop, Double x, Double y, Double z, MLuaEntityScope followEntityScope, SoundSource category, double followOffsetX, double followOffsetY, double followOffsetZ) {
        return play(soundId, volume, pitch, loop, x, y, z, followEntityScope, category, followOffsetX, followOffsetY, followOffsetZ,
                new MLuaSoundOwnershipRegistry.Owner(null, ""));
    }

    public static int play(String soundId, float volume, float pitch, boolean loop, Double x, Double y, Double z, MLuaEntityScope followEntityScope, SoundSource category, double followOffsetX, double followOffsetY, double followOffsetZ, MLuaSoundOwnershipRegistry.Owner owner) {
        ResourceLocation location = MLuaResourceLocations.tryParseOrNull(soundId);
        if (location == null) {
            return -1;
        }
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(location);
        if (sound == null) {
            return -1;
        }

        ManagedSoundInstance instance = new ManagedSoundInstance(sound, category, volume, pitch);
        instance.setLoopingValue(loop);
        instance.setFollowOffset(followOffsetX, followOffsetY, followOffsetZ);
        if (followEntityScope != null) {
            instance.followEntity(followEntityScope);
        } else if (x != null && y != null && z != null) {
            instance.setPosition(x, y, z);
        } else {
            instance.setRelativeUi();
        }

        int id = NEXT_ID.getAndIncrement();
        instance.id = id;
        SOUNDS.put(id, instance);
        OWNERS.register(id, owner);
        Minecraft.getInstance().getSoundManager().play(instance);
        return id;
    }

    public static ManagedSoundInstance get(int id) {
        return SOUNDS.get(id);
    }

    public static ManagedSoundInstance get(int id, MLuaSoundOwnershipRegistry.Owner owner) {
        return OWNERS.owns(id, owner) ? SOUNDS.get(id) : null;
    }

    public static boolean isActive(int id) {
        ManagedSoundInstance instance = SOUNDS.get(id);
        if (instance == null) {
            return false;
        }
        boolean active = Minecraft.getInstance().getSoundManager().isActive(instance) && !instance.isMarkedStopped();
        if (!active) {
            if (SOUNDS.remove(id, instance)) OWNERS.remove(id);
        }
        return active;
    }

    public static boolean isActive(int id, MLuaSoundOwnershipRegistry.Owner owner) {
        return OWNERS.owns(id, owner) && isActive(id);
    }

    public static void stop(int id) {
        ManagedSoundInstance instance = SOUNDS.remove(id);
        OWNERS.remove(id);
        if (instance != null) {
            instance.markStopped();
        }
    }

    public static void stop(int id, MLuaSoundOwnershipRegistry.Owner owner) {
        if (OWNERS.owns(id, owner)) stop(id);
    }

    public static void clearOwned(MLuaSoundOwnershipRegistry.Owner owner) {
        OWNERS.clearOwned(owner).forEach(MLuaClientSoundManager::stop);
    }

    public static void clearRuntime(Object runtimeOwner) {
        OWNERS.clearRuntime(runtimeOwner).forEach(MLuaClientSoundManager::stop);
    }

    public static void clearAll() {
        for (Integer id : SOUNDS.keySet()) {
            stop(id);
        }
        SOUNDS.clear();
        OWNERS.clear();
    }

    public static void removeFollowingEntity(Entity entity) {
        if (entity == null) return;
        removeFollowingEntity(MLuaEntityScope.of(entity));
    }

    public static void removeFollowingEntity(MLuaEntityScope scope) {
        if (scope == null) return;
        SOUNDS.forEach((id, instance) -> {
            if (scope.equals(instance.followEntityScope())) stop(id);
        });
    }

    public static void tick() {
        var soundManager = Minecraft.getInstance().getSoundManager();
        SOUNDS.forEach((id, instance) -> {
            if (instance.isMarkedStopped() || !soundManager.isActive(instance)) {
                if (SOUNDS.remove(id, instance)) OWNERS.remove(id);
            }
        });
    }

    public static final class ManagedSoundInstance extends AbstractTickableSoundInstance {
        private int id;
        private MLuaEntityScope followEntityScope;
        private double followOffsetX;
        private double followOffsetY;
        private double followOffsetZ;
        private boolean markedStopped;

        private ManagedSoundInstance(SoundEvent sound, SoundSource category, float volume, float pitch) {
            super(sound, category, RandomSource.create());
            this.volume = volume;
            this.pitch = pitch;
            this.looping = false;
            this.delay = 0;
            this.attenuation = Attenuation.LINEAR;
        }

        public void setPosition(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.relative = false;
            this.followEntityScope = null;
        }

        public void followEntity(MLuaEntityScope scope) {
            this.followEntityScope = scope;
            this.relative = false;
        }

        public MLuaEntityScope followEntityScope() {
            return followEntityScope;
        }

        public boolean loopingValue() {
            return looping;
        }

        public float volumeValue() {
            return volume;
        }

        public float pitchValue() {
            return pitch;
        }

        public double xPos() {
            return x;
        }

        public double yPos() {
            return y;
        }

        public double zPos() {
            return z;
        }

        public boolean isMarkedStopped() {
            return markedStopped;
        }

        public void setLoopingValue(boolean loop) {
            this.looping = loop;
        }

        public void setVolumeValue(float volume) {
            this.volume = volume;
        }

        public void setPitchValue(float pitch) {
            this.pitch = pitch;
        }

        public void setRelativeUi() {
            this.relative = true;
            this.attenuation = Attenuation.NONE;
            this.followEntityScope = null;
        }

        public void setFollowOffset(double x, double y, double z) {
            this.followOffsetX = x;
            this.followOffsetY = y;
            this.followOffsetZ = z;
        }

        public double followOffsetX() {
            return followOffsetX;
        }

        public double followOffsetY() {
            return followOffsetY;
        }

        public double followOffsetZ() {
            return followOffsetZ;
        }

        public void markStopped() {
            this.markedStopped = true;
            stop();
        }

        @Override
        public void tick() {
            if (markedStopped) {
                SOUNDS.remove(id);
                return;
            }
            if (followEntityScope == null) {
                return;
            }
            Entity entity = resolveFollowEntity(followEntityScope);
            if (entity == null || !entity.isAlive()) {
                stop();
                markedStopped = true;
                SOUNDS.remove(id);
                return;
            }
            // Keep the follow scope attached while updating the cached sound position.
            // setPosition intentionally switches the sound back to a fixed location.
            this.x = entity.getX() + followOffsetX;
            this.y = entity.getY() + followOffsetY;
            this.z = entity.getZ() + followOffsetZ;
            this.relative = false;
        }
    }

    private static Entity resolveFollowEntity(MLuaEntityScope scope) {
        Minecraft minecraft = Minecraft.getInstance();
        if (scope.side() != dev.mlua.common.enums.Side.CLIENT || minecraft.level == null
            || !minecraft.level.dimension().location().toString().equals(scope.dimension())) return null;
        Entity entity = minecraft.level.getEntity(scope.entityId());
        return entity != null && entity.getUUID().equals(scope.uuid()) ? entity : null;
    }
}
