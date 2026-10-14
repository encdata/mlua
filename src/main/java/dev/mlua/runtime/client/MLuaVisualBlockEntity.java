package dev.mlua.runtime.client;

import net.minecraft.core.NonNullList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import dev.mlua.minecraft.scripted.MLuaBehaviorRegistry;
import dev.mlua.minecraft.scripted.MLuaScriptedBlock;
//--version 26.1
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//--endversion

public final class MLuaVisualBlockEntity extends BlockEntity implements Container {
   private static final int MAX_CONTAINER_SLOTS = 54;
   private NonNullList<ItemStack> items = NonNullList.create();
   public MLuaVisualBlockEntity(BlockPos pos, BlockState state) {
      this(resolveType(state), pos, state);
   }

   public MLuaVisualBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
      super((BlockEntityType)java.util.Objects.requireNonNull(type, "blockEntityType"), pos, state);
      Block block = state.getBlock();
      if (block instanceof MLuaScriptedBlock scripted) {
         MLuaBehaviorRegistry.ContainerDefinition definition = MLuaBehaviorRegistry.getBlockContainer(scripted.behaviorKey());
         if (definition != null) this.items = NonNullList.withSize(definition.rows() * 9, ItemStack.EMPTY);
      }
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
      if (!removed.isEmpty()) this.setChanged();
      return removed;
   }

   @Override public ItemStack removeItemNoUpdate(int slot) {
      if (slot < 0 || slot >= this.items.size()) return ItemStack.EMPTY;
      return ContainerHelper.takeItem(this.items, slot);
   }

   @Override public void setItem(int slot, ItemStack stack) {
      if (slot < 0 || slot >= this.items.size()) return;
      if (!stack.isEmpty() && !this.canPlaceItem(slot, stack)) return;
      this.items.set(slot, stack);
      if (stack.getCount() > this.getMaxStackSize()) stack.setCount(this.getMaxStackSize());
      this.setChanged();
   }

   @Override public boolean canPlaceItem(int slot, ItemStack stack) {
      if (slot < 0 || slot >= this.items.size()) return false;
      Block block = this.getBlockState().getBlock();
      if (!(block instanceof MLuaScriptedBlock scripted)) return true;
      MLuaBehaviorRegistry.ContainerDefinition definition = MLuaBehaviorRegistry.getBlockContainer(scripted.behaviorKey());
      return definition == null || definition.allows(stack);
   }

   @Override public void setChanged() { super.setChanged(); }

   @Override public boolean stillValid(Player player) {
      return Container.stillValidBlockEntity(this, player);
   }

   @Override public void clearContent() {
      for (int slot = 0; slot < this.items.size(); slot++) this.items.set(slot, ItemStack.EMPTY);
      this.setChanged();
   }

//--version 1.20.1
   @Override public void load(CompoundTag tag) {
      super.load(tag);
      int size = Math.max(0, Math.min(MAX_CONTAINER_SLOTS, tag.getInt("MLuaContainerSize")));
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
      int size = Math.max(0, Math.min(MAX_CONTAINER_SLOTS, tag.getInt("MLuaContainerSize")));
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
      int size = Math.max(0, Math.min(MAX_CONTAINER_SLOTS, input.getIntOr("MLuaContainerSize", 0)));
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
