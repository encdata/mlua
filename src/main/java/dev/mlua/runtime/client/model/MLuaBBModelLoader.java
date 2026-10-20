package dev.mlua.runtime.client.model;

import dev.mlua.loaderapi.LoaderApi;
import dev.mlua.loaderapi.ScriptRootRef;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MLuaBBModelLoader {
   private static final int MAX_MODEL_BYTES = 8 * 1024 * 1024;
   private static final int MAX_ANIMATION_BYTES = 8 * 1024 * 1024;
   private static final int MAX_CACHED_ASSETS = 64;
   private static final long MAX_CACHED_SOURCE_BYTES = 16L * 1024L * 1024L;
   private static final LinkedHashMap<Path, CachedAsset> CACHE = new LinkedHashMap<>(16, 0.75F, true);
   private static long cachedSourceBytes;
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
      return MLuaBBModelAsset.parseAnimations(readLimited(file, MAX_ANIMATION_BYTES));
   }

   private static synchronized MLuaBBModelAsset cached(Path path) throws IOException {
      Path normalized = path.toAbsolutePath().normalize();
      FileTime modified = Files.getLastModifiedTime(normalized);
      long size = Files.size(normalized);
      if (size > MAX_MODEL_BYTES) throw new IOException("[MLua] Blockbench model exceeds the 8 MiB source limit: " + normalized.getFileName());
      CachedAsset cached = CACHE.get(normalized);
      if (cached != null && cached.modified == modified && cached.size == size) return cached.asset;
      MLuaBBModelAsset asset = parse(normalized);
      if (cached != null) cachedSourceBytes -= cached.size;
      CachedAsset replacement = new CachedAsset(modified, size, asset);
      CACHE.put(normalized, replacement);
      cachedSourceBytes += size;
      trimCache();
      return asset;
   }

   private static void trimCache() {
      Iterator<Map.Entry<Path, CachedAsset>> iterator = CACHE.entrySet().iterator();
      while ((CACHE.size() > MAX_CACHED_ASSETS || cachedSourceBytes > MAX_CACHED_SOURCE_BYTES)
            && iterator.hasNext()) {
         Map.Entry<Path, CachedAsset> eldest = iterator.next();
         cachedSourceBytes -= eldest.getValue().size;
         iterator.remove();
      }
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
      return MLuaBBModelAsset.parse(readLimited(path, MAX_MODEL_BYTES));
   }

   private static String readLimited(Path path, int maximumBytes) throws IOException {
      long size = Files.size(path);
      if (size > maximumBytes) throw new IOException("[MLua] Blockbench asset exceeds the "
         + (maximumBytes / (1024 * 1024)) + " MiB source limit: " + path.getFileName());
      try (InputStream input = Files.newInputStream(path)) {
         byte[] bytes = input.readNBytes(maximumBytes + 1);
         if (bytes.length > maximumBytes) {
            throw new IOException("[MLua] Blockbench asset exceeds the "
               + (maximumBytes / (1024 * 1024)) + " MiB source limit: " + path.getFileName());
         }
         return new String(bytes, StandardCharsets.UTF_8);
      }
   }

   private record CachedAsset(FileTime modified, long size, MLuaBBModelAsset asset) { }
}
