package dev.mlua.math;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;

public final class MLuaMat4 {
   private MLuaMat4() {
   }

   public static LuaTable identity() {
      return mat4(1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0);
   }

   public static LuaTable mat4(double v11, double v12, double v13, double v14, double v21, double v22, double v23, double v24, double v31, double v32, double v33, double v34, double v41, double v42, double v43, double v44) {
      LuaTable table = new LuaTable();
      table.set("__type", "Mat4");
      setAll(table, v11, v12, v13, v14, v21, v22, v23, v24, v31, v32, v33, v34, v41, v42, v43, v44);
      table.set("add", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue other) {
            LuaTable b = other.checktable();
            return MLuaMat4.mat4(self.get("v11").todouble() + b.get("v11").todouble(), self.get("v12").todouble() + b.get("v12").todouble(), self.get("v13").todouble() + b.get("v13").todouble(), self.get("v14").todouble() + b.get("v14").todouble(), self.get("v21").todouble() + b.get("v21").todouble(), self.get("v22").todouble() + b.get("v22").todouble(), self.get("v23").todouble() + b.get("v23").todouble(), self.get("v24").todouble() + b.get("v24").todouble(), self.get("v31").todouble() + b.get("v31").todouble(), self.get("v32").todouble() + b.get("v32").todouble(), self.get("v33").todouble() + b.get("v33").todouble(), self.get("v34").todouble() + b.get("v34").todouble(), self.get("v41").todouble() + b.get("v41").todouble(), self.get("v42").todouble() + b.get("v42").todouble(), self.get("v43").todouble() + b.get("v43").todouble(), self.get("v44").todouble() + b.get("v44").todouble());
         }
      });
      table.set("sub", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue other) {
            LuaTable b = other.checktable();
            return MLuaMat4.mat4(self.get("v11").todouble() - b.get("v11").todouble(), self.get("v12").todouble() - b.get("v12").todouble(), self.get("v13").todouble() - b.get("v13").todouble(), self.get("v14").todouble() - b.get("v14").todouble(), self.get("v21").todouble() - b.get("v21").todouble(), self.get("v22").todouble() - b.get("v22").todouble(), self.get("v23").todouble() - b.get("v23").todouble(), self.get("v24").todouble() - b.get("v24").todouble(), self.get("v31").todouble() - b.get("v31").todouble(), self.get("v32").todouble() - b.get("v32").todouble(), self.get("v33").todouble() - b.get("v33").todouble(), self.get("v34").todouble() - b.get("v34").todouble(), self.get("v41").todouble() - b.get("v41").todouble(), self.get("v42").todouble() - b.get("v42").todouble(), self.get("v43").todouble() - b.get("v43").todouble(), self.get("v44").todouble() - b.get("v44").todouble());
         }
      });
      table.set("mul", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue other) {
            String type = other.get("__type").tojstring();
            if ("Mat4".equals(type)) {
               return MLuaMat4.mul4x4(self.checktable(), other.checktable());
            } else if ("Vec4".equals(type)) {
               return MLuaMat4.transformVec4(self.checktable(), other.checktable());
            } else if ("Vec3".equals(type)) {
               return MLuaMat4.transformVec3(self.checktable(), other.checktable());
            } else {
               throw new LuaError("Mat4 multiplication expects a Mat4, Vec4, or Vec3");
            }
         }
      });
      table.set("transpose", new OneArgFunction() {
         public LuaValue call(LuaValue self) {
            LuaTable a = self.checktable();
            return MLuaMat4.mat4(a.get("v11").todouble(), a.get("v21").todouble(), a.get("v31").todouble(), a.get("v41").todouble(), a.get("v12").todouble(), a.get("v22").todouble(), a.get("v32").todouble(), a.get("v42").todouble(), a.get("v13").todouble(), a.get("v23").todouble(), a.get("v33").todouble(), a.get("v43").todouble(), a.get("v14").todouble(), a.get("v24").todouble(), a.get("v34").todouble(), a.get("v44").todouble());
         }
      });
      table.set("det", new OneArgFunction() {
         public LuaValue call(LuaValue self) {
            return LuaValue.valueOf(MLuaMat4.determinant(self.checktable()));
         }
      });
      table.set("inverted", new OneArgFunction() {
         public LuaValue call(LuaValue self) {
            return MLuaMat4.invert(self.checktable());
         }
      });
      table.set("scale", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            LuaTable a = args.checktable(1);
            double sx, sy, sz;
            if (args.arg(2).istable()) {
               LuaTable s = args.checktable(2);
               sx = s.get("X").todouble(); sy = s.get("Y").todouble(); sz = s.get("Z").todouble();
            } else {
               sx = args.checkdouble(2); sy = args.checkdouble(3); sz = args.checkdouble(4);
            }
            return MLuaMat4.mul4x4(a, MLuaMat4.scaleMatrix(sx, sy, sz));
         }
      });
      table.set("translate", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            LuaTable a = args.checktable(1);
            double tx, ty, tz;
            if (args.arg(2).istable()) {
               LuaTable t = args.checktable(2);
               tx = t.get("X").todouble(); ty = t.get("Y").todouble(); tz = t.get("Z").todouble();
            } else {
               tx = args.checkdouble(2); ty = args.checkdouble(3); tz = args.checkdouble(4);
            }
            return MLuaMat4.mul4x4(a, MLuaMat4.translationMatrix(tx, ty, tz));
         }
      });
      table.set("rotateX", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue degrees) {
            return MLuaMat4.mul4x4(self.checktable(), MLuaMat4.rotationXMatrix(degrees.checkdouble()));
         }
      });
      table.set("rotateY", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue degrees) {
            return MLuaMat4.mul4x4(self.checktable(), MLuaMat4.rotationYMatrix(degrees.checkdouble()));
         }
      });
      table.set("rotateZ", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue degrees) {
            return MLuaMat4.mul4x4(self.checktable(), MLuaMat4.rotationZMatrix(degrees.checkdouble()));
         }
      });
      table.setmetatable(mat4Metatable());
      return table;
   }

   public static LuaTable fromColumns(LuaTable col1, LuaTable col2, LuaTable col3, LuaTable col4) {
      return mat4(col1.get("X").todouble(), col2.get("X").todouble(), col3.get("X").todouble(), col4.get("X").todouble(), col1.get("Y").todouble(), col2.get("Y").todouble(), col3.get("Y").todouble(), col4.get("Y").todouble(), col1.get("Z").todouble(), col2.get("Z").todouble(), col3.get("Z").todouble(), col4.get("Z").todouble(), col1.get("W").todouble(), col2.get("W").todouble(), col3.get("W").todouble(), col4.get("W").todouble());
   }

   private static void setAll(LuaTable table, double v11, double v12, double v13, double v14, double v21, double v22, double v23, double v24, double v31, double v32, double v33, double v34, double v41, double v42, double v43, double v44) {
      table.set("v11", v11); table.set("V11", v11);
      table.set("v12", v12); table.set("V12", v12);
      table.set("v13", v13); table.set("V13", v13);
      table.set("v14", v14); table.set("V14", v14);
      table.set("v21", v21); table.set("V21", v21);
      table.set("v22", v22); table.set("V22", v22);
      table.set("v23", v23); table.set("V23", v23);
      table.set("v24", v24); table.set("V24", v24);
      table.set("v31", v31); table.set("V31", v31);
      table.set("v32", v32); table.set("V32", v32);
      table.set("v33", v33); table.set("V33", v33);
      table.set("v34", v34); table.set("V34", v34);
      table.set("v41", v41); table.set("V41", v41);
      table.set("v42", v42); table.set("V42", v42);
      table.set("v43", v43); table.set("V43", v43);
      table.set("v44", v44); table.set("V44", v44);
   }

   private static double determinant(LuaTable a) {
      double a11 = a.get("v11").todouble(), a12 = a.get("v12").todouble(), a13 = a.get("v13").todouble(), a14 = a.get("v14").todouble();
      double a21 = a.get("v21").todouble(), a22 = a.get("v22").todouble(), a23 = a.get("v23").todouble(), a24 = a.get("v24").todouble();
      double a31 = a.get("v31").todouble(), a32 = a.get("v32").todouble(), a33 = a.get("v33").todouble(), a34 = a.get("v34").todouble();
      double a41 = a.get("v41").todouble(), a42 = a.get("v42").todouble(), a43 = a.get("v43").todouble(), a44 = a.get("v44").todouble();
      return a11 * minor3(a22, a23, a24, a32, a33, a34, a42, a43, a44) - a12 * minor3(a21, a23, a24, a31, a33, a34, a41, a43, a44) + a13 * minor3(a21, a22, a24, a31, a32, a34, a41, a42, a44) - a14 * minor3(a21, a22, a23, a31, a32, a33, a41, a42, a43);
   }

   private static double minor3(double a, double b, double c, double d, double e, double f, double g, double h, double i) {
      return a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g);
   }

   private static LuaTable invert(LuaTable a) {
      double det = determinant(a);
      if (Math.abs(det) < 1.0E-12) {
         throw new LuaError("Mat4 determinant is zero, cannot invert");
      }
      double a11 = a.get("v11").todouble(), a12 = a.get("v12").todouble(), a13 = a.get("v13").todouble(), a14 = a.get("v14").todouble();
      double a21 = a.get("v21").todouble(), a22 = a.get("v22").todouble(), a23 = a.get("v23").todouble(), a24 = a.get("v24").todouble();
      double a31 = a.get("v31").todouble(), a32 = a.get("v32").todouble(), a33 = a.get("v33").todouble(), a34 = a.get("v34").todouble();
      double a41 = a.get("v41").todouble(), a42 = a.get("v42").todouble(), a43 = a.get("v43").todouble(), a44 = a.get("v44").todouble();
      double invDet = 1.0 / det;
      return mat4(
         minor3(a22, a23, a24, a32, a33, a34, a42, a43, a44) * invDet,
        -minor3(a12, a13, a14, a32, a33, a34, a42, a43, a44) * invDet,
         minor3(a12, a13, a14, a22, a23, a24, a42, a43, a44) * invDet,
        -minor3(a12, a13, a14, a22, a23, a24, a32, a33, a34) * invDet,
        -minor3(a21, a23, a24, a31, a33, a34, a41, a43, a44) * invDet,
         minor3(a11, a13, a14, a31, a33, a34, a41, a43, a44) * invDet,
        -minor3(a11, a13, a14, a21, a23, a24, a41, a43, a44) * invDet,
         minor3(a11, a13, a14, a21, a23, a24, a31, a33, a34) * invDet,
         minor3(a21, a22, a24, a31, a32, a34, a41, a42, a44) * invDet,
        -minor3(a11, a12, a14, a31, a32, a34, a41, a42, a44) * invDet,
         minor3(a11, a12, a14, a21, a22, a24, a41, a42, a44) * invDet,
        -minor3(a11, a12, a14, a21, a22, a24, a31, a32, a34) * invDet,
        -minor3(a21, a22, a23, a31, a32, a33, a41, a42, a43) * invDet,
         minor3(a11, a12, a13, a31, a32, a33, a41, a42, a43) * invDet,
        -minor3(a11, a12, a13, a21, a22, a23, a41, a42, a43) * invDet,
         minor3(a11, a12, a13, a21, a22, a23, a31, a32, a33) * invDet
      );
   }

   private static LuaTable mul4x4(LuaTable a, LuaTable b) {
      double a11 = a.get("v11").todouble(), a12 = a.get("v12").todouble(), a13 = a.get("v13").todouble(), a14 = a.get("v14").todouble();
      double a21 = a.get("v21").todouble(), a22 = a.get("v22").todouble(), a23 = a.get("v23").todouble(), a24 = a.get("v24").todouble();
      double a31 = a.get("v31").todouble(), a32 = a.get("v32").todouble(), a33 = a.get("v33").todouble(), a34 = a.get("v34").todouble();
      double a41 = a.get("v41").todouble(), a42 = a.get("v42").todouble(), a43 = a.get("v43").todouble(), a44 = a.get("v44").todouble();
      double b11 = b.get("v11").todouble(), b12 = b.get("v12").todouble(), b13 = b.get("v13").todouble(), b14 = b.get("v14").todouble();
      double b21 = b.get("v21").todouble(), b22 = b.get("v22").todouble(), b23 = b.get("v23").todouble(), b24 = b.get("v24").todouble();
      double b31 = b.get("v31").todouble(), b32 = b.get("v32").todouble(), b33 = b.get("v33").todouble(), b34 = b.get("v34").todouble();
      double b41 = b.get("v41").todouble(), b42 = b.get("v42").todouble(), b43 = b.get("v43").todouble(), b44 = b.get("v44").todouble();
      return mat4(
         a11 * b11 + a12 * b21 + a13 * b31 + a14 * b41,
         a11 * b12 + a12 * b22 + a13 * b32 + a14 * b42,
         a11 * b13 + a12 * b23 + a13 * b33 + a14 * b43,
         a11 * b14 + a12 * b24 + a13 * b34 + a14 * b44,
         a21 * b11 + a22 * b21 + a23 * b31 + a24 * b41,
         a21 * b12 + a22 * b22 + a23 * b32 + a24 * b42,
         a21 * b13 + a22 * b23 + a23 * b33 + a24 * b43,
         a21 * b14 + a22 * b24 + a23 * b34 + a24 * b44,
         a31 * b11 + a32 * b21 + a33 * b31 + a34 * b41,
         a31 * b12 + a32 * b22 + a33 * b32 + a34 * b42,
         a31 * b13 + a32 * b23 + a33 * b33 + a34 * b43,
         a31 * b14 + a32 * b24 + a33 * b34 + a34 * b44,
         a41 * b11 + a42 * b21 + a43 * b31 + a44 * b41,
         a41 * b12 + a42 * b22 + a43 * b32 + a44 * b42,
         a41 * b13 + a42 * b23 + a43 * b33 + a44 * b43,
         a41 * b14 + a42 * b24 + a43 * b34 + a44 * b44
      );
   }

   private static LuaTable transformVec4(LuaTable a, LuaTable v) {
      double x = v.get("X").todouble(), y = v.get("Y").todouble(), z = v.get("Z").todouble(), w = v.get("W").todouble();
      return MLuaVec4.vec4(
         a.get("v11").todouble() * x + a.get("v12").todouble() * y + a.get("v13").todouble() * z + a.get("v14").todouble() * w,
         a.get("v21").todouble() * x + a.get("v22").todouble() * y + a.get("v23").todouble() * z + a.get("v24").todouble() * w,
         a.get("v31").todouble() * x + a.get("v32").todouble() * y + a.get("v33").todouble() * z + a.get("v34").todouble() * w,
         a.get("v41").todouble() * x + a.get("v42").todouble() * y + a.get("v43").todouble() * z + a.get("v44").todouble() * w
      );
   }

   private static LuaTable transformVec3(LuaTable a, LuaTable v) {
      double x = v.get("X").todouble(), y = v.get("Y").todouble(), z = v.get("Z").todouble();
      double tw = a.get("v41").todouble() * x + a.get("v42").todouble() * y + a.get("v43").todouble() * z + a.get("v44").todouble();
      if (tw == 0.0) {
         return MLuaVector.vector3(
            a.get("v11").todouble() * x + a.get("v12").todouble() * y + a.get("v13").todouble() * z + a.get("v14").todouble(),
            a.get("v21").todouble() * x + a.get("v22").todouble() * y + a.get("v23").todouble() * z + a.get("v24").todouble(),
            a.get("v31").todouble() * x + a.get("v32").todouble() * y + a.get("v33").todouble() * z + a.get("v34").todouble()
         );
      }
      double invW = 1.0 / tw;
      return MLuaVector.vector3(
         (a.get("v11").todouble() * x + a.get("v12").todouble() * y + a.get("v13").todouble() * z + a.get("v14").todouble()) * invW,
         (a.get("v21").todouble() * x + a.get("v22").todouble() * y + a.get("v23").todouble() * z + a.get("v24").todouble()) * invW,
         (a.get("v31").todouble() * x + a.get("v32").todouble() * y + a.get("v33").todouble() * z + a.get("v34").todouble()) * invW
      );
   }

   public static LuaTable translationMatrix(double x, double y, double z) {
      return mat4(1.0, 0.0, 0.0, x, 0.0, 1.0, 0.0, y, 0.0, 0.0, 1.0, z, 0.0, 0.0, 0.0, 1.0);
   }

   public static LuaTable scaleMatrix(double x, double y, double z) {
      return mat4(x, 0.0, 0.0, 0.0, 0.0, y, 0.0, 0.0, 0.0, 0.0, z, 0.0, 0.0, 0.0, 0.0, 1.0);
   }

   public static LuaTable rotationXMatrix(double degrees) {
      double rad = Math.toRadians(degrees);
      double c = Math.cos(rad);
      double s = Math.sin(rad);
      return mat4(1.0, 0.0, 0.0, 0.0, 0.0, c, -s, 0.0, 0.0, s, c, 0.0, 0.0, 0.0, 0.0, 1.0);
   }

   public static LuaTable rotationYMatrix(double degrees) {
      double rad = Math.toRadians(degrees);
      double c = Math.cos(rad);
      double s = Math.sin(rad);
      return mat4(c, 0.0, s, 0.0, 0.0, 1.0, 0.0, 0.0, -s, 0.0, c, 0.0, 0.0, 0.0, 0.0, 1.0);
   }

   public static LuaTable rotationZMatrix(double degrees) {
      double rad = Math.toRadians(degrees);
      double c = Math.cos(rad);
      double s = Math.sin(rad);
      return mat4(c, -s, 0.0, 0.0, s, c, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0);
   }

   private static LuaTable mat4Metatable() {
      LuaTable metatable = new LuaTable();
      metatable.set(LuaValue.ADD, new TwoArgFunction() {
         public LuaValue call(LuaValue left, LuaValue right) {
            LuaTable a = left.checktable();
            LuaTable b = right.checktable();
            return MLuaMat4.mat4(a.get("v11").todouble() + b.get("v11").todouble(), a.get("v12").todouble() + b.get("v12").todouble(), a.get("v13").todouble() + b.get("v13").todouble(), a.get("v14").todouble() + b.get("v14").todouble(), a.get("v21").todouble() + b.get("v21").todouble(), a.get("v22").todouble() + b.get("v22").todouble(), a.get("v23").todouble() + b.get("v23").todouble(), a.get("v24").todouble() + b.get("v24").todouble(), a.get("v31").todouble() + b.get("v31").todouble(), a.get("v32").todouble() + b.get("v32").todouble(), a.get("v33").todouble() + b.get("v33").todouble(), a.get("v34").todouble() + b.get("v34").todouble(), a.get("v41").todouble() + b.get("v41").todouble(), a.get("v42").todouble() + b.get("v42").todouble(), a.get("v43").todouble() + b.get("v43").todouble(), a.get("v44").todouble() + b.get("v44").todouble());
         }
      });
      metatable.set(LuaValue.SUB, new TwoArgFunction() {
         public LuaValue call(LuaValue left, LuaValue right) {
            LuaTable a = left.checktable();
            LuaTable b = right.checktable();
            return MLuaMat4.mat4(a.get("v11").todouble() - b.get("v11").todouble(), a.get("v12").todouble() - b.get("v12").todouble(), a.get("v13").todouble() - b.get("v13").todouble(), a.get("v14").todouble() - b.get("v14").todouble(), a.get("v21").todouble() - b.get("v21").todouble(), a.get("v22").todouble() - b.get("v22").todouble(), a.get("v23").todouble() - b.get("v23").todouble(), a.get("v24").todouble() - b.get("v24").todouble(), a.get("v31").todouble() - b.get("v31").todouble(), a.get("v32").todouble() - b.get("v32").todouble(), a.get("v33").todouble() - b.get("v33").todouble(), a.get("v34").todouble() - b.get("v34").todouble(), a.get("v41").todouble() - b.get("v41").todouble(), a.get("v42").todouble() - b.get("v42").todouble(), a.get("v43").todouble() - b.get("v43").todouble(), a.get("v44").todouble() - b.get("v44").todouble());
         }
      });
      metatable.set(LuaValue.MUL, new TwoArgFunction() {
         public LuaValue call(LuaValue left, LuaValue right) {
            LuaTable a = left.checktable();
            String type = right.get("__type").tojstring();
            if ("Mat4".equals(type)) {
               return MLuaMat4.mul4x4(a, right.checktable());
            } else if ("Vec4".equals(type)) {
               return MLuaMat4.transformVec4(a, right.checktable());
            } else if ("Vec3".equals(type)) {
               return MLuaMat4.transformVec3(a, right.checktable());
            } else {
               throw new LuaError("Mat4 * expects a Mat4, Vec4, or Vec3");
            }
         }
      });
      metatable.set("__tostring", new OneArgFunction() {
         public LuaValue call(LuaValue value) {
            LuaTable t = value.checktable();
            return LuaValue.valueOf("Mat4(" + t.get("v11").tojstring() + ", " + t.get("v12").tojstring() + ", " + t.get("v13").tojstring() + ", " + t.get("v14").tojstring() + "; " + t.get("v21").tojstring() + ", " + t.get("v22").tojstring() + ", " + t.get("v23").tojstring() + ", " + t.get("v24").tojstring() + "; " + t.get("v31").tojstring() + ", " + t.get("v32").tojstring() + ", " + t.get("v33").tojstring() + ", " + t.get("v34").tojstring() + "; " + t.get("v41").tojstring() + ", " + t.get("v42").tojstring() + ", " + t.get("v43").tojstring() + ", " + t.get("v44").tojstring() + ")");
         }
      });
      return metatable;
   }
}
