package dev.mlua.transformer;

import dev.mlua.common.mixin.MLuaMixinRule;
import dev.mlua.common.mixin.MixinRuleEngine;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MLuaMixinTransformer implements ClassFileTransformer {
   private static final Logger LOGGER = LoggerFactory.getLogger("MLuaTransformer");
   private static final MLuaMixinTransformer INSTANCE = new MLuaMixinTransformer();
   private static volatile boolean installedIntoInstrumentation = false;
   private final java.util.Map<String, java.util.Map<String, String>> nameMapping = MLuaTransformer.loadNameMapping();
   private final java.util.Set<String> transformedTargets = java.util.concurrent.ConcurrentHashMap.newKeySet();

   private MLuaMixinTransformer() {
      MixinRuleEngine.setRetransformTask(this::applyRetransform);
   }

    public static MLuaMixinTransformer get() {
       return INSTANCE;
    }

   public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
      if (className == null) {
         return null;
      } else {
         String binaryName = className.replace('/', '.');
         List<MLuaMixinRule> matchingRules = MixinRuleEngine.get().getRules().stream()
            .filter((rule) -> rule.targetsClass(binaryName))
            .toList();

         if (matchingRules.isEmpty()) {
            return null;
         } else {
            LOGGER.info("[MLua] Transforming class '{}' with {} mixin rules", binaryName, matchingRules.size());
            try {
               byte[] modified = MLuaMixinClassWriter.apply(
                  classfileBuffer,
                  binaryName,
                  matchingRules,
                  this.nameMapping
               );
               if (modified != null) {
                  this.transformedTargets.add(binaryName);
                  LOGGER.info("[MLua] Class '{}' transformed successfully", binaryName);
               } else {
                  this.transformedTargets.remove(binaryName);
               }
               MixinRuleEngine.get().clearApplicationFailure(binaryName);
               return modified;
            } catch (Exception failure) {
               this.transformedTargets.remove(binaryName);
               MixinRuleEngine.get().recordApplicationFailure(binaryName, matchingRules, failure);
               LOGGER.error("[MLua] Transform failed for class '{}'", binaryName, failure);
               return null;
            }
         }
      }
   }

   public static Instrumentation resolveInstrumentation() {
      Instrumentation bridgeInst = MLuaInstrumentationBridge.getInstrumentation();
      if (bridgeInst != null) {
         return bridgeInst;
      }
      try {
         Class<?> agentClass = Class.forName("dev.mlua.agent.MLuaMixinAgent");
         return (Instrumentation) agentClass.getMethod("getInstrumentation").invoke(null);
      } catch (Exception e) {
         // Fallback via system classloader (agent jar is on the system classpath)
         try {
            Class<?> agentClass = Class.forName("dev.mlua.agent.MLuaMixinAgent", false, ClassLoader.getSystemClassLoader());
            return (Instrumentation) agentClass.getMethod("getInstrumentation").invoke(null);
         } catch (Exception e2) {
            try {
               ClassLoader loader = ModuleLayer.boot().findLoader("mlua");
               Class<?> agentClass = Class.forName("dev.mlua.agent.MLuaMixinAgent", false, loader);
               return (Instrumentation) agentClass.getMethod("getInstrumentation").invoke(null);
            } catch (Throwable ignored) {
               return null;
            }
         }
      }
   }

   public static synchronized boolean installIntoInstrumentation() {
      if (installedIntoInstrumentation) {
         return true;
      }

      Instrumentation inst = resolveInstrumentation();
      if (inst == null) {
         return false;
      }

      try {
         inst.addTransformer(INSTANCE, true);
         installedIntoInstrumentation = true;
         LOGGER.info("[MLua] Registered MLua runtime transformer with live Instrumentation");
         return true;
      } catch (Exception e) {
         LOGGER.error("[MLua] Failed to register runtime transformer with Instrumentation: {}", e.getMessage());
         return false;
      }
   }

   public synchronized void applyRetransform() {
      java.util.Set<String> targets = new java.util.TreeSet<>(this.transformedTargets);
      targets.addAll(MixinRuleEngine.get().getRules().stream()
            .map(rule -> MLuaMixinRule.normalizeClassName(rule.targetClassName()))
            .collect(Collectors.toSet()));

      if (targets.isEmpty()) {
         LOGGER.info("[MLua] No mixin rules to retransform");
         return;
      }

      Instrumentation inst = resolveInstrumentation();
      if (inst == null) {
         LOGGER.warn("[MLua] No Instrumentation available — mixins apply only to classes loaded after this point");
         LOGGER.info("[MLua] Registered {} mixin rules across {} unique classes",
            MixinRuleEngine.get().getRules().size(), targets.size());
         return;
      }

      for (String target : targets) {
         try {
            Class<?> clazz = this.findLoadedClass(inst, target);
            if (clazz == null) {
               LOGGER.info("[MLua] Class '{}' not loaded yet — will transform on first load", target);
               continue;
            }
            if (inst.isModifiableClass(clazz)) {
               inst.retransformClasses(clazz);
               if (MixinRuleEngine.get().getRules().stream().noneMatch(rule -> rule.targetsClass(target))) {
                  this.transformedTargets.remove(target);
               }
               LOGGER.info("[MLua] Retransformed class '{}'", target);
            } else {
               LOGGER.info("[MLua] Class '{}' is not loaded yet or not modifiable — will transform on first load", target);
            }
         } catch (Exception e) {
            LOGGER.error("[MLua] Failed to retransform class '{}': {}", target, e.getMessage());
         }
      }
   }

   private Class<?> findLoadedClass(Instrumentation inst, String targetClassName) {
      for (Class<?> loadedClass : inst.getAllLoadedClasses()) {
         if (targetClassName.equals(loadedClass.getName())) {
            return loadedClass;
         }
      }

      return null;
   }
}
