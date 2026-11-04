package dev.mlua.minecraft.binding;

import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.minecraft.scripted.MLuaBehaviorRegistry;
import dev.mlua.core.service.MLuaServiceProperties;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.mc.impl.core.MCComponentImpl;
import dev.mlua.mc.impl.item.MCItemImpl;
import dev.mlua.mc.impl.item.MCItemStackImpl;
import dev.mlua.mc.impl.nbt.MCCompoundTagImpl;
import dev.mlua.mc.impl.nbt.MCListTagImpl;
import dev.mlua.mc.impl.nbt.MCStringTagImpl;
import dev.mlua.mcapi.MCApiRuntime;
import dev.mlua.mcapi.item.MCItem;
import dev.mlua.mcapi.item.MCItemStack;
import dev.mlua.mcapi.nbt.MCCompoundTag;
import dev.mlua.mcapi.nbt.MCListTag;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiFunction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class MLuaItemWrapper {
   private static final Map<MLuaRegistration, ItemWrapperCache> ITEM_CACHES = new WeakHashMap<>();

   private MLuaItemWrapper() {
   }

   public static LuaTable itemTable(ItemStack stack) {
      return itemTable(stack, MLuaRuntime.get());
   }

   public static LuaTable itemTable(ItemStack stack, MLuaRegistration registration) {
      if (stack == null || stack == ItemStack.EMPTY) {
         // EMPTY is a shared Minecraft sentinel, but Lua Item instances expose mutators.
         // Give each wrapped empty value its own harmless zero-count backing stack.
         stack = new ItemStack(Items.AIR, 0);
      }
      String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
      String className = MLuaBehaviorRegistry.getContentClass("item", itemId);
      if (className == null && stack.getItem() instanceof BlockItem) {
         className = "BlockItem";
      }
      String resolvedClass = className == null ? "Item" : className;
      ItemWrapperCache cache;
      synchronized (ITEM_CACHES) {
         cache = ITEM_CACHES.computeIfAbsent(registration, ignored -> new ItemWrapperCache());
      }
      return cache.getOrCreate(stack, resolvedClass, registration);
   }

   private static LuaTable createItemTable(String className, MLuaRegistration registration) {
      return registration.attachClassInstance(new LuaTable(), className, null);
   }

   private static final class ItemWrapperCache {
      private final ReferenceQueue<ItemStack> collectedStacks = new ReferenceQueue<>();
      private final Map<StackReference, Map<String, WeakReference<LuaTable>>> entries = new HashMap<>();

      synchronized LuaTable getOrCreate(ItemStack stack, String className,
                                        MLuaRegistration registration) {
         this.removeCollectedStacks();
         StackReference lookup = new StackReference(stack, null);
         Map<String, WeakReference<LuaTable>> variants = this.entries.get(lookup);
         WeakReference<LuaTable> reference = variants == null ? null : variants.get(className);
         LuaTable cached = reference == null ? null : reference.get();
         if (cached != null && className.equals(registration.classNameOf(cached))) {
            if (cached.rawget("__mlua_item_initializing").toboolean()) {
               throw new LuaError("[MLua] Circular item initialization for class '" + className + "'.");
            }
            return cached;
         }
         LuaTable instance = createItemTable(className, registration);
         instance.rawset("__mlua_item_initializing", LuaValue.TRUE);
         if (variants == null) {
            variants = new HashMap<>();
            this.entries.put(new StackReference(stack, this.collectedStacks), variants);
         }
         WeakReference<LuaTable> instanceReference = new WeakReference<>(instance);
         variants.put(className, instanceReference);
         LuaTable fields = new LuaTable();
         fields.rawset("__side", LuaValue.valueOf("Shared"));
         fields.rawset("__item_stack", LuaValue.userdataOf(stack));
         try {
            registration.initializeClassInstance(instance, className, fields);
            MLuaServiceProperties.attach(instance);
            instance.rawset("__mlua_item_initializing", LuaValue.NIL);
            return instance;
         } catch (RuntimeException | Error failure) {
            instance.rawset("__mlua_item_initializing", LuaValue.NIL);
            variants.remove(className, instanceReference);
            if (variants.isEmpty()) this.entries.remove(lookup);
            throw failure;
         }
      }

      private void removeCollectedStacks() {
         StackReference reference;
         while ((reference = (StackReference)this.collectedStacks.poll()) != null) {
            this.entries.remove(reference);
         }
         this.entries.values().forEach(variants -> variants.entrySet().removeIf(entry -> entry.getValue().get() == null));
         this.entries.values().removeIf(Map::isEmpty);
      }
   }

   private static final class StackReference extends WeakReference<ItemStack> {
      private final int identityHash;

      StackReference(ItemStack stack, ReferenceQueue<ItemStack> queue) {
         super(stack, queue);
         this.identityHash = System.identityHashCode(stack);
      }

      @Override
      public int hashCode() {
         return this.identityHash;
      }

      @Override
      public boolean equals(Object other) {
         if (this == other) return true;
         if (!(other instanceof StackReference reference)) return false;
         ItemStack stack = this.get();
         return stack != null && stack == reference.get();
      }
   }

   public static LuaTable itemGlobal() {
      return itemGlobal(MLuaRuntime.get());
   }

   public static LuaTable itemGlobal(MLuaRegistration registration) {
      LuaTable table = new LuaTable();
      LuaTable itemClass = registration.classTable("Item");
      if (itemClass == null) throw new IllegalStateException("Lua class is not registered: Item");
      table.set("Class", itemClass);
      LuaValue findItem = new VarArgFunction() {
         @Override
         public Varargs invoke(Varargs args) {
            int offset = args.arg1() == table ? 1 : 0;
            int count = MLuaLuaArguments.optionalWholeInt(args, offset + 2, 1, "Item:Find", "count");
            return find(args.checkjstring(offset + 1), count, registration);
         }
      };
      table.set("Find", findItem);
      LuaTable meta = new LuaTable();
      meta.set("__call", new VarArgFunction() {
         @Override
         public Varargs invoke(Varargs args) {
            return findItem.invoke(args);
         }
      });
      table.setmetatable(meta);
      return table;
   }

   public static LuaTable classTable() {
      return classTable(MLuaRuntime.get());
   }

   public static LuaTable classTable(MLuaRegistration registration) {
      return createClass(registration);
   }

   public static void attachCatalogs(LuaTable itemGlobal, LuaTable items) {
      itemGlobal.set("Name", items);
      itemGlobal.set("Id", items);
      itemGlobal.set("Catalog", items);
   }

   static LuaValue find(String id, int count, MLuaRegistration registration) {
      if (count < 0) throw new LuaError("[MLua] Item count must be zero or greater.");
      Item item = (Item) MCApiRuntime.lookupRegistry("item", id);
      if (item == null) {
         throw new LuaError("[MLua] Unknown item: " + id);
      }
      return itemTable(new ItemStack(item, count), registration);
   }

   private static LuaTable createClass(MLuaRegistration registration) {
      return MLuaItemStackClass.register(registration);
   }

   static String id(MCItemStack stack) {
      MCItem mcItem = stack.getItem();
      if (mcItem == null) return "unknown";
      var key = BuiltInRegistries.ITEM.getKey(((MCItemImpl)mcItem).inner());
      return key != null ? key.toString() : "unknown";
   }

   static LuaTable lore(MCItemStack stack) {
      LuaTable lore = new LuaTable();
      if (stack.hasTag()) {
         MCCompoundTag tag = stack.getTag();
         if (tag != null && tag.contains("display")) {
            MCCompoundTag display = tag.getCompound("display");
            if (display != null && display.contains("Lore", 9)) {
               MCListTag loreList = display.getList("Lore", 8);
               if (loreList != null) {
                  for (int i = 0; i < loreList.size(); i++) {
                     lore.set(i + 1, LuaValue.valueOf(loreList.getString(i)));
                  }
               }
            }
         }
      }
      return lore;
   }

   static void setLore(MCItemStack stack, LuaValue loreVal) {
      MCCompoundTag display = stack.getOrCreateTagElement("display");
      MCListTag loreList = new MCListTagImpl(new ListTag());
      if (loreVal.istable()) {
         LuaTable loreTable = loreVal.checktable();
         for (int i = 1; !loreTable.get(i).isnil(); i++) {
            loreList.add(new MCStringTagImpl(StringTag.valueOf(loreTable.get(i).tojstring())));
         }
      } else {
         loreList.add(new MCStringTagImpl(StringTag.valueOf(loreVal.tojstring())));
      }
      display.put("Lore", loreList);
   }

   static MCItemStack stack(LuaValue self) {
      LuaValue value = self.checktable().rawget("__item_stack");
      ItemStack raw = value.isuserdata(ItemStack.class) ? (ItemStack)value.touserdata(ItemStack.class) : null;
      if (raw == null) {
         throw new LuaError("[MLua] Item instance lost its stack reference");
      }
      return new MCItemStackImpl(raw);
   }

   static ItemStack rawStack(LuaValue self) {
      return ((MCItemStackImpl)stack(self)).inner();
   }

   public static int remainingDurability(ItemStack raw) {
      MCItemStack item = new MCItemStackImpl(raw);
      return item.isDamageableItem() ? Math.max(0, item.getMaxDamage() - Math.max(0, item.getDamageValue())) : 0;
   }

   public static int damage(ItemStack raw) {
      return new MCItemStackImpl(raw).getDamageValue();
   }

   public static int maxDamage(ItemStack raw) {
      return new MCItemStackImpl(raw).getMaxDamage();
   }

   public static double damagePercent(ItemStack raw) {
      MCItemStack item = new MCItemStackImpl(raw);
      if (!item.isDamageableItem()) return 0.0;
      return Math.min(1.0, Math.max(0.0, (double)Math.max(0, item.getDamageValue()) / item.getMaxDamage()));
   }

   public static void setDamage(ItemStack raw, int damage, String method) {
      if (damage < 0) throw new LuaError("[MLua] " + method + " damage must be zero or greater.");
      MCItemStack item = new MCItemStackImpl(raw);
      if (!item.isDamageableItem()) {
         throw new LuaError("[MLua] " + method + " cannot set durability damage on an item without durability.");
      }
      item.setDamageValue(damage);
   }

   public static void setDamage(LuaValue item, int damage, MLuaRegistration registration, String method) {
      if (registration == null || !registration.isInstanceOf(item, "Item")) {
         throw new LuaError("[MLua] " + method + " expects an Item instance.");
      }
      setDamage(toItemStack(item, registration), damage, method);
   }

   static void setNbtValue(LuaValue self, String key, LuaValue value) {
      CompoundTag tag = itemTag(self, true);
      MLuaNbtWrapper.set(tag, key, value);
      setItemTag(self, tag);
   }

   static CompoundTag itemTag(LuaValue self, boolean create) {
      MCItemStack item = stack(self);
      if (!hasItemType(item)) {
         LuaValue stored = self.checktable().rawget("__mlua_empty_stack_nbt");
         if (stored.isuserdata(CompoundTag.class)) {
            return (CompoundTag)stored.touserdata(CompoundTag.class);
         }
         if (!create) return null;
         CompoundTag tag = new CompoundTag();
         self.checktable().rawset("__mlua_empty_stack_nbt", LuaValue.userdataOf(tag));
         return tag;
      }
      MCCompoundTag tag = create ? item.getOrCreateTag() : item.getTag();
      return tag == null ? null : ((MCCompoundTagImpl)tag).inner();
   }

   static void setItemTag(LuaValue self, CompoundTag tag) {
      MCItemStack item = stack(self);
      if (!hasItemType(item)) {
         self.checktable().rawset("__mlua_empty_stack_nbt", tag == null
            ? LuaValue.NIL : LuaValue.userdataOf(tag));
         return;
      }
      item.setTag(MCCompoundTagImpl.fromReal(tag));
   }

   static int nonNegativeCount(int count, String method) {
      if (count < 0) throw new LuaError("[MLua] Item:" + method + " count must be zero or greater.");
      return count;
   }

   static void setCount(MCItemStack item, int count) {
      nonNegativeCount(count, "SetCount");
      if (count > 0 && !hasItemType(item)) {
         throw new LuaError("[MLua] Item:SetCount cannot add items to an empty stack without an item type.");
      }
      item.setCount(count);
   }

   public static void setCount(LuaValue item, int count, MLuaRegistration registration) {
      if (registration == null || !registration.isInstanceOf(item, "Item")) {
         throw new LuaError("[MLua] ItemStackService:SetCount expects an Item instance.");
      }
      setCount(new MCItemStackImpl(toItemStack(item, registration)), count);
   }

   public static LuaValue getNbt(LuaValue item, java.util.Optional<String> key, MLuaRegistration registration) {
      if (registration == null || !registration.isInstanceOf(item, "Item")) {
         throw new LuaError("[MLua] ItemStackService:GetNbt expects an Item instance.");
      }
      if (key.isEmpty()) {
         return MLuaNbtWrapper.table(() -> itemTag(item, true), tag -> setItemTag(item, tag), registration);
      }
      return MLuaNbtWrapper.get(itemTag(item, false), key.get());
   }

   public static void setNbt(LuaValue item, String key, LuaValue value, MLuaRegistration registration) {
      if (registration == null || !registration.isInstanceOf(item, "Item")) {
         throw new LuaError("[MLua] ItemStackService:SetNbt expects an Item instance.");
      }
      setNbtValue(item, key, value);
   }

   public static void replaceNbt(LuaValue item, LuaValue value, MLuaRegistration registration) {
      if (registration == null || !registration.isInstanceOf(item, "Item")) {
         throw new LuaError("[MLua] ItemStackService:ReplaceNbt expects an Item instance.");
      }
      if (!value.istable()) {
         throw new LuaError("[MLua] ItemStackService:ReplaceNbt expects an Nbt or table value.");
      }
      setItemTag(item, MLuaNbtWrapper.toCompound(value));
   }

   public static LuaValue cloneItem(LuaValue item, MLuaRegistration registration) {
      if (registration == null || !registration.isInstanceOf(item, "Item")) {
         throw new LuaError("[MLua] ItemStackService:Clone expects an Item instance.");
      }
      MCItemStack original = stack(item);
      ItemStack copiedStack = original instanceof MCItemStackImpl wrapped
         ? wrapped.inner().copy() : ItemStack.EMPTY;
      LuaTable cloned = itemTable(copiedStack, registration);
      if (!hasItemType(original)) {
         CompoundTag compoundData = itemTag(item, false);
         if (compoundData != null) setItemTag(cloned, compoundData.copy());
      }
      return cloned;
   }

   static boolean hasItemType(MCItemStack item) {
      return item instanceof MCItemStackImpl wrapped && wrapped.inner().getItem() != Items.AIR;
   }

   public static ItemStack toItemStack(LuaValue itemVal) {
      MLuaRuntime runtime = MLuaRuntime.get();
      if (runtime == null) throw new LuaError("[MLua] Item conversion requires an active MLua registration.");
      return toItemStack(itemVal, runtime);
   }

   public static ItemStack toItemStack(LuaValue itemVal, MLuaRegistration registration) {
      if (itemVal.isnil()) return ItemStack.EMPTY;
      if (registration == null) throw new LuaError("[MLua] Item conversion requires an active MLua registration.");
      if (!registration.isInstanceOf(itemVal, "Item")) {
         throw new LuaError("[MLua] Expected an Item instance created by Item.Find, Item(id, count), Item.Class.new, or an inventory API.");
      }
      LuaTable table = itemVal.checktable();
      LuaValue stackVal = table.rawget("__item_stack");
      if (stackVal.isuserdata(ItemStack.class)) {
         return (ItemStack)stackVal.touserdata(ItemStack.class);
      }
      throw new LuaError("[MLua] Item instance has no native ItemStack backing.");
   }

   static LuaValue selfItem(BiFunction<LuaValue, Varargs, LuaValue> body) {
      return new VarArgFunction() {
         @Override
         public Varargs invoke(Varargs args) {
            return body.apply(args.arg1(), args);
         }
      };
   }
}
