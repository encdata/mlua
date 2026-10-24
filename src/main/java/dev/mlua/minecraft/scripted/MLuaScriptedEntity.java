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
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.LuaError;

public class MLuaScriptedEntity extends PathfinderMob {
   public static final int MAX_AI_TRANSITIONS_PER_REQUEST = MLuaScriptedEntityAI.MAX_TRANSITIONS_PER_REQUEST;
   private final String behaviorKey;
   private final String className;
   private LuaTable methodsMT;
   private final MLuaScriptedEntityPersistentData entityData = new MLuaScriptedEntityPersistentData();
   private int animationSyncCounter;
   private List<MLuaAnimationSyncPacket.BonePoseEntry> lastSyncedAnimationPose;
   private String lastSyncedAnimationDimension;
   private String lastAnimationSyncFailure;
   private int meleeCooldown;
   private boolean spawnCallbackFired;
   private boolean notifyingTargetChange;
   private final MLuaScriptedEntityAI scriptedAI = new MLuaScriptedEntityAI();
   private final MLuaScriptCallbackState callbackState = new MLuaScriptCallbackState();
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
            } catch (Exception failure) {
               this.onTickDisabled = true;
               MLuaLogger.LOGGER.error("MLua entity onTick callback failed for '{}'; disabled for this entity until the callback is replaced",
                  this.behaviorKey, failure);
            }
         }
         LuaValue onAI = behaviors.callbacks().getOrDefault("AI", LuaValue.NIL);
         if (onAI != this.activeAI) {
            // Preserve a saved state on the entity's first tick after loading, but
            // restart the state machine when a live AI definition is replaced.
            if (!this.activeAI.isnil()) {
               this.scriptedAI.clearState();
            }
            this.activeAI = onAI;
            this.aiDisabled = false;
            this.scriptedAI.resetTransitionLimit();
            this.clearAIEventFailures(null);
         }
         int aiInterval = MLuaBehaviorRegistry.getEntityAIInterval(this.behaviorKey);
         if (!onAI.isnil() && !this.aiDisabled && this.tickCount % aiInterval == 0) {
            try {
               this.tickScriptedAI(behaviors, onAI);
            } catch (Exception failure) {
               this.aiDisabled = true;
               MLuaLogger.LOGGER.error("MLua entity AI callback failed for '{}'; disabled for this entity until the AI definition changes or SetAIState is called",
                  this.behaviorKey, failure);
            }
         }
      }
   }

   public boolean hurt(DamageSource source, float amount) {
      boolean result = super.hurt(source, amount);
      if (result && !this.level().isClientSide && this.behaviorKey != null) {
         MLuaBehaviorRegistry.Snapshot behaviors = MLuaBehaviorRegistry.snapshotBehaviors("entity", this.behaviorKey);
         LuaValue onHurt = behaviors.callbacks().getOrDefault("onHurt", LuaValue.NIL);
         if (this.shouldInvokeEventCallback(behaviors, "onHurt", onHurt)) {
            try {
               this.invokeBehavior(behaviors, onHurt,
                  this.entityTable(), LuaValue.valueOf((double)amount), LuaValue.valueOf(source.getMsgId())
               );
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
               this.invokeBehavior(behaviors, onDeath,
                  this.entityTable(), LuaValue.valueOf(source.getMsgId())
               );
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
         MLuaBehaviorRegistry.Snapshot behaviors = MLuaBehaviorRegistry.snapshotBehaviors("entity", this.behaviorKey);
         this.ensureSpawnCallback(behaviors);
      }
   }

   private void ensureSpawnCallback(MLuaBehaviorRegistry.Snapshot behaviors) {
      if (this.spawnCallbackFired) return;
      // Some loader and version paths add entities without dispatching the legacy
      // onAddedToWorld hook. The first server AI tick is also guaranteed to run
      // after insertion, so it provides a shared fallback for the spawn callback.
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
                  this.entityTable(), playerTable, LuaValue.valueOf(hand.name())
               ).arg1();
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
         double[] euler = new double[3];
         for (Map.Entry<String, MLuaCFrame> entry : orderedPose.entrySet()) {
            MLuaCFrame transform = entry.getValue();
            transform.toEulerAnglesXYZ(euler);
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
            MLuaLogger.LOGGER.error("Failed to sync animation pose for entity {}", this.getId(), e);
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
                  Entity.RemovalReason reason = this.getRemovalReason();
                  this.invokeBehavior(behaviors, onRemove, this.entityTable(),
                     LuaValue.valueOf(reason == null ? "UNKNOWN" : reason.name()));
               } catch (Exception var3) {
                  this.disableEventCallback("onRemove", var3);
               }
            }
         }
      } finally {
         // onRemove needs a usable wrapper; release that wrapper and all attached
         // per-entity state only after the callback has finished using it. Drop
         // callback references here too so removed entities cannot retain Lua
         // closures through their failure-isolation cache.
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
         if (key.type() != LuaValue.TSTRING || !next.arg(2).isfunction()) continue;
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
               throw new LuaError("[MLua] ScriptedEntity:MoveTo is server-side only.");
            }
            double[] position = MLuaLuaArguments.finiteVector3(args.arg(2), "ScriptedEntity:MoveTo position");
            double speed = positiveSpeed(args, 3, 1.0, "ScriptedEntity:MoveTo");
            return LuaValue.valueOf(MLuaScriptedEntity.this.navigation.moveTo(
               position[0], position[1], position[2], speed));
         }
      });
      methods.set("NavigateTo", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            MLuaScriptedEntity.this.requireServerSide("NavigateTo");
            double[] position = MLuaLuaArguments.finiteVector3(args.arg(2), "ScriptedEntity:NavigateTo position");
            double speed = positiveSpeed(args, 3, 1.0, "ScriptedEntity:NavigateTo");
            return LuaValue.valueOf(MLuaScriptedEntity.this.navigation.moveTo(
               position[0], position[1], position[2], speed));
         }
      });
      methods.set("StopMoving", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
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
               throw new LuaError("[MLua] AttackTarget expects a live LivingEntity or Player instance, or nil to clear the target.");
            }
            if (target.level() != MLuaScriptedEntity.this.level()) {
               throw new LuaError("[MLua] AttackTarget expects an entity in the same dimension.");
            }
            MLuaScriptedEntity.this.setTarget(target);
            return LuaValue.valueOf(true);
         }
      });
      methods.set("GetTarget", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            LivingEntity target = MLuaScriptedEntity.this.getTarget();
            return target == null || target.isRemoved() ? LuaValue.NIL : MLuaScriptedEntity.this.entityToLua(target);
         }
      });
      methods.set("CanSee", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            Entity target = MLuaScriptedEntity.this.resolveEntity(args.arg(2));
            if (target == null) {
               throw new LuaError("[MLua] ScriptedEntity:CanSee expects a live entity in the same dimension.");
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
            MLuaBehaviorRegistry.Snapshot behaviors = MLuaBehaviorRegistry.snapshotBehaviors(
               "entity", MLuaScriptedEntity.this.behaviorKey);
            MLuaScriptedEntity.this.refreshBehaviorDefinition(behaviors);
            LuaValue ai = behaviors.callbacks().getOrDefault("AI", LuaValue.NIL);
            if (!ai.istable()) throw new LuaError("[MLua] SetAIState requires AI to be a state table.");
            LuaValue state = args.arg(2);
            if (state.type() != LuaValue.TSTRING || state.tojstring().isBlank()) {
               throw new LuaError("[MLua] ScriptedEntity:SetAIState expects a non-empty state name string.");
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
            Level level = MLuaScriptedEntity.this.level();
            Player[] nearest = new Player[1];
            double[] nearestDistance = {distance * distance};
            MLuaEntityQuery.forEachWithinRadius(level,
                  MLuaScriptedEntity.this.getX(), MLuaScriptedEntity.this.getY(), MLuaScriptedEntity.this.getZ(),
                  distance, MLuaScriptedEntity.this,
                  MLuaEntityQuery.compileFilter(filter, MLuaRuntime.registrationForLevel(level)), candidate -> {
                     if (candidate instanceof Player target
                           && (!requireLineOfSight || MLuaScriptedEntity.this.hasLineOfSight(target))) {
                        double candidateDistance = MLuaScriptedEntity.this.distanceToSqr(target);
                        if (candidateDistance <= nearestDistance[0]) {
                           nearestDistance[0] = candidateDistance;
                           nearest[0] = target;
                        }
                     }
                  });
            return nearest[0] == null ? LuaValue.NIL : MLuaScriptedEntity.this.entityToLua(nearest[0]);
         }
      });
      methods.set("FindNearestEntity", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            LuaValue typeValue = args.arg(2);
            if (!typeValue.isnil() && typeValue.type() != LuaValue.TSTRING) {
               throw new LuaError("[MLua] ScriptedEntity:FindNearestEntity type must be an entity type id or nil.");
            }
            String typeId = typeValue.isnil() ? null : typeValue.tojstring();
            double distance = boundedSearchDistance(MLuaLuaArguments.optionalFiniteNumber(args, 3, 16.0,
               "ScriptedEntity:FindNearestEntity", "range"));
            LuaTable filter = new LuaTable();
            filter.set("alive", LuaValue.TRUE);
            if (typeId != null && !typeId.isBlank()) filter.set("type", typeId);
            Level level = MLuaScriptedEntity.this.level();
            LivingEntity[] nearest = new LivingEntity[1];
            double[] bestDistance = {distance * distance};
            MLuaEntityQuery.forEachWithinRadius(level,
                  MLuaScriptedEntity.this.getX(), MLuaScriptedEntity.this.getY(), MLuaScriptedEntity.this.getZ(),
                  distance, MLuaScriptedEntity.this,
                  MLuaEntityQuery.compileFilter(filter, MLuaRuntime.registrationForLevel(level)), candidate -> {
                     if (!(candidate instanceof LivingEntity entity)) return;
                     double entityDistance = MLuaScriptedEntity.this.distanceToSqr(entity);
                     if (entityDistance < bestDistance[0]) {
                        bestDistance[0] = entityDistance;
                        nearest[0] = entity;
                     }
                  });
            return nearest[0] == null ? LuaValue.NIL : MLuaScriptedEntity.this.entityToLua(nearest[0]);
         }
      });
      methods.set("FindNearestBlock", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            LuaValue blockValue = args.arg(2);
            if (blockValue.type() != LuaValue.TSTRING || blockValue.tojstring().isBlank()) {
               throw new LuaError("[MLua] ScriptedEntity:FindNearestBlock expects a non-empty block id string.");
            }
            ResourceLocation blockId =
               dev.mlua.minecraft.MLuaResourceLocations.tryParseOrNull(blockValue.tojstring());
            if (blockId == null) {
               throw new LuaError("[MLua] ScriptedEntity:FindNearestBlock received an invalid block id: "
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
                     if (blockId.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()))) {
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
               throw new LuaError("[MLua] ScriptedEntity:SetDoorBreakProgress progress must be -1 to clear or 0 through 9.");
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
            level.levelEvent(2001, pos, Block.getId(state));
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
               groundNavigation.setCanPassDoors(enabled);
               return LuaValue.valueOf(true);
            }
            return LuaValue.valueOf(false);
         }
      });
      methods.set("GetData", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            return MLuaScriptedEntity.this.entityData.toLua();
         }
      });
      methods.set("SetData", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaScriptedEntity.this.requireServerSide("SetData");
            LuaTable data = args.checktable(2);
            MLuaScriptedEntity.this.entityData.replaceFromLua(data);
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
               int dx = (int)Math.round(away.x * 5.0);
               int dz = (int)Math.round(away.z * 5.0);
               BlockPos panicPos = MLuaScriptedEntity.this.blockPosition().offset(dx, 0, dz);
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
               throw new LuaError("[MLua] DistanceTo expects a live Entity or Player instance.");
            }
            return LuaValue.valueOf(Math.sqrt(MLuaScriptedEntity.this.distanceToSqr(other)));
         }
      });
      methods.set("MeleeAttack", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaScriptedEntity.this.requireServerSide("MeleeAttack");
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
      tag.put("MLuaData", this.entityData.value());
      tag.putBoolean("MLuaSpawnCallbackFired", this.spawnCallbackFired);
      if (this.scriptedAI.state() != null) {
         tag.putString("MLuaAIState", this.scriptedAI.state());
         tag.putInt("MLuaAIStateTicks", this.scriptedAI.ticksInState());
      }
   }

   public void readAdditionalSaveData(CompoundTag tag) {
      super.readAdditionalSaveData(tag);
      if (tag.contains("MLuaData")) {
         this.entityData.load(tag.getCompound("MLuaData"));
      }
      this.spawnCallbackFired = tag.getBoolean("MLuaSpawnCallbackFired");
      if (tag.contains("MLuaAIState")) {
         this.scriptedAI.restore(tag.getString("MLuaAIState"), tag.getInt("MLuaAIStateTicks"));
      }
      }
   }

   private void tickScriptedAI(MLuaBehaviorRegistry.Snapshot behaviors, LuaValue definition) {
      this.scriptedAI.tick(definition, this.aiCallbacks(behaviors));
   }

   private void clearAIEventFailures(String state) {
      this.callbackState.clearState(state);
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
      throw new LuaError("[MLua] Expected a Block instance or native Vector3 position");
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
         throw new LuaError("[MLua] " + method + " speed must be greater than zero.");
      }
      return speed;
   }

   private void requireServerSide(String method) {
      if (this.level().isClientSide) {
         throw new LuaError("[MLua] ScriptedEntity:" + method + " is server-side only.");
      }
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

   private Varargs invokeBehavior(MLuaBehaviorRegistry.Snapshot behaviors, LuaValue callback, LuaValue... args) {
      return MLuaBehaviorRegistry.invokeCallback(behaviors, callback, LuaValue.varargsOf(args));
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
            return MLuaScriptedEntity.this.shouldInvokeEventCallback(behaviors, event, callback);
         }
         @Override public void disable(String event, Exception failure) {
            MLuaScriptedEntity.this.disableEventCallback(event, failure);
         }
         @Override public void clearFailures(String state) {
            MLuaScriptedEntity.this.clearAIEventFailures(state);
         }
      };
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

   private boolean shouldInvokeEventCallback(MLuaBehaviorRegistry.Snapshot behaviors,
         String event, LuaValue callback) {
      this.refreshBehaviorDefinition(behaviors);
      return this.callbackState.shouldInvoke(event, callback);
   }

   private void disableEventCallback(String event, Exception failure) {
      this.callbackState.disable(event);
      MLuaLogger.LOGGER.error("MLua entity {} callback failed for '{}'; disabled for this entity until replaced",
         event, this.behaviorKey, failure);
   }

}
