package dev.mlua.math;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.ThreeArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;

public final class MLuaVector {
   private MLuaVector() {
   }

   public static LuaTable vector3(double x, double y, double z) {
      LuaTable table = new LuaTable();
      table.set("__type", "Vector3");
      table.set("X", x);
      table.set("Y", y);
      table.set("Z", z);
      table.set("x", x);
      table.set("y", y);
      table.set("z", z);
      double mag = Math.sqrt(x * x + y * y + z * z);
      table.set("Magnitude", mag);
      table.set("magnitude", mag);
      if (mag == 0.0) {
         table.set("Unit", vector3Bare(0.0, 0.0, 0.0));
      } else {
         table.set("Unit", vector3Bare(x / mag, y / mag, z / mag));
      }

      table.set("unit", table.get("Unit"));
      table.set("Dot", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue other) {
            LuaTable b = other.checktable();
            return LuaValue.valueOf(self.get("X").todouble() * b.get("X").todouble() + self.get("Y").todouble() * b.get("Y").todouble() + self.get("Z").todouble() * b.get("Z").todouble());
         }
      });
      table.set("dot", table.get("Dot"));
      table.set("Cross", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue other) {
            LuaTable b = other.checktable();
            double ax = self.get("X").todouble();
            double ay = self.get("Y").todouble();
            double az = self.get("Z").todouble();
            double bx = b.get("X").todouble();
            double by = b.get("Y").todouble();
            double bz = b.get("Z").todouble();
            return MLuaVector.vector3(ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx);
         }
      });
      table.set("cross", table.get("Cross"));
      table.set("Lerp", new ThreeArgFunction() {
         public LuaValue call(LuaValue self, LuaValue goal, LuaValue alpha) {
            LuaTable b = goal.checktable();
            double t = alpha.checkdouble();
            return MLuaVector.vector3(MLuaVector.lerp(self.get("X").todouble(), b.get("X").todouble(), t), MLuaVector.lerp(self.get("Y").todouble(), b.get("Y").todouble(), t), MLuaVector.lerp(self.get("Z").todouble(), b.get("Z").todouble(), t));
         }
      });
      table.set("lerp", table.get("Lerp"));
      table.set("Distance", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue other) {
            double dx = self.get("X").todouble() - other.get("X").todouble();
            double dy = self.get("Y").todouble() - other.get("Y").todouble();
            double dz = self.get("Z").todouble() - other.get("Z").todouble();
            return LuaValue.valueOf(Math.sqrt(dx * dx + dy * dy + dz * dz));
         }
      });
      table.set("distance", table.get("Distance"));
      table.set("Floor", new OneArgFunction() {
         public LuaValue call(LuaValue self) {
            return MLuaVector.vector3(Math.floor(self.get("X").todouble()), Math.floor(self.get("Y").todouble()), Math.floor(self.get("Z").todouble()));
         }
      });
      table.set("floor", table.get("Floor"));
      table.set("Ceil", new OneArgFunction() {
         public LuaValue call(LuaValue self) {
            return MLuaVector.vector3(Math.ceil(self.get("X").todouble()), Math.ceil(self.get("Y").todouble()), Math.ceil(self.get("Z").todouble()));
         }
      });
      table.set("ceil", table.get("Ceil"));
      table.set("Abs", new OneArgFunction() {
         public LuaValue call(LuaValue self) {
            return MLuaVector.vector3(Math.abs(self.get("X").todouble()), Math.abs(self.get("Y").todouble()), Math.abs(self.get("Z").todouble()));
         }
      });
      table.set("abs", table.get("Abs"));
      table.setmetatable(vector3Metatable());
      return table;
   }

   public static LuaTable vector2(double x, double y) {
      LuaTable table = new LuaTable();
      table.set("__type", "Vector2");
      table.set("X", x);
      table.set("Y", y);
      table.set("x", x);
      table.set("y", y);
      double mag = Math.sqrt(x * x + y * y);
      table.set("Magnitude", mag);
      table.set("magnitude", mag);
      if (mag == 0.0) {
         table.set("Unit", vector2Bare(0.0, 0.0));
      } else {
         table.set("Unit", vector2Bare(x / mag, y / mag));
      }

      table.set("unit", table.get("Unit"));
      table.set("Dot", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue other) {
            LuaTable b = other.checktable();
            return LuaValue.valueOf(self.get("X").todouble() * b.get("X").todouble() + self.get("Y").todouble() * b.get("Y").todouble());
         }
      });
      table.set("Lerp", new ThreeArgFunction() {
         public LuaValue call(LuaValue self, LuaValue goal, LuaValue alpha) {
            LuaTable b = goal.checktable();
            double t = alpha.checkdouble();
            return MLuaVector.vector2(MLuaVector.lerp(self.get("X").todouble(), b.get("X").todouble(), t), MLuaVector.lerp(self.get("Y").todouble(), b.get("Y").todouble(), t));
         }
      });
      table.set("lerp", table.get("Lerp"));
      table.setmetatable(vector2Metatable());
      return table;
   }

   private static LuaTable vector3Bare(double x, double y, double z) {
      LuaTable table = new LuaTable();
      table.set("__type", "Vector3");
      table.set("X", x);
      table.set("Y", y);
      table.set("Z", z);
      table.set("x", x);
      table.set("y", y);
      table.set("z", z);
      double mag = Math.sqrt(x * x + y * y + z * z);
      table.set("Magnitude", mag);
      table.set("magnitude", mag);
      return table;
   }

   private static LuaTable vector2Bare(double x, double y) {
      LuaTable table = new LuaTable();
      table.set("__type", "Vector2");
      table.set("X", x);
      table.set("Y", y);
      table.set("x", x);
      table.set("y", y);
      double mag = Math.sqrt(x * x + y * y);
      table.set("Magnitude", mag);
      table.set("magnitude", mag);
      return table;
   }

   private static LuaTable vector3Metatable() {
      LuaTable metatable = new LuaTable();
      metatable.set(LuaValue.ADD, new TwoArgFunction() {
         public LuaValue call(LuaValue left, LuaValue right) {
            LuaTable a = left.checktable();
            LuaTable b = right.checktable();
            return MLuaVector.vector3(a.get("X").todouble() + b.get("X").todouble(), a.get("Y").todouble() + b.get("Y").todouble(), a.get("Z").todouble() + b.get("Z").todouble());
         }
      });
      metatable.set(LuaValue.SUB, new TwoArgFunction() {
         public LuaValue call(LuaValue left, LuaValue right) {
            LuaTable a = left.checktable();
            LuaTable b = right.checktable();
            return MLuaVector.vector3(a.get("X").todouble() - b.get("X").todouble(), a.get("Y").todouble() - b.get("Y").todouble(), a.get("Z").todouble() - b.get("Z").todouble());
         }
      });
      metatable.set(LuaValue.MUL, new TwoArgFunction() {
         public LuaValue call(LuaValue left, LuaValue right) {
            LuaTable b;
            double s;
            if (right.isnumber()) {
               b = left.checktable();
               s = right.todouble();
               return MLuaVector.vector3(b.get("X").todouble() * s, b.get("Y").todouble() * s, b.get("Z").todouble() * s);
            } else if (left.isnumber()) {
               b = right.checktable();
               s = left.todouble();
               return MLuaVector.vector3(b.get("X").todouble() * s, b.get("Y").todouble() * s, b.get("Z").todouble() * s);
            } else {
               throw new LuaError("Vector3 multiplication expects a number");
            }
         }
      });
      metatable.set(LuaValue.DIV, new TwoArgFunction() {
         public LuaValue call(LuaValue left, LuaValue right) {
            LuaTable a = left.checktable();
            double d = right.checkdouble();
            return MLuaVector.vector3(a.get("X").todouble() / d, a.get("Y").todouble() / d, a.get("Z").todouble() / d);
         }
      });
      metatable.set("__tostring", new OneArgFunction() {
         public LuaValue call(LuaValue value) {
            LuaTable t = value.checktable();
            String var10000 = t.get("X").tojstring();
            return LuaValue.valueOf(var10000 + ", " + t.get("Y").tojstring() + ", " + t.get("Z").tojstring());
         }
      });
      return metatable;
   }

   private static LuaTable vector2Metatable() {
      LuaTable metatable = new LuaTable();
      metatable.set(LuaValue.ADD, new TwoArgFunction() {
         public LuaValue call(LuaValue left, LuaValue right) {
            LuaTable a = left.checktable();
            LuaTable b = right.checktable();
            return MLuaVector.vector2(a.get("X").todouble() + b.get("X").todouble(), a.get("Y").todouble() + b.get("Y").todouble());
         }
      });
      metatable.set(LuaValue.SUB, new TwoArgFunction() {
         public LuaValue call(LuaValue left, LuaValue right) {
            LuaTable a = left.checktable();
            LuaTable b = right.checktable();
            return MLuaVector.vector2(a.get("X").todouble() - b.get("X").todouble(), a.get("Y").todouble() - b.get("Y").todouble());
         }
      });
      metatable.set(LuaValue.MUL, new TwoArgFunction() {
         public LuaValue call(LuaValue left, LuaValue right) {
            LuaTable b;
            double s;
            if (right.isnumber()) {
               b = left.checktable();
               s = right.todouble();
               return MLuaVector.vector2(b.get("X").todouble() * s, b.get("Y").todouble() * s);
            } else if (left.isnumber()) {
               b = right.checktable();
               s = left.todouble();
               return MLuaVector.vector2(b.get("X").todouble() * s, b.get("Y").todouble() * s);
            } else {
               throw new LuaError("Vector2 multiplication expects a number");
            }
         }
      });
      metatable.set(LuaValue.DIV, new TwoArgFunction() {
         public LuaValue call(LuaValue left, LuaValue right) {
            LuaTable a = left.checktable();
            double d = right.checkdouble();
            return MLuaVector.vector2(a.get("X").todouble() / d, a.get("Y").todouble() / d);
         }
      });
      metatable.set("__tostring", new OneArgFunction() {
         public LuaValue call(LuaValue value) {
            LuaTable t = value.checktable();
            String var10000 = t.get("X").tojstring();
            return LuaValue.valueOf(var10000 + ", " + t.get("Y").tojstring());
         }
      });
      return metatable;
   }

   private static double lerp(double a, double b, double t) {
      return a + (b - a) * t;
   }
}
