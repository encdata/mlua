package dev.mlua.math;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;

public final class MLuaMat3 {
   private MLuaMat3() {
   }

   public static LuaTable identity() {
      return mat3(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0);
   }

   public static LuaTable mat3(double v11, double v12, double v13, double v21, double v22, double v23, double v31, double v32, double v33) {
      LuaTable table = new LuaTable();
      table.set("__type", "Mat3");
      setAll(table, v11, v12, v13, v21, v22, v23, v31, v32, v33);
      table.set("add", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue other) {
            LuaTable b = other.checktable();
            return MLuaMat3.mat3(self.get("v11").todouble() + b.get("v11").todouble(), self.get("v12").todouble() + b.get("v12").todouble(), self.get("v13").todouble() + b.get("v13").todouble(), self.get("v21").todouble() + b.get("v21").todouble(), self.get("v22").todouble() + b.get("v22").todouble(), self.get("v23").todouble() + b.get("v23").todouble(), self.get("v31").todouble() + b.get("v31").todouble(), self.get("v32").todouble() + b.get("v32").todouble(), self.get("v33").todouble() + b.get("v33").todouble());
         }
      });
      table.set("sub", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue other) {
            LuaTable b = other.checktable();
            return MLuaMat3.mat3(self.get("v11").todouble() - b.get("v11").todouble(), self.get("v12").todouble() - b.get("v12").todouble(), self.get("v13").todouble() - b.get("v13").todouble(), self.get("v21").todouble() - b.get("v21").todouble(), self.get("v22").todouble() - b.get("v22").todouble(), self.get("v23").todouble() - b.get("v23").todouble(), self.get("v31").todouble() - b.get("v31").todouble(), self.get("v32").todouble() - b.get("v32").todouble(), self.get("v33").todouble() - b.get("v33").todouble());
         }
      });
      table.set("mul", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue other) {
            if (other.istable() && "Mat3".equals(other.get("__type").tojstring())) {
               LuaTable a = self.checktable();
               LuaTable b = other.checktable();
               double a11 = a.get("v11").todouble(), a12 = a.get("v12").todouble(), a13 = a.get("v13").todouble();
               double a21 = a.get("v21").todouble(), a22 = a.get("v22").todouble(), a23 = a.get("v23").todouble();
               double a31 = a.get("v31").todouble(), a32 = a.get("v32").todouble(), a33 = a.get("v33").todouble();
               double b11 = b.get("v11").todouble(), b12 = b.get("v12").todouble(), b13 = b.get("v13").todouble();
               double b21 = b.get("v21").todouble(), b22 = b.get("v22").todouble(), b23 = b.get("v23").todouble();
               double b31 = b.get("v31").todouble(), b32 = b.get("v32").todouble(), b33 = b.get("v33").todouble();
               return MLuaMat3.mat3(a11 * b11 + a12 * b21 + a13 * b31, a11 * b12 + a12 * b22 + a13 * b32, a11 * b13 + a12 * b23 + a13 * b33, a21 * b11 + a22 * b21 + a23 * b31, a21 * b12 + a22 * b22 + a23 * b32, a21 * b13 + a22 * b23 + a23 * b33, a31 * b11 + a32 * b21 + a33 * b31, a31 * b12 + a32 * b22 + a33 * b32, a31 * b13 + a32 * b23 + a33 * b33);
            } else if (other.istable() && "Vec3".equals(other.get("__type").tojstring())) {
               LuaTable a = self.checktable();
               LuaTable v = other.checktable();
               double x = v.get("X").todouble(), y = v.get("Y").todouble(), z = v.get("Z").todouble();
               return MLuaVector.vector3(a.get("v11").todouble() * x + a.get("v12").todouble() * y + a.get("v13").todouble() * z, a.get("v21").todouble() * x + a.get("v22").todouble() * y + a.get("v23").todouble() * z, a.get("v31").todouble() * x + a.get("v32").todouble() * y + a.get("v33").todouble() * z);
            } else {
               throw new LuaError("Mat3 multiplication expects a Mat3 or Vec3");
            }
         }
      });
      table.set("transpose", new OneArgFunction() {
         public LuaValue call(LuaValue self) {
            LuaTable a = self.checktable();
            return MLuaMat3.mat3(a.get("v11").todouble(), a.get("v21").todouble(), a.get("v31").todouble(), a.get("v12").todouble(), a.get("v22").todouble(), a.get("v32").todouble(), a.get("v13").todouble(), a.get("v23").todouble(), a.get("v33").todouble());
         }
      });
      table.set("det", new OneArgFunction() {
         public LuaValue call(LuaValue self) {
            return LuaValue.valueOf(MLuaMat3.determinant(self.checktable()));
         }
      });
      table.set("inverted", new OneArgFunction() {
         public LuaValue call(LuaValue self) {
            return MLuaMat3.invert(self.checktable());
         }
      });
      table.set("scale", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            LuaTable a = args.checktable(1);
            if (args.arg(2).istable()) {
               LuaTable s = args.checktable(2);
               return MLuaMat3.mul3x3(a, MLuaMat3.mat3(s.get("X").todouble(), 0.0, 0.0, 0.0, s.get("Y").todouble(), 0.0, 0.0, 0.0, s.get("Z").todouble()));
            } else {
               return MLuaMat3.mul3x3(a, MLuaMat3.mat3(args.checkdouble(2), 0.0, 0.0, 0.0, args.checkdouble(3), 0.0, 0.0, 0.0, args.checkdouble(4)));
            }
         }
      });
      table.set("rotateX", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue degrees) {
            return MLuaMat3.mul3x3(self.checktable(), MLuaMat3.rotationX(degrees.checkdouble()));
         }
      });
      table.set("rotateY", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue degrees) {
            return MLuaMat3.mul3x3(self.checktable(), MLuaMat3.rotationY(degrees.checkdouble()));
         }
      });
      table.set("rotateZ", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue degrees) {
            return MLuaMat3.mul3x3(self.checktable(), MLuaMat3.rotationZ(degrees.checkdouble()));
         }
      });
      table.setmetatable(mat3Metatable());
      return table;
   }

   public static LuaTable fromColumns(LuaTable col1, LuaTable col2, LuaTable col3) {
      return mat3(col1.get("X").todouble(), col2.get("X").todouble(), col3.get("X").todouble(), col1.get("Y").todouble(), col2.get("Y").todouble(), col3.get("Y").todouble(), col1.get("Z").todouble(), col2.get("Z").todouble(), col3.get("Z").todouble());
   }

   private static void setAll(LuaTable table, double v11, double v12, double v13, double v21, double v22, double v23, double v31, double v32, double v33) {
      table.set("v11", v11); table.set("V11", v11);
      table.set("v12", v12); table.set("V12", v12);
      table.set("v13", v13); table.set("V13", v13);
      table.set("v21", v21); table.set("V21", v21);
      table.set("v22", v22); table.set("V22", v22);
      table.set("v23", v23); table.set("V23", v23);
      table.set("v31", v31); table.set("V31", v31);
      table.set("v32", v32); table.set("V32", v32);
      table.set("v33", v33); table.set("V33", v33);
   }

   private static double determinant(LuaTable a) {
      double a11 = a.get("v11").todouble(), a12 = a.get("v12").todouble(), a13 = a.get("v13").todouble();
      double a21 = a.get("v21").todouble(), a22 = a.get("v22").todouble(), a23 = a.get("v23").todouble();
      double a31 = a.get("v31").todouble(), a32 = a.get("v32").todouble(), a33 = a.get("v33").todouble();
      return a11 * (a22 * a33 - a23 * a32) - a12 * (a21 * a33 - a23 * a31) + a13 * (a21 * a32 - a22 * a31);
   }

   private static LuaTable invert(LuaTable a) {
      double det = determinant(a);
      if (Math.abs(det) < 1.0E-12) {
         throw new LuaError("Mat3 determinant is zero, cannot invert");
      }
      double a11 = a.get("v11").todouble(), a12 = a.get("v12").todouble(), a13 = a.get("v13").todouble();
      double a21 = a.get("v21").todouble(), a22 = a.get("v22").todouble(), a23 = a.get("v23").todouble();
      double a31 = a.get("v31").todouble(), a32 = a.get("v32").todouble(), a33 = a.get("v33").todouble();
      double invDet = 1.0 / det;
      return mat3((a22 * a33 - a23 * a32) * invDet, (a13 * a32 - a12 * a33) * invDet, (a12 * a23 - a13 * a22) * invDet, (a23 * a31 - a21 * a33) * invDet, (a11 * a33 - a13 * a31) * invDet, (a13 * a21 - a11 * a23) * invDet, (a21 * a32 - a22 * a31) * invDet, (a12 * a31 - a11 * a32) * invDet, (a11 * a22 - a12 * a21) * invDet);
   }

   private static LuaTable mul3x3(LuaTable a, double b11, double b12, double b13, double b21, double b22, double b23, double b31, double b32, double b33) {
      double a11 = a.get("v11").todouble(), a12 = a.get("v12").todouble(), a13 = a.get("v13").todouble();
      double a21 = a.get("v21").todouble(), a22 = a.get("v22").todouble(), a23 = a.get("v23").todouble();
      double a31 = a.get("v31").todouble(), a32 = a.get("v32").todouble(), a33 = a.get("v33").todouble();
      return mat3(a11 * b11 + a12 * b21 + a13 * b31, a11 * b12 + a12 * b22 + a13 * b32, a11 * b13 + a12 * b23 + a13 * b33, a21 * b11 + a22 * b21 + a23 * b31, a21 * b12 + a22 * b22 + a23 * b32, a21 * b13 + a22 * b23 + a23 * b33, a31 * b11 + a32 * b21 + a33 * b31, a31 * b12 + a32 * b22 + a33 * b32, a31 * b13 + a32 * b23 + a33 * b33);
   }

   private static LuaTable mul3x3(LuaTable a, LuaTable b) {
      return mul3x3(a, b.get("v11").todouble(), b.get("v12").todouble(), b.get("v13").todouble(), b.get("v21").todouble(), b.get("v22").todouble(), b.get("v23").todouble(), b.get("v31").todouble(), b.get("v32").todouble(), b.get("v33").todouble());
   }

   public static LuaTable rotationX(double degrees) {
      double rad = Math.toRadians(degrees);
      double c = Math.cos(rad);
      double s = Math.sin(rad);
      return mat3(1.0, 0.0, 0.0, 0.0, c, -s, 0.0, s, c);
   }

   public static LuaTable rotationY(double degrees) {
      double rad = Math.toRadians(degrees);
      double c = Math.cos(rad);
      double s = Math.sin(rad);
      return mat3(c, 0.0, s, 0.0, 1.0, 0.0, -s, 0.0, c);
   }

   public static LuaTable rotationZ(double degrees) {
      double rad = Math.toRadians(degrees);
      double c = Math.cos(rad);
      double s = Math.sin(rad);
      return mat3(c, -s, 0.0, s, c, 0.0, 0.0, 0.0, 1.0);
   }

   private static LuaTable mat3Metatable() {
      LuaTable metatable = new LuaTable();
      metatable.set(LuaValue.ADD, new TwoArgFunction() {
         public LuaValue call(LuaValue left, LuaValue right) {
            LuaTable a = left.checktable();
            LuaTable b = right.checktable();
            return MLuaMat3.mat3(a.get("v11").todouble() + b.get("v11").todouble(), a.get("v12").todouble() + b.get("v12").todouble(), a.get("v13").todouble() + b.get("v13").todouble(), a.get("v21").todouble() + b.get("v21").todouble(), a.get("v22").todouble() + b.get("v22").todouble(), a.get("v23").todouble() + b.get("v23").todouble(), a.get("v31").todouble() + b.get("v31").todouble(), a.get("v32").todouble() + b.get("v32").todouble(), a.get("v33").todouble() + b.get("v33").todouble());
         }
      });
      metatable.set(LuaValue.SUB, new TwoArgFunction() {
         public LuaValue call(LuaValue left, LuaValue right) {
            LuaTable a = left.checktable();
            LuaTable b = right.checktable();
            return MLuaMat3.mat3(a.get("v11").todouble() - b.get("v11").todouble(), a.get("v12").todouble() - b.get("v12").todouble(), a.get("v13").todouble() - b.get("v13").todouble(), a.get("v21").todouble() - b.get("v21").todouble(), a.get("v22").todouble() - b.get("v22").todouble(), a.get("v23").todouble() - b.get("v23").todouble(), a.get("v31").todouble() - b.get("v31").todouble(), a.get("v32").todouble() - b.get("v32").todouble(), a.get("v33").todouble() - b.get("v33").todouble());
         }
      });
      metatable.set(LuaValue.MUL, new TwoArgFunction() {
         public LuaValue call(LuaValue left, LuaValue right) {
            LuaTable a = left.checktable();
            LuaValue rtype = right.get("__type");
            if ("Mat3".equals(rtype.tojstring())) {
               return MLuaMat3.mul3x3(a, right.checktable());
            } else if ("Vec3".equals(rtype.tojstring())) {
               double x = right.get("X").todouble(), y = right.get("Y").todouble(), z = right.get("Z").todouble();
               return MLuaVector.vector3(a.get("v11").todouble() * x + a.get("v12").todouble() * y + a.get("v13").todouble() * z, a.get("v21").todouble() * x + a.get("v22").todouble() * y + a.get("v23").todouble() * z, a.get("v31").todouble() * x + a.get("v32").todouble() * y + a.get("v33").todouble() * z);
            } else {
               throw new LuaError("Mat3 * expects a Mat3 or Vec3");
            }
         }
      });
      metatable.set("__tostring", new OneArgFunction() {
         public LuaValue call(LuaValue value) {
            LuaTable t = value.checktable();
            return LuaValue.valueOf("Mat3(" + t.get("v11").tojstring() + ", " + t.get("v12").tojstring() + ", " + t.get("v13").tojstring() + "; " + t.get("v21").tojstring() + ", " + t.get("v22").tojstring() + ", " + t.get("v23").tojstring() + "; " + t.get("v31").tojstring() + ", " + t.get("v32").tojstring() + ", " + t.get("v33").tojstring() + ")");
         }
      });
      return metatable;
   }
}
