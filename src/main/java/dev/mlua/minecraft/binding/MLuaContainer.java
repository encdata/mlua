package dev.mlua.minecraft.binding;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.lang.ref.WeakReference;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import dev.mlua.mcapi.container.MCContainer;
import dev.mlua.mcapi.container.MCAbstractContainerMenu;
import dev.mlua.mcapi.container.MCSlot;
import dev.mlua.mc.impl.container.MCContainerImpl;
import dev.mlua.mcapi.item.MCItemStack;
import dev.mlua.mcapi.core.MCComponent;
import dev.mlua.mc.impl.item.MCItemStackImpl;
import dev.mlua.mc.impl.item.MCItemImpl;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.minecraft.MLuaResourceLocations;
import net.minecraft.core.registries.BuiltInRegistries;
import org.luaj.vm2.LuaError;

public final class MLuaContainer {
   private static final Map<MLuaRegistration, Map<Object, Map<InstanceVariant, WeakReference<LuaTable>>>> INSTANCES = new WeakHashMap<>();

   private MLuaContainer() {}

   public static LuaTable containerTable(MCContainer inv, boolean writable) {
      return containerTable(inv, writable, MLuaRuntime.get());
   }

   public static LuaTable containerTable(MCContainer inv, boolean writable, MLuaRegistration registration) {
      return containerTable(inv, writable, registration, "Container", null);
   }

   public static LuaTable containerTable(MCContainer inv, boolean writable, MLuaRegistration registration,
                                         String title) {
      return containerTable(inv, writable, registration, "Container", title);
   }

   public static LuaTable inventoryTable(MCContainer inv, boolean writable, MLuaRegistration registration) {
      int size = inv.getContainerSize();
      return inventoryTable(inv, writable, registration,
         new InventoryLayout(Math.min(9, size), Math.max(1, size - 4), Math.min(4, Math.max(0, size - 1)), size));
   }

   public static LuaTable inventoryTable(MCContainer inv, boolean writable, MLuaRegistration registration,
                                         InventoryLayout layout) {
      validateInventoryLayout(inv.getContainerSize(), layout);
      return containerTable(inv, writable, registration, "Inventory", "Inventory", layout);
   }

   private static LuaTable containerTable(MCContainer inv, boolean writable, MLuaRegistration registration,
                                          String className, String title) {
      return containerTable(inv, writable, registration, className, title, null);
   }

   private static LuaTable containerTable(MCContainer inv, boolean writable, MLuaRegistration registration,
                                          String className, String title, InventoryLayout inventoryLayout) {
      Object backing = inv instanceof MCContainerImpl container ? container.inner() : inv;
      return cachedInstance(registration, backing, className, writable, title, inventoryLayout,
         () -> createContainerTable(inv, writable, registration, className, title, inventoryLayout));
   }

   private static LuaTable createContainerTable(MCContainer inv, boolean writable, MLuaRegistration registration,
                                                String className, String title, InventoryLayout inventoryLayout) {
      LuaTable fields = new LuaTable();
      Map<Integer, LuaTable> slotInstances = new HashMap<>();
      LuaTable table = createClassInstance(registration, className, fields);
      table.set("__side", "Shared");
      bindContainerMethods(table, containerAccess(inv), title, slotInstances, writable, registration);

      if ("Inventory".equals(className)) {
         InventoryLayout layout = inventoryLayout == null
            ? new InventoryLayout(Math.min(9, inv.getContainerSize()),
               Math.max(1, inv.getContainerSize() - 4),
               Math.min(4, Math.max(0, inv.getContainerSize() - 1)), inv.getContainerSize())
            : inventoryLayout;
         MLuaClassMethods.setInstanceMethod(table, "SetContents", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
               ensureWritable(writable);
               LuaValue contentsValue = args.arg(2);
               if (!contentsValue.istable()) {
                  throw new LuaError("[MLua] SetContents expects an array of Item instances or nil values.");
               }
               LuaTable contents = contentsValue.checktable();
               int capacity = inv.getContainerSize();
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
                  int maxCount = stack.isEmpty() ? inv.getMaxStackSize()
                     : Math.min(inv.getMaxStackSize(), stack.getMaxStackSize());
                  if (!stack.isEmpty() && stack.getCount() > maxCount) {
                     throw new LuaError("[MLua] Item count for slot " + (index + 1)
                        + " exceeds this inventory's maximum stack size of " + maxCount + ".");
                  }
                  if (!stack.isEmpty() && !inv.canPlaceItem(index, stack)) {
                     throw new LuaError("[MLua] Container rejected item for slot " + (index + 1) + ".");
                  }
               }
               for (int index = 0; index < replacement.size(); index++) {
                  setContainerItem(inv, index, replacement.get(index), index + 1);
               }
               return LuaValue.NONE;
            }
         });
         MLuaClassMethods.setInstanceMethod(table, "GetHotbar", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
               LuaTable slots = new LuaTable();
               for (int index = 1; index <= layout.hotbarSize(); index++) {
                  slots.set(index, getSlotInstance(inv, slotInstances, index, writable, registration));
               }
               return slots;
            }
         });
         MLuaClassMethods.setInstanceMethod(table, "GetArmorContents", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
               LuaTable slots = new LuaTable();
               for (int index = 0; index < layout.armorSize(); index++) {
                  slots.set(index + 1, getSlotInstance(inv, slotInstances,
                     layout.armorStartIndex() + index, writable, registration));
               }
               return slots;
            }
         });
         MLuaClassMethods.setInstanceMethod(table, "GetOffhandSlot", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
               return getSlotInstance(inv, slotInstances, layout.offhandIndex(), writable, registration);
            }
         });
         MLuaClassMethods.setInstanceMethod(table, "SetHotbarSlot", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
               int index = MLuaLuaArguments.wholeInt(args, 2, "Inventory:SetHotbarSlot", "slot");
               if (index < 1 || index > layout.hotbarSize()) {
                  throw new LuaError("[MLua] Hotbar slot must be between 1 and "
                     + layout.hotbarSize() + ".");
               }
               setSlot(inv, writable, index, args.arg(3), registration);
               return LuaValue.NONE;
            }
         });
         MLuaClassMethods.setInstanceMethod(table, "SetArmorSlot", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
               int index = MLuaLuaArguments.wholeInt(args, 2, "Inventory:SetArmorSlot", "slot");
               if (index < 1 || index > layout.armorSize()) {
                  throw new LuaError("[MLua] Armor slot must be between 1 and "
                     + layout.armorSize() + ".");
               }
               setSlot(inv, writable, layout.armorStartIndex() + index - 1, args.arg(3), registration);
               return LuaValue.NONE;
            }
         });
         MLuaClassMethods.setInstanceMethod(table, "SetOffhandSlot", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
               setSlot(inv, writable, layout.offhandIndex(), args.arg(2), registration);
               return LuaValue.NONE;
            }
         });
      }

      return table;
   }

   private static LuaValue getSlotInstance(MCContainer container, Map<Integer, LuaTable> slotInstances,
                                           int oneBasedIndex, boolean writable,
                                           MLuaRegistration registration) {
      int rawIndex = toRawIndex(oneBasedIndex, container.getContainerSize());
      if (rawIndex < 0) return LuaValue.NIL;
      return slotInstance(slotInstances, rawIndex, () -> container.getItem(rawIndex), () -> rawIndex,
         () -> {
            MCItemStack stack = container.getItem(rawIndex);
            return stack == null || stack.isEmpty() ? container.getMaxStackSize()
               : Math.min(container.getMaxStackSize(), stack.getMaxStackSize());
         },
         stack -> setContainerItem(container, rawIndex, stack, oneBasedIndex),
         oneBasedIndex, writable, registration);
   }

   private static void setSlot(MCContainer container, boolean writable, int oneBasedIndex, LuaValue value,
                               MLuaRegistration registration) {
      ensureWritable(writable);
      int rawIndex = requireRawIndex(oneBasedIndex, container.getContainerSize());
      MCItemStack stack = value.isnil() ? MCItemStack.EMPTY
         : new MCItemStackImpl(MLuaItemWrapper.toItemStack(value, registration));
      setContainerItem(container, rawIndex, stack, oneBasedIndex);
   }

   private static void setContainerItem(MCContainer container, int rawIndex, MCItemStack stack,
                                        int oneBasedIndex) {
      if (!stack.isEmpty()) {
         int maxCount = Math.min(container.getMaxStackSize(), stack.getMaxStackSize());
         if (stack.getCount() > maxCount) {
            throw new LuaError("[MLua] Item count for slot " + oneBasedIndex
               + " exceeds this container's maximum stack size of " + maxCount + ".");
         }
      }
      if (!stack.isEmpty() && !container.canPlaceItem(rawIndex, stack)) {
         throw new LuaError("[MLua] Container rejected item for slot " + oneBasedIndex + ".");
      }
      container.setItem(rawIndex, stack);
   }

   public static LuaTable containerTable(MCAbstractContainerMenu menu, MCComponent title, boolean writable) {
      return containerTable(menu, title, writable, MLuaRuntime.get());
   }

   public static LuaTable containerTable(MCAbstractContainerMenu menu, MCComponent title, boolean writable, MLuaRegistration registration) {
      Object backing = menu instanceof dev.mlua.mc.impl.container.MCAbstractContainerMenuImpl container ? container.inner() : menu;
      String titleStr = title == null ? null : title.getString();
      return cachedInstance(registration, backing, "Container", writable, titleStr,
         () -> createMenuTable(menu, title, writable, registration));
   }

   /** Creates a live menu view whose writes stop being allowed when its owner closes or replaces the menu. */
   public static LuaTable containerTable(MCAbstractContainerMenu menu, MCComponent title, boolean writable,
                                         MLuaRegistration registration, BooleanSupplier menuIsActive) {
      if (menuIsActive == null) return containerTable(menu, title, writable, registration);
      return createMenuTable(menu, title, writable, registration, menuIsActive);
   }

   private static LuaTable createMenuTable(MCAbstractContainerMenu menu, MCComponent title, boolean writable, MLuaRegistration registration) {
      return createMenuTable(menu, title, writable, registration, () -> true);
   }

   private static LuaTable createMenuTable(MCAbstractContainerMenu menu, MCComponent title, boolean writable,
                                          MLuaRegistration registration, BooleanSupplier menuIsActive) {
      LuaTable fields = new LuaTable();
      Map<Integer, LuaTable> slotInstances = new HashMap<>();
      LuaTable table = createClassInstance(registration, "Container", fields);
      table.set("__side", "Shared");
      bindContainerMethods(table, menuAccess(menu, menuIsActive), title == null ? null : title.getString(),
         slotInstances, writable, registration);

      return table;
   }

   private static void bindContainerMethods(LuaTable table, ContainerAccess access, String title,
                                           Map<Integer, LuaTable> slotInstances, boolean writable,
                                           MLuaRegistration registration) {
      MLuaClassMethods.setInstanceMethod(table, "GetSize", new ZeroArgFunction() {
         @Override public LuaValue call() { return LuaValue.valueOf(access.size()); }
      });
      MLuaClassMethods.setInstanceMethod(table, "IsEmpty", new ZeroArgFunction() {
         @Override public LuaValue call() { return LuaValue.valueOf(access.isEmpty()); }
      });
      MLuaClassMethods.setInstanceMethod(table, "Clear", new ZeroArgFunction() {
         @Override
         public LuaValue call() {
            ensureWritable(writable, access);
            access.clear();
            return LuaValue.NONE;
         }
      });
      MLuaClassMethods.setInstanceMethod(table, "GetSlot", new VarArgFunction() {
         @Override
         public Varargs invoke(Varargs args) {
            int index = MLuaLuaArguments.wholeInt(args, 2, "Container:GetSlot", "slot");
            int rawIndex = toRawIndex(index, access.size());
            if (rawIndex < 0) return LuaValue.NIL;
            return slotInstance(slotInstances, rawIndex, () -> access.getItem(rawIndex), () -> rawIndex,
               () -> access.getMaxStackSize(rawIndex, access.getItem(rawIndex)),
               stack -> access.setItem(rawIndex, stack), index, writable, registration, access::ensureWritable);
         }
      });
      MLuaClassMethods.setInstanceMethod(table, "SetSlot", new VarArgFunction() {
         @Override
         public Varargs invoke(Varargs args) {
            ensureWritable(writable, access);
            int rawIndex = requireRawIndex(
               MLuaLuaArguments.wholeInt(args, 2, "Container:SetSlot", "slot"), access.size());
            LuaValue itemValue = args.arg(3);
            access.setItem(rawIndex, itemValue.isnil() ? MCItemStack.EMPTY
               : new MCItemStackImpl(MLuaItemWrapper.toItemStack(itemValue, registration)));
            return LuaValue.NONE;
         }
      });
      MLuaClassMethods.setInstanceMethod(table, "GetTitle", new ZeroArgFunction() {
         @Override
         public LuaValue call() {
            return title == null ? LuaValue.NIL : LuaValue.valueOf(title);
         }
      });
      MLuaClassMethods.setInstanceMethod(table, "GetAll", new ZeroArgFunction() {
         @Override
         public LuaValue call() {
            LuaTable slots = new LuaTable();
            for (int rawIndex = 0; rawIndex < access.size(); rawIndex++) {
               int resolvedRawIndex = rawIndex;
               int index = resolvedRawIndex + 1;
               slots.set(index, slotInstance(slotInstances, resolvedRawIndex,
                  () -> access.getItem(resolvedRawIndex), () -> resolvedRawIndex,
                  () -> access.getMaxStackSize(resolvedRawIndex, access.getItem(resolvedRawIndex)),
                  stack -> access.setItem(resolvedRawIndex, stack), index, writable, registration,
                  access::ensureWritable));
            }
            return slots;
         }
      });
      MLuaClassMethods.setInstanceMethod(table, "GetFirstEmptySlot", new ZeroArgFunction() {
         @Override public LuaValue call() {
            for (int rawIndex = 0; rawIndex < access.size(); rawIndex++) {
               if (access.getItem(rawIndex).isEmpty()) {
                  int slotIndex = rawIndex;
                  int index = slotIndex + 1;
                  return slotInstance(slotInstances, slotIndex, () -> access.getItem(slotIndex), () -> slotIndex,
                     () -> access.getMaxStackSize(slotIndex, access.getItem(slotIndex)),
                     stack -> access.setItem(slotIndex, stack), index, writable, registration,
                     access::ensureWritable);
               }
            }
            return LuaValue.NIL;
         }
      });
      MLuaClassMethods.setInstanceMethod(table, "CountItem", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            String itemId = itemIdArgument(args, 2, "Container:CountItem", registration);
            long count = 0;
            for (int index = 0; index < access.size(); index++) {
               MCItemStack stack = access.getItem(index);
               if (!stack.isEmpty() && itemId.equals(itemId(stack))) count += stack.getCount();
            }
            return LuaValue.valueOf(count);
         }
      });
      MLuaClassMethods.setInstanceMethod(table, "FindSlots", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            String itemId = itemIdArgument(args, 2, "Container:FindSlots", registration);
            LuaTable matches = new LuaTable();
            int resultIndex = 1;
            for (int rawIndex = 0; rawIndex < access.size(); rawIndex++) {
               MCItemStack stack = access.getItem(rawIndex);
               if (stack.isEmpty() || !itemId.equals(itemId(stack))) continue;
               int slotIndex = rawIndex;
               int index = slotIndex + 1;
               matches.set(resultIndex++, slotInstance(slotInstances, slotIndex,
                  () -> access.getItem(slotIndex), () -> slotIndex,
                  () -> access.getMaxStackSize(slotIndex, access.getItem(slotIndex)),
                  value -> access.setItem(slotIndex, value), index, writable, registration,
                  access::ensureWritable));
            }
            return matches;
         }
      });
      MLuaClassMethods.setInstanceMethod(table, "ContainsItem", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            String itemId = itemIdArgument(args, 2, "Container:ContainsItem", registration);
            int required = MLuaLuaArguments.optionalWholeInt(args, 3, 1, "Container:ContainsItem", "count");
            if (required < 1) throw new LuaError("[MLua] Required item count must be positive.");
            long count = 0;
            for (int index = 0; index < access.size(); index++) {
               MCItemStack stack = access.getItem(index);
               if (!stack.isEmpty() && itemId.equals(itemId(stack))) count += stack.getCount();
            }
            return LuaValue.valueOf(count >= required);
         }
      });
      MLuaClassMethods.setInstanceMethod(table, "RemoveItem", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            ensureWritable(writable, access);
            String itemId = itemIdArgument(args, 2, "Container:RemoveItem", registration);
            int remaining = MLuaLuaArguments.wholeInt(args, 3, "Container:RemoveItem", "count");
            if (remaining < 1) throw new LuaError("[MLua] Removed item count must be positive.");
            int removed = 0;
            for (int index = 0; index < access.size() && remaining > 0; index++) {
               MCItemStack stack = access.getItem(index);
               if (stack.isEmpty() || !itemId.equals(itemId(stack))) continue;
               int amount = Math.min(remaining, stack.getCount());
               int newCount = stack.getCount() - amount;
               if (newCount == 0) {
                  access.setItem(index, MCItemStack.EMPTY);
               } else {
                  stack.setCount(newCount);
                  access.setItem(index, stack);
               }
               removed += amount;
               remaining -= amount;
            }
            return LuaValue.valueOf(removed);
         }
      });
   }

   private static String itemIdArgument(Varargs args, int index, String method,
                                       MLuaRegistration registration) {
      LuaValue value = args.arg(index);
      if (registration.isInstanceOf(value, "Item")) value = value.get("Id");
      if (value.type() != LuaValue.TSTRING || value.tojstring().isBlank()) {
         throw new LuaError("[MLua] " + method + " expects a registered item id or Item instance.");
      }
      return requireItemId(value.tojstring(), method);
   }

   private static String requireItemId(String value, String method) {
      var location = MLuaResourceLocations.tryParseOrNull(value);
      if (location == null) {
         throw new LuaError("[MLua] " + method + " received an invalid item id: " + value);
      }
      if (BuiltInRegistries.ITEM.getOptional(location).isEmpty()) {
         throw new LuaError("[MLua] " + method + " received an unknown item id: " + value);
      }
      return location.toString();
   }

   private static String itemId(MCItemStack stack) {
      if (!(stack.getItem() instanceof MCItemImpl item)) return "";
      var location = BuiltInRegistries.ITEM.getKey(item.inner());
      return location == null ? "" : location.toString();
   }

   private static ContainerAccess containerAccess(MCContainer container) {
      return new ContainerAccess() {
         @Override public int size() { return container.getContainerSize(); }
         @Override public MCItemStack getItem(int index) { return container.getItem(index); }
         @Override public int getMaxStackSize(int index, MCItemStack stack) {
            return stack == null || stack.isEmpty() ? container.getMaxStackSize()
               : Math.min(container.getMaxStackSize(), stack.getMaxStackSize());
         }
         @Override public void setItem(int index, MCItemStack stack) {
            setContainerItem(container, index, stack, index + 1);
         }
         @Override public void clear() { container.clearContent(); }
         @Override
         public boolean isEmpty() {
            for (MCItemStack stack : container.getItems()) {
               if (stack != null && !stack.isEmpty()) return false;
            }
            return true;
         }
      };
   }

   private static ContainerAccess menuAccess(MCAbstractContainerMenu menu, BooleanSupplier menuIsActive) {
      return new ContainerAccess() {
         @Override public void ensureWritable() {
            if (!menuIsActive.getAsBoolean()) {
               throw new LuaError("[MLua] This menu is no longer open; its container cannot be changed.");
            }
         }
         @Override public int size() { return menu.slots().size(); }
         @Override public MCItemStack getItem(int index) { return menu.slots().get(index).getItem(); }
         @Override public int getMaxStackSize(int index, MCItemStack stack) {
            return menu.slots().get(index).getMaxStackSize(stack);
         }
         @Override public void setItem(int index, MCItemStack stack) {
            ensureWritable();
            MCSlot slot = menu.slots().get(index);
            if (!stack.isEmpty() && !slot.mayPlace(stack)) {
               throw new LuaError("[MLua] Container slot " + (index + 1) + " rejected this item.");
            }
            if (!stack.isEmpty()) {
               int maxCount = slot.getMaxStackSize(stack);
               if (stack.getCount() > maxCount) {
                  throw new LuaError("[MLua] Item count for container slot " + (index + 1)
                     + " exceeds its maximum stack size of " + maxCount + ".");
               }
            }
            slot.set(stack);
            slot.setChanged();
         }
         @Override public void clear() {
            for (int index = 0; index < menu.slots().size(); index++) setItem(index, MCItemStack.EMPTY);
         }
         @Override
         public boolean isEmpty() {
            for (MCSlot slot : menu.slots()) {
               if (slot.hasItem()) return false;
            }
            return true;
         }
      };
   }

   private interface ContainerAccess {
      default void ensureWritable() {}
      int size();
      MCItemStack getItem(int index);
      int getMaxStackSize(int index, MCItemStack stack);
      void setItem(int index, MCItemStack stack);
      void clear();
      boolean isEmpty();
   }

   private static LuaTable createClassInstance(MLuaRegistration registration, String className, LuaTable fields) {
      return registration.createClassInstance(className, fields);
   }

   public static void registerClassMethods(MLuaRegistration registration) {
      if (registration.classTable("Container") == null) {
         throw new IllegalStateException("Container class must be registered before its instance methods");
      }
      MLuaClassMethods.registerInstanceMethod(registration, "Container", "GetSize", "Returns the number of slots in this container.");
      MLuaClassMethods.registerInstanceMethod(registration, "Container", "IsEmpty", "Returns whether this container has no items.");
      MLuaClassMethods.registerInstanceMethod(registration, "Container", "Clear", "Removes all items from this writable container.");
      MLuaClassMethods.registerInstanceMethod(registration, "Container", "GetSlot", "Returns the live slot at a one-based whole-number index.");
      MLuaClassMethods.registerInstanceMethod(registration, "Container", "SetSlot", "Sets the item in a writable slot by one-based whole-number index; raises a Lua error if Minecraft rejects the item or its stack count exceeds the slot limit.");
      MLuaClassMethods.registerInstanceMethod(registration, "Container", "GetTitle", "Returns this container's display title when available.");
      MLuaClassMethods.registerInstanceMethod(registration, "Container", "GetAll", "Returns all live slots in this container.");
      MLuaClassMethods.registerInstanceMethod(registration, "Container", "GetFirstEmptySlot", "Returns the first empty live slot, or nil when full.");
      MLuaClassMethods.registerInstanceMethod(registration, "Container", "CountItem", "Returns the total count of an item identifier in this container.");
      MLuaClassMethods.registerInstanceMethod(registration, "Container", "FindSlots", "Returns live slots containing the given item identifier.");
      MLuaClassMethods.registerInstanceMethod(registration, "Container", "ContainsItem", "Returns whether this container has at least the requested whole-number item count.");
      MLuaClassMethods.registerInstanceMethod(registration, "Container", "RemoveItem", "Removes up to a positive whole-number count from this writable container and returns the removed count.");
      MLuaClassMethods.registerInstanceMethod(registration, "Inventory", "GetHotbar", "Returns the live slots for the nine hotbar positions.");
      MLuaClassMethods.registerInstanceMethod(registration, "Inventory", "SetContents", "Validates all items before replacing inventory contents; omitted slots are cleared and Minecraft-rejected items fail before any slot changes.");
      MLuaClassMethods.registerInstanceMethod(registration, "Inventory", "GetArmorContents", "Returns the live slots for the four armor positions.");
      MLuaClassMethods.registerInstanceMethod(registration, "Inventory", "GetOffhandSlot", "Returns the live offhand slot.");
      MLuaClassMethods.registerInstanceMethod(registration, "Inventory", "SetHotbarSlot", "Sets or clears a hotbar slot by one-based position.");
      MLuaClassMethods.registerInstanceMethod(registration, "Inventory", "SetArmorSlot", "Sets or clears an armor slot by one-based position.");
      MLuaClassMethods.registerInstanceMethod(registration, "Inventory", "SetOffhandSlot", "Sets or clears the offhand slot.");
      MLuaClassMethods.registerInstanceProperty(registration, "Inventory", "Hotbar", "GetHotbar", null,
         "Slot[]", "shared:Shared", "Returns the live slots for the nine hotbar positions.");
      MLuaClassMethods.registerInstanceProperty(registration, "Inventory", "Armor", "GetArmorContents", null,
         "Slot[]", "shared:Shared", "Returns the live slots for the four armor positions.");
      MLuaClassMethods.registerInstanceProperty(registration, "Inventory", "Offhand", "GetOffhandSlot", null,
         "Slot|nil", "shared:Shared", "Returns the live offhand slot.");
      MLuaClassMethods.registerInstanceProperty(registration, "Container", "Size", "GetSize", null,
         "number", "shared:Shared", "Returns the number of slots in this container.");
      MLuaClassMethods.registerInstanceProperty(registration, "Container", "Empty", "IsEmpty", null,
         "boolean", "shared:Shared", "Whether this container has no items.");
      MLuaClassMethods.registerInstanceProperty(registration, "Container", "Slots", "GetAll", null,
         "Slot[]", "shared:Shared", "Returns the live slot instances in this container.");
      MLuaClassMethods.registerInstanceProperty(registration, "Container", "Title", "GetTitle", null,
         "string|nil", "shared:Shared", "Returns this container's title when the backing container exposes one.");
      MLuaClassMethods.registerInstanceProperty(registration, "Container", "FirstEmptySlot", "GetFirstEmptySlot", null,
         "Slot|nil", "shared:Shared", "Returns the first empty live slot, or nil when full.");
   }

   private static LuaTable cachedInstance(MLuaRegistration registration, Object backing, String className,
                                          boolean writable, String title, Supplier<LuaTable> factory) {
      return cachedInstance(registration, backing, className, writable, title, null, factory);
   }

   private static LuaTable cachedInstance(MLuaRegistration registration, Object backing, String className,
                                          boolean writable, String title, InventoryLayout inventoryLayout,
                                          Supplier<LuaTable> factory) {
      InstanceVariant variant = new InstanceVariant(className, writable, title, inventoryLayout);
      synchronized (INSTANCES) {
         Map<Object, Map<InstanceVariant, WeakReference<LuaTable>>> byBacking =
            INSTANCES.computeIfAbsent(registration, ignored -> new WeakHashMap<>());
         Map<InstanceVariant, WeakReference<LuaTable>> variants =
            byBacking.computeIfAbsent(backing, ignored -> new HashMap<>());
         WeakReference<LuaTable> reference = variants.get(variant);
         LuaTable cached = reference != null ? reference.get() : null;
         if (cached != null) return cached;
         LuaTable created = factory.get();
         variants.put(variant, new WeakReference<>(created));
         return created;
      }
   }

   public record InventoryLayout(int hotbarSize, int armorStartIndex, int armorSize, int offhandIndex) {
      public InventoryLayout {
         if (hotbarSize < 0 || armorStartIndex < 1 || armorSize < 0 || offhandIndex < 1) {
            throw new IllegalArgumentException("Inventory layout indexes and sizes must be non-negative and one-based where applicable.");
         }
      }
   }

   private static void validateInventoryLayout(int containerSize, InventoryLayout layout) {
      if (layout == null) throw new NullPointerException("layout");
      if (containerSize < 0) throw new IllegalArgumentException("Container size cannot be negative.");
      long armorEnd = (long) layout.armorStartIndex() + layout.armorSize() - 1;
      if (layout.hotbarSize() > containerSize
          || (layout.armorSize() > 0 && armorEnd > containerSize)
          || (containerSize > 0 && layout.offhandIndex() > containerSize)) {
         throw new IllegalArgumentException("Inventory layout indexes exceed the container size of "
            + containerSize + ".");
      }
      if (layout.hotbarSize() > 0 && layout.armorSize() > 0
          && layout.armorStartIndex() <= layout.hotbarSize()) {
         throw new IllegalArgumentException("Inventory hotbar and armor slots cannot overlap.");
      }
      if (layout.offhandIndex() <= layout.hotbarSize()
          || (layout.armorSize() > 0 && layout.offhandIndex() >= layout.armorStartIndex()
              && layout.offhandIndex() <= armorEnd)) {
         throw new IllegalArgumentException("Inventory offhand slot cannot overlap hotbar or armor slots.");
      }
   }

   private record InstanceVariant(String className, boolean writable, String title,
                                  InventoryLayout inventoryLayout) {}

   private static LuaTable slotInstance(Map<Integer, LuaTable> cache, int rawIndex,
                                        Supplier<MCItemStack> stackSupplier, IntSupplier rawIndexSupplier,
                                        IntSupplier maxStackSizeSupplier,
                                        Consumer<MCItemStack> setter, int index, boolean writable,
                                        MLuaRegistration registration) {
      return slotInstance(cache, rawIndex, stackSupplier, rawIndexSupplier, maxStackSizeSupplier,
         setter, index, writable, registration, () -> {});
   }

   private static LuaTable slotInstance(Map<Integer, LuaTable> cache, int rawIndex,
                                        Supplier<MCItemStack> stackSupplier, IntSupplier rawIndexSupplier,
                                        IntSupplier maxStackSizeSupplier,
                                        Consumer<MCItemStack> setter, int index, boolean writable,
                                        MLuaRegistration registration, Runnable writeGuard) {
      synchronized (cache) {
         return cache.computeIfAbsent(rawIndex, ignored ->
            MLuaSlot.slotTable(stackSupplier, rawIndexSupplier, maxStackSizeSupplier,
               index, setter, writable, registration, () -> {
                  writeGuard.run();
                  return true;
               }));
      }
   }

   public static LuaTable containerTable(MCContainer inv) {
      return containerTable(inv, true);
   }

   public static LuaTable containerTable(MCAbstractContainerMenu menu, MCComponent title) {
      return containerTable(menu, title, true);
   }

   public static LuaTable containerTable(MCAbstractContainerMenu menu) {
      return containerTable(menu, null, true);
   }

   private static int toRawIndex(int index, int size) {
      int rawIndex = index - 1;
      return rawIndex >= 0 && rawIndex < size ? rawIndex : -1;
   }

   private static int requireRawIndex(int index, int size) {
      int rawIndex = toRawIndex(index, size);
      if (rawIndex < 0) {
         throw new LuaError("[MLua] Slot index " + index + " is outside this container's valid range (1-" + size + ").");
      }
      return rawIndex;
   }

   private static void ensureWritable(boolean writable) {
      if (!writable) {
         throw new LuaError("[MLua] This container is read-only on the current side.");
      }
   }

   private static void ensureWritable(boolean writable, ContainerAccess access) {
      ensureWritable(writable);
      access.ensureWritable();
   }
}
