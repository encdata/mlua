package dev.mlua.modules.entity;

import dev.mlua.math.MLuaVector;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

/** Shared conversion and geometry operations for the public Lua EntityBounds class. */
public final class MLuaEntityBounds {
   private MLuaEntityBounds() {}

   public static AABB fromLua(LuaValue value) {
      if (!value.istable() || !value.get("__type").isstring()
            || !"EntityBounds".equals(value.get("__type").tojstring())) {
         throw new LuaError("[MLua] Expected an EntityBounds instance.");
      }
      LuaValue minimum = value.get("Minimum");
      LuaValue maximum = value.get("Maximum");
      if (!MLuaVector.isVector3(minimum) || !MLuaVector.isVector3(maximum)) {
         throw new LuaError("[MLua] EntityBounds must contain Vector3 Minimum and Maximum corners.");
      }
      double[] min = MLuaVector.components3(minimum);
      double[] max = MLuaVector.components3(maximum);
      requireFinite(min, "EntityBounds minimum");
      requireFinite(max, "EntityBounds maximum");
      if (min[0] > max[0] || min[1] > max[1] || min[2] > max[2]) {
         throw new LuaError("[MLua] EntityBounds corners must be finite and ordered from minimum to maximum.");
      }
      return new AABB(min[0], min[1], min[2], max[0], max[1], max[2]);
   }

   public static Vec3 rayHitNormal(AABB bounds, Vec3 hit) {
      double[] distances = {
         Math.abs(hit.x - bounds.minX), Math.abs(bounds.maxX - hit.x),
         Math.abs(hit.y - bounds.minY), Math.abs(bounds.maxY - hit.y),
         Math.abs(hit.z - bounds.minZ), Math.abs(bounds.maxZ - hit.z)
      };
      int nearest = 0;
      for (int index = 1; index < distances.length; index++) {
         if (distances[index] < distances[nearest]) nearest = index;
      }
      return switch (nearest) {
         case 0 -> new Vec3(-1, 0, 0);
         case 1 -> new Vec3(1, 0, 0);
         case 2 -> new Vec3(0, -1, 0);
         case 3 -> new Vec3(0, 1, 0);
         case 4 -> new Vec3(0, 0, -1);
         default -> new Vec3(0, 0, 1);
      };
   }

   private static void requireFinite(double[] values, String argument) {
      for (double value : values) {
         if (!Double.isFinite(value)) {
            throw new LuaError("[MLua] " + argument + " must contain finite coordinates.");
         }
      }
   }
}
