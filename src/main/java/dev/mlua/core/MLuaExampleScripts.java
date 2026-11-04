package dev.mlua.core;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Copies bundled starter scripts without ever executing or overwriting them. */
public final class MLuaExampleScripts {
   private static final String RESOURCE_ROOT = "assets/mlua/example-scripts/";
   private MLuaExampleScripts() {
   }

   public static void copy(Path gameDir) {
      Path targetRoot = gameDir.resolve("mlua").resolve("example-scripts");
      int copied = 0;
      try {
         Files.createDirectories(targetRoot);
         ClassLoader loader = MLuaExampleScripts.class.getClassLoader();
         for (String file : resourceFiles()) {
            Path target = targetRoot.resolve(file);
            if (Files.exists(target)) {
               continue;
            }
            try (InputStream input = loader.getResourceAsStream(RESOURCE_ROOT + file)) {
               if (input == null) {
                  MLuaLogger.LOGGER.warn("Bundled example resource is missing: {}", file);
                  continue;
               }
               Files.copy(input, target);
               copied++;
            }
         }
         if (copied > 0) {
            MLuaLogger.LOGGER.info("Copied {} starter script example(s) to {}", copied, targetRoot);
         }
      } catch (IOException exception) {
         MLuaLogger.LOGGER.warn("Could not prepare MLua example scripts", exception);
      }
   }

   /** Returns the bundled Lua templates with the summary written in each file header. */
   public static List<ExampleScript> scripts() {
      List<ExampleScript> scripts = new ArrayList<>();
      ClassLoader loader = MLuaExampleScripts.class.getClassLoader();
      for (String file : resourceFiles()) {
         if (!file.endsWith(".lua")) continue;
         try (InputStream input = loader.getResourceAsStream(RESOURCE_ROOT + file)) {
            if (input == null) continue;
            String source = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            String description = source.lines().takeWhile(line -> line.startsWith("--"))
               .map(line -> line.substring(2).trim()).filter(line -> !line.isEmpty())
               .collect(java.util.stream.Collectors.joining(" "));
            scripts.add(new ExampleScript(file, description, source));
         } catch (IOException exception) {
            MLuaLogger.LOGGER.warn("Could not read bundled example script {}", file, exception);
         }
      }
      return List.copyOf(scripts);
   }

   private static List<String> resourceFiles() {
      ClassLoader loader = MLuaExampleScripts.class.getClassLoader();
      List<String> files = new ArrayList<>();
      try {
         URL root = loader.getResource(RESOURCE_ROOT);
         if (root == null) return List.of();
         if ("file".equals(root.getProtocol())) {
            try (var paths = Files.list(Path.of(root.toURI()))) {
               paths.filter(Files::isRegularFile).map(path -> path.getFileName().toString()).forEach(files::add);
            }
         } else if ("jar".equals(root.getProtocol())) {
            JarURLConnection connection = (JarURLConnection) root.openConnection();
            connection.setUseCaches(false);
            try (JarFile jar = connection.getJarFile()) {
               Enumeration<JarEntry> entries = jar.entries();
               while (entries.hasMoreElements()) {
                  String name = entries.nextElement().getName();
                  if (name.startsWith(RESOURCE_ROOT) && !name.equals(RESOURCE_ROOT)) {
                     String relative = name.substring(RESOURCE_ROOT.length());
                     if (!relative.isEmpty() && !relative.contains("/")) files.add(relative);
                  }
               }
            }
         }
      } catch (Exception exception) {
         MLuaLogger.LOGGER.warn("Could not enumerate bundled MLua example scripts", exception);
      }
      files.sort(Comparator.naturalOrder());
      return List.copyOf(files);
   }

   public record ExampleScript(String name, String description, String source) { }
}
