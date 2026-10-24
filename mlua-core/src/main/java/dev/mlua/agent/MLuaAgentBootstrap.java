package dev.mlua.agent;

import com.sun.tools.attach.VirtualMachine;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

public final class MLuaAgentBootstrap {
   private static final System.Logger LOGGER = System.getLogger("MLua");
   private static volatile boolean attempted;

   private MLuaAgentBootstrap() {
   }

   public static synchronized void ensureAttached() {
      if (MLuaMixinAgent.getInstrumentation() != null) {
         return;
      }
      if (attempted) {
         return;
      }
      attempted = true;

      try {
         Path jarPath = resolveOwnJarPath();
         if (jarPath == null || !Files.isRegularFile(jarPath)) {
            LOGGER.log(System.Logger.Level.WARNING, "[MLua] Could not resolve mod jar for self-attach: " + jarPath);
            return;
         }

         String pid = ManagementFactory.getRuntimeMXBean().getName().split("@", 2)[0];
         if (!tryDirectAttach(pid, jarPath)) {
            launchHelperAttach(pid, jarPath);
         }

         if (resolveTransformerInstrumentation() != null) {
            LOGGER.log(System.Logger.Level.INFO, "[MLua] Self-attach agent loaded from " + jarPath);
         } else {
            LOGGER.log(System.Logger.Level.WARNING, "[MLua] Self-attach completed but instrumentation is still unavailable");
         }
      } catch (Throwable t) {
         LOGGER.log(System.Logger.Level.WARNING, "[MLua] Failed to self-attach runtime transformer: " + t);
      }
   }

   private static boolean tryDirectAttach(String pid, Path jarPath) {
      try {
         VirtualMachine vm = VirtualMachine.attach(pid);
         try {
            vm.loadAgent(jarPath.toString());
            return true;
         } finally {
            vm.detach();
         }
      } catch (Throwable t) {
         LOGGER.log(System.Logger.Level.INFO, "[MLua] Direct self-attach unavailable, falling back to helper JVM: " + t);
         return false;
      }
   }

   private static void launchHelperAttach(String pid, Path jarPath) throws IOException, InterruptedException {
      Path javaBin = resolveJavaBinary();
      Process process = new ProcessBuilder(
         javaBin.toString(),
         "-cp",
         jarPath.toString(),
         "dev.mlua.agent.MLuaAgentAttacher",
         pid,
         jarPath.toString()
      ).redirectErrorStream(true).start();

      int exitCode = process.waitFor();
      if (exitCode != 0) {
         String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
         throw new IOException("Helper attach failed with exit code " + exitCode + ": " + output);
      }
   }

   private static Path resolveJavaBinary() {
      String executable = System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
      return Paths.get(System.getProperty("java.home")).resolve("bin").resolve(executable);
   }

   private static Path resolveOwnJarPath() {
      try {
         Path fabricPath = tryFabricModJarPath();
         if (fabricPath != null) {
            return fabricPath;
         }
      } catch (Throwable t) {
         LOGGER.log(System.Logger.Level.DEBUG, "[MLua] Fabric mod jar resolution not available: " + t);
      }

      try {
         URL location = MLuaAgentBootstrap.class.getProtectionDomain().getCodeSource().getLocation();
         if (location == null) {
            return null;
         }

         // Always decode the URL as a URI first.  Converting the external
         // form directly to a Path breaks when the instance path contains
         // spaces (for example, "Mlua Fabric 1.21.1").
         URI uri = location.toURI();
         if ("file".equalsIgnoreCase(uri.getScheme())) {
            return Paths.get(uri);
         }

         // Some launchers expose a nested jar/union URL.  Fall back to its
         // file component rather than passing the literal "file:/..." to
         // Paths.get().
         String source = location.toExternalForm();
         int fileIndex = source.toLowerCase().indexOf("file:");
         if (fileIndex >= 0) {
            String filePart = source.substring(fileIndex);
            int bang = filePart.indexOf('!');
            if (bang >= 0) {
               filePart = filePart.substring(0, bang);
            }
            return Paths.get(URI.create(filePart));
         }
         return Paths.get(uri);
      } catch (Exception e) {
         LOGGER.log(System.Logger.Level.WARNING, "[MLua] Failed to resolve own jar path for agent attach: " + e.getMessage());
         return null;
      }
   }

   private static java.lang.instrument.Instrumentation resolveTransformerInstrumentation() {
      try {
         ClassLoader runtimeLoader = MLuaAgentBootstrap.class.getClassLoader();
         Class<?> transformer = findCompatibleTransformerClass(runtimeLoader, runtimeLoader);
         if (transformer == null) {
            ClassLoader moduleLoader = ModuleLayer.boot().findLoader("mlua_loader");
            transformer = findCompatibleTransformerClass(moduleLoader, runtimeLoader);
         }
         if (transformer == null) return null;
         return (java.lang.instrument.Instrumentation) transformer.getMethod("resolveInstrumentation").invoke(null);
      } catch (Throwable ignored) {
         return null;
      }
   }

   private static Class<?> findCompatibleTransformerClass(ClassLoader candidate, ClassLoader runtimeLoader) {
      try {
         Class<?> transformer = Class.forName("dev.mlua.transformer.MLuaMixinTransformer", false, candidate);
         Class<?> runtimeEngine = Class.forName("dev.mlua.common.mixin.MixinRuleEngine", false, runtimeLoader);
         Class<?> transformerEngine = Class.forName("dev.mlua.common.mixin.MixinRuleEngine", false,
            transformer.getClassLoader());
         return runtimeEngine == transformerEngine ? transformer : null;
      } catch (ClassNotFoundException | LinkageError unavailable) {
         return null;
      }
   }

   private static Path tryFabricModJarPath() {
      try {
         Class<?> loaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
         Object loader = loaderClass.getMethod("getInstance").invoke(null);
         Object modContainer = loaderClass.getMethod("getModContainer", String.class).invoke(loader, "mlua");
         if (modContainer == null) {
            return null;
         }

         Optional<?> optional = (Optional<?>) modContainer;
         if (!optional.isPresent()) {
            return null;
         }

         Object container = optional.get();
         Object originPaths = container.getClass().getMethod("getOriginPaths").invoke(container);
         List<?> paths = (List<?>) originPaths;
         if (paths.isEmpty()) {
            return null;
         }

         Path path = (Path) paths.get(0);
         return path != null && Files.isRegularFile(path) ? path : null;
      } catch (Throwable t) {
         LOGGER.log(System.Logger.Level.DEBUG, "[MLua] Fabric mod jar resolution failed: " + t);
         return null;
      }
   }
}
