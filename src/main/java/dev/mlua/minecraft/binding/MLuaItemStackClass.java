package dev.mlua.minecraft.binding;

import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.mc.impl.core.MCComponentImpl;
import dev.mlua.mc.impl.item.MCItemImpl;
import dev.mlua.mc.impl.item.MCItemStackImpl;
import dev.mlua.mcapi.item.MCItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Registers the Item stack class and its typed item subclasses. */
final class MLuaItemStackClass {
    private MLuaItemStackClass() { }

   static LuaTable register(MLuaRegistration registration) {
      LuaTable classTable = registration.registerClass("Item", registration.requireInstanceBaseClassName(), "A live item stack instance with count, durability, display name, tags, and enchantments.");
      LuaTable blockItemClass = registration.registerClass("BlockItem", "Item", "An item instance that places its associated block.");
      if (!blockItemClass.rawget("__initialized").toboolean()) {
         MLuaClassMethods.registerClassMethod(registration, "BlockItem", blockItemClass, "GetBlockId",
            MLuaItemWrapper.selfItem((self, ignored) -> {
               Item item = ((MCItemImpl)MLuaItemWrapper.stack(self).getItem()).inner();
               if (!(item instanceof BlockItem blockItem)) {
                  throw new LuaError("[MLua] BlockItem instance does not contain a block item stack.");
               }
               return LuaValue.valueOf(BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()).toString());
            }), "Returns the identifier of the block placed by this item.");
         MLuaClassMethods.registerInstanceProperty(registration, "BlockItem", "BlockId", "GetBlockId", null,
            "string", "shared:Shared", "The identifier of the block placed by this item.");
         blockItemClass.rawset("__initialized", LuaValue.TRUE);
      }
      registration.registerClass("Weapon", "Item", "A scriptable weapon base for melee, ranged, thrown, and other item actions.");
      if (!classTable.rawget("__initialized").toboolean()) {
         registration.registerClassFunction("Item", "new", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
               int offset = args.arg1() == classTable ? 1 : 0;
               int count = MLuaLuaArguments.optionalWholeInt(args, offset + 2, 1, "Item.Class.new", "count");
               return MLuaItemWrapper.find(args.checkjstring(offset + 1), count, registration);
            }
         }, "Creates a live item stack instance from a registered item identifier and count.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "GetId", MLuaItemWrapper.selfItem((self, ignored) -> LuaValue.valueOf(MLuaItemWrapper.id(MLuaItemWrapper.stack(self)))), "Returns this item's registered identifier.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "GetName", classTable.rawget("GetId"), "Returns this item's registered identifier.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "GetCount", MLuaItemWrapper.selfItem((self, ignored) -> LuaValue.valueOf(MLuaItemWrapper.stack(self).getCount())), "Returns the number of items in this stack.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "SetCount", MLuaItemWrapper.selfItem((self, args) -> {
            MLuaItemWrapper.setCount(MLuaItemWrapper.stack(self), MLuaLuaArguments.wholeInt(args, 2, "Item:SetCount", "count"));
            return LuaValue.NONE;
         }), "Sets the stack count to a whole number of zero or greater.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "Grow", MLuaItemWrapper.selfItem((self, args) -> {
            MCItemStack item = MLuaItemWrapper.stack(self);
            int amount = MLuaItemWrapper.nonNegativeCount(MLuaLuaArguments.wholeInt(args, 2, "Item:Grow", "count"), "Grow");
            if (amount == 0) return LuaValue.NONE;
            if (!MLuaItemWrapper.hasItemType(item)) throw new LuaError("[MLua] Item:Grow cannot grow an empty stack without an item type.");
            long count = (long)item.getCount() + amount;
            if (count > Integer.MAX_VALUE) throw new LuaError("[MLua] Item:Grow exceeds the maximum supported stack count.");
            item.setCount((int)count);
            return LuaValue.NONE;
         }), "Adds items to this stack while keeping its item type and data.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "Shrink", MLuaItemWrapper.selfItem((self, args) -> {
            MCItemStack item = MLuaItemWrapper.stack(self);
            int amount = MLuaItemWrapper.nonNegativeCount(MLuaLuaArguments.wholeInt(args, 2, "Item:Shrink", "count"), "Shrink");
            item.setCount(Math.max(0, item.getCount() - amount));
            return LuaValue.NONE;
         }), "Removes items from this stack, reducing its count to zero when necessary.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "Split", MLuaItemWrapper.selfItem((self, args) -> {
            MCItemStack item = MLuaItemWrapper.stack(self);
            int amount = MLuaItemWrapper.nonNegativeCount(MLuaLuaArguments.wholeInt(args, 2, "Item:Split", "count"), "Split");
            int count = Math.min(amount, Math.max(0, item.getCount()));
            if (count == 0 || !MLuaItemWrapper.hasItemType(item)) return MLuaItemWrapper.itemTable(ItemStack.EMPTY, registration);
            ItemStack source = ((MCItemStackImpl)item).inner();
            ItemStack result = source.copy();
            result.setCount(count);
            source.setCount(source.getCount() - count);
            return MLuaItemWrapper.itemTable(result, registration);
         }), "Moves up to the requested number of items into a new stack and removes them from this stack.");
         LuaValue getMaxStackSize = MLuaItemWrapper.selfItem((self, ignored) -> LuaValue.valueOf(MLuaItemWrapper.stack(self).getMaxStackSize()));
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "GetMaxStackSize", getMaxStackSize, "Returns the maximum stack size for this item.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "GetMaxCount", getMaxStackSize, "Compatibility alias for GetMaxStackSize.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "GetMaxDamage", MLuaItemWrapper.selfItem((self, ignored) -> LuaValue.valueOf(MLuaItemWrapper.maxDamage(MLuaItemWrapper.rawStack(self)))), "Returns the maximum durability damage for this item.");
         LuaValue getRemainingDurability = MLuaItemWrapper.selfItem((self, ignored) -> {
            return LuaValue.valueOf(MLuaItemWrapper.remainingDurability(MLuaItemWrapper.rawStack(self)));
         });
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "GetRemainingDurability",
            getRemainingDurability, "Returns remaining durability, or zero for items without durability.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "GetDurabilityRemaining",
            getRemainingDurability, "Alias for GetRemainingDurability; returns zero when this item has no durability.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "GetDamagePercent", MLuaItemWrapper.selfItem((self, ignored) -> {
            return LuaValue.valueOf(MLuaItemWrapper.damagePercent(MLuaItemWrapper.rawStack(self)));
         }), "Returns durability damage as a fraction from zero to one.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "IsEnchantable", MLuaItemWrapper.selfItem((self, ignored) -> LuaValue.valueOf(((MCItemStackImpl)MLuaItemWrapper.stack(self)).inner().isEnchantable())), "Returns whether this stack can receive enchantments.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "IsStackable", MLuaItemWrapper.selfItem((self, ignored) -> LuaValue.valueOf(((MCItemStackImpl)MLuaItemWrapper.stack(self)).inner().isStackable())), "Returns whether this stack can be stacked with another matching stack.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "CanStackWith", MLuaItemWrapper.selfItem((self, args) -> {
            LuaValue other = args.arg(2);
            if (!registration.isInstanceOf(other, "Item")) {
               throw new LuaError("[MLua] Item:CanStackWith expects another Item instance.");
            }
            return LuaValue.valueOf(MLuaItemWrapper.stack(self).canCombine(new MCItemStackImpl(MLuaItemWrapper.toItemStack(other, registration))));
         }), "Returns whether another item stack has the same item and data, ignoring stack counts.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "GetDamage", MLuaItemWrapper.selfItem((self, ignored) -> LuaValue.valueOf(MLuaItemWrapper.damage(MLuaItemWrapper.rawStack(self)))), "Returns this item's current durability damage.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "SetDamage", MLuaItemWrapper.selfItem((self, args) -> {
            int damage = MLuaLuaArguments.wholeInt(args, 2, "Item:SetDamage", "damage");
            MLuaItemWrapper.setDamage(MLuaItemWrapper.rawStack(self), damage, "Item:SetDamage");
            return LuaValue.NONE;
         }), "Sets this item's current durability damage to a whole number of zero or greater. The stack must be damageable.");
         LuaValue isEmpty = MLuaItemWrapper.selfItem((self, ignored) -> LuaValue.valueOf(MLuaItemWrapper.stack(self).isEmpty()));
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "GetIsEmpty", isEmpty,
            "Returns whether this stack contains no items.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "IsEmpty", isEmpty,
            "Alias for GetIsEmpty.");
         LuaValue isDamageable = MLuaItemWrapper.selfItem((self, ignored) -> LuaValue.valueOf(MLuaItemWrapper.stack(self).isDamageableItem()));
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "GetIsDamageable", isDamageable,
            "Returns whether this item has durability.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "IsDamageable", isDamageable,
            "Alias for GetIsDamageable.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "GetHasTag", MLuaItemWrapper.selfItem((self, ignored) -> {
            CompoundTag compoundData = MLuaItemWrapper.itemTag(self, false);
            return LuaValue.valueOf(compoundData != null && !compoundData.getAllKeys().isEmpty());
         }), "Returns whether this item has NBT data.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "GetDisplayName", MLuaItemWrapper.selfItem((self, ignored) -> LuaValue.valueOf(MLuaItemWrapper.stack(self).getHoverName().getString())), "Returns this item's rendered display name as plain text.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "GetDisplayComponent", MLuaItemWrapper.selfItem((self, ignored) ->
            dev.mlua.api.MLuaLuaCoercions.toLuaValue(((MCItemStackImpl)MLuaItemWrapper.stack(self)).inner().getHoverName())),
            "Returns this item's native Minecraft display Component.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "SetDisplayName", MLuaItemWrapper.selfItem((self, args) -> {
            LuaValue value = args.arg(2);
            Component component;
            try {
               if (value.isstring()) component = Component.literal(value.checkjstring());
               else {
                  Object nativeValue = dev.mlua.api.MLuaLuaCoercions.javaValue(value, Component.class);
                  if (!(nativeValue instanceof Component text)) {
                     throw new LuaError("[MLua] Item:SetDisplayName expects a string or Java Component value.");
                  }
                  component = text;
               }
            } catch (Exception e) {
               throw new LuaError("[MLua] Item:SetDisplayName expects a string or Java Component value.");
            }
            MLuaItemWrapper.stack(self).setHoverName(MCComponentImpl.fromReal(component));
            return LuaValue.NONE;
         }), "Sets this item's display name from a string or Java Component.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "GetLore", MLuaItemWrapper.selfItem((self, ignored) -> MLuaItemWrapper.lore(MLuaItemWrapper.stack(self))), "Returns this item's lore lines.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "SetLore", MLuaItemWrapper.selfItem((self, args) -> {
            MLuaItemWrapper.setLore(MLuaItemWrapper.stack(self), args.arg(2));
            return LuaValue.NONE;
         }), "Sets this item's lore lines.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "GetTag", MLuaItemWrapper.selfItem((self, args) -> {
            return MLuaNbtWrapper.get(MLuaItemWrapper.itemTag(self, false), args.checkjstring(2));
         }), "Returns this item's NBT value by key.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "GetTagKeys", MLuaItemWrapper.selfItem((self, ignored) -> {
            LuaTable keys = new LuaTable();
            CompoundTag compoundData = MLuaItemWrapper.itemTag(self, false);
            if (compoundData == null) {
               return keys;
            }
            int i = 1;
            for (String key : compoundData.getAllKeys()) {
               keys.set(i++, LuaValue.valueOf(key));
            }
            return keys;
         }), "Returns the keys in this item's NBT data.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "SetTag", MLuaItemWrapper.selfItem((self, args) -> {
            MLuaItemWrapper.setNbtValue(self, args.checkjstring(2), args.arg(3));
            return LuaValue.NONE;
         }), "Sets one value in this item's NBT data.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "GetNbtTable", MLuaItemWrapper.selfItem((self, ignored) -> MLuaNbtWrapper.table(
            () -> MLuaItemWrapper.itemTag(self, true),
            tag -> MLuaItemWrapper.setItemTag(self, tag), registration
         )), "Returns a live table view of this item's NBT data.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "GetNbt", MLuaItemWrapper.selfItem((self, args) -> {
            if (args.narg() < 2 || args.arg(2).isnil()) {
               return MLuaNbtWrapper.table(
                  () -> MLuaItemWrapper.itemTag(self, true),
                  tag -> MLuaItemWrapper.setItemTag(self, tag), registration
               );
            }
            return MLuaNbtWrapper.get(MLuaItemWrapper.itemTag(self, false), args.checkjstring(2));
         }), "Returns this item's NBT data or one value by key.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "getNbt", classTable.rawget("GetNbt"), "Returns this item's NBT data or one value by key.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "SetNbt", MLuaItemWrapper.selfItem((self, args) -> {
            if (args.narg() == 2 && args.arg(2).istable()) {
               MLuaItemWrapper.setItemTag(self, MLuaNbtWrapper.toCompound(args.arg(2)));
               return LuaValue.NONE;
            }
            MLuaItemWrapper.setNbtValue(self, args.checkjstring(2), args.arg(3));
            return LuaValue.NONE;
         }), "Sets one NBT value or replaces this item's NBT data.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "setNbt", classTable.rawget("SetNbt"), "Sets one NBT value or replaces this item's NBT data.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "HasTagKey", MLuaItemWrapper.selfItem((self, args) -> {
            CompoundTag tag = MLuaItemWrapper.itemTag(self, false);
            return LuaValue.valueOf(tag != null && tag.contains(args.checkjstring(2)));
         }), "Returns whether this item's NBT data contains the given key.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "Clone", MLuaItemWrapper.selfItem((self, ignored) -> {
            return MLuaItemWrapper.cloneItem(self, registration);
         }), "Returns a copy of this item stack.");
         MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "Copy", classTable.rawget("Clone"), "Returns a copy of this item stack.");
          MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "GetEnchantments", MLuaItemWrapper.selfItem((self, ignored) -> {
              return MLuaEnchantmentWrapper.getEnchantments(MLuaItemWrapper.stack(self));
          }), "Returns a map from enchantment id to level for this item.");
          MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "GetEnchantmentLevel", MLuaItemWrapper.selfItem((self, args) ->
              LuaValue.valueOf(MLuaEnchantmentWrapper.getEnchantmentLevel(MLuaItemWrapper.stack(self), args.checkjstring(2)))),
              "Returns this item's level for the given enchantment id, or zero when it is not applied.");
          MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "HasEnchantment", MLuaItemWrapper.selfItem((self, args) ->
              LuaValue.valueOf(MLuaEnchantmentWrapper.hasEnchantment(MLuaItemWrapper.stack(self), args.checkjstring(2)))),
              "Returns whether this item has the given enchantment at a positive level.");
          MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "AddEnchantment", MLuaItemWrapper.selfItem((self, args) -> {
              MCItemStack item = MLuaItemWrapper.stack(self);
              if (item.isEmpty()) return LuaValue.FALSE;
              String id = args.checkjstring(2);
              int level = MLuaLuaArguments.optionalWholeInt(args, 3, 1, "Item:AddEnchantment", "level");
              if (!MLuaEnchantmentWrapper.addEnchantment(item, id, level)) {
                  throw new LuaError("[MLua] Unknown or invalid enchantment: " + id);
              }
              return LuaValue.TRUE;
          }), "Adds an enchantment to this item stack; returns false when the stack is empty.");
          MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "RemoveEnchantment", MLuaItemWrapper.selfItem((self, args) -> {
              MCItemStack item = MLuaItemWrapper.stack(self);
              if (item.isEmpty()) return LuaValue.FALSE;
              String id = args.checkjstring(2);
              if (!MLuaEnchantmentWrapper.removeEnchantment(item, id)) {
                  throw new LuaError("[MLua] Unknown or invalid enchantment: " + id);
              }
              return LuaValue.TRUE;
          }), "Removes an enchantment from this item stack; returns false when the stack is empty.");
          MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "GetItemId", classTable.rawget("GetId"), "Returns this item's registered identifier.");
          MLuaClassMethods.registerClassMethod(registration, "Item", classTable, "ToString", MLuaItemWrapper.selfItem((self, ignored) -> LuaValue.valueOf(MLuaItemWrapper.id(MLuaItemWrapper.stack(self)) + " x" + MLuaItemWrapper.stack(self).getCount())), "Returns this item's identifier and stack count.");
          String[][] properties = {
              {"Id", "GetId", null, "string", "This item's registered identifier."},
              {"Name", "GetName", null, "string", "This item's registered identifier."},
              {"Count", "GetCount", "SetCount", "number", "The number of items in this stack."},
              {"MaxStackSize", "GetMaxStackSize", null, "number", "The maximum stack size for this item."},
              {"MaxDamage", "GetMaxDamage", null, "number", "The maximum durability damage for this item."},
              {"RemainingDurability", "GetRemainingDurability", null, "number", "Remaining durability, or zero for an item without durability."},
              {"DamagePercent", "GetDamagePercent", null, "number", "Durability damage as a fraction from zero to one, capped for already-broken items."},
              {"Damage", "GetDamage", "SetDamage", "number", "This item's current durability damage."},
              {"Empty", "IsEmpty", null, "boolean", "Whether this stack contains no items."},
              {"Damageable", "IsDamageable", null, "boolean", "Whether this item has durability."},
              {"Enchantable", "IsEnchantable", null, "boolean", "Whether this item stack can receive enchantments."},
              {"Stackable", "IsStackable", null, "boolean", "Whether this stack can be stacked with a matching stack."},
              {"HasTag", "GetHasTag", null, "boolean", "Whether this item has NBT data."},
              {"DisplayName", "GetDisplayName", "SetDisplayName", "string|Component", "This item's rendered display name; assign a string or Java Component."},
              {"DisplayComponent", "GetDisplayComponent", null, "Component", "This item's native Minecraft display Component."},
              {"Lore", "GetLore", "SetLore", "table", "This item's lore lines."},
              {"Nbt", "GetNbt", "SetNbt", "Nbt|table", "This item's live NBT view; assign a table to replace its data."},
              {"Enchantments", "GetEnchantments", null, "table", "A map from enchantment id to level for this item."}
          };
          for (String[] property : properties) {
              MLuaClassMethods.registerInstanceProperty(registration, "Item", property[0], property[1], property[2],
                  property[3], "shared:Shared", property[4]);
          }
         classTable.rawset("__initialized", LuaValue.TRUE);
      }
      return classTable;
   }

}
