package dev.mlua.core.engine;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaThread;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Prototype;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;
import dev.mlua.common.enums.ScriptType;
import dev.mlua.common.enums.Side;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.MLuaDocumentation;
import dev.mlua.core.MLuaLogger;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.core.script.MLuaScript;
import dev.mlua.loaderapi.LoaderApi;
import dev.mlua.module.MLuaModuleLoader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class LuaJEngineBackend implements MLuaEngineBackend {
   private static final MLuaLuauTranspiler TRANSPILER = new MLuaLuauTranspiler();
   private static final String[] ENTRYPOINT_NAMES = new String[]{"run_mixin", "run_registry", "run_commands", "run_server", "run_client"};
   private List<MLuaScript> scripts;
   private final Map<Side, Globals> globalsBySide = new EnumMap<>(Side.class);
   private MLuaContext currentContext;

   public LuaJEngineBackend(List<MLuaScript> scripts) {
      this.scripts = List.copyOf(scripts);
   }

   @Override
   public synchronized void replaceScripts(List<MLuaScript> scripts) {
      this.scripts = List.copyOf(scripts);
      this.evaluationCache.clear();
      this.compiledPrototypes.clear();
   }

   @Override
   public synchronized void resetSide(Side side) {
      this.globalsBySide.remove(side);
      this.evaluationCache.keySet().removeIf(key -> key.side() == side);
      this.compiledPrototypes.keySet().removeIf(key -> key.side() == side);
   }

   public String name() {
      return "MLua";
   }

   public synchronized MLuaValue execute(MLuaScript script, MLuaContext context) {
      MLuaContext previousContext = this.currentContext;

      MLuaValue var8;
      try {
         LuaValue result = this.evaluate(script, context).result();
         var8 = new MLuaValue(result);
      } catch (LuaError var12) {
         MLuaLogger.LOGGER.error("Script error in {}\nType: {}\nSide: {}\nPhase: {}\nError: {}\nRequire chain:\n{}", new Object[]{script.displayName(), script.type(), context.side(), context.phase(), var12.getMessage(), this.requireChain(context)});
         throw var12;
      } finally {
         this.writeRuntimeApiDocs(context);
         this.currentContext = previousContext;
      }

      return var8;
   }

   public synchronized MLuaValue executeResumable(MLuaScript script, MLuaContext context) {
      MLuaContext previousContext = this.currentContext;

      MLuaValue var9;
      try {
         Globals globals = this.globals(context);
         LuaTable previousEntrypoints = this.snapshotEntrypoints(globals);

         try {
            LuaValue chunk = this.loadChunk(globals, script, context.side());
            LuaThread thread = new LuaThread(globals, chunk);
            Varargs result = thread.resume(LuaValue.NONE);
            if (!result.arg1().toboolean()) {
               throw new LuaError(result.arg(2).tojstring());
            }

            context.scheduler().scheduleResumableThread(globals, thread, result);
            LuaValue value = result.narg() >= 2 ? result.arg(2) : LuaValue.NIL;
            var9 = new MLuaValue(value);
         } finally {
            this.restoreEntrypoints(globals, previousEntrypoints);
         }
      } catch (LuaError var13) {
         MLuaLogger.LOGGER.error("Script error in {}\nType: {}\nSide: {}\nPhase: {}\nError: {}\nRequire chain:\n{}", new Object[]{script.displayName(), script.type(), context.side(), context.phase(), var13.getMessage(), this.requireChain(context)});
         throw var13;
      } finally {
         this.writeRuntimeApiDocs(context);
         this.currentContext = previousContext;
      }

      return var9;
   }

   public synchronized boolean executeEntrypoint(MLuaScript script, MLuaContext context, String entrypointName) {
      MLuaContext previousContext = this.currentContext;

      try {
         ScriptEvaluation evaluation = this.evaluate(script, context);
         LuaValue entrypoints = this.entrypointContainer(evaluation);
         LuaValue function = entrypoints.get(entrypointName);
         if (function.isnil()) {
            return false;
         } else if (!function.isfunction()) {
            throw new LuaError("[MLua] Entrypoint '" + entrypointName + "' in " + script.displayName() + " must be a function.");
         } else {
            function.call(MLuaLuaBindings.scriptContextTable(context));
            return true;
         }
      } catch (LuaError var12) {
         MLuaLogger.LOGGER.error("Script error in {}\nType: {}\nSide: {}\nPhase: {}\nError: {}\nRequire chain:\n{}", new Object[]{script.displayName(), script.type(), context.side(), context.phase(), var12.getMessage(), this.requireChain(context)});
         throw var12;
      } finally {
         this.writeRuntimeApiDocs(context);
         this.currentContext = previousContext;
      }
   }

   public synchronized MLuaValue require(String path, MLuaContext context) {
      ResolvedModule resolved = this.resolveRequire(path, context.script());
      String id = resolved.cacheId();
      if (context.moduleCache().contains(id)) {
         return context.moduleCache().get(id);
      } else {
         MLuaScript module = (MLuaScript)this.scripts.stream().filter((script) -> {
            return script.type() == ScriptType.MODULE || script.type() == ScriptType.REGISTRY || script.type() == ScriptType.ENTRYPOINT;
         }).filter((script) -> {
            return script.ownerModId().equals(resolved.ownerModId());
         }).filter((script) -> {
            return script.moduleId().equals(resolved.moduleId());
         }).findFirst().orElseThrow(() -> {
            return new LuaError("[MLua] Require could not find module '" + path + "' resolved as '" + id + "' from " + context.script().displayName());
         });
         context.moduleCache().enter(id);

         MLuaValue var7;
         try {
            MLuaValue value = this.execute(module, context.withScript(module));
            context.moduleCache().put(id, value);
            var7 = value;
         } finally {
            context.moduleCache().exit(id);
         }

         return var7;
      }
   }

   private Globals globals(MLuaContext context) {
      Globals globals = this.globalsBySide.computeIfAbsent(context.side(), ignored -> this.createGlobals(context));
      context.bindGlobals(globals);
      this.configureContextGlobals(globals, context);
      this.currentContext = context;
      return globals;
   }

   /** Writes a safe inventory of the Lua globals for this script's side and phase. */
   private void writeRuntimeApiDocs(MLuaContext context) {
      try {
         MLuaLuaApiCrawler.CrawlResult crawl = MLuaLuaApiCrawler.crawl(
            this.globals(context), context.materializedServices());
         MLuaDocumentation.writeRuntimeLuaApi(
            LoaderApi.platform().gameDir(),
            context.side().name(),
            context.phase().name(),
            context.script().displayName(),
            crawl
         );
      } catch (RuntimeException error) {
         MLuaLogger.LOGGER.warn("Could not generate Lua API documentation", error);
      }
   }

   private Globals createGlobals(MLuaContext initialContext) {
      Globals globals = JsePlatform.standardGlobals();
      globals.set("warn", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaLogger.LOGGER.warn(LuaJEngineBackend.this.luaArgs(args));
            return LuaValue.NONE;
         }
      });
      globals.set("print", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaLogger.LOGGER.info(LuaJEngineBackend.this.luaArgs(args));
            return LuaValue.NONE;
         }
      });
      globals.set("typeof", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            return MLuaLuauTypes.typeName(args.arg1());
         }
      });
      globals.set("assert", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            if (!args.arg1().toboolean()) {
               throw new LuaError(args.narg() > 1 ? args.arg(2).tojstring() : "assertion failed!");
            } else {
               return args;
            }
         }
      });
      globals.set("Require", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaContext requireContext = LuaJEngineBackend.this.currentContext != null
               ? LuaJEngineBackend.this.currentContext
               : MLuaLuaBindings.boundContext(globals);
            if (requireContext == null) {
               throw new LuaError("[MLua] Require called without an active script context");
            } else {
               MLuaValue value = LuaJEngineBackend.this.require(args.checkjstring(1), requireContext);
               Object var5 = value.raw();
               LuaValue var10000;
               if (var5 instanceof LuaValue) {
                  LuaValue lua = (LuaValue)var5;
                  var10000 = lua;
               } else {
                  var10000 = LuaValue.NIL;
               }

               return var10000;
            }
         }
      });
      globals.set("Signal", MLuaLuaBindings.signalTable(globals, initialContext.scheduler()));
      // Static Lua values are contributed during initialization; class tables and
      // registration-bound globals are resolved for each active context below.
      MLuaLuauTypes.install(globals);
      MLuaModuleLoader.get().contributeGlobals(globals, initialContext.side());
      return globals;
   }

   private void configureContextGlobals(Globals globals, MLuaContext context) {
      MLuaLuaBindings.bindContext(globals, context);
      LuaTable task = MLuaLuaBindings.taskTable(context.scheduler(), globals);
      globals.set("task", task);
      globals.set("wait", task.get("wait"));
      globals.set("Minecraft", MLuaLuaBindings.minecraftTable(context));
      MLuaModuleLoader.get().contributeContextGlobals(globals, context);
   }

   private ResolvedModule resolveRequire(String requested, MLuaScript current) {
      String path = requested.replace('\\', '/');
      if (!path.isBlank() && !path.contains("//")) {
         if (!path.startsWith("./") && !path.startsWith("../")) {
            String dotPath = path.replace('/', '.');
            Optional<MLuaScript> explicit = this.scripts.stream().filter((script) -> {
               return script.type() == ScriptType.MODULE || script.type() == ScriptType.REGISTRY || script.type() == ScriptType.ENTRYPOINT;
            }).filter((script) -> {
               return (script.ownerModId() + "." + script.moduleId()).equals(dotPath);
            }).findFirst();
            return explicit.isPresent() ? new ResolvedModule(((MLuaScript)explicit.get()).ownerModId(), ((MLuaScript)explicit.get()).moduleId()) : new ResolvedModule(current.ownerModId(), dotPath);
         } else {
            Path currentPath = current.path();
            Path currentDir = currentPath.getParent();
            Path root = current.root();
            Path base = currentDir == null ? Path.of("") : root.relativize(currentDir);
            Path resolved = base.resolve(path).normalize();
            if (resolved.startsWith("..") || resolved.toString().equals("..")) {
               throw new LuaError("[MLua] Relative Require escaped script root: " + requested);
            } else {
               String resolvedPathString = resolved.toString().replace(root.getFileSystem().getSeparator(), "/");
               return new ResolvedModule(current.ownerModId(), this.stripLuaSuffix(resolvedPathString));
            }
         }
      } else {
         throw new LuaError("[MLua] Invalid Require path: " + requested);
      }
   }

   private String stripLuaSuffix(String path) {
      String id = path.endsWith(".lua") ? path.substring(0, path.length() - 4) : path;
      return id.replace('/', '.');
   }

   private final Map<EvaluationKey, ScriptEvaluation> evaluationCache = new java.util.HashMap<>();
   private final Map<CompiledChunkKey, Prototype> compiledPrototypes = new java.util.HashMap<>();

   private LuaValue loadChunk(Globals globals, MLuaScript script, Side side) {
      String chunkName = script.displayName();
      String source = TRANSPILER.transpile(script.source());
      CompiledChunkKey key = new CompiledChunkKey(side, chunkName, source);
      Prototype prototype = this.compiledPrototypes.get(key);
      try {
         if (prototype == null) {
            prototype = globals.compilePrototype(new StringReader(source), chunkName);
            this.compiledPrototypes.put(key, prototype);
         }
         return globals.loader.load(prototype, chunkName, globals);
      } catch (IOException error) {
         throw new LuaError("Could not load script '" + chunkName + "': " + error.getMessage());
      }
   }

   private ScriptEvaluation evaluate(MLuaScript script, MLuaContext context) {
      EvaluationKey key = new EvaluationKey(script, context.side());
      if (script.type() == ScriptType.ENTRYPOINT && this.evaluationCache.containsKey(key)) {
         return this.evaluationCache.get(key);
      }

      Globals globals = this.globals(context);
      LuaTable previousEntrypoints = this.snapshotEntrypoints(globals);

      try {
         LuaValue chunk = this.loadChunk(globals, script, context.side());
         LuaValue result = chunk.call();
         LuaTable currentEntrypoints = this.snapshotEntrypoints(globals);
         ScriptEvaluation evaluation = new ScriptEvaluation(result, currentEntrypoints);
         if (script.type() == ScriptType.ENTRYPOINT) {
            this.evaluationCache.put(key, evaluation);
         }
         return evaluation;
      } finally {
         this.restoreEntrypoints(globals, previousEntrypoints);
      }
   }

   private LuaTable entrypointContainer(ScriptEvaluation evaluation) {
      LuaTable table = new LuaTable();
      
      // If the script returned a table, use it as a source
      if (evaluation.result().istable()) {
         LuaTable resultTable = evaluation.result().checktable();
         for (String name : ENTRYPOINT_NAMES) {
            LuaValue value = resultTable.get(name);
            if (!value.isnil()) {
               table.set(name, value);
            }
         }
      }

      // Also check the globals snapshot for entrypoints
      for (String name : ENTRYPOINT_NAMES) {
         LuaValue value = evaluation.entrypoints().get(name);
         if (!value.isnil() && table.get(name).isnil()) {
            table.set(name, value);
         }
      }

      return table;
   }

   private LuaTable snapshotEntrypoints(Globals globals) {
      LuaTable values = new LuaTable();
      String[] var3 = ENTRYPOINT_NAMES;
      int var4 = var3.length;

      for(int var5 = 0; var5 < var4; ++var5) {
         String name = var3[var5];
         values.set(name, globals.get(name));
      }

      return values;
   }

   private void restoreEntrypoints(Globals globals, LuaTable values) {
      String[] var3 = ENTRYPOINT_NAMES;
      int var4 = var3.length;

      for(int var5 = 0; var5 < var4; ++var5) {
         String name = var3[var5];
         LuaValue value = values.get(name);
         if (value.isnil()) {
            globals.set(name, LuaValue.NIL);
         } else {
            globals.set(name, value);
         }
      }

   }

   private String luaArgs(Varargs args) {
      StringBuilder builder = new StringBuilder();

      for(int i = 1; i <= args.narg(); ++i) {
         if (i > 1) {
            builder.append(' ');
         }

         builder.append(args.arg(i).tojstring());
      }

      return builder.toString();
   }

   private String requireChain(MLuaContext context) {
      if (context.moduleCache().chain().isEmpty()) {
         return "  <none>";
      } else {
         StringBuilder builder = new StringBuilder();
         Iterator var3 = context.moduleCache().chain().iterator();

         while(var3.hasNext()) {
            String item = (String)var3.next();
            builder.append("  ").append(item).append('\n');
         }

         return builder.toString().stripTrailing();
      }
   }

   private static record ResolvedModule(String ownerModId, String moduleId) {
      private ResolvedModule(String ownerModId, String moduleId) {
         this.ownerModId = ownerModId;
         this.moduleId = moduleId;
      }

      String cacheId() {
         return this.ownerModId + "." + this.moduleId;
      }

      public String ownerModId() {
         return this.ownerModId;
      }

      public String moduleId() {
         return this.moduleId;
      }
   }

   private static record ScriptEvaluation(LuaValue result, LuaTable entrypoints) {
      private ScriptEvaluation(LuaValue result, LuaTable entrypoints) {
         this.result = result;
         this.entrypoints = entrypoints;
      }

      public LuaValue result() {
         return this.result;
      }

      public LuaTable entrypoints() {
         return this.entrypoints;
      }
   }

   private static record EvaluationKey(MLuaScript script, Side side) { }
   private static record CompiledChunkKey(Side side, String chunkName, String source) { }
}
