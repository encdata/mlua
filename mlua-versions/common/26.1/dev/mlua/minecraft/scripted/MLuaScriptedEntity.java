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
import dev.mlua.modules.players.classes.MLuaEntityWrapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public class MLuaScriptedEntity extends PathfinderMob {
   private static final int MAX_PERSISTENT_DATA_DEPTH = 64;
   private static final int MAX_PERSISTENT_DATA_ENTRIES = 16_384;
   public static final int MAX_AI_TRANSITIONS_PER_REQUEST = MLuaScriptedEntityAI.MAX_TRANSITIONS_PER_REQUEST;
   private final String behaviorKey;
   private final String className;
   private LuaTable methodsMT;
   private CompoundTag persistentData = new CompoundTag();
   private int animationSyncCounter;
   private List<MLuaAnimationSyncPacket.BonePoseEntry> lastSyncedAnimationPose;
   private String lastSyncedAnimationDimension;
   private String lastAnimationSyncFailure;
   private int meleeCooldown;
   private boolean spawnCallbackFired;
   private boolean notifyingTargetChange;
   private final MLuaScriptedEntityAI scriptedAI = new MLuaScriptedEntityAI();
   private LuaValue activeOnTick = LuaValue.NIL;
   private boolean onTickDisabled;
   private LuaValue activeAI = LuaValue.NIL;
   private boolean aiDisabled;
   private final MLuaScriptCallbackState callbackState = new MLuaScriptCallbackState();

   public MLuaScriptedEntity(EntityType<? extends MLuaScriptedEntity> type, Level level, String behaviorKey) {
      this(type, level, behaviorKey, "ScriptedEntity");
   }

   public MLuaScriptedEntity(EntityType<? extends MLuaScriptedEntity> type, Level level, String behaviorKey, String className) {
      super(type, level);
      this.behaviorKey = behaviorKey;
      this.className = className == null || className.isBlank() ? "ScriptedEntity" : className;
      if (this.getNavigation() instanceof GroundPathNavigation groundNavigation) {
         groundNavigation.setCanOpenDoors(true);
         setCanPassDoors(groundNavigation, true);
      }
   }

   public void aiStep() {
      if (!this.level().isClientSide && this.behaviorKey != null && !this.spawnCallbackFired) {
         this.ensureSpawnCallback(MLuaBehaviorRegistry.snapshotBehaviors("entity", this.behaviorKey));
      }
      super.aiStep();
      if (!this.level().isClientSide) {
         this.animationSyncCounter++;
         if (this.animationSyncCounter % 2 == 0) {
            syncAnimationPose();
         }
      }
      if (!this.level().isClientSide && this.behaviorKey != null) {
         MLuaBehaviorRegistry.Snapshot behaviors = MLuaBehaviorRegistry.snapshotBehaviors("entity", this.behaviorKey);
         this.refreshBehaviorDefinition(behaviors);
         this.scriptedAI.tickState();
         if (this.meleeCooldown > 0) this.meleeCooldown--;
         LuaValue onTick = behaviors.callbacks().getOrDefault("onTick", LuaValue.NIL);
         if (onTick != this.activeOnTick) {
            this.activeOnTick = onTick;
            this.onTickDisabled = false;
         }
         if (!onTick.isnil() && !this.onTickDisabled) {
            try {
               this.invokeBehavior(behaviors, onTick, this.entityTable());
            } catch (Exception var3) {
               this.onTickDisabled = true;
               MLuaLogger.LOGGER.error("MLua entity onTick callback failed for '{}'; disabled for this entity until the callback is replaced: {}",
                  this.behaviorKey, var3.getMessage());
            }
         }
         LuaValue onAI = behaviors.callbacks().getOrDefault("AI", LuaValue.NIL);
         if (onAI != this.activeAI) {
            if (!this.activeAI.isnil()) this.scriptedAI.clearState();
            this.activeAI = onAI;
            this.aiDisabled = false;
            this.scriptedAI.resetTransitionLimit();
            this.clearAIEventFailures(null);
         }
         int aiInterval = MLuaBehaviorRegistry.getEntityAIInterval(this.behaviorKey);
         if (!onAI.isnil() && !this.aiDisabled && this.tickCount % aiInterval == 0) {
            try {
               this.tickScriptedAI(behaviors, onAI);
            } catch (Exception var4) {
               this.aiDisabled = true;
               MLuaLogger.LOGGER.error("MLua entity AI callback failed for '{}'; disabled for this entity until the AI definition changes or SetAIState is called: {}",
                  this.behaviorKey, var4.getMessage());
            }
         }
      }
   }

   public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
      boolean result = super.hurtServer(level, source, amount);
      if (result && this.behaviorKey != null) {
         MLuaBehaviorRegistry.Snapshot behaviors = MLuaBehaviorRegistry.snapshotBehaviors("entity", this.behaviorKey);
         LuaValue onHurt = behaviors.callbacks().getOrDefault("onHurt", LuaValue.NIL);
         if (this.shouldInvokeEventCallback(behaviors, "onHurt", onHurt)) {
            try {
               this.invokeBehavior(behaviors, onHurt, this.entityTable(),
                  LuaValue.valueOf((double)amount), LuaValue.valueOf(source.getMsgId()));
            } catch (Exception var6) {
               this.disableEventCallback("onHurt", var6);
            }
         }
      }
      return result;
   }

   public void die(DamageSource source) {
      if (!this.level().isClientSide && this.behaviorKey != null) {
         MLuaBehaviorRegistry.Snapshot behaviors = MLuaBehaviorRegistry.snapshotBehaviors("entity", this.behaviorKey);
         LuaValue onDeath = behaviors.callbacks().getOrDefault("onDeath", LuaValue.NIL);
         if (this.shouldInvokeEventCallback(behaviors, "onDeath", onDeath)) {
            try {
               this.invokeBehavior(behaviors, onDeath, this.entityTable(), LuaValue.valueOf(source.getMsgId()));
            } catch (Exception var4) {
               this.disableEventCallback("onDeath", var4);
            }
         }
      }
      super.die(source);
   }

   public void onAddedToWorld() {
      this.invokeLifecycleSuper("onAddedToWorld");
      if (!this.level().isClientSide && this.behaviorKey != null) {
         this.ensureSpawnCallback(MLuaBehaviorRegistry.snapshotBehaviors("entity", this.behaviorKey));
      }
   }

   private void ensureSpawnCallback(MLuaBehaviorRegistry.Snapshot behaviors) {
      if (this.spawnCallbackFired) return;
      this.spawnCallbackFired = true;
      LuaValue onSpawn = behaviors.callbacks().getOrDefault("onSpawn", LuaValue.NIL);
      if (!this.shouldInvokeEventCallback(behaviors, "onSpawn", onSpawn)) return;
      try {
         this.invokeBehavior(behaviors, onSpawn, this.entityTable());
      } catch (Exception failure) {
         this.disableEventCallback("onSpawn", failure);
      }
   }

   protected InteractionResult mobInteract(Player player, InteractionHand hand) {
      if (!this.level().isClientSide && this.behaviorKey != null) {
         MLuaBehaviorRegistry.Snapshot behaviors = MLuaBehaviorRegistry.snapshotBehaviors("entity", this.behaviorKey);
         LuaValue onInteract = behaviors.callbacks().getOrDefault("onInteract", LuaValue.NIL);
         if (this.shouldInvokeEventCallback(behaviors, "onInteract", onInteract)) {
            try {
               LuaValue playerTable = MLuaEntityWrapper.entityTable(
                  player, MLuaRuntime.registrationForLevel(this.level()));
               LuaValue handled = this.invokeBehavior(behaviors, onInteract,
                  this.entityTable(), playerTable, LuaValue.valueOf(hand.name())).arg1();
               if (!handled.isboolean() || handled.toboolean()) return InteractionResult.CONSUME;
            } catch (Exception var4) {
               this.disableEventCallback("onInteract", var4);
            }
         }
      }
      return super.mobInteract(player, hand);
   }

   @Override
   public void setTarget(LivingEntity target) {
      LivingEntity previousTarget = this.getTarget();
      super.setTarget(target);
      if (previousTarget == target || this.notifyingTargetChange || this.level().isClientSide
            || this.behaviorKey == null) return;
      MLuaBehaviorRegistry.Snapshot behaviors = MLuaBehaviorRegistry.snapshotBehaviors("entity", this.behaviorKey);
      LuaValue onTargetChanged = behaviors.callbacks().getOrDefault("onTargetChanged", LuaValue.NIL);
      if (!this.shouldInvokeEventCallback(behaviors, "onTargetChanged", onTargetChanged)) return;
      this.notifyingTargetChange = true;
      try {
         this.invokeBehavior(behaviors, onTargetChanged, this.entityTable(),
            target == null ? LuaValue.NIL : this.entityToLua(target),
            previousTarget == null ? LuaValue.NIL : this.entityToLua(previousTarget));
      } catch (Exception failure) {
         this.disableEventCallback("onTargetChanged", failure);
      } finally {
         this.notifyingTargetChange = false;
      }
   }

   private void syncAnimationPose() {
      try {
         Map<String, MLuaCFrame> pose = MLuaBonePose.getPose(this);
         Map<String, MLuaCFrame> orderedPose = pose == null ? Map.of() : new java.util.TreeMap<>(pose);
         if (orderedPose.size() > MLuaAnimationSyncPacket.MAX_BONES) {
            throw new IllegalStateException("Entity pose exceeds the animation sync limit of "
               + MLuaAnimationSyncPacket.MAX_BONES + " bones");
         }
         List<MLuaAnimationSyncPacket.BonePoseEntry> entries = new ArrayList<>(orderedPose.size());
         for (Map.Entry<String, MLuaCFrame> entry : orderedPose.entrySet()) {
            double[] euler = entry.getValue().toEulerAnglesXYZ();
            MLuaCFrame transform = entry.getValue();
            entries.add(new MLuaAnimationSyncPacket.BonePoseEntry(entry.getKey(), (float)transform.x(),
               (float)transform.y(), (float)transform.z(), (float)euler[0], (float)euler[1], (float)euler[2]));
         }
         String dimension = this.level().dimension().location().toString();
         boolean heartbeat = !entries.isEmpty() && this.animationSyncCounter % 10 == 0;
         if (entries.equals(this.lastSyncedAnimationPose) && dimension.equals(this.lastSyncedAnimationDimension)
               && !heartbeat) return;
         if (this.lastSyncedAnimationPose == null && entries.isEmpty()) {
            this.lastSyncedAnimationPose = List.of();
            this.lastSyncedAnimationDimension = dimension;
            return;
         }
         MLuaNetworkManager.get().sendAnimationSync(new MLuaAnimationSyncPacket(
            this.getId(), this.getUUID(), this.level().dimension().location(), entries), this.level().getServer());
         this.lastSyncedAnimationPose = List.copyOf(entries);
         this.lastSyncedAnimationDimension = dimension;
         this.lastAnimationSyncFailure = null;
      } catch (Exception e) {
         String failure = e.getClass().getName() + ":" + e.getMessage();
         if (!failure.equals(this.lastAnimationSyncFailure)) {
            MLuaLogger.LOGGER.error("Failed to sync animation pose for entity {}: {}", this.getId(), e.getMessage(), e);
            this.lastAnimationSyncFailure = failure;
         }
      }
   }

   public void onRemovedFromWorld() {
      try {
         this.invokeLifecycleSuper("onRemovedFromWorld");
         if (!this.level().isClientSide && this.behaviorKey != null) {
            MLuaBehaviorRegistry.Snapshot behaviors = MLuaBehaviorRegistry.snapshotBehaviors("entity", this.behaviorKey);
            LuaValue onRemove = behaviors.callbacks().getOrDefault("onRemove", LuaValue.NIL);
            if (this.shouldInvokeEventCallback(behaviors, "onRemove", onRemove)) {
               try {
                  net.minecraft.world.entity.Entity.RemovalReason reason = this.getRemovalReason();
                  this.invokeBehavior(behaviors, onRemove, this.entityTable(),
                     LuaValue.valueOf(reason == null ? "UNKNOWN" : reason.name()));
               } catch (Exception var3) {
                  this.disableEventCallback("onRemove", var3);
               }
            }
         }
      } finally {
         // onRemove needs a usable wrapper; release that wrapper and all attached
         // per-entity state only after the callback has finished using it.
         this.callbackState.clear();
         this.activeOnTick = LuaValue.NIL;
         this.activeAI = LuaValue.NIL;
         MLuaEntityWrapper.removeFromCache(this);
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
            return MLuaScriptedEntity.this.navigateTo(args, "MoveTo");
         }
      });
      methods.set("StopMoving", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaScriptedEntity.this.requireServerSide("StopMoving");
            boolean wasMoving = !MLuaScriptedEntity.this.getNavigation().isDone();
            MLuaScriptedEntity.this.getNavigation().stop();
            return LuaValue.valueOf(wasMoving);
         }
      });
      methods.set("LookAt", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaScriptedEntity.this.requireServerSide("LookAt");
            double[] position = MLuaLuaArguments.finiteVector3(args.arg(2), "ScriptedEntity:LookAt position");
            MLuaScriptedEntity.this.getLookControl().setLookAt(position[0], position[1], position[2]);
            return LuaValue.NONE;
         }
      });
      methods.set("AttackTarget", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaScriptedEntity.this.requireServerSide("AttackTarget");
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
      methods.set("GetTarget", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            LivingEntity target = MLuaScriptedEntity.this.getTarget();
            return target == null || target.isRemoved() ? LuaValue.NIL : MLuaScriptedEntity.this.entityToLua(target);
         }
      });
      methods.set("CanSee", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            Entity target = MLuaScriptedEntity.this.resolveEntity(args.arg(2));
            if (target == null) {
               throw new org.luaj.vm2.LuaError("[MLua] ScriptedEntity:CanSee expects a live entity in the same dimension.");
            }
            return LuaValue.valueOf(MLuaScriptedEntity.this.hasLineOfSight(target));
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
            String state = MLuaScriptedEntity.this.scriptedAI.state();
            return state == null ? LuaValue.NIL : LuaValue.valueOf(state);
         }
      });
      methods.set("GetAIStateTicks", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            return LuaValue.valueOf(MLuaScriptedEntity.this.scriptedAI.ticksInState());
         }
      });
      methods.set("SetAIState", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaScriptedEntity.this.requireServerSide("SetAIState");
            if (MLuaScriptedEntity.this.behaviorKey == null) {
               throw new LuaError("[MLua] SetAIState requires AI to be a state table.");
            }
            MLuaBehaviorRegistry.Snapshot behaviors = MLuaBehaviorRegistry.snapshotBehaviors(
               "entity", MLuaScriptedEntity.this.behaviorKey);
            MLuaScriptedEntity.this.refreshBehaviorDefinition(behaviors);
            LuaValue ai = behaviors.callbacks().getOrDefault("AI", LuaValue.NIL);
            if (!ai.istable()) throw new org.luaj.vm2.LuaError("[MLua] SetAIState requires AI to be a state table.");
            LuaValue state = args.arg(2);
            if (!state.isstring() || state.tojstring().isBlank()) {
               throw new org.luaj.vm2.LuaError("[MLua] ScriptedEntity:SetAIState expects a non-empty state name string.");
            }
            MLuaScriptedEntity.this.scriptedAI.setState(ai.checktable(), state.tojstring(), ai,
               MLuaScriptedEntity.this.aiCallbacks(behaviors));
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
            LuaValue blockValue = args.arg(2);
            if (!blockValue.isstring() || blockValue.tojstring().isBlank()) {
               throw new org.luaj.vm2.LuaError("[MLua] ScriptedEntity:FindNearestBlock expects a non-empty block id string.");
            }
            net.minecraft.resources.ResourceLocation blockId =
               dev.mlua.minecraft.MLuaResourceLocations.tryParseOrNull(blockValue.tojstring());
            if (blockId == null) {
               throw new org.luaj.vm2.LuaError("[MLua] ScriptedEntity:FindNearestBlock received an invalid block id: "
                  + blockValue.tojstring());
            }
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
                     double distance = (double)dx * dx + (double)dy * dy + (double)dz * dz;
                     if (distance > (double)radius * radius || distance >= nearestDistance) continue;
                     mutable.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                     if (!MLuaScriptedEntity.this.level().hasChunkAt(mutable)) continue;
                     BlockState state = MLuaScriptedEntity.this.level().getBlockState(mutable);
                     if (blockId.equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()))) {
                        nearestDistance = distance;
                        nearest = mutable.immutable();
                     }
                  }
               }
            }
            return nearest == null ? LuaValue.NIL : MLuaBlockWrapper.blockTable(
               MLuaScriptedEntity.this.level(), nearest, MLuaRuntime.registrationForLevel(MLuaScriptedEntity.this.level()));
         }
      });
      methods.set("NavigateTo", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            return MLuaScriptedEntity.this.navigateTo(args, "NavigateTo");
         }
      });
      methods.set("OpenDoor", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaScriptedEntity.this.requireServerSide("OpenDoor");
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
            MLuaScriptedEntity.this.requireServerSide("CloseDoor");
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
            MLuaScriptedEntity.this.requireServerSide("SetDoorBreakProgress");
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
            MLuaScriptedEntity.this.requireServerSide("BreakDoor");
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
            MLuaScriptedEntity.this.requireServerSide("SetCanOpenDoors");
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
            MLuaScriptedEntity.this.requireServerSide("SetCanPassDoors");
            if (MLuaScriptedEntity.this.getNavigation() instanceof GroundPathNavigation groundNavigation) {
               boolean enabled = MLuaLuaArguments.optionalBoolean(args, 2, true,
                  "ScriptedEntity:SetCanPassDoors", "enabled");
               MLuaScriptedEntity.this.setCanPassDoors(groundNavigation, enabled);
               return LuaValue.valueOf(true);
            }
            return LuaValue.valueOf(false);
         }
      });
      methods.set("GetData", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            return MLuaScriptedEntity.this.compoundToLua(MLuaScriptedEntity.this.persistentData, new PersistentDataBudget(), 0);
         }
      });
      methods.set("SetData", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaScriptedEntity.this.requireServerSide("SetData");
            LuaTable data = args.checktable(2);
            CompoundTag replacement = new CompoundTag();
            Set<LuaTable> activeTables = Collections.newSetFromMap(new IdentityHashMap<>());
            try {
               MLuaScriptedEntity.this.luaToCompound(data, replacement, activeTables, 0,
                  new PersistentDataBudget());
            } catch (IllegalArgumentException invalidData) {
               throw new org.luaj.vm2.LuaError("[MLua] ScriptedEntity:SetData could not convert this table to NBT: "
                  + invalidData.getMessage());
            }
            MLuaScriptedEntity.this.persistentData = replacement;
            return LuaValue.NONE;
         }
      });
      methods.set("RandomStroll", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaScriptedEntity.this.requireServerSide("RandomStroll");
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
            MLuaScriptedEntity.this.requireServerSide("Panic");
            double speed = positiveSpeed(args, 2, 1.5, "ScriptedEntity:Panic");
            DamageSource lastDamage = MLuaScriptedEntity.this.getLastDamageSource();
            if (lastDamage == null) return LuaValue.valueOf(false);
            LivingEntity attacker = MLuaScriptedEntity.this.getLastHurtByMob();
            if (attacker != null) {
               Vec3 away = MLuaScriptedEntity.this.position().subtract(attacker.position());
               away = new Vec3(away.x, 0.0, away.z);
               if (away.lengthSqr() < 1.0E-6) {
                  double angle = MLuaScriptedEntity.this.random.nextDouble() * Math.PI * 2.0;
                  away = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
               } else {
                  away = away.normalize();
               }
               BlockPos panicPos = MLuaScriptedEntity.this.blockPosition().offset(
                  (int)Math.round(away.x * 5.0), 0, (int)Math.round(away.z * 5.0));
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
            MLuaScriptedEntity.this.requireServerSide("MeleeAttack");
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
            boolean hit = MLuaScriptedEntity.this.doHurtTarget((ServerLevel)MLuaScriptedEntity.this.level(), other);
            if (hit) {
               int cooldown = MLuaLuaArguments.optionalWholeInt(args, 3, 20,
                  "ScriptedEntity:MeleeAttack", "cooldown");
               MLuaScriptedEntity.this.meleeCooldown = Math.max(1, Math.min(200, cooldown));
            }
            return LuaValue.valueOf(hit);
         }
      });
      this.methodsMT = new LuaTable();
      this.methodsMT.set("__index", methods);
      return this.methodsMT;
   }

   protected void addAdditionalSaveData(ValueOutput output) {
      super.addAdditionalSaveData(output);
      output.store("MLuaData", CompoundTag.CODEC, this.persistentData);
      output.putBoolean("MLuaSpawnCallbackFired", this.spawnCallbackFired);
      if (this.scriptedAI.state() != null) {
         output.putString("MLuaAIState", this.scriptedAI.state());
         output.putInt("MLuaAIStateTicks", this.scriptedAI.ticksInState());
      }
   }

   protected void readAdditionalSaveData(ValueInput input) {
      super.readAdditionalSaveData(input);
      this.persistentData = input.read("MLuaData", CompoundTag.CODEC).orElseGet(CompoundTag::new);
      this.spawnCallbackFired = input.getBooleanOr("MLuaSpawnCallbackFired", false);
      this.scriptedAI.restore(input.getString("MLuaAIState").orElse(null), input.getIntOr("MLuaAIStateTicks", 0));
   }

   private void tickScriptedAI(MLuaBehaviorRegistry.Snapshot behaviors, LuaValue definition) {
      this.scriptedAI.tick(definition, this.aiCallbacks(behaviors));
   }

   private void clearAIEventFailures(String state) {
      this.callbackState.clearState(state);
   }

   private MLuaScriptedEntityAI.CallbackHost aiCallbacks(MLuaBehaviorRegistry.Snapshot behaviors) {
      return new MLuaScriptedEntityAI.CallbackHost() {
         @Override public LuaValue entity() { return MLuaScriptedEntity.this.entityTable(); }
         @Override public LuaValue target() {
            LivingEntity target = MLuaScriptedEntity.this.getTarget();
            return target == null ? LuaValue.NIL : MLuaScriptedEntity.this.entityToLua(target);
         }
         @Override public String behaviorKey() { return MLuaScriptedEntity.this.behaviorKey; }
         @Override public Varargs invokeDefinition(LuaValue callback, LuaValue... args) {
            return MLuaScriptedEntity.this.invokeBehavior(behaviors, callback, args);
         }
         @Override public Varargs invokeState(LuaValue definition, LuaValue callback, LuaValue... args) {
            return MLuaScriptedEntity.this.invokeBehavior(behaviors, callback, args);
         }
         @Override public boolean shouldInvoke(String event, LuaValue callback) {
            return MLuaScriptedEntity.this.callbackState.shouldInvoke(event, callback);
         }
         @Override public void disable(String event, Exception failure) {
            MLuaScriptedEntity.this.disableEventCallback(event, failure);
         }
         @Override public void clearFailures(String state) {
            MLuaScriptedEntity.this.clearAIEventFailures(state);
         }
      };
   }

   private Varargs invokeBehavior(MLuaBehaviorRegistry.Snapshot behaviors, LuaValue callback, LuaValue... args) {
      return MLuaBehaviorRegistry.invokeCallback(behaviors, callback, LuaValue.varargsOf(args));
   }

   private boolean shouldInvokeEventCallback(MLuaBehaviorRegistry.Snapshot behaviors,
         String event, LuaValue callback) {
      this.refreshBehaviorDefinition(behaviors);
      return this.callbackState.shouldInvoke(event, callback);
   }

   private void refreshBehaviorDefinition(MLuaBehaviorRegistry.Snapshot behaviors) {
      if (!this.callbackState.refreshDefinition(behaviors.identity())) return;
      this.scriptedAI.clearState();
      this.activeOnTick = LuaValue.NIL;
      this.onTickDisabled = false;
      this.activeAI = LuaValue.NIL;
      this.aiDisabled = false;
      this.scriptedAI.resetTransitionLimit();
   }

   private void disableEventCallback(String event, Exception failure) {
      this.callbackState.disable(event);
      MLuaLogger.LOGGER.error("MLua entity {} callback failed for '{}'; disabled for this entity until replaced",
         event, this.behaviorKey, failure);
   }

   private LuaValue compoundToLua(CompoundTag tag, PersistentDataBudget budget, int depth) {
      if (depth > MAX_PERSISTENT_DATA_DEPTH) {
         throw new org.luaj.vm2.LuaError("[MLua] ScriptedEntity:GetData supports nested data up to "
            + MAX_PERSISTENT_DATA_DEPTH + " levels deep.");
      }
      LuaTable table = new LuaTable();
      for (String key : tag.getAllKeys()) {
         budget.addEntry("GetData");
         Tag value = tag.get(key);
         table.set(key, this.tagToLua(value, budget, depth + 1));
      }
      return table;
   }

   private LuaValue tagToLua(Tag tag, PersistentDataBudget budget, int depth) {
      if (depth > MAX_PERSISTENT_DATA_DEPTH) {
         throw new org.luaj.vm2.LuaError("[MLua] ScriptedEntity:GetData supports nested data up to "
            + MAX_PERSISTENT_DATA_DEPTH + " levels deep.");
      }
      if (tag instanceof ByteTag bt) {
         return LuaValue.valueOf(bt.byteValue() != 0);
      } else if (tag instanceof net.minecraft.nbt.NumericTag nt) {
         return LuaValue.valueOf(nt.getAsDouble());
      } else if (tag instanceof net.minecraft.nbt.StringTag st) {
         return LuaValue.valueOf(st.value());
      } else if (tag instanceof CompoundTag ct) {
         return this.compoundToLua(ct, budget, depth);
      } else if (tag instanceof ListTag lt) {
         LuaTable list = new LuaTable();
         for (int i = 0; i < lt.size(); i++) {
            budget.addEntry("GetData");
            list.set(i + 1, this.tagToLua(lt.get(i), budget, depth + 1));
         }
         return list;
      }
      return LuaValue.NIL;
   }

   private void luaToCompound(LuaTable table, CompoundTag tag, Set<LuaTable> activeTables, int depth,
                              PersistentDataBudget budget) {
      this.enterPersistentDataTable(table, activeTables, depth);
      try {
         LuaValue key = LuaValue.NIL;
         while (true) {
            Varargs next = table.next(key);
            key = next.arg1();
            if (key.isnil()) break;
            if (key.type() != LuaValue.TSTRING) {
               throw new IllegalArgumentException("compound table keys must be strings; got " + key.typename());
            }
            budget.addEntry("SetData");
            Tag converted = this.valueToTag(next.arg(2), activeTables, depth + 1, budget);
            tag.put(key.tojstring(), converted);
         }
      } finally {
         activeTables.remove(table);
      }
   }

   private Tag valueToTag(LuaValue value, Set<LuaTable> activeTables, int depth, PersistentDataBudget budget) {
      if (value.type() == LuaValue.TBOOLEAN) {
         return ByteTag.valueOf(value.toboolean());
      } else if (value.type() == LuaValue.TNUMBER) {
         double d = value.todouble();
         if (!Double.isFinite(d)) {
            throw new org.luaj.vm2.LuaError("[MLua] ScriptedEntity:SetData cannot save NaN or infinite numbers.");
         }
         if (d == Math.rint(d) && d >= Long.MIN_VALUE && d < 0x1.0p63) {
            return LongTag.valueOf((long)d);
         }
         return DoubleTag.valueOf(d);
      } else if (value.type() == LuaValue.TSTRING) {
         return StringTag.valueOf(value.tojstring());
      } else if (value.istable()) {
         LuaTable tbl = value.checktable();
         this.enterPersistentDataTable(tbl, activeTables, depth);
         try {
            int arrayLength = persistentArrayLength(tbl, budget);
            if (arrayLength < 0) {
               CompoundTag nested = new CompoundTag();
               LuaValue key = LuaValue.NIL;
               while (true) {
                  Varargs next = tbl.next(key);
                  key = next.arg1();
                  if (key.isnil()) break;
                  if (key.type() != LuaValue.TSTRING) {
                     throw new IllegalArgumentException("compound table keys must be strings; got " + key.typename());
                  }
                  Tag converted = this.valueToTag(next.arg(2), activeTables, depth + 1, budget);
                  nested.put(key.tojstring(), converted);
               }
               return nested;
            }
            ListTag list = new ListTag();
            for (int i = 1; i <= arrayLength; i++) {
               if (!list.add(this.valueToTag(tbl.get(i), activeTables, depth + 1, budget))) {
                  throw new IllegalArgumentException("Lua array values must use one NBT value type");
               }
            }
            return list;
         } finally {
            activeTables.remove(tbl);
         }
      }
      throw new IllegalArgumentException("unsupported Lua value type " + value.typename()
         + "; only booleans, finite numbers, strings, maps, and dense arrays can be saved");
   }

   /** Returns the dense one-based array length, or -1 for a string-keyed map. */
   private static int persistentArrayLength(LuaTable table, PersistentDataBudget budget) {
      LuaValue key = LuaValue.NIL;
      int count = 0;
      int maximum = 0;
      boolean hasNumberKeys = false;
      boolean hasStringKeys = false;
      while (true) {
         Varargs entry = table.next(key);
         key = entry.arg1();
         if (key.isnil()) break;
         count++;
         budget.addEntry("SetData");
         if (key.type() == LuaValue.TSTRING) {
            hasStringKeys = true;
         } else if (key.type() == LuaValue.TNUMBER) {
            hasNumberKeys = true;
            double index = key.todouble();
            if (!Double.isFinite(index) || index < 1 || index > Integer.MAX_VALUE || index != Math.rint(index)) {
               throw new IllegalArgumentException("array keys must be positive whole numbers");
            }
            maximum = Math.max(maximum, (int)index);
         } else {
            throw new IllegalArgumentException("table keys must be strings or positive array indexes; got " + key.typename());
         }
      }
      if (hasNumberKeys && hasStringKeys) {
         throw new IllegalArgumentException("a table cannot mix map keys and array indexes");
      }
      if (!hasNumberKeys) return -1;
      if (maximum != count) throw new IllegalArgumentException("arrays must use dense indexes starting at one");
      return maximum;
   }

   private static final class PersistentDataBudget {
      private int entries;

      private void addEntry(String method) {
         if (++this.entries > MAX_PERSISTENT_DATA_ENTRIES) {
            throw new org.luaj.vm2.LuaError("[MLua] ScriptedEntity:" + method + " exceeds the limit of "
               + MAX_PERSISTENT_DATA_ENTRIES + " total table entries.");
         }
      }
   }

   private void enterPersistentDataTable(LuaTable table, Set<LuaTable> activeTables, int depth) {
      if (depth > MAX_PERSISTENT_DATA_DEPTH) {
         throw new org.luaj.vm2.LuaError("[MLua] ScriptedEntity:SetData supports nested tables up to "
            + MAX_PERSISTENT_DATA_DEPTH + " levels deep.");
      }
      if (!activeTables.add(table)) {
         throw new org.luaj.vm2.LuaError("[MLua] ScriptedEntity:SetData cannot save cyclic Lua tables.");
      }
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
      if (args.narg() >= index + 2) {
         return new BlockPos(args.arg(index).checkint(), args.arg(index + 1).checkint(), args.arg(index + 2).checkint());
      }
      throw new org.luaj.vm2.LuaError("[MLua] Expected Block instance, Vector3, or x, y, z coordinates");
   }

   private LivingEntity resolveLivingEntity(LuaValue value) {
      return this.resolveEntity(value) instanceof LivingEntity entity ? entity : null;
   }

   private void requireServerSide(String method) {
      if (this.level().isClientSide) {
         throw new LuaError("[MLua] ScriptedEntity:" + method + " is only available on the server.");
      }
   }

   private static void setCanPassDoors(GroundPathNavigation navigation, boolean enabled) {
      try {
         java.lang.reflect.Method setter = navigation.getClass().getMethod("setCanPassDoors", boolean.class);
         setter.invoke(navigation, enabled);
      } catch (NoSuchMethodException unsupportedByMinecraft) {
         // Newer Minecraft navigation controls door traversal through pathfinding flags.
      } catch (ReflectiveOperationException failure) {
         throw new LuaError("[MLua] Could not configure scripted entity door navigation: " + failure.getMessage());
      }
   }

   private Entity resolveEntity(LuaValue value) {
      if (!value.istable()) return null;
      LuaValue idValue = value.checktable().get("Id");
      if (!idValue.isnumber()) return null;
      return this.level().getEntity(idValue.checkint());
   }

   private LuaValue navigateTo(Varargs args, String method) {
      this.requireServerSide(method);
      double[] position;
      int speedIndex;
      if (args.arg(2).isnumber()) {
         position = new double[]{
            finiteCoordinate(args, 2, method, "x"),
            finiteCoordinate(args, 3, method, "y"),
            finiteCoordinate(args, 4, method, "z")
         };
         speedIndex = 5;
      } else {
         position = MLuaLuaArguments.finiteVector3(args.arg(2), "ScriptedEntity:" + method + " position");
         speedIndex = 3;
      }
      double speed = positiveSpeed(args, speedIndex, 1.0, "ScriptedEntity:" + method);
      return LuaValue.valueOf(this.navigation.moveTo(position[0], position[1], position[2], speed));
   }

   private static double positiveSpeed(Varargs args, int index, double fallback, String method) {
      double speed = MLuaLuaArguments.optionalFiniteNumber(args, index, fallback, method, "speed");
      if (speed <= 0.0) throw new org.luaj.vm2.LuaError("[MLua] " + method + " speed must be greater than zero.");
      return speed;
   }

   private static double finiteCoordinate(Varargs args, int index, String method, String coordinate) {
      LuaValue value = args.arg(index);
      if (!value.isnumber() || !Double.isFinite(value.todouble())) {
         throw new org.luaj.vm2.LuaError("[MLua] ScriptedEntity:" + method + " " + coordinate + " must be finite.");
      }
      return value.todouble();
   }

   private static double boundedSearchDistance(double distance) {
      if (!Double.isFinite(distance)) return 16.0;
      return Math.max(0.0, Math.min(128.0, distance));
   }

   private void invokeLifecycleSuper(String methodName) {
      try {
         java.lang.invoke.MethodHandle method = java.lang.invoke.MethodHandles.lookup().findSpecial(
            PathfinderMob.class, methodName, java.lang.invoke.MethodType.methodType(void.class),
            MLuaScriptedEntity.class);
         method.invoke(this);
      } catch (NoSuchMethodException ignored) {
      } catch (IllegalAccessException exception) {
         MLuaLogger.LOGGER.debug("Could not access {} on scripted entity superclass", methodName, exception);
      } catch (VirtualMachineError | ThreadDeath fatal) {
         throw fatal;
      } catch (Throwable failure) {
         MLuaLogger.LOGGER.debug("Failed to invoke {} on scripted entity superclass", methodName, failure);
      }
   }

}
