package dev.mlua.minecraft.network;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public sealed interface MLuaNetworkValue permits MLuaNetworkValue.Cf, MLuaNetworkValue.Vec3,
      MLuaNetworkValue.Obj, MLuaNetworkValue.Arr, MLuaNetworkValue.Str, MLuaNetworkValue.Num,
      MLuaNetworkValue.Bool, MLuaNetworkValue.Nil {
   record Cf(double x, double y, double z, double r00, double r01, double r02,
             double r10, double r11, double r12, double r20, double r21, double r22)
         implements MLuaNetworkValue {
      public Cf {
         requireFinite(x, y, z, r00, r01, r02, r10, r11, r12, r20, r21, r22);
      }
   }

   record Vec3(double x, double y, double z) implements MLuaNetworkValue {
      public Vec3 {
         requireFinite(x, y, z);
      }
   }

   record Obj(Map<String, MLuaNetworkValue> values) implements MLuaNetworkValue {
      public Obj {
         values = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(values, "values")));
         if (values.entrySet().stream().anyMatch(entry -> entry.getKey() == null || entry.getValue() == null)) {
            throw new NullPointerException("network object entries cannot contain null");
         }
      }
   }

   record Arr(List<MLuaNetworkValue> values) implements MLuaNetworkValue {
      public Arr {
         values = List.copyOf(Objects.requireNonNull(values, "values"));
      }
   }

   record Str(String value) implements MLuaNetworkValue {
      public Str {
         Objects.requireNonNull(value, "value");
      }
   }

   record Num(double value) implements MLuaNetworkValue {
      public Num {
         requireFinite(value);
      }
   }

   record Bool(boolean value) implements MLuaNetworkValue { }

   record Nil() implements MLuaNetworkValue { }

   private static void requireFinite(double... values) {
      for (double value : values) {
         if (!Double.isFinite(value)) throw new IllegalArgumentException("network numbers must be finite");
      }
   }
}
