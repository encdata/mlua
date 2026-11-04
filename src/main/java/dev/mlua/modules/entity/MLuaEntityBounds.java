package dev.mlua.modules.entity;

import dev.mlua.math.MLuaVector;
import dev.mlua.module.MLuaRegistration;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Shared conversion and geometry operations for the public Lua EntityBounds class. */
public final class MLuaEntityBounds {
   private static final Vec3 NEGATIVE_X = new Vec3(-1, 0, 0);
   private static final Vec3 POSITIVE_X = new Vec3(1, 0, 0);
   private static final Vec3 NEGATIVE_Y = new Vec3(0, -1, 0);
   private static final Vec3 POSITIVE_Y = new Vec3(0, 1, 0);
   private static final Vec3 NEGATIVE_Z = new Vec3(0, 0, -1);
   private static final Vec3 POSITIVE_Z = new Vec3(0, 0, 1);

   private MLuaEntityBounds() {}

   /** Creates the single public Lua representation used by native entities and Lua constructors. */
   public static LuaTable toLua(MLuaRegistration registration, AABB bounds) {
      return createLua(registration, bounds.minX, bounds.minY, bounds.minZ,
         bounds.maxX, bounds.maxY, bounds.maxZ);
   }

   public static LuaTable createLua(MLuaRegistration registration,
         double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
      if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)
            || !Double.isFinite(maxX) || !Double.isFinite(maxY) || !Double.isFinite(maxZ)
            || minX > maxX || minY > maxY || minZ > maxZ) {
         throw new LuaError("[MLua] EntityBounds corners must be finite and ordered from minimum to maximum.");
      }
      LuaTable result = registration.createClassInstance("EntityBounds");
      result.rawset("Minimum", MLuaVector.vector3(minX, minY, minZ));
      result.rawset("Maximum", MLuaVector.vector3(maxX, maxY, maxZ));
      result.freeze();
      return result;
   }

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
      double nearestDistance = Math.abs(hit.x - bounds.minX);
      Vec3 nearestNormal = NEGATIVE_X;
      double distance = Math.abs(bounds.maxX - hit.x);
      if (distance < nearestDistance) {
         nearestDistance = distance;
         nearestNormal = POSITIVE_X;
      }
      distance = Math.abs(hit.y - bounds.minY);
      if (distance < nearestDistance) {
         nearestDistance = distance;
         nearestNormal = NEGATIVE_Y;
      }
      distance = Math.abs(bounds.maxY - hit.y);
      if (distance < nearestDistance) {
         nearestDistance = distance;
         nearestNormal = POSITIVE_Y;
      }
      distance = Math.abs(hit.z - bounds.minZ);
      if (distance < nearestDistance) {
         nearestDistance = distance;
         nearestNormal = NEGATIVE_Z;
      }
      distance = Math.abs(bounds.maxZ - hit.z);
      if (distance < nearestDistance) nearestNormal = POSITIVE_Z;
      return nearestNormal;
   }

   private static void requireFinite(double[] values, String argument) {
      for (double value : values) {
         if (!Double.isFinite(value)) {
            throw new LuaError("[MLua] " + argument + " must contain finite coordinates.");
         }
      }
   }
}
