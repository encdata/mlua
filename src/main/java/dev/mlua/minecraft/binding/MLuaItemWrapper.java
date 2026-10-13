package dev.mlua.minecraft.binding;

import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.core.service.MLuaServiceProperties;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.mc.impl.core.MCComponentImpl;
import dev.mlua.mc.impl.item.MCItemImpl;
import dev.mlua.mc.impl.item.MCItemStackImpl;
import dev.mlua.mc.impl.nbt.MCCompoundTagImpl;
import dev.mlua.mc.impl.nbt.MCListTagImpl;
import dev.mlua.mc.impl.nbt.MCStringTagImpl;
import dev.mlua.mcapi.core.MCComponent;
import dev.mlua.mcapi.MCApiRuntime;
import dev.mlua.mcapi.item.MCItem;
import dev.mlua.mcapi.item.MCItemStack;
import dev.mlua.mcapi.nbt.MCCompoundTag;
import dev.mlua.mcapi.nbt.MCListTag;
import java.util.function.BiFunction;
import net.minecraft.core.registries.BuiltInRegistries;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class MLuaItemWrapper {
   private MLuaItemWrapper() {
   }

   public static LuaTable itemTable(net.minecraft.world.item.ItemStack stack) {
      return itemTable(stack, MLuaRuntime.get());
   }

   public static LuaTable itemTable(net.minecraft.world.item.ItemStack stack, MLuaRegistration registration) {
      if (stack == null) stack = net.minecraft.world.item.ItemStack.EMPTY;
      LuaTable fields = new LuaTable();
      fields.rawset("__side", LuaValue.valueOf("Shared"));
      fields.rawset("__item_stack", LuaValue.userdataOf(stack));
      LuaTable table = registration.createClassInstance("Item", fields);
      return MLuaServiceProperties.attach(table);
   }

   public static LuaTable itemGlobal() {
      return itemGlobal(MLuaRuntime.get());
   }

   public static LuaTable itemGlobal(MLuaRegistration registration) {
      LuaTable table = new LuaTable();
      LuaTable itemClass = registration.classTable("Item");
      if (itemClass == null) throw new IllegalStateException("Lua class is not registered: Item");
      table.set("Class", itemClass);
      table.set("Find", new VarArgFunction() {
         @Override
         public Varargs invoke(Varargs args) {
            return find(args.checkjstring(1), args.optint(2, 1), registration);
         }
      });
      LuaTable meta = new LuaTable();
      meta.set("__call", new VarArgFunction() {
         @Override
         public Varargs invoke(Varargs args) {
            return find(args.checkjstring(2), args.optint(3, 1), registration);
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

   public static LuaValue resolveItem(LuaValue val) {
      if (val.istable()) {
         LuaValue idVal = val.get("Id");
         LuaValue countVal = val.get("Count");
         if (!idVal.isnil()) {
            return find(idVal.tojstring(), countVal.isnil() ? 1 : countVal.checkint());
         }
      }
      if (val.isstring()) {
         return find(val.tojstring(), 1);
      }
      return LuaValue.NIL;
   }

   private static LuaValue find(String id, int count) {
      return find(id, count, MLuaRuntime.get());
   }

   private static LuaValue find(String id, int count, MLuaRegistration registration) {
      net.minecraft.world.item.Item item = (net.minecraft.world.item.Item) MCApiRuntime.lookupRegistry("item", id);
      if (item == null) {
         throw new LuaError("[MLua] Unknown item: " + id);
      }
      return itemTable(new net.minecraft.world.item.ItemStack(item, count), registration);
   }

   private static LuaTable createClass(MLuaRegistration registration) {
      LuaTable classTable = registration.registerClass("Item", registration.requireInstanceBaseClassName(), "A live item stack instance with count, durability, display name, tags, and enchantments.");
      if (!classTable.rawget("__initialized").toboolean()) {
         registration.registerClassFunction("Item", "new", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
               int offset = args.arg1() == classTable ? 1 : 0;
               return find(args.checkjstring(offset + 1), args.optint(offset + 2, 1), registration);
            }
         }, "Creates a live item stack instance from a registered item identifier and count.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "GetId", selfItem((self, ignored) -> LuaValue.valueOf(id(stack(self)))), "Returns this item's registered identifier.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "GetName", classTable.rawget("GetId"), "Returns this item's registered identifier.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "GetCount", selfItem((self, ignored) -> LuaValue.valueOf(stack(self).getCount())), "Returns the number of items in this stack.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "SetCount", selfItem((self, args) -> {
            stack(self).setCount(args.checkint(2));
            return LuaValue.NONE;
         }), "Sets the number of items in this stack.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "GetMaxCount", selfItem((self, ignored) -> LuaValue.valueOf(stack(self).getMaxStackSize())), "Returns the maximum stack size for this item.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "GetMaxDamage", selfItem((self, ignored) -> LuaValue.valueOf(stack(self).getMaxDamage())), "Returns the maximum durability damage for this item.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "GetDamage", selfItem((self, ignored) -> LuaValue.valueOf(stack(self).getDamageValue())), "Returns this item's current durability damage.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "SetDamage", selfItem((self, args) -> {
            stack(self).setDamageValue(args.checkint(2));
            return LuaValue.NONE;
         }), "Sets this item's current durability damage.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "GetIsEmpty", selfItem((self, ignored) -> LuaValue.valueOf(stack(self).isEmpty())), "Returns whether this stack contains no items.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "IsEmpty", selfItem((self, ignored) -> LuaValue.valueOf(stack(self).isEmpty())), "Returns whether this stack contains no items.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "GetIsDamageable", selfItem((self, ignored) -> LuaValue.valueOf(stack(self).isDamageableItem())), "Returns whether this item has durability.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "IsDamageable", selfItem((self, ignored) -> LuaValue.valueOf(stack(self).isDamageableItem())), "Returns whether this item has durability.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "GetHasTag", selfItem((self, ignored) -> LuaValue.valueOf(stack(self).hasTag())), "Returns whether this item has NBT data.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "GetDisplayName", selfItem((self, ignored) -> LuaValue.valueOf(MCComponent.toJson(stack(self).getHoverName()))), "Returns this item's display name.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "SetDisplayName", selfItem((self, args) -> {
            String raw = args.checkjstring(2);
            try {
               String displayText;
               try {
                  com.google.gson.JsonElement jsonEl = com.google.gson.JsonParser.parseString(raw);
                  if (jsonEl.isJsonObject() && jsonEl.getAsJsonObject().has("text")) {
                     displayText = jsonEl.getAsJsonObject().get("text").getAsString();
                  } else {
                     displayText = raw;
                  }
               } catch (Exception e) {
                  displayText = raw;
               }
               stack(self).setHoverName(MCComponentImpl.fromReal(net.minecraft.network.chat.Component.literal(displayText)));
            } catch (Exception e) {
               stack(self).setHoverName(MCComponentImpl.literal(raw));
            }
            return LuaValue.NONE;
         }), "Sets this item's display name.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "GetLore", selfItem((self, ignored) -> lore(stack(self))), "Returns this item's lore lines.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "SetLore", selfItem((self, args) -> {
            setLore(stack(self), args.arg(2));
            return LuaValue.NONE;
         }), "Sets this item's lore lines.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "GetTag", selfItem((self, args) -> {
            MCCompoundTag tag = stack(self).getTag();
            return MLuaNbtWrapper.get(tag != null ? ((MCCompoundTagImpl)tag).inner() : null, args.checkjstring(2));
         }), "Returns this item's NBT value by key.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "GetTagKeys", selfItem((self, ignored) -> {
            LuaTable keys = new LuaTable();
            MCCompoundTag tag = stack(self).getTag();
            if (tag == null) {
               return keys;
            }
            int i = 1;
            for (String key : tag.getAllKeys()) {
               keys.set(i++, LuaValue.valueOf(key));
            }
            return keys;
         }), "Returns the keys in this item's NBT data.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "SetTag", selfItem((self, args) -> {
            MLuaNbtWrapper.set(((MCCompoundTagImpl)stack(self).getOrCreateTag()).inner(), args.checkjstring(2), args.arg(3));
            return LuaValue.NONE;
         }), "Sets one value in this item's NBT data.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "GetNbtTable", selfItem((self, ignored) -> MLuaNbtWrapper.table(
            () -> ((MCCompoundTagImpl)stack(self).getOrCreateTag()).inner(),
            tag -> stack(self).setTag(MCCompoundTagImpl.fromReal(tag)), registration
         )), "Returns a live table view of this item's NBT data.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "GetNbt", selfItem((self, args) -> {
            if (args.narg() < 2 || args.arg(2).isnil()) {
               return MLuaNbtWrapper.table(
                  () -> ((MCCompoundTagImpl)stack(self).getOrCreateTag()).inner(),
                  tag -> stack(self).setTag(MCCompoundTagImpl.fromReal(tag)), registration
               );
            }
            MCCompoundTag tag = stack(self).getTag();
            return MLuaNbtWrapper.get(tag != null ? ((MCCompoundTagImpl)tag).inner() : null, args.checkjstring(2));
         }), "Returns this item's NBT data or one value by key.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "getNbt", classTable.rawget("GetNbt"), "Returns this item's NBT data or one value by key.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "SetNbt", selfItem((self, args) -> {
            if (args.narg() == 2 && args.arg(2).istable()) {
               net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
               LuaTable replacement = args.arg(2).checktable();
               LuaValue key = LuaValue.NIL;
               while (true) {
                  Varargs next = replacement.next(key);
                  key = next.arg1();
                  if (key.isnil()) {
                     break;
                  }
                  if (key.isstring()) {
                     MLuaNbtWrapper.set(tag, key.tojstring(), next.arg(2));
                  }
               }
               stack(self).setTag(MCCompoundTagImpl.fromReal(tag));
               return LuaValue.NONE;
            }
            MLuaNbtWrapper.set(((MCCompoundTagImpl)stack(self).getOrCreateTag()).inner(), args.checkjstring(2), args.arg(3));
            return LuaValue.NONE;
         }), "Sets one NBT value or replaces this item's NBT data.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "setNbt", classTable.rawget("SetNbt"), "Sets one NBT value or replaces this item's NBT data.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "HasTagKey", selfItem((self, args) -> LuaValue.valueOf(stack(self).hasTag() && stack(self).getTag().contains(args.checkjstring(2)))), "Returns whether this item's NBT data contains the given key.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "Clone", selfItem((self, ignored) -> {
            MCItemStack copy = stack(self).copy();
            return itemTable(copy != null ? ((MCItemStackImpl)copy).inner() : net.minecraft.world.item.ItemStack.EMPTY);
         }), "Returns a copy of this item stack.");
          MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "GetEnchantments", selfItem((self, ignored) -> {
              return MLuaEnchantmentWrapper.getEnchantments(stack(self));
          }), "Returns this item's enchantments.");
          MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "GetEnchantmentLevel", selfItem((self, args) ->
              LuaValue.valueOf(MLuaEnchantmentWrapper.getEnchantmentLevel(stack(self), args.checkjstring(2)))),
              "Returns this item's level for the given enchantment id, or zero when it is not applied.");
          MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "AddEnchantment", selfItem((self, args) -> {
              MLuaEnchantmentWrapper.addEnchantment(stack(self), args.checkjstring(2), args.optint(3, 1));
             return LuaValue.NONE;
          }), "Adds an enchantment to this item stack.");
          MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "RemoveEnchantment", selfItem((self, args) -> {
              MLuaEnchantmentWrapper.removeEnchantment(stack(self), args.checkjstring(2));
             return LuaValue.NONE;
          }), "Removes an enchantment from this item stack.");
          MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "GetItemId", classTable.rawget("GetId"), "Returns this item's registered identifier.");
          MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "ToString", selfItem((self, ignored) -> LuaValue.valueOf(id(stack(self)) + " x" + stack(self).getCount())), "Returns this item's identifier and stack count.");
         classTable.rawset("__initialized", LuaValue.TRUE);
      }
      return classTable;
   }

   private static String id(MCItemStack stack) {
      MCItem mcItem = stack.getItem();
      if (mcItem == null) return "unknown";
      String id = MCApiRuntime.registryKey("item", ((MCItemImpl)mcItem).inner());
      return id != null ? id : "unknown";
   }

   private static LuaTable lore(MCItemStack stack) {
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

   private static void setLore(MCItemStack stack, LuaValue loreVal) {
      MCCompoundTag display = stack.getOrCreateTagElement("display");
      MCListTag loreList = new MCListTagImpl(new net.minecraft.nbt.ListTag());
      if (loreVal.istable()) {
         LuaTable loreTable = loreVal.checktable();
         for (int i = 1; !loreTable.get(i).isnil(); i++) {
            loreList.add(new MCStringTagImpl(net.minecraft.nbt.StringTag.valueOf(loreTable.get(i).tojstring())));
         }
      } else {
         loreList.add(new MCStringTagImpl(net.minecraft.nbt.StringTag.valueOf(loreVal.tojstring())));
      }
      display.put("Lore", loreList);
   }

   private static MCItemStack stack(LuaValue self) {
      LuaValue value = self.checktable().rawget("__item_stack");
      net.minecraft.world.item.ItemStack raw = value.isuserdata(net.minecraft.world.item.ItemStack.class) ? (net.minecraft.world.item.ItemStack)value.touserdata(net.minecraft.world.item.ItemStack.class) : null;
      if (raw == null) {
         throw new LuaError("[MLua] Item instance lost its stack reference");
      }
      return new MCItemStackImpl(raw);
   }

   public static net.minecraft.world.item.ItemStack toItemStack(LuaValue itemVal) {
      if (itemVal.isnil()) return net.minecraft.world.item.ItemStack.EMPTY;
      LuaTable table = itemVal.checktable();
      LuaValue stackVal = table.rawget("__item_stack");
      if (stackVal.isuserdata(net.minecraft.world.item.ItemStack.class)) {
         return (net.minecraft.world.item.ItemStack)stackVal.touserdata(net.minecraft.world.item.ItemStack.class);
      }
      String id = table.get("Id").optjstring(table.get("id").optjstring(""));
      if (id.isEmpty()) return net.minecraft.world.item.ItemStack.EMPTY;
      int count = (int)table.get("Count").optdouble(table.get("count").optdouble(1.0));
      net.minecraft.world.item.Item item = (net.minecraft.world.item.Item) MCApiRuntime.lookupRegistry("item", id);
      if (item == null) return net.minecraft.world.item.ItemStack.EMPTY;
      return new net.minecraft.world.item.ItemStack(item, count);
   }

   private static LuaValue selfItem(BiFunction<LuaValue, Varargs, LuaValue> body) {
      return new VarArgFunction() {
         @Override
         public Varargs invoke(Varargs args) {
            return body.apply(args.arg1(), args);
         }
      };
   }
}
