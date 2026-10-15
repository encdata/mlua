package dev.mlua.minecraft.scripted;

import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.MLuaLogger;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.minecraft.animation.MLuaBonePose;
import dev.mlua.minecraft.binding.MLuaBlockWrapper;
import dev.mlua.minecraft.binding.MLuaItemWrapper;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import dev.mlua.minecraft.network.MLuaAnimationSyncPacket;
import dev.mlua.minecraft.network.MLuaNetworkManager;
import dev.mlua.modules.entity.MLuaEntityQuery;
import dev.mlua.modules.players.Class.MLuaEntityWrapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public class MLuaScriptedEntity extends PathfinderMob {
   private final String behaviorKey;
   private final String className;
   private LuaTable methodsMT;
   private CompoundTag persistentData = new CompoundTag();
   private int animationSyncCounter;
   private int meleeCooldown;
   private String aiState;
   private int aiStateTicks;
   private LuaValue activeOnTick = LuaValue.NIL;
   private boolean onTickDisabled;
   private LuaValue activeAI = LuaValue.NIL;
   private boolean aiDisabled;

   public MLuaScriptedEntity(EntityType<? extends MLuaScriptedEntity> type, Level level, String behaviorKey) {
      this(type, level, behaviorKey, "ScriptedEntity");
   }

   public MLuaScriptedEntity(EntityType<? extends MLuaScriptedEntity> type, Level level, String behaviorKey, String className) {
      super(type, level);
      this.behaviorKey = behaviorKey;
      this.className = className == null || className.isBlank() ? "ScriptedEntity" : className;
      if (this.getNavigation() instanceof GroundPathNavigation groundNavigation) {
         groundNavigation.setCanOpenDoors(true);
         groundNavigation.setCanPassDoors(true);
      }
   }

   public void aiStep() {
      super.aiStep();
      if (!this.level().isClientSide) {
         this.animationSyncCounter++;
         if (this.animationSyncCounter % 2 == 0) {
            syncAnimationPose();
         }
      }
      if (!this.level().isClientSide && this.behaviorKey != null) {
         if (this.aiState != null) this.aiStateTicks++;
         if (this.meleeCooldown > 0) this.meleeCooldown--;
         LuaValue onTick = MLuaBehaviorRegistry.getBehavior("entity", this.behaviorKey, "onTick");
         if (onTick != this.activeOnTick) {
            this.activeOnTick = onTick;
            this.onTickDisabled = false;
         }
         if (!onTick.isnil() && !this.onTickDisabled) {
            try {
               onTick.invoke(this.entityTable());
            } catch (Exception var3) {
               this.onTickDisabled = true;
               MLuaLogger.LOGGER.error("MLua entity onTick callback failed for '{}'; disabled for this entity until the callback is replaced: {}",
                  this.behaviorKey, var3.getMessage());
            }
         }
         LuaValue onAI = MLuaBehaviorRegistry.getBehavior("entity", this.behaviorKey, "AI");
         if (onAI != this.activeAI) {
            this.activeAI = onAI;
            this.aiDisabled = false;
         }
         int aiInterval = MLuaBehaviorRegistry.getEntityAIInterval(this.behaviorKey);
         if (!onAI.isnil() && !this.aiDisabled && this.tickCount % aiInterval == 0) {
            try {
               this.tickScriptedAI(onAI);
            } catch (Exception var4) {
               this.aiDisabled = true;
               MLuaLogger.LOGGER.error("MLua entity AI callback failed for '{}'; disabled for this entity until the AI definition changes or SetAIState is called: {}",
                  this.behaviorKey, var4.getMessage());
            }
         }
      }
   }

   public boolean hurt(DamageSource source, float amount) {
      boolean result = super.hurt(source, amount);
      if (result && !this.level().isClientSide && this.behaviorKey != null) {
         LuaValue onHurt = MLuaBehaviorRegistry.getBehavior("entity", this.behaviorKey, "onHurt");
         if (!onHurt.isnil()) {
            try {
               onHurt.invoke(LuaValue.varargsOf(new LuaValue[]{
                  this.entityTable(), LuaValue.valueOf((double)amount), LuaValue.valueOf(source.getMsgId())
               }));
            } catch (Exception var6) {
               MLuaLogger.LOGGER.error("MLua entity onHurt callback failed for '{}': {}", this.behaviorKey, var6.getMessage());
            }
         }
      }
      return result;
   }

   public void die(DamageSource source) {
      if (!this.level().isClientSide && this.behaviorKey != null) {
         LuaValue onDeath = MLuaBehaviorRegistry.getBehavior("entity", this.behaviorKey, "onDeath");
         if (!onDeath.isnil()) {
            try {
               onDeath.invoke(LuaValue.varargsOf(new LuaValue[]{
                  this.entityTable(), LuaValue.valueOf(source.getMsgId())
               }));
            } catch (Exception var4) {
               MLuaLogger.LOGGER.error("MLua entity onDeath callback failed for '{}': {}", this.behaviorKey, var4.getMessage());
            }
         }
      }
      super.die(source);
   }

   public void onAddedToWorld() {
      this.invokeLifecycleSuper("onAddedToWorld");
      if (!this.level().isClientSide && this.behaviorKey != null) {
         LuaValue onSpawn = MLuaBehaviorRegistry.getBehavior("entity", this.behaviorKey, "onSpawn");
         if (!onSpawn.isnil()) {
            try {
               onSpawn.invoke(this.entityTable());
            } catch (Exception var3) {
               MLuaLogger.LOGGER.error("MLua entity onSpawn callback failed for '{}': {}", this.behaviorKey, var3.getMessage());
            }
         }
      }
   }

   protected InteractionResult mobInteract(Player player, InteractionHand hand) {
      if (!this.level().isClientSide && this.behaviorKey != null) {
         LuaValue onInteract = MLuaBehaviorRegistry.getBehavior("entity", this.behaviorKey, "onInteract");
         if (!onInteract.isnil()) {
            try {
               LuaValue playerTable = MLuaEntityWrapper.entityTable(
                  player, MLuaRuntime.registrationForLevel(this.level()));
               LuaValue handled = onInteract.invoke(LuaValue.varargsOf(new LuaValue[]{
                  this.entityTable(), playerTable, LuaValue.valueOf(hand.name())
               })).arg1();
               if (!handled.isboolean() || handled.toboolean()) return InteractionResult.CONSUME;
            } catch (Exception var4) {
               MLuaLogger.LOGGER.error("MLua entity onInteract callback failed for '{}': {}", this.behaviorKey, var4.getMessage());
            }
         }
      }
      return super.mobInteract(player, hand);
   }

   private void syncAnimationPose() {
      try {
         Map<String, MLuaCFrame> pose = MLuaBonePose.getPose(this);
         if (pose == null || pose.isEmpty()) return;
         List<MLuaAnimationSyncPacket.BonePoseEntry> entries = new ArrayList<>();
         for (Map.Entry<String, MLuaCFrame> entry : pose.entrySet()) {
            double[] euler = entry.getValue().toEulerAnglesXYZ();
            entries.add(new MLuaAnimationSyncPacket.BonePoseEntry(entry.getKey(), (float)euler[0], (float)euler[1], (float)euler[2]));
         }
         MLuaNetworkManager.get().sendAnimationSync(new MLuaAnimationSyncPacket(
            this.getId(), this.getUUID(), this.level().dimension().location(), entries), this.level().getServer());
      } catch (Exception e) {
         MLuaLogger.LOGGER.error("Failed to sync animation pose for entity {}: {}", this.getId(), e.getMessage());
      }
   }

   public void onRemovedFromWorld() {
      this.invokeLifecycleSuper("onRemovedFromWorld");
      MLuaEntityWrapper.removeFromCache(this);
      if (!this.level().isClientSide && this.behaviorKey != null) {
         LuaValue onRemove = MLuaBehaviorRegistry.getBehavior("entity", this.behaviorKey, "onRemove");
         if (!onRemove.isnil()) {
            try {
               onRemove.invoke(this.entityTable());
            } catch (Exception var3) {
               MLuaLogger.LOGGER.error("MLua entity onRemove callback failed for '{}': {}", this.behaviorKey, var3.getMessage());
            }
         }
      }
   }

   public LuaTable entityTable() {
      dev.mlua.module.MLuaRegistration registration = MLuaRuntime.registrationForLevel(this.level());
      return MLuaEntityWrapper.entityTable(this, registration);
   }

   public void attachLuaClass(LuaTable table, dev.mlua.module.MLuaRegistration registration) {
      if (table.rawget("__mlua_scripted_entity_bound").toboolean()) return;
      if (registration.classTable("ScriptedEntity") == null) {
         throw new IllegalStateException("ScriptedEntity class must be registered before creating scripted entities");
      }
      registration.attachClassInstance(table, this.className, "ScriptedEntity");
      LuaTable methods = this.entityMethods().get("__index").checktable();
      LuaValue key = LuaValue.NIL;
      while (true) {
         Varargs next = methods.next(key);
         key = next.arg1();
         if (key.isnil()) {
            break;
         }
         if (!key.isstring() || !next.arg(2).isfunction()) continue;
         MLuaClassMethods.setInstanceMethod(table, key.tojstring(), next.arg(2));
      }
      table.rawset("__mlua_scripted_entity_bound", LuaValue.TRUE);
   }

   private LuaTable entityMethods() {
      if (this.methodsMT != null) {
         return this.methodsMT;
      }
      LuaTable methods = new LuaTable();
      methods.set("MoveTo", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            if (MLuaScriptedEntity.this.level().isClientSide) {
               throw new org.luaj.vm2.LuaError("[MLua] ScriptedEntity:MoveTo is server-side only.");
            }
            double[] position = MLuaLuaArguments.finiteVector3(args.arg(2), "ScriptedEntity:MoveTo position");
            double speed = positiveSpeed(args, 3, 1.0, "ScriptedEntity:MoveTo");
            return LuaValue.valueOf(MLuaScriptedEntity.this.navigation.moveTo(
               position[0], position[1], position[2], speed));
         }
      });
      methods.set("LookAt", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            double[] position = MLuaLuaArguments.finiteVector3(args.arg(2), "ScriptedEntity:LookAt position");
            MLuaScriptedEntity.this.getLookControl().setLookAt(position[0], position[1], position[2]);
            return LuaValue.NONE;
         }
      });
      methods.set("AttackTarget", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            if (MLuaScriptedEntity.this.level().isClientSide) {
               throw new org.luaj.vm2.LuaError("[MLua] ScriptedEntity:AttackTarget is server-side only.");
            }
            LuaValue targetVal = args.arg(2);
            if (targetVal.isnil()) {
               MLuaScriptedEntity.this.setTarget((LivingEntity)null);
               return LuaValue.valueOf(true);
            }
            LivingEntity target = MLuaScriptedEntity.this.resolveLivingEntity(targetVal);
            if (target == null) {
               throw new org.luaj.vm2.LuaError("[MLua] AttackTarget expects a live LivingEntity or Player instance, or nil to clear the target.");
            }
            if (target.level() != MLuaScriptedEntity.this.level()) {
               throw new org.luaj.vm2.LuaError("[MLua] AttackTarget expects an entity in the same dimension.");
            }
            MLuaScriptedEntity.this.setTarget(target);
            return LuaValue.valueOf(true);
         }
      });
      methods.set("GetBehaviorKey", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            return LuaValue.valueOf(MLuaScriptedEntity.this.behaviorKey != null
               ? MLuaScriptedEntity.this.behaviorKey : "unknown");
         }
      });
      methods.set("GetAIState", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            return MLuaScriptedEntity.this.aiState == null ? LuaValue.NIL : LuaValue.valueOf(MLuaScriptedEntity.this.aiState);
         }
      });
      methods.set("GetAIStateTicks", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            return LuaValue.valueOf(MLuaScriptedEntity.this.aiStateTicks);
         }
      });
      methods.set("SetAIState", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            LuaValue ai = MLuaBehaviorRegistry.getBehavior("entity", MLuaScriptedEntity.this.behaviorKey, "AI");
            if (!ai.istable()) throw new org.luaj.vm2.LuaError("[MLua] SetAIState requires AI to be a state table.");
            MLuaScriptedEntity.this.transitionAIState(ai.checktable(), args.checkjstring(2));
            MLuaScriptedEntity.this.aiDisabled = false;
            return LuaValue.valueOf(true);
         }
      });
      methods.set("FindNearestPlayer", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            double distance = boundedSearchDistance(MLuaLuaArguments.optionalFiniteNumber(args, 2, 16.0,
               "ScriptedEntity:FindNearestPlayer", "range"));
            boolean requireLineOfSight = MLuaLuaArguments.optionalBoolean(args, 3, false,
               "ScriptedEntity:FindNearestPlayer", "requireLineOfSight");
            LuaTable filter = new LuaTable();
            filter.set("players_only", LuaValue.TRUE);
            filter.set("alive", LuaValue.TRUE);
            Player player = null;
            double nearestDistance = distance * distance;
            for (Entity candidate : MLuaEntityQuery.findWithinRadius(MLuaScriptedEntity.this.level(),
                  MLuaScriptedEntity.this.getX(), MLuaScriptedEntity.this.getY(), MLuaScriptedEntity.this.getZ(),
                  distance, MLuaScriptedEntity.this, filter,
                  MLuaRuntime.registrationForLevel(MLuaScriptedEntity.this.level()))) {
               if (candidate instanceof Player target
                     && (!requireLineOfSight || MLuaScriptedEntity.this.hasLineOfSight(target))) {
                  double candidateDistance = MLuaScriptedEntity.this.distanceToSqr(target);
                  if (candidateDistance > nearestDistance) continue;
                  nearestDistance = candidateDistance;
                  player = target;
               }
            }
            return player == null ? LuaValue.NIL : MLuaScriptedEntity.this.entityToLua(player);
         }
      });
      methods.set("FindNearestEntity", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            LuaValue typeValue = args.arg(2);
            if (!typeValue.isnil() && !typeValue.isstring()) {
               throw new org.luaj.vm2.LuaError("[MLua] ScriptedEntity:FindNearestEntity type must be an entity type id or nil.");
            }
            String typeId = typeValue.isnil() ? null : typeValue.tojstring();
            double distance = boundedSearchDistance(MLuaLuaArguments.optionalFiniteNumber(args, 3, 16.0,
               "ScriptedEntity:FindNearestEntity", "range"));
            LuaTable filter = new LuaTable();
            filter.set("alive", LuaValue.TRUE);
            if (typeId != null && !typeId.isBlank()) filter.set("type", typeId);
            LivingEntity nearest = null;
            double bestDistance = distance * distance;
            for (Entity candidate : MLuaEntityQuery.findWithinRadius(MLuaScriptedEntity.this.level(),
                  MLuaScriptedEntity.this.getX(), MLuaScriptedEntity.this.getY(), MLuaScriptedEntity.this.getZ(),
                  distance, MLuaScriptedEntity.this, filter,
                  MLuaRuntime.registrationForLevel(MLuaScriptedEntity.this.level()))) {
               if (!(candidate instanceof LivingEntity entity)) continue;
               double entityDistance = MLuaScriptedEntity.this.distanceToSqr(entity);
               if (entityDistance < bestDistance) {
                  bestDistance = entityDistance;
                  nearest = entity;
               }
            }
            return nearest == null ? LuaValue.NIL : MLuaScriptedEntity.this.entityToLua(nearest);
         }
      });
      methods.set("FindNearestBlock", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            String blockId = args.arg(2).checkjstring();
            int requestedRadius = MLuaLuaArguments.optionalWholeInt(args, 3, 10,
               "ScriptedEntity:FindNearestBlock", "radius");
            int radius = Math.max(0, Math.min(16, requestedRadius));
            BlockPos origin = MLuaScriptedEntity.this.blockPosition();
            BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
            BlockPos nearest = null;
            double nearestDistance = Double.MAX_VALUE;
            for (int dx = -radius; dx <= radius; dx++) {
               for (int dy = -radius; dy <= radius; dy++) {
                  for (int dz = -radius; dz <= radius; dz++) {
                     mutable.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                     BlockState state = MLuaScriptedEntity.this.level().getBlockState(mutable);
                     String regName = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                     if (regName.equals(blockId)) {
                        double distance = (double)dx * dx + (double)dy * dy + (double)dz * dz;
                        if (distance < nearestDistance) {
                           nearestDistance = distance;
                           nearest = mutable.immutable();
                        }
                     }
                  }
               }
            }
            return nearest == null ? LuaValue.NIL : MLuaBlockWrapper.blockTable(
               MLuaScriptedEntity.this.level(), nearest, MLuaRuntime.registrationForLevel(MLuaScriptedEntity.this.level()));
         }
      });
      methods.set("OpenDoor", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            BlockPos pos = MLuaScriptedEntity.this.blockArg(args, 2);
            BlockState state = MLuaBlockWrapper.doorBaseState(MLuaScriptedEntity.this.level(), pos);
            if (!(state.getBlock() instanceof DoorBlock doorBlock) || !state.hasProperty(DoorBlock.OPEN)) {
               return LuaValue.valueOf(false);
            }
            pos = MLuaBlockWrapper.doorBasePos(MLuaScriptedEntity.this.level(), pos);
            boolean isOpen = state.getValue(DoorBlock.OPEN);
            doorBlock.setOpen(MLuaScriptedEntity.this, MLuaScriptedEntity.this.level(), state, pos, !isOpen);
            return LuaValue.valueOf(true);
         }
      });
      methods.set("CloseDoor", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            BlockPos pos = MLuaScriptedEntity.this.blockArg(args, 2);
            BlockState state = MLuaBlockWrapper.doorBaseState(MLuaScriptedEntity.this.level(), pos);
            if (!(state.getBlock() instanceof DoorBlock doorBlock) || !state.hasProperty(DoorBlock.OPEN)) {
               return LuaValue.valueOf(false);
            }
            pos = MLuaBlockWrapper.doorBasePos(MLuaScriptedEntity.this.level(), pos);
            if (state.getValue(DoorBlock.OPEN)) {
               doorBlock.setOpen(MLuaScriptedEntity.this, MLuaScriptedEntity.this.level(), state, pos, false);
            }
            return LuaValue.valueOf(true);
         }
      });
      methods.set("IsDoorOpen", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            BlockPos pos = MLuaScriptedEntity.this.blockArg(args, 2);
            BlockState state = MLuaBlockWrapper.doorBaseState(MLuaScriptedEntity.this.level(), pos);
            if (!(state.getBlock() instanceof DoorBlock) || !state.hasProperty(DoorBlock.OPEN)) {
               return LuaValue.valueOf(false);
            }
            return LuaValue.valueOf(state.getValue(DoorBlock.OPEN));
         }
      });
      methods.set("SetDoorBreakProgress", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            BlockPos pos = MLuaBlockWrapper.doorBasePos(MLuaScriptedEntity.this.level(), MLuaScriptedEntity.this.blockArg(args, 2));
            BlockState state = MLuaScriptedEntity.this.level().getBlockState(pos);
            int progressIndex = args.arg(2).istable() ? 3 : 5;
            int progress = MLuaLuaArguments.wholeInt(args, progressIndex,
               "ScriptedEntity:SetDoorBreakProgress", "progress");
            if (progress < -1 || progress > 9) {
               throw new org.luaj.vm2.LuaError("[MLua] ScriptedEntity:SetDoorBreakProgress progress must be -1 to clear or 0 through 9.");
            }
            if (!(state.getBlock() instanceof DoorBlock) || !DoorBlock.isWoodenDoor(state)) {
               return LuaValue.valueOf(false);
            }
            MLuaScriptedEntity.this.level().destroyBlockProgress(MLuaScriptedEntity.this.getId(), pos, progress);
            return LuaValue.valueOf(true);
         }
      });
      methods.set("BreakDoor", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            BlockPos pos = MLuaBlockWrapper.doorBasePos(MLuaScriptedEntity.this.level(), MLuaScriptedEntity.this.blockArg(args, 2));
            Level level = MLuaScriptedEntity.this.level();
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof DoorBlock) || !DoorBlock.isWoodenDoor(state)) {
               return LuaValue.valueOf(false);
            }
            BlockPos upperPos = pos.above();
            level.destroyBlockProgress(MLuaScriptedEntity.this.getId(), pos, -1);
            level.removeBlock(pos, false);
            if (level.getBlockState(upperPos).getBlock() == state.getBlock()) {
               level.removeBlock(upperPos, false);
            }
            level.levelEvent(1021, pos, 0);
            level.levelEvent(2001, pos, net.minecraft.world.level.block.Block.getId(state));
            return LuaValue.valueOf(true);
         }
      });
      methods.set("SetCanOpenDoors", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            if (MLuaScriptedEntity.this.getNavigation() instanceof GroundPathNavigation groundNavigation) {
                  boolean enabled = MLuaLuaArguments.optionalBoolean(args, 2, true,
                     "ScriptedEntity:SetCanOpenDoors", "enabled");
               groundNavigation.setCanOpenDoors(enabled);
               return LuaValue.valueOf(true);
            }
            return LuaValue.valueOf(false);
         }
      });
      methods.set("SetCanPassDoors", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            if (MLuaScriptedEntity.this.getNavigation() instanceof GroundPathNavigation groundNavigation) {
                  boolean enabled = MLuaLuaArguments.optionalBoolean(args, 2, true,
                     "ScriptedEntity:SetCanPassDoors", "enabled");
               groundNavigation.setCanPassDoors(enabled);
               return LuaValue.valueOf(true);
            }
            return LuaValue.valueOf(false);
         }
      });
      methods.set("GetData", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            return MLuaScriptedEntity.this.compoundToLua(MLuaScriptedEntity.this.persistentData);
         }
      });
      methods.set("SetData", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            LuaTable data = args.checktable(2);
            MLuaScriptedEntity.this.persistentData = new CompoundTag();
            MLuaScriptedEntity.this.luaToCompound(data, MLuaScriptedEntity.this.persistentData);
            return LuaValue.NONE;
         }
      });
      methods.set("RandomStroll", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            double speed = positiveSpeed(args, 2, 1.0, "ScriptedEntity:RandomStroll");
            Level level = MLuaScriptedEntity.this.level();
            for (int attempt = 0; attempt < 10; attempt++) {
               int dx = MLuaScriptedEntity.this.random.nextInt(10) - 5;
               int dz = MLuaScriptedEntity.this.random.nextInt(10) - 5;
               BlockPos target = MLuaScriptedEntity.this.blockPosition().offset(dx, 0, dz);
               BlockPos below = target.below();
               if (level.getBlockState(below).isSolid() && level.getBlockState(target).isAir() && level.getBlockState(target.above()).isAir()) {
                  return LuaValue.valueOf(MLuaScriptedEntity.this.navigation.moveTo(target.getX(), target.getY(), target.getZ(), speed));
               }
            }
            return LuaValue.valueOf(false);
         }
      });
      methods.set("Panic", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            double speed = positiveSpeed(args, 2, 1.5, "ScriptedEntity:Panic");
            DamageSource lastDamage = MLuaScriptedEntity.this.getLastDamageSource();
            if (lastDamage == null) return LuaValue.valueOf(false);
            LivingEntity attacker = MLuaScriptedEntity.this.getLastHurtByMob();
            if (attacker != null) {
               Vec3 away = MLuaScriptedEntity.this.position().subtract(attacker.position()).normalize();
               BlockPos panicPos = MLuaScriptedEntity.this.blockPosition().offset((int)away.x * 5, 0, (int)away.z * 5);
               return LuaValue.valueOf(MLuaScriptedEntity.this.navigation.moveTo(panicPos.getX(), panicPos.getY(), panicPos.getZ(), speed));
            }
            int x = MLuaScriptedEntity.this.random.nextInt(20) - 10;
            int z = MLuaScriptedEntity.this.random.nextInt(20) - 10;
            BlockPos target = MLuaScriptedEntity.this.blockPosition().offset(x, 0, z);
            return LuaValue.valueOf(MLuaScriptedEntity.this.navigation.moveTo(target.getX(), target.getY(), target.getZ(), speed));
         }
      });
      methods.set("DistanceTo", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            Entity other = MLuaScriptedEntity.this.resolveEntity(args.arg(2));
            if (other == null) {
               throw new org.luaj.vm2.LuaError("[MLua] DistanceTo expects a live Entity or Player instance.");
            }
            return LuaValue.valueOf(Math.sqrt(MLuaScriptedEntity.this.distanceToSqr(other)));
         }
      });
      methods.set("MeleeAttack", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            int cooldownTicks = MLuaLuaArguments.optionalWholeInt(args, 3, 20,
               "ScriptedEntity:MeleeAttack", "cooldownTicks");
            LivingEntity other = MLuaScriptedEntity.this.resolveLivingEntity(args.arg(2));
            if (other == null || !other.isAlive() || MLuaScriptedEntity.this.meleeCooldown > 0) {
               return LuaValue.valueOf(false);
            }
            double combinedReach = MLuaScriptedEntity.this.getBbWidth() * 2.0F + other.getBbWidth();
            double reachSqr = combinedReach * combinedReach;
            if (MLuaScriptedEntity.this.distanceToSqr(other) > reachSqr) {
               return LuaValue.valueOf(false);
            }
            MLuaScriptedEntity.this.swing(MLuaScriptedEntity.this.getUsedItemHand());
            boolean hit = MLuaScriptedEntity.this.doHurtTarget(other);
            if (hit) MLuaScriptedEntity.this.meleeCooldown = Math.max(1, Math.min(200, cooldownTicks));
            return LuaValue.valueOf(hit);
         }
      });
      this.methodsMT = new LuaTable();
      this.methodsMT.set("__index", methods);
      return this.methodsMT;
   }

   public void addAdditionalSaveData(CompoundTag tag) {
      super.addAdditionalSaveData(tag);
      tag.put("MLuaData", this.persistentData);
      if (this.aiState != null) {
         tag.putString("MLuaAIState", this.aiState);
         tag.putInt("MLuaAIStateTicks", this.aiStateTicks);
      }
   }

   public void readAdditionalSaveData(CompoundTag tag) {
      super.readAdditionalSaveData(tag);
      if (tag.contains("MLuaData")) {
         this.persistentData = tag.getCompound("MLuaData");
      }
      if (tag.contains("MLuaAIState")) {
         this.aiState = tag.getString("MLuaAIState");
         this.aiStateTicks = Math.max(0, tag.getInt("MLuaAIStateTicks"));
      }
   }

   private void tickScriptedAI(LuaValue definition) {
      if (definition.isfunction()) {
         definition.invoke(this.entityTable());
         return;
      }
      if (!definition.istable()) throw new org.luaj.vm2.LuaError("[MLua] Entity AI must be a function or a state table.");
      LuaTable config = definition.checktable();
      LuaTable states = config.get("states").checktable();
      if (this.aiState == null || !states.get(this.aiState).istable() && !states.get(this.aiState).isfunction()) {
         this.transitionAIState(config, config.get("initial").checkjstring());
      }
      LuaValue stateValue = states.get(this.aiState);
      LuaValue update = stateValue.isfunction() ? stateValue : stateValue.get("update");
      if (update.isnil()) return;
      LuaTable stateContext = this.aiStateContext();
      LuaValue result = update.invoke(LuaValue.varargsOf(new LuaValue[]{this.entityTable(), stateContext})).arg1();
      if (this.aiState.equals(stateContext.get("State").optjstring(null)) && result.isstring()
            && !result.tojstring().equals(this.aiState)) {
         this.transitionAIState(config, result.tojstring());
      }
   }

   private void transitionAIState(LuaTable config, String nextState) {
      LuaTable states = config.get("states").checktable();
      LuaValue next = states.get(nextState);
      if (!next.istable() && !next.isfunction()) {
         throw new org.luaj.vm2.LuaError("[MLua] Unknown scripted AI state: " + nextState);
      }
      LuaTable entity = this.entityTable();
      if (this.aiState != null) {
         LuaValue previous = states.get(this.aiState);
         LuaValue exit = previous.istable() ? previous.get("onExit") : LuaValue.NIL;
         if (exit.isfunction()) exit.invoke(LuaValue.varargsOf(new LuaValue[]{entity, this.aiStateContext()}));
      }
      this.aiState = nextState;
      this.aiStateTicks = 0;
      LuaValue enter = next.istable() ? next.get("onEnter") : LuaValue.NIL;
      if (enter.isfunction()) enter.invoke(LuaValue.varargsOf(new LuaValue[]{entity, this.aiStateContext()}));
   }

   private LuaTable aiStateContext() {
      LuaTable context = new LuaTable();
      context.set("State", this.aiState == null ? LuaValue.NIL : LuaValue.valueOf(this.aiState));
      context.set("TicksInState", this.aiStateTicks);
      context.set("Target", this.getTarget() == null ? LuaValue.NIL : this.entityToLua(this.getTarget()));
      return context;
   }

   private LuaValue compoundToLua(CompoundTag tag) {
      LuaTable table = new LuaTable();
      for (String key : tag.getAllKeys()) {
         Tag value = tag.get(key);
         table.set(key, this.tagToLua(value));
      }
      return table;
   }

   private LuaValue tagToLua(Tag tag) {
      if (tag instanceof net.minecraft.nbt.NumericTag nt) {
         return LuaValue.valueOf(nt.getAsDouble());
      } else if (tag instanceof net.minecraft.nbt.StringTag st) {
         return LuaValue.valueOf(st.getAsString());
      } else if (tag instanceof CompoundTag ct) {
         return this.compoundToLua(ct);
      } else if (tag instanceof ListTag lt) {
         LuaTable list = new LuaTable();
         for (int i = 0; i < lt.size(); i++) {
            list.set(i + 1, this.tagToLua(lt.get(i)));
         }
         return list;
      } else if (tag instanceof net.minecraft.nbt.ByteTag bt) {
         return LuaValue.valueOf(bt.getAsByte() != 0);
      }
      return LuaValue.NIL;
   }

   private void luaToCompound(LuaTable table, CompoundTag tag) {
      LuaValue key = LuaValue.NIL;
      while (true) {
         Varargs next = table.next(key);
         key = next.arg1();
         if (key.isnil()) break;
         this.luaValueToTag(key.tojstring(), next.arg(2), tag);
      }
   }

   private void luaValueToTag(String key, LuaValue value, CompoundTag tag) {
      if (value.isboolean()) {
         tag.putBoolean(key, value.toboolean());
      } else if (value.isnumber()) {
         double d = value.todouble();
         if (d == Math.floor(d) && !Double.isInfinite(d)) {
            tag.putLong(key, (long)d);
         } else {
            tag.putDouble(key, d);
         }
      } else if (value.isstring()) {
         tag.putString(key, value.tojstring());
      } else if (value.istable()) {
         LuaTable tbl = value.checktable();
         if (tbl.get(1).isnil()) {
            CompoundTag nested = new CompoundTag();
            this.luaToCompound(tbl, nested);
            tag.put(key, nested);
         } else {
            ListTag list = new ListTag();
            for (int i = 1; !tbl.get(i).isnil(); i++) {
               list.add(this.valueToTag(tbl.get(i)));
            }
            tag.put(key, list);
         }
      }
   }

   private Tag valueToTag(LuaValue value) {
      if (value.isboolean()) {
         return net.minecraft.nbt.ByteTag.valueOf(value.toboolean());
      } else if (value.isnumber()) {
         double d = value.todouble();
         if (d == Math.floor(d) && !Double.isInfinite(d)) {
            return net.minecraft.nbt.LongTag.valueOf((long)d);
         }
         return net.minecraft.nbt.DoubleTag.valueOf(d);
      } else if (value.isstring()) {
         return net.minecraft.nbt.StringTag.valueOf(value.tojstring());
      } else if (value.istable()) {
         LuaTable tbl = value.checktable();
         if (tbl.get(1).isnil()) {
            CompoundTag nested = new CompoundTag();
            this.luaToCompound(tbl, nested);
            return nested;
         }
         ListTag list = new ListTag();
         for (int i = 1; !tbl.get(i).isnil(); i++) {
            list.add(this.valueToTag(tbl.get(i)));
         }
         return list;
      }
      return net.minecraft.nbt.ByteTag.valueOf(false);
   }

   public static AttributeSupplier.Builder createAttributes() {
      return Mob.createMobAttributes()
         .add(Attributes.MAX_HEALTH, 20.0)
         .add(Attributes.MOVEMENT_SPEED, 0.25)
         .add(Attributes.ATTACK_DAMAGE, 2.0)
         .add(Attributes.ARMOR, 0.0)
         .add(Attributes.ARMOR_TOUGHNESS, 0.0)
         .add(Attributes.ATTACK_SPEED, 4.0)
         .add(Attributes.KNOCKBACK_RESISTANCE, 0.0)
         .add(Attributes.FOLLOW_RANGE, 16.0);
   }

   private LuaTable entityToLua(LivingEntity entity) {
      return MLuaEntityWrapper.entityTable(entity, MLuaRuntime.registrationForLevel(entity.level()));
   }

   private BlockPos blockArg(Varargs args, int index) {
      LuaValue value = args.arg(index);
      BlockPos fromBlock = MLuaBlockWrapper.blockPosFromLua(value);
      if (fromBlock != null) {
         return fromBlock;
      }
      throw new org.luaj.vm2.LuaError("[MLua] Expected a Block instance or native Vector3 position");
   }

   private LivingEntity resolveLivingEntity(LuaValue value) {
      return this.resolveEntity(value) instanceof LivingEntity entity ? entity : null;
   }

   private Entity resolveEntity(LuaValue value) {
      Entity entity = MLuaEntityWrapper.fromLuaInstance(value, MLuaRuntime.registrationForLevel(this.level()));
      return entity != null && entity.level() == this.level() && !entity.isRemoved() ? entity : null;
   }

   private static double boundedSearchDistance(double distance) {
      if (!Double.isFinite(distance)) return 16.0;
      return Math.max(0.0, Math.min(128.0, distance));
   }

   private static double positiveSpeed(Varargs args, int index, double fallback, String method) {
      double speed = MLuaLuaArguments.optionalFiniteNumber(args, index, fallback, method, "speed");
      if (speed <= 0.0) {
         throw new org.luaj.vm2.LuaError("[MLua] " + method + " speed must be greater than zero.");
      }
      return speed;
   }

   private void invokeLifecycleSuper(String methodName) {
      try {
         java.lang.reflect.Method method = PathfinderMob.class.getMethod(methodName);
         method.invoke(this);
      } catch (NoSuchMethodException ignored) {
      } catch (ReflectiveOperationException exception) {
         MLuaLogger.LOGGER.debug("Failed to invoke {} on scripted entity superclass", methodName, exception);
      }
   }

}
