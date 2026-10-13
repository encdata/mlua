package dev.mlua.core.script;

import dev.mlua.core.MLuaLogger;
import dev.mlua.common.enums.ScriptType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class MLuaScriptLoader {
   public List<MLuaScript> discover(List<MLuaScriptRoot> roots) {
      List<MLuaScript> scripts = new ArrayList();
      Iterator var3 = roots.iterator();

      while(true) {
         while(var3.hasNext()) {
            MLuaScriptRoot root = (MLuaScriptRoot)var3.next();
            if (!Files.isDirectory(root.path(), new LinkOption[0])) {
               MLuaLogger.LOGGER.info("Script root missing, skipped: {}", root.path());
            } else {
               try {
                  try (Stream<Path> stream = Files.walk(root.path())) {
                     stream.filter((x$0) -> {
                        return Files.isRegularFile(x$0, new LinkOption[0]);
                     }).filter((path) -> {
                        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".lua");
                     }).sorted(Comparator.comparing(Path::toString)).forEach((path) -> {
                        this.addScript(scripts, root, path);
                     });
                  }
               } catch (IOException var10) {
                  MLuaLogger.LOGGER.error("Failed to scan script root {}", root.path(), var10);
               }
            }
         }

         return scripts;
      }
   }

   private void addScript(List<MLuaScript> scripts, MLuaScriptRoot root, Path path) {
      try {
         String source = Files.readString(path, StandardCharsets.UTF_8);
         String name = path.getFileName().toString();
         ScriptType type = this.classify(name, source);
         String moduleId = this.moduleId(root.path(), path, type);
         scripts.add(new MLuaScript(root.modId(), type, root.path(), path, moduleId, source));
      } catch (IOException var8) {
         MLuaLogger.LOGGER.error("Failed to read script {}", path, var8);
      }

   }

   private ScriptType classify(String name, String source) {
      return ScriptType.classify(name, source);
   }

   private String moduleId(Path root, Path path, ScriptType type) {
      Path relative = root.relativize(path);
      String relativeString = relative.toString().replace(root.getFileSystem().getSeparator(), "/");
      String suffix = type.suffix();
      if (type == ScriptType.REGISTRY && relativeString.endsWith(".register.lua")) {
         relativeString = relativeString.substring(0, relativeString.length() - ".register.lua".length());
      } else if (relativeString.endsWith(suffix)) {
         relativeString = relativeString.substring(0, relativeString.length() - suffix.length());
      }

      return relativeString.replace('/', '.');
   }
}
