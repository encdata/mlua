package dev.mlua.modules.files.Service;

import dev.mlua.core.MLuaPathRules;
import dev.mlua.core.service.MLuaReflectiveService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;

/** Mod-scoped file access exposed through the shared reflective Java service base. */
public final class MLuaFilesService extends MLuaReflectiveService {
   public MLuaFilesService(Context context) {
      super(context);
   }

   @Override
   public String name() {
      return "Files";
   }

   @Override
   public LuaTable toLua() {
      LuaTable table = super.toLua();
      table.set("__side", "Both");
      return table;
   }

   public String ReadText(String relative) {
      Path path = resolve(relative);
      try {
         return Files.readString(path, StandardCharsets.UTF_8);
      } catch (IOException failure) {
         throw new LuaError("[MLua] Failed to read file: " + failure.getMessage());
      }
   }

   public boolean WriteText(String relative, String content) {
      Path path = resolve(relative);
      try {
         Files.createDirectories(path.getParent());
         Files.writeString(path, content, StandardCharsets.UTF_8);
         return true;
      } catch (IOException failure) {
         throw new LuaError("[MLua] Failed to write file: " + failure.getMessage());
      }
   }

   private Path resolve(String relative) {
      if (!MLuaPathRules.isModDataPath(relative)) {
         throw new LuaError("[MLua] Files path is outside the MLua data folder: " + relative);
      }
      Path root = this.context.filesRoot().resolve(this.context.ownerModId()).normalize();
      Path path = root.resolve(relative).normalize();
      if (!path.startsWith(root)) {
         throw new LuaError("[MLua] Files path traversal blocked: " + relative);
      }
      return path;
   }
}
