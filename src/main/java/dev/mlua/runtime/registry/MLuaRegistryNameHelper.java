package dev.mlua.runtime.registry;

public final class MLuaRegistryNameHelper {
   private MLuaRegistryNameHelper() {
   }

   public static String displayName(String registryName) {
      String clean = registryName.contains(":") ? registryName.substring(registryName.indexOf(58) + 1) : registryName;
      String[] parts = clean.split("_");
      StringBuilder result = new StringBuilder();
      String[] var4 = parts;
      int var5 = parts.length;

      for(int var6 = 0; var6 < var5; ++var6) {
         String part = var4[var6];
         if (!part.isEmpty()) {
            if (result.length() > 0) {
               result.append(' ');
            }

            if (part.length() == 1) {
               result.append(Character.toUpperCase(part.charAt(0)));
            } else {
               result.append(Character.toUpperCase(part.charAt(0)));
               result.append(part.substring(1));
            }
         }
      }

      return result.toString();
   }
}
