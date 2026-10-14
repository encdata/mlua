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

   private static final LuaTable VECTOR3_METATABLE = vector3Metatable().freeze();
   private static final LuaTable VECTOR2_METATABLE = vector2Metatable().freeze();

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
      table.setmetatable(VECTOR3_METATABLE);
      return table.freeze();
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
      table.setmetatable(VECTOR2_METATABLE);
      return table.freeze();
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
      table.setmetatable(VECTOR3_METATABLE);
      return table.freeze();
   }

   private static LuaTable checkVector3(LuaValue value) {
      if (!value.istable() || !"Vector3".equals(value.get("__type").optjstring(null)) || !value.checktable().isfrozen() || value.getmetatable() != VECTOR3_METATABLE) {
         throw new LuaError("Vector3 operations require values created by Vector3.new");
      }
      return value.checktable();
   }

   public static double[] components3(LuaValue value) {
      LuaTable vector = checkVector3(value);
      return new double[]{vector.get("X").checkdouble(), vector.get("Y").checkdouble(), vector.get("Z").checkdouble()};
   }

   public static boolean isVector3(LuaValue value) {
      if (!value.istable() || !value.checktable().isfrozen() || value.getmetatable() != VECTOR3_METATABLE) {
         return false;
      }
      return "Vector3".equals(value.get("__type").optjstring(null));
   }

   private static LuaTable checkVector2(LuaValue value) {
      if (!value.istable() || !"Vector2".equals(value.get("__type").optjstring(null)) || !value.checktable().isfrozen() || value.getmetatable() != VECTOR2_METATABLE) {
         throw new LuaError("Vector2 operations require values created by Vector2.new");
      }
      return value.checktable();
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
      table.setmetatable(VECTOR2_METATABLE);
      return table.freeze();
   }

   private static LuaTable vector3Metatable() {
      LuaTable metatable = new LuaTable();
      LuaTable methods = new LuaTable();
      methods.set("Dot", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue other) {
            LuaTable a = checkVector3(self);
            LuaTable b = checkVector3(other);
            return LuaValue.valueOf(a.get("X").todouble() * b.get("X").todouble() + a.get("Y").todouble() * b.get("Y").todouble() + a.get("Z").todouble() * b.get("Z").todouble());
         }
      });
      methods.set("dot", methods.get("Dot"));
      methods.set("Cross", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue other) {
            LuaTable a = checkVector3(self);
            LuaTable b = checkVector3(other);
            double ax = a.get("X").todouble();
            double ay = a.get("Y").todouble();
            double az = a.get("Z").todouble();
            double bx = b.get("X").todouble();
            double by = b.get("Y").todouble();
            double bz = b.get("Z").todouble();
            return MLuaVector.vector3(ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx);
         }
      });
      methods.set("cross", methods.get("Cross"));
      methods.set("Lerp", new ThreeArgFunction() {
         public LuaValue call(LuaValue self, LuaValue goal, LuaValue alpha) {
            LuaTable a = checkVector3(self);
            LuaTable b = checkVector3(goal);
            double t = alpha.checkdouble();
            return MLuaVector.vector3(MLuaVector.lerp(a.get("X").todouble(), b.get("X").todouble(), t), MLuaVector.lerp(a.get("Y").todouble(), b.get("Y").todouble(), t), MLuaVector.lerp(a.get("Z").todouble(), b.get("Z").todouble(), t));
         }
      });
      methods.set("lerp", methods.get("Lerp"));
      methods.set("Distance", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue other) {
            LuaTable a = checkVector3(self);
            LuaTable b = checkVector3(other);
            double dx = a.get("X").todouble() - b.get("X").todouble();
            double dy = a.get("Y").todouble() - b.get("Y").todouble();
            double dz = a.get("Z").todouble() - b.get("Z").todouble();
            return LuaValue.valueOf(Math.sqrt(dx * dx + dy * dy + dz * dz));
         }
      });
      methods.set("distance", methods.get("Distance"));
      methods.set("Floor", new OneArgFunction() {
         public LuaValue call(LuaValue self) {
            LuaTable a = checkVector3(self);
            return MLuaVector.vector3(Math.floor(a.get("X").todouble()), Math.floor(a.get("Y").todouble()), Math.floor(a.get("Z").todouble()));
         }
      });
      methods.set("floor", methods.get("Floor"));
      methods.set("Ceil", new OneArgFunction() {
         public LuaValue call(LuaValue self) {
            LuaTable a = checkVector3(self);
            return MLuaVector.vector3(Math.ceil(a.get("X").todouble()), Math.ceil(a.get("Y").todouble()), Math.ceil(a.get("Z").todouble()));
         }
      });
      methods.set("ceil", methods.get("Ceil"));
      methods.set("Abs", new OneArgFunction() {
         public LuaValue call(LuaValue self) {
            LuaTable a = checkVector3(self);
            return MLuaVector.vector3(Math.abs(a.get("X").todouble()), Math.abs(a.get("Y").todouble()), Math.abs(a.get("Z").todouble()));
         }
      });
      methods.set("abs", methods.get("Abs"));
      metatable.set("__index", methods.freeze());
      metatable.set(LuaValue.ADD, new TwoArgFunction() {
         public LuaValue call(LuaValue left, LuaValue right) {
            LuaTable a = checkVector3(left);
            LuaTable b = checkVector3(right);
            return MLuaVector.vector3(a.get("X").todouble() + b.get("X").todouble(), a.get("Y").todouble() + b.get("Y").todouble(), a.get("Z").todouble() + b.get("Z").todouble());
         }
      });
      metatable.set(LuaValue.SUB, new TwoArgFunction() {
         public LuaValue call(LuaValue left, LuaValue right) {
            LuaTable a = checkVector3(left);
            LuaTable b = checkVector3(right);
            return MLuaVector.vector3(a.get("X").todouble() - b.get("X").todouble(), a.get("Y").todouble() - b.get("Y").todouble(), a.get("Z").todouble() - b.get("Z").todouble());
         }
      });
      metatable.set(LuaValue.MUL, new TwoArgFunction() {
         public LuaValue call(LuaValue left, LuaValue right) {
            LuaTable b;
            double s;
            if (right.isnumber()) {
               b = checkVector3(left);
               s = right.todouble();
               return MLuaVector.vector3(b.get("X").todouble() * s, b.get("Y").todouble() * s, b.get("Z").todouble() * s);
            } else if (left.isnumber()) {
               b = checkVector3(right);
               s = left.todouble();
               return MLuaVector.vector3(b.get("X").todouble() * s, b.get("Y").todouble() * s, b.get("Z").todouble() * s);
            } else {
               throw new LuaError("Vector3 multiplication expects a number");
            }
         }
      });
      metatable.set(LuaValue.DIV, new TwoArgFunction() {
         public LuaValue call(LuaValue left, LuaValue right) {
            LuaTable a = checkVector3(left);
            double d = right.checkdouble();
            return MLuaVector.vector3(a.get("X").todouble() / d, a.get("Y").todouble() / d, a.get("Z").todouble() / d);
         }
      });
      metatable.set("__tostring", new OneArgFunction() {
         public LuaValue call(LuaValue value) {
            LuaTable t = checkVector3(value);
            String var10000 = t.get("X").tojstring();
            return LuaValue.valueOf(var10000 + ", " + t.get("Y").tojstring() + ", " + t.get("Z").tojstring());
         }
      });
      return metatable;
   }

   private static LuaTable vector2Metatable() {
      LuaTable metatable = new LuaTable();
      LuaTable methods = new LuaTable();
      methods.set("Dot", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue other) {
            LuaTable a = checkVector2(self);
            LuaTable b = checkVector2(other);
            return LuaValue.valueOf(a.get("X").todouble() * b.get("X").todouble() + a.get("Y").todouble() * b.get("Y").todouble());
         }
      });
      methods.set("Lerp", new ThreeArgFunction() {
         public LuaValue call(LuaValue self, LuaValue goal, LuaValue alpha) {
            LuaTable a = checkVector2(self);
            LuaTable b = checkVector2(goal);
            double t = alpha.checkdouble();
            return MLuaVector.vector2(MLuaVector.lerp(a.get("X").todouble(), b.get("X").todouble(), t), MLuaVector.lerp(a.get("Y").todouble(), b.get("Y").todouble(), t));
         }
      });
      methods.set("lerp", methods.get("Lerp"));
      metatable.set("__index", methods.freeze());
      metatable.set(LuaValue.ADD, new TwoArgFunction() {
         public LuaValue call(LuaValue left, LuaValue right) {
            LuaTable a = checkVector2(left);
            LuaTable b = checkVector2(right);
            return MLuaVector.vector2(a.get("X").todouble() + b.get("X").todouble(), a.get("Y").todouble() + b.get("Y").todouble());
         }
      });
      metatable.set(LuaValue.SUB, new TwoArgFunction() {
         public LuaValue call(LuaValue left, LuaValue right) {
            LuaTable a = checkVector2(left);
            LuaTable b = checkVector2(right);
            return MLuaVector.vector2(a.get("X").todouble() - b.get("X").todouble(), a.get("Y").todouble() - b.get("Y").todouble());
         }
      });
      metatable.set(LuaValue.MUL, new TwoArgFunction() {
         public LuaValue call(LuaValue left, LuaValue right) {
            LuaTable b;
            double s;
            if (right.isnumber()) {
               b = checkVector2(left);
               s = right.todouble();
               return MLuaVector.vector2(b.get("X").todouble() * s, b.get("Y").todouble() * s);
            } else if (left.isnumber()) {
               b = checkVector2(right);
               s = left.todouble();
               return MLuaVector.vector2(b.get("X").todouble() * s, b.get("Y").todouble() * s);
            } else {
               throw new LuaError("Vector2 multiplication expects a number");
            }
         }
      });
      metatable.set(LuaValue.DIV, new TwoArgFunction() {
         public LuaValue call(LuaValue left, LuaValue right) {
            LuaTable a = checkVector2(left);
            double d = right.checkdouble();
            return MLuaVector.vector2(a.get("X").todouble() / d, a.get("Y").todouble() / d);
         }
      });
      metatable.set("__tostring", new OneArgFunction() {
         public LuaValue call(LuaValue value) {
            LuaTable t = checkVector2(value);
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
