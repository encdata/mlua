package dev.mlua.common.mixin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class MLuaMixinTargetMapper {
   private static final Map<String, String> FABRIC_CLASS_PREFIXES = new LinkedHashMap<>();
   private static final Map<String, String> FABRIC_INTERNAL_PREFIXES = new LinkedHashMap<>();
   private static final AtomicReference<TinyMappings> TINY_MAPPINGS = new AtomicReference<>();

   static {
      FABRIC_CLASS_PREFIXES.put("net.minecraft.client.renderer.", "net.minecraft.client.render.");
      FABRIC_CLASS_PREFIXES.put("net.minecraft.client.player.", "net.minecraft.client.network.");
      FABRIC_CLASS_PREFIXES.put("net.minecraft.world.entity.", "net.minecraft.entity.");
      FABRIC_CLASS_PREFIXES.put("net.minecraft.world.damagesource.", "net.minecraft.entity.damage.");
      FABRIC_CLASS_PREFIXES.put("net.minecraft.world.phys.", "net.minecraft.util.hit.");
      FABRIC_CLASS_PREFIXES.put("net.minecraft.server.level.", "net.minecraft.server.world.");
      FABRIC_CLASS_PREFIXES.put("com.mojang.blaze3d.vertex.PoseStack", "net.minecraft.client.util.math.MatrixStack");
      FABRIC_CLASS_PREFIXES.put("net.minecraft.client.renderer.MultiBufferSource", "net.minecraft.client.render.VertexConsumerProvider");
      FABRIC_CLASS_PREFIXES.put("net.minecraft.client.renderer.entity.player.PlayerRenderer", "net.minecraft.client.render.entity.PlayerEntityRenderer");
      FABRIC_CLASS_PREFIXES.put("net.minecraft.client.player.AbstractClientPlayer", "net.minecraft.client.network.AbstractClientPlayerEntity");

      for (Map.Entry<String, String> entry : FABRIC_CLASS_PREFIXES.entrySet()) {
         FABRIC_INTERNAL_PREFIXES.put(entry.getKey().replace('.', '/'), entry.getValue().replace('.', '/'));
      }
   }

   private MLuaMixinTargetMapper() {
   }

   public static MLuaMixinRule remapRule(MLuaMixinRule rule) {
      return MLuaMappingResolvers.current().remapRule(rule);
   }

   /** @deprecated use {@link MLuaMappingResolvers#current()} instead. */
   @Deprecated
   public static String remapClassName(String className) {
      return MLuaMappingResolvers.current().mapClassName(className);
   }

   /** @deprecated use {@link MLuaMappingResolvers#current()} instead. */
   @Deprecated
   public static String remapDescriptor(String descriptor) {
      return MLuaMappingResolvers.current().mapDescriptor(descriptor);
   }

   static MLuaMappingResolver legacyResolver() {
      return new MLuaMappingResolver() {
         @Override
         public String loaderId() {
            return isFabricEnvironment() ? "fabric" : "identity";
         }

         @Override
         public String mapClassName(String className) {
            return legacyRemapClassName(className);
         }

         @Override
         public String mapDescriptor(String descriptor) {
            return legacyRemapDescriptor(descriptor);
         }

         @Override
         public ResolvedMethod mapMethod(String owner, String method, String descriptor) {
            MLuaMixinTargetMapper.ResolvedMethod resolved = legacyRemapMethod(
               owner,
               method,
               descriptor
            );
            return new MLuaMappingResolver.ResolvedMethod(resolved.ownerClassName, resolved.methodName, resolved.descriptor);
         }

         @Override
         public String mapRedirectTarget(String target) {
            return legacyRemapRedirectTarget(target);
         }
      };
   }

   private static String legacyRemapClassName(String className) {
      if (className == null || className.isEmpty() || !isFabricEnvironment()) {
         return className;
      }

      return mapClassNameToRuntime(className);
   }

   private static String remapClassNameInternal(String className) {
      for (Map.Entry<String, String> entry : FABRIC_CLASS_PREFIXES.entrySet()) {
         if (className.startsWith(entry.getKey())) {
            return entry.getValue() + className.substring(entry.getKey().length());
         }
      }
      return className;
   }

   private static String legacyRemapDescriptor(String descriptor) {
      if (descriptor == null || descriptor.isEmpty() || !isFabricEnvironment()) {
         return descriptor;
      }

      return mapDescriptorToRuntime(descriptor);
   }

   private static String remapDescriptorInternal(String descriptor) {
      if (descriptor == null || descriptor.isEmpty()) {
         return descriptor;
      }

      String remapped = descriptor;
      for (Map.Entry<String, String> entry : FABRIC_INTERNAL_PREFIXES.entrySet()) {
         remapped = remapped.replace(entry.getKey(), entry.getValue());
      }
      return remapped;
   }

   private static ResolvedMethod legacyRemapMethod(String className, String methodName, String descriptor) {
      if (!isFabricEnvironment()) {
         return new ResolvedMethod(className, methodName, descriptor);
      }

      try {
         Object resolver = fabricMappingResolver();
         Collection<String> namespaces = mappingNamespaces(resolver);
         if (namespaces.contains("official")) {
            String runtimeClassName = mapFabricClassName(resolver, "official", className);
            String runtimeDescriptor = mapDescriptorToRuntime(resolver, descriptor, "official");
            if (descriptor != null && !descriptor.isEmpty()) {
               String runtimeMethodName = mapFabricMethodName(resolver, "official", className, methodName, descriptor);
               return new ResolvedMethod(runtimeClassName, runtimeMethodName, runtimeDescriptor);
            }
            try {
               ResolvedMethod inferred = inferMethodWithoutDescriptor(
                  resolver, className, runtimeClassName, methodName, "official");
               return inferred != null ? inferred : new ResolvedMethod(runtimeClassName, methodName, descriptor);
            } catch (Throwable ignored) {
               return new ResolvedMethod(runtimeClassName, methodName, descriptor);
            }
         }
      } catch (Throwable ignored) {
         // Continue with the bundled Yarn map or the older named-namespace resolver.
      }

      TinyMappings tinyMappings = loadTinyMappings();
      if (tinyMappings != null) {
         String sourceClassName = remapClassNameInternal(className);
         String sourceDescriptor = remapDescriptorInternal(descriptor);
         String runtimeClassName = tinyMappings.mapClassName(sourceClassName);
         String runtimeDescriptor = sourceDescriptor == null ? null : tinyMappings.mapDescriptor(sourceDescriptor);
         if (runtimeDescriptor != null && !runtimeDescriptor.isEmpty()) {
            String runtimeMethodName = tinyMappings.mapMethod(sourceClassName, methodName, sourceDescriptor);
            return new ResolvedMethod(runtimeClassName, runtimeMethodName != null ? runtimeMethodName : methodName, runtimeDescriptor);
         }

         String inferredMethod = tinyMappings.inferMethodName(sourceClassName, methodName);
         String inferredDescriptor = tinyMappings.inferDescriptor(sourceClassName, methodName);
         return new ResolvedMethod(
            runtimeClassName,
            inferredMethod != null ? inferredMethod : methodName,
            inferredDescriptor != null ? inferredDescriptor : descriptor
         );
      }

      try {
         Object resolver = fabricMappingResolver();
         String sourceClassName = remapClassNameInternal(className);
         String sourceDescriptor = remapDescriptorInternal(descriptor);
         String runtimeClassName = mapFabricClassName(resolver, "named", sourceClassName);
         String runtimeDescriptor = mapDescriptorToRuntime(resolver, sourceDescriptor, "named");

         if (runtimeDescriptor != null && !runtimeDescriptor.isEmpty()) {
            String runtimeMethodName = mapFabricMethodName(
               resolver, "named", sourceClassName, methodName, sourceDescriptor);
            return new ResolvedMethod(runtimeClassName, runtimeMethodName, runtimeDescriptor);
         }

         try {
            ResolvedMethod inferred = inferMethodWithoutDescriptor(
               resolver, sourceClassName, runtimeClassName, methodName, "named");
            return inferred != null ? inferred : new ResolvedMethod(runtimeClassName, methodName, descriptor);
         } catch (Throwable ignored) {
            return new ResolvedMethod(runtimeClassName, methodName, descriptor);
         }
      } catch (Throwable ignored) {
         return new ResolvedMethod(className, methodName, descriptor);
      }
   }

   private static Object fabricMappingResolver() throws Exception {
      Class<?> loaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader", false,
         MLuaMixinTargetMapper.class.getClassLoader());
      Object loader = loaderClass.getMethod("getInstance").invoke(null);
      return loaderClass.getMethod("getMappingResolver").invoke(loader);
   }

   @SuppressWarnings("unchecked")
   private static Collection<String> mappingNamespaces(Object resolver) throws Exception {
      return (Collection<String>) resolver.getClass().getMethod("getNamespaces").invoke(resolver);
   }

   private static String mapFabricClassName(Object resolver, String namespace, String className) throws Exception {
      return (String) resolver.getClass()
         .getMethod("mapClassName", String.class, String.class)
         .invoke(resolver, namespace, className);
   }

   private static String mapFabricMethodName(Object resolver, String namespace, String owner,
                                             String method, String descriptor) throws Exception {
      return (String) resolver.getClass()
         .getMethod("mapMethodName", String.class, String.class, String.class, String.class)
         .invoke(resolver, namespace, owner, method, descriptor);
   }

   private static String mapDescriptorToRuntime(Object resolver, String descriptor, String namespace) throws Exception {
      if (descriptor == null || descriptor.isEmpty()) {
         return descriptor;
      }

      StringBuilder out = new StringBuilder(descriptor.length());
      for (int i = 0; i < descriptor.length(); i++) {
         char ch = descriptor.charAt(i);
         if (ch != 'L') {
            out.append(ch);
            continue;
         }

         int end = descriptor.indexOf(';', i);
         if (end < 0) {
            return descriptor;
         }

         String internalName = descriptor.substring(i + 1, end);
         String dotName = internalName.replace('/', '.');
         String mappedDot = mapFabricClassName(resolver, namespace, dotName);
         out.append('L').append(mappedDot.replace('.', '/')).append(';');
         i = end;
      }
      return out.toString();
   }

   private static String mapClassNameToRuntime(String className) {
      try {
         Class<?> loaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader", false,
            MLuaMixinTargetMapper.class.getClassLoader());
         Object loader = loaderClass.getMethod("getInstance").invoke(null);
         Object resolver = loaderClass.getMethod("getMappingResolver").invoke(loader);
         @SuppressWarnings("unchecked")
         Collection<String> namespaces = (Collection<String>) resolver.getClass()
            .getMethod("getNamespaces").invoke(resolver);
         if (namespaces.contains("official")) {
            return (String) resolver.getClass()
               .getMethod("mapClassName", String.class, String.class)
               .invoke(resolver, "official", className);
         }
         if (namespaces.contains("named")) {
            String namedName = remapClassNameInternal(className);
            return (String) resolver.getClass()
               .getMethod("mapClassName", String.class, String.class)
               .invoke(resolver, "named", namedName);
         }
      } catch (Throwable ignored) {
         // Older bootstrap stages may not expose Fabric's resolver yet.
      }
      TinyMappings tinyMappings = loadTinyMappings();
      if (tinyMappings != null) {
         String officialName = tinyMappings.mapOfficialClassName(className);
         if (officialName != null) return officialName;
         return tinyMappings.mapClassName(remapClassNameInternal(className));
      }
      return remapClassNameInternal(className);
   }

   private static String mapDescriptorToRuntime(String descriptor) {
      TinyMappings tinyMappings = loadTinyMappings();
      if (tinyMappings != null) {
         return tinyMappings.mapDescriptor(descriptor);
      }
      return remapDescriptorInternal(descriptor);
   }

   @SuppressWarnings("unchecked")
   private static ResolvedMethod inferMethodWithoutDescriptor(Object resolver, String sourceClassName,
                                                               String runtimeClassName, String methodName,
                                                               String sourceNamespace) throws Exception {
      Field mappingsField = resolver.getClass().getDeclaredField("mappings");
      mappingsField.setAccessible(true);
      Object mappings = mappingsField.get(resolver);

      Method getNamespaceId = mappings.getClass().getMethod("getNamespaceId", String.class);
      int sourceNamespaceId = (Integer) getNamespaceId.invoke(mappings, sourceNamespace);
      String runtimeNamespace = (String) resolver.getClass().getMethod("getCurrentRuntimeNamespace").invoke(resolver);
      int runtimeNamespaceId = (Integer) getNamespaceId.invoke(mappings, runtimeNamespace);

      Method getClass = mappings.getClass().getMethod("getClass", String.class, int.class);
      Object classMapping = getClass.invoke(mappings, sourceClassName.replace('.', '/'), sourceNamespaceId);
      if (classMapping == null) {
         return null;
      }

      Collection<Object> methods = (Collection<Object>) classMapping.getClass().getMethod("getMethods").invoke(classMapping);
      Object match = null;
      for (Object candidate : methods) {
         String candidateName = (String) candidate.getClass().getMethod("getName", int.class).invoke(candidate, sourceNamespaceId);
         if (!methodName.equals(candidateName)) {
            continue;
         }

         if (match != null) {
            return null;
         }
         match = candidate;
      }

      if (match == null) {
         return null;
      }

      String runtimeMethodName = (String) match.getClass().getMethod("getName", int.class).invoke(match, runtimeNamespaceId);
      String sourceDescriptor = (String) match.getClass().getMethod("getDesc", int.class).invoke(match, sourceNamespaceId);
      String runtimeDescriptor = mapDescriptorToRuntime(resolver, sourceDescriptor, sourceNamespace);
      return new ResolvedMethod(runtimeClassName, runtimeMethodName, runtimeDescriptor);
   }

   private static String legacyRemapRedirectTarget(String target) {
      if (target == null || target.isEmpty() || !isFabricEnvironment()) {
         return target;
      }

      int semi = target.indexOf(';');
      if (!target.startsWith("L") || semi < 0) {
         return target;
      }

      String owner = target.substring(1, semi).replace('/', '.');
      int descriptorStart = target.indexOf('(', semi + 1);
      if (descriptorStart > semi + 1) {
         String method = target.substring(semi + 1, descriptorStart);
         String descriptor = target.substring(descriptorStart);
         ResolvedMethod mapped = legacyRemapMethod(owner, method, descriptor);
         return "L" + mapped.ownerClassName().replace('.', '/') + ";"
            + mapped.methodName() + mapped.descriptor();
      }

      String remappedOwner = legacyRemapClassName(owner).replace('.', '/');
      return "L" + remappedOwner + mapDescriptorToRuntime(target.substring(semi));
   }

   public static boolean isFabricEnvironment() {
      try {
         Class.forName("net.fabricmc.loader.api.FabricLoader", false, MLuaMixinTargetMapper.class.getClassLoader());
         return true;
      } catch (Throwable ignored) {
         return false;
      }
   }

   private record ResolvedMethod(String ownerClassName, String methodName, String descriptor) {
   }

   private static TinyMappings loadTinyMappings() {
      TinyMappings cached = TINY_MAPPINGS.get();
      if (cached != null) {
         return cached;
      }

      try (var stream = MLuaMixinTargetMapper.class.getClassLoader().getResourceAsStream("mlua/yarn-mappings.tiny")) {
         if (stream == null) {
            return null;
         }

         TinyMappings parsed = TinyMappings.parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
         TINY_MAPPINGS.compareAndSet(null, parsed);
         return TINY_MAPPINGS.get();
      } catch (Exception ignored) {
         return null;
      }
   }

   private record MethodEntry(String namedName, String namedDescriptor, String intermediaryName, String intermediaryDescriptor) {
   }

   private static final class TinyMappings {
      private final Map<String, String> namedToIntermediaryClass;
      private final Map<String, String> officialToNamedClass;
      private final Map<String, String> officialToIntermediaryClass;
      private final Map<String, MethodEntry> methodsBySignature;
      private final Map<String, List<MethodEntry>> methodsByOwnerAndName;

      private TinyMappings(
         Map<String, String> namedToIntermediaryClass,
         Map<String, String> officialToNamedClass,
         Map<String, String> officialToIntermediaryClass,
         Map<String, MethodEntry> methodsBySignature,
         Map<String, List<MethodEntry>> methodsByOwnerAndName
      ) {
         this.namedToIntermediaryClass = namedToIntermediaryClass;
         this.officialToNamedClass = officialToNamedClass;
         this.officialToIntermediaryClass = officialToIntermediaryClass;
         this.methodsBySignature = methodsBySignature;
         this.methodsByOwnerAndName = methodsByOwnerAndName;
      }

      static TinyMappings parse(String text) {
         Map<String, String> namedToIntermediaryClass = new HashMap<>();
         Map<String, String> officialToNamedClass = new HashMap<>();
         Map<String, String> officialToIntermediaryClass = new HashMap<>();
         List<String[]> classRows = new ArrayList<>();
         List<String[]> methodRows = new ArrayList<>();
         List<String> methodOwners = new ArrayList<>();
         String currentOfficialOwner = null;
         String currentNamedOwner = null;
         String currentIntermediaryOwner = null;

         for (String line : text.split("\\R")) {
            if (line.isEmpty() || line.charAt(0) == '#') {
               continue;
            }

            String[] parts = line.split("\t");
            if (parts.length == 0) {
               continue;
            }

            if ("c".equals(parts[0]) && parts.length >= 4) {
               classRows.add(parts);
               currentOfficialOwner = parts[1];
               currentIntermediaryOwner = parts[2];
               currentNamedOwner = parts[3];
               officialToIntermediaryClass.put(currentOfficialOwner, currentIntermediaryOwner);
               officialToNamedClass.put(currentOfficialOwner, currentNamedOwner);
               namedToIntermediaryClass.put(currentNamedOwner.replace('/', '.'), currentIntermediaryOwner.replace('/', '.'));
               continue;
            }

            boolean indentedMethod = parts.length >= 6 && parts[0].isEmpty() && "m".equals(parts[1]);
            boolean rootMethod = parts.length >= 5 && "m".equals(parts[0]);
            if (indentedMethod || rootMethod) {
               if (currentOfficialOwner != null && currentNamedOwner != null && currentIntermediaryOwner != null) {
                  methodRows.add(parts);
                  methodOwners.add(currentOfficialOwner);
               }
            }
         }

         Map<String, MethodEntry> methodsBySignature = new HashMap<>();
         Map<String, List<MethodEntry>> methodsByOwnerAndName = new HashMap<>();
         for (int i = 0; i < methodRows.size(); i++) {
            String[] parts = methodRows.get(i);
            String officialOwner = methodOwners.get(i);
            int offset = parts[0].isEmpty() ? 1 : 0;
            String officialDescriptor = parts[offset + 1];
            String intermediaryName = parts[offset + 3];
            String namedName = parts[offset + 4];
            String namedOwner = officialToNamedClass.get(officialOwner).replace('/', '.');
            String intermediaryOwner = officialToIntermediaryClass.get(officialOwner).replace('/', '.');
            String namedDescriptor = mapOfficialDescriptor(officialDescriptor, officialToNamedClass);
            String intermediaryDescriptor = mapOfficialDescriptor(officialDescriptor, officialToIntermediaryClass);
            MethodEntry entry = new MethodEntry(namedName, namedDescriptor, intermediaryName, intermediaryDescriptor);
            methodsBySignature.put(signatureKey(namedOwner, namedName, namedDescriptor), entry);
            methodsByOwnerAndName.computeIfAbsent(ownerNameKey(namedOwner, namedName), ignored -> new ArrayList<>()).add(entry);
         }

         return new TinyMappings(
            namedToIntermediaryClass,
            officialToNamedClass,
            officialToIntermediaryClass,
            methodsBySignature,
            methodsByOwnerAndName
         );
      }

      String mapClassName(String namedClassName) {
         return this.namedToIntermediaryClass.getOrDefault(namedClassName, namedClassName);
      }

      String mapOfficialClassName(String officialClassName) {
         String mapped = this.officialToIntermediaryClass.get(officialClassName.replace('.', '/'));
         return mapped == null ? null : mapped.replace('/', '.');
      }

      String mapDescriptor(String namedDescriptor) {
         if (namedDescriptor == null) {
            return null;
         }

         StringBuilder out = new StringBuilder(namedDescriptor.length());
         for (int i = 0; i < namedDescriptor.length(); i++) {
            char ch = namedDescriptor.charAt(i);
            if (ch != 'L') {
               out.append(ch);
               continue;
            }

            int end = namedDescriptor.indexOf(';', i);
            if (end < 0) {
               return namedDescriptor;
            }

            String internalName = namedDescriptor.substring(i + 1, end);
            String mapped = this.namedToIntermediaryClass.getOrDefault(internalName.replace('/', '.'), internalName.replace('/', '.'));
            out.append('L').append(mapped.replace('.', '/')).append(';');
            i = end;
         }
         return out.toString();
      }

      String mapMethod(String namedOwner, String namedMethod, String namedDescriptor) {
         MethodEntry entry = this.methodsBySignature.get(signatureKey(namedOwner, namedMethod, namedDescriptor));
         return entry != null ? entry.intermediaryName() : null;
      }

      String inferMethodName(String namedOwner, String namedMethod) {
         List<MethodEntry> matches = this.methodsByOwnerAndName.get(ownerNameKey(namedOwner, namedMethod));
         return matches != null && matches.size() == 1 ? matches.get(0).intermediaryName() : null;
      }

      String inferDescriptor(String namedOwner, String namedMethod) {
         List<MethodEntry> matches = this.methodsByOwnerAndName.get(ownerNameKey(namedOwner, namedMethod));
         return matches != null && matches.size() == 1 ? matches.get(0).intermediaryDescriptor() : null;
      }

      private static String mapOfficialDescriptor(String officialDescriptor, Map<String, String> officialClassMap) {
         StringBuilder out = new StringBuilder(officialDescriptor.length());
         for (int i = 0; i < officialDescriptor.length(); i++) {
            char ch = officialDescriptor.charAt(i);
            if (ch != 'L') {
               out.append(ch);
               continue;
            }

            int end = officialDescriptor.indexOf(';', i);
            if (end < 0) {
               return officialDescriptor;
            }

            String internalName = officialDescriptor.substring(i + 1, end);
            out.append('L').append(officialClassMap.getOrDefault(internalName, internalName)).append(';');
            i = end;
         }
         return out.toString();
      }

      private static String signatureKey(String owner, String method, String descriptor) {
         return owner + "#" + method + descriptor;
      }

      private static String ownerNameKey(String owner, String method) {
         return owner + "#" + method;
      }
   }
}
