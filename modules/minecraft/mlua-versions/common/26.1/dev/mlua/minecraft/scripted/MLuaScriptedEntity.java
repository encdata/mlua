package dev.mlua.minecraft.scripted;

import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.MLuaLogger;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.minecraft.animation.MLuaBonePose;
import dev.mlua.minecraft.binding.MLuaBlockWrapper;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import dev.mlua.minecraft.network.MLuaAnimationSyncPacket;
import dev.mlua.minecraft.network.MLuaNetworkManager;
import dev.mlua.runtime.MLuaGameThreadAccess;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

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
         this.configureDoorPassage(groundNavigation, true);
      }
   }

   public void aiStep() {
      if (!MLuaGameThreadAccess.isClientSide(this.level()) && this.behaviorKey != null && !this.spawnCallbackFired) {
         this.ensureSpawnCallback(MLuaBehaviorRegistry.snapshotBehaviors("entity", this.behaviorKey));
      }
      super.aiStep();
      if (!MLuaGameThreadAccess.isClientSide(this.level())) {
         this.animationSyncCounter++;
         if (this.animationSyncCounter % 2 == 0) {
            syncAnimationPose();
         }
      }
      if (!MLuaGameThreadAccess.isClientSide(this.level()) && this.behaviorKey != null) {
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
      if (!MLuaGameThreadAccess.isClientSide(this.level()) && this.behaviorKey != null) {
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
      if (!MLuaGameThreadAccess.isClientSide(this.level()) && this.behaviorKey != null) {
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
      if (!MLuaGameThreadAccess.isClientSide(this.level()) && this.behaviorKey != null) {
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
      if (previousTarget == target || this.notifyingTargetChange || MLuaGameThreadAccess.isClientSide(this.level())
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
         String dimension = dev.mlua.minecraft.MLuaDimensionId.from(this.level());
         boolean heartbeat = !entries.isEmpty() && this.animationSyncCounter % 10 == 0;
         if (entries.equals(this.lastSyncedAnimationPose) && dimension.equals(this.lastSyncedAnimationDimension)
               && !heartbeat) return;
         if (this.lastSyncedAnimationPose == null && entries.isEmpty()) {
            this.lastSyncedAnimationPose = List.of();
            this.lastSyncedAnimationDimension = dimension;
            return;
         }
         MLuaNetworkManager.get().sendAnimationSync(new MLuaAnimationSyncPacket(
            this.getId(), this.getUUID(), dev.mlua.minecraft.MLuaDimensionId.nativeId(this.level()), entries), this.level().getServer());
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
         if (!MLuaGameThreadAccess.isClientSide(this.level()) && this.behaviorKey != null) {
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
      MLuaScriptedEntityBehaviorApi.bind(methods, this);
      MLuaScriptedEntityNavigationApi.bind(methods, this);
      MLuaScriptedEntityQueryApi.bind(methods, this);
      MLuaScriptedEntityDoorApi.bind(methods, this);
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

   String behaviorKey() {
      return this.behaviorKey;
   }

   String aiState() {
      return this.scriptedAI.state();
   }

   int aiStateTicks() {
      return this.scriptedAI.ticksInState();
   }

   void setScriptedAIState(String stateName) {
      if (this.behaviorKey == null) {
         throw new LuaError("[MLua] SetAIState requires AI to be a state table.");
      }
      MLuaBehaviorRegistry.Snapshot behaviors = MLuaBehaviorRegistry.snapshotBehaviors("entity", this.behaviorKey);
      this.refreshBehaviorDefinition(behaviors);
      LuaValue ai = behaviors.callbacks().getOrDefault("AI", LuaValue.NIL);
      if (!ai.istable()) throw new LuaError("[MLua] SetAIState requires AI to be a state table.");
      this.scriptedAI.setState(ai.checktable(), stateName, ai, this.aiCallbacks(behaviors));
      this.aiDisabled = false;
   }

   protected void addAdditionalSaveData(ValueOutput output) {
      super.addAdditionalSaveData(output);
      output.store("MLuaData", CompoundTag.CODEC, this.entityData.value());
      output.putBoolean("MLuaSpawnCallbackFired", this.spawnCallbackFired);
      if (this.scriptedAI.state() != null) {
         output.putString("MLuaAIState", this.scriptedAI.state());
         output.putInt("MLuaAIStateTicks", this.scriptedAI.ticksInState());
      }
   }

   protected void readAdditionalSaveData(ValueInput input) {
      super.readAdditionalSaveData(input);
      this.entityData.load(input.read("MLuaData", CompoundTag.CODEC).orElseGet(CompoundTag::new));
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

   LuaTable entityToLua(LivingEntity entity) {
      return MLuaEntityWrapper.entityTable(entity, MLuaRuntime.registrationForLevel(entity.level()));
   }

   BlockPos blockArg(Varargs args, int index) {
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

   LivingEntity resolveLivingEntity(LuaValue value) {
      return this.resolveEntity(value) instanceof LivingEntity entity ? entity : null;
   }

   void requireServerSide(String method) {
      if (MLuaGameThreadAccess.isClientSide(this.level())) {
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

   void configureDoorPassage(GroundPathNavigation navigation, boolean enabled) {
      setCanPassDoors(navigation, enabled);
   }

   net.minecraft.util.RandomSource randomSource() {
      return this.random;
   }

   Entity resolveEntity(LuaValue value) {
      if (!value.istable()) return null;
      LuaValue idValue = value.checktable().get("Id");
      if (!idValue.isnumber()) return null;
      return this.level().getEntity(idValue.checkint());
   }

   LuaValue navigateTo(Varargs args, String method) {
      this.requireServerSide(method);
      MLuaScriptedEntityNavigation.Request request = MLuaScriptedEntityNavigation.readRequest(args, method);
      return LuaValue.valueOf(this.navigation.moveTo(
         request.x(), request.y(), request.z(), request.speed()));
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
      } catch (Error fatal) {
         throw fatal;
      } catch (Throwable failure) {
         MLuaLogger.LOGGER.debug("Failed to invoke {} on scripted entity superclass", methodName, failure);
      }
   }

}
