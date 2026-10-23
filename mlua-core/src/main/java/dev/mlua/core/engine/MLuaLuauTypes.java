package dev.mlua.core.engine;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.ThreeArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.math.MLuaMat3;
import dev.mlua.math.MLuaMat4;
import dev.mlua.math.MLuaVec4;
import dev.mlua.math.MLuaVector;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class MLuaLuauTypes {
   private static final LuaTable UDIM_METATABLE = valueMetatable("UDim");
   private static final LuaTable UDIM2_METATABLE = valueMetatable("UDim2");
   private static final LuaTable COLOR3_METATABLE = color3Metatable();
   private static final Set<LuaTable> UDIM_VALUES = Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<>()));
   private static final Set<LuaTable> UDIM2_VALUES = Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<>()));
   private static final Set<LuaTable> COLOR3_VALUES = Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<>()));

   private MLuaLuauTypes() {
   }

   public static void install(LuaTable globals) {
      globals.set("Vector2", vector2Library());
      LuaTable vector3 = vector3Library();
      globals.set("Vector3", vector3);
      LuaTable vectors = vectorAlias(vector3);
      globals.set("vector", vectors);
      globals.set("vectors", vectors);
      globals.set("CFrame", MLuaCFrame.library());
      globals.set("Vec4", vec4Library());
      globals.set("Mat3", mat3Library());
      globals.set("Mat4", mat4Library());
      globals.set("Color3", color3Library());
      globals.set("UDim", udimLibrary());
      globals.set("UDim2", udim2Library());
      MLuaMathLibrary.install(globals.get("math").checktable());
      MLuaLuauStandardLibraries.install(globals);
   }

   public static LuaValue typeName(LuaValue value) {
      if (value.istable()) {
         LuaValue type = value.checktable().get("__type");
         if (type.isstring()) {
            if (!isRegisteredValue(type.checkjstring(), value)) return LuaValue.valueOf("table");
            return type;
         }
      }

      return LuaValue.valueOf(value.typename());
   }

   private static boolean isRegisteredValue(String type, LuaValue value) {
      return switch (type) {
         case "Vector2" -> MLuaVector.isVector2(value);
         case "Vector3" -> MLuaVector.isVector3(value);
         case "Vec4" -> MLuaVec4.isVec4(value);
         case "CFrame" -> MLuaCFrame.isCFrame(value);
         case "UDim" -> isUDim(value);
         case "UDim2" -> isUDim2(value);
         case "Color3" -> isColor3(value);
         default -> true;
      };
   }

   private static LuaTable vector2Library() {
      LuaTable table = new LuaTable();
      table.set("new", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            return MLuaVector.vector2(args.optdouble(1, 0.0), args.optdouble(2, 0.0));
         }
      });
      table.set("zero", MLuaVector.vector2(0.0, 0.0));
      table.set("one", MLuaVector.vector2(1.0, 1.0));
      table.set("xAxis", MLuaVector.vector2(1.0, 0.0));
      table.set("yAxis", MLuaVector.vector2(0.0, 1.0));
      table.set("Zero", table.get("zero"));
      table.set("One", table.get("one"));
      table.set("XAxis", table.get("xAxis"));
      table.set("YAxis", table.get("yAxis"));
      return table;
   }

   private static LuaTable vector3Library() {
      LuaTable table = new LuaTable();
      table.set("new", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            return MLuaVector.vector3(args.optdouble(1, 0.0), args.optdouble(2, 0.0), args.optdouble(3, 0.0));
         }
      });
      table.set("zero", MLuaVector.vector3(0.0, 0.0, 0.0));
      table.set("one", MLuaVector.vector3(1.0, 1.0, 1.0));
      table.set("xAxis", MLuaVector.vector3(1.0, 0.0, 0.0));
      table.set("yAxis", MLuaVector.vector3(0.0, 1.0, 0.0));
      table.set("zAxis", MLuaVector.vector3(0.0, 0.0, 1.0));
      table.set("Zero", table.get("zero"));
      table.set("One", table.get("one"));
      table.set("XAxis", table.get("xAxis"));
      table.set("YAxis", table.get("yAxis"));
      table.set("ZAxis", table.get("zAxis"));
      return table;
   }

   private static LuaTable vec4Library() {
      LuaTable table = new LuaTable();
      table.set("new", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            return MLuaVec4.vec4(args.optdouble(1, 0.0), args.optdouble(2, 0.0), args.optdouble(3, 0.0), args.optdouble(4, 0.0));
         }
      });
      table.set("zero", MLuaVec4.vec4(0.0, 0.0, 0.0, 0.0));
      table.set("one", MLuaVec4.vec4(1.0, 1.0, 1.0, 1.0));
      table.set("Zero", table.get("zero"));
      table.set("One", table.get("one"));
      return table;
   }

   private static LuaTable mat3Library() {
      LuaTable table = new LuaTable();
      table.set("new", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            if (args.narg() == 0) {
               return MLuaMat3.identity();
            } else if (args.narg() == 3 && args.arg(1).istable() && args.arg(2).istable() && args.arg(3).istable()) {
               return MLuaMat3.fromColumns(args.checktable(1), args.checktable(2), args.checktable(3));
            } else if (args.narg() == 9) {
               return MLuaMat3.mat3(args.checkdouble(1), args.checkdouble(2), args.checkdouble(3), args.checkdouble(4), args.checkdouble(5), args.checkdouble(6), args.checkdouble(7), args.checkdouble(8), args.checkdouble(9));
            } else {
               throw new LuaError("[MLua] Mat3.new expects 0 args, 3 column vectors, or 9 numbers");
            }
         }
      });
      table.set("identity", MLuaMat3.identity());
      table.set("Identity", table.get("identity"));
      table.set("fromColumns", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            return MLuaMat3.fromColumns(args.checktable(1), args.checktable(2), args.checktable(3));
         }
      });
      table.set("rotationX", new OneArgFunction() {
         public LuaValue call(LuaValue degrees) {
            return MLuaMat3.rotationX(degrees.checkdouble());
         }
      });
      table.set("rotationY", new OneArgFunction() {
         public LuaValue call(LuaValue degrees) {
            return MLuaMat3.rotationY(degrees.checkdouble());
         }
      });
      table.set("rotationZ", new OneArgFunction() {
         public LuaValue call(LuaValue degrees) {
            return MLuaMat3.rotationZ(degrees.checkdouble());
         }
      });
      return table;
   }

   private static LuaTable mat4Library() {
      LuaTable table = new LuaTable();
      table.set("new", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            if (args.narg() == 0) {
               return MLuaMat4.identity();
            } else if (args.narg() == 4 && args.arg(1).istable() && args.arg(2).istable() && args.arg(3).istable() && args.arg(4).istable()) {
               return MLuaMat4.fromColumns(args.checktable(1), args.checktable(2), args.checktable(3), args.checktable(4));
            } else if (args.narg() == 16) {
               return MLuaMat4.mat4(args.checkdouble(1), args.checkdouble(2), args.checkdouble(3), args.checkdouble(4), args.checkdouble(5), args.checkdouble(6), args.checkdouble(7), args.checkdouble(8), args.checkdouble(9), args.checkdouble(10), args.checkdouble(11), args.checkdouble(12), args.checkdouble(13), args.checkdouble(14), args.checkdouble(15), args.checkdouble(16));
            } else {
               throw new LuaError("[MLua] Mat4.new expects 0 args, 4 column vectors, or 16 numbers");
            }
         }
      });
      table.set("identity", MLuaMat4.identity());
      table.set("Identity", table.get("identity"));
      table.set("fromColumns", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            return MLuaMat4.fromColumns(args.checktable(1), args.checktable(2), args.checktable(3), args.checktable(4));
         }
      });
      table.set("translation", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            return MLuaMat4.translationMatrix(args.checkdouble(1), args.checkdouble(2), args.checkdouble(3));
         }
      });
      table.set("scale", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            return MLuaMat4.scaleMatrix(args.checkdouble(1), args.checkdouble(2), args.checkdouble(3));
         }
      });
      table.set("rotationX", new OneArgFunction() {
         public LuaValue call(LuaValue degrees) {
            return MLuaMat4.rotationXMatrix(degrees.checkdouble());
         }
      });
      table.set("rotationY", new OneArgFunction() {
         public LuaValue call(LuaValue degrees) {
            return MLuaMat4.rotationYMatrix(degrees.checkdouble());
         }
      });
      table.set("rotationZ", new OneArgFunction() {
         public LuaValue call(LuaValue degrees) {
            return MLuaMat4.rotationZMatrix(degrees.checkdouble());
         }
      });
      return table;
   }

   private static LuaTable vectorAlias(LuaTable vector3) {
      LuaTable table = new LuaTable();
      table.set("new", vector3.get("new"));
      table.set("create", vector3.get("new"));
      table.set("zero", vector3.get("zero"));
      table.set("one", vector3.get("one"));
      table.set("vec", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            if (args.narg() == 2) return vector2(args.checkdouble(1), args.checkdouble(2));
            if (args.narg() == 3) return vector3(args.checkdouble(1), args.checkdouble(2), args.checkdouble(3));
            if (args.narg() == 4) return MLuaVec4.vec4(args.checkdouble(1), args.checkdouble(2), args.checkdouble(3), args.checkdouble(4));
            throw new LuaError("vectors.vec expects two, three, or four numbers");
         }
      });
      table.set("vec2", vector2Library().get("new"));
      table.set("vec3", vector3.get("new"));
      table.set("vec4", vec4Library().get("new"));
      table.set("rgbToInt", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            double[] rgb = normalizedRgb(args, "vectors.rgbToInt");
            int red = (int)Math.round(clamp01(rgb[0]) * 255.0);
            int green = (int)Math.round(clamp01(rgb[1]) * 255.0);
            int blue = (int)Math.round(clamp01(rgb[2]) * 255.0);
            return LuaValue.valueOf(red << 16 | green << 8 | blue);
         }
      });
      table.set("intToRGB", new OneArgFunction() {
         @Override public LuaValue call(LuaValue value) {
            LuaTable color = color3FromArgb(value.checkint());
            return color.get("ToVector3").call(color);
         }
      });
      table.set("hexToRGB", new OneArgFunction() {
         @Override public LuaValue call(LuaValue hex) {
            LuaTable color = color3FromHex(hex.checkjstring());
            return color.get("ToVector3").call(color);
         }
      });
      table.set("hsvToRGB", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            double[] hsv = hsvValues(args, "vectors.hsvToRGB");
            LuaTable color = colorFromHSV(hsv[0], hsv[1], hsv[2]);
            return color.get("ToVector3").call(color);
         }
      });
      table.set("rgbToHSV", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            double[] rgb = normalizedRgb(args, "vectors.rgbToHSV");
            double[] hsv = rgbToHsv(rgb[0], rgb[1], rgb[2]);
            return vector3(hsv[0], hsv[1], hsv[2]);
         }
      });
      table.set("rgbToHex", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            double[] rgb = normalizedRgb(args, "vectors.rgbToHex");
            LuaTable color = color3(rgb[0], rgb[1], rgb[2]);
            return color.get("ToHex").call(color);
         }
      });
      return table;
   }

   private static double[] normalizedRgb(Varargs args, String function) {
      double[] values = colorComponents(args, function);
      for (int i = 0; i < values.length; i++) values[i] = clamp01(values[i]);
      return values;
   }

   private static double[] hsvValues(Varargs args, String function) {
      double[] values = colorComponents(args, function);
      values[1] = clamp01(values[1]);
      values[2] = clamp01(values[2]);
      return values;
   }

   private static double[] colorComponents(Varargs args, String function) {
      double[] values;
      if (args.narg() == 1) values = MLuaVector.components3(args.arg1());
      else if (args.narg() == 3) values = new double[] {args.checkdouble(1), args.checkdouble(2), args.checkdouble(3)};
      else throw new LuaError(function + " expects a Vector3 or three numbers");
      for (double value : values) {
         if (!Double.isFinite(value)) throw new LuaError(function + " channel values must be finite");
      }
      return values;
   }

   private static LuaTable color3Library() {
      LuaTable table = new LuaTable();
      table.set("new", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            return MLuaLuauTypes.color3(args.optdouble(1, 0.0), args.optdouble(2, 0.0), args.optdouble(3, 0.0));
         }
      });
      table.set("fromRGB", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            if (args.narg() == 1 && MLuaVector.isVector3(args.arg1())) {
               double[] rgb = MLuaVector.components3(args.arg1());
               return MLuaLuauTypes.color3(rgb[0] / 255.0, rgb[1] / 255.0, rgb[2] / 255.0);
            }
            return MLuaLuauTypes.color3(args.checkdouble(1) / 255.0, args.checkdouble(2) / 255.0, args.checkdouble(3) / 255.0);
         }
      });
      table.set("fromVector3", new OneArgFunction() {
         public LuaValue call(LuaValue value) {
            double[] rgb = MLuaVector.components3(value);
            return MLuaLuauTypes.color3(rgb[0], rgb[1], rgb[2]);
         }
      });
      table.set("fromHSV", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            return MLuaLuauTypes.colorFromHSV(args.checkdouble(1), args.checkdouble(2), args.checkdouble(3));
         }
      });
      table.set("fromHex", new OneArgFunction() {
         public LuaValue call(LuaValue hex) {
            return color3FromHex(hex.checkjstring());
         }
      });
      return table;
   }

   private static LuaTable udimLibrary() {
      LuaTable table = new LuaTable();
      table.set("new", new TwoArgFunction() {
         public LuaValue call(LuaValue scale, LuaValue offset) {
            return MLuaLuauTypes.udim(scale.checkdouble(),
               wholeInt(offset, "UDim.new", "offset"));
         }
      });
      return table;
   }

   private static LuaTable udim2Library() {
      LuaTable table = new LuaTable();
      table.set("new", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            if (args.narg() == 2 && args.arg(1).istable() && args.arg(2).istable()) {
               return MLuaLuauTypes.udim2(args.arg(1), args.arg(2));
            }
            if (args.narg() != 4) {
               throw new LuaError("UDim2.new expects two UDim values or four numeric scale and offset values");
            }
            return MLuaLuauTypes.udim2(args.arg(1).checkdouble(),
               wholeInt(args.arg(2), "UDim2.new", "xOffset"),
               args.arg(3).checkdouble(),
               wholeInt(args.arg(4), "UDim2.new", "yOffset"));
         }
      });
      table.set("fromScale", new TwoArgFunction() {
         public LuaValue call(LuaValue x, LuaValue y) {
            return MLuaLuauTypes.udim2(x.checkdouble(), 0, y.checkdouble(), 0);
         }
      });
      table.set("fromOffset", new TwoArgFunction() {
         public LuaValue call(LuaValue x, LuaValue y) {
            return MLuaLuauTypes.udim2(0.0,
               wholeInt(x, "UDim2.fromOffset", "xPixels"),
               0.0,
               wholeInt(y, "UDim2.fromOffset", "yPixels"));
         }
      });
      return table;
   }

   private static LuaTable vector2(double x, double y) {
      return MLuaVector.vector2(x, y);
   }

   private static LuaTable vector3(double x, double y, double z) {
      return MLuaVector.vector3(x, y, z);
   }

   private static LuaTable color3(double r, double g, double b) {
      LuaTable value = typed("Color3");
      value.set("R", clamp01(r));
      value.set("G", clamp01(g));
      value.set("B", clamp01(b));
      value.set("r", value.get("R"));
      value.set("g", value.get("G"));
      value.set("b", value.get("B"));
      value.set("ToHSV", new VarArgFunction() {
         public Varargs invoke(Varargs self) {
            LuaTable c = MLuaLuauTypes.requireColor3(self.arg1());
            double[] hsv = MLuaLuauTypes.rgbToHsv(c.get("R").todouble(), c.get("G").todouble(), c.get("B").todouble());
            return LuaValue.varargsOf(new LuaValue[]{LuaValue.valueOf(hsv[0]), LuaValue.valueOf(hsv[1]), LuaValue.valueOf(hsv[2])});
         }
      });
      value.set("ToHex", new VarArgFunction() {
         public Varargs invoke(Varargs self) {
            LuaTable c = MLuaLuauTypes.requireColor3(self.arg1());
            int rv = (int)Math.round(MLuaLuauTypes.clamp01(c.get("R").todouble()) * 255.0);
            int gv = (int)Math.round(MLuaLuauTypes.clamp01(c.get("G").todouble()) * 255.0);
            int bv = (int)Math.round(MLuaLuauTypes.clamp01(c.get("B").todouble()) * 255.0);
            return LuaValue.valueOf(String.format("#%02x%02x%02x", rv, gv, bv));
         }
      });
      value.set("toHex", value.get("ToHex"));
      value.set("ToVector3", new VarArgFunction() {
         public Varargs invoke(Varargs self) {
            LuaTable c = MLuaLuauTypes.requireColor3(self.arg1());
            return MLuaVector.vector3(c.get("R").todouble(), c.get("G").todouble(), c.get("B").todouble());
         }
      });
      value.set("toVector3", value.get("ToVector3"));
      value.set("toHSV", value.get("ToHSV"));
      value.set("Lerp", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            return MLuaLuauTypes.color3Lerp(args.arg(1), args.arg(2), args.checkdouble(3));
         }
      });
      value.set("lerp", value.get("Lerp"));
      value.setmetatable(COLOR3_METATABLE);
      COLOR3_VALUES.add(value);
      return value.freeze();
   }

   private static LuaTable color3Metatable() {
      LuaTable metatable = new LuaTable();
      metatable.set("__metatable", LuaValue.valueOf("Color3"));
      metatable.set(LuaValue.EQ, new TwoArgFunction() {
         @Override public LuaValue call(LuaValue left, LuaValue right) {
            if (!isColor3(left) || !isColor3(right)) return LuaValue.FALSE;
            LuaTable a = left.checktable(), b = right.checktable();
            return LuaValue.valueOf(a.get("R").eq_b(b.get("R"))
               && a.get("G").eq_b(b.get("G")) && a.get("B").eq_b(b.get("B")));
         }
      });
      metatable.set(LuaValue.ADD, colorBinary((r1, g1, b1, r2, g2, b2) ->
         color3(r1 + r2, g1 + g2, b1 + b2)));
      metatable.set(LuaValue.SUB, colorBinary((r1, g1, b1, r2, g2, b2) ->
         color3(r1 - r2, g1 - g2, b1 - b2)));
      metatable.set(LuaValue.MUL, new TwoArgFunction() {
         @Override public LuaValue call(LuaValue left, LuaValue right) {
            if (isColor3(left) && isColor3(right)) {
               LuaTable a = left.checktable(), b = right.checktable();
               return color3(a.get("R").todouble() * b.get("R").todouble(),
                  a.get("G").todouble() * b.get("G").todouble(),
                  a.get("B").todouble() * b.get("B").todouble());
            }
            LuaValue color = isColor3(left) ? left : right;
            LuaValue scalar = color == left ? right : left;
            if (!isColor3(color) || !scalar.isnumber()) throw new LuaError("Color3 multiplication expects another Color3 or a number");
            LuaTable value = color.checktable();
            double factor = scalar.checkdouble();
            return color3(value.get("R").todouble() * factor,
               value.get("G").todouble() * factor, value.get("B").todouble() * factor);
         }
      });
      metatable.set(LuaValue.DIV, new TwoArgFunction() {
         @Override public LuaValue call(LuaValue left, LuaValue right) {
            if (isColor3(left) && isColor3(right)) {
               LuaTable a = left.checktable(), b = right.checktable();
               double r = b.get("R").todouble(), g = b.get("G").todouble(), blue = b.get("B").todouble();
               if (r == 0.0 || g == 0.0 || blue == 0.0) throw new LuaError("Color3 division by zero component");
               return color3(a.get("R").todouble() / r, a.get("G").todouble() / g, a.get("B").todouble() / blue);
            }
            if (!isColor3(left) || !right.isnumber()) throw new LuaError("Color3 division expects another Color3 or a number");
            double divisor = right.checkdouble();
            if (divisor == 0.0) throw new LuaError("Color3 division by zero");
            LuaTable color = left.checktable();
            return color3(color.get("R").todouble() / divisor,
               color.get("G").todouble() / divisor, color.get("B").todouble() / divisor);
         }
      });
      metatable.set("__tostring", new OneArgFunction() {
         @Override public LuaValue call(LuaValue value) {
            LuaTable color = requireColor3(value);
            return LuaValue.valueOf(String.format("Color3(%.4f, %.4f, %.4f)",
               color.get("R").todouble(), color.get("G").todouble(), color.get("B").todouble()));
         }
      });
      return metatable.freeze();
   }

   private static VarArgFunction colorBinary(ColorOperation operation) {
      return new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            LuaTable a = requireColor3(args.arg(1));
            LuaTable b = requireColor3(args.arg(2));
            return operation.apply(a.get("R").todouble(), a.get("G").todouble(), a.get("B").todouble(),
               b.get("R").todouble(), b.get("G").todouble(), b.get("B").todouble());
         }
      };
   }

   @FunctionalInterface
   private interface ColorOperation {
      LuaValue apply(double r1, double g1, double b1, double r2, double g2, double b2);
   }

   static LuaTable color3Lerp(LuaValue start, LuaValue end, double alpha) {
      LuaTable a = requireColor3(start);
      LuaTable b = requireColor3(end);
      if (!Double.isFinite(alpha)) throw new LuaError("math.lerp alpha must be finite");
      return color3(lerp(a.get("R").todouble(), b.get("R").todouble(), alpha),
         lerp(a.get("G").todouble(), b.get("G").todouble(), alpha),
         lerp(a.get("B").todouble(), b.get("B").todouble(), alpha));
   }

   private static LuaTable colorFromHSV(double h, double s, double v) {
      h -= Math.floor(h);
      double c = v * s;
      double x = c * (1.0 - Math.abs(h * 6.0 % 2.0 - 1.0));
      double m = v - c;
      double b;
      double r;
      double g;
      if (h < 0.16666666666666666) {
         r = c;
         g = x;
         b = 0.0;
      } else if (h < 0.3333333333333333) {
         r = x;
         g = c;
         b = 0.0;
      } else if (h < 0.5) {
         r = 0.0;
         g = c;
         b = x;
      } else if (h < 0.6666666666666666) {
         r = 0.0;
         g = x;
         b = c;
      } else if (h < 0.8333333333333334) {
         r = x;
         g = 0.0;
         b = c;
      } else {
         r = c;
         g = 0.0;
         b = x;
      }

      return color3(r + m, g + m, b + m);
   }

   private static LuaTable color3FromHex(String input) {
      String hex = input.trim();
      if (hex.startsWith("#")) hex = hex.substring(1);
      if (hex.length() == 3) {
         hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
      }
      if (hex.length() != 6 || !hex.matches("[0-9a-fA-F]{6}")) {
         throw new LuaError("Color3.fromHex expects a three- or six-digit hex string, optionally prefixed with #");
      }
      int rgb = Integer.parseInt(hex, 16);
      return color3((rgb >> 16 & 255) / 255.0, (rgb >> 8 & 255) / 255.0, (rgb & 255) / 255.0);
   }

   private static double[] rgbToHsv(double r, double g, double b) {
      double max = Math.max(r, Math.max(g, b));
      double min = Math.min(r, Math.min(g, b));
      double delta = max - min;
      double h;
      if (delta == 0.0) {
         h = 0.0;
      } else if (max == r) {
         h = (g - b) / delta / 6.0;
      } else if (max == g) {
         h = ((b - r) / delta + 2.0) / 6.0;
      } else {
         h = ((r - g) / delta + 4.0) / 6.0;
      }

      if (h < 0.0) {
         ++h;
      }

      double s = max == 0.0 ? 0.0 : delta / max;
      return new double[]{h, s, max};
   }

   public static LuaTable udim(double scale, int offset) {
      if (!Double.isFinite(scale)) {
         throw new LuaError("[MLua] UDim scale must be a finite number.");
      }
      LuaTable value = typed("UDim");
      value.set("Scale", scale);
      value.set("Offset", offset);
      value.setmetatable(UDIM_METATABLE);
      UDIM_VALUES.add(value);
      return value.freeze();
   }

   public static LuaTable udim2(double xScale, int xOffset, double yScale, int yOffset) {
      LuaTable value = typed("UDim2");
      value.set("X", udim(xScale, xOffset));
      value.set("Y", udim(yScale, yOffset));
      value.setmetatable(UDIM2_METATABLE);
      UDIM2_VALUES.add(value);
      return value.freeze();
   }

   public static LuaTable udim2(LuaValue x, LuaValue y) {
      if (!isUDim(x) || !isUDim(y)) {
         throw new LuaError("UDim2.new expects two UDim values or four numeric scale and offset values");
      }
      LuaTable value = typed("UDim2");
      value.set("X", x);
      value.set("Y", y);
      value.setmetatable(UDIM2_METATABLE);
      UDIM2_VALUES.add(value);
      return value.freeze();
   }

   public static boolean isUDim(LuaValue value) {
      return value.istable() && value.checktable().isfrozen() && UDIM_VALUES.contains(value.checktable());
   }

   public static boolean isUDim2(LuaValue value) {
      return value.istable() && value.checktable().isfrozen() && UDIM2_VALUES.contains(value.checktable());
   }

   public static boolean isColor3(LuaValue value) {
      return value.istable() && value.checktable().isfrozen() && COLOR3_VALUES.contains(value.checktable());
   }

   private static LuaTable requireColor3(LuaValue value) {
      if (!isColor3(value)) throw new LuaError("Expected a native Color3 value");
      return value.checktable();
   }

   public static int color3Argb(LuaValue value) {
      if (!isColor3(value)) throw new LuaError("Expected a native Color3 value");
      LuaTable color = value.checktable();
      int red = (int)Math.round(color.get("R").checkdouble() * 255.0);
      int green = (int)Math.round(color.get("G").checkdouble() * 255.0);
      int blue = (int)Math.round(color.get("B").checkdouble() * 255.0);
      return 0xFF000000 | red << 16 | green << 8 | blue;
   }

   public static LuaTable color3FromArgb(int color) {
      return color3((double)(color >> 16 & 255) / 255.0,
         (double)(color >> 8 & 255) / 255.0, (double)(color & 255) / 255.0);
   }

   private static LuaTable valueMetatable(String type) {
      LuaTable metatable = new LuaTable();
      metatable.set("__metatable", LuaValue.valueOf(type));
      return metatable.freeze();
   }

   private static LuaTable typed(String type) {
      LuaTable table = new LuaTable();
      table.set("__type", type);
      return table;
   }

   private static double clamp01(double value) {
      if (!Double.isFinite(value)) throw new LuaError("Color3 components must be finite numbers");
      return Math.max(0.0, Math.min(1.0, value));
   }

   private static int wholeInt(LuaValue value, String method, String parameter) {
      if (!value.isnumber()) {
         throw new LuaError("[MLua] " + method + " " + parameter + " must be an integer number.");
      }
      double number = value.todouble();
      if (!Double.isFinite(number) || number != Math.rint(number)
          || number < Long.MIN_VALUE || number >= 0x1.0p63) {
         throw new LuaError("[MLua] " + method + " " + parameter
            + " must be a whole number in the 64-bit integer range.");
      }
      if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
         throw new LuaError("[MLua] " + method + " " + parameter + " must fit in a 32-bit integer.");
      }
      return (int)number;
   }
}
