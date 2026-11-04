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

public final class MLuaVector {
   private MLuaVector() {
   }

   private static final LuaTable VECTOR3_METATABLE = vector3Metatable().freeze();
   private static final LuaTable VECTOR2_METATABLE = vector2Metatable().freeze();
   private static final Set<LuaTable> VECTOR3_VALUES = Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<>()));
   private static final Set<LuaTable> VECTOR2_VALUES = Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<>()));

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
      table.set("MagnitudeSquared", x * x + y * y + z * z);
      table.set("magnitudeSquared", x * x + y * y + z * z);
      if (mag == 0.0) {
         table.set("Unit", vector3Bare(0.0, 0.0, 0.0));
      } else {
         table.set("Unit", vector3Bare(x / mag, y / mag, z / mag));
      }

      table.set("unit", table.get("Unit"));
      table.setmetatable(VECTOR3_METATABLE);
      VECTOR3_VALUES.add(table);
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
      table.set("MagnitudeSquared", x * x + y * y);
      table.set("magnitudeSquared", x * x + y * y);
      if (mag == 0.0) {
         table.set("Unit", vector2Bare(0.0, 0.0));
      } else {
         table.set("Unit", vector2Bare(x / mag, y / mag));
      }

      table.set("unit", table.get("Unit"));
      table.setmetatable(VECTOR2_METATABLE);
      VECTOR2_VALUES.add(table);
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
      table.set("MagnitudeSquared", x * x + y * y + z * z);
      table.set("magnitudeSquared", x * x + y * y + z * z);
      table.setmetatable(VECTOR3_METATABLE);
      VECTOR3_VALUES.add(table);
      return table.freeze();
   }

   public static boolean isVector3(LuaValue value) {
      return value.istable() && value.checktable().isfrozen()
         && VECTOR3_VALUES.contains(value.checktable());
   }

   public static boolean isVector2(LuaValue value) {
      return value.istable() && value.checktable().isfrozen()
         && VECTOR2_VALUES.contains(value.checktable());
   }

   public static double[] components2(LuaValue value) {
      LuaTable vector = checkVector2(value);
      return new double[] {vector.get("X").checkdouble(), vector.get("Y").checkdouble()};
   }

   public static double[] components3(LuaValue value) {
      LuaTable vector = checkVector3(value);
      return new double[] {vector.get("X").checkdouble(), vector.get("Y").checkdouble(), vector.get("Z").checkdouble()};
   }

   private static LuaTable checkVector3(LuaValue value) {
      if (!isVector3(value)) {
         throw new LuaError("Vector3 operations require values created by Vector3.new");
      }
      return value.checktable();
   }

   private static LuaTable checkVector2(LuaValue value) {
      if (!isVector2(value)) {
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
      table.set("MagnitudeSquared", x * x + y * y);
      table.set("magnitudeSquared", x * x + y * y);
      table.setmetatable(VECTOR2_METATABLE);
      VECTOR2_VALUES.add(table);
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
      methods.set("Angle", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue other) {
            LuaTable a = checkVector3(self);
            LuaTable b = checkVector3(other);
            double ax = a.get("X").todouble();
            double ay = a.get("Y").todouble();
            double az = a.get("Z").todouble();
            double bx = b.get("X").todouble();
            double by = b.get("Y").todouble();
            double bz = b.get("Z").todouble();
            double denominator = Math.sqrt((ax * ax + ay * ay + az * az) * (bx * bx + by * by + bz * bz));
            if (denominator == 0.0) throw new LuaError("Vector3:Angle requires non-zero vectors");
            double cosine = (ax * bx + ay * by + az * bz) / denominator;
            return LuaValue.valueOf(Math.acos(Math.max(-1.0, Math.min(1.0, cosine))));
         }
      });
      methods.set("angle", methods.get("Angle"));
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
      methods.set("ProjectOn", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue axis) {
            LuaTable value = checkVector3(self);
            LuaTable target = checkVector3(axis);
            double x = target.get("X").todouble();
            double y = target.get("Y").todouble();
            double z = target.get("Z").todouble();
            double lengthSquared = x * x + y * y + z * z;
            if (lengthSquared == 0.0) return MLuaVector.vector3(0.0, 0.0, 0.0);
            double scale = (value.get("X").todouble() * x + value.get("Y").todouble() * y + value.get("Z").todouble() * z) / lengthSquared;
            return MLuaVector.vector3(x * scale, y * scale, z * scale);
         }
      });
      methods.set("projectOn", methods.get("ProjectOn"));
      methods.set("Reflect", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue normal) {
            LuaTable value = checkVector3(self);
            LuaTable axis = checkVector3(normal);
            double x = value.get("X").todouble();
            double y = value.get("Y").todouble();
            double z = value.get("Z").todouble();
            double nx = axis.get("X").todouble();
            double ny = axis.get("Y").todouble();
            double nz = axis.get("Z").todouble();
            double scale = 2.0 * (x * nx + y * ny + z * nz);
            return MLuaVector.vector3(x - scale * nx, y - scale * ny, z - scale * nz);
         }
      });
      methods.set("reflect", methods.get("Reflect"));
      methods.set("Min", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue other) {
            LuaTable a = checkVector3(self);
            LuaTable b = checkVector3(other);
            return MLuaVector.vector3(Math.min(a.get("X").todouble(), b.get("X").todouble()), Math.min(a.get("Y").todouble(), b.get("Y").todouble()), Math.min(a.get("Z").todouble(), b.get("Z").todouble()));
         }
      });
      methods.set("min", methods.get("Min"));
      methods.set("Max", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue other) {
            LuaTable a = checkVector3(self);
            LuaTable b = checkVector3(other);
            return MLuaVector.vector3(Math.max(a.get("X").todouble(), b.get("X").todouble()), Math.max(a.get("Y").todouble(), b.get("Y").todouble()), Math.max(a.get("Z").todouble(), b.get("Z").todouble()));
         }
      });
      methods.set("max", methods.get("Max"));
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
      methods.set("distanceTo", methods.get("Distance"));
      methods.set("DistanceSquared", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue other) {
            LuaTable a = checkVector3(self);
            LuaTable b = checkVector3(other);
            double dx = a.get("X").todouble() - b.get("X").todouble();
            double dy = a.get("Y").todouble() - b.get("Y").todouble();
            double dz = a.get("Z").todouble() - b.get("Z").todouble();
            return LuaValue.valueOf(dx * dx + dy * dy + dz * dz);
         }
      });
      methods.set("distanceSquared", methods.get("DistanceSquared"));
      methods.set("distanceToSqr", methods.get("DistanceSquared"));
      methods.set("LengthSquared", new OneArgFunction() {
         public LuaValue call(LuaValue self) {
            LuaTable a = checkVector3(self);
            double x = a.get("X").todouble();
            double y = a.get("Y").todouble();
            double z = a.get("Z").todouble();
            return LuaValue.valueOf(x * x + y * y + z * z);
         }
      });
      methods.set("lengthSquared", methods.get("LengthSquared"));
      methods.set("lengthSqr", methods.get("LengthSquared"));
      methods.set("Length", new OneArgFunction() {
         public LuaValue call(LuaValue self) {
            return LuaValue.valueOf(checkVector3(self).get("Magnitude").todouble());
         }
      });
      methods.set("length", methods.get("Length"));
      methods.set("horizontalDistanceSqr", new OneArgFunction() {
         public LuaValue call(LuaValue self) {
            LuaTable vector = checkVector3(self);
            double x = vector.get("X").todouble();
            double z = vector.get("Z").todouble();
            return LuaValue.valueOf(x * x + z * z);
         }
      });
      methods.set("horizontalDistance", new OneArgFunction() {
         public LuaValue call(LuaValue self) {
            LuaTable vector = checkVector3(self);
            double x = vector.get("X").todouble();
            double z = vector.get("Z").todouble();
            return LuaValue.valueOf(Math.sqrt(x * x + z * z));
         }
      });
      methods.set("scale", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue scalar) {
            LuaTable vector = checkVector3(self);
            double amount = scalar.checkdouble();
            return MLuaVector.vector3(vector.get("X").todouble() * amount, vector.get("Y").todouble() * amount, vector.get("Z").todouble() * amount);
         }
      });
      methods.set("Normalize", new OneArgFunction() {
         public LuaValue call(LuaValue self) {
            LuaTable vector = checkVector3(self);
            return vector.get("Unit");
         }
      });
      methods.set("normalize", methods.get("Normalize"));
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
      metatable.set("__metatable", LuaValue.valueOf("Vector3"));
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
      methods.set("dot", methods.get("Dot"));
      methods.set("Angle", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue other) {
            LuaTable a = checkVector2(self);
            LuaTable b = checkVector2(other);
            double ax = a.get("X").todouble();
            double ay = a.get("Y").todouble();
            double bx = b.get("X").todouble();
            double by = b.get("Y").todouble();
            if ((ax == 0.0 && ay == 0.0) || (bx == 0.0 && by == 0.0)) {
               throw new LuaError("Vector2:Angle requires non-zero vectors");
            }
            return LuaValue.valueOf(Math.atan2(ax * by - ay * bx, ax * bx + ay * by));
         }
      });
      methods.set("angle", methods.get("Angle"));
      methods.set("Cross", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue other) {
            LuaTable a = checkVector2(self);
            LuaTable b = checkVector2(other);
            return LuaValue.valueOf(a.get("X").todouble() * b.get("Y").todouble() - a.get("Y").todouble() * b.get("X").todouble());
         }
      });
      methods.set("cross", methods.get("Cross"));
      methods.set("Min", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue other) {
            LuaTable a = checkVector2(self);
            LuaTable b = checkVector2(other);
            return MLuaVector.vector2(Math.min(a.get("X").todouble(), b.get("X").todouble()), Math.min(a.get("Y").todouble(), b.get("Y").todouble()));
         }
      });
      methods.set("min", methods.get("Min"));
      methods.set("Max", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue other) {
            LuaTable a = checkVector2(self);
            LuaTable b = checkVector2(other);
            return MLuaVector.vector2(Math.max(a.get("X").todouble(), b.get("X").todouble()), Math.max(a.get("Y").todouble(), b.get("Y").todouble()));
         }
      });
      methods.set("max", methods.get("Max"));
      methods.set("DistanceSquared", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue other) {
            LuaTable a = checkVector2(self);
            LuaTable b = checkVector2(other);
            double dx = a.get("X").todouble() - b.get("X").todouble();
            double dy = a.get("Y").todouble() - b.get("Y").todouble();
            return LuaValue.valueOf(dx * dx + dy * dy);
         }
      });
      methods.set("distanceSquared", methods.get("DistanceSquared"));
      methods.set("distanceToSqr", methods.get("DistanceSquared"));
      methods.set("LengthSquared", new OneArgFunction() {
         public LuaValue call(LuaValue self) {
            LuaTable a = checkVector2(self);
            double x = a.get("X").todouble();
            double y = a.get("Y").todouble();
            return LuaValue.valueOf(x * x + y * y);
         }
      });
      methods.set("lengthSquared", methods.get("LengthSquared"));
      methods.set("lengthSqr", methods.get("LengthSquared"));
      methods.set("Distance", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue other) {
            LuaTable a = checkVector2(self);
            LuaTable b = checkVector2(other);
            double dx = a.get("X").todouble() - b.get("X").todouble();
            double dy = a.get("Y").todouble() - b.get("Y").todouble();
            return LuaValue.valueOf(Math.sqrt(dx * dx + dy * dy));
         }
      });
      methods.set("distance", methods.get("Distance"));
      methods.set("distanceTo", methods.get("Distance"));
      methods.set("Length", new OneArgFunction() {
         public LuaValue call(LuaValue self) {
            return LuaValue.valueOf(checkVector2(self).get("Magnitude").todouble());
         }
      });
      methods.set("length", methods.get("Length"));
      methods.set("scale", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue scalar) {
            LuaTable vector = checkVector2(self);
            double amount = scalar.checkdouble();
            return MLuaVector.vector2(vector.get("X").todouble() * amount, vector.get("Y").todouble() * amount);
         }
      });
      methods.set("Normalize", new OneArgFunction() {
         public LuaValue call(LuaValue self) {
            LuaTable vector = checkVector2(self);
            return vector.get("Unit");
         }
      });
      methods.set("normalize", methods.get("Normalize"));
      methods.set("Lerp", new ThreeArgFunction() {
         public LuaValue call(LuaValue self, LuaValue goal, LuaValue alpha) {
            LuaTable a = checkVector2(self);
            LuaTable b = checkVector2(goal);
            double t = alpha.checkdouble();
            return MLuaVector.vector2(MLuaVector.lerp(a.get("X").todouble(), b.get("X").todouble(), t), MLuaVector.lerp(a.get("Y").todouble(), b.get("Y").todouble(), t));
         }
      });
      methods.set("lerp", methods.get("Lerp"));
      methods.set("Floor", new OneArgFunction() {
         public LuaValue call(LuaValue self) {
            LuaTable value = checkVector2(self);
            return MLuaVector.vector2(Math.floor(value.get("X").todouble()), Math.floor(value.get("Y").todouble()));
         }
      });
      methods.set("floor", methods.get("Floor"));
      methods.set("Ceil", new OneArgFunction() {
         public LuaValue call(LuaValue self) {
            LuaTable value = checkVector2(self);
            return MLuaVector.vector2(Math.ceil(value.get("X").todouble()), Math.ceil(value.get("Y").todouble()));
         }
      });
      methods.set("ceil", methods.get("Ceil"));
      methods.set("Abs", new OneArgFunction() {
         public LuaValue call(LuaValue self) {
            LuaTable value = checkVector2(self);
            return MLuaVector.vector2(Math.abs(value.get("X").todouble()), Math.abs(value.get("Y").todouble()));
         }
      });
      methods.set("abs", methods.get("Abs"));
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
      metatable.set("__metatable", LuaValue.valueOf("Vector2"));
      return metatable;
   }

   private static double lerp(double a, double b, double t) {
      return a + (b - a) * t;
   }
}
