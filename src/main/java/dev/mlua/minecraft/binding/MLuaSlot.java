package dev.mlua.minecraft.binding;

import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.mc.impl.item.MCItemImpl;
import dev.mlua.mc.impl.item.MCItemStackImpl;
import dev.mlua.mc.impl.nbt.MCCompoundTagImpl;
import dev.mlua.mcapi.item.MCItemStack;
import dev.mlua.module.MLuaRegistration;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class MLuaSlot {
   private static final String SLOT_STATE = "__mlua_slot_state";

   private MLuaSlot() {}

   public static LuaTable slotTable(Supplier<MCItemStack> stackSupplier, IntSupplier rawIndexSupplier,
                                    int index, Consumer<MCItemStack> setter, boolean writable) {
      return slotTable(stackSupplier, rawIndexSupplier, index, setter, writable, MLuaRuntime.get());
   }

   public static LuaTable slotTable(Supplier<MCItemStack> stackSupplier, IntSupplier rawIndexSupplier,
                                    int index, Consumer<MCItemStack> setter, boolean writable,
                                    MLuaRegistration registration) {
      LuaTable table = registration.createClassInstance("Slot");
      table.rawset(SLOT_STATE, LuaValue.userdataOf(new SlotState(stackSupplier, rawIndexSupplier, index,
         setter, writable, registration)));
      table.set("__side", "Shared");
      return table;
   }

   public static void registerClassMethods(MLuaRegistration registration) {
      if (registration.classTable("Slot") == null) {
         throw new IllegalStateException("Slot class must be registered before its instance methods");
      }
      register(registration, "GetItem", "Returns the live item stack currently in this slot, or nil when empty.", args -> {
         SlotState state = state(args.arg1());
         MCItemStack stack = currentStack(state.stackSupplier);
         return stack.isEmpty() ? LuaValue.NIL
            : itemInstance(stack, state.cachedStackIdentity, state.cachedItem, state.registration);
      });
      register(registration, "GetIndex", "Returns this slot's one-based container index.",
         args -> LuaValue.valueOf(state(args.arg1()).index));
      register(registration, "GetRawIndex", "Returns this slot's zero-based Minecraft index.",
         args -> LuaValue.valueOf(state(args.arg1()).rawIndexSupplier.getAsInt()));
      register(registration, "IsEmpty", "Returns whether this slot contains no item.",
         args -> LuaValue.valueOf(currentStack(state(args.arg1()).stackSupplier).isEmpty()));
      register(registration, "GetCount", "Returns the number of items in this slot.",
         args -> LuaValue.valueOf(currentStack(state(args.arg1()).stackSupplier).getCount()));
      Function<Varargs, Varargs> getMaxStackSize = args -> LuaValue.valueOf(
         currentStack(state(args.arg1()).stackSupplier).getMaxStackSize());
      register(registration, "GetMaxStackSize", "Returns the maximum stack size for this slot's item.", getMaxStackSize);
      register(registration, "GetMaxCount", "Compatibility alias for GetMaxStackSize.", getMaxStackSize);
      register(registration, "GetDisplayName", "Returns this slot item's display name, or nil when empty.", args -> {
         MCItemStack stack = currentStack(state(args.arg1()).stackSupplier);
         return stack.isEmpty() ? LuaValue.NIL : LuaValue.valueOf(stack.getDisplayName().getString());
      });
      register(registration, "GetItemId", "Returns this slot item's registered identifier, or nil when empty.", args -> {
         MCItemStack stack = currentStack(state(args.arg1()).stackSupplier);
         if (stack.isEmpty()) return LuaValue.NIL;
         var id = BuiltInRegistries.ITEM.getKey(((MCItemImpl) stack.getItem()).inner());
         return LuaValue.valueOf(id != null ? id.toString() : "unknown");
      });
      register(registration, "GetLore", "Returns this slot item's lore lines.", args -> {
         MCItemStack stack = currentStack(state(args.arg1()).stackSupplier);
         if (stack.isEmpty() || !stack.hasTag()) return new LuaTable();
         var display = stack.getTag().getCompound("display");
         LuaTable lore = new LuaTable();
         if (display.contains("Lore", 9)) {
            var loreList = display.getList("Lore", 8);
            for (int index = 0; index < loreList.size(); index++) {
               lore.set(index + 1, LuaValue.valueOf(loreList.getString(index)));
            }
         }
         return lore;
      });
      register(registration, "GetEnchantments", "Returns a map from enchantment id to level for this slot's item.", args -> {
         MCItemStack stack = currentStack(state(args.arg1()).stackSupplier);
         return stack.isEmpty() ? new LuaTable() : MLuaEnchantmentWrapper.getEnchantments(stack);
      });
      register(registration, "GetEnchantmentLevel",
         "Returns this slot item's level for an enchantment id, or zero when empty or not enchanted with it.", args -> {
            MCItemStack stack = currentStack(state(args.arg1()).stackSupplier);
            return LuaValue.valueOf(stack.isEmpty() ? 0
               : MLuaEnchantmentWrapper.getEnchantmentLevel(stack, args.checkjstring(2)));
         });
      register(registration, "AddEnchantment", "Adds an enchantment to this slot's item when writable.", args -> {
         SlotState state = state(args.arg1());
         ensureWritable(state.writable);
         MCItemStack stack = currentStack(state.stackSupplier);
         if (!stack.isEmpty()) MLuaEnchantmentWrapper.addEnchantment(stack, args.checkjstring(2), args.optint(3, 1));
         return LuaValue.NONE;
      });
      register(registration, "RemoveEnchantment", "Removes an enchantment from this slot's item when writable.", args -> {
         SlotState state = state(args.arg1());
         ensureWritable(state.writable);
         MCItemStack stack = currentStack(state.stackSupplier);
         if (!stack.isEmpty()) MLuaEnchantmentWrapper.removeEnchantment(stack, args.checkjstring(2));
         return LuaValue.NONE;
      });
      register(registration, "GetDamage", "Returns this slot item's current durability damage.",
         args -> LuaValue.valueOf(currentStack(state(args.arg1()).stackSupplier).getDamageValue()));
      register(registration, "GetMaxDamage", "Returns this slot item's maximum durability damage.",
         args -> LuaValue.valueOf(currentStack(state(args.arg1()).stackSupplier).getMaxDamage()));
      register(registration, "HasTag", "Returns whether this slot's item has a data tag.",
         args -> LuaValue.valueOf(currentStack(state(args.arg1()).stackSupplier).hasTag()));
      register(registration, "HasTagKey", "Returns whether this slot's item data has the given key.", args -> {
         MCItemStack stack = currentStack(state(args.arg1()).stackSupplier);
         return LuaValue.valueOf(stack.hasTag() && stack.getTag().contains(args.checkjstring(2)));
      });
      register(registration, "GetTag", "Returns the value for a key in this slot item's data tag.", args -> {
         MCItemStack stack = currentStack(state(args.arg1()).stackSupplier);
         if (stack.isEmpty() || !stack.hasTag()) return LuaValue.NIL;
         return MLuaNbtWrapper.get(((MCCompoundTagImpl) stack.getTag()).inner(), args.checkjstring(2));
      });
      register(registration, "SetItem", "Sets the item in this writable slot.", args -> {
         SlotState state = state(args.arg1());
         ensureWritable(state.writable);
         LuaValue itemValue = args.arg(2);
         state.setter.accept(itemValue.isnil() ? MCItemStack.EMPTY
            : MCItemStackImpl.fromReal(MLuaItemWrapper.toItemStack(itemValue)));
         return LuaValue.NONE;
      });
      register(registration, "Clear", "Removes the item from this writable slot.", args -> {
         SlotState state = state(args.arg1());
         ensureWritable(state.writable);
         state.setter.accept(MCItemStack.EMPTY);
         return LuaValue.NONE;
      });

      MLuaClassMethods.registerInstanceProperty(registration, "Slot", "Item", "GetItem", "SetItem",
         "Item|nil", "shared:Shared", "Gets or sets the item in this writable slot; nil clears it.");
      MLuaClassMethods.registerInstanceProperty(registration, "Slot", "Index", "GetIndex", null,
         "number", "shared:Shared", "Returns this slot's one-based container index.");
      MLuaClassMethods.registerInstanceProperty(registration, "Slot", "RawIndex", "GetRawIndex", null,
         "number", "shared:Shared", "Returns this slot's zero-based Minecraft index.");
      MLuaClassMethods.registerInstanceProperty(registration, "Slot", "Count", "GetCount", null,
         "number", "shared:Shared", "Returns the number of items in this slot.");
      MLuaClassMethods.registerInstanceProperty(registration, "Slot", "MaxStackSize", "GetMaxStackSize", null,
         "number", "shared:Shared", "Returns the maximum stack size for this slot's item.");
      MLuaClassMethods.registerInstanceProperty(registration, "Slot", "DisplayName", "GetDisplayName", null,
         "string|nil", "shared:Shared", "Returns this slot item's display name, or nil when empty.");
      MLuaClassMethods.registerInstanceProperty(registration, "Slot", "ItemId", "GetItemId", null,
         "string|nil", "shared:Shared", "Returns this slot item's registered identifier, or nil when empty.");
      MLuaClassMethods.registerInstanceProperty(registration, "Slot", "Damage", "GetDamage", null,
         "number", "shared:Shared", "Returns this slot item's current durability damage.");
      MLuaClassMethods.registerInstanceProperty(registration, "Slot", "MaxDamage", "GetMaxDamage", null,
         "number", "shared:Shared", "Returns this slot item's maximum durability damage.");
      MLuaClassMethods.registerInstanceProperty(registration, "Slot", "Lore", "GetLore", null,
         "string[]", "shared:Shared", "Returns this slot item's lore lines.");
      MLuaClassMethods.registerInstanceProperty(registration, "Slot", "Enchantments", "GetEnchantments", null,
         "table", "shared:Shared", "Returns a map from enchantment id to level for this slot's item.");
      MLuaClassMethods.registerInstanceProperty(registration, "Slot", "HasDataTag", "HasTag", null,
         "boolean", "shared:Shared", "Whether this slot's item has a data tag.");
      MLuaClassMethods.registerInstanceProperty(registration, "Slot", "Empty", "IsEmpty", null,
         "boolean", "shared:Shared", "Whether this slot has no item.");
   }

   private static void register(MLuaRegistration registration, String name, String description,
                                Function<Varargs, Varargs> implementation) {
      registration.registerClassFunction("Slot", name, new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) { return implementation.apply(args); }
      }, description);
   }

   private static SlotState state(LuaValue instance) {
      if (!instance.istable()) throw new LuaError("[MLua] Expected a live Slot instance.");
      LuaValue value = instance.checktable().rawget(SLOT_STATE);
      Object state = value.isuserdata() ? value.touserdata() : null;
      if (!(state instanceof SlotState slotState)) {
         throw new LuaError("[MLua] Slot is missing its live backing state.");
      }
      return slotState;
   }

   private static LuaTable itemInstance(MCItemStack stack, Object[] cachedStackIdentity,
                                        LuaTable[] cachedItem, MLuaRegistration registration) {
      net.minecraft.world.item.ItemStack rawStack = ((MCItemStackImpl) stack).inner();
      synchronized (cachedStackIdentity) {
         if (cachedStackIdentity[0] != rawStack) {
            cachedItem[0] = MLuaItemWrapper.itemTable(rawStack, registration);
            cachedStackIdentity[0] = rawStack;
         }
         return cachedItem[0];
      }
   }

   private static MCItemStack currentStack(Supplier<MCItemStack> stackSupplier) {
      MCItemStack stack = stackSupplier.get();
      return stack != null ? stack : MCItemStack.EMPTY;
   }

   private static void ensureWritable(boolean writable) {
      if (!writable) throw new LuaError("[MLua] This slot is read-only on the current side.");
   }

   private record SlotState(Supplier<MCItemStack> stackSupplier, IntSupplier rawIndexSupplier, int index,
                            Consumer<MCItemStack> setter, boolean writable, MLuaRegistration registration,
                            Object[] cachedStackIdentity, LuaTable[] cachedItem) {
      private SlotState(Supplier<MCItemStack> stackSupplier, IntSupplier rawIndexSupplier, int index,
                        Consumer<MCItemStack> setter, boolean writable, MLuaRegistration registration) {
         this(stackSupplier, rawIndexSupplier, index, setter, writable, registration, new Object[1], new LuaTable[1]);
      }
   }
}
