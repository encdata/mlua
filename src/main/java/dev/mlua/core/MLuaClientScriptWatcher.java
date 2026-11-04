package dev.mlua.core;

import dev.mlua.core.script.MLuaScriptRoot;
import java.io.IOException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

/** Watches file-backed script roots and debounces client hot reload requests. */
final class MLuaClientScriptWatcher {
   private static final long POLL_INTERVAL_MS = 500L;
   private static final long DEBOUNCE_MS = 900L;
   private static final long FULL_SCAN_INTERVAL_MS = 10_000L;

   private final Map<Path, Long> snapshot = new HashMap<>();
   private final Set<Path> trackedFiles = new HashSet<>();
   private boolean lastWindowActive;
   private boolean pendingReload;
   private long lastPollAt;
   private long lastChangeAt;
   private long lastFullScanAt;

   /** Returns true once a stable change is ready to reload while the window is active. */
   boolean poll(List<MLuaScriptRoot> roots, long now, BooleanSupplier windowActiveSupplier) {
      if (now - this.lastPollAt < POLL_INTERVAL_MS) return false;

      this.lastPollAt = now;
      if (this.refreshSnapshot(roots, now)) {
         this.pendingReload = true;
         this.lastChangeAt = now;
      }

      boolean windowActive = windowActiveSupplier.getAsBoolean();
      boolean reload = windowActive && this.pendingReload && now - this.lastChangeAt >= DEBOUNCE_MS;
      if (reload) this.pendingReload = false;
      this.lastWindowActive = windowActive;
      return reload;
   }

   boolean lastWindowActive() {
      return this.lastWindowActive;
   }

   private boolean refreshSnapshot(List<MLuaScriptRoot> roots, long now) {
      Map<Path, Long> current = now - this.lastFullScanAt >= FULL_SCAN_INTERVAL_MS || this.trackedFiles.isEmpty()
         ? this.scanAll(roots, now)
         : this.scanTracked(roots, now);

      if (this.snapshot.isEmpty()) {
         this.snapshot.putAll(current);
         return false;
      }

      boolean changed = !this.snapshot.equals(current);
      this.snapshot.clear();
      this.snapshot.putAll(current);
      return changed;
   }

   private Map<Path, Long> scanTracked(List<MLuaScriptRoot> roots, long now) {
      Map<Path, Long> current = new HashMap<>();
      List<Path> missing = new ArrayList<>();

      for (Path path : this.trackedFiles) {
         try {
            if (Files.isRegularFile(path)) {
               current.put(path, Files.getLastModifiedTime(path).toMillis());
            } else {
               missing.add(path);
            }
         } catch (IOException ignored) {
            missing.add(path);
         }
      }

      return missing.isEmpty() ? current : this.scanAll(roots, now);
   }

   private Map<Path, Long> scanAll(List<MLuaScriptRoot> roots, long now) {
      Map<Path, Long> current = new HashMap<>();

      for (MLuaScriptRoot root : roots) {
         if (!isWatchableRoot(root.path())) continue;

         try (Stream<Path> stream = Files.walk(root.path())) {
            stream.filter(path -> Files.isRegularFile(path))
               .filter(MLuaClientScriptWatcher::isLuaScriptFile)
               .forEach(path -> {
                  try {
                     current.put(path.toAbsolutePath().normalize(),
                        Files.getLastModifiedTime(path).toMillis());
                  } catch (IOException ignored) {
                  }
               });
         } catch (IOException exception) {
            MLuaLogger.LOGGER.warn("[MLua] Failed to scan script root for hot reload: {}", root.path(), exception);
         }
      }

      this.trackedFiles.clear();
      this.trackedFiles.addAll(current.keySet());
      this.lastFullScanAt = now;
      return current;
   }

   private static boolean isWatchableRoot(Path path) {
      try {
         return Files.isDirectory(path)
            && "file".equalsIgnoreCase(path.getFileSystem().provider().getScheme());
      } catch (FileSystemNotFoundException ignored) {
         return false;
      }
   }

   private static boolean isLuaScriptFile(Path path) {
      String name = path.getFileName().toString();
      return name.length() >= 4 && name.regionMatches(true, name.length() - 4, ".lua", 0, 4);
   }
}
