package dev.mlua.minecraft.binding;

import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.minecraft.scripted.MLuaBehaviorRegistry;
import dev.mlua.minecraft.scripted.MLuaBehaviorRegistry.ContainerDefinition;
import java.util.List;
import java.util.function.Function;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

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
      register(registration, "GetAllowedItems", "Returns a sorted array of allowed item ids; an empty array allows every item.", type -> {
         LuaTable result = new LuaTable();
         List<String> items = type.definition().allowedItems().stream().sorted().toList();
         for (int index = 0; index < items.size(); index++) result.set(index + 1, LuaValue.valueOf(items.get(index)));
         return result;
      });
      register(registration, "AllowsAllItems", "Returns whether this container accepts every item.",
         type -> LuaValue.valueOf(type.definition().allowedItems().isEmpty()));
      registration.registerClassFunction("ContainerType", "AllowsItem", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            State type = state(args.arg1());
            LuaValue candidate = args.arg(2);
            if (registration.isInstanceOf(candidate, "Item")) candidate = candidate.get("Id");
            if (!candidate.isstring()) {
               throw new org.luaj.vm2.LuaError("[MLua] ContainerType:AllowsItem expects an item id or Item instance.");
            }
            String requested = candidate.tojstring().trim();
            if (!requested.contains(":")) {
               requested = type.id().substring(0, type.id().indexOf(':')) + ":" + requested;
            }
            net.minecraft.resources.ResourceLocation id = dev.mlua.minecraft.MLuaResourceLocations.tryParseOrNull(requested);
            if (id == null) throw new org.luaj.vm2.LuaError("[MLua] ContainerType:AllowsItem received an invalid item id: " + requested);
            return LuaValue.valueOf(type.definition().allowedItems().isEmpty()
               || type.definition().allowedItems().contains(id.toString()));
         }
      }, "Checks whether an item id or live Item instance is allowed by this container type. An unqualified id uses the container type's namespace.");
      MLuaClassMethods.registerInstanceProperty(registration, "ContainerType", "Id", "GetId", null,
         "string", "shared:Shared", "This container type's full namespaced identifier.");
      MLuaClassMethods.registerInstanceProperty(registration, "ContainerType", "Rows", "GetRows", null,
         "number", "shared:Shared", "The number of rows in this container layout.");
      MLuaClassMethods.registerInstanceProperty(registration, "ContainerType", "SlotCount", "GetSlotCount", null,
         "number", "shared:Shared", "The number of slots in this container layout.");
      MLuaClassMethods.registerInstanceProperty(registration, "ContainerType", "Title", "GetTitle", null,
         "string", "shared:Shared", "The title used when this container type is opened.");
      MLuaClassMethods.registerInstanceProperty(registration, "ContainerType", "AllowedItems", "GetAllowedItems", null,
         "string[]", "shared:Shared", "Allowed item ids; an empty list allows every item.");
      MLuaClassMethods.registerInstanceProperty(registration, "ContainerType", "AcceptsAllItems", "AllowsAllItems", null,
         "boolean", "shared:Shared", "Whether this container accepts every item.");
   }

   public static LuaTable create(MLuaRegistration registration, String id, ContainerDefinition definition) {
      LuaTable instance = registration.createClassInstance("ContainerType");
      instance.rawset(STATE, LuaValue.userdataOf(new State(id, definition)));
      return instance;
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
      if (!value.istable()) throw new org.luaj.vm2.LuaError("[MLua] Expected a ContainerType instance.");
      LuaValue raw = value.checktable().rawget(STATE);
      Object state = raw.isuserdata() ? raw.touserdata() : null;
      if (!(state instanceof State type)) throw new org.luaj.vm2.LuaError("[MLua] Invalid ContainerType instance.");
      return type;
   }

   private record State(String id, ContainerDefinition definition) { }
}
