package dev.mlua.math;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.ThreeArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class MLuaVec4 {
   private static final Set<LuaTable> VALUES = Collections.newSetFromMap(
      Collections.synchronizedMap(new WeakHashMap<>()));

   private MLuaVec4() {
   }

   public static LuaTable vec4(double x, double y, double z, double w) {
      LuaTable table = new LuaTable();
      table.set("__type", "Vec4");
      table.set("X", x);
      table.set("Y", y);
      table.set("Z", z);
      table.set("W", w);
      table.set("x", x);
      table.set("y", y);
      table.set("z", z);
      table.set("w", w);
      double mag = Math.sqrt(x * x + y * y + z * z + w * w);
      table.set("Magnitude", mag);
      table.set("magnitude", mag);
      if (mag == 0.0) {
         table.set("Unit", vec4Bare(0.0, 0.0, 0.0, 0.0));
      } else {
         table.set("Unit", vec4Bare(x / mag, y / mag, z / mag, w / mag));
      }
      table.set("unit", table.get("Unit"));
      table.set("Dot", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue other) {
            LuaTable b = other.checktable();
            return LuaValue.valueOf(self.get("X").todouble() * b.get("X").todouble() + self.get("Y").todouble() * b.get("Y").todouble() + self.get("Z").todouble() * b.get("Z").todouble() + self.get("W").todouble() * b.get("W").todouble());
         }
      });
      table.set("dot", table.get("Dot"));
      table.set("Lerp", new ThreeArgFunction() {
         public LuaValue call(LuaValue self, LuaValue goal, LuaValue alpha) {
            LuaTable b = goal.checktable();
            double t = alpha.checkdouble();
            return MLuaVec4.vec4(MLuaVec4.lerp(self.get("X").todouble(), b.get("X").todouble(), t), MLuaVec4.lerp(self.get("Y").todouble(), b.get("Y").todouble(), t), MLuaVec4.lerp(self.get("Z").todouble(), b.get("Z").todouble(), t), MLuaVec4.lerp(self.get("W").todouble(), b.get("W").todouble(), t));
         }
      });
      table.set("lerp", table.get("Lerp"));
      table.set("Floor", new OneArgFunction() {
         public LuaValue call(LuaValue self) {
            return MLuaVec4.vec4(Math.floor(self.get("X").todouble()), Math.floor(self.get("Y").todouble()), Math.floor(self.get("Z").todouble()), Math.floor(self.get("W").todouble()));
         }
      });
      table.set("floor", table.get("Floor"));
      table.set("Ceil", new OneArgFunction() {
         public LuaValue call(LuaValue self) {
            return MLuaVec4.vec4(Math.ceil(self.get("X").todouble()), Math.ceil(self.get("Y").todouble()), Math.ceil(self.get("Z").todouble()), Math.ceil(self.get("W").todouble()));
         }
      });
      table.set("ceil", table.get("Ceil"));
      table.set("Abs", new OneArgFunction() {
         public LuaValue call(LuaValue self) {
            return MLuaVec4.vec4(Math.abs(self.get("X").todouble()), Math.abs(self.get("Y").todouble()), Math.abs(self.get("Z").todouble()), Math.abs(self.get("W").todouble()));
         }
      });
      table.set("abs", table.get("Abs"));
      table.set("Length", new OneArgFunction() {
         public LuaValue call(LuaValue self) {
            return LuaValue.valueOf(MLuaVec4.magnitude(self.checktable()));
         }
      });
      table.set("length", table.get("Length"));
      table.set("LengthSquared", new OneArgFunction() {
         public LuaValue call(LuaValue self) {
            LuaTable t = self.checktable();
            double x = t.get("X").todouble(), y = t.get("Y").todouble(), z = t.get("Z").todouble(), w = t.get("W").todouble();
            return LuaValue.valueOf(x * x + y * y + z * z + w * w);
         }
      });
      table.set("lengthSquared", table.get("LengthSquared"));
      table.set("Plus", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue other) {
            LuaTable a = self.checktable();
            LuaTable b = other.checktable();
            return MLuaVec4.vec4(a.get("X").todouble() + b.get("X").todouble(), a.get("Y").todouble() + b.get("Y").todouble(), a.get("Z").todouble() + b.get("Z").todouble(), a.get("W").todouble() + b.get("W").todouble());
         }
      });
      table.set("plus", table.get("Plus"));
      table.set("Minus", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue other) {
            LuaTable a = self.checktable();
            LuaTable b = other.checktable();
            return MLuaVec4.vec4(a.get("X").todouble() - b.get("X").todouble(), a.get("Y").todouble() - b.get("Y").todouble(), a.get("Z").todouble() - b.get("Z").todouble(), a.get("W").todouble() - b.get("W").todouble());
         }
      });
      table.set("minus", table.get("Minus"));
      table.set("Times", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue other) {
            LuaTable a = self.checktable();
            LuaTable b = other.checktable();
            return MLuaVec4.vec4(a.get("X").todouble() * b.get("X").todouble(), a.get("Y").todouble() * b.get("Y").todouble(), a.get("Z").todouble() * b.get("Z").todouble(), a.get("W").todouble() * b.get("W").todouble());
         }
      });
      table.set("times", table.get("Times"));
      table.set("DividedBy", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue other) {
            LuaTable a = self.checktable();
            LuaTable b = other.checktable();
            return MLuaVec4.vec4(a.get("X").todouble() / b.get("X").todouble(), a.get("Y").todouble() / b.get("Y").todouble(), a.get("Z").todouble() / b.get("Z").todouble(), a.get("W").todouble() / b.get("W").todouble());
         }
      });
      table.set("dividedBy", table.get("DividedBy"));
      table.set("Scaled", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue factor) {
            LuaTable a = self.checktable();
            double s = factor.checkdouble();
            return MLuaVec4.vec4(a.get("X").todouble() * s, a.get("Y").todouble() * s, a.get("Z").todouble() * s, a.get("W").todouble() * s);
         }
      });
      table.set("scaled", table.get("Scaled"));
      table.set("Normalized", new OneArgFunction() {
         public LuaValue call(LuaValue self) {
            LuaTable a = self.checktable();
            double m = Math.sqrt(a.get("X").todouble() * a.get("X").todouble() + a.get("Y").todouble() * a.get("Y").todouble() + a.get("Z").todouble() * a.get("Z").todouble() + a.get("W").todouble() * a.get("W").todouble());
            if (m == 0.0) return MLuaVec4.vec4(0.0, 0.0, 0.0, 0.0);
            return MLuaVec4.vec4(a.get("X").todouble() / m, a.get("Y").todouble() / m, a.get("Z").todouble() / m, a.get("W").todouble() / m);
         }
      });
      table.set("normalized", table.get("Normalized"));
      table.setmetatable(vec4Metatable());
      VALUES.add(table);
      return table.freeze();
   }

   private static LuaTable vec4Bare(double x, double y, double z, double w) {
      LuaTable table = new LuaTable();
      table.set("__type", "Vec4");
      table.set("X", x);
      table.set("Y", y);
      table.set("Z", z);
      table.set("W", w);
      table.set("x", x);
      table.set("y", y);
      table.set("z", z);
      table.set("w", w);
      double mag = Math.sqrt(x * x + y * y + z * z + w * w);
      table.set("Magnitude", mag);
      table.set("magnitude", mag);
      table.setmetatable(vec4Metatable());
      VALUES.add(table);
      return table.freeze();
   }

   public static boolean isVec4(LuaValue value) {
      return value.istable() && value.checktable().isfrozen() && VALUES.contains(value.checktable());
   }

   private static LuaTable vec4Metatable() {
      LuaTable metatable = new LuaTable();
      metatable.set(LuaValue.ADD, new TwoArgFunction() {
         public LuaValue call(LuaValue left, LuaValue right) {
            LuaTable a = left.checktable();
            LuaTable b = right.checktable();
            return MLuaVec4.vec4(a.get("X").todouble() + b.get("X").todouble(), a.get("Y").todouble() + b.get("Y").todouble(), a.get("Z").todouble() + b.get("Z").todouble(), a.get("W").todouble() + b.get("W").todouble());
         }
      });
      metatable.set(LuaValue.SUB, new TwoArgFunction() {
         public LuaValue call(LuaValue left, LuaValue right) {
            LuaTable a = left.checktable();
            LuaTable b = right.checktable();
            return MLuaVec4.vec4(a.get("X").todouble() - b.get("X").todouble(), a.get("Y").todouble() - b.get("Y").todouble(), a.get("Z").todouble() - b.get("Z").todouble(), a.get("W").todouble() - b.get("W").todouble());
         }
      });
      metatable.set(LuaValue.MUL, new TwoArgFunction() {
         public LuaValue call(LuaValue left, LuaValue right) {
            LuaTable b;
            double s;
            if (right.isnumber()) {
               b = left.checktable();
               s = right.todouble();
               return MLuaVec4.vec4(b.get("X").todouble() * s, b.get("Y").todouble() * s, b.get("Z").todouble() * s, b.get("W").todouble() * s);
            } else if (left.isnumber()) {
               b = right.checktable();
               s = left.todouble();
               return MLuaVec4.vec4(b.get("X").todouble() * s, b.get("Y").todouble() * s, b.get("Z").todouble() * s, b.get("W").todouble() * s);
            } else {
               throw new LuaError("Vec4 multiplication expects a number");
            }
         }
      });
      metatable.set(LuaValue.DIV, new TwoArgFunction() {
         public LuaValue call(LuaValue left, LuaValue right) {
            LuaTable a = left.checktable();
            double d = right.checkdouble();
            return MLuaVec4.vec4(a.get("X").todouble() / d, a.get("Y").todouble() / d, a.get("Z").todouble() / d, a.get("W").todouble() / d);
         }
      });
      metatable.set("__tostring", new OneArgFunction() {
         public LuaValue call(LuaValue value) {
            LuaTable t = value.checktable();
            String var10000 = t.get("X").tojstring();
            return LuaValue.valueOf(var10000 + ", " + t.get("Y").tojstring() + ", " + t.get("Z").tojstring() + ", " + t.get("W").tojstring());
         }
      });
      return metatable;
   }

   private static double magnitude(LuaTable t) {
      double x = t.get("X").todouble(), y = t.get("Y").todouble(), z = t.get("Z").todouble(), w = t.get("W").todouble();
      return Math.sqrt(x * x + y * y + z * z + w * w);
   }

   private static double lerp(double a, double b, double t) {
      return a + (b - a) * t;
   }
}
