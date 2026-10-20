package dev.mlua.minecraft.binding;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import dev.mlua.minecraft.MLuaResourceLocations;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

public final class MLuaTextureBindings {
   private MLuaTextureBindings() {
   }

   public static LuaTable textureGlobal() {
      LuaTable table = new LuaTable();
      table.set("new", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            LuaTable textures = new LuaTable();
            if (!args.arg(1).isnil()) {
               textures.set("All", args.checkjstring(1));
            }

            return textures;
         }
      });
      table.set("New", table.get("new"));
      table.set("Item", itemTextures());
      table.set("Block", blockTextures());
      table.set("Missing", LuaValue.valueOf("minecraft:missingno"));
      table.set("ItemPlaceholder", LuaValue.valueOf("mlua:item/placeholder"));
      table.set("BlockPlaceholder", LuaValue.valueOf("mlua:block/placeholder"));
      return table;
   }

   private static LuaTable itemTextures() {
      LuaTable table = new LuaTable();

      try {
         BuiltInRegistries.ITEM.keySet().forEach((id) -> {
            add(table, id, "item");
         });
      } catch (Throwable var2) {
      }

      return table;
   }

   private static LuaTable blockTextures() {
      LuaTable table = new LuaTable();

      try {
         BuiltInRegistries.BLOCK.keySet().forEach((id) -> {
            add(table, id, "block");
         });
      } catch (Throwable var2) {
      }

      return table;
   }

   private static void add(LuaTable root, ResourceLocation id, String folder) {
      String path = MLuaResourceLocations.path(id);
      String namespaceId = MLuaResourceLocations.namespace(id);
      String value = namespaceId + ":" + folder + "/" + path;
      String constant = pascalCase(path);
      String namespace = pascalCase(namespaceId);
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

      return result.length() == 0 ? id : result.toString();
   }
}
