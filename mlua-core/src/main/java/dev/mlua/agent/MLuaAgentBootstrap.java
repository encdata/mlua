package dev.mlua.agent;

import com.sun.tools.attach.VirtualMachine;
import dev.mlua.core.MLuaLogger;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

public final class MLuaAgentBootstrap {
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
            MLuaLogger.LOGGER.warn("[MLua] Could not resolve mod jar for self-attach: {}", jarPath);
            return;
         }

         String pid = ManagementFactory.getRuntimeMXBean().getName().split("@", 2)[0];
         if (!tryDirectAttach(pid, jarPath)) {
            launchHelperAttach(pid, jarPath);
         }

         if (resolveInstrumentation() != null) {
            MLuaLogger.LOGGER.info("[MLua] Self-attach agent loaded from {}", jarPath);
         } else {
            MLuaLogger.LOGGER.warn("[MLua] Self-attach completed but instrumentation is still unavailable");
         }
      } catch (Throwable t) {
         MLuaLogger.LOGGER.warn("[MLua] Failed to self-attach runtime transformer: {}", t.toString());
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
         MLuaLogger.LOGGER.info("[MLua] Direct self-attach unavailable, falling back to helper JVM: {}", t.toString());
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
         MLuaLogger.LOGGER.debug("[MLua] Fabric mod jar resolution not available: {}", t.toString());
      }

      try {
         URL location = MLuaAgentBootstrap.class.getProtectionDomain().getCodeSource().getLocation();
         if (location == null) {
            return null;
         }

         String source = location.toExternalForm();
         int jarIndex = source.toLowerCase().indexOf(".jar");
         if (jarIndex < 0) {
            return Paths.get(location.toURI());
         }

         String jarPath = source.substring(0, jarIndex + 4);
         if (jarPath.regionMatches(true, 0, "jar:", 0, 4)) {
            jarPath = jarPath.substring(4);
         }

         jarPath = java.net.URLDecoder.decode(jarPath, java.nio.charset.StandardCharsets.UTF_8);
         jarPath = jarPath.replace("%23", "#");
         if (jarPath.regionMatches(true, 0, "file:", 0, 5)) {
            jarPath = java.net.URI.create(jarPath).getPath();
         } else if (jarPath.regionMatches(true, 0, "union:", 0, 6)) {
            jarPath = jarPath.substring(6);
         }
         if (jarPath.startsWith("/") && jarPath.length() > 2 && jarPath.charAt(2) == ':') {
            jarPath = jarPath.substring(1);
         }
         return Paths.get(jarPath);
      } catch (Exception e) {
         MLuaLogger.LOGGER.warn("[MLua] Failed to resolve own jar path for agent attach: {}", e.getMessage());
         return null;
      }
   }

   private static Instrumentation resolveInstrumentation() {
      try {
         Class<?> transformerClass = Class.forName("dev.mlua.transformer.MLuaMixinTransformer");
         return (Instrumentation) transformerClass.getMethod("resolveInstrumentation").invoke(null);
      } catch (Exception e) {
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
         MLuaLogger.LOGGER.debug("[MLua] Fabric mod jar resolution failed: {}", t.toString());
         return null;
      }
   }

}
