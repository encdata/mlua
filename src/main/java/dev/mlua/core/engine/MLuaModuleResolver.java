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

   void replaceScripts(List<MLuaScript> scripts) {
      Map<ModuleKey, List<MLuaScript>> mutable = new HashMap<>();
      Map<String, Resolution> qualified = new HashMap<>();
      for (MLuaScript script : scripts) {
         if (!isRequireable(script)) continue;
         ModuleKey key = new ModuleKey(script.ownerModId(), script.moduleId());
         mutable.computeIfAbsent(key, ignored -> new ArrayList<>()).add(script);
         qualified.putIfAbsent(script.ownerModId() + "." + script.moduleId(),
            new Resolution(script.ownerModId(), script.moduleId()));
      }
      Map<ModuleKey, List<MLuaScript>> indexedModules = new HashMap<>(mutable.size());
      mutable.forEach((key, matches) -> indexedModules.put(key, List.copyOf(matches)));
      this.snapshot = new Snapshot(Map.copyOf(indexedModules), Map.copyOf(qualified));
   }

   Resolution resolve(String requested, MLuaScript current) {
      String path = requested.replace('\\', '/');
      if (path.isBlank() || path.contains("//")) {
         throw new LuaError("[MLua] Invalid Require path: " + requested);
      }
      if (path.startsWith("./") || path.startsWith("../")) return this.resolveRelative(path, requested, current);

      int namespaceSeparator = path.indexOf(':');
      if (namespaceSeparator >= 0) {
         if (namespaceSeparator == 0 || namespaceSeparator == path.length() - 1
               || path.indexOf(':', namespaceSeparator + 1) >= 0) {
            throw invalidNamespacePath(requested);
         }
         String owner = path.substring(0, namespaceSeparator);
         String module = normalizeModule(path.substring(namespaceSeparator + 1), requested);
         if (!owner.matches("[a-z0-9_.-]+") || module.isBlank()) throw invalidNamespacePath(requested);
         return new Resolution(owner, module);
      }

      String dotPath = path.replace('/', '.');
      Resolution explicit = this.snapshot.modulesByQualifiedId().get(dotPath);
      return explicit != null ? explicit : new Resolution(current.ownerModId(), dotPath);
   }

   List<MLuaScript> find(String ownerModId, String moduleId) {
      return this.snapshot.modulesById().getOrDefault(new ModuleKey(ownerModId, moduleId), List.of());
   }

   private Resolution resolveRelative(String path, String requested, MLuaScript current) {
      Path currentPath = current.path();
      Path root = current.root();
      Path currentDir = currentPath.getParent();
      Path base = currentDir == null ? Path.of("") : root.relativize(currentDir);
      Path resolved = base.resolve(path).normalize();
      if (resolved.startsWith("..") || resolved.toString().equals("..")) {
         throw new LuaError("[MLua] Relative Require escaped script root: " + requested);
      }
      String relativePath = resolved.toString().replace(root.getFileSystem().getSeparator(), "/");
      return new Resolution(current.ownerModId(), stripLuaSuffix(relativePath));
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

   private static String stripLuaSuffix(String path) {
      String id = path.endsWith(".lua") ? path.substring(0, path.length() - 4) : path;
      return id.replace('/', '.');
   }

   private static boolean isRequireable(MLuaScript script) {
      return script.type() == ScriptType.MODULE || script.type() == ScriptType.REGISTRY
         || script.type() == ScriptType.ENTRYPOINT;
   }

   private static LuaError invalidNamespacePath(String requested) {
      return new LuaError("[MLua] Invalid namespaced Require path: " + requested
         + ". Use require(\"modid:scriptname\").");
   }

   record Resolution(String ownerModId, String moduleId) {
      String cacheId() { return this.ownerModId + "." + this.moduleId; }
   }

   private record Snapshot(Map<ModuleKey, List<MLuaScript>> modulesById,
                           Map<String, Resolution> modulesByQualifiedId) {
      static Snapshot empty() { return new Snapshot(Map.of(), Map.of()); }
   }

   private record ModuleKey(String ownerModId, String moduleId) { }
}
