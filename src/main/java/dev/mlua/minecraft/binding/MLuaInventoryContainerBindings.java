package dev.mlua.minecraft.binding;

import dev.mlua.mcapi.container.MCContainer;
import dev.mlua.mcapi.item.MCItemStack;
import dev.mlua.mc.impl.item.MCItemStackImpl;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.core.classes.MLuaClassMethods;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

/** Binds the inventory-only slot groups on top of the shared container API. */
final class MLuaInventoryContainerBindings {
   private MLuaInventoryContainerBindings() { }

   static void registerClassMethods(MLuaRegistration registration) {
      MLuaClassMethods.registerInstanceMethod(registration, "Inventory", "GetHotbar",
         "Returns the live slots for the nine hotbar positions.");
      MLuaClassMethods.registerInstanceMethod(registration, "Inventory", "SetContents",
         "Validates all items before replacing inventory contents; omitted slots are cleared and Minecraft-rejected items fail before any slot changes.");
      MLuaClassMethods.registerInstanceMethod(registration, "Inventory", "GetArmorContents",
         "Returns the live slots for the four armor positions.");
      MLuaClassMethods.registerInstanceMethod(registration, "Inventory", "GetOffhandSlot",
         "Returns the live offhand slot.");
      MLuaClassMethods.registerInstanceMethod(registration, "Inventory", "SetHotbarSlot",
         "Sets or clears a hotbar slot by one-based position.");
      MLuaClassMethods.registerInstanceMethod(registration, "Inventory", "SetArmorSlot",
         "Sets or clears an armor slot by one-based position.");
      MLuaClassMethods.registerInstanceMethod(registration, "Inventory", "SetOffhandSlot",
         "Sets or clears the offhand slot.");
      MLuaClassMethods.registerInstanceProperty(registration, "Inventory", "Hotbar", "GetHotbar", null,
         "Slot[]", "shared:Shared", "Returns the live slots for the nine hotbar positions.");
      MLuaClassMethods.registerInstanceProperty(registration, "Inventory", "Armor", "GetArmorContents", null,
         "Slot[]", "shared:Shared", "Returns the live slots for the four armor positions.");
      MLuaClassMethods.registerInstanceProperty(registration, "Inventory", "Offhand", "GetOffhandSlot", null,
         "Slot|nil", "shared:Shared", "Returns the live offhand slot.");
   }

   static void bind(LuaTable table, MCContainer inventory, MLuaContainer.InventoryLayout layout,
                    Map<Integer, LuaTable> slots, boolean writable, MLuaRegistration registration) {
      if (layout == null) {
         int size = inventory.getContainerSize();
         layout = new MLuaContainer.InventoryLayout(Math.min(9, size), Math.max(1, size - 4),
            Math.min(4, Math.max(0, size - 1)), size);
      }
      bindContents(table, inventory, writable, registration);
      bindHotbar(table, inventory, layout, slots, writable, registration);
      bindArmor(table, inventory, layout, slots, writable, registration);
      bindOffhand(table, inventory, layout, slots, writable, registration);
   }

   private static void bindContents(LuaTable table, MCContainer inventory, boolean writable,
                                    MLuaRegistration registration) {
      MLuaClassMethods.setInstanceMethod(table, "SetContents", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            MLuaContainer.ensureWritable(writable);
            LuaValue contentsValue = args.arg(2);
            if (!contentsValue.istable()) {
               throw new LuaError("[MLua] SetContents expects an array of Item instances or nil values.");
            }
            LuaTable contents = contentsValue.checktable();
            int capacity = inventory.getContainerSize();
            LuaValue key = LuaValue.NIL;
            while (true) {
               Varargs entry = contents.next(key);
               key = entry.arg1();
               if (key.isnil()) break;
               if (key.type() != LuaValue.TNUMBER) {
                  throw new LuaError("[MLua] SetContents keys must be one-based numeric slot indices.");
               }
               double index = key.todouble();
               if (!Double.isFinite(index) || index != Math.rint(index) || index < 1 || index > capacity) {
                  throw new LuaError("[MLua] SetContents slot indices must be whole numbers from 1 to "
                     + capacity + ".");
               }
            }

            List<MCItemStack> replacement = new ArrayList<>(capacity);
            for (int index = 1; index <= capacity; index++) {
               LuaValue item = contents.rawget(index);
               replacement.add(item.isnil() ? MCItemStack.EMPTY
                  : new MCItemStackImpl(MLuaItemWrapper.toItemStack(item, registration)));
            }
            for (int index = 0; index < replacement.size(); index++) {
               MCItemStack stack = replacement.get(index);
               int maxCount = stack.isEmpty() ? inventory.getMaxStackSize()
                  : Math.min(inventory.getMaxStackSize(), stack.getMaxStackSize());
               if (!stack.isEmpty() && stack.getCount() > maxCount) {
                  throw new LuaError("[MLua] Item count for slot " + (index + 1)
                     + " exceeds this inventory's maximum stack size of " + maxCount + ".");
               }
               if (!stack.isEmpty() && !inventory.canPlaceItem(index, stack)) {
                  throw new LuaError("[MLua] Container rejected item for slot " + (index + 1) + ".");
               }
            }
            for (int index = 0; index < replacement.size(); index++) {
               MLuaContainer.setContainerItem(inventory, index, replacement.get(index), index + 1);
            }
            return LuaValue.NONE;
         }
      });
   }

   private static void bindHotbar(LuaTable table, MCContainer inventory, MLuaContainer.InventoryLayout layout,
                                  Map<Integer, LuaTable> slots, boolean writable,
                                  MLuaRegistration registration) {
      MLuaClassMethods.setInstanceMethod(table, "GetHotbar", new ZeroArgFunction() {
         @Override public LuaValue call() {
            LuaTable result = new LuaTable();
            for (int index = 1; index <= layout.hotbarSize(); index++) {
               result.set(index, MLuaContainer.getSlotInstance(inventory, slots, index, writable, registration));
            }
            return result;
         }
      });
      MLuaClassMethods.setInstanceMethod(table, "SetHotbarSlot", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            int index = MLuaLuaArguments.wholeInt(args, 2, "Inventory:SetHotbarSlot", "slot");
            if (index < 1 || index > layout.hotbarSize()) {
               throw new LuaError("[MLua] Hotbar slot must be between 1 and " + layout.hotbarSize() + ".");
            }
            MLuaContainer.setSlot(inventory, writable, index, args.arg(3), registration);
            return LuaValue.NONE;
         }
      });
   }

   private static void bindArmor(LuaTable table, MCContainer inventory, MLuaContainer.InventoryLayout layout,
                                 Map<Integer, LuaTable> slots, boolean writable,
                                 MLuaRegistration registration) {
      MLuaClassMethods.setInstanceMethod(table, "GetArmorContents", new ZeroArgFunction() {
         @Override public LuaValue call() {
            LuaTable result = new LuaTable();
            for (int index = 0; index < layout.armorSize(); index++) {
               result.set(index + 1, MLuaContainer.getSlotInstance(inventory, slots,
                  layout.armorStartIndex() + index, writable, registration));
            }
            return result;
         }
      });
      MLuaClassMethods.setInstanceMethod(table, "SetArmorSlot", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            int index = MLuaLuaArguments.wholeInt(args, 2, "Inventory:SetArmorSlot", "slot");
            if (index < 1 || index > layout.armorSize()) {
               throw new LuaError("[MLua] Armor slot must be between 1 and " + layout.armorSize() + ".");
            }
            MLuaContainer.setSlot(inventory, writable,
               layout.armorStartIndex() + index - 1, args.arg(3), registration);
            return LuaValue.NONE;
         }
      });
   }

   private static void bindOffhand(LuaTable table, MCContainer inventory, MLuaContainer.InventoryLayout layout,
                                   Map<Integer, LuaTable> slots, boolean writable,
                                   MLuaRegistration registration) {
      MLuaClassMethods.setInstanceMethod(table, "GetOffhandSlot", new ZeroArgFunction() {
         @Override public LuaValue call() {
            return MLuaContainer.getSlotInstance(inventory, slots, layout.offhandIndex(), writable, registration);
         }
      });
      MLuaClassMethods.setInstanceMethod(table, "SetOffhandSlot", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            MLuaContainer.setSlot(inventory, writable, layout.offhandIndex(), args.arg(2), registration);
            return LuaValue.NONE;
         }
      });
   }
}
