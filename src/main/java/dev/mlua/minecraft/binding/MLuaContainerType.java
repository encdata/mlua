package dev.mlua.minecraft.binding;

import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.core.MLuaLogger;
import dev.mlua.minecraft.scripted.MLuaBehaviorRegistry;
import dev.mlua.minecraft.scripted.MLuaBehaviorRegistry.ContainerDefinition;
import java.util.List;
import java.util.function.Function;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.LuaError;

/** Lua instance for an immutable, addon-registered container type. */
public final class MLuaContainerType {
   private static final String STATE = "__mlua_container_type_state";

   private MLuaContainerType() { }

   public static void registerClass(MLuaRegistration registration) {
      registration.registerClass("ContainerType", registration.requireInstanceBaseClassName(),
         "An immutable registered container layout with its namespaced id, title, row count, and item rules.");
      register(registration, "GetId", "Returns this container type's full namespaced identifier.",
         type -> LuaValue.valueOf(type.id()));
      register(registration, "GetRows", "Returns the number of rows in this container layout.",
         type -> LuaValue.valueOf(type.definition().rows()));
      register(registration, "GetSlotCount", "Returns the number of slots in this container layout.",
         type -> LuaValue.valueOf(type.definition().rows() * 9));
      register(registration, "GetTitle", "Returns the title used when this container type is opened.",
         type -> LuaValue.valueOf(type.definition().title()));
      register(registration, "GetAllowedItems", "Returns the sorted container-wide item allowlist; an empty array allows every item unless a slot overrides it.", type -> {
         LuaTable result = new LuaTable();
         List<String> items = type.definition().allowedItems().stream().sorted().toList();
         for (int index = 0; index < items.size(); index++) result.set(index + 1, LuaValue.valueOf(items.get(index)));
         return result;
      });
      registration.registerClassFunction("ContainerType", "GetAllowedItemsForSlot", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            State type = state(args.arg1());
            int slot = slotIndex(args.arg(2), type, "ContainerType:GetAllowedItemsForSlot");
            java.util.Set<String> allowed = type.definition().slotAllowedItems().getOrDefault(
               slot, type.definition().allowedItems());
            LuaTable result = new LuaTable();
            List<String> items = allowed.stream().sorted().toList();
            for (int index = 0; index < items.size(); index++) result.set(index + 1, items.get(index));
            return result;
         }
      }, "Returns the effective sorted item allowlist for one one-based slot; an empty list means all items are allowed.");
      register(registration, "AllowsAllItems", "Returns whether this container accepts every item.",
         type -> LuaValue.valueOf(acceptsEveryItem(type.definition())));
      registration.registerClassFunction("ContainerType", "AllowsItem", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            State type = state(args.arg1());
            String requested = itemId(args.arg(2), type, registration, "ContainerType:AllowsItem");
            return LuaValue.valueOf(type.definition().allowedItems().isEmpty()
               || type.definition().allowedItems().contains(requested));
         }
      }, "Checks whether an item id or live Item instance is allowed by the container-wide rule. An unqualified id uses the container type's namespace; use AllowsItemInSlot for slot-specific rules.");
      registration.registerClassFunction("ContainerType", "AllowsItemInSlot", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            try {
               State type = state(args.arg1());
               int slot = slotIndex(args.arg(2), type, "ContainerType:AllowsItemInSlot");
               String item = itemId(args.arg(3), type, registration, "ContainerType:AllowsItemInSlot");
               java.util.Set<String> allowed = type.definition().slotAllowedItems().getOrDefault(
                  slot, type.definition().allowedItems());
               return LuaValue.valueOf(allowed.isEmpty() || allowed.contains(item));
            } catch (RuntimeException failure) {
               MLuaLogger.LOGGER.error("[MLua] ContainerType:AllowsItemInSlot failed while checking a registered slot rule", failure);
               throw failure;
            }
         }
      }, "Checks whether an item id or live Item instance is allowed in one one-based slot, using the container-wide allowlist when that slot has no override.");
      MLuaClassMethods.registerInstanceProperty(registration, "ContainerType", "Id", "GetId", null,
         "string", "shared:Shared", "This container type's full namespaced identifier.");
      MLuaClassMethods.registerInstanceProperty(registration, "ContainerType", "Rows", "GetRows", null,
         "number", "shared:Shared", "The number of rows in this container layout.");
      MLuaClassMethods.registerInstanceProperty(registration, "ContainerType", "SlotCount", "GetSlotCount", null,
         "number", "shared:Shared", "The number of slots in this container layout.");
      MLuaClassMethods.registerInstanceProperty(registration, "ContainerType", "Title", "GetTitle", null,
         "string", "shared:Shared", "The title used when this container type is opened.");
      MLuaClassMethods.registerInstanceProperty(registration, "ContainerType", "AllowedItems", "GetAllowedItems", null,
         "string[]", "shared:Shared", "Container-wide allowed item ids; an empty list allows every item unless a slot overrides it.");
      MLuaClassMethods.registerInstanceProperty(registration, "ContainerType", "AcceptsAllItems", "AllowsAllItems", null,
         "boolean", "shared:Shared", "Whether this container accepts every item.");
   }

   public static LuaTable create(MLuaRegistration registration, String id, ContainerDefinition definition) {
      LuaTable instance = registration.createClassInstance("ContainerType");
      instance.rawset(STATE, LuaValue.userdataOf(new State(id, definition)));
      return instance;
   }

   /** Returns whether a value was created by this binding, without relying on class-name metadata. */
   public static boolean isInstance(LuaValue value) {
      return value != null && value.istable() && !value.checktable().rawget(STATE).isnil();
   }

   /** Returns the registered identifier carried by a live ContainerType instance. */
   public static String id(LuaValue value) {
      return state(value).id();
   }

   private static void register(MLuaRegistration registration, String name, String description,
                                Function<State, LuaValue> getter) {
      registration.registerClassFunction("ContainerType", name, new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            return getter.apply(state(args.arg1()));
         }
      }, description);
   }

   private static State state(LuaValue value) {
      if (!value.istable()) throw new LuaError("[MLua] Expected a ContainerType instance.");
      LuaValue raw = value.checktable().rawget(STATE);
      Object state = raw.isuserdata() ? raw.touserdata() : null;
      if (!(state instanceof State type)) throw new LuaError("[MLua] Invalid ContainerType instance.");
      return type;
   }

   private static int slotIndex(LuaValue value, State type, String method) {
      if (value.type() != LuaValue.TNUMBER) {
         throw new LuaError("[MLua] " + method + " expects a whole-number slot index.");
      }
      double index = value.todouble();
      int maximum = type.definition().rows() * 9;
      if (!Double.isFinite(index) || index != Math.rint(index) || index < 1 || index > maximum) {
         throw new LuaError("[MLua] " + method + " slot must be from 1 to " + maximum + ".");
      }
      return (int)index;
   }

   private static String itemId(LuaValue candidate, State type, MLuaRegistration registration, String method) {
      if (registration.isInstanceOf(candidate, "Item")) candidate = candidate.get("Id");
      if (candidate.type() != LuaValue.TSTRING) {
         throw new LuaError("[MLua] " + method + " expects an item id or Item instance.");
      }
      String requested = candidate.tojstring().trim();
      if (!requested.contains(":")) requested = type.id().substring(0, type.id().indexOf(':')) + ":" + requested;
      int separator = requested.indexOf(':');
      if (separator <= 0 || separator == requested.length() - 1
            || requested.indexOf(':', separator + 1) >= 0
            || !requested.substring(0, separator).matches("[a-z0-9_.-]+")
            || !requested.substring(separator + 1).matches("[a-z0-9/._-]+")) {
         throw new LuaError("[MLua] " + method + " received an invalid item id: " + requested);
      }
      // Container definitions may refer to addon items registered in the same
      // registry phase, before the vanilla item registry has completed binding.
      // Validate identifier syntax here; membership is resolved when inventory
      // slots later receive a live ItemStack.
      return requested;
   }

   private static boolean acceptsEveryItem(MLuaBehaviorRegistry.ContainerDefinition definition) {
      for (int slot = 1; slot <= definition.rows() * 9; slot++) {
         java.util.Set<String> allowed = definition.slotAllowedItems().get(slot);
         if (allowed == null) allowed = definition.allowedItems();
         if (!allowed.isEmpty()) return false;
      }
      return true;
   }

   private record State(String id, ContainerDefinition definition) { }
}
