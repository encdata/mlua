package dev.mlua.minecraft.network;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.math.MLuaVector;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;

public final class MLuaNetworkSerializer {
   private static final int MAX_ARGUMENTS = 64;
   private static final int MAX_DEPTH = 16;
   private static final int MAX_STRING = 32767;

   private MLuaNetworkSerializer() {
   }

   public static List<MLuaNetworkValue> fromLuaArgs(Varargs args, int startIndex) {
      int count = args.narg() - startIndex + 1;
      if (count < 0) {
         count = 0;
      }

      if (count > 64) {
         throw new LuaError("[MLua] Network payload has too many arguments: " + count);
      } else {
         List<MLuaNetworkValue> values = new ArrayList(count);

         for(int i = startIndex; i <= args.narg(); ++i) {
            values.add(fromLua(args.arg(i), 0));
         }

         return values;
      }
   }

   public static LuaValue[] toLuaArray(List<MLuaNetworkValue> values) {
      LuaValue[] lua = new LuaValue[values.size()];

      for(int i = 0; i < values.size(); ++i) {
         lua[i] = toLua((MLuaNetworkValue)values.get(i));
      }

      return lua;
   }

   public static void encode(MLuaNetworkPacket packet, FriendlyByteBuf buf) {
      buf.writeUtf(packet.remoteName(), 128);
      buf.writeVarInt(packet.arguments().size());
      Iterator var2 = packet.arguments().iterator();

      while(var2.hasNext()) {
         MLuaNetworkValue value = (MLuaNetworkValue)var2.next();
         writeValue(value, buf, 0);
      }

   }

   public static MLuaNetworkPacket decode(FriendlyByteBuf buf) {
      String remoteName = buf.readUtf(128);
      int count = buf.readVarInt();
      if (count >= 0 && count <= 64) {
         List<MLuaNetworkValue> values = new ArrayList(count);

         for(int i = 0; i < count; ++i) {
            values.add(readValue(buf, 0));
         }

         return new MLuaNetworkPacket(remoteName, values);
      } else {
         throw new IllegalArgumentException("[MLua] Invalid network argument count: " + count);
      }
   }

   private static MLuaNetworkValue fromLua(LuaValue value, int depth) {
      if (depth > 16) {
         throw new LuaError("[MLua] Network payload table nesting is too deep");
      } else if (value.isnil()) {
         return new MLuaNetworkValue.Nil();
      } else if (value.isboolean()) {
         return new MLuaNetworkValue.Bool(value.toboolean());
      } else if (value.isnumber()) {
         return new MLuaNetworkValue.Num(value.todouble());
      } else if (value.isstring()) {
         return new MLuaNetworkValue.Str(value.tojstring());
      } else if (value.istable()) {
         LuaTable table = value.checktable();
         LuaValue type = table.get("__type");
         if (type.isstring()) {
            String typeStr = type.tojstring();
            if ("Vector3".equals(typeStr)) {
               if (!MLuaVector.isVector3(value)) throw new LuaError("[MLua] Network Vector3 values must be created by Vector3.new");
               return new MLuaNetworkValue.Vec3(table.get("X").todouble(), table.get("Y").todouble(), table.get("Z").todouble());
            }

            if ("CFrame".equals(typeStr)) {
               if (!MLuaCFrame.isCFrame(value)) throw new LuaError("[MLua] Network CFrame values must be created by CFrame.new");
               MLuaCFrame cf = MLuaCFrame.fromLua(value);
               return new MLuaNetworkValue.Cf(cf.x(), cf.y(), cf.z(), cf.r00(), cf.r01(), cf.r02(), cf.r10(), cf.r11(), cf.r12(), cf.r20(), cf.r21(), cf.r22());
            }
         }

         return tableFromLua(table, depth + 1);
      } else {
         throw new LuaError("[MLua] Unsupported network argument type: " + value.typename());
      }
   }

   private static MLuaNetworkValue tableFromLua(LuaTable table, int depth) {
      if (isArray(table)) {
         List<MLuaNetworkValue> values = new ArrayList();
         int i = 1;

         while(!table.get(i).isnil()) {
            values.add(fromLua(table.get(i++), depth));
         }

         return new MLuaNetworkValue.Arr(values);
      } else {
         Map<String, MLuaNetworkValue> values = new LinkedHashMap();
         LuaValue key = LuaValue.NIL;

         while(true) {
            Varargs next = table.next(key);
            key = next.arg1();
            if (key.isnil()) {
               return new MLuaNetworkValue.Obj(values);
            }

            if (!key.isstring()) {
               throw new LuaError("[MLua] Network table maps only support string keys");
            }

            values.put(key.tojstring(), fromLua(next.arg(2), depth));
         }
      }
   }

   private static boolean isArray(LuaTable table) {
      if (table.get(1).isnil()) {
         return false;
      } else {
         LuaValue key = LuaValue.NIL;
         int max = 0;
         int count = 0;

         while(true) {
            Varargs next = table.next(key);
            key = next.arg1();
            if (key.isnil()) {
               return max == count;
            }

            if (!key.isint()) {
               return false;
            }

            int index = key.toint();
            if (index < 1) {
               return false;
            }

            max = Math.max(max, index);
            ++count;
         }
      }
   }

   private static LuaValue toLua(MLuaNetworkValue value) {
      if (value instanceof MLuaNetworkValue.Nil) {
         return LuaValue.NIL;
      } else if (value instanceof MLuaNetworkValue.Bool) {
         MLuaNetworkValue.Bool bool = (MLuaNetworkValue.Bool)value;
         return LuaValue.valueOf(bool.value());
      } else if (value instanceof MLuaNetworkValue.Num) {
         MLuaNetworkValue.Num num = (MLuaNetworkValue.Num)value;
         return LuaValue.valueOf(num.value());
      } else if (value instanceof MLuaNetworkValue.Str) {
         MLuaNetworkValue.Str str = (MLuaNetworkValue.Str)value;
         return LuaValue.valueOf(str.value());
      } else if (value instanceof MLuaNetworkValue.Vec3) {
         MLuaNetworkValue.Vec3 vec = (MLuaNetworkValue.Vec3)value;
         return MLuaVector.vector3(vec.x(), vec.y(), vec.z());
      } else if (value instanceof MLuaNetworkValue.Cf) {
         MLuaNetworkValue.Cf cf = (MLuaNetworkValue.Cf)value;
         return (new MLuaCFrame(cf.x(), cf.y(), cf.z(), cf.r00(), cf.r01(), cf.r02(), cf.r10(), cf.r11(), cf.r12(), cf.r20(), cf.r21(), cf.r22())).toLua();
      } else {
         LuaTable table;
         if (!(value instanceof MLuaNetworkValue.Arr)) {
            if (value instanceof MLuaNetworkValue.Obj) {
               MLuaNetworkValue.Obj obj = (MLuaNetworkValue.Obj)value;
               table = new LuaTable();
               obj.values().forEach((key, item) -> {
                  table.set(key, toLua(item));
               });
               return table;
            } else {
               return LuaValue.NIL;
            }
         } else {
            MLuaNetworkValue.Arr arr = (MLuaNetworkValue.Arr)value;
            table = new LuaTable();

            for(int i = 0; i < arr.values().size(); ++i) {
               table.set(i + 1, toLua((MLuaNetworkValue)arr.values().get(i)));
            }

            return table;
         }
      }
   }

   public static void writePacketValue(MLuaNetworkValue value, FriendlyByteBuf buf) {
      writeValue(value, buf, 0);
   }

   public static MLuaNetworkValue readPacketValue(FriendlyByteBuf buf) {
      return readValue(buf, 0);
   }

   private static void writeValue(MLuaNetworkValue value, FriendlyByteBuf buf, int depth) {
      if (depth > 16) {
         throw new IllegalArgumentException("[MLua] Network payload table nesting is too deep");
      }

      if (value instanceof MLuaNetworkValue.Nil) {
         buf.writeByte(0);
      } else if (value instanceof MLuaNetworkValue.Bool) {
         MLuaNetworkValue.Bool bool = (MLuaNetworkValue.Bool)value;
         buf.writeByte(1);
         buf.writeBoolean(bool.value());
      } else if (value instanceof MLuaNetworkValue.Num) {
         MLuaNetworkValue.Num num = (MLuaNetworkValue.Num)value;
         buf.writeByte(2);
         buf.writeDouble(num.value());
      } else if (value instanceof MLuaNetworkValue.Str) {
         MLuaNetworkValue.Str str = (MLuaNetworkValue.Str)value;
         buf.writeByte(3);
         buf.writeUtf(str.value(), 32767);
      } else if (value instanceof MLuaNetworkValue.Arr) {
         MLuaNetworkValue.Arr arr = (MLuaNetworkValue.Arr)value;
         buf.writeByte(4);
         buf.writeVarInt(arr.values().size());
         arr.values().forEach((item) -> {
            writeValue(item, buf, depth + 1);
         });
      } else if (value instanceof MLuaNetworkValue.Obj) {
         MLuaNetworkValue.Obj obj = (MLuaNetworkValue.Obj)value;
         buf.writeByte(5);
         buf.writeVarInt(obj.values().size());
         obj.values().forEach((key, item) -> {
            buf.writeUtf(key, 32767);
            writeValue(item, buf, depth + 1);
         });
      } else if (value instanceof MLuaNetworkValue.Vec3) {
         MLuaNetworkValue.Vec3 vec = (MLuaNetworkValue.Vec3)value;
         buf.writeByte(6);
         buf.writeDouble(vec.x());
         buf.writeDouble(vec.y());
         buf.writeDouble(vec.z());
      } else {
         if (!(value instanceof MLuaNetworkValue.Cf)) {
            throw new IllegalArgumentException("[MLua] Unsupported network value");
         }

         MLuaNetworkValue.Cf cf = (MLuaNetworkValue.Cf)value;
         buf.writeByte(7);
         buf.writeDouble(cf.x());
         buf.writeDouble(cf.y());
         buf.writeDouble(cf.z());
         buf.writeDouble(cf.r00());
         buf.writeDouble(cf.r01());
         buf.writeDouble(cf.r02());
         buf.writeDouble(cf.r10());
         buf.writeDouble(cf.r11());
         buf.writeDouble(cf.r12());
         buf.writeDouble(cf.r20());
         buf.writeDouble(cf.r21());
         buf.writeDouble(cf.r22());
      }

   }

   private static MLuaNetworkValue readValue(FriendlyByteBuf buf, int depth) {
      if (depth > 16) {
         throw new IllegalArgumentException("[MLua] Network payload table nesting is too deep");
      } else {
         Object var10000;
         switch (buf.readByte()) {
            case 0:
               var10000 = new MLuaNetworkValue.Nil();
               break;
            case 1:
               var10000 = new MLuaNetworkValue.Bool(buf.readBoolean());
               break;
            case 2:
               var10000 = new MLuaNetworkValue.Num(buf.readDouble());
               break;
            case 3:
               var10000 = new MLuaNetworkValue.Str(buf.readUtf(32767));
               break;
            case 4:
               var10000 = readArray(buf, depth + 1);
               break;
            case 5:
               var10000 = readObject(buf, depth + 1);
               break;
            case 6:
               var10000 = new MLuaNetworkValue.Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
               break;
            case 7:
               var10000 = new MLuaNetworkValue.Cf(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble());
               break;
            default:
               throw new IllegalArgumentException("[MLua] Unknown network value type");
         }

         return (MLuaNetworkValue)var10000;
      }
   }

   private static MLuaNetworkValue readArray(FriendlyByteBuf buf, int depth) {
      int size = buf.readVarInt();
      List<MLuaNetworkValue> values = new ArrayList(size);

      for(int i = 0; i < size; ++i) {
         values.add(readValue(buf, depth));
      }

      return new MLuaNetworkValue.Arr(values);
   }

   private static MLuaNetworkValue readObject(FriendlyByteBuf buf, int depth) {
      int size = buf.readVarInt();
      Map<String, MLuaNetworkValue> values = new LinkedHashMap();

      for(int i = 0; i < size; ++i) {
         values.put(buf.readUtf(32767), readValue(buf, depth));
      }

      return new MLuaNetworkValue.Obj(values);
   }
}
