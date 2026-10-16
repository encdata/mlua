package dev.mlua.modules.files.Service;

import dev.mlua.core.MLuaPathRules;
import dev.mlua.api.MLuaDescription;
import dev.mlua.core.service.MLuaReflectiveService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.Path;
import java.util.Optional;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

@MLuaDescription("Provides owner-scoped file operations in the MLua data directory.")
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

   @MLuaDescription("Reads a UTF-8 text file from the calling mod's data directory.")
   public String ReadText(String relative) {
      Path path = resolve(relative);
      try {
         return Files.readString(path, StandardCharsets.UTF_8);
      } catch (IOException failure) {
         throw fileError("read", relative, failure);
      }
   }

   @MLuaDescription("Writes UTF-8 text to a file in the calling mod's data directory.")
   public boolean WriteText(String relative, String content) {
      Path path = resolve(relative);
      try {
         Files.createDirectories(path.getParent());
         Files.writeString(path, content, StandardCharsets.UTF_8);
         return true;
      } catch (IOException failure) {
         throw fileError("write", relative, failure);
      }
   }

   @MLuaDescription("Appends UTF-8 text to a file, creating the file and parent directories when needed.")
   public boolean AppendText(String relative, String content) {
      Path path = resolve(relative);
      try {
         Files.createDirectories(path.getParent());
         Files.writeString(path, content, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
         return true;
      } catch (IOException failure) {
         throw fileError("append", relative, failure);
      }
   }

   @MLuaDescription("Returns whether a file or directory exists inside the calling mod's data directory.")
   public boolean Exists(String relative) {
      return Files.exists(resolve(relative));
   }

   @MLuaDescription("Returns whether a path inside the calling mod's data directory is a directory.")
   public boolean IsDirectory(String relative) {
      return Files.isDirectory(resolve(relative));
   }

   @MLuaDescription("Creates a directory and any missing parent directories inside the calling mod's data directory.")
   public boolean CreateDirectory(String relative) {
      Path path = resolve(relative);
      try {
         Files.createDirectories(path);
         return true;
      } catch (IOException failure) {
         throw fileError("create directory", relative, failure);
      }
   }

   @MLuaDescription("Returns the names of the direct children of a directory, sorted alphabetically. Omit the path to list the mod data root.")
   public LuaTable List(Optional<String> relative) {
      String requested = relative.orElse(".");
      Path directory = requested.equals(".") ? root() : resolve(requested);
      LuaTable result = new LuaTable();
      if (!Files.exists(directory) && requested.equals(".")) return result;
      if (!Files.isDirectory(directory)) throw new LuaError("[MLua] Files:List path is not a directory: " + requested);
      try (var children = Files.list(directory)) {
         var names = children.map(path -> path.getFileName().toString()).sorted().toList();
         for (int index = 0; index < names.size(); index++) result.set(index + 1, LuaValue.valueOf(names.get(index)));
         return result;
      } catch (IOException failure) {
         throw fileError("list", requested, failure);
      }
   }

   @MLuaDescription("Deletes a file or an empty directory inside the calling mod's data directory. Returns false when it does not exist.")
   public boolean Delete(String relative) {
      Path path = resolve(relative);
      if (path.equals(root())) throw new LuaError("[MLua] Files:Delete cannot remove the mod data root.");
      try {
         return Files.deleteIfExists(path);
      } catch (IOException failure) {
         throw fileError("delete", relative, failure);
      }
   }

   private Path resolve(String relative) {
      if (!MLuaPathRules.isModDataPath(relative)) {
         throw new LuaError("[MLua] Files path is outside the MLua data folder: " + relative);
      }
      Path root = root();
      Path relativePath = Path.of(relative).normalize();
      if (relativePath.toString().isEmpty()) throw new LuaError("[MLua] Files path must name a file or directory.");
      Path path = root.resolve(relativePath).normalize();
      if (!path.startsWith(root)) {
         throw new LuaError("[MLua] Files path traversal blocked: " + relative);
      }
      try {
         Path realRoot = resolvedPath(root);
         if (!resolvedPath(path).startsWith(realRoot)) {
            throw new LuaError("[MLua] Files path resolves outside the calling mod's data directory: " + relative);
         }
      } catch (IOException failure) {
         throw fileError("resolve", relative, failure);
      }
      return path;
   }

   private Path root() {
      Path root = this.context.filesRoot().toAbsolutePath().normalize();
      if (Files.isSymbolicLink(root)) {
         throw new LuaError("[MLua] The calling mod's data directory cannot be a symbolic link.");
      }
      return root;
   }

   /** Resolves existing symlink components while retaining the tail of a new path. */
   private static Path resolvedPath(Path path) throws IOException {
      Path existing = path;
      while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) existing = existing.getParent();
      if (existing == null) throw new IOException("no existing parent directory");
      Path realExisting = existing.toRealPath();
      return realExisting.resolve(existing.relativize(path)).normalize();
   }

   private static LuaError fileError(String operation, String path, IOException failure) {
      return new LuaError("[MLua] Failed to " + operation + " file path '" + path + "': " + failure.getMessage());
   }
}
