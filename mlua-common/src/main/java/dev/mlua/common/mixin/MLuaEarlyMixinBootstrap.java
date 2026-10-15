package dev.mlua.common.mixin;

import dev.mlua.common.enums.ScriptType;
import dev.mlua.common.enums.Side;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MLuaEarlyMixinBootstrap {
   private static final Logger LOGGER = LoggerFactory.getLogger("MLua");
   private static final ThreadLocal<String> ACTIVE_SCRIPT_SCOPE = new ThreadLocal<>();

   private MLuaEarlyMixinBootstrap() {
   }

   public static void bootstrap() {
      if (MixinRuleEngine.isEarlyBootstrapDone()) return;
      try {
         Path gameDir = resolveGameDir();
         Globals globals = JsePlatform.standardGlobals();
         setupEarlyEnvironment(globals);

         int executedScripts = 0;
         Set<String> seen = new HashSet<>();
         Path scriptsDir = gameDir.resolve("mlua").resolve("scripts");
         if (Files.isDirectory(scriptsDir)) {
            executedScripts += executeMixinScriptsFromDir(scriptsDir, globals, "game directory " + scriptsDir, seen);
         }

         executedScripts += executeMixinScriptsFromClasspath(globals, seen);
         if (executedScripts > 0) {
            installAgentTransformerWhenNoModLauncherService();
            MixinRuleEngine.markEarlyBootstrapDone();
            LOGGER.info("[MLua] Early bootstrap complete - {} scripts executed, {} mixin rules registered",
               executedScripts, MixinRuleEngine.get().getRules().size());
         } else {
            LOGGER.info("[MLua] Early bootstrap found no mixin scripts in {}", gameDir);
         }
      } catch (Throwable e) {
         LOGGER.error("[MLua] Early bootstrap failed", e);
      }
   }

   private static void installAgentTransformerWhenNoModLauncherService() {
      String serviceResource = "META-INF/services/cpw.mods.modlauncher.api.ITransformationService";
      String mluaService = "dev.mlua.transformer.MLuaTransformService";
      try {
         Enumeration<URL> resources = MLuaEarlyMixinBootstrap.class.getClassLoader().getResources(serviceResource);
         while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            String contents;
            try (var input = resource.openStream()) {
               contents = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (contents.lines().map(String::trim).anyMatch(mluaService::equals)) return;
         }

         Class<?> bootstrap = Class.forName("dev.mlua.agent.MLuaAgentBootstrap", true,
            MLuaEarlyMixinBootstrap.class.getClassLoader());
         bootstrap.getMethod("ensureAttached").invoke(null);
      } catch (Throwable failure) {
         LOGGER.warn("[MLua] Could not install the early runtime mixin transformer", failure);
      }
   }

   private static Path resolveGameDir() {
      Path fabricGameDir = resolveFabricGameDir();
      if (fabricGameDir != null) {
         LOGGER.info("[MLua] Early bootstrap using Fabric gameDir {}", fabricGameDir);
         return fabricGameDir;
      }

      Path launchArgGameDir = resolveGameDirFromLaunchArgs();
      if (launchArgGameDir != null) {
         LOGGER.info("[MLua] Early bootstrap using launch argument gameDir {}", launchArgGameDir);
         return launchArgGameDir;
      }

      Path workingDir = Paths.get("").toAbsolutePath().normalize();
      LOGGER.info("[MLua] Early bootstrap falling back to working directory {}", workingDir);
      return workingDir;
   }

   private static Path resolveFabricGameDir() {
      try {
         Class<?> loaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader", false, MLuaEarlyMixinBootstrap.class.getClassLoader());
         Object loader = loaderClass.getMethod("getInstance").invoke(null);
         Object gameDir = loaderClass.getMethod("getGameDir").invoke(loader);
         if (gameDir instanceof Path path) {
            return path.toAbsolutePath().normalize();
         }
      } catch (Throwable ignored) {
      }

      return null;
   }

   private static Path resolveGameDirFromLaunchArgs() {
      try {
         Class<?> loaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader", false, MLuaEarlyMixinBootstrap.class.getClassLoader());
         Object loader = loaderClass.getMethod("getInstance").invoke(null);
         Object launchArgsValue = loaderClass.getMethod("getLaunchArguments", boolean.class).invoke(loader, true);
         if (!(launchArgsValue instanceof String[] launchArgs)) {
            return null;
         }

         for (int i = 0; i + 1 < launchArgs.length; i++) {
            if ("--gameDir".equals(launchArgs[i]) && launchArgs[i + 1] != null && !launchArgs[i + 1].isBlank()) {
               return Paths.get(launchArgs[i + 1]).toAbsolutePath().normalize();
            }
         }
      } catch (Throwable ignored) {
      }

      return null;
   }

   private static void setupEarlyEnvironment(Globals globals) {
      LuaTable minecraft = new LuaTable();
      minecraft.set("GetService", new TwoArgFunction() {
         @Override
         public LuaValue call(LuaValue self, LuaValue name) {
            String serviceName = name.checkjstring();
             if ("Mixin".equals(serviceName)) {
                return MLuaEarlyMixinApi.create(ACTIVE_SCRIPT_SCOPE::get);
             }
             if ("Java".equals(serviceName)) {
                return MLuaEarlyJavaService.create();
             }

            return MLuaUnavailableService.create(serviceName);
         }
      });
      globals.set("Minecraft", minecraft);
   }

   private static int executeMixinScriptsFromDir(Path dir, Globals globals, String sourceLabel, Set<String> seen) {
      List<Path> scripts;
      try (Stream<Path> files = Files.walk(dir)) {
         scripts = files.filter(MLuaEarlyMixinBootstrap::isEarlyMixinScript)
            .filter(script -> seen.add(MLuaMixinCallbackRegistry.scriptScope(
               script.toAbsolutePath().normalize().toString())))
            .sorted()
            .collect(Collectors.toList());
      } catch (Exception e) {
         LOGGER.error("[MLua] Failed to scan directory " + dir, e);
         return 0;
      }

      if (scripts.isEmpty()) {
         return 0;
      }

      LOGGER.info("[MLua] Early bootstrapping {} mixin scripts from {}", scripts.size(), sourceLabel);
      int executed = 0;
      for (Path script : scripts) {
         try {
            String source = Files.readString(script, StandardCharsets.UTF_8);
            if (executeScript(script.getFileName().toString(), sourceLabel, script.toAbsolutePath().normalize().toString(),
                  () -> globals.load(source, script.toString()).call())) {
               executed++;
            }
         } catch (Exception e) {
            LOGGER.error("[MLua] Failed to read mixin script '" + script + "'", e);
         }
      }
      return executed;
   }

   private static int executeMixinScriptsFromClasspath(Globals globals, Set<String> seen) {
      int count = 0;

      try {
         ClassLoader cl = MLuaEarlyMixinBootstrap.class.getClassLoader();

         Enumeration<URL> dirs = cl != null ? cl.getResources("mlua/scripts") : ClassLoader.getSystemResources("mlua/scripts");
         List<URL> dirResources = Collections.list(dirs);
         for (URL u : dirResources) {
            if ("file".equals(u.getProtocol())) {
               Path path = Paths.get(u.toURI());
               count += executeMixinScriptsFromDir(path, globals, "classpath directory " + path, seen);
            }
         }

         count += executeMixinScriptsFromCodeSource(globals, seen);
         count += executeMixinScriptsFromJavaClassPath(globals, seen);
      } catch (Exception e) {
         LOGGER.error("[MLua] Failed to scan classpath for mixin scripts", e);
      }

      return count;
   }

   private static int executeMixinScriptsFromCodeSource(Globals globals, Set<String> seen) {
      try {
         URL location = MLuaEarlyMixinBootstrap.class.getProtectionDomain().getCodeSource().getLocation();
         if (location == null) {
            return 0;
         }

         String external = location.toExternalForm();
         if ("file".equals(location.getProtocol())) {
            Path path = Paths.get(location.toURI());
            if (Files.isDirectory(path)) {
               Path scriptsDir = path.resolve("mlua").resolve("scripts");
               return Files.isDirectory(scriptsDir)
                  ? executeMixinScriptsFromDir(scriptsDir, globals, "code source directory " + scriptsDir, seen)
                  : 0;
            }

            return executeMixinScriptsFromJarPath(external, globals, seen);
         }

         return executeMixinScriptsFromJarPath(external, globals, seen);
      } catch (Exception e) {
         LOGGER.error("[MLua] Failed to scan code source for mixin scripts", e);
      }

      return 0;
   }

   private static int executeMixinScriptsFromJavaClassPath(Globals globals, Set<String> seen) {
      String classPath = System.getProperty("java.class.path");
      if (classPath == null || classPath.isBlank()) {
         return 0;
      }

      int count = 0;
      String[] entries = classPath.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator));
      for (String entry : entries) {
         if (entry == null || entry.isBlank()) {
            continue;
         }

         try {
            Path path = Paths.get(entry);
            if (Files.isDirectory(path)) {
               Path scriptsDir = path.resolve("mlua").resolve("scripts");
               if (Files.isDirectory(scriptsDir)) {
                  count += executeMixinScriptsFromDir(scriptsDir, globals, "classpath directory " + scriptsDir, seen);
               }
            } else {
               count += executeMixinScriptsFromJarPath(entry, globals, seen);
            }
         } catch (Exception e) {
            LOGGER.debug("[MLua] Skipping classpath entry '{}': {}", entry, e.getMessage());
         }
      }

      return count;
   }

   private static int executeMixinScriptsFromJarPath(String source, Globals globals, Set<String> seen) {
      try {
         String jarPath = extractJarPath(source);
         if (jarPath == null) {
            return 0;
         }

         try (JarFile jar = new JarFile(jarPath)) {
            return enumerateAndExecuteFromJar(jar, globals, seen, jarPath);
         }
      } catch (Exception e) {
         LOGGER.debug("[MLua] Failed to scan jar path '{}': {}", source, e.getMessage());
         return 0;
      }
   }

   private static String extractJarPath(String source) {
      String lower = source.toLowerCase();
      int jarIndex = lower.indexOf(".jar");
      if (jarIndex < 0) {
         return null;
      }

      String jarPath = source.substring(0, jarIndex + 4);
      int schemeIndex = jarPath.indexOf(":/");
      if (schemeIndex >= 0) {
         String scheme = jarPath.substring(0, schemeIndex).toLowerCase();
         if ("file".equals(scheme) || "union".equals(scheme) || "jar".equals(scheme)) {
            jarPath = jarPath.substring(schemeIndex + 2);
         }
      }

      jarPath = jarPath.replace("%23", "#");
      jarPath = URLDecoder.decode(jarPath, StandardCharsets.UTF_8);
      if (jarPath.startsWith("/") && jarPath.length() > 2 && jarPath.charAt(2) == ':') {
         jarPath = jarPath.substring(1);
      }

      return jarPath;
   }

   private static int enumerateAndExecuteFromJar(JarFile jar, Globals globals, Set<String> seen, String jarPath) {
      List<String> toExecute = new ArrayList<>();
      Enumeration<JarEntry> entries = jar.entries();
      while (entries.hasMoreElements()) {
         JarEntry entry = entries.nextElement();
         String name = entry.getName();
         if (name.startsWith("mlua/scripts/") && !entry.isDirectory()) {
            String fileName = name.substring("mlua/scripts/".length());
            String scriptPath = jarPath + "!/mlua/scripts/" + fileName;
            if (isEarlyMixinScriptName(fileName) && seen.add(MLuaMixinCallbackRegistry.scriptScope(scriptPath))) {
               toExecute.add(fileName);
            }
         }
      }

      if (toExecute.isEmpty()) {
         return 0;
      }

      Collections.sort(toExecute);
      LOGGER.info("[MLua] Early bootstrapping {} mixin scripts from JAR {}", toExecute.size(), jarPath);
      int count = 0;
      for (String scriptName : toExecute) {
         String entryName = "mlua/scripts/" + scriptName;
         JarEntry entry = jar.getJarEntry(entryName);
         if (entry != null) {
            try {
               String source = new String(jar.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
               if (executeScript(scriptName, "JAR " + jarPath,
                     jarPath + "!/mlua/scripts/" + scriptName,
                     () -> globals.load(source, scriptName).call())) {
                  count++;
               }
            } catch (Exception e) {
               LOGGER.error("[MLua] Failed to read script '" + scriptName + "'", e);
            }
         }
      }

      return count;
   }

   private static boolean executeScript(String name, String sourceLabel, String scriptPath, Runnable task) {
      String previousScope = ACTIVE_SCRIPT_SCOPE.get();
      String scope = MLuaMixinCallbackRegistry.scriptScope(scriptPath);
      ACTIVE_SCRIPT_SCOPE.set(scope);
      try {
         task.run();
         LOGGER.info("[MLua] Early-executed mixin script: {} from {}", name, sourceLabel);
         return true;
      } catch (Throwable e) {
         MixinRuleEngine.get().unregisterScope(scope);
         LOGGER.error("[MLua] Early mixin script '" + name + "' from " + sourceLabel + " failed", e);
         if (e instanceof Error error) throw error;
         return false;
      } finally {
         if (previousScope == null) ACTIVE_SCRIPT_SCOPE.remove();
         else ACTIVE_SCRIPT_SCOPE.set(previousScope);
      }
   }

   private static boolean isEarlyMixinScript(Path path) {
      return isEarlyMixinScriptName(path.getFileName().toString());
   }

   private static boolean isEarlyMixinScriptName(String name) {
      ScriptType type = ScriptType.mixinType(name);
      return type != null && (type.mixinSide() != Side.CLIENT || isClientEnvironment());
   }

   private static boolean isClientEnvironment() {
      String target = System.getProperty("mlua.launchTarget");
      if (target != null) {
         return target.toLowerCase().contains("client");
      }

      String[] hints = new String[]{
         System.getProperty("sun.java.command"),
         System.getProperty("libraryDirectory"),
         System.getProperty("launchTarget")
      };

      for (String hint : hints) {
         if (hint != null && hint.toLowerCase().contains("client")) {
            return true;
         }
      }

      return false;
   }

}
