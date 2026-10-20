package dev.mlua.agent;

import java.lang.instrument.Instrumentation;
import java.util.jar.JarFile;

public final class MLuaMixinAgent {
   private static final System.Logger LOGGER = System.getLogger("MLua");
   private static final String[] MOD_CLASSLOADER_ANCHORS = {
      "dev.mlua.MLuaMod",
      "dev.mlua.loader.impl.fabric.MLuaFabricMod",
      "dev.mlua.runtime.MLuaBootstrap",
      "dev.mlua.loader.forge.ForgeLoaderProvider",
      "dev.mlua.loader.impl.fabric.FabricLoaderProvider"
   };

   private static volatile Instrumentation instrumentation;
   private static volatile boolean transformerInstalled;

   private MLuaMixinAgent() {
   }

   public static void premain(String args, Instrumentation inst) {
      install(inst);
   }

   public static void agentmain(String args, Instrumentation inst) {
      install(inst);
   }

   private static synchronized void install(Instrumentation inst) {
      instrumentation = inst;
      appendSelfToSystemClassLoader(inst);
      handOffToModClassLoader(inst);
   }

   private static void handOffToModClassLoader(Instrumentation inst) {
      try {
         ClassLoader modLoader = resolveModClassLoader(inst);
         if (modLoader == null) {
            LOGGER.log(System.Logger.Level.WARNING, "[MLua] Could not find MLua mod classloader during agent install");
            return;
         }

         ClassLoader transformerLoader = transformerClassLoader(modLoader);
         Class<?> bridgeClass = Class.forName("dev.mlua.transformer.MLuaInstrumentationBridge", true, transformerLoader);
         bridgeClass.getMethod("setInstrumentation", Instrumentation.class).invoke(null, inst);

         if (!transformerInstalled) {
            Class<?> transformerClass = Class.forName("dev.mlua.transformer.MLuaMixinTransformer", true, transformerLoader);
            Object transformer = transformerClass.getMethod("get").invoke(null);
            inst.addTransformer((java.lang.instrument.ClassFileTransformer) transformer, true);
            transformerInstalled = true;
            LOGGER.log(System.Logger.Level.INFO, "[MLua] Installed MLua runtime transformer via java agent");
         }
      } catch (Exception e) {
         LOGGER.log(System.Logger.Level.WARNING, "[MLua] Failed to hand off instrumentation to MLua mod classloader: " + e);
      }
   }

   private static ClassLoader transformerClassLoader(ClassLoader fallback) {
      try {
         return ModuleLayer.boot().findLoader("mlua_loader");
      } catch (IllegalArgumentException ignored) {
         return fallback;
      }
   }

   private static ClassLoader resolveModClassLoader(Instrumentation inst) {
      for (String anchor : MOD_CLASSLOADER_ANCHORS) {
         for (Class<?> loaded : inst.getAllLoadedClasses()) {
            if (anchor.equals(loaded.getName()) && loaded.getClassLoader() != null) {
               return loaded.getClassLoader();
            }
         }
      }

      ClassLoader agentLoader = MLuaMixinAgent.class.getClassLoader();
      for (Class<?> loaded : inst.getAllLoadedClasses()) {
         String name = loaded.getName();
         ClassLoader candidate = loaded.getClassLoader();
         if (candidate == null || candidate == agentLoader) {
            continue;
         }
         if (name.startsWith("dev.mlua.")) {
            return candidate;
         }
      }

      return null;
   }

   private static void appendSelfToSystemClassLoader(Instrumentation inst) {
      try {
         var location = MLuaMixinAgent.class.getProtectionDomain().getCodeSource().getLocation();
         if (location == null) {
            return;
         }
         java.nio.file.Path path = java.nio.file.Paths.get(location.toURI());
         if (java.nio.file.Files.isRegularFile(path)) {
            inst.appendToSystemClassLoaderSearch(new JarFile(path.toFile()));
         }
      } catch (Exception e) {
         LOGGER.log(System.Logger.Level.WARNING, "[MLua] Failed to append agent jar to system classloader search: " + e.getMessage());
      }
   }

   public static Instrumentation getInstrumentation() {
      return instrumentation;
   }
}
