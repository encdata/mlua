package dev.mlua.runtime.client;

import net.minecraft.core.NonNullList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.resources.ResourceLocation;
import dev.mlua.minecraft.scripted.MLuaBehaviorRegistry;
import dev.mlua.minecraft.scripted.MLuaScriptedBlock;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.MLuaCallbackBudget;
import dev.mlua.core.MLuaLogger;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.minecraft.binding.MLuaBlockWrapper;
import dev.mlua.minecraft.binding.MLuaLevelWrapper;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import java.lang.ref.WeakReference;
//--version 26.1
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//--endversion

public final class MLuaVisualBlockEntity extends BlockEntity implements Container {
   private static final int MAX_CONTAINER_SLOTS = 54;
   private NonNullList<ItemStack> items = NonNullList.create();
   private Level loadCallbackLevel;
   private final String behaviorKey;
   private boolean unloadCallbackFired;
   private boolean dispatchingContainerChange;
   private final LifecycleCallbackState containerChangeCallback = new LifecycleCallbackState();
   private final LifecycleCallbackState containerOpenCallback = new LifecycleCallbackState();
   private final LifecycleCallbackState containerCloseCallback = new LifecycleCallbackState();
   public MLuaVisualBlockEntity(BlockPos pos, BlockState state) {
      this(resolveType(state), pos, state);
   }

   public MLuaVisualBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
      super((BlockEntityType)java.util.Objects.requireNonNull(type, "blockEntityType"), pos, state);
      Block block = state.getBlock();
      ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
      this.behaviorKey = blockId == null ? null : blockId.toString();
      if (block instanceof MLuaScriptedBlock scripted) {
         MLuaBehaviorRegistry.ContainerDefinition definition = MLuaBehaviorRegistry.getBlockContainer(scripted.behaviorKey());
         if (definition != null) this.items = NonNullList.withSize(definition.rows() * 9, ItemStack.EMPTY);
      }
   }

   @Override public void setLevel(Level level) {
      super.setLevel(level);
      if (level == null || level.isClientSide || this.loadCallbackLevel == level) return;
      // Minecraft can clear and reattach a removed block entity in the same Level. A
      // Level-only guard would then suppress the next load callback, and the unload flag
      // would suppress every later unload callback for that instance.
      this.unloadCallbackFired = false;
      this.loadCallbackLevel = level;
      this.invokeLifecycle("onLoad", "OnLoad", level);
   }

   @Override public void setRemoved() {
      if (!this.unloadCallbackFired) {
         this.unloadCallbackFired = true;
         this.invokeLifecycle("onUnload", "OnUnload", this.level);
      }
      // setRemoved is the lifecycle boundary; make a later setLevel call a fresh load,
      // including when Minecraft reuses the instance in the same dimension.
      this.loadCallbackLevel = null;
      this.containerChangeCallback.clear();
      this.containerOpenCallback.clear();
      this.containerCloseCallback.clear();
      super.setRemoved();
   }

   private boolean invokeLifecycle(String callbackName, String methodName, Level level) {
      return this.invokeLifecycle(callbackName, methodName, level, -1);
   }

   private boolean invokeLifecycle(String callbackName, String methodName, Level level, int changedSlot) {
      return this.invokeLifecycle(callbackName, methodName, level, changedSlot, null);
   }

   private boolean invokeLifecycle(String callbackName, String methodName, Level level, int changedSlot,
                                  Entity actor) {
      if (level == null || level.isClientSide || this.behaviorKey == null) return true;
      MLuaBehaviorRegistry.Snapshot owner = MLuaBehaviorRegistry.snapshotBehaviors("block_entity", this.behaviorKey);
      MLuaBehaviorRegistry.ContentClassDefinition classDefinition =
         MLuaBehaviorRegistry.getContentClassDefinition("block_entity", this.behaviorKey);
      return this.invokeLifecycle(owner, classDefinition, callbackName, methodName, level, changedSlot, actor);
   }

   private boolean invokeLifecycle(MLuaBehaviorRegistry.Snapshot owner,
                                  MLuaBehaviorRegistry.ContentClassDefinition classDefinition,
                                  String callbackName, String methodName, Level level, int changedSlot, Entity actor) {
      if (level == null || level.isClientSide || this.behaviorKey == null) return true;
      LuaValue callback = owner.callbacks().getOrDefault(callbackName, LuaValue.NIL);
      String className = classDefinition == null ? "BlockEntity" : classDefinition.className();
      try {
         var registration = MLuaRuntime.registrationForLevel(level);
         if (registration == null) return true;
         LuaTable block = MLuaBlockWrapper.blockTable(level, this.worldPosition, registration);
         LuaTable blockEntity = MLuaBlockWrapper.blockEntityTable(this, level, className, registration);
         LuaTable context = new LuaTable();
         context.set("Block", block);
         context.set("BlockEntity", blockEntity);
         context.set("World", MLuaLevelWrapper.levelTable(level, registration));
         context.set("Position", dev.mlua.math.MLuaVector.vector3(this.worldPosition.getX(), this.worldPosition.getY(), this.worldPosition.getZ()));
         if (changedSlot >= 0) context.set("Slot", LuaValue.valueOf(changedSlot + 1));
         if (actor != null) {
            LuaTable actorTable = dev.mlua.modules.players.classes.MLuaEntityWrapper.entityTable(actor, registration);
            context.set("User", actorTable);
            if (actor instanceof Player) context.set("Player", actorTable);
         }
         LuaValue container = blockEntity.get("Container");
         if (!container.isnil()) context.set("Container", container);
         LuaValue classMethod = findLifecycleMethod(registration, className, methodName);
         LuaValue[] invocationArgs = new LuaValue[]{blockEntity, context};
         if (classMethod.isfunction()) {
            if (MLuaBehaviorRegistry.getContentClassDefinition("block_entity", this.behaviorKey)
                  != classDefinition) return true;
            MLuaContext methodContext = classDefinition == null ? null : classDefinition.context();
            if (methodContext == null) methodContext = owner.context();
            MLuaContext activeContext = methodContext;
            MLuaLuaBindings.withContext(methodContext, () -> {
               return MLuaCallbackBudget.run(activeContext, () -> {
                  classMethod.invoke(LuaValue.varargsOf(invocationArgs));
                  return null;
               });
            });
         } else if (callback.isfunction()) {
            MLuaBehaviorRegistry.invokeCallback(owner, callback, LuaValue.varargsOf(invocationArgs));
         }
         return true;
      } catch (Exception exception) {
         MLuaLogger.LOGGER.error("MLua block entity {} callback failed for '{}'", callbackName, this.behaviorKey, exception);
         return false;
      }
   }

   private void dispatchContainerChanged(int changedSlot) {
      if (this.items == null || this.items.isEmpty() || this.dispatchingContainerChange
            || this.level == null || this.level.isClientSide || this.behaviorKey == null) return;
      Level level = this.level;
      this.dispatchingContainerChange = true;
      try {
         this.dispatchLifecycleCallback("onContainerChanged", "OnContainerChanged", level,
            changedSlot, null, this.containerChangeCallback);
      } finally {
         this.dispatchingContainerChange = false;
      }
   }

   private void dispatchLifecycleCallback(String callbackName, String methodName, Level level,
         int changedSlot, Entity actor, LifecycleCallbackState state) {
      if (level == null || level.isClientSide || this.behaviorKey == null) return;
      MLuaBehaviorRegistry.Snapshot owner = MLuaBehaviorRegistry.snapshotBehaviors("block_entity", this.behaviorKey);
      LuaValue callback = owner.callbacks().getOrDefault(callbackName, LuaValue.NIL);
      MLuaBehaviorRegistry.ContentClassDefinition classDefinition =
         MLuaBehaviorRegistry.getContentClassDefinition("block_entity", this.behaviorKey);
      String className = classDefinition == null ? "BlockEntity" : classDefinition.className();
      var registration = MLuaRuntime.registrationForLevel(level);
      LuaValue classMethod = registration == null ? LuaValue.NIL
         : findLifecycleMethod(registration, className, methodName);
      if (!state.refresh(owner.identity(), classDefinition, callback, classMethod)
            || !callback.isfunction() && !classMethod.isfunction()) return;
      if (!this.invokeLifecycle(owner, classDefinition, callbackName, methodName, level, changedSlot, actor)) state.disable();
   }

   //--version 1.20.1,1.21.1
   @Override public void startOpen(Player player) {
      this.dispatchContainerOpen(player, this.containerOpenCallback, "onOpen", "OnOpen");
   }

   @Override public void stopOpen(Player player) {
      this.dispatchContainerOpen(player, this.containerCloseCallback, "onClose", "OnClose");
   }
   //--endversion

   //--version 26.1
   @Override public void startOpen(ContainerUser user) {
      this.dispatchContainerOpen(user == null ? null : user.getLivingEntity(),
         this.containerOpenCallback, "onOpen", "OnOpen");
   }

   @Override public void stopOpen(ContainerUser user) {
      this.dispatchContainerOpen(user == null ? null : user.getLivingEntity(),
         this.containerCloseCallback, "onClose", "OnClose");
   }
   //--endversion

   private void dispatchContainerOpen(Entity actor, LifecycleCallbackState state,
         String callbackName, String methodName) {
      if (actor == null) return;
      this.dispatchLifecycleCallback(callbackName, methodName, this.level, -1, actor, state);
   }

   private static final class LifecycleCallbackState {
      private WeakReference<Object> behaviorDefinition = new WeakReference<>(null);
      private WeakReference<Object> contentClassDefinition = new WeakReference<>(null);
      private WeakReference<LuaValue> callback = new WeakReference<>(LuaValue.NIL);
      private WeakReference<LuaValue> classMethod = new WeakReference<>(LuaValue.NIL);
      private boolean disabled;

      private boolean refresh(Object behaviorDefinition, Object contentClassDefinition,
                              LuaValue callback, LuaValue classMethod) {
         if (this.behaviorDefinition.get() != behaviorDefinition
               || this.contentClassDefinition.get() != contentClassDefinition
               || this.callback.get() != callback || this.classMethod.get() != classMethod) {
            this.behaviorDefinition = new WeakReference<>(behaviorDefinition);
            this.contentClassDefinition = new WeakReference<>(contentClassDefinition);
            this.callback = new WeakReference<>(callback);
            this.classMethod = new WeakReference<>(classMethod);
            this.disabled = false;
         }
         return !this.disabled;
      }

      private void disable() { this.disabled = true; }

      private void clear() {
         this.behaviorDefinition.clear();
         this.contentClassDefinition.clear();
         this.callback.clear();
         this.classMethod.clear();
         this.behaviorDefinition = new WeakReference<>(null);
         this.contentClassDefinition = new WeakReference<>(null);
         this.callback = new WeakReference<>(LuaValue.NIL);
         this.classMethod = new WeakReference<>(LuaValue.NIL);
         this.disabled = false;
      }
   }

   private static LuaValue findLifecycleMethod(dev.mlua.module.MLuaRegistration registration,
                                                String className, String methodName) {
      String currentClass = className;
      while (currentClass != null && !"BlockEntity".equals(currentClass)) {
         LuaTable type = registration.classTable(currentClass);
         if (type != null) {
            LuaValue method = type.rawget(methodName);
            if (method.isfunction()) return method;
         }
         currentClass = registration.superclassName(currentClass);
      }
      return LuaValue.NIL;
   }

   private static BlockEntityType<?> resolveType(BlockState state) {
      for (BlockEntityType<?> type : BuiltInRegistries.BLOCK_ENTITY_TYPE) {
         if (type.isValid(state)) return type;
      }
      throw new IllegalStateException("No registered MLua block entity type accepts block " + state.getBlock());
   }

   public boolean hasContainer() { return !this.items.isEmpty(); }

   public void configureContainer(int slots) {
      if (slots < 1 || slots > MAX_CONTAINER_SLOTS) {
         throw new IllegalArgumentException("Scripted container size must be from 1 to " + MAX_CONTAINER_SLOTS);
      }
      if (this.items.size() == slots) return;
      for (int index = slots; index < this.items.size(); index++) {
         if (!this.items.get(index).isEmpty()) {
            throw new IllegalStateException("Cannot shrink a scripted container while removed slots contain items");
         }
      }
      NonNullList<ItemStack> resized = NonNullList.withSize(slots, ItemStack.EMPTY);
      for (int index = 0; index < Math.min(slots, this.items.size()); index++) {
         resized.set(index, this.items.get(index));
      }
      this.items = resized;
      this.setChanged();
   }

   private int registeredContainerSlots() {
      Block block = this.getBlockState().getBlock();
      if (!(block instanceof MLuaScriptedBlock scripted)) return 0;
      MLuaBehaviorRegistry.ContainerDefinition definition = MLuaBehaviorRegistry.getBlockContainer(scripted.behaviorKey());
      return definition == null ? 0 : definition.rows() * 9;
   }

   private int resolveLoadedContainerSize(int savedSize) {
      int size = savedSize > 0 ? savedSize : this.registeredContainerSlots();
      return Math.max(0, Math.min(MAX_CONTAINER_SLOTS, size));
   }

   @Override public int getContainerSize() { return this.items.size(); }

   @Override public boolean isEmpty() {
      for (ItemStack item : this.items) if (!item.isEmpty()) return false;
      return true;
   }

   @Override public ItemStack getItem(int slot) {
      return slot >= 0 && slot < this.items.size() ? this.items.get(slot) : ItemStack.EMPTY;
   }

   @Override public ItemStack removeItem(int slot, int amount) {
      if (slot < 0 || slot >= this.items.size()) return ItemStack.EMPTY;
      ItemStack removed = ContainerHelper.removeItem(this.items, slot, amount);
      if (!removed.isEmpty()) this.markContainerContentsChanged(slot);
      return removed;
   }

   @Override public ItemStack removeItemNoUpdate(int slot) {
      if (slot < 0 || slot >= this.items.size()) return ItemStack.EMPTY;
      ItemStack removed = ContainerHelper.takeItem(this.items, slot);
      if (!removed.isEmpty()) this.dispatchContainerChanged(slot);
      return removed;
   }

   @Override public void setItem(int slot, ItemStack stack) {
      if (slot < 0 || slot >= this.items.size()) return;
      if (!stack.isEmpty() && !this.canPlaceItem(slot, stack)) return;
      this.items.set(slot, stack);
      if (stack.getCount() > this.getMaxStackSize()) stack.setCount(this.getMaxStackSize());
      this.markContainerContentsChanged(slot);
   }

   @Override public boolean canPlaceItem(int slot, ItemStack stack) {
      if (slot < 0 || slot >= this.items.size()) return false;
      Block block = this.getBlockState().getBlock();
      if (!(block instanceof MLuaScriptedBlock scripted)) return true;
      MLuaBehaviorRegistry.ContainerDefinition definition = MLuaBehaviorRegistry.getBlockContainer(scripted.behaviorKey());
      return definition == null || definition.allows(slot, stack);
   }

   @Override public void setChanged() { super.setChanged(); }

   private void markContainerContentsChanged(int changedSlot) {
      super.setChanged();
      this.dispatchContainerChanged(changedSlot);
   }

   @Override public boolean stillValid(Player player) {
      return Container.stillValidBlockEntity(this, player);
   }

   @Override public void clearContent() {
      boolean changed = !this.isEmpty();
      for (int slot = 0; slot < this.items.size(); slot++) this.items.set(slot, ItemStack.EMPTY);
      if (changed) this.markContainerContentsChanged(-1);
      else this.setChanged();
   }

//--version 1.20.1
   @Override public void load(CompoundTag tag) {
      super.load(tag);
      int size = this.resolveLoadedContainerSize(tag.getInt("MLuaContainerSize"));
      this.items = NonNullList.withSize(size, ItemStack.EMPTY);
      ContainerHelper.loadAllItems(tag, this.items);
   }

   @Override protected void saveAdditional(CompoundTag tag) {
      super.saveAdditional(tag);
      tag.putInt("MLuaContainerSize", this.items.size());
      ContainerHelper.saveAllItems(tag, this.items);
   }
//--endversion
//--version 1.21.1
   @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
      super.loadAdditional(tag, registries);
      int size = this.resolveLoadedContainerSize(tag.getInt("MLuaContainerSize"));
      this.items = NonNullList.withSize(size, ItemStack.EMPTY);
      ContainerHelper.loadAllItems(tag, this.items, registries);
   }

   @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
      super.saveAdditional(tag, registries);
      tag.putInt("MLuaContainerSize", this.items.size());
      ContainerHelper.saveAllItems(tag, this.items, registries);
   }
//--endversion
//--version 26.1
   @Override protected void loadAdditional(ValueInput input) {
      super.loadAdditional(input);
      int size = this.resolveLoadedContainerSize(input.getIntOr("MLuaContainerSize", 0));
      this.items = NonNullList.withSize(size, ItemStack.EMPTY);
      ContainerHelper.loadAllItems(input, this.items);
   }

   @Override protected void saveAdditional(ValueOutput output) {
      super.saveAdditional(output);
      output.putInt("MLuaContainerSize", this.items.size());
      ContainerHelper.saveAllItems(output, this.items);
   }
//--endversion
}
