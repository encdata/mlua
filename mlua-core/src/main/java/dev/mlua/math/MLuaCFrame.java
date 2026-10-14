package dev.mlua.math;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaUserdata;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.ThreeArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;

public final class MLuaCFrame {
   public static final MLuaCFrame IDENTITY = new MLuaCFrame(0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0);
   private final double x;
   private final double y;
   private final double z;
   private final double r00;
   private final double r01;
   private final double r02;
   private final double r10;
   private final double r11;
   private final double r12;
   private final double r20;
   private final double r21;
   private final double r22;

   public MLuaCFrame(double x, double y, double z, double r00, double r01, double r02, double r10, double r11, double r12, double r20, double r21, double r22) {
      this.x = x;
      this.y = y;
      this.z = z;
      this.r00 = r00;
      this.r01 = r01;
      this.r02 = r02;
      this.r10 = r10;
      this.r11 = r11;
      this.r12 = r12;
      this.r20 = r20;
      this.r21 = r21;
      this.r22 = r22;
   }

   public static LuaTable library() {
      LuaTable table = new LuaTable();
      table.set("new", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            LuaValue arg1 = args.arg(1);
            LuaValue arg2 = args.arg(2);
            if (arg1.istable()) {
               double[] p;
               if (arg2.istable()) {
                  p = MLuaCFrame.vector(arg1);
                  double[] p2 = MLuaCFrame.vector(arg2);
                  return MLuaCFrame.lookAt(p[0], p[1], p[2], p2[0], p2[1], p2[2]).toLua();
               } else {
                  p = MLuaCFrame.vector(arg1);
                  return MLuaCFrame.at(p[0], p[1], p[2]).toLua();
               }
            } else {
               return MLuaCFrame.at(args.optdouble(1, 0.0), args.optdouble(2, 0.0), args.optdouble(3, 0.0)).toLua();
            }
         }
      });
      table.set("Angles", new ThreeArgFunction() {
         public LuaValue call(LuaValue rx, LuaValue ry, LuaValue rz) {
            return MLuaCFrame.angles(rx.checkdouble(), ry.checkdouble(), rz.checkdouble()).toLua();
         }
      });
      table.set("fromEulerAnglesXYZ", table.get("Angles"));
      table.set("fromOrientation", table.get("Angles"));
      table.set("fromYawPitch", new TwoArgFunction() {
         public LuaValue call(LuaValue yaw, LuaValue pitch) {
            return MLuaCFrame.fromMinecraftRotation(yaw.checkdouble(), pitch.checkdouble()).toLua();
         }
      });
      table.set("fromYawPitchRadians", new TwoArgFunction() {
         public LuaValue call(LuaValue yaw, LuaValue pitch) {
            return MLuaCFrame.fromMinecraftRotationRadians(yaw.checkdouble(), pitch.checkdouble()).toLua();
         }
      });
      table.set("lookAt", new TwoArgFunction() {
         public LuaValue call(LuaValue position, LuaValue target) {
            double[] p = MLuaCFrame.vector(position);
            double[] t = MLuaCFrame.vector(target);
            return MLuaCFrame.lookAt(p[0], p[1], p[2], t[0], t[1], t[2]).toLua();
         }
      });
      table.set("lookAlong", new TwoArgFunction() {
         public LuaValue call(LuaValue position, LuaValue direction) {
            double[] p = MLuaCFrame.vector(position);
            double[] d = MLuaCFrame.vector(direction);
            return MLuaCFrame.lookAlong(p[0], p[1], p[2], d[0], d[1], d[2]).toLua();
         }
      });
      table.set("LookAlong", table.get("lookAlong"));
      table.set("identity", IDENTITY.toLua());
      table.set("Identity", table.get("identity"));
      return table;
   }

   public static MLuaCFrame at(double x, double y, double z) {
      return new MLuaCFrame(x, y, z, 1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0);
   }

   public static MLuaCFrame angles(double rx, double ry, double rz) {
      double cx = Math.cos(rx);
      double sx = Math.sin(rx);
      double cy = Math.cos(ry);
      double sy = Math.sin(ry);
      double cz = Math.cos(rz);
      double sz = Math.sin(rz);
      double r00 = cy * cz;
      double r01 = -cy * sz;
      double r10 = sx * sy * cz + cx * sz;
      double r11 = -sx * sy * sz + cx * cz;
      double r12 = -sx * cy;
      double r20 = -cx * sy * cz + sx * sz;
      double r21 = cx * sy * sz + sx * cz;
      double r22 = cx * cy;
      return new MLuaCFrame(0.0, 0.0, 0.0, r00, r01, sy, r10, r11, r12, r20, r21, r22);
   }

   public static MLuaCFrame lookAt(double x, double y, double z, double tx, double ty, double tz) {
      return lookAlong(x, y, z, tx - x, ty - y, tz - z);
   }

   public static MLuaCFrame lookAlong(double x, double y, double z, double dx, double dy, double dz) {
      double[] look = normalize(dx, dy, dz, 0.0, 0.0, -1.0);
      double[] upHint = Math.abs(look[1]) > 0.999 ? new double[]{0.0, 0.0, 1.0} : new double[]{0.0, 1.0, 0.0};
      double[] back = new double[]{-look[0], -look[1], -look[2]};
      double[] right = normalize(cross(upHint, back), 1.0, 0.0, 0.0);
      double[] up = cross(back, right);
      return fromBasis(x, y, z, right, up, back);
   }

   public static MLuaCFrame fromEntity(double x, double y, double z, double lookX, double lookY, double lookZ) {
      return lookAt(x, y, z, x + lookX, y + lookY, z + lookZ);
   }

   public static MLuaCFrame fromMinecraftRotation(double yawDegrees, double pitchDegrees) {
      return fromMinecraftRotationRadians(Math.toRadians(yawDegrees), Math.toRadians(pitchDegrees));
   }

   public static MLuaCFrame fromMinecraftRotationRadians(double yaw, double pitch) {
      double lookX = -Math.sin(yaw) * Math.cos(pitch);
      double lookY = -Math.sin(pitch);
      double lookZ = Math.cos(yaw) * Math.cos(pitch);
      return fromEntity(0.0, 0.0, 0.0, lookX, lookY, lookZ);
   }

   public LuaTable toLua() {
      LuaTable table = new LuaTable();
      table.set("__type", "CFrame");
      table.set("__side", "Shared");
      table.set("__cframe", new LuaUserdata(this));
      table.set("X", this.x);
      table.set("Y", this.y);
      table.set("Z", this.z);
      table.set("Position", MLuaVector.vector3(this.x, this.y, this.z));
      table.set("RightVector", MLuaVector.vector3(this.r00, this.r10, this.r20));
      table.set("UpVector", MLuaVector.vector3(this.r01, this.r11, this.r21));
      table.set("LookVector", MLuaVector.vector3(-this.r02, -this.r12, -this.r22));
      double[] euler = this.toEulerAnglesXYZ();
      table.set("Rotation", MLuaVector.vector3(euler[0], euler[1], euler[2]));
      table.set("Orientation", MLuaVector.vector3(Math.toDegrees(euler[0]), Math.toDegrees(euler[1]), Math.toDegrees(euler[2])));
      table.set("Yaw", (double)this.minecraftYaw());
      table.set("Pitch", (double)this.minecraftPitch());
      table.set("Roll", 0);
      table.set("Inverse", new OneArgFunction() {
         public LuaValue call(LuaValue self) {
            return MLuaCFrame.fromLua(self).inverse().toLua();
         }
      });
      table.set("Lerp", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            return MLuaCFrame.fromLua(args.arg(1)).lerp(MLuaCFrame.fromLua(args.arg(2)), args.checkdouble(3)).toLua();
         }
      });
      table.set("ToWorldSpace", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue other) {
            return MLuaCFrame.fromLua(self).mul(MLuaCFrame.fromLua(other)).toLua();
         }
      });
      table.set("ToObjectSpace", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue other) {
            return MLuaCFrame.fromLua(self).inverse().mul(MLuaCFrame.fromLua(other)).toLua();
         }
      });
      table.set("PointToWorldSpace", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue point) {
            return MLuaCFrame.vectorTable(MLuaCFrame.fromLua(self).pointToWorld(MLuaCFrame.vector(point)));
         }
      });
      table.set("PointToObjectSpace", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue point) {
            return MLuaCFrame.vectorTable(MLuaCFrame.fromLua(self).pointToObject(MLuaCFrame.vector(point)));
         }
      });
      table.set("VectorToWorldSpace", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue vector) {
            return MLuaCFrame.vectorTable(MLuaCFrame.fromLua(self).vectorToWorld(MLuaCFrame.vector(vector)));
         }
      });
      table.set("VectorToObjectSpace", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue vector) {
            return MLuaCFrame.vectorTable(MLuaCFrame.fromLua(self).vectorToObject(MLuaCFrame.vector(vector)));
         }
      });
      table.set("Mul", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue other) {
            if (!MLuaCFrame.isCFrame(other)) {
               throw new LuaError("[MLua] CFrame:Mul expects another CFrame. Use cf + Vector3 to offset position, or cf:PointToWorldSpace(vector) to transform a point.");
            } else {
               return MLuaCFrame.fromLua(self).mul(MLuaCFrame.fromLua(other)).toLua();
            }
         }
      });
      table.set("Add", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue offset) {
            return MLuaCFrame.fromLua(self).add(MLuaCFrame.vector(offset)).toLua();
         }
      });
      table.set("TransformPoint", table.get("PointToWorldSpace"));
      table.set("GetComponents", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaCFrame cf = MLuaCFrame.fromLua(args.arg(1));
            return LuaValue.varargsOf(new LuaValue[]{LuaValue.valueOf(cf.x), LuaValue.valueOf(cf.y), LuaValue.valueOf(cf.z), LuaValue.valueOf(cf.r00), LuaValue.valueOf(cf.r01), LuaValue.valueOf(cf.r02), LuaValue.valueOf(cf.r10), LuaValue.valueOf(cf.r11), LuaValue.valueOf(cf.r12), LuaValue.valueOf(cf.r20), LuaValue.valueOf(cf.r21), LuaValue.valueOf(cf.r22)});
         }
      });
      table.set("ToEulerAnglesXYZ", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            double[] angles = MLuaCFrame.fromLua(args.arg(1)).toEulerAnglesXYZ();
            return LuaValue.varargsOf(new LuaValue[]{LuaValue.valueOf(angles[0]), LuaValue.valueOf(angles[1]), LuaValue.valueOf(angles[2])});
         }
      });
      table.setmetatable(metatable());
      return table;
   }

   public static MLuaCFrame fromLua(LuaValue value) {
      if (!value.istable()) {
         throw new LuaError("[MLua] Expected CFrame");
      } else {
         LuaValue userdata = value.checktable().rawget("__cframe");
         if (userdata.isuserdata(MLuaCFrame.class)) {
            return (MLuaCFrame)userdata.checkuserdata(MLuaCFrame.class);
         } else {
            throw new LuaError("[MLua] Expected CFrame");
         }
      }
   }

   public static boolean isCFrame(LuaValue value) {
      return value.istable() && value.checktable().rawget("__cframe").isuserdata(MLuaCFrame.class);
   }

   public static double[] vector(LuaValue value) {
      if (!value.istable()) {
         throw new LuaError("[MLua] Expected a Vector3 or CFrame value");
      }
      if (isCFrame(value)) {
         MLuaCFrame cframe = fromLua(value);
         return new double[]{cframe.x(), cframe.y(), cframe.z()};
      }
      LuaTable table = value.checktable();
      if (!"Vector3".equals(table.get("__type").optjstring(null))) {
         throw new LuaError("[MLua] Expected a Vector3 or CFrame value; coordinate tables are not accepted");
      }
      return new double[]{component(table, "X", "x"), component(table, "Y", "y"), component(table, "Z", "z")};
   }

   private static double component(LuaTable table, String upper, String lower) {
      LuaValue value = table.get(upper);
      return value.isnumber() ? value.todouble() : table.get(lower).checkdouble();
   }

   public double x() {
      return this.x;
   }

   public double y() {
      return this.y;
   }

   public double z() {
      return this.z;
   }

   public double r00() {
      return this.r00;
   }

   public double r01() {
      return this.r01;
   }

   public double r02() {
      return this.r02;
   }

   public double r10() {
      return this.r10;
   }

   public double r11() {
      return this.r11;
   }

   public double r12() {
      return this.r12;
   }

   public double r20() {
      return this.r20;
   }

   public double r21() {
      return this.r21;
   }

   public double r22() {
      return this.r22;
   }

   public double[] lookVector() {
      return new double[]{-this.r02, -this.r12, -this.r22};
   }

   public double[] toEulerAnglesXYZ() {
      double ry = Math.asin(Math.max(-1.0, Math.min(1.0, this.r02)));
      double cy = Math.cos(ry);
      double rx;
      double rz;
      if (Math.abs(cy) > 1.0E-6) {
         rx = Math.atan2(-this.r12, this.r22);
         rz = Math.atan2(-this.r01, this.r00);
      } else {
         rx = Math.atan2(this.r21, this.r11);
         rz = 0.0;
      }

      return new double[]{rx, ry, rz};
   }

   public float minecraftYaw() {
      double[] look = this.lookVector();
      return (float)Math.toDegrees(Math.atan2(-look[0], look[2]));
   }

   public float minecraftPitch() {
      double[] look = this.lookVector();
      return (float)Math.toDegrees(Math.asin(-Math.max(-1.0, Math.min(1.0, look[1]))));
   }

   public MLuaCFrame mul(MLuaCFrame b) {
      double[] p = this.pointToWorld(new double[]{b.x, b.y, b.z});
      return new MLuaCFrame(p[0], p[1], p[2], this.r00 * b.r00 + this.r01 * b.r10 + this.r02 * b.r20, this.r00 * b.r01 + this.r01 * b.r11 + this.r02 * b.r21, this.r00 * b.r02 + this.r01 * b.r12 + this.r02 * b.r22, this.r10 * b.r00 + this.r11 * b.r10 + this.r12 * b.r20, this.r10 * b.r01 + this.r11 * b.r11 + this.r12 * b.r21, this.r10 * b.r02 + this.r11 * b.r12 + this.r12 * b.r22, this.r20 * b.r00 + this.r21 * b.r10 + this.r22 * b.r20, this.r20 * b.r01 + this.r21 * b.r11 + this.r22 * b.r21, this.r20 * b.r02 + this.r21 * b.r12 + this.r22 * b.r22);
   }

   public MLuaCFrame add(double[] offset) {
      return new MLuaCFrame(this.x + offset[0], this.y + offset[1], this.z + offset[2], this.r00, this.r01, this.r02, this.r10, this.r11, this.r12, this.r20, this.r21, this.r22);
   }

   public MLuaCFrame inverse() {
      double ix = -(this.r00 * this.x + this.r10 * this.y + this.r20 * this.z);
      double iy = -(this.r01 * this.x + this.r11 * this.y + this.r21 * this.z);
      double iz = -(this.r02 * this.x + this.r12 * this.y + this.r22 * this.z);
      return new MLuaCFrame(ix, iy, iz, this.r00, this.r10, this.r20, this.r01, this.r11, this.r21, this.r02, this.r12, this.r22);
   }

   public MLuaCFrame lerp(MLuaCFrame b, double alpha) {
      double nx = lerp(this.x, b.x, alpha);
      double ny = lerp(this.y, b.y, alpha);
      double nz = lerp(this.z, b.z, alpha);
      Quaternion q1 = MLuaCFrame.Quaternion.fromRotationMatrix(this.r00, this.r01, this.r02, this.r10, this.r11, this.r12, this.r20, this.r21, this.r22);
      Quaternion q2 = MLuaCFrame.Quaternion.fromRotationMatrix(b.r00, b.r01, b.r02, b.r10, b.r11, b.r12, b.r20, b.r21, b.r22);
      Quaternion qr = MLuaCFrame.Quaternion.slerp(q1, q2, alpha);
      double[][] m = qr.toRotationMatrix();
      return new MLuaCFrame(nx, ny, nz, m[0][0], m[0][1], m[0][2], m[1][0], m[1][1], m[1][2], m[2][0], m[2][1], m[2][2]);
   }

   public double[] pointToWorld(double[] p) {
      double[] v = this.vectorToWorld(p);
      return new double[]{v[0] + this.x, v[1] + this.y, v[2] + this.z};
   }

   public double[] pointToObject(double[] p) {
      return this.vectorToObject(new double[]{p[0] - this.x, p[1] - this.y, p[2] - this.z});
   }

   public double[] vectorToWorld(double[] v) {
      return new double[]{this.r00 * v[0] + this.r01 * v[1] + this.r02 * v[2], this.r10 * v[0] + this.r11 * v[1] + this.r12 * v[2], this.r20 * v[0] + this.r21 * v[1] + this.r22 * v[2]};
   }

   public double[] vectorToObject(double[] v) {
      return new double[]{this.r00 * v[0] + this.r10 * v[1] + this.r20 * v[2], this.r01 * v[0] + this.r11 * v[1] + this.r21 * v[2], this.r02 * v[0] + this.r12 * v[1] + this.r22 * v[2]};
   }

   private static MLuaCFrame fromBasis(double x, double y, double z, double[] right, double[] up, double[] back) {
      return new MLuaCFrame(x, y, z, right[0], up[0], back[0], right[1], up[1], back[1], right[2], up[2], back[2]);
   }

   private static LuaTable metatable() {
      LuaTable metatable = new LuaTable();
      metatable.set(LuaValue.MUL, new TwoArgFunction() {
         public LuaValue call(LuaValue left, LuaValue right) {
            if (!MLuaCFrame.isCFrame(right)) {
               throw new LuaError("[MLua] CFrame * expects another CFrame. Use CFrame + Vector3 to offset position, or cf:PointToWorldSpace(vector) to transform a point.");
            } else {
               return MLuaCFrame.fromLua(left).mul(MLuaCFrame.fromLua(right)).toLua();
            }
         }
      });
      metatable.set(LuaValue.ADD, new TwoArgFunction() {
         public LuaValue call(LuaValue left, LuaValue right) {
            if (MLuaCFrame.isCFrame(left)) {
               return MLuaCFrame.fromLua(left).add(MLuaCFrame.vector(right)).toLua();
            } else if (MLuaCFrame.isCFrame(right)) {
               return MLuaCFrame.fromLua(right).add(MLuaCFrame.vector(left)).toLua();
            } else {
               throw new LuaError("[MLua] CFrame + expects a Vector3 offset");
            }
         }
      });
      metatable.set(LuaValue.SUB, new TwoArgFunction() {
         public LuaValue call(LuaValue left, LuaValue right) {
            double[] offset = MLuaCFrame.vector(right);
            return MLuaCFrame.fromLua(left).add(new double[]{-offset[0], -offset[1], -offset[2]}).toLua();
         }
      });
      metatable.set("__tostring", new OneArgFunction() {
         public LuaValue call(LuaValue value) {
            MLuaCFrame cf = MLuaCFrame.fromLua(value);
            return LuaValue.valueOf("CFrame(" + cf.x + ", " + cf.y + ", " + cf.z + ")");
         }
      });
      return metatable;
   }

   private static LuaTable vectorTable(double[] v) {
      return MLuaVector.vector3(v[0], v[1], v[2]);
   }

   private static double[] normalize(double x, double y, double z, double fx, double fy, double fz) {
      double m = Math.sqrt(x * x + y * y + z * z);
      return m == 0.0 ? new double[]{fx, fy, fz} : new double[]{x / m, y / m, z / m};
   }

   private static double[] normalize(double[] v, double fx, double fy, double fz) {
      return normalize(v[0], v[1], v[2], fx, fy, fz);
   }

   private static double[] cross(double[] a, double[] b) {
      return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
   }

   private static double lerp(double a, double b, double t) {
      return a + (b - a) * t;
   }

   private static class Quaternion {
      final double w;
      final double x;
      final double y;
      final double z;

      Quaternion(double w, double x, double y, double z) {
         this.w = w;
         this.x = x;
         this.y = y;
         this.z = z;
      }

      static Quaternion fromRotationMatrix(double r00, double r01, double r02, double r10, double r11, double r12, double r20, double r21, double r22) {
         double tr = r00 + r11 + r22;
         double qw;
         double qx;
         double qy;
         double qz;
         double s;
         if (tr > 0.0) {
            s = Math.sqrt(tr + 1.0) * 2.0;
            qw = 0.25 * s;
            qx = (r21 - r12) / s;
            qy = (r02 - r20) / s;
            qz = (r10 - r01) / s;
         } else if (r00 > r11 && r00 > r22) {
            s = Math.sqrt(1.0 + r00 - r11 - r22) * 2.0;
            qw = (r21 - r12) / s;
            qx = 0.25 * s;
            qy = (r01 + r10) / s;
            qz = (r02 + r20) / s;
         } else if (r11 > r22) {
            s = Math.sqrt(1.0 + r11 - r00 - r22) * 2.0;
            qw = (r02 - r20) / s;
            qx = (r01 + r10) / s;
            qy = 0.25 * s;
            qz = (r12 + r21) / s;
         } else {
            s = Math.sqrt(1.0 + r22 - r00 - r11) * 2.0;
            qw = (r10 - r01) / s;
            qx = (r02 + r20) / s;
            qy = (r12 + r21) / s;
            qz = 0.25 * s;
         }

         return (new Quaternion(qw, qx, qy, qz)).normalize();
      }

      Quaternion normalize() {
         double mag = Math.sqrt(this.w * this.w + this.x * this.x + this.y * this.y + this.z * this.z);
         return mag < 1.0E-10 ? new Quaternion(1.0, 0.0, 0.0, 0.0) : new Quaternion(this.w / mag, this.x / mag, this.y / mag, this.z / mag);
      }

      static Quaternion slerp(Quaternion a, Quaternion b, double t) {
         double dot = a.w * b.w + a.x * b.x + a.y * b.y + a.z * b.z;
         if (dot < 0.0) {
            b = new Quaternion(-b.w, -b.x, -b.y, -b.z);
            dot = -dot;
         }

         if (dot > 0.9995) {
            return (new Quaternion(a.w + t * (b.w - a.w), a.x + t * (b.x - a.x), a.y + t * (b.y - a.y), a.z + t * (b.z - a.z))).normalize();
         } else {
            double theta0 = Math.acos(dot);
            double theta = theta0 * t;
            double sinTheta = Math.sin(theta);
            double sinTheta0 = Math.sin(theta0);
            double s0 = Math.cos(theta) - dot * sinTheta / sinTheta0;
            double s1 = sinTheta / sinTheta0;
            return (new Quaternion(s0 * a.w + s1 * b.w, s0 * a.x + s1 * b.x, s0 * a.y + s1 * b.y, s0 * a.z + s1 * b.z)).normalize();
         }
      }

      double[][] toRotationMatrix() {
         double[][] res = new double[3][3];
         res[0][0] = 1.0 - 2.0 * this.y * this.y - 2.0 * this.z * this.z;
         res[0][1] = 2.0 * this.x * this.y - 2.0 * this.w * this.z;
         res[0][2] = 2.0 * this.x * this.z + 2.0 * this.w * this.y;
         res[1][0] = 2.0 * this.x * this.y + 2.0 * this.w * this.z;
         res[1][1] = 1.0 - 2.0 * this.x * this.x - 2.0 * this.z * this.z;
         res[1][2] = 2.0 * this.y * this.z - 2.0 * this.w * this.x;
         res[2][0] = 2.0 * this.x * this.z - 2.0 * this.w * this.y;
         res[2][1] = 2.0 * this.y * this.z + 2.0 * this.w * this.x;
         res[2][2] = 1.0 - 2.0 * this.x * this.x - 2.0 * this.y * this.y;
         return res;
      }
   }
}
