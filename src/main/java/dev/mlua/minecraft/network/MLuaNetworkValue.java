package dev.mlua.minecraft.network;

import java.util.List;
import java.util.Map;

public sealed interface MLuaNetworkValue {
   public static record Cf(double x, double y, double z, double r00, double r01, double r02, double r10, double r11, double r12, double r20, double r21, double r22) implements MLuaNetworkValue {
      public Cf(double x, double y, double z, double r00, double r01, double r02, double r10, double r11, double r12, double r20, double r21, double r22) {
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
   }

   public static record Vec3(double x, double y, double z) implements MLuaNetworkValue {
      public Vec3(double x, double y, double z) {
         this.x = x;
         this.y = y;
         this.z = z;
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
   }

   public static record Obj(Map<String, MLuaNetworkValue> values) implements MLuaNetworkValue {
      public Obj(Map<String, MLuaNetworkValue> values) {
         this.values = values;
      }

      public Map<String, MLuaNetworkValue> values() {
         return this.values;
      }
   }

   public static record Arr(List<MLuaNetworkValue> values) implements MLuaNetworkValue {
      public Arr(List<MLuaNetworkValue> values) {
         this.values = values;
      }

      public List<MLuaNetworkValue> values() {
         return this.values;
      }
   }

   public static record Str(String value) implements MLuaNetworkValue {
      public Str(String value) {
         this.value = value;
      }

      public String value() {
         return this.value;
      }
   }

   public static record Num(double value) implements MLuaNetworkValue {
      public Num(double value) {
         this.value = value;
      }

      public double value() {
         return this.value;
      }
   }

   public static record Bool(boolean value) implements MLuaNetworkValue {
      public Bool(boolean value) {
         this.value = value;
      }

      public boolean value() {
         return this.value;
      }
   }

   public static record Nil() implements MLuaNetworkValue {
   }
}
