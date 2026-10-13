package dev.mlua.runtime.client;

import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

public final class MLuaClientParticleManager {
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
    private static final ConcurrentHashMap<Integer, Emitter> EMITTERS = new ConcurrentHashMap<>();
    private static final MLuaParticleOwnershipRegistry OWNERS = new MLuaParticleOwnershipRegistry();

    private MLuaClientParticleManager() {
    }

    public static int createEmitter(String particleId, double x, double y, double z, double rate, double spread, double speed) {
        return createEmitter(particleId, x, y, z, rate, spread, speed, new MLuaParticleOwnershipRegistry.Owner(null, ""));
    }

    public static int createEmitter(String particleId, double x, double y, double z, double rate, double spread, double speed, MLuaParticleOwnershipRegistry.Owner owner) {
        return createEmitter(new ParticleSpec(particleId, "simple", null, null, null, 1.0F), x, y, z, rate, spread, speed, owner);
    }

    public static int createEmitter(ParticleSpec spec, double x, double y, double z, double rate, double spread, double speed) {
        return createEmitter(spec, x, y, z, rate, spread, speed, new MLuaParticleOwnershipRegistry.Owner(null, ""));
    }

    public static int createEmitter(ParticleSpec spec, double x, double y, double z, double rate, double spread, double speed, MLuaParticleOwnershipRegistry.Owner owner) {
        int id = NEXT_ID.getAndIncrement();
        EMITTERS.put(id, new Emitter(id, spec, x, y, z, rate, spread, speed));
        OWNERS.register(id, owner);
        return id;
    }

    public static Emitter get(int id) {
        return EMITTERS.get(id);
    }

    public static Emitter get(int id, MLuaParticleOwnershipRegistry.Owner owner) {
        return OWNERS.owns(id, owner) ? EMITTERS.get(id) : null;
    }

    public static void remove(int id) {
        EMITTERS.remove(id);
        OWNERS.remove(id);
    }

    public static void remove(int id, MLuaParticleOwnershipRegistry.Owner owner) {
        if (OWNERS.owns(id, owner)) remove(id);
    }

    public static void clearOwned(MLuaParticleOwnershipRegistry.Owner owner) {
        OWNERS.clearOwned(owner).forEach(MLuaClientParticleManager::remove);
    }

    public static void clearRuntime(Object runtimeOwner) {
        OWNERS.clearRuntime(runtimeOwner).forEach(MLuaClientParticleManager::remove);
    }

    public static void removeFollowingEntity(Entity entity) {
        if (entity == null) return;
        removeFollowingEntity(MLuaEntityScope.of(entity));
    }

    public static void removeFollowingEntity(MLuaEntityScope scope) {
        if (scope == null) return;
        EMITTERS.forEach((id, emitter) -> {
            if (scope.equals(emitter.followEntityScope()) && EMITTERS.remove(id, emitter)) OWNERS.remove(id);
        });
    }

    public static void clearAll() {
        EMITTERS.clear();
        OWNERS.clear();
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            EMITTERS.entrySet().removeIf(entry -> {
                boolean remove = entry.getValue().followEntityScope() != null;
                if (remove) OWNERS.remove(entry.getKey());
                return remove;
            });
            return;
        }

        for (Emitter emitter : EMITTERS.values()) {
            if (!emitter.enabled) {
                continue;
            }

            if (emitter.followEntityScope != null) {
                Entity entity = resolveFollowEntity(emitter.followEntityScope);
                if (entity == null || !entity.isAlive()) {
                    if (EMITTERS.remove(emitter.id, emitter)) OWNERS.remove(emitter.id);
                    continue;
                }
                emitter.x = entity.getX() + emitter.offsetX;
                emitter.y = entity.getY() + emitter.offsetY;
                emitter.z = entity.getZ() + emitter.offsetZ;
            }

            emitter.accumulator += emitter.rate / 20.0;
            while (emitter.accumulator >= 1.0) {
                emitter.accumulator -= 1.0;
                ParticleOptions particle = resolve(emitter.spec);
                if (particle == null) {
                    continue;
                }
                double dx = (Math.random() - 0.5) * emitter.spreadX;
                double dy = (Math.random() - 0.5) * emitter.spreadY;
                double dz = (Math.random() - 0.5) * emitter.spreadZ;
                mc.level.addParticle(particle, emitter.x + dx, emitter.y + dy, emitter.z + dz, emitter.velocityX, emitter.velocityY + emitter.speed, emitter.velocityZ);
            }
        }
    }

    public static void spawnOnce(String particleId, double x, double y, double z, int count, double spread, double speed) {
        spawnOnce(new ParticleSpec(particleId, "simple", null, null, null, 1.0F), x, y, z, count, spread, speed);
    }

    public static void spawnOnce(ParticleSpec spec, double x, double y, double z, int count, double spread, double speed) {
        spawnBurst(spec, x, y, z, count, spread, spread, spread, 0.0, speed, 0.0);
    }

    public static void spawnBurst(ParticleSpec spec, double x, double y, double z, int count, double spreadX, double spreadY, double spreadZ, double velocityX, double velocityY, double velocityZ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        ParticleOptions particle = resolve(spec);
        if (particle == null) {
            return;
        }
        for (int i = 0; i < count; i++) {
            double dx = (Math.random() - 0.5) * spreadX;
            double dy = (Math.random() - 0.5) * spreadY;
            double dz = (Math.random() - 0.5) * spreadZ;
            mc.level.addParticle(particle, x + dx, y + dy, z + dz, velocityX, velocityY, velocityZ);
        }
    }

    public static void spawnRing(ParticleSpec spec, double x, double y, double z, int count, double radius, double outwardSpeed) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        ParticleOptions particle = resolve(spec);
        if (particle == null) {
            return;
        }
        int safeCount = Math.max(1, count);
        for (int i = 0; i < safeCount; i++) {
            double angle = (Math.PI * 2.0 * i) / safeCount;
            double px = x + Math.cos(angle) * radius;
            double pz = z + Math.sin(angle) * radius;
            double vx = Math.cos(angle) * outwardSpeed;
            double vz = Math.sin(angle) * outwardSpeed;
            mc.level.addParticle(particle, px, y, pz, vx, 0.0, vz);
        }
    }

    public static void spawnCone(ParticleSpec spec, double x, double y, double z, double dirX, double dirY, double dirZ, int count, double angleDegrees, double speed) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        ParticleOptions particle = resolve(spec);
        if (particle == null) {
            return;
        }
        Vector3f direction = new Vector3f((float) dirX, (float) dirY, (float) dirZ);
        if (direction.lengthSquared() <= 1.0E-6F) {
            direction.set(0.0F, 1.0F, 0.0F);
        } else {
            direction.normalize();
        }
        double angle = Math.toRadians(Math.max(0.0, angleDegrees));
        for (int i = 0; i < Math.max(1, count); i++) {
            Vector3f jitter = new Vector3f(
                (float) ((Math.random() - 0.5) * Math.sin(angle)),
                (float) ((Math.random() - 0.5) * Math.sin(angle)),
                (float) ((Math.random() - 0.5) * Math.sin(angle))
            );
            Vector3f velocity = new Vector3f(direction).add(jitter).normalize().mul((float) speed);
            mc.level.addParticle(particle, x, y, z, velocity.x, velocity.y, velocity.z);
        }
    }

    private static ParticleOptions resolve(ParticleSpec spec) {
        return switch (spec.kind) {
            //--version 26.1
            case "dust" -> new DustParticleOptions(
                ((Math.round((spec.red != null ? spec.red : 1.0F) * 255.0F) & 0xff) << 16)
                    | ((Math.round((spec.green != null ? spec.green : 1.0F) * 255.0F) & 0xff) << 8)
                    | (Math.round((spec.blue != null ? spec.blue : 1.0F) * 255.0F) & 0xff),
                spec.size
            );
            //--endversion
            //--version 1.20.1,1.21.1
            case "dust" -> new DustParticleOptions(
                new Vector3f(spec.red != null ? spec.red : 1.0F, spec.green != null ? spec.green : 1.0F, spec.blue != null ? spec.blue : 1.0F),
                spec.size
            );
            //--endversion
            case "block" -> {
                ResourceLocation blockId = MLuaResourceLocations.tryParseOrNull(spec.id);
                yield blockId != null && BuiltInRegistries.BLOCK.containsKey(blockId)
                    ? new BlockParticleOption(ParticleTypes.BLOCK, BuiltInRegistries.BLOCK.get(blockId).defaultBlockState())
                    : null;
            }
            case "item" -> {
                ResourceLocation itemId = MLuaResourceLocations.tryParseOrNull(spec.id);
                yield itemId != null && BuiltInRegistries.ITEM.containsKey(itemId)
                    ? new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(BuiltInRegistries.ITEM.get(itemId)))
                    : null;
            }
            default -> resolveSimple(spec.id);
        };
    }

    private static Entity resolveFollowEntity(MLuaEntityScope scope) {
        if (scope.side() != dev.mlua.common.enums.Side.CLIENT) return null;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !minecraft.level.dimension().location().toString().equals(scope.dimension())) return null;
        Entity entity = minecraft.level.getEntity(scope.entityId());
        return entity != null && entity.getUUID().equals(scope.uuid()) ? entity : null;
    }

    private static ParticleOptions resolveSimple(String id) {
        ResourceLocation location = MLuaResourceLocations.tryParseOrNull(id);
        if (location == null) {
            return null;
        }
        return BuiltInRegistries.PARTICLE_TYPE.getOptional(location)
            .filter(SimpleParticleType.class::isInstance)
            .map(SimpleParticleType.class::cast)
            .orElse(null);
    }

    public static final class Emitter {
        private final int id;
        private ParticleSpec spec;
        private double x;
        private double y;
        private double z;
        private double rate;
        private double spreadX;
        private double spreadY;
        private double spreadZ;
        private double speed;
        private double velocityX;
        private double velocityY;
        private double velocityZ;
        private MLuaEntityScope followEntityScope;
        private double offsetX;
        private double offsetY;
        private double offsetZ;
        private boolean enabled = true;
        private double accumulator;

        private Emitter(int id, ParticleSpec spec, double x, double y, double z, double rate, double spread, double speed) {
            this.id = id;
            this.spec = spec;
            this.x = x;
            this.y = y;
            this.z = z;
            this.rate = Math.max(0.0, rate);
            this.spreadX = Math.max(0.0, spread);
            this.spreadY = Math.max(0.0, spread);
            this.spreadZ = Math.max(0.0, spread);
            this.speed = speed;
        }

        public int id() { return id; }
        public ParticleSpec spec() { return spec; }
        public double x() { return x; }
        public double y() { return y; }
        public double z() { return z; }
        public double rate() { return rate; }
        public double spread() { return Math.max(spreadX, Math.max(spreadY, spreadZ)); }
        public double speed() { return speed; }
        public boolean enabled() { return enabled; }
        public MLuaEntityScope followEntityScope() { return followEntityScope; }
        public double offsetX() { return offsetX; }
        public double offsetY() { return offsetY; }
        public double offsetZ() { return offsetZ; }
        public double velocityX() { return velocityX; }
        public double velocityY() { return velocityY; }
        public double velocityZ() { return velocityZ; }

        public void setParticleId(String particleId) { this.spec = new ParticleSpec(particleId, "simple", null, null, null, 1.0F); }
        public void setSpec(ParticleSpec spec) { this.spec = spec; }
        public void setPosition(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
        public void setRate(double rate) { this.rate = Math.max(0.0, rate); }
        public void setSpread(double spread) {
            double safe = Math.max(0.0, spread);
            this.spreadX = safe;
            this.spreadY = safe;
            this.spreadZ = safe;
        }
        public void setSpread(double x, double y, double z) {
            this.spreadX = Math.max(0.0, x);
            this.spreadY = Math.max(0.0, y);
            this.spreadZ = Math.max(0.0, z);
        }
        public void setSpeed(double speed) { this.speed = speed; }
        public void setVelocity(double x, double y, double z) { this.velocityX = x; this.velocityY = y; this.velocityZ = z; }
        public void setFollowEntity(MLuaEntityScope scope) { this.followEntityScope = scope; }
        public void setOffset(double x, double y, double z) { this.offsetX = x; this.offsetY = y; this.offsetZ = z; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public record ParticleSpec(String id, String kind, Float red, Float green, Float blue, float size) {
    }
}
