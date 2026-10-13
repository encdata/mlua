package dev.mlua.minecraft.binding;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import dev.mlua.minecraft.MLuaResourceLocations;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

public final class MLuaRegistryCatalogs {
   private MLuaRegistryCatalogs() {
   }

   public static LuaTable items() {
      LuaTable table = new LuaTable();

      try {
         BuiltInRegistries.ITEM.keySet().forEach((id) -> {
            add(table, id);
         });
      } catch (Throwable var2) {
      }

      return table;
   }

   public static LuaTable blocks() {
      LuaTable table = new LuaTable();

      try {
         BuiltInRegistries.BLOCK.keySet().forEach((id) -> {
            add(table, id);
         });
      } catch (Throwable var2) {
      }

      return table;
   }

   private static void add(LuaTable root, ResourceLocation id) {
      String value = id.toString();
      String constant = pascalCase(MLuaResourceLocations.path(id));
      String namespace = pascalCase(MLuaResourceLocations.namespace(id));
      if (root.get(constant).isnil()) {
         root.set(constant, LuaValue.valueOf(value));
      }

      LuaValue namespaceValue = root.get(namespace);
      LuaTable namespaceTable;
      if (namespaceValue.istable()) {
         namespaceTable = namespaceValue.checktable();
      } else {
         namespaceTable = new LuaTable();
         root.set(namespace, namespaceTable);
      }

      namespaceTable.set(constant, LuaValue.valueOf(value));
   }

   private static String pascalCase(String id) {
      StringBuilder result = new StringBuilder();
      boolean upperNext = true;

      for(int i = 0; i < id.length(); ++i) {
         char c = id.charAt(i);
         if (c != '_' && c != '-' && c != '/' && c != '.') {
            if (upperNext) {
               result.append(Character.toUpperCase(c));
               upperNext = false;
            } else {
               result.append(Character.toLowerCase(c));
            }
         } else {
            upperNext = true;
         }
      }

      if (result.length() == 0) {
         return id.toUpperCase(Locale.ROOT);
      } else {
         return result.toString();
      }
   }
}
