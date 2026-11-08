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
import dev.mlua.common.enums.RegistrationPhase;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.MLuaCallbackBudget;
import dev.mlua.core.MLuaDocumentation;
import dev.mlua.core.MLuaLogger;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.core.script.MLuaScript;
import dev.mlua.loaderapi.LoaderApi;
import dev.mlua.module.MLuaModuleLoader;
import java.io.IOException;
import java.io.StringReader;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class LuaJEngineBackend implements MLuaEngineBackend {
   private static final MLuaLuauTranspiler TRANSPILER = new MLuaLuauTranspiler();
   private static final String[] ENTRYPOINT_NAMES = new String[]{"run_mixin", "run_registry", "run_commands", "run_server", "run_client", "run_server_stopping", "run_world_load", "run_world_unload"};
   private final MLuaModuleResolver moduleResolver = new MLuaModuleResolver();
   private final Map<Side, Globals> globalsBySide = new EnumMap<>(Side.class);
   private final Map<RuntimeDocumentationKey, RuntimeDocumentationState> runtimeDocumentation = new java.util.HashMap<>();

   public LuaJEngineBackend(List<MLuaScript> scripts) {
      this.replaceScripts(scripts);
   }

   @Override
   public synchronized void replaceScripts(List<MLuaScript> scripts) {
      this.moduleResolver.replaceScripts(scripts);
      this.evaluationCache.clear();
      this.compiledPrototypes.clear();
      this.runtimeDocumentation.clear();
   }

   @Override
   public synchronized void resetSide(Side side) {
      this.globalsBySide.remove(side);
      this.evaluationCache.keySet().removeIf(key -> key.side() == side);
      this.compiledPrototypes.keySet().removeIf(key -> key.side() == side);
      this.runtimeDocumentation.keySet().removeIf(key -> key.side() == side);
   }

   public String name() {
      return "MLua";
   }

   public synchronized MLuaValue execute(MLuaScript script, MLuaContext context) {
      MLuaValue var8;
      try {
         LuaValue result = this.evaluate(script, context).result();
         var8 = new MLuaValue(result);
      } catch (LuaError var12) {
         MLuaLogger.LOGGER.error("Script error in {}\nType: {}\nSide: {}\nPhase: {}\nError: {}\nRequire chain:\n{}", new Object[]{script.displayName(), script.type(), context.side(), context.phase(), var12.getMessage(), this.requireChain(context)});
         throw var12;
      } finally {
         this.writeRuntimeApiDocs(context);
      }

      return var8;
   }

   public synchronized MLuaValue executeResumable(MLuaScript script, MLuaContext context) {
      MLuaValue var9;
      try {
         Globals globals = this.globals(context);
         LuaTable previousEntrypoints = this.snapshotEntrypoints(globals);

         try {
            LuaValue chunk = this.loadChunk(globals, script, context.side());
            LuaThread thread = new LuaThread(globals, chunk);
            Varargs result = MLuaCallbackBudget.runScript(context, globals,
               () -> thread.resume(LuaValue.NONE));
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
      }

      return var9;
   }

   public synchronized boolean executeEntrypoint(MLuaScript script, MLuaContext context, String entrypointName) {
      return this.executeEntrypoint(script, context, entrypointName, null);
   }

   @Override
   public synchronized boolean executeEntrypoint(MLuaScript script, MLuaContext context, String entrypointName,
                                                 LuaValue event) {
      try {
         ScriptEvaluation evaluation = this.evaluate(script, context);
         LuaValue entrypoints = this.entrypointContainer(evaluation);
         LuaValue function = entrypoints.get(entrypointName);
         if (function.isnil()) {
            return false;
         } else if (!function.isfunction()) {
            throw new LuaError("[MLua] Entrypoint '" + entrypointName + "' in " + script.displayName() + " must be a function.");
         } else {
            LuaValue scriptContext = MLuaLuaBindings.scriptContextTable(context);
            MLuaCallbackBudget.runScript(context, null, () -> {
               if (event == null) function.call(scriptContext);
               else function.invoke(LuaValue.varargsOf(new LuaValue[]{scriptContext, event}));
               return null;
            });
            return true;
         }
      } catch (LuaError var12) {
         MLuaLogger.LOGGER.error("Script error in {}\nType: {}\nSide: {}\nPhase: {}\nError: {}\nRequire chain:\n{}", new Object[]{script.displayName(), script.type(), context.side(), context.phase(), var12.getMessage(), this.requireChain(context)});
         throw var12;
      } finally {
         this.writeRuntimeApiDocs(context);
      }
   }

   public synchronized MLuaValue require(String path, MLuaContext context) {
      MLuaModuleResolver.Resolution resolved = this.moduleResolver.resolve(path, context.script());
      String id = resolved.cacheId();
      if (context.moduleCache().contains(id)) {
         return context.moduleCache().get(id);
      } else {
         List<MLuaScript> matches = this.moduleResolver.find(resolved.ownerModId(), resolved.moduleId());
         if (matches.isEmpty()) {
            throw new LuaError("[MLua] Require could not find module '" + path + "' resolved as '" + id + "' from " + context.script().displayName());
         }
         if (matches.size() > 1) {
            String candidates = matches.stream().map(MLuaScript::displayName)
               .sorted().collect(java.util.stream.Collectors.joining("\n  "));
            throw new LuaError("[MLua] Require found " + matches.size() + " scripts named '" + id
               + "'. Keep module names unique within a mod:\n  " + candidates);
         }
         MLuaScript module = matches.get(0);
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
      return globals;
   }

   /** Writes a safe inventory of the Lua globals for this script's side and phase. */
   private void writeRuntimeApiDocs(MLuaContext context) {
      Map<String, LuaTable> materializedServices = context.materializedServices();
      RuntimeDocumentationKey key = new RuntimeDocumentationKey(
         context.side(), context.phase(), context.script().displayName());
      RuntimeDocumentationState state = new RuntimeDocumentationState(
         MLuaModuleLoader.get().globalsRevision(),
         materializedServices.keySet().stream().sorted().toList());
      if (state.equals(this.runtimeDocumentation.get(key))) return;
      try {
         MLuaLuaApiCrawler.CrawlResult crawl = MLuaLuaApiCrawler.crawl(
            this.globals(context), materializedServices,
            MLuaDocumentation.registeredReturnTypes());
         MLuaDocumentation.writeRuntimeLuaApi(
            LoaderApi.platform().gameDir(),
            context.side().name(),
            context.phase().name(),
            context.script().displayName(),
            crawl
         );
         this.runtimeDocumentation.put(key, state);
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
      VarArgFunction requireFunction = new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaContext requireContext = MLuaLuaBindings.boundContext(globals);
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
      };
      globals.set("Require", requireFunction);
      globals.set("require", requireFunction);
      globals.set("Signal", MLuaLuaBindings.signalTable(globals, initialContext.scheduler()));
      // Static Lua values are contributed during initialization; class tables and
      // registration-bound globals are resolved for each active context below.
      MLuaLuauTypes.install(globals);
      MLuaModuleLoader.get().contributeGlobals(globals, initialContext.side());
      return globals;
   }

   private void configureContextGlobals(Globals globals, MLuaContext context) {
      MLuaLuaBindings.configureContextGlobals(globals, context);
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
         LuaValue result = MLuaCallbackBudget.runScript(context, globals, chunk::call);
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
   private static record RuntimeDocumentationKey(Side side, RegistrationPhase phase, String scriptName) { }
   private static record RuntimeDocumentationState(long moduleRevision, List<String> materializedServices) { }
}
