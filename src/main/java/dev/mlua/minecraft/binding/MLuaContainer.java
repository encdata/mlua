package dev.mlua.minecraft.binding;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.lang.ref.WeakReference;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import dev.mlua.mcapi.container.MCContainer;
import dev.mlua.mcapi.container.MCAbstractContainerMenu;
import dev.mlua.mcapi.container.MCSlot;
import dev.mlua.mc.impl.container.MCContainerImpl;
import dev.mlua.mcapi.item.MCItemStack;
import dev.mlua.mcapi.core.MCComponent;
import dev.mlua.mc.impl.item.MCItemStackImpl;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.classes.MLuaClassMethods;

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
               int index = args.checkint(2);
               if (index < 1 || index > layout.hotbarSize()) {
                  throw new org.luaj.vm2.LuaError("[MLua] Hotbar slot must be between 1 and "
                     + layout.hotbarSize() + ".");
               }
               setSlot(inv, writable, index, args.arg(3));
               return LuaValue.NONE;
            }
         });
         MLuaClassMethods.setInstanceMethod(table, "SetArmorSlot", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
               int index = args.checkint(2);
               if (index < 1 || index > layout.armorSize()) {
                  throw new org.luaj.vm2.LuaError("[MLua] Armor slot must be between 1 and "
                     + layout.armorSize() + ".");
               }
               setSlot(inv, writable, layout.armorStartIndex() + index - 1, args.arg(3));
               return LuaValue.NONE;
            }
         });
         MLuaClassMethods.setInstanceMethod(table, "SetOffhandSlot", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
               setSlot(inv, writable, layout.offhandIndex(), args.arg(2));
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
         stack -> container.setItem(rawIndex, stack), oneBasedIndex, writable, registration);
   }

   private static void setSlot(MCContainer container, boolean writable, int oneBasedIndex, LuaValue value) {
      ensureWritable(writable);
      int rawIndex = requireRawIndex(oneBasedIndex, container.getContainerSize());
      container.setItem(rawIndex, value.isnil() ? MCItemStack.EMPTY
         : new MCItemStackImpl(MLuaItemWrapper.toItemStack(value)));
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

   private static LuaTable createMenuTable(MCAbstractContainerMenu menu, MCComponent title, boolean writable, MLuaRegistration registration) {
      LuaTable fields = new LuaTable();
      Map<Integer, LuaTable> slotInstances = new HashMap<>();
      LuaTable table = createClassInstance(registration, "Container", fields);
      table.set("__side", "Shared");
      bindContainerMethods(table, menuAccess(menu), title == null ? null : title.getString(),
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
            ensureWritable(writable);
            access.clear();
            return LuaValue.NONE;
         }
      });
      MLuaClassMethods.setInstanceMethod(table, "GetSlot", new VarArgFunction() {
         @Override
         public Varargs invoke(Varargs args) {
            int index = args.checkint(2);
            int rawIndex = toRawIndex(index, access.size());
            if (rawIndex < 0) return LuaValue.NIL;
            return slotInstance(slotInstances, rawIndex, () -> access.getItem(rawIndex), () -> rawIndex,
               stack -> access.setItem(rawIndex, stack), index, writable, registration);
         }
      });
      MLuaClassMethods.setInstanceMethod(table, "SetSlot", new VarArgFunction() {
         @Override
         public Varargs invoke(Varargs args) {
            ensureWritable(writable);
            int rawIndex = requireRawIndex(args.checkint(2), access.size());
            LuaValue itemValue = args.arg(3);
            access.setItem(rawIndex, itemValue.isnil() ? MCItemStack.EMPTY
               : new MCItemStackImpl(MLuaItemWrapper.toItemStack(itemValue)));
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
                  stack -> access.setItem(resolvedRawIndex, stack), index, writable, registration));
            }
            return slots;
         }
      });
   }

   private static ContainerAccess containerAccess(MCContainer container) {
      return new ContainerAccess() {
         @Override public int size() { return container.getContainerSize(); }
         @Override public MCItemStack getItem(int index) { return container.getItem(index); }
         @Override public void setItem(int index, MCItemStack stack) { container.setItem(index, stack); }
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

   private static ContainerAccess menuAccess(MCAbstractContainerMenu menu) {
      return new ContainerAccess() {
         @Override public int size() { return menu.slots().size(); }
         @Override public MCItemStack getItem(int index) { return menu.slots().get(index).getItem(); }
         @Override public void setItem(int index, MCItemStack stack) {
            MCSlot slot = menu.slots().get(index);
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
      int size();
      MCItemStack getItem(int index);
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
      MLuaClassMethods.registerInstanceMethod(registration, "Container", "GetSlot", "Returns the live slot at a one-based index.");
      MLuaClassMethods.registerInstanceMethod(registration, "Container", "SetSlot", "Sets the item in a writable slot by one-based index.");
      MLuaClassMethods.registerInstanceMethod(registration, "Container", "GetTitle", "Returns this container's display title when available.");
      MLuaClassMethods.registerInstanceMethod(registration, "Container", "GetAll", "Returns all live slots in this container.");
      MLuaClassMethods.registerInstanceMethod(registration, "Inventory", "GetHotbar", "Returns the live slots for the nine hotbar positions.");
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
                                        Consumer<MCItemStack> setter, int index, boolean writable,
                                        MLuaRegistration registration) {
      synchronized (cache) {
         return cache.computeIfAbsent(rawIndex, ignored ->
            MLuaSlot.slotTable(stackSupplier, rawIndexSupplier, index, setter, writable, registration));
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
         throw new org.luaj.vm2.LuaError("[MLua] Slot index " + index + " is outside this container's valid range (1-" + size + ").");
      }
      return rawIndex;
   }

   private static void ensureWritable(boolean writable) {
      if (!writable) {
         throw new org.luaj.vm2.LuaError("[MLua] This container is read-only on the current side.");
      }
   }
}
