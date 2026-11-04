package dev.mlua.math;

import org.luaj.vm2.LuaError;

public final class MLuaNoise {
   private MLuaNoise() {
   }

   public static double sample(double... coordinates) {
      if (coordinates.length < 1 || coordinates.length > 5) {
         throw new LuaError("math.noise expects between 1 and 5 coordinates");
      }
      return switch (coordinates.length) {
         case 1 -> sample(coordinates[0]);
         case 2 -> sample(coordinates[0], coordinates[1]);
         case 3 -> sample(coordinates[0], coordinates[1], coordinates[2]);
         case 4 -> sample(coordinates[0], coordinates[1], coordinates[2], coordinates[3]);
         case 5 -> sample(coordinates[0], coordinates[1], coordinates[2], coordinates[3], coordinates[4]);
         default -> throw new LuaError("math.noise expects between 1 and 5 coordinates");
      };
   }

   public static double sample(double x) {
      return sampleFixed(1, x, 0.0, 0.0, 0.0, 0.0);
   }

   public static double sample(double x, double y) {
      return sampleFixed(2, x, y, 0.0, 0.0, 0.0);
   }

   public static double sample(double x, double y, double z) {
      return sampleFixed(3, x, y, z, 0.0, 0.0);
   }

   public static double sample(double x, double y, double z, double w) {
      return sampleFixed(4, x, y, z, w, 0.0);
   }

   public static double sample(double x, double y, double z, double w, double v) {
      return sampleFixed(5, x, y, z, w, v);
   }

   private static double sampleFixed(int dimensions, double x, double y, double z, double w, double v) {
      long lowerX = lowerCoordinate(x);
      long lowerY = dimensions > 1 ? lowerCoordinate(y) : 0L;
      long lowerZ = dimensions > 2 ? lowerCoordinate(z) : 0L;
      long lowerW = dimensions > 3 ? lowerCoordinate(w) : 0L;
      long lowerV = dimensions > 4 ? lowerCoordinate(v) : 0L;
      double blendX = smoothBlend(x, lowerX);
      double blendY = dimensions > 1 ? smoothBlend(y, lowerY) : 0.0;
      double blendZ = dimensions > 2 ? smoothBlend(z, lowerZ) : 0.0;
      double blendW = dimensions > 3 ? smoothBlend(w, lowerW) : 0.0;
      double blendV = dimensions > 4 ? smoothBlend(v, lowerV) : 0.0;

      double result = 0.0;
      int corners = 1 << dimensions;
      for (int corner = 0; corner < corners; corner++) {
         double weight = 1.0;
         long hash = mix64(0x9E3779B97F4A7C15L ^ dimensions);
         for (int axis = 0; axis < dimensions; axis++) {
            boolean upper = (corner & (1 << axis)) != 0;
            long lower = switch (axis) {
               case 0 -> lowerX;
               case 1 -> lowerY;
               case 2 -> lowerZ;
               case 3 -> lowerW;
               default -> lowerV;
            };
            double blend = switch (axis) {
               case 0 -> blendX;
               case 1 -> blendY;
               case 2 -> blendZ;
               case 3 -> blendW;
               default -> blendV;
            };
            long lattice = lower + (upper ? 1L : 0L);
            weight *= upper ? blend : 1.0 - blend;
            hash = mix64(hash ^ mix64(lattice + 0x9E3779B97F4A7C15L * (axis + 1L)));
         }
         double value = ((hash >>> 11) * 0x1.0p-52) - 1.0;
         result += value * weight;
      }
      return Math.max(-1.0, Math.min(1.0, result));
   }

   private static long lowerCoordinate(double coordinate) {
      if (!Double.isFinite(coordinate)) throw new LuaError("math.noise coordinates must be finite");
      double floor = Math.floor(coordinate);
      if (floor <= Long.MIN_VALUE || floor >= Long.MAX_VALUE) {
         throw new LuaError("math.noise coordinate is outside the supported range");
      }
      return (long)floor;
   }

   private static double smoothBlend(double coordinate, long lower) {
      double fraction = coordinate - (double)lower;
      return fraction * fraction * fraction * (fraction * (fraction * 6.0 - 15.0) + 10.0);
   }

   private static long mix64(long value) {
      value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
      value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
      return value ^ (value >>> 31);
   }
}
