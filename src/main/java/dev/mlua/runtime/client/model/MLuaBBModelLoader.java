package dev.mlua.runtime.client.model;

import dev.mlua.loaderapi.LoaderApi;
import dev.mlua.loaderapi.ScriptRootRef;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MLuaBBModelLoader {
   private static final Map<Path, CachedAsset> CACHE = new ConcurrentHashMap<>();
   private MLuaBBModelLoader() { }

   public static MLuaBBModelAsset load(String path) throws IOException {
      Path requested = Path.of(path);
      if (requested.isAbsolute() && Files.isRegularFile(requested)) return cached(requested);
      Path[] roots = LoaderApi.platform().bundledScriptRoots().stream().map(ScriptRootRef::path)
         .toArray(Path[]::new);
      for (Path root : roots) {
         Path candidate = root.resolve(path).normalize();
         if (candidate.startsWith(root.normalize()) && Files.isRegularFile(candidate)) return cached(candidate);
      }
      Path candidate = LoaderApi.platform().mluaScriptDir().resolve(path).normalize();
      if (!candidate.startsWith(LoaderApi.platform().mluaScriptDir().normalize()) || !Files.isRegularFile(candidate)) {
         throw new IOException("[MLua] BBModel not found: " + path);
      }
      return cached(candidate);
   }

   public static Map<String, MLuaBBModelAsset.Animation> loadAnimations(String path) throws IOException {
      Path file = resolve(path);
      return MLuaBBModelAsset.parseAnimations(Files.readString(file, StandardCharsets.UTF_8));
   }

   private static MLuaBBModelAsset cached(Path path) throws IOException {
      Path normalized = path.toAbsolutePath().normalize();
      long modified = Files.getLastModifiedTime(normalized).toMillis();
      CachedAsset cached = CACHE.get(normalized);
      if (cached != null && cached.modified == modified) return cached.asset;
      MLuaBBModelAsset asset = parse(normalized);
      CACHE.put(normalized, new CachedAsset(modified, asset));
      return asset;
   }

   private static Path resolve(String path) throws IOException {
      Path requested = Path.of(path);
      if (requested.isAbsolute() && Files.isRegularFile(requested)) return requested;
      Path[] roots = LoaderApi.platform().bundledScriptRoots().stream().map(ScriptRootRef::path).toArray(Path[]::new);
      for (Path root : roots) {
         Path candidate = root.resolve(path).normalize();
         if (candidate.startsWith(root.normalize()) && Files.isRegularFile(candidate)) return candidate;
      }
      Path scriptDir = LoaderApi.platform().mluaScriptDir();
      Path candidate = scriptDir.resolve(path).normalize();
      if (candidate.startsWith(scriptDir.normalize()) && Files.isRegularFile(candidate)) return candidate;
      throw new IOException("[MLua] BBModel animation file not found: " + path);
   }

   private static MLuaBBModelAsset parse(Path path) throws IOException {
      return MLuaBBModelAsset.parse(Files.readString(path, StandardCharsets.UTF_8));
   }

   private record CachedAsset(long modified, MLuaBBModelAsset asset) { }
}
