package dev.mlua.core.engine;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.ThreeArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.math.MLuaMat3;
import dev.mlua.math.MLuaMat4;
import dev.mlua.math.MLuaNoise;
import dev.mlua.math.MLuaVec4;
import dev.mlua.math.MLuaVector;

/** Installs MLua's typed interpolation, angle, range, and noise functions on Lua's math table. */
final class MLuaMathLibrary {
   private MLuaMathLibrary() { }

   static void install(LuaTable math) {
      math.set("rad", new OneArgFunction() {
         public LuaValue call(LuaValue degrees) {
            return LuaValue.valueOf(Math.toRadians(degrees.checkdouble()));
         }
      });
      math.set("deg", new OneArgFunction() {
         public LuaValue call(LuaValue radians) {
            return LuaValue.valueOf(Math.toDegrees(radians.checkdouble()));
         }
      });
      math.set("yaw", new OneArgFunction() {
         public LuaValue call(LuaValue degrees) {
            return LuaValue.valueOf(Math.toRadians(degrees.checkdouble()));
         }
      });
      math.set("pitch", math.get("yaw"));
      math.set("clamp", new ThreeArgFunction() {
         public LuaValue call(LuaValue value, LuaValue min, LuaValue max) {
            return LuaValue.valueOf(Math.max(min.checkdouble(), Math.min(max.checkdouble(), value.checkdouble())));
         }
      });
      math.set("round", new OneArgFunction() {
         public LuaValue call(LuaValue value) {
            return LuaValue.valueOf(Math.floor(value.checkdouble() + 0.5));
         }
      });
      math.set("sign", new OneArgFunction() {
         public LuaValue call(LuaValue value) {
            return LuaValue.valueOf(Double.compare(value.checkdouble(), 0.0));
         }
      });
      math.set("lerp", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            if (args.narg() != 3) throw new LuaError("math.lerp expects start, finish, and alpha");
            LuaValue start = args.arg(1);
            LuaValue finish = args.arg(2);
            double alpha = args.checkdouble(3);
            if (!Double.isFinite(alpha)) throw new LuaError("math.lerp alpha must be finite");
            if (start.isnumber() && finish.isnumber()) {
               return LuaValue.valueOf(lerp(start.checkdouble(), finish.checkdouble(), alpha));
            }
            if (MLuaLuauTypes.isColor3(start) && MLuaLuauTypes.isColor3(finish)) {
               return MLuaLuauTypes.color3Lerp(start, finish, alpha);
            }
            if (MLuaVector.isVector2(start) && MLuaVector.isVector2(finish)) {
               double[] a = MLuaVector.components2(start), b = MLuaVector.components2(finish);
               return MLuaVector.vector2(lerp(a[0], b[0], alpha), lerp(a[1], b[1], alpha));
            }
            if (MLuaVector.isVector3(start) && MLuaVector.isVector3(finish)) {
               double[] a = MLuaVector.components3(start), b = MLuaVector.components3(finish);
               return MLuaVector.vector3(lerp(a[0], b[0], alpha), lerp(a[1], b[1], alpha), lerp(a[2], b[2], alpha));
            }
            if (MLuaVec4.isVec4(start) && MLuaVec4.isVec4(finish)) {
               LuaTable a = start.checktable(), b = finish.checktable();
               return MLuaVec4.vec4(lerp(a.get("X").checkdouble(), b.get("X").checkdouble(), alpha),
                  lerp(a.get("Y").checkdouble(), b.get("Y").checkdouble(), alpha),
                  lerp(a.get("Z").checkdouble(), b.get("Z").checkdouble(), alpha),
                  lerp(a.get("W").checkdouble(), b.get("W").checkdouble(), alpha));
            }
            if (MLuaCFrame.isCFrame(start) && MLuaCFrame.isCFrame(finish)) {
               return MLuaCFrame.fromLua(start).lerp(MLuaCFrame.fromLua(finish), alpha).toLua();
            }
            if (isMatrix(start, "Mat3") && isMatrix(finish, "Mat3")) {
               LuaTable a = start.checktable(), b = finish.checktable();
               return MLuaMat3.mat3(lerp(a.get("v11").checkdouble(), b.get("v11").checkdouble(), alpha),
                  lerp(a.get("v12").checkdouble(), b.get("v12").checkdouble(), alpha),
                  lerp(a.get("v13").checkdouble(), b.get("v13").checkdouble(), alpha),
                  lerp(a.get("v21").checkdouble(), b.get("v21").checkdouble(), alpha),
                  lerp(a.get("v22").checkdouble(), b.get("v22").checkdouble(), alpha),
                  lerp(a.get("v23").checkdouble(), b.get("v23").checkdouble(), alpha),
                  lerp(a.get("v31").checkdouble(), b.get("v31").checkdouble(), alpha),
                  lerp(a.get("v32").checkdouble(), b.get("v32").checkdouble(), alpha),
                  lerp(a.get("v33").checkdouble(), b.get("v33").checkdouble(), alpha));
            }
            if (isMatrix(start, "Mat4") && isMatrix(finish, "Mat4")) {
               LuaTable a = start.checktable(), b = finish.checktable();
               double[] values = new double[16];
               for (int row = 1, i = 0; row <= 4; row++) {
                  for (int column = 1; column <= 4; column++, i++) {
                     String key = "v" + row + column;
                     values[i] = lerp(a.get(key).checkdouble(), b.get(key).checkdouble(), alpha);
                  }
               }
               return MLuaMat4.mat4(values[0], values[1], values[2], values[3], values[4], values[5], values[6], values[7],
                  values[8], values[9], values[10], values[11], values[12], values[13], values[14], values[15]);
            }
            throw new LuaError("math.lerp endpoints must have the same type: number, Vector2, Vector3, Vec4, Color3, Mat3, Mat4, or CFrame");
         }
      });
      math.set("inverseLerp", new ThreeArgFunction() {
         public LuaValue call(LuaValue a, LuaValue b, LuaValue value) {
            double start = a.checkdouble();
            double range = b.checkdouble() - start;
            if (range == 0.0) throw new LuaError("math.inverseLerp endpoints must be different");
            return LuaValue.valueOf((value.checkdouble() - start) / range);
         }
      });
      math.set("smoothstep", new ThreeArgFunction() {
         public LuaValue call(LuaValue edge0, LuaValue edge1, LuaValue value) {
            double low = edge0.checkdouble();
            double high = edge1.checkdouble();
            if (low == high) throw new LuaError("math.smoothstep edges must be different");
            double t = Math.max(0.0, Math.min(1.0, (value.checkdouble() - low) / (high - low)));
            return LuaValue.valueOf(t * t * (3.0 - 2.0 * t));
         }
      });
      math.set("wrap", new ThreeArgFunction() {
         public LuaValue call(LuaValue value, LuaValue minimum, LuaValue maximum) {
            double low = minimum.checkdouble();
            double width = maximum.checkdouble() - low;
            if (!(width > 0.0) || !Double.isFinite(width)) throw new LuaError("math.wrap requires a finite increasing range");
            double wrapped = (value.checkdouble() - low) % width;
            if (wrapped < 0.0) wrapped += width;
            return LuaValue.valueOf(low + wrapped);
         }
      });
      math.set("noise", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            int dimensions = args.narg();
            if (dimensions < 1 || dimensions > 5) throw new LuaError("math.noise expects between 1 and 5 coordinates");
            double[] coordinates = new double[dimensions];
            for (int i = 0; i < dimensions; i++) coordinates[i] = args.checkdouble(i + 1);
            return LuaValue.valueOf(MLuaNoise.sample(coordinates));
         }
      });
   }

   private static double lerp(double a, double b, double t) {
      return a + (b - a) * t;
   }

   private static boolean isMatrix(LuaValue value, String type) {
      return value.istable() && type.equals(value.get("__type").optjstring(null));
   }
}
