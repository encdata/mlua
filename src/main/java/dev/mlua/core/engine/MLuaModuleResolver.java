package dev.mlua.core.engine;

import dev.mlua.common.enums.ScriptType;
import dev.mlua.core.script.MLuaScript;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaError;

/** Resolves require paths against one immutable script snapshot. */
final class MLuaModuleResolver {
   private static final List<String> SCRIPT_SUFFIXES = Arrays.stream(ScriptType.values())
      .map(ScriptType::suffix)
      .filter(suffix -> !".lua".equals(suffix))
      .distinct()
      .sorted(java.util.Comparator.comparingInt(String::length).reversed())
      .toList();

   private volatile Snapshot snapshot = Snapshot.empty();

   Object snapshot() {
      return this.snapshot;
   }

   long generation(Object snapshotToken) {
      return snapshotToken instanceof Snapshot captured ? captured.generation() : this.snapshot.generation();
   }

   void replaceScripts(List<MLuaScript> scripts) {
      Map<ModuleKey, List<MLuaScript>> mutable = new HashMap<>();
      Map<String, List<ModuleKey>> qualified = new HashMap<>();
      for (MLuaScript script : scripts) {
         if (!isRequireable(script)) continue;
         ModuleKey key = new ModuleKey(script.ownerModId(), script.moduleId());
         mutable.computeIfAbsent(key, ignored -> new ArrayList<>()).add(script);
         List<ModuleKey> candidates = qualified.computeIfAbsent(
            script.ownerModId() + "." + script.moduleId(), ignored -> new ArrayList<>());
         if (!candidates.contains(key)) candidates.add(key);
      }
      Map<ModuleKey, List<MLuaScript>> indexedModules = new HashMap<>(mutable.size());
      mutable.forEach((key, matches) -> indexedModules.put(key, List.copyOf(matches)));
      Map<String, List<ModuleKey>> indexedQualified = new HashMap<>(qualified.size());
      qualified.forEach((key, candidates) -> indexedQualified.put(key, List.copyOf(candidates)));
      this.snapshot = new Snapshot(this.snapshot.generation() + 1,
         Map.copyOf(indexedModules), Map.copyOf(indexedQualified));
   }

   Resolution resolve(String requested, MLuaScript current, Object snapshotToken) {
      Snapshot snapshot = snapshotToken instanceof Snapshot captured ? captured : this.snapshot;
      String path = requested.replace('\\', '/');
      if (path.isBlank() || path.contains("//")) {
         throw new LuaError("[MLua] Invalid Require path: " + requested);
      }
      if (path.startsWith("./") || path.startsWith("../")) {
         return this.resolveRelative(path, requested, current, snapshot);
      }

      int namespaceSeparator = path.indexOf(':');
      if (namespaceSeparator >= 0) {
         if (namespaceSeparator == 0 || namespaceSeparator == path.length() - 1
               || path.indexOf(':', namespaceSeparator + 1) >= 0) {
            throw invalidNamespacePath(requested);
         }
         String owner = path.substring(0, namespaceSeparator);
         String module = normalizeModule(path.substring(namespaceSeparator + 1), requested);
         if (!owner.matches("[a-z0-9_.-]+") || module.isBlank()) throw invalidNamespacePath(requested);
         return this.resolution(snapshot, owner, module);
      }

      String dotPath = normalizeModule(path, requested);
      List<ModuleKey> explicit = snapshot.modulesByQualifiedId().get(dotPath);
      if (explicit != null && explicit.size() > 1) {
         throw new LuaError("[MLua] Ambiguous Require path '" + requested
            + "'. Use require(\"modid:scriptname\") to select an addon explicitly.");
      }
      ModuleKey module = explicit != null
         ? explicit.get(0) : new ModuleKey(current.ownerModId(), dotPath);
      return this.resolution(snapshot, module.ownerModId(), module.moduleId());
   }

   private Resolution resolveRelative(String path, String requested, MLuaScript current,
                                      Snapshot snapshot) {
      Path currentPath = current.path();
      Path root = current.root();
      Path currentDir = currentPath.getParent();
      Path base = currentDir == null ? Path.of("") : root.relativize(currentDir);
      Path resolved = base.resolve(path).normalize();
      if (resolved.startsWith("..") || resolved.toString().equals("..")) {
         throw new LuaError("[MLua] Relative Require escaped script root: " + requested);
      }
      String relativePath = resolved.toString().replace(root.getFileSystem().getSeparator(), "/");
      return this.resolution(snapshot, current.ownerModId(), normalizeModule(relativePath, requested));
   }

   private Resolution resolution(Snapshot snapshot, String ownerModId, String moduleId) {
      ModuleKey key = new ModuleKey(ownerModId, moduleId);
      return new Resolution(snapshot.generation(), ownerModId, moduleId,
         snapshot.modulesById().getOrDefault(key, List.of()));
   }

   private static String normalizeModule(String path, String requested) {
      String module = path;
      for (String suffix : SCRIPT_SUFFIXES) {
         if (module.endsWith(suffix)) {
            module = module.substring(0, module.length() - suffix.length());
            break;
         }
      }
      if (module.endsWith(".lua")) module = module.substring(0, module.length() - 4);
      module = module.replace('/', '.');
      if (module.startsWith(".") || module.endsWith(".") || module.contains("..")) {
         throw new LuaError("[MLua] Invalid Require script name: " + requested);
      }
      return module;
   }

   private static boolean isRequireable(MLuaScript script) {
      return script.type() == ScriptType.MODULE || script.type() == ScriptType.REGISTRY
         || script.type() == ScriptType.ENTRYPOINT;
   }

   private static LuaError invalidNamespacePath(String requested) {
      return new LuaError("[MLua] Invalid namespaced Require path: " + requested
         + ". Use require(\"modid:scriptname\").");
   }

   record Resolution(long generation, String ownerModId, String moduleId, List<MLuaScript> matches) {
      String cacheId() {
         return this.generation + ":" + this.ownerModId.length() + ":" + this.ownerModId + this.moduleId;
      }
   }

   private record Snapshot(long generation, Map<ModuleKey, List<MLuaScript>> modulesById,
                           Map<String, List<ModuleKey>> modulesByQualifiedId) {
      static Snapshot empty() { return new Snapshot(0, Map.of(), Map.of()); }
   }

   private record ModuleKey(String ownerModId, String moduleId) { }
}
