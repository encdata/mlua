package dev.mlua.minecraft.binding;

import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.ArrayList;
import java.util.List;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.module.MLuaRegistration;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.ThreeArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;

public final class MLuaNbtWrapper {
   private MLuaNbtWrapper() {
   }

   public static LuaTable table(Supplier<CompoundTag> getter, Consumer<CompoundTag> setter) {
      return table(getter, setter, MLuaRuntime.get());
   }

   public static LuaTable table(Supplier<CompoundTag> getter, Consumer<CompoundTag> setter,
                                MLuaRegistration registration) {
      return table(getter, setter, List.of(), registration);
   }

   private static LuaTable table(Supplier<CompoundTag> getter, Consumer<CompoundTag> setter,
                                 List<Object> path, MLuaRegistration registration) {
      LuaTable table = new LuaTable();
      table.rawset("__nbt_proxy", LuaValue.TRUE);
      table.rawset("Get", new VarArgFunction() {
         @Override
         public Varargs invoke(Varargs args) {
            return getPathValue(getter, setter, append(path, keyObject(args.arg(2))), false, registration);
         }
      });
      table.rawset("Set", new VarArgFunction() {
         @Override
         public Varargs invoke(Varargs args) {
            CompoundTag tag = mutable(getter.get());
            setAtPath(tag, append(path, keyObject(args.arg(2))), args.arg(3));
            setter.accept(tag);
            return LuaValue.NONE;
         }
      });
      table.rawset("Remove", new VarArgFunction() {
         @Override
         public Varargs invoke(Varargs args) {
            CompoundTag tag = mutable(getter.get());
            removeAtPath(tag, append(path, keyObject(args.arg(2))));
            setter.accept(tag);
            return LuaValue.NONE;
         }
      });

      LuaTable meta = new LuaTable();
      meta.set("__index", new TwoArgFunction() {
         @Override
         public LuaValue call(LuaValue self, LuaValue key) {
            LuaValue raw = self.rawget(key);
            if (!raw.isnil()) {
               return raw;
            }
            if (!key.isstring() && !key.isnumber()) {
               return LuaValue.NIL;
            }
            return getPathValue(getter, setter, append(path, keyObject(key)), true, registration);
         }
      });
      meta.set("__newindex", new ThreeArgFunction() {
         @Override
         public LuaValue call(LuaValue self, LuaValue key, LuaValue value) {
            if (!key.isstring() && !key.isnumber()) {
               self.checktable().rawset(key, value);
               return LuaValue.NIL;
            }
            CompoundTag tag = mutable(getter.get());
            setAtPath(tag, append(path, keyObject(key)), value);
            setter.accept(tag);
            return LuaValue.NIL;
         }
      });
      meta.set("__tostring", new VarArgFunction() {
         @Override
         public Varargs invoke(Varargs args) {
            Tag tag = tagAtPath(getter.get(), path);
            return LuaValue.valueOf(tag == null ? "{}" : tag.toString());
         }
      });
      table.setmetatable(meta);
      if (registration == null || registration.classTable("Nbt") == null) {
         throw new IllegalStateException("Nbt class must be registered before creating NBT proxies");
      }
      registration.attachClassInstance(table, "Nbt", null);
      return table;
   }

   public static LuaValue get(CompoundTag tag, String key) {
      if (tag == null || !tag.contains(key)) {
         return LuaValue.NIL;
      }
      return toLua(tag.get(key));
   }

   public static void set(CompoundTag tag, String key, LuaValue value) {
      if (value.isnil()) {
         tag.remove(key);
      } else {
         tag.put(key, toTag(value));
      }
   }

   public static LuaValue toLua(Tag tag) {
      if (tag instanceof ByteTag byteTag) {
         return LuaValue.valueOf(byteTag.getAsByte() != 0);
      }
      if (tag instanceof net.minecraft.nbt.NumericTag numericTag) {
         double value = numericTag.getAsDouble();
         long integral = numericTag.getAsLong();
         return value == (double)integral ? LuaValue.valueOf(integral) : LuaValue.valueOf(value);
      }
      if (tag instanceof StringTag stringTag) {
         return LuaValue.valueOf(stringTag.getAsString());
      }
      if (tag instanceof CompoundTag compoundTag) {
         LuaTable table = new LuaTable();
         for (String key : compoundTag.getAllKeys()) {
            table.set(key, toLua(compoundTag.get(key)));
         }
         return table;
      }
      if (tag instanceof ListTag listTag) {
         LuaTable table = new LuaTable();
         for (int i = 0; i < listTag.size(); i++) {
            table.set(i + 1, toLua(listTag.get(i)));
         }
         return table;
      }
      return LuaValue.NIL;
   }

   public static Tag toTag(LuaValue value) {
      if (value.istable() && value.checktable().rawget("__nbt_proxy").toboolean()) {
         return toTagFromProxy(value.checktable());
      }
      if (value.isboolean()) {
         return ByteTag.valueOf(value.toboolean());
      }
      if (value.isnumber()) {
         double numeric = value.todouble();
         if (numeric == Math.floor(numeric) && !Double.isInfinite(numeric)) {
            return LongTag.valueOf((long)numeric);
         }
         return DoubleTag.valueOf(numeric);
      }
      if (value.isstring()) {
         return StringTag.valueOf(value.tojstring());
      }
      if (value.istable()) {
         LuaTable table = value.checktable();
         if (isArray(table)) {
            ListTag list = new ListTag();
            for (int i = 1; !table.get(i).isnil(); i++) {
               list.add(toTag(table.get(i)));
            }
            return list;
         }

         CompoundTag compound = new CompoundTag();
         LuaValue key = LuaValue.NIL;
         while (true) {
            Varargs next = table.next(key);
            key = next.arg1();
            if (key.isnil()) {
               break;
            }
            if (key.isstring()) {
               compound.put(key.tojstring(), toTag(next.arg(2)));
            }
         }
         return compound;
      }
      return StringTag.valueOf(value.tojstring());
   }

   private static CompoundTag mutable(CompoundTag tag) {
      return tag == null ? new CompoundTag() : tag;
   }

   private static boolean isArray(LuaTable table) {
      return !table.get(1).isnil();
   }

   private static LuaValue getPathValue(Supplier<CompoundTag> getter, Consumer<CompoundTag> setter,
                                        List<Object> path, boolean missingAsProxy,
                                        MLuaRegistration registration) {
      Tag tag = tagAtPath(getter.get(), path);
      if (tag == null) {
         return missingAsProxy ? table(getter, setter, path, registration) : LuaValue.NIL;
      }
      if (tag instanceof CompoundTag || tag instanceof ListTag) {
         return table(getter, setter, path, registration);
      }
      return toLua(tag);
   }

   private static Tag tagAtPath(CompoundTag root, List<Object> path) {
      if (root == null) {
         return null;
      }
      Tag current = root;
      for (Object segment : path) {
         if (current instanceof CompoundTag compound && segment instanceof String key) {
            current = compound.get(key);
         } else if (current instanceof ListTag list && segment instanceof Integer index) {
            int zeroBased = index - 1;
            current = zeroBased >= 0 && zeroBased < list.size() ? list.get(zeroBased) : null;
         } else {
            return null;
         }
         if (current == null) {
            return null;
         }
      }
      return current;
   }

   private static void setAtPath(CompoundTag root, List<Object> path, LuaValue value) {
      if (path.isEmpty()) {
         return;
      }
      Tag parent = parentTagForSet(root, path);
      Object leaf = path.get(path.size() - 1);
      if (parent instanceof CompoundTag compound && leaf instanceof String key) {
         set(compound, key, value);
      } else if (parent instanceof ListTag list && leaf instanceof Integer index) {
         int zeroBased = index - 1;
         if (zeroBased >= 0 && zeroBased < list.size()) {
            list.set(zeroBased, toTag(value));
         } else if (zeroBased == list.size()) {
            list.add(toTag(value));
         }
      }
   }

   private static Tag parentTagForSet(CompoundTag root, List<Object> path) {
      Tag current = root;
      for (int i = 0; i < path.size() - 1; i++) {
         Object segment = path.get(i);
         Object nextSegment = path.get(i + 1);
         if (current instanceof CompoundTag compound && segment instanceof String key) {
            Tag next = compound.get(key);
            if (next == null) {
               next = nextSegment instanceof Integer ? new ListTag() : new CompoundTag();
               compound.put(key, next);
            }
            current = next;
         } else if (current instanceof ListTag list && segment instanceof Integer index) {
            int zeroBased = index - 1;
            if (zeroBased < 0 || zeroBased >= list.size()) {
               return null;
            }
            current = list.get(zeroBased);
         } else {
            return null;
         }
      }
      return current;
   }

   private static void removeAtPath(CompoundTag root, List<Object> path) {
      if (path.isEmpty()) {
         return;
      }
      Tag parent = tagAtPath(root, path.subList(0, path.size() - 1));
      Object leaf = path.get(path.size() - 1);
      if (parent instanceof CompoundTag compound && leaf instanceof String key) {
         compound.remove(key);
      } else if (parent instanceof ListTag list && leaf instanceof Integer index) {
         int zeroBased = index - 1;
         if (zeroBased >= 0 && zeroBased < list.size()) {
            list.remove(zeroBased);
         }
      }
   }

   private static List<Object> append(List<Object> path, Object key) {
      ArrayList<Object> copy = new ArrayList<>(path);
      copy.add(key);
      return copy;
   }

   private static Object keyObject(LuaValue key) {
      return key.isnumber() ? key.checkint() : key.checkjstring();
   }

   private static Tag toTagFromProxy(LuaTable proxy) {
      LuaValue tostring = proxy.getmetatable().checktable().get("__tostring");
      return StringTag.valueOf(tostring.call(proxy).tojstring());
   }
}
