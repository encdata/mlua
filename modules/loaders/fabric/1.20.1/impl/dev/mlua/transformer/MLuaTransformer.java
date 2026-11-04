package dev.mlua.transformer;

import dev.mlua.common.mixin.MLuaMixinMapping;
import java.util.Map;

public final class MLuaTransformer {
   private MLuaTransformer() {
   }

   public static Map<String, Map<String, String>> loadNameMapping() {
      return MLuaMixinMapping.load(MLuaTransformer.class.getClassLoader());
   }

   public static String resolveMethodName(String className, String methodName, String methodDesc,
                                           Map<String, Map<String, String>> mapping) {
      return MLuaMixinMapping.resolveMethodName(className, methodName, methodDesc, mapping);
   }
}
