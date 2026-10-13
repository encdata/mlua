package dev.mlua.common.mixin;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared method-name mapping loader and resolver used by every loader's transformer adapter. */
public final class MLuaMixinMapping {
   private static final Logger LOGGER = LoggerFactory.getLogger("MLuaTransformer");
   private static final Map<ClassLoader, Map<String, Map<String, String>>> CACHE =
      Collections.synchronizedMap(new WeakHashMap<>());

   private MLuaMixinMapping() { }

   public static Map<String, Map<String, String>> load(ClassLoader loader) {
      if (loader == null) return Map.of();
      synchronized (CACHE) {
         Map<String, Map<String, String>> cached = CACHE.get(loader);
         if (cached != null) return cached;
         Map<String, Map<String, String>> loaded = loadUncached(loader);
         CACHE.put(loader, loaded);
         return loaded;
      }
   }

   private static Map<String, Map<String, String>> loadUncached(ClassLoader loader) {
      try (var stream = loader.getResourceAsStream("official_to_srg.json")) {
         if (stream == null) {
            LOGGER.warn("[MLua] official_to_srg.json not found on classpath");
            return Map.of();
         }
         try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var type = new TypeToken<Map<String, Map<String, String>>>() { }.getType();
            Map<String, Map<String, String>> mapping = new Gson().fromJson(reader, type);
            return mapping == null ? Map.of() : mapping;
         }
      } catch (Exception failure) {
         LOGGER.warn("[MLua] Failed to load method name mapping: {}", failure.getMessage());
         return Map.of();
      }
   }

   public static String resolveMethodName(String className, String methodName, String descriptor,
                                          Map<String, Map<String, String>> mapping) {
      if (className == null || methodName == null || mapping == null || mapping.isEmpty()) return null;
      Map<String, String> classMapping = mapping.get(className.replace('.', '/'));
      if (classMapping == null) classMapping = mapping.get(className.replace('/', '.'));
      if (classMapping == null) return null;

      if (descriptor != null && !descriptor.isEmpty()) {
         String resolved = classMapping.get(methodName + "#" + descriptor);
         if (resolved != null) return resolved;
         resolved = classMapping.get(methodName + " " + descriptor);
         if (resolved != null) return resolved;
         return null;
      }
      String prefix = methodName + "#";
      Set<String> candidates = new HashSet<>();
      for (Map.Entry<String, String> entry : classMapping.entrySet()) {
         if (entry.getKey().startsWith(prefix)) candidates.add(entry.getValue());
      }
      if (candidates.size() == 1) return candidates.iterator().next();
      if (candidates.size() > 1) {
         throw new IllegalArgumentException("Mixin method '" + className + "#" + methodName
            + "' has multiple runtime names; provide its descriptor to select the overload");
      }
      return classMapping.get(methodName);
   }

   /** Maps the owner method inside an invocation target used by Redirect and ModifyArg. */
   public static String resolveInvocationTarget(String target, Map<String, Map<String, String>> mapping) {
      if (target == null || !target.startsWith("L")) return target;
      int ownerEnd = target.indexOf(';');
      int descriptorStart = target.indexOf('(', ownerEnd + 1);
      if (ownerEnd <= 1 || descriptorStart <= ownerEnd + 1) return target;
      String owner = target.substring(1, ownerEnd);
      String method = target.substring(ownerEnd + 1, descriptorStart);
      String descriptor = target.substring(descriptorStart);
      String mappedMethod = resolveMethodName(owner, method, descriptor, mapping);
      return mappedMethod == null ? target : "L" + owner + ";" + mappedMethod + descriptor;
   }
}
