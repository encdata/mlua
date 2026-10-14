package dev.mlua.core;

import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModuleLoader;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.modules.enums.EnumsModule;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

public final class MLuaDocumentation {
   private static Path currentGameDir;
   private static Path sourceRoot;
   private static volatile List<MLuaDocBuilder.Contribution> activeContributions = List.of();
   private static final String CSS = "*{margin:0;padding:0;box-sizing:border-box}\n:root{--bg-0:#121215;--bg-100:#191a1f;--bg-200:#202227;--bg-300:#272930;--text:#e5e7eb;--text-em:#ffffff;--text-muted:#d1d5db;--link:#60a5fa;--border:rgba(208,217,251,0.12);--border-em:rgba(208,217,251,0.16);--hover:rgba(208,217,251,0.08);--active:rgba(208,217,251,0.12);--blue-700:#3b82f6;--green-500:#22c55e;--red-800:#ef4444;--yellow-400:#eab308;--radius:8px}\nbody{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Helvetica Neue',Arial,sans-serif;background:var(--bg-0);color:var(--text);font-size:16px;line-height:1.6;display:flex;min-height:100vh;overflow:hidden}\n::-webkit-scrollbar{width:8px}::-webkit-scrollbar-track{background:transparent}::-webkit-scrollbar-thumb{background:var(--bg-300);border-radius:4px}::-webkit-scrollbar-thumb:hover{background:var(--text-muted)}\n.app{display:flex;width:100%;height:100vh}\n.sidebar{width:300px;min-width:300px;background:var(--bg-0);border-right:1px solid var(--border);display:flex;flex-direction:column;height:100vh}\n.sidebar-header{padding:20px 24px;border-bottom:1px solid var(--border)}\n.sidebar-logo{font-size:20px;font-weight:700;color:var(--text-em);text-decoration:none;margin-bottom:16px}\n.sidebar-logo span{color:var(--link)}\n.sidebar-search{position:relative;margin-bottom:8px}\n.sidebar-search input{width:100%;background:var(--bg-200);border:1px solid var(--border);border-radius:6px;padding:8px 12px 8px 32px;color:var(--text-em);font-size:13px;outline:none;transition:border-color 0.15s}\n.sidebar-search input:focus{border-color:var(--link)}\n.sidebar-search::before{content:'🔍';position:absolute;left:10px;top:50%;transform:translateY(-50%);font-size:12px;opacity:0.5}\n.sidebar-content{flex:1;overflow-y:auto;padding:16px 12px}\n.sidebar-item{margin-bottom:4px}\n.sidebar-row{display:flex;align-items:center;padding:8px 12px;border-radius:6px;cursor:pointer;transition:all 0.15s;text-decoration:none;color:var(--text);font-size:14px;justify-content:space-between}\n.sidebar-row:hover{background:var(--hover);color:var(--text-em)}\n.sidebar-row.active{background:var(--active);color:var(--link);font-weight:500}\n.sidebar-arrow{width:16px;height:16px;display:flex;align-items:center;justify-content:center;color:var(--text-muted);transition:transform 0.2s}\n.sidebar-arrow.open{transform:rotate(90deg)}\n.sidebar-arrow::before{content:'▶';font-size:8px}\n.sidebar-children{padding-left:16px;display:none}\n.sidebar-children.open{display:block}\n.main{flex:1;min-width:0;display:flex;flex-direction:column;background:var(--bg-200);position:relative}\n.search-overlay{position:absolute;top:56px;left:0;right:0;bottom:0;background:var(--bg-0);z-index:100;display:none;flex-direction:column;padding:32px 64px}\n.search-overlay.open{display:flex}\n.search-results{flex:1;overflow-y:auto}\n.search-result-item{padding:16px;border-bottom:1px solid var(--border);text-decoration:none;display:block;border-radius:8px;transition:background 0.15s}\n.search-result-item:hover{background:var(--hover)}\n.search-result-title{font-weight:600;color:var(--link);margin-bottom:4px;display:flex;align-items:center;gap:8px}\n.search-result-type{font-size:11px;text-transform:uppercase;color:var(--text-muted);background:var(--bg-200);padding:2px 6px;border-radius:4px}\n.search-result-desc{font-size:14px;color:var(--text-muted)}\n.topbar{background:var(--bg-0);border-bottom:1px solid var(--border);padding:12px 32px;display:flex;align-items:center;min-height:56px}\n.topbar-breadcrumb{font-size:13px;color:var(--text-muted);display:flex;align-items:center;gap:8px}\n.topbar-breadcrumb a{color:var(--text-muted);text-decoration:none}\n.topbar-breadcrumb a:hover{color:var(--text-em)}\n.topbar-breadcrumb .sep{color:var(--border-em)}\n.topbar-breadcrumb .current{color:var(--text-em);font-weight:500}\n.content-scroll{flex:1;overflow-y:auto}\n.content{padding:48px 64px 96px;max-width:1000px;margin:0 auto}\nh1{font-size:40px;font-weight:700;color:var(--text-em);margin-bottom:12px;letter-spacing:-0.5px}\n.desc{font-size:20px;color:var(--text-muted);margin-bottom:40px;line-height:1.5}\nh2{font-size:24px;font-weight:600;color:var(--text-em);margin-top:56px;margin-bottom:20px;padding-bottom:12px;border-bottom:1px solid var(--border)}\npre{background:var(--bg-200);border:1px solid var(--border);border-radius:var(--radius);padding:20px;margin-bottom:24px;overflow-x:auto}\ncode{font-family:'JetBrains Mono','Fira Code',monospace;font-size:14px}\ntable{width:100%;border-collapse:collapse;margin-bottom:32px}\nth{text-align:left;font-size:12px;text-transform:uppercase;letter-spacing:1px;color:var(--text-muted);padding:12px 16px;background:var(--bg-0);border-bottom:2px solid var(--border)}\ntd{padding:12px 16px;border-bottom:1px solid var(--border);font-size:14px}\n.lua-api-filter{width:min(100%,32rem);padding:10px 14px;margin:12px 0 24px;background:var(--bg-0);color:var(--text);border:1px solid var(--border);border-radius:6px;font:inherit}.lua-api-filter:focus{border-color:var(--link);outline:none}\n.syntax-keyword{color:#60a5fa;font-weight:600}.syntax-string{color:#fb923c}.syntax-number{color:#c4b5fd}.syntax-comment{color:#4ade80}\n.member{background:var(--bg-0);border:1px solid var(--border);border-radius:var(--radius);padding:24px;margin-bottom:20px}\n.member-header{display:flex;align-items:flex-start;gap:8px;flex-wrap:wrap}.member-name{color:var(--link);font-weight:600;font-size:18px}.member-flags{margin-left:auto;white-space:nowrap}\n.member-name a{color:inherit;text-decoration:none}\n.member-desc{color:var(--text-muted);font-size:15px}\n.badge{font-size:10px;font-weight:700;padding:2px 6px;border-radius:4px;text-transform:uppercase;margin-right:6px}\n.badge.server{background:rgba(242,186,42,0.15);color:var(--yellow-400)}\n.badge.client{background:rgba(82,139,255,0.15);color:var(--link)}\n.callout{padding:16px 20px;border-radius:var(--radius);margin-bottom:32px;border-left:4px solid}\n.callout.info{background:rgba(82,139,255,0.05);border-color:var(--link)}\n.badge.read{background:rgba(82,139,255,0.15);color:var(--link)}\n.badge.shared{background:rgba(208,217,251,0.08);color:var(--text-muted)}\n.badge.write{background:rgba(242,186,42,0.15);color:var(--yellow-400)}\n.badge.call{background:rgba(57,197,130,0.15);color:var(--green-500)}\n.badge.both{background:rgba(168,85,247,0.15);color:#a855f7}\n.badge.registry{background:rgba(236,72,153,0.15);color:#ec4899}\n.badge.inherited{background:rgba(107,114,128,0.15);color:#9ca3af}\n.inherits-from{color:var(--text-muted);font-size:14px;margin-bottom:16px;padding:8px 12px;background:var(--bg-100);border-radius:6px;display:inline-block}\n";

   private MLuaDocumentation() {
   }

   public static void generate(Path gameDir) {
      currentGameDir = gameDir;
      MLuaExampleScripts.copy(gameDir);
      Path docs = gameDir.resolve("mlua").resolve("docs");
      sourceRoot = MLuaApiDocScanner.findSourceRoot(docs).orElse(null);

      try {
         Files.createDirectories(docs);
         Files.createDirectories(docs.resolve("classes"));
          Files.createDirectories(docs.resolve("services"));
          Files.createDirectories(docs.resolve("enums"));
          Files.createDirectories(docs.resolve("data-types"));
          Files.createDirectories(docs.resolve("guides"));
          Files.createDirectories(docs.resolve("libraries"));
          Files.createDirectories(docs.resolve("api"));

          // Collect addon and built-in reference metadata once for all generated outputs.
          MLuaDocBuilder builder = new MLuaDocBuilder();
          MLuaModuleLoader.get().generateDocs(builder);
          MLuaRuntime.get().documentationContributions().forEach(builder::contribution);
          activeContributions = builder.contributions();
          for (MLuaDocBuilder.DocEntry entry : runtimeDocEntries(builder)) {
             String fp = entry.filePath();
             int lastSlash = fp.lastIndexOf('/');
             String sidebarKey = lastSlash >= 0 ? fp.substring(0, lastSlash) + "/" + fp.substring(lastSlash + 1).replace(".html", "") : fp.replace(".html", "");
             Path outPath = docs.resolve(fp);
             Files.createDirectories(outPath.getParent());
             String title = entry.name() + (entry.type() == MLuaDocBuilder.DocEntryType.SERVICE ? " Service" : "");
             String content = moduleReferenceContent(entry, builder.guideSteps(), builder.methods(), builder.signatures(), builder.serviceFunctions(), builder.members());
             write(outPath, page(title + " - MLua", sidebar(sidebarKey), content));
          }

           List<DluaTypeSpec> specs = collectDluaTypeSpecs(builder);
           Optional<Path> dluaSourceRoot = sourceRoot != null ? Optional.of(sourceRoot) :
              (currentGameDir != null ? MLuaApiDocScanner.findSourceRoot(currentGameDir) : Optional.empty());

           Map<String, List<String>> mcClasses = scanMinecraftClasses();
           generateDluaFile(docs.resolve("api"), specs, dluaSourceRoot, mcClasses,
              builder.methods(), builder.signatures(), builder.types(), builder.fields());
           generateExtensionFiles(docs.resolve("api"), specs, dluaSourceRoot, mcClasses, builder.signatures());
          write(docs.resolve("index.html"), page("MLua Documentation", sidebar("index"), overviewContent()));
          write(docs.resolve("api").resolve("lua.html"), page("Lua API - MLua", sidebar("api/lua"),
             content("Lua API", "Browse the Lua classes, services, and globals registered by MLua.", null,
                callout("info", "MLua builds this inventory by reading Lua globals and service tables. Registered class and service descriptions appear beside their members. The crawl reads tables and registration metadata without calling API functions or property accessors."))));
          Files.deleteIfExists(docs.resolve("live-lua-api.html"));
           write(docs.resolve("classes").resolve("command-context.html"), page("CommandContext - MLua", sidebar("classes/command-context"), commandContextContent()));
           // Discover and generate all enum documentation pages
           for (EnumInfo ei : discoverEnums()) {
              write(docs.resolve("enums").resolve(ei.fileName + ".html"), page(ei.title + " - MLua", sidebar("enums/" + ei.fileName), ei.toContent()));
           }
            write(docs.resolve("enums").resolve("enum.html"), page("Enum - MLua", sidebar("enums/enum"), enumOverviewContent()));
           write(docs.resolve("guides").resolve("getting-started.html"), page("Getting Started - MLua", sidebar("guides/getting-started"), gettingStartedContent(builder.contributions())));
          Files.deleteIfExists(docs.resolve("guides").resolve("scripts.html"));
          Files.deleteIfExists(docs.resolve("guides").resolve("network-guide.html"));
          Files.deleteIfExists(docs.resolve("guides").resolve("commands-guide.html"));
          write(docs.resolve("guides").resolve("mod-integration.html"), page("Mod Integration - MLua", sidebar("guides/mod-integration"), modIntegrationContent()));
          write(docs.resolve("guides").resolve("addons.html"), page("Addon API - MLua", sidebar("guides/addons"), addonsGuideContent()));
          clearRegisteredAddonDocs(docs.resolve("guides"));
          clearRegisteredGuidePages(docs.resolve("guides"));
          writeContributionGuides(docs, builder.contributions());
          appendRegisteredClassMethods(docs, runtimeDocEntries(builder));
          } catch (IOException var3) {

         MLuaLogger.LOGGER.warn("Failed to generate runtime docs in {}", docs, var3);
      }

   }

    private static void generateDluaFile(Path apiDir, List<DluaTypeSpec> specs, Optional<Path> dluaSourceRoot,
                                         Map<String, List<String>> mcClasses,
                                         List<MLuaDocBuilder.MethodDoc> methodDocs,
                                         List<MLuaDocBuilder.SignatureDoc> signatures,
                                         List<MLuaDocBuilder.TypeDoc> typeDocs,
                                         List<MLuaDocBuilder.FieldDoc> fieldDocs) throws IOException {
       StringBuilder sb = new StringBuilder();
       sb.append("-- MLua API Definitions\n");
       sb.append("-- Generated from runtime docs plus scanned Lua-facing exports.\n");
       sb.append("---@meta\n\n");

        appendDluaPrelude(sb);
        appendMixinDluaTypes(sb, mcClasses);
        appendOptionTypes(sb, typeDocs, fieldDocs);

       List<DluaTypeSpec> allSpecs = expandNestedDluaSpecs(specs, dluaSourceRoot);
      Map<String, DluaTypeSpec> specsByReceiver = new LinkedHashMap<>();
      for (DluaTypeSpec spec : allSpecs) {
         specsByReceiver.putIfAbsent(spec.receiverName, spec);
      }

      Set<String> emittedTypeNames = new java.util.LinkedHashSet<>();
      List<DluaTypeSpec> serviceSpecs = new ArrayList<>();

      for (DluaTypeSpec spec : allSpecs) {
         if (!emittedTypeNames.add(spec.typeName)) {
            continue;
         }
         if (spec.kind == MLuaDocBuilder.DocEntryType.SERVICE) {
             serviceSpecs.add(spec);
         }
         appendTypeDefinition(sb, dluaSourceRoot, spec, mcClasses, specsByReceiver, methodDocs, signatures);
      }

       appendMinecraftGetServiceDefinition(sb, serviceSpecs);
       appendEnumDefinitions(sb);
       write(apiDir.resolve("mlua.d.lua"), sb.toString());
    }

   private static void appendDluaPrelude(StringBuilder sb) {
      sb.append("---@class Connection\n");
      sb.append("local Connection = {}\n\n");
      sb.append("function Connection:Disconnect() end\n\n");

      sb.append("---@class Signal\n");
      sb.append("local Signal = {}\n\n");
      sb.append("---@return Connection\n");
      sb.append("function Signal:Connect(...) end\n\n");
      sb.append("---@return Connection\n");
      sb.append("function Signal:Once(...) end\n\n");
      sb.append("---@return any\n");
      sb.append("function Signal:Wait(...) end\n\n");
      sb.append("function Signal:Fire(...) end\n\n");

      sb.append("---@class TaskLibrary\n");
      sb.append("task = {}\n\n");
      sb.append("function task.spawn(...) end\n");
      sb.append("function task.defer(...) end\n");
      sb.append("function task.delay(...) end\n");
      sb.append("---@return any\n");
      sb.append("function task.wait(...) end\n\n");
      sb.append("wait = task.wait\n\n");

      sb.append("---@class Minecraft\n");
      sb.append("Minecraft = {}\n\n");
      sb.append("---@return boolean\n");
      sb.append("function Minecraft:IsServer() end\n\n");
      sb.append("---@return boolean\n");
      sb.append("function Minecraft:IsClient() end\n\n");
      sb.append("---@return string\n");
      sb.append("function Minecraft:GetSide() end\n\n");
      sb.append("---@return number\n");

      sb.append("---@class Vector2\n");
      sb.append("---@field X number\n");
      sb.append("---@field Y number\n");
      sb.append("Vector2 = {}\n\n");
      sb.append("---@return Vector2\n");
      sb.append("function Vector2.new(x, y) end\n\n");

      sb.append("---@class Vector3\n");
      sb.append("---@field X number\n");
      sb.append("---@field Y number\n");
      sb.append("---@field Z number\n");
      sb.append("Vector3 = {}\n\n");
      sb.append("---@type Vector3\n");
      sb.append("Vector3.zero = nil\n");
      sb.append("---@type Vector3\n");
      sb.append("Vector3.one = nil\n");
      sb.append("---@type Vector3\n");
      sb.append("Vector3.xAxis = nil\n");
      sb.append("---@type Vector3\n");
      sb.append("Vector3.yAxis = nil\n");
      sb.append("---@type Vector3\n");
      sb.append("Vector3.zAxis = nil\n");
      sb.append("---@return Vector3\n");
      sb.append("function Vector3.new(x, y, z) end\n\n");

      sb.append("---@class CFrame\n");
      sb.append("CFrame = {}\n\n");
      sb.append("---@return CFrame\n");
      sb.append("function CFrame.new(...) end\n\n");
      sb.append("---@return CFrame\n");
      sb.append("function CFrame.lookAt(position, target) end\n\n");
      sb.append("---@return CFrame\n");
      sb.append("function CFrame.Angles(rx, ry, rz) end\n\n");

      sb.append("---@class Color3\n");
      sb.append("---@field R number\n");
      sb.append("---@field G number\n");
      sb.append("---@field B number\n");
      sb.append("Color3 = {}\n\n");
      sb.append("---@return Color3\n");
      sb.append("function Color3.new(r, g, b) end\n\n");
      sb.append("---@return Color3\n");
      sb.append("function Color3.fromRGB(r, g, b) end\n\n");
      sb.append("---@return Color3\n");
      sb.append("function Color3.fromHSV(h, s, v) end\n\n");

      sb.append("---@class UDim\n");
      sb.append("---@field Scale number\n");
      sb.append("---@field Offset number\n");
      sb.append("UDim = {}\n\n");
      sb.append("---@return UDim\n");
      sb.append("function UDim.new(scale, offset) end\n\n");

      sb.append("---@class UDim2\n");
      sb.append("---@field X UDim\n");
      sb.append("---@field Y UDim\n");
      sb.append("UDim2 = {}\n\n");
      sb.append("---@return UDim2\n");
      sb.append("function UDim2.new(xScale, xOffset, yScale, yOffset) end\n\n");
      sb.append("---@return UDim2\n");
      sb.append("function UDim2.fromScale(x, y) end\n\n");
      sb.append("---@return UDim2\n");
      sb.append("function UDim2.fromOffset(x, y) end\n\n");

      sb.append("function warn(...) end\n");
      sb.append("function print(...) end\n");
      sb.append("function assert(...) end\n");
      sb.append("---@return string\n");
      sb.append("function typeof(value) end\n");
      sb.append("---@return any\n");
      sb.append("function Require(path) end\n\n");
   }

   private static void appendMixinDluaTypes(StringBuilder sb, Map<String, List<String>> mcClasses) {
      appendLiteralAlias(sb, "MLuaMixinTargetClass", new ArrayList<>(mcClasses.keySet()));

      Set<String> methodNames = new java.util.TreeSet<>();
      for (List<String> methods : mcClasses.values()) {
         methodNames.addAll(methods);
      }
      appendLiteralAlias(sb, "MLuaMixinMethodName", new ArrayList<>(methodNames));

      sb.append("---@alias MLuaMixinInjectionPoint \"HEAD\"|\"RETURN\"\n\n");

      sb.append("---@class MLuaMixinInjectOptions\n");
      sb.append("---@field at? MLuaMixinInjectionPoint\n");
      sb.append("---@field descriptor? string\n");
      sb.append("---@field callback fun(self: any, ...: any)\n\n");

      sb.append("---@class MLuaMixinOverwriteOptions\n");
      sb.append("---@field descriptor? string\n");
      sb.append("---@field callback fun(self: any, ...: any)\n\n");

      sb.append("---@class MLuaMixinRedirectOptions\n");
      sb.append("---@field target string\n");
      sb.append("---@field callback fun(self: any, targetReceiver: any|nil, ...targetAndEnclosingArgs: any): any|nil\n\n");

      sb.append("---@class MLuaMixinModifyArgOptions\n");
      sb.append("---@field target string\n");
      sb.append("---@field index? number\n");
      sb.append("---@field callback fun(self: any, originalValue: any, ...enclosingMethodArgs: any): any|nil\n\n");
   }

   private static void appendOptionTypes(StringBuilder sb, List<MLuaDocBuilder.TypeDoc> typeDocs,
                                         List<MLuaDocBuilder.FieldDoc> fieldDocs) {
      Map<String, List<MLuaDocBuilder.FieldDoc>> fieldsByType = new LinkedHashMap<>();
      for (MLuaDocBuilder.FieldDoc field : fieldDocs) {
         fieldsByType.computeIfAbsent(field.typeName(), ignored -> new ArrayList<>()).add(field);
      }
      Set<String> declaredTypes = new java.util.HashSet<>();
      for (MLuaDocBuilder.TypeDoc type : typeDocs) {
         if (!declaredTypes.add(type.name())) continue;
         sb.append("---@class ").append(type.name());
         if (type.parent() != null && !type.parent().isBlank()) sb.append(": ").append(type.parent());
         sb.append("\n--- ").append(type.description()).append("\n");
         for (MLuaDocBuilder.FieldDoc field : fieldsByType.getOrDefault(type.name(), List.of())) {
            sb.append("---@field ").append(field.name()).append(" ").append(field.valueType())
               .append(" ").append(field.description()).append("\n");
         }
         sb.append("\n");
      }

   }

   private static void appendLiteralAlias(StringBuilder sb, String aliasName, List<String> values) {
      sb.append("---@alias ").append(aliasName);
      if (values.isEmpty()) {
         sb.append(" string\n\n");
         return;
      }
      sb.append("\n");
      values.sort(String::compareTo);
      for (String value : values) {
         sb.append("---| \"").append(value).append("\"\n");
      }
      sb.append("\n");
   }

   private static List<DluaTypeSpec> collectDluaTypeSpecs(MLuaDocBuilder builder) {
      java.util.LinkedHashMap<String, DluaTypeSpec> specs = new java.util.LinkedHashMap<>();
      Map<String, MLuaDocBuilder.DocEntry> documentedClasses = new LinkedHashMap<>();
      Map<String, MLuaDocBuilder.DocEntry> documentedServices = new LinkedHashMap<>();
      for (MLuaDocBuilder.DocEntry entry : builder.entries()) {
         if (entry.type() == MLuaDocBuilder.DocEntryType.CLASS) documentedClasses.putIfAbsent(entry.name(), entry);
         else if (entry.type() == MLuaDocBuilder.DocEntryType.SERVICE) documentedServices.putIfAbsent(entry.name(), entry);
      }
      MLuaRuntime runtime = MLuaRuntime.get();
      for (String className : runtime.classNames()) {
         LuaTable classTable = runtime.classTable(className);
         if (classTable == null || classTable.rawget("__mlua_service_class").toboolean()) continue;
         MLuaDocBuilder.DocEntry source = documentedClasses.get(className);
         String description = requireDocumentationDescription("class", className,
            classTable.rawget("__mlua_doc_description").optjstring(source == null ? null : source.desc()));
         String parentName = firstParentName(classTable.rawget("__mlua_doc_parents"));
         specs.put(className, new DluaTypeSpec(MLuaDocBuilder.DocEntryType.CLASS, className,
            className, source == null ? null : source.className(), parentName, description));
      }
      for (String serviceName : runtime.services().names()) {
         String className = runtime.serviceClassName(serviceName);
         LuaTable serviceClass = runtime.classTable(className);
         MLuaDocBuilder.DocEntry source = documentedServices.get(serviceName);
         String description = serviceClass == null ? null
            : serviceClass.rawget("__mlua_doc_description").optjstring(null);
         if (description == null || description.isBlank()) {
            var metadata = runtime.services().metadata(serviceName);
            description = metadata == null ? null : metadata.description();
         }
         if ((description == null || description.isBlank()) && source != null) description = source.desc();
         description = requireDocumentationDescription("service", serviceName, description);
         specs.put(className, new DluaTypeSpec(MLuaDocBuilder.DocEntryType.SERVICE,
            serviceName, className, source == null ? null : source.className(), firstParentName(serviceClass == null ? LuaValue.NIL
               : serviceClass.rawget("__mlua_doc_parents")), description));
      }

      // Keep standalone type documentation usable for tooling or optional modules that do not
      // install a runtime class, without allowing it to override a registered Lua type contract.
      for (MLuaDocBuilder.DocEntry entry : builder.entries()) {
         if (entry.type() != MLuaDocBuilder.DocEntryType.SERVICE
             && entry.type() != MLuaDocBuilder.DocEntryType.CLASS) continue;
         String typeName = entry.type() == MLuaDocBuilder.DocEntryType.SERVICE
            ? documentedServiceTypeName(entry.name()) : entry.name();
         if (specs.containsKey(typeName)) continue;
         String description = requireDocumentationDescription(entry.type().name().toLowerCase(), entry.name(), entry.desc());
         specs.put(typeName, new DluaTypeSpec(entry.type(), entry.name(), typeName,
            entry.className(), entry.parentClass(), description));
      }

      return new ArrayList<>(specs.values());
   }

   private static String documentedServiceTypeName(String serviceName) {
      return MLuaRuntime.get().serviceClassName(serviceName);
   }

   private static String requireDocumentationDescription(String kind, String name, String description) {
      if (description == null || description.isBlank()) {
         throw new IllegalStateException("Registered Lua " + kind + " '" + name
            + "' has no description; add one to its runtime registration or module documentation");
      }
      return description;
   }

   private static String firstParentName(LuaValue parentsValue) {
      if (!parentsValue.istable()) return null;
      LuaValue parent = parentsValue.checktable().rawget(1);
      return parent.isstring() ? parent.tojstring() : null;
   }

   private static List<DluaTypeSpec> expandNestedDluaSpecs(List<DluaTypeSpec> specs, Optional<Path> dluaSourceRoot) {
      if (dluaSourceRoot.isEmpty()) {
         return specs;
      }

      Map<String, DluaTypeSpec> expanded = new LinkedHashMap<>();
      for (DluaTypeSpec spec : specs) {
         expanded.putIfAbsent(spec.receiverName, spec);
      }

      for (DluaTypeSpec spec : specs) {
         Optional<String> sourcePath = resolveSourcePath(dluaSourceRoot.get(), spec.sourceClassName);
         if (sourcePath.isEmpty()) {
            continue;
         }
         for (String receiverPath : MLuaApiDocScanner.discoverReceiverPaths(dluaSourceRoot.get(), sourcePath.get(), spec.receiverName)) {
            if (receiverPath.equals(spec.receiverName) || !receiverPath.startsWith(spec.receiverName + ".")) {
               continue;
            }
            expanded.putIfAbsent(receiverPath, new DluaTypeSpec(
               MLuaDocBuilder.DocEntryType.CLASS,
               receiverPath,
               nestedTypeName(receiverPath),
               spec.sourceClassName,
               null,
               receiverPath + " table"
            ));
         }
      }

      return new ArrayList<>(expanded.values());
   }

   private static String nestedTypeName(String receiverPath) {
      return receiverPath.replace(".", "");
   }

   private static void appendTypeDefinition(StringBuilder sb, Optional<Path> dluaSourceRoot, DluaTypeSpec spec,
                                            Map<String, List<String>> mcClasses,
                                            Map<String, DluaTypeSpec> specsByReceiver,
                                            List<MLuaDocBuilder.MethodDoc> methodDocs,
                                            List<MLuaDocBuilder.SignatureDoc> signatures) {
      List<MLuaApiDocScanner.Entry> entries = new ArrayList<>();
      if (dluaSourceRoot.isPresent()) {
         Optional<String> sourcePath = resolveSourcePath(dluaSourceRoot.get(), spec.sourceClassName);
         if (sourcePath.isPresent()) {
            Map<String, MLuaApiDocScanner.Entry> scanned = MLuaApiDocScanner.getEntries(dluaSourceRoot.get(), sourcePath.get(), spec.receiverName);
            entries.addAll(scanned.values());
         }
      }

      entries.sort(java.util.Comparator.comparing(entry -> entry.signature.toLowerCase(Locale.ROOT)));

      sb.append("---@class ").append(spec.typeName);
      if (spec.parentTypeName != null && !spec.parentTypeName.isBlank()) {
         sb.append(" : ").append(spec.parentTypeName);
      }
      sb.append("\n");
      appendNestedFields(sb, spec, specsByReceiver);
      appendRegisteredClassProperties(sb, spec, entries);
      Set<String> documentedMethods = appendDocumentedMethods(sb, spec, methodDocs, signatures);
      for (MLuaApiDocScanner.Entry entry : entries) {
         String fieldName = dluaFieldName(entry, spec.receiverName);
         if (fieldName == null || fieldName.isBlank()) {
            continue;
         }
         if (shouldSkipScannedField(spec.receiverName, fieldName, specsByReceiver)) {
            continue;
         }
         if (isDluaMethod(entry, spec.receiverName)) {
            continue;
         }
         sb.append("---@field ").append(fieldName).append(" ").append(dluaType(entry.type)).append("\n");
      }
      sb.append("local ").append(spec.typeName).append(" = {}\n\n");

      for (MLuaApiDocScanner.Entry entry : entries) {
          String methodName = dluaMethodName(entry, spec.receiverName);
         if (methodName == null || methodName.isBlank()) {
            continue;
         }
         if (documentedMethods.contains(methodName)) {
            continue;
         }
           String[][] paramInfo = dluaParamInfo(entry.signature);
          String returnType = dluaReturnType(entry.type);
         if (returnType != null) {
            sb.append("---@return ").append(returnType).append("\n");
         }
         List<String> sigParams = new ArrayList<>();
         sigParams.add("self");
         for (String[] p : paramInfo) {
            String paramType = mixinParamType(spec.receiverName, methodName, p[0], p[1], mcClasses);
            sb.append("---@param ").append(p[0]).append(" ").append(paramType).append("\n");
            sigParams.add(p[0]);
         }
         sb.append("function ").append(spec.typeName).append(".").append(methodName).append("(").append(String.join(", ", sigParams)).append(") end\n\n");
      }
      appendRegisteredClassMethods(sb, spec, entries, documentedMethods);
   }

   private static void appendRegisteredClassMethods(StringBuilder sb, DluaTypeSpec spec,
                                                     List<MLuaApiDocScanner.Entry> scannedEntries,
                                                     Set<String> documentedMethods) {
      LuaTable classTable = MLuaRuntime.get().classTable(spec.typeName);
      if (classTable == null) return;
      LuaValue methodsValue = classTable.rawget("__mlua_doc_methods");
      if (!methodsValue.istable()) return;

      Set<String> scannedNames = new java.util.HashSet<>();
      for (MLuaApiDocScanner.Entry entry : scannedEntries) {
         String name = dluaMethodName(entry, spec.receiverName);
         if (name != null) scannedNames.add(name);
      }
      scannedNames.addAll(documentedMethods);
      LuaTable methods = methodsValue.checktable();
      List<String> names = new ArrayList<>();
      LuaValue key = LuaValue.NIL;
      while (true) {
         org.luaj.vm2.Varargs entry = methods.next(key);
         key = entry.arg1();
         if (key.isnil()) break;
         if (key.isstring()) names.add(key.tojstring());
      }
      names.sort(String.CASE_INSENSITIVE_ORDER);
      for (String name : names) {
         if (scannedNames.contains(name) || !name.matches("[A-Za-z_][A-Za-z0-9_]*")) continue;
         String description = methods.get(name).optjstring("").replace("\r", "").trim();
         if (!description.isEmpty()) {
            for (String line : description.split("\n")) {
               sb.append("--- ").append(line).append("\n");
            }
         }
         sb.append("function ").append(spec.typeName).append(".").append(name).append("(");
         if (!name.equals("new")) sb.append("self, ");
         sb.append("...) end\n\n");
      }
   }

   private static void appendRegisteredClassProperties(StringBuilder sb, DluaTypeSpec spec,
                                                        List<MLuaApiDocScanner.Entry> scannedEntries) {
      LuaTable classTable = MLuaRuntime.get().classTable(spec.typeName);
      if (classTable == null) return;
      Map<String, LuaValue> properties = new LinkedHashMap<>();
      collectClassProperties(classTable, properties, new java.util.HashSet<>());
      Set<String> scannedFields = new java.util.HashSet<>();
      for (MLuaApiDocScanner.Entry entry : scannedEntries) {
         if (entry.name != null) scannedFields.add(entry.name);
      }
      properties.forEach((name, descriptor) -> {
         if (!scannedFields.contains(name)) {
            sb.append("---@field ").append(name).append(" ")
               .append(descriptor.get("type").optjstring("any")).append("\n");
         }
      });
   }

   private static void collectClassProperties(LuaTable classTable, Map<String, LuaValue> properties,
                                              Set<String> visited) {
      String className = classTable.rawget("__type").optjstring("");
      if (className.isBlank() || !visited.add(className)) return;
      LuaValue values = classTable.rawget("__mlua_properties");
      if (values.istable()) {
         LuaValue key = LuaValue.NIL;
         while (true) {
            Varargs next = values.checktable().next(key);
            key = next.arg1();
            if (key.isnil()) break;
            if (key.isstring() && next.arg(2).istable()) properties.putIfAbsent(key.tojstring(), next.arg(2));
         }
      }
      LuaValue parents = classTable.rawget("__mlua_doc_parents");
      if (!parents.istable()) return;
      for (int index = 1; index <= parents.checktable().length(); index++) {
         LuaValue parentName = parents.checktable().rawget(index);
         if (!parentName.isstring()) continue;
         LuaTable parent = MLuaRuntime.get().classTable(parentName.tojstring());
         if (parent != null) collectClassProperties(parent, properties, visited);
      }
   }

   private static Set<String> appendDocumentedMethods(StringBuilder sb, DluaTypeSpec spec,
                                                       List<MLuaDocBuilder.MethodDoc> methodDocs,
                                                       List<MLuaDocBuilder.SignatureDoc> signatures) {
      Set<String> written = new java.util.HashSet<>();
      for (MLuaDocBuilder.MethodDoc method : methodDocs) {
         if (!method.receiver().equals(spec.receiverName) && !isClassOrParent(spec.typeName, method.receiver())) continue;
         appendDluaSignature(sb, spec, method.signature(), method.returns(), method.description(), written);
      }
      for (MLuaDocBuilder.SignatureDoc signature : signatures) {
         if (!signature.receiver().equals(spec.receiverName) && !isClassOrParent(spec.typeName, signature.receiver())) continue;
         appendDluaSignature(sb, spec, signature.signature(), signature.returns(), null, written);
      }
      return written;
   }

   private static void appendDluaSignature(StringBuilder sb, DluaTypeSpec spec, String signature,
                                           String returns, String description, Set<String> written) {
         int open = signature.indexOf('(');
         int close = signature.lastIndexOf(')');
         if (open < 0 || close < open) return;
         String head = signature.substring(0, open).trim();
         int separator = Math.max(head.lastIndexOf(':'), head.lastIndexOf('.'));
         String name = separator >= 0 ? head.substring(separator + 1).trim() : head;
         if (!name.matches("[A-Za-z_][A-Za-z0-9_]*") || !written.add(name)) return;
         String arguments = signature.substring(open + 1, close).trim();
         List<String> parameters = new ArrayList<>();
         if (!arguments.isEmpty() && !arguments.equals("...")) {
            for (String argument : arguments.split("\\s*,\\s*")) {
               String[] parts = argument.split("\\s*:\\s*", 2);
               String parameterName = parts[0].trim();
               if (!parameterName.matches("[A-Za-z_][A-Za-z0-9_]*")) continue;
               String type = parts.length == 2 ? parts[1].trim() : "any";
               sb.append("---@param ").append(parameterName).append(" ").append(type).append("\n");
               parameters.add(parameterName);
            }
         }
         if (returns != null && !returns.isBlank() && !returns.equalsIgnoreCase("void")) {
            sb.append("---@return ").append(returns).append("\n");
         }
         if (description != null && !description.isBlank()) {
            for (String line : description.split("\\R")) sb.append("--- ").append(line).append("\n");
         }
         List<String> signatureParameters = new ArrayList<>();
         signatureParameters.add("self");
         signatureParameters.addAll(parameters);
         sb.append("function ").append(spec.typeName).append(".").append(name).append("(")
            .append(String.join(", ", signatureParameters)).append(") end\n\n");
   }

   private static boolean isClassOrParent(String className, String expectedName) {
      if (className == null || expectedName == null) return false;
      Set<String> visited = new java.util.HashSet<>();
      List<String> pending = new ArrayList<>();
      pending.add(className);
      for (int index = 0; index < pending.size(); index++) {
         String current = pending.get(index);
         if (!visited.add(current)) continue;
         if (current.equals(expectedName)) return true;
         LuaTable table = MLuaRuntime.get().classTable(current);
         if (table == null) continue;
         LuaValue parents = table.rawget("__mlua_doc_parents");
         if (!parents.istable()) continue;
         for (int parentIndex = 1; parentIndex <= parents.checktable().length(); parentIndex++) {
            LuaValue parent = parents.checktable().rawget(parentIndex);
            if (parent.isstring()) pending.add(parent.tojstring());
         }
      }
      return false;
   }

   private static void appendNestedFields(StringBuilder sb, DluaTypeSpec spec, Map<String, DluaTypeSpec> specsByReceiver) {
      String prefix = spec.receiverName + ".";
      for (DluaTypeSpec candidate : specsByReceiver.values()) {
         if (!candidate.receiverName.startsWith(prefix)) {
            continue;
         }
         String remainder = candidate.receiverName.substring(prefix.length());
         if (remainder.isBlank() || remainder.contains(".")) {
            continue;
         }
         sb.append("---@field ").append(remainder).append(" ").append(candidate.typeName).append("\n");
      }
   }

   private static boolean shouldSkipScannedField(String receiverName, String fieldName, Map<String, DluaTypeSpec> specsByReceiver) {
      return specsByReceiver.containsKey(receiverName + "." + fieldName);
   }


   private static String mixinParamType(String receiverName, String methodName, String paramName, String fallbackType, Map<String, List<String>> mcClasses) {
      if (!"Mixin".equals(receiverName)) {
         return fallbackType;
      }
      return switch (methodName) {
         case "Inject" -> switch (paramName) {
            case "targetClass" -> mcClasses.isEmpty() ? fallbackType : "MLuaMixinTargetClass";
            case "method", "methodName" -> mcClasses.isEmpty() ? fallbackType : "MLuaMixinMethodName";
            case "options" -> "MLuaMixinInjectOptions";
            default -> fallbackType;
         };
         case "Overwrite" -> switch (paramName) {
            case "targetClass" -> mcClasses.isEmpty() ? fallbackType : "MLuaMixinTargetClass";
            case "method", "methodName" -> mcClasses.isEmpty() ? fallbackType : "MLuaMixinMethodName";
            case "options" -> "MLuaMixinOverwriteOptions";
            default -> fallbackType;
         };
         case "Redirect" -> switch (paramName) {
            case "targetClass" -> mcClasses.isEmpty() ? fallbackType : "MLuaMixinTargetClass";
            case "method", "methodName" -> mcClasses.isEmpty() ? fallbackType : "MLuaMixinMethodName";
            case "options" -> "MLuaMixinRedirectOptions";
            default -> fallbackType;
         };
         case "ModifyArg" -> switch (paramName) {
            case "targetClass" -> mcClasses.isEmpty() ? fallbackType : "MLuaMixinTargetClass";
            case "method", "methodName" -> mcClasses.isEmpty() ? fallbackType : "MLuaMixinMethodName";
            case "options" -> "MLuaMixinModifyArgOptions";
            default -> fallbackType;
         };
         default -> fallbackType;
      };
   }


   private static boolean isDluaMethod(MLuaApiDocScanner.Entry entry, String receiverName) {
      return dluaMethodName(entry, receiverName) != null;
   }

   private static String dluaMethodName(MLuaApiDocScanner.Entry entry, String receiverName) {
      String prefix = receiverName + ":";
      if (!entry.signature.startsWith(prefix)) {
         return null;
      }
      int start = prefix.length();
      int end = entry.signature.indexOf('(', start);
      if (end < 0) {
         end = entry.signature.length();
      }
      return entry.signature.substring(start, end);
   }

   private static String dluaFieldName(MLuaApiDocScanner.Entry entry, String receiverName) {
      String prefix = receiverName + ".";
      if (!entry.signature.startsWith(prefix)) {
         return null;
      }
      String name = entry.signature.substring(prefix.length());
      if (name.startsWith("operator:") || name.startsWith("__") || name.contains(":")) {
         return null;
      }
      return name;
   }

   private static String[][] dluaParamInfo(String signature) {
      int paren = signature.indexOf('(');
      if (paren < 0) return new String[0][0];
      int close = signature.lastIndexOf(')');
      if (close <= paren + 1) return new String[0][0];
      String body = signature.substring(paren + 1, close).trim();
      if (body.isEmpty() || body.equals("...")) return new String[0][0];
      String[] declarations = body.split("\\s*,\\s*");
      List<String[]> result = new ArrayList<>();
      for (String declaration : declarations) {
         String[] parts = declaration.split("\\s*:\\s*", 2);
         String name = parts[0].trim();
         if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) continue;
         result.add(new String[] { name, parts.length == 2 ? parts[1].trim() : "any" });
      }
      return result.toArray(new String[0][0]);
   }

   private static String dluaReturnType(String type) {
      String normalized = dluaType(type);
      if ("nil".equals(normalized)) {
         return null;
      }
      return normalized;
   }

   private static String dluaType(String type) {
      if (type == null || type.isBlank()) {
         return "any";
      }
      return switch (type) {
         case "void" -> "nil";
         case "function" -> "any";
         case "Signal" -> "Signal";
         case "Connection" -> "Connection";
         default -> type;
      };
   }

   private static void appendMinecraftGetServiceDefinition(StringBuilder sb, List<DluaTypeSpec> serviceSpecs) {
      serviceSpecs.sort(java.util.Comparator.comparing(spec -> spec.receiverName.toLowerCase(Locale.ROOT)));
      for (DluaTypeSpec spec : serviceSpecs) {
         sb.append("---@overload fun(self: Minecraft, serviceName: \"")
            .append(spec.receiverName)
            .append("\"): ")
            .append(spec.typeName)
            .append("\n");
      }
      sb.append("---@return any\n");
      sb.append("function Minecraft:GetService(serviceName) end\n\n");
      sb.append("---@return string[] serviceNames Available services on the current side.\n");
      sb.append("function Minecraft:GetServiceNames() end\n\n");
   }

   private static void appendEnumDefinitions(StringBuilder sb) {
      sb.append("---@class EnumRoot\n");
      for (EnumInfo ei : discoverEnums()) {
         sb.append("---@field ").append(ei.name).append(" ").append(ei.name).append("Enum\n");
      }
      sb.append("Enum = {}\n\n");

      for (EnumInfo ei : discoverEnums()) {
         sb.append("---@class ").append(ei.name).append("Enum\n");
         for (String[] constant : ei.constants) {
            String key = constant[0].replace("Enum." + ei.name + ".", "");
            sb.append("---@field ").append(key).append(" string\n");
         }
         sb.append("Enum.").append(ei.name).append(" = {\n");
         for (int i = 0; i < ei.constants.length; i++) {
            String key = ei.constants[i][0].replace("Enum." + ei.name + ".", "");
            String val = ei.luaValueOf(key);
            sb.append("    ").append(key).append(" = \"").append(val).append("\"");
            sb.append(i + 1 < ei.constants.length ? ",\n" : "\n");
         }
         sb.append("}\n\n");
      }
   }

   private record DluaTypeSpec(MLuaDocBuilder.DocEntryType kind, String receiverName, String typeName, String sourceClassName, String parentTypeName, String desc) {
   }

    private static void generateExtensionFiles(Path apiDir, List<DluaTypeSpec> specs, Optional<Path> dluaSourceRoot, Map<String, List<String>> mcClasses, List<MLuaDocBuilder.SignatureDoc> signatures) throws IOException {
       generateExtensionJs(apiDir, specs, dluaSourceRoot, mcClasses, signatures);
       generateSettingsJson(apiDir);
    }

    private static void generateExtensionJs(Path apiDir, List<DluaTypeSpec> specs, Optional<Path> dluaSourceRoot, Map<String, List<String>> mcClasses, List<MLuaDocBuilder.SignatureDoc> signatures) throws IOException {
       StringBuilder sb = new StringBuilder();
       sb.append("// MLua VS Code Extension — auto-generated data section\n");
       sb.append("// Changes to service methods, enums, or globals should be made\n");
       sb.append("// in the owning module documentation registrations\n");
       sb.append("// and regenerated via syncVscodeExtensionApi.\n\n");

       List<String> serviceNames = new ArrayList<>();
       java.util.LinkedHashMap<String, List<String>> serviceMethods = new java.util.LinkedHashMap<>();

       for (DluaTypeSpec spec : specs) {
          if (spec.kind != MLuaDocBuilder.DocEntryType.SERVICE) continue;
          String receiverName = spec.receiverName;
          serviceNames.add(receiverName);

          List<MLuaApiDocScanner.Entry> entries = new ArrayList<>();
          if (dluaSourceRoot.isPresent()) {
             Optional<String> sourcePath = resolveSourcePath(dluaSourceRoot.get(), spec.sourceClassName);
             if (sourcePath.isPresent()) {
                java.util.Map<String, MLuaApiDocScanner.Entry> scanned = MLuaApiDocScanner.getEntries(dluaSourceRoot.get(), sourcePath.get(), receiverName);
                entries.addAll(scanned.values());
             }
          }
          entries.sort(java.util.Comparator.comparing(e -> e.signature.toLowerCase(java.util.Locale.ROOT)));

          Map<String, List<String>> documentedParameters = new LinkedHashMap<>();
          for (MLuaDocBuilder.SignatureDoc signature : signatures) {
             if (!signature.receiver().equals(receiverName)) continue;
             String signaturePrefix = receiverName + ":";
             if (!signature.signature().startsWith(signaturePrefix)) continue;
             int methodStart = signaturePrefix.length();
             int methodEnd = signature.signature().indexOf('(', methodStart);
             if (methodEnd < 0) continue;
             String methodName = signature.signature().substring(methodStart, methodEnd);
             List<String> parameters = new ArrayList<>();
             for (String[] parameter : dluaParamInfo(signature.signature())) parameters.add(parameter[0]);
             documentedParameters.put(methodName, parameters);
          }
          List<String> methods = new ArrayList<>();
          for (MLuaApiDocScanner.Entry entry : entries) {
             String methodName = dluaMethodName(entry, receiverName);
             if (methodName == null) continue;
             List<String> sigParams = documentedParameters.get(methodName);
             if (sigParams == null) {
                sigParams = new ArrayList<>();
                for (String[] parameter : dluaParamInfo(entry.signature)) sigParams.add(parameter[0]);
             }
             methods.add(sigParams.isEmpty() ? methodName + "()" : methodName + "(" + String.join(", ", sigParams) + ")");
          }
          if (!methods.isEmpty()) {
             serviceMethods.put(receiverName, methods);
          }
       }

       List<EnumInfo> enumInfos = discoverEnums();
       List<String> enumNames = new ArrayList<>();
       for (EnumInfo ei : enumInfos) {
          enumNames.add(ei.name);
       }

       // GLOBAL_NAMES
       sb.append("const GLOBAL_NAMES = [\n");
       sb.append("  \"Minecraft\", \"Enum\", \"Require\", \"Signal\", \"task\", \"wait\",\n");
       sb.append("  \"typeof\", \"Vector2\", \"Vector3\", \"CFrame\", \"Color3\",\n");
       sb.append("  \"UDim\", \"UDim2\", \"Random\", \"DateTime\"\n");
       sb.append("];\n\n");

       // MINECRAFT_METHODS
       sb.append("const MINECRAFT_METHODS = [\n");
       sb.append("  \"GetService(serviceName)\", \"GetServiceNames()\", \"IsServer()\", \"IsClient()\", \"GetSide()\"\n");
       sb.append("];\n\n");

       // SERVICE_NAMES
       sb.append("const SERVICE_NAMES = [\n");
       for (int i = 0; i < serviceNames.size(); i++) {
          sb.append("  \"").append(serviceNames.get(i)).append("\"");
          sb.append(i + 1 < serviceNames.size() ? ",\n" : "\n");
       }
       sb.append("];\n\n");

       // SERVICE_METHODS
       sb.append("const SERVICE_METHODS = {\n");
       Iterator<java.util.Map.Entry<String, List<String>>> sit = serviceMethods.entrySet().iterator();
       while (sit.hasNext()) {
          java.util.Map.Entry<String, List<String>> entry = sit.next();
          sb.append("  ").append(entry.getKey()).append(": [");
          List<String> methods = entry.getValue();
          for (int i = 0; i < methods.size(); i++) {
             sb.append("\"").append(methods.get(i)).append("\"");
             if (i + 1 < methods.size()) sb.append(", ");
          }
          sb.append("]");
          if (sit.hasNext()) sb.append(",");
          sb.append("\n");
       }
       sb.append("};\n\n");

       // ENUM_CATEGORIES
       sb.append("const ENUM_CATEGORIES = [\n");
       for (int i = 0; i < enumNames.size(); i++) {
          sb.append("  \"").append(enumNames.get(i)).append("\"");
          sb.append(i + 1 < enumNames.size() ? ",\n" : "\n");
       }
       sb.append("];\n\n");

        // MIXIN_CLASSES — scanned from Minecraft jars on classpath
        sb.append("const MIXIN_CLASSES = [\n");
        List<String> mcClassNames = new ArrayList<>(mcClasses.keySet());
        mcClassNames.sort(null);
        for (int i = 0; i < mcClassNames.size(); i++) {
           sb.append("  \"").append(mcClassNames.get(i)).append("\"");
           sb.append(i + 1 < mcClassNames.size() ? ",\n" : "\n");
        }
        sb.append("];\n\n");

        // MIXIN_METHODS — per-class methods for completions
        sb.append("const MIXIN_METHODS = {\n");
        Iterator<java.util.Map.Entry<String, List<String>>> mit = mcClasses.entrySet().iterator();
        while (mit.hasNext()) {
           java.util.Map.Entry<String, List<String>> entry = mit.next();
           sb.append("  \"").append(entry.getKey()).append("\": [");
           List<String> methods = entry.getValue();
           methods.sort(null);
           for (int i = 0; i < methods.size(); i++) {
              sb.append("\"").append(methods.get(i)).append("()\"");
              if (i + 1 < methods.size()) sb.append(", ");
           }
           sb.append("]");
           if (mit.hasNext()) sb.append(",");
           sb.append("\n");
        }
        sb.append("};\n\n");

        // Runtime code — embedded template
        sb.append(EXTENSION_JS_RUNTIME);

       write(apiDir.resolve("extension.js"), sb.toString());
    }

    private static void generateSettingsJson(Path apiDir) throws IOException {
       StringBuilder sb = new StringBuilder();
       sb.append("{\n");
       sb.append("  \"Lua.workspace.checkThirdParty\": false,\n");
       sb.append("  \"Lua.workspace.library\": [\n");
       sb.append("    \"${workspaceFolder}/.vscode/mlua\"\n");
       sb.append("  ],\n");
       sb.append("  \"Lua.diagnostics.globals\": [\n");
       sb.append("    \"Minecraft\", \"Enum\", \"Require\", \"Signal\", \"task\", \"wait\",\n");
       sb.append("    \"typeof\", \"Vector2\", \"Vector3\", \"CFrame\", \"Color3\",\n");
       sb.append("    \"UDim\", \"UDim2\", \"Random\", \"DateTime\"\n");
       sb.append("  ]\n");
       sb.append("}\n");
       write(apiDir.resolve("settings.json"), sb.toString());
    }

    private static Map<String, List<String>> scanMinecraftClasses() {
       Map<String, List<String>> result = new LinkedHashMap<>();
       String cp = System.getProperty("mlua.minecraft.classpath");
       if (cp == null || cp.isEmpty()) cp = System.getProperty("java.class.path");
       if (cp == null || cp.isEmpty()) {
          System.err.println("[MLuaDoc] No classpath found for mixin scan, skipping");
          return result;
       }

       String[] parts = cp.split(File.pathSeparator);
       System.err.println("[MLuaDoc] Scanning " + parts.length + " classpath entries for Minecraft classes");
       for (String entry : parts) {
          File f = new File(entry);
          if (!f.exists()) { System.err.println("[MLuaDoc]   skipping missing: " + entry); continue; }
          if (!f.getName().endsWith(".jar")) { System.err.println("[MLuaDoc]   skipping not-jar: " + entry); continue; }

          int classesFromJar = 0;
          try (ZipFile zip = new ZipFile(f)) {
             Enumeration<? extends ZipEntry> entries = zip.entries();
             while (entries.hasMoreElements()) {
                ZipEntry ze = entries.nextElement();
                String name = ze.getName();
                if (!name.startsWith("net/minecraft/") || !name.endsWith(".class")) continue;
                if (name.contains("$")) continue; // skip inner classes

                String className = name.substring(0, name.length() - 6).replace('/', '.');
                if (result.containsKey(className)) continue;

                List<String> methodNames = new ArrayList<>();
                try (InputStream is = zip.getInputStream(ze)) {
                   JavaClass jc = new ClassParser(is, name).parse();
                   for (Method m : jc.getMethods()) {
                      if (m.isPrivate() || m.isStatic() || m.getName().startsWith("<")) continue;
                      String mn = m.getName();
                      if (mn.startsWith("<") || mn.equals("m_") || mn.length() > 60) continue;
                      if (!methodNames.contains(mn)) methodNames.add(mn);
                   }
                } catch (Exception ignored) {
                }

                result.put(className, methodNames);
                classesFromJar++;
             }
          } catch (Exception e) {
             System.err.println("[MLuaDoc]   failed to scan jar: " + f.getName() + " (" + e.getMessage() + ")");
             continue;
          }

          if (classesFromJar > 0) {
             System.err.println("[MLuaDoc]   found " + classesFromJar + " mc classes in: " + f.getName());
          } else {
             System.err.println("[MLuaDoc]   no mc classes: " + f.getName());
          }
       }
       System.err.println("[MLuaDoc] Scanned " + result.size() + " Minecraft classes");
       return result;
    }

    private static final String EXTENSION_JS_RUNTIME = """
function activate(context) {
    const disposable = vscode.commands.registerCommand('mlua.openDocumentation', function () {
        const panel = vscode.window.createWebviewPanel('mluaDocs', 'MLua Documentation', vscode.ViewColumn.One, { enableScripts: true });
        const docPath = vscode.Uri.joinPath(context.extensionUri, 'resources', 'docs', 'index.html');
        panel.webview.html = getWebviewContent(docPath, context.extensionUri);
        function getWebviewContent(fileUri, extensionUri) {
            const filePath = fileUri.fsPath;
            const fs = require('fs');
            let html = fs.readFileSync(filePath, 'utf8');
            const resourceDir = vscode.Uri.joinPath(extensionUri, 'resources', 'docs');
            const onDiskPath = vscode.Uri.file(resourceDir.fsPath);
            const resourceUri = panel.webview.asWebviewUri(onDiskPath);
            html = html.replace(/src="([^"]+)"/g, (match, p1) => {
                const fullPath = vscode.Uri.joinPath(resourceDir, p1);
                return `src="${panel.webview.asWebviewUri(fullPath)}"`;
            });
            html = html.replace(/href="([^"]+)"/g, (match, p1) => {
                if (p1.startsWith('http')) return match;
                const fullPath = vscode.Uri.joinPath(resourceDir, p1);
                return `href="${panel.webview.asWebviewUri(fullPath)}"`;
            });
            return html;
        }
    });
    context.subscriptions.push(disposable);

    const mluaMode = { language: 'lua', scheme: 'file' };
    function suggestClassSegments(classPrefix) {
        const seen = new Set(), segments = [];
        for (const cls of MIXIN_CLASSES) {
            if (!cls.toLowerCase().startsWith(classPrefix.toLowerCase())) continue;
            if (cls.length === classPrefix.length) continue;
            const rest = cls.substring(classPrefix.length);
            const dot = rest.indexOf('.');
            const seg = dot >= 0 ? cls.substring(0, classPrefix.length + dot + 1) : cls;
            if (seen.has(seg)) continue;
            seen.add(seg);
            segments.push(seg);
        }
        segments.sort();
        return segments;
    }

    const MIXIN_OPTION_KEYS = {
        Inject: ['at', 'descriptor', 'callback'],
        Overwrite: ['descriptor', 'callback'],
        Redirect: ['target', 'callback'],
        ModifyArg: ['target', 'index', 'callback']
    };

    const MIXIN_AT_VALUES = ['HEAD', 'RETURN'];

    function extractStringValue(argText) {
        const match = argText.trim().match(/^["']([^"']*)["']$/);
        return match ? match[1] : '';
    }

    function analyzeMixinCall(textAfterOpen) {
        const args = [];
        let current = '';
        let inSingle = false;
        let inDouble = false;
        let escaped = false;
        let parenDepth = 0;
        let braceDepth = 0;
        let bracketDepth = 0;

        for (let i = 0; i < textAfterOpen.length; i++) {
            const ch = textAfterOpen[i];
            current += ch;

            if (escaped) {
                escaped = false;
                continue;
            }
            if ((inSingle || inDouble) && ch === '\\\\') {
                escaped = true;
                continue;
            }
            if (ch === "'" && !inDouble) {
                inSingle = !inSingle;
                continue;
            }
            if (ch === '"' && !inSingle) {
                inDouble = !inDouble;
                continue;
            }
            if (inSingle || inDouble) continue;

            if (ch === '(') {
                parenDepth++;
                continue;
            }
            if (ch === ')') {
                if (parenDepth == 0 && braceDepth == 0 && bracketDepth == 0) {
                    return null;
                }
                parenDepth--;
                continue;
            }
            if (ch === '{') {
                braceDepth++;
                continue;
            }
            if (ch === '}') {
                braceDepth--;
                continue;
            }
            if (ch === '[') {
                bracketDepth++;
                continue;
            }
            if (ch === ']') {
                bracketDepth--;
                continue;
            }
            if (ch === ',' && parenDepth == 0 && braceDepth == 0 && bracketDepth == 0) {
                current = current.substring(0, current.length - 1);
                args.push(current);
                current = '';
            }
        }

        args.push(current);
        return {
            args,
            currentArgIndex: Math.max(0, args.length - 1),
            currentArgText: current,
            inSingle,
            inDouble,
            parenDepth,
            braceDepth,
            bracketDepth
        };
    }

    function getActiveMixinContext(document, position) {
        const offset = document.offsetAt(position);
        const textBefore = document.getText().substring(0, offset);
        const re = /:(Inject|Overwrite|Redirect|ModifyArg)\\s*\\(/g;
        const matches = [];
        let match;
        while ((match = re.exec(textBefore)) !== null) {
            matches.push({
                type: match[1],
                openIndex: match.index + match[0].length
            });
        }

        for (let i = matches.length - 1; i >= 0; i--) {
            const candidate = matches[i];
            const analysis = analyzeMixinCall(textBefore.substring(candidate.openIndex));
            if (!analysis) continue;
            return {
                type: candidate.type,
                className: extractStringValue(analysis.args[0] || ''),
                methodName: extractStringValue(analysis.args[1] || ''),
                argIndex: analysis.currentArgIndex,
                argText: analysis.currentArgText
            };
        }
        return null;
    }

    const provider1 = vscode.languages.registerCompletionItemProvider(mluaMode, {
        provideCompletionItems(document, position) {
            const lineText = document.lineAt(position).text;
            const linePrefix = lineText.substring(0, position.character);
            const items = [];

            // --- Mixin completions ---

            // 1. Method name: match : followed by partial word (suggest Overwrite/Inject/Redirect/ModifyArg)
            const mixinPartialMatch = linePrefix.match(/:([a-zA-Z]*)$/);
            if (mixinPartialMatch) {
                const partial = mixinPartialMatch[1] || '';
                const methods = ['Overwrite', 'Inject', 'Redirect', 'ModifyArg'].filter(m =>
                    m.toLowerCase().startsWith(partial.toLowerCase())
                );
                for (const m of methods) {
                    const item = new vscode.CompletionItem(m, vscode.CompletionItemKind.Method);
                    item.insertText = m + '("';
                    items.push(item);
                }
                if (methods.length > 0) return items;
            }

            // 2. Argument completions inside a mixin call (e.g. Mixin:Overwrite(...))
            const mixinContext = getActiveMixinContext(document, position);
            if (mixinContext) {
                if (mixinContext.argIndex === 0) {
                    const quoteMatch = mixinContext.argText.match(/["']([^"']*)$/);
                    const classPrefix = quoteMatch ? quoteMatch[1] : '';
                    for (const s of suggestClassSegments(classPrefix)) {
                        items.push(new vscode.CompletionItem(s, vscode.CompletionItemKind.Class));
                    }
                    return items;
                }

                if (mixinContext.argIndex === 1) {
                    const partial = mixinContext.argText.match(/["']([^"']*)$/);
                    const methodPrefix = partial ? partial[1] : '';
                    if (MIXIN_METHODS && MIXIN_METHODS[mixinContext.className]) {
                        for (const m of MIXIN_METHODS[mixinContext.className]) {
                            const mn = m.endsWith('()') ? m.substring(0, m.length - 2) : m;
                            if (!mn.toLowerCase().startsWith(methodPrefix.toLowerCase())) continue;
                            const item = new vscode.CompletionItem(mn, vscode.CompletionItemKind.Method);
                            if (methodPrefix) {
                                item.range = new vscode.Range(
                                    position.with(undefined, position.character - methodPrefix.length),
                                    position
                                );
                            }
                            items.push(item);
                        }
                    }
                    return items;
                }

                if (mixinContext.argIndex === 2) {
                    const optionKeys = MIXIN_OPTION_KEYS[mixinContext.type] || [];
                    const atValueMatch = mixinContext.argText.match(/\\bat\\s*=\\s*["']([^"']*)$/);
                    if (atValueMatch) {
                        const atPrefix = atValueMatch[1];
                        for (const value of MIXIN_AT_VALUES) {
                            if (!value.toLowerCase().startsWith(atPrefix.toLowerCase())) continue;
                            const item = new vscode.CompletionItem(value, vscode.CompletionItemKind.EnumMember);
                            if (atPrefix) {
                                item.range = new vscode.Range(
                                    position.with(undefined, position.character - atPrefix.length),
                                    position
                                );
                            }
                            items.push(item);
                        }
                        return items;
                    }

                    const keyMatch = mixinContext.argText.match(/[,{]\\s*([A-Za-z_]*)$/);
                    if (keyMatch) {
                        const keyPrefix = keyMatch[1] || '';
                        for (const key of optionKeys) {
                            if (!key.toLowerCase().startsWith(keyPrefix.toLowerCase())) continue;
                            const item = new vscode.CompletionItem(key, vscode.CompletionItemKind.Property);
                            item.insertText = key + ' = ';
                            if (keyPrefix) {
                                item.range = new vscode.Range(
                                    position.with(undefined, position.character - keyPrefix.length),
                                    position
                                );
                            }
                            items.push(item);
                        }
                        return items;
                    }
                }

                return items;
            }

            // --- Non-mixin completions ---
            if (linePrefix.endsWith('Minecraft.')) {
                for (const s of SERVICE_NAMES) {
                    const item = new vscode.CompletionItem(s, vscode.CompletionItemKind.Method);
                    items.push(item);
                }
                for (const m of MINECRAFT_METHODS) {
                    items.push(new vscode.CompletionItem(m, vscode.CompletionItemKind.Method));
                }
            } else if (linePrefix.endsWith('Minecraft.GetService(enumName)')) {
                const item = new vscode.CompletionItem('Minecraft.GetService("' + enumName + '")', vscode.CompletionItemKind.Snippet);
                items.push(item);
            } else {
                for (const service of SERVICE_NAMES) {
                    const idx = linePrefix.lastIndexOf(service + '.');
                    if (idx >= 0 && !linePrefix.substring(idx + service.length + 1).includes('(')) {
                        const methods = SERVICE_METHODS[service] || [];
                        for (const m of methods) {
                            const idx2 = m.indexOf('(');
                            if (m.match(/^\\w+/)) continue;
                            items.push(new vscode.CompletionItem(m, vscode.CompletionItemKind.Method));
                        }
                    }
                }
                if (linePrefix.endsWith('Enum.')) {
                    for (const c of ENUM_CATEGORIES) {
                        items.push(new vscode.CompletionItem(c, vscode.CompletionItemKind.Enum));
                    }
                }
                for (const g of GLOBAL_NAMES) {
                    if (linePrefix.endsWith(g + '.')) continue;
                    if (linePrefix.length === 0 || /[^\\w.]$/.test(linePrefix)) {
                        items.push(new vscode.CompletionItem(g, vscode.CompletionItemKind.Variable));
                    }
                }
            }
            return items;
        }
    }, '.', '"', ':', '(');
    context.subscriptions.push(provider1);

    let mluaDiagnostics = vscode.languages.createDiagnosticCollection('mlua');
    const diagProvider = vscode.languages.registerCompletionItemProvider(mluaMode, {
        provideCompletionItems(document, position) {
            const items = [];
            const text = document.getText();
            const re = /Minecraft\\.GetService\\(\\s*[\"']?(\\w*)[\"']?\\s*\\)/g;
            let match;
            while ((match = re.exec(text)) !== null) {
                const svc = match[1];
                if (svc && !SERVICE_NAMES.includes(svc)) {
                    const range = new vscode.Range(document.positionAt(match.index), document.positionAt(match.index + match[0].length));
                    const diag = new vscode.Diagnostic(range, `Unknown service "${svc}". Did you mean one of: ${SERVICE_NAMES.join(', ')}?`, vscode.DiagnosticSeverity.Warning);
                    mluaDiagnostics.set(document.uri, [diag]);
                } else {
                }
            }
            const re2 = /(\\w+)\\.(\\w+)\\s*\\(/g;
            while ((match = re2.exec(text)) !== null) {
                const svc = match[1];
                if (SERVICE_NAMES.includes(svc)) {
                    const methodName = match[2];
                    const methods = SERVICE_METHODS[svc] || [];
                    const found = methods.some(m => m.startsWith(methodName + '(') || m === methodName);
                    if (!found) {
                        const range = new vscode.Range(document.positionAt(match.index), document.positionAt(match.index + match[0].length));
                        const diag = new vscode.Diagnostic(range, `Unknown method "${svc}.${methodName}".`, vscode.DiagnosticSeverity.Warning);
                        mluaDiagnostics.set(document.uri, [diag]);
                    }
                }
            }
            return items;
        }
    });
    context.subscriptions.push(diagProvider);

    const sidebarProvider = new MLSidebarProvider(context.extensionUri);
    context.subscriptions.push(vscode.window.registerWebviewViewProvider('mlua-sidebar', sidebarProvider));

    const syncCommand = vscode.commands.registerCommand('mlua.syncRuntime', function () {
        const wsFolders = vscode.workspace.workspaceFolders;
        if (!wsFolders) {
            vscode.window.showErrorMessage('No workspace folder open');
            return;
        }
        const rootPath = wsFolders[0].uri.fsPath;
        const fs = require('fs');
        const path = require('path');
        const srcDir = path.join(rootPath, 'mlua-runtime');
        const destDir = path.join(rootPath, 'run', 'mlua');
        const mcmetaPath = path.join(rootPath, 'run', 'mlua', 'mc.lua');
        function copyRecursive(src, dest) {
            if (!fs.existsSync(src)) {
                vscode.window.showErrorMessage('Source directory not found: ' + src);
                return;
            }
            if (!fs.existsSync(dest)) fs.mkdirSync(dest, { recursive: true });
            const entries = fs.readdirSync(src, { withFileTypes: true });
            for (const entry of entries) {
                const srcPath = path.join(src, entry.name);
                const destPath = path.join(dest, entry.name);
                if (entry.isDirectory()) {
                    copyRecursive(srcPath, destPath);
                } else {
                    if (entry.name.endsWith('.lua')) {
                        fs.copyFileSync(srcPath, destPath);
                    }
                }
            }
        }
        try {
            copyRecursive(srcDir, destDir);
            vscode.window.showInformationMessage('MLua runtime synced successfully to ' + destDir);
        } catch (e) {
            vscode.window.showErrorMessage('Sync failed: ' + e.message);
        }
    });
    context.subscriptions.push(syncCommand);
}

class MLSidebarProvider {
    constructor(extensionUri) {
        this._extensionUri = extensionUri;
    }

    resolveWebviewView(webviewView) {
        webviewView.webview.options = { enableScripts: true };
        webviewView.webview.html = this._getHtmlForWebview(webviewView.webview);
    }

    _getHtmlForWebview(webview) {
        return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<style>
    body { padding:10px; font-family:system-ui,sans-serif; color:#ccc; background:#1e1e1e; }
    h1 { font-size:14px; border-bottom:1px solid #333; padding-bottom:8px; }
    .section { margin-top:16px; }
    .section h2 { font-size:12px; text-transform:uppercase; color:#888; }
    .section a { display:block; padding:4px 0; color:#4fc1ff; text-decoration:none; font-size:13px; }
    .section a:hover { color:#80d4ff; }
</style>
</head>
<body>
<h1>MLua</h1>
<div class="section">
    <h2>Documentation</h2>
    <a href="#" onclick="openDocs()">Open MLua Docs</a>
    <a href="#" onclick="syncRuntime()">Sync Runtime</a>
</div>
<script>
    const vscode = acquireVsCodeApi();
    function openDocs() { vscode.postMessage({command:'openDocs'}); }
    function syncRuntime() { vscode.postMessage({command:'syncRuntime'}); }
</script>
</body>
</html>`;
    }
}

module.exports = { activate, deactivate };
function deactivate() {}
""";

    private static void write(Path path, String text) throws IOException {
       Files.createDirectories(path.getParent());
       Files.writeString(path, text, StandardCharsets.UTF_8);
    }

    private static String html(String title, String body) {
      return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>" + title + "</title><style>" + CSS + "</style></head><body>" + body + "<script>\n" +
              "function highlightCode(){document.querySelectorAll('pre code').forEach(function(e){e.innerHTML=e.textContent.replace(/[&<>]/g,function(c){return{'&':'&amp;','<':'&lt;','>':'&gt;'}[c]}).replace(/--\\[\\[[\\s\\S]*?\\]\\]|--[^\\n]*|\\/\\*[\\s\\S]*?\\*\\/|\\/\\/[^\\n]*|\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*'|\\b(?:and|break|do|else|elseif|end|false|for|function|if|in|local|nil|not|or|repeat|return|then|true|until|while|goto|continue)\\b|\\b(?:abstract|assert|boolean|byte|case|catch|char|class|const|default|do|double|else|enum|extends|false|final|finally|float|for|goto|if|implements|import|instanceof|int|interface|long|native|new|null|package|private|protected|public|return|short|static|strictfp|super|switch|synchronized|this|throw|throws|transient|true|try|void|volatile|while|var|yield|record|sealed|permits)\\b|\\b(\\d+(?:\\.\\d+)?)\\b|\\b(Minecraft|Enum|Require|Vector3|Vector2|CFrame|Color3|UDim|print|warn|pairs|ipairs|pcall|xpcall|tostring|tonumber|type|typeof|wait|error|assert|Instance)\\b|@(\\w+)\\b|:(\\w+)\\b/g,function(m,c1,c2,c3,c4){if(c1)return'<span style=\"color:#a78bfa\">'+c1+'</span>';if(c2)return'<span style=\"color:#facc15\">'+c2+'</span>';if(c3)return'<span style=\"color:#facc15\">@'+c3+'</span>';if(c4)return':<span style=\"color:#facc15\">'+c4+'</span>';if(m[0]=='-'||m[0]=='/')return'<span style=\"color:#4ade80\">'+m+'</span>';if(m[0]=='\"'||m[0]==\"'\")return'<span style=\"color:#fb923c\">'+m+'</span>';return'<span style=\"color:#60a5fa\">'+m+'</span>'})})}\n" +
             "highlightCode();\n" +
             "function toggleFolder(row) {\n" +
             "  const item = row.parentElement;\n" +
             "  const children = item.querySelector('.sidebar-children');\n" +
             "  const arrow = item.querySelector('.sidebar-arrow');\n" +
             "  if (children) {\n" +
             "    const isOpening = !children.classList.contains('open');\n" +
             "    children.classList.toggle('open', isOpening);\n" +
             "    if (arrow) arrow.classList.toggle('open', isOpening);\n" +
             "  }\n" +
             "}\n" +
             "</script></body></html>";
   }

   private static String page(String title, String sidebar, String content) {
      return html(title, "<div class=\"app\">" + sidebar + "<div class=\"main\"><div class=\"topbar\"><div class=\"topbar-breadcrumb\"><a href=\"#\">MLua</a><span class=\"sep\">/</span><span class=\"current\">" + title + "</span></div></div><div class=\"content-scroll\">" + content + "</div></div></div>");
   }

   private static String sLink(String href, String label, boolean active) {
      return "<div class=\"sidebar-item\"><a class=\"sidebar-row" + (active ? " active" : "") + "\" href=\""
         + htmlEscape(href) + "\">" + htmlEscape(label) + "</a></div>";
   }

   private static String sFolder(String label, String content, boolean expanded) {
       return "<div class=\"sidebar-item\">\n" +
              "  <div class=\"sidebar-row\" onclick=\"toggleFolder(this)\">\n" +
              "    <span>" + htmlEscape(label) + "</span>\n" +
              "    <div class=\"sidebar-arrow" + (expanded ? " open" : "") + "\"></div>\n" +
              "  </div>\n" +
             "  <div class=\"sidebar-children" + (expanded ? " open" : "") + "\">\n" +
             "    " + content + "\n" +
             "  </div>\n" +
             "</div>";
   }

   private static List<MLuaDocBuilder.DocEntry> moduleEntries(MLuaDocBuilder.DocEntryType type) {
      MLuaDocBuilder builder = new MLuaDocBuilder();
      MLuaModuleLoader.get().generateDocs(builder);
      List<MLuaDocBuilder.DocEntry> result = new ArrayList();
      for (MLuaDocBuilder.DocEntry entry : runtimeDocEntries(builder)) {
         if (entry.type() == type) result.add(entry);
      }
      return result;
   }

   private static List<MLuaDocBuilder.DocEntry> runtimeDocEntries(MLuaDocBuilder builder) {
      List<MLuaDocBuilder.DocEntry> entries = new ArrayList<>(builder.entries());
      Set<String> existing = new java.util.HashSet<>();
      for (MLuaDocBuilder.DocEntry entry : entries) {
         if (entry.type() == MLuaDocBuilder.DocEntryType.SERVICE || entry.type() == MLuaDocBuilder.DocEntryType.CLASS
             || entry.type() == MLuaDocBuilder.DocEntryType.DATA_TYPE || entry.type() == MLuaDocBuilder.DocEntryType.LIBRARY) {
            requireDocumentationDescription(entry.type().name().toLowerCase(), entry.name(), entry.desc());
         }
         existing.add(entry.type().name() + ":" + entry.name());
      }

      MLuaRuntime runtime = MLuaRuntime.get();
      for (String className : runtime.classNames()) {
         LuaTable classTable = runtime.classTable(className);
         if (classTable == null) continue;
         if (classTable.rawget("__mlua_service_class").toboolean()) {
            String serviceName = classTable.rawget("__mlua_service_name").optjstring("");
            if (serviceName.isBlank() || !existing.add(MLuaDocBuilder.DocEntryType.SERVICE.name() + ":" + serviceName)) continue;
            String description = requireDocumentationDescription("service", serviceName,
               classTable.rawget("__mlua_doc_description").optjstring(null));
            entries.add(new MLuaDocBuilder.DocEntry(MLuaDocBuilder.DocEntryType.SERVICE, serviceName,
               serviceName, description, "services/" + kebabCase(serviceName) + ".html"));
            continue;
         }
         if (!existing.add(MLuaDocBuilder.DocEntryType.CLASS.name() + ":" + className)) continue;
         String description = requireDocumentationDescription("class", className,
            classTable.rawget("__mlua_doc_description").optjstring(null));
         String classPath = isSubclassOf(runtime, className, "GuiBase")
            ? "classes.ui/" + kebabCase(className) + ".html"
            : "classes/" + kebabCase(className) + ".html";
         entries.add(new MLuaDocBuilder.DocEntry(MLuaDocBuilder.DocEntryType.CLASS, className,
            className, description, classPath,
            firstParentName(classTable.rawget("__mlua_doc_parents"))));
      }
      for (String serviceName : runtime.services().names()) {
         if (!existing.add(MLuaDocBuilder.DocEntryType.SERVICE.name() + ":" + serviceName)) continue;
         String className = runtime.serviceClassName(serviceName);
         LuaTable serviceClass = runtime.classTable(className);
         var metadata = runtime.services().metadata(serviceName);
         String description = serviceClass == null ? null
            : serviceClass.rawget("__mlua_doc_description").optjstring(null);
         if (description == null || description.isBlank()) {
            description = metadata == null ? null : metadata.description();
         }
         description = requireDocumentationDescription("service", serviceName, description);
         entries.add(new MLuaDocBuilder.DocEntry(MLuaDocBuilder.DocEntryType.SERVICE, serviceName,
            serviceName, description, "services/" + kebabCase(serviceName) + ".html"));
      }
      return entries;
   }

   private static boolean isSubclassOf(MLuaRuntime runtime, String className, String parentName) {
      LuaTable probe = new LuaTable();
      probe.rawset("__type", LuaValue.valueOf(className));
      return runtime.isInstanceOf(probe, parentName);
   }

   /** Render a nested sidebar folder from module entries sharing a path prefix */
   private static String renderModuleTree(List<MLuaDocBuilder.DocEntry> entries, String prefix, String p, String current) {
      StringBuilder direct = new StringBuilder();
      Map<String, List<MLuaDocBuilder.DocEntry>> subfolders = new LinkedHashMap();

      for (MLuaDocBuilder.DocEntry entry : entries) {
         String fp = entry.filePath();
         String withoutExt = fp.replace(".html", "");
         int slash = fp.lastIndexOf('/');
         if (slash < 0) { direct.append(sLink(p + fp, entry.name(), current.equals(withoutExt))); continue; }

         String dir = fp.substring(0, slash);
         if (dir.equals(prefix)) {
            direct.append(sLink(p + fp, entry.name(), current.equals(withoutExt)));
         } else if (dir.startsWith(prefix + ".")) {
            String rest = dir.substring(prefix.length() + 1);
            String top = rest.contains(".") ? rest.substring(0, rest.indexOf('.')) : rest;
            subfolders.computeIfAbsent(top, k -> new ArrayList<>()).add(entry);
         }
      }

      StringBuilder tree = new StringBuilder();
      tree.append(direct);
      for (Map.Entry<String, List<MLuaDocBuilder.DocEntry>> sf : subfolders.entrySet()) {
         String displayName = sf.getKey().substring(0, 1).toUpperCase() + sf.getKey().substring(1);
         String subtreePrefix = prefix + "." + sf.getKey();
         boolean expanded = current.equals(subtreePrefix) || current.startsWith(subtreePrefix + "/") || current.startsWith(subtreePrefix + ".");
         tree.append(sFolder(displayName, renderModuleTree(sf.getValue(), subtreePrefix, p, current), expanded));
      }
      return tree.toString();
   }

   private static String kebabCase(String value) {
      return value.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
   }

   private static String normalizeDocAlias(String value) {
      return value.toLowerCase(Locale.ROOT).trim();
   }

   private static void putDocAlias(Map<String, String> hrefs, String alias, String href) {
      if (alias == null || alias.isBlank() || href == null || href.isBlank()) {
         return;
      }
      hrefs.putIfAbsent(normalizeDocAlias(alias), href);
   }

   private static Map<String, String> docHrefIndex() {
      Map<String, String> hrefs = new LinkedHashMap<>();

      for (MLuaDocBuilder.DocEntry entry : moduleEntries(MLuaDocBuilder.DocEntryType.DATA_TYPE)) {
         putDocAlias(hrefs, entry.name(), "../" + entry.filePath());
      }
      for (MLuaDocBuilder.DocEntry entry : moduleEntries(MLuaDocBuilder.DocEntryType.LIBRARY)) {
         putDocAlias(hrefs, entry.name(), "../" + entry.filePath());
      }

      for (EnumInfo ei : discoverEnums()) {
         putDocAlias(hrefs, ei.name, "../enums/" + ei.fileName + ".html");
         putDocAlias(hrefs, ei.fileName, "../enums/" + ei.fileName + ".html");
      }


      for (MLuaDocBuilder.DocEntry entry : moduleEntries(MLuaDocBuilder.DocEntryType.SERVICE)) {
         putDocAlias(hrefs, entry.name(), "../" + entry.filePath());
      }
      for (MLuaDocBuilder.DocEntry entry : moduleEntries(MLuaDocBuilder.DocEntryType.CLASS)) {
         putDocAlias(hrefs, entry.name(), "../" + entry.filePath());
      }

      return hrefs;
   }

   private static String sidebar(String current) {
      long slashes = current.chars().filter(ch -> ch == '/').count();
      String p = "";
      for (int i = 0; i < slashes; i++) p += "../";
      if (p.isEmpty()) p = "./";
      
      String intro = sLink(p + "index.html", "Overview", current.equals("index")) +
                     sLink(p + "guides/getting-started.html", "Getting Started", current.equals("guides/getting-started")) +
                     sLink(p + "guides/mod-integration.html", "Mod Integration", current.equals("guides/mod-integration")) +
                     sLink(p + "guides/addons.html", "Addon API", current.equals("guides/addons"));
      for (Map.Entry<String, List<MLuaDocBuilder.Contribution>> section : contributionSections(activeContributions).entrySet()) {
         if (MLuaDocBuilder.isGettingStartedSection(section.getValue().get(0).section())) continue;
         String key = contributionSectionPath(section.getKey());
         intro += sLink(p + key + ".html", docSectionTitle(section.getValue().get(0).section()), current.equals(key));
      }

      // Services from module contributions and the active runtime registry.
      String services = renderModuleTree(moduleEntries(MLuaDocBuilder.DocEntryType.SERVICE), "services", p, current);
      // Classes from module contributions and the active runtime registry.
      String classes = renderModuleTree(moduleEntries(MLuaDocBuilder.DocEntryType.CLASS), "classes", p, current);

      StringBuilder enums = new StringBuilder();
      enums.append(sLink(p + "enums/enum.html", "Enum Overview", current.equals("enums/enum")));
      for (EnumInfo ei : discoverEnums()) {
          enums.append(sLink(p + "enums/" + ei.fileName + ".html", ei.name, current.equals("enums/" + ei.fileName)));
      }


      String dataTypes = renderModuleTree(moduleEntries(MLuaDocBuilder.DocEntryType.DATA_TYPE), "data-types", p, current);

      String libraries = renderModuleTree(moduleEntries(MLuaDocBuilder.DocEntryType.LIBRARY), "libraries", p, current);

       String apiRef = sFolder("Services", services, current.startsWith("services/")) +
                       sFolder("Classes", classes, current.startsWith("classes/") || current.startsWith("classes.")) +
                       sFolder("Enums", enums.toString(), current.startsWith("enums/")) +
                       sFolder("Data Types", dataTypes, current.startsWith("data-types/"));
       apiRef += sLink(p + "api/lua.html", "Lua API", current.equals("api/lua"));

      return "<nav class=\"sidebar\">\n" +
             "  <div class=\"sidebar-header\">\n" +
             "    <a class=\"sidebar-logo\" href=\"" + p + "index.html\"><span>M</span>Lua</a>\n" +
             "  </div>\n" +
             "  <div class=\"sidebar-content\">\n" +
             "    " + sFolder("Introduction", intro, current.equals("index") || current.startsWith("guides/")) + "\n" +
             "    " + sFolder("API Reference", apiRef, true) + "\n" +
             "    " + sFolder("Libraries", libraries, current.startsWith("libraries/")) + "\n" +
             "  </div>\n" +
             "</nav>";
   }

   /** Writes a side- and phase-specific crawl of the actual Lua globals into the generated docs site. */
   public static void writeRuntimeLuaApi(Path gameDir, String side, String phase, String scriptName, String inventoryHtml) {
      writeRuntimeLuaApi(gameDir, side, phase, scriptName,
         new dev.mlua.core.engine.MLuaLuaApiCrawler.CrawlResult(inventoryHtml));
   }

   public static void writeRuntimeLuaApi(Path gameDir, String side, String phase, String scriptName,
                                         dev.mlua.core.engine.MLuaLuaApiCrawler.CrawlResult crawl) {
      Path docs = gameDir.resolve("mlua").resolve("docs");
      String details = "<p>Captured while loading <code>" + htmlEscape(scriptName) + "</code> on the "
         + htmlEscape(side) + " side during the " + htmlEscape(phase) + " phase.</p>"
         + crawl.inventoryHtml();
      try {
         Files.createDirectories(docs);
         write(docs.resolve("api").resolve("lua.html"), page("Lua API - MLua", sidebar("api/lua"),
            content("Lua API", "Lua classes, services, and globals available to this script.", null, details)));
         Files.deleteIfExists(docs.resolve("live-lua-api.html"));
         clearGeneratedLuaApiPages(docs.resolve("api/lua"));
      } catch (IOException error) {
         MLuaLogger.LOGGER.warn("Failed to write runtime Lua API documentation in {}", docs, error);
      }
   }

   /** Writes the build-time LuaJ table crawl produced by the gendocs task. */
   public static void writeGeneratedLuaApi(Path gameDir, String inventoryHtml) {
      Path docs = gameDir.resolve("mlua").resolve("docs");
      String details = "<p>Built from Lua globals, module bindings, and registered services for both runtime sides.</p>" + inventoryHtml;
      try {
         Files.createDirectories(docs);
         write(docs.resolve("api").resolve("lua.html"), page("Lua API - MLua", sidebar("api/lua"),
            content("Lua API", "Lua classes, services, and globals registered by MLua.", null, details)));
         Files.deleteIfExists(docs.resolve("live-lua-api.html"));
         clearGeneratedLuaApiPages(docs.resolve("api/lua"));
      } catch (IOException error) {
         MLuaLogger.LOGGER.warn("Failed to write generated Lua API documentation in {}", docs, error);
      }
   }

   private static void clearGeneratedLuaApiPages(Path root) throws IOException {
      if (!Files.exists(root)) return;
      List<Path> previous;
      try (var paths = Files.walk(root)) {
         previous = paths.sorted(java.util.Comparator.reverseOrder()).toList();
      }
      for (Path path : previous) Files.deleteIfExists(path);
   }

   private static String htmlEscape(String value) {
      return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
         .replace("\"", "&quot;").replace("'", "&#39;");
   }

    static String linkMemberParts(String name, String returnType) {
       if (name.contains("</a>")) return name;
       int dot = name.indexOf('.');
       int colon = name.indexOf(':');
       int sep = -1;
       if (dot >= 0 && colon >= 0) sep = Math.min(dot, colon);
       else if (dot >= 0) sep = dot;
       else if (colon >= 0) sep = colon;
       if (sep <= 0) return name;

       String prefix = name.substring(0, sep);
       String suffixAndRest = name.substring(sep + 1);
       int paren = suffixAndRest.indexOf('(');
       String suffix = paren >= 0 ? suffixAndRest.substring(0, paren) : suffixAndRest;
       String parenPart = paren >= 0 ? suffixAndRest.substring(paren) : "";
       if (suffix.isEmpty()) return name;

       StringBuilder result = new StringBuilder();
       String prefixHref = typeHref(prefix.toLowerCase(Locale.ROOT));
       String suffixHref = returnType != null ? typeHref(returnType.toLowerCase(Locale.ROOT)) : null;

       if (prefixHref != null) {
          result.append("<a href=\"").append(prefixHref).append("\">").append(prefix).append("</a>");
       } else {
          result.append(prefix);
       }
       result.append(name.charAt(sep));
       if (suffixHref != null) {
          result.append("<a href=\"").append(suffixHref).append("\">").append(suffix).append("</a>");
       } else {
          result.append(suffix);
       }
       result.append(parenPart);
       return result.toString();
    }

      private static String member(String name, String type, String desc, String flags) {
         return "<div class=\"member\"><div class=\"member-header\"><span class=\"member-name\">" + linkMemberParts(name, type) + "</span>" + flags + "</div><div class=\"member-desc\">" + desc + "</div></div>";
       }

       private static String memberFull(String name, String type, String desc, String extra) {
          return "<div class=\"member\"><div class=\"member-header\"><span class=\"member-name\">" + linkMemberParts(name, type) + "</span>" + extra + "</div><div class=\"member-desc\">" + desc + "</div></div>";
       }

   private static String section(String title, String desc, String content) {
      return "<h2 id=\"" + slug(title) + "\">" + title + "</h2><p class=\"section-desc\">" + desc + "</p>" + content;
   }

   private static String slug(String s) {
      return s.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
   }

   private static String callout(String type, String msg) {
      return "<div class=\"callout " + type + "\">" + msg + "</div>";
   }

   private static String codeBlock(String code) {
      boolean plainText = code.contains("<game directory>") || code.contains("src/main/resources/")
         || code.endsWith(".lua") || code.endsWith("/");
      boolean java = !plainText && (code.contains("import ") || code.contains("public ")
         || code.contains("private ") || code.contains("MLuaApi") || code.contains("@Override")
         || code.contains("new "));
      boolean lua = !plainText && !java;
      Set<String> keywords = lua
         ? Set.of("and", "break", "do", "else", "elseif", "end", "false", "for", "function", "if", "in", "local", "nil", "not", "or", "repeat", "return", "then", "true", "until", "while", "goto", "continue")
         : java ? Set.of("abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue", "default", "do", "double", "else", "enum", "extends", "false", "final", "finally", "float", "for", "if", "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "null", "package", "private", "protected", "public", "return", "short", "static", "super", "switch", "synchronized", "this", "throw", "throws", "transient", "true", "try", "void", "volatile", "while", "var", "yield", "record", "sealed", "permits")
         : Set.of();
      StringBuilder highlighted = new StringBuilder();
      int index = 0;
      while (index < code.length()) {
         int start = index;
         char current = code.charAt(index);
         if ((current == '-' && index + 1 < code.length() && code.charAt(index + 1) == '-')
            || (current == '/' && index + 1 < code.length() && code.charAt(index + 1) == '/')) {
            index = code.indexOf('\n', index);
            if (index < 0) index = code.length();
            appendCodeToken(highlighted, code.substring(start, index), "syntax-comment");
         } else if (current == '/' && index + 1 < code.length() && code.charAt(index + 1) == '*') {
            int end = code.indexOf("*/", index + 2);
            index = end < 0 ? code.length() : end + 2;
            appendCodeToken(highlighted, code.substring(start, index), "syntax-comment");
         } else if (current == '"' || current == '\'') {
            char quote = current;
            index++;
            while (index < code.length()) {
               char next = code.charAt(index++);
               if (next == '\\' && index < code.length()) index++;
               else if (next == quote) break;
            }
            appendCodeToken(highlighted, code.substring(start, index), "syntax-string");
         } else if (Character.isDigit(current)) {
            index++;
            while (index < code.length() && (Character.isDigit(code.charAt(index)) || code.charAt(index) == '.')) index++;
            appendCodeToken(highlighted, code.substring(start, index), "syntax-number");
         } else if (Character.isLetter(current) || current == '_' || current == '$') {
            index++;
            while (index < code.length() && (Character.isLetterOrDigit(code.charAt(index))
               || code.charAt(index) == '_' || code.charAt(index) == '$')) index++;
            String token = code.substring(start, index);
            appendCodeToken(highlighted, token, keywords.contains(token) ? "syntax-keyword" : null);
         } else {
            index++;
            appendCodeToken(highlighted, code.substring(start, index), null);
         }
      }
      String language = plainText ? "text" : java ? "java" : "lua";
      return "<pre><code class=\"language-" + language + "\">" + highlighted + "</code></pre>";
   }

   private static void appendCodeToken(StringBuilder output, String token, String style) {
      String escaped = htmlEscape(token);
      if (style == null) output.append(escaped);
      else output.append("<span class=\"").append(style).append("\">").append(escaped).append("</span>");
   }

   private static String flags(String... badges) {
      StringBuilder sb = new StringBuilder("<div class=\"member-flags\">");
      String[] var2 = badges;
      int var3 = badges.length;

      for(int var4 = 0; var4 < var3; ++var4) {
         String b = var2[var4];
         sb.append("<span class=\"badge ").append(b.split(":")[0]).append("\">").append(b.split(":")[1]).append("</span>");
      }

      sb.append("</div>");
      return sb.toString();
   }

   private static String overviewContent() {
      StringBuilder services = new StringBuilder("<div class=\"cards\">");
      for (MLuaDocBuilder.DocEntry entry : moduleEntries(MLuaDocBuilder.DocEntryType.SERVICE)) {
         if (entry.filePath() != null) services.append(card(entry.filePath(), entry.name(), entry.desc()));
      }
      services.append("</div>");

      StringBuilder classes = new StringBuilder("<div class=\"cards\">");
      for (MLuaDocBuilder.DocEntry entry : moduleEntries(MLuaDocBuilder.DocEntryType.CLASS)) {
         if (entry.filePath() != null) classes.append(card(entry.filePath(), entry.name(), entry.desc()));
      }
      classes.append("</div>");

      StringBuilder enums = new StringBuilder("<div class=\"cards\">");
      for (EnumInfo entry : discoverEnums()) {
         enums.append(card("enums/" + entry.fileName + ".html", entry.name, entry.description));
      }
      enums.append("</div>");

      StringBuilder guides = new StringBuilder("<div class=\"cards\">");
      for (Map.Entry<String, List<MLuaDocBuilder.Contribution>> section : contributionSections(activeContributions).entrySet()) {
         if (MLuaDocBuilder.isGettingStartedSection(section.getValue().get(0).section())) continue;
         MLuaDocBuilder.Contribution first = section.getValue().get(0);
         guides.append(card(contributionSectionPath(section.getKey()) + ".html",
            docSectionTitle(first.section()), first.description()));
      }
      guides.append("</div>");

      return "<div class=\"content\"><div class=\"hero\"><h1>MLua Documentation</h1><p>MLua brings Lua scripting to Minecraft. Register game content, define Lua classes and services, and use Java-backed APIs to work with the world.</p></div><div class=\"callout warn\"><strong>Security Notice:</strong> MLua scripts are trusted mod code. Installing an MLua script pack is equivalent to installing code that runs in your game. Do not run scripts from untrusted sources.</div>"
         + "<h2>Start here</h2><div class=\"cards\">"
         + card("guides/getting-started.html", "Getting Started", "Set up MLua and write your first script.")
         + card("api/lua.html", "Lua API", "Browse classes, services, and globals discovered from Lua tables.")
         + card("guides/addons.html", "Addon API", "Register services, classes, mixins, and addon documentation.")
         + "</div><h2>Guides</h2>" + guides + "<h2>Services</h2>" + services + "<h2>Classes</h2>" + classes + "<h2>Enums</h2>" + enums + "</div>";
   }

   private static String card(String href, String title, String desc) {
      return "<a class=\"card\" href=\"" + htmlEscape(href) + "\"><h3>" + htmlEscape(title)
         + "</h3><p>" + htmlEscape(desc == null ? "" : desc) + "</p></a>";
   }

       static String inheritsFrom(String parentType) {
       String href = typeHref(parentType.toLowerCase(Locale.ROOT));
       String link = href != null ? "<a href=\"" + href + "\">" + parentType + "</a>" : parentType;
       return "<p class=\"inherits-from\">Inherits from: " + link + "</p>";
    }

        static String linkType(String type) {
       if (type == null) {
         return "";
      } else {
         String trimmed = type.trim();
         if (trimmed.isEmpty()) {
            return trimmed;
         } else {
            String[] parts = trimmed.split("\\s*,\\s*");
            StringBuilder out = new StringBuilder();

            for(int i = 0; i < parts.length; ++i) {
               if (i > 0) {
                  out.append(", ");
               }

               out.append(linkSingleType(parts[i]));
            }

            return out.toString();
         }
      }
   }

   private static String linkSingleType(String type) {
      String trimmed = type.trim();
      String href = typeHref(trimmed);
      String escaped = escapeHtml(trimmed);
      return href == null ? escaped : "<a href=\"" + href + "\">" + escaped + "</a>";
   }

      private static String moduleReferenceContent(MLuaDocBuilder.DocEntry entry,
                                               List<MLuaDocBuilder.GuideStep> guideSteps,
                                               List<MLuaDocBuilder.MethodDoc> methodDocs,
                                               List<MLuaDocBuilder.SignatureDoc> signatureDocs,
                                               List<MLuaDocBuilder.ServiceFunctionDoc> serviceFunctions,
                                               List<MLuaDocBuilder.MemberDoc> memberDocs) {
      String ctx = switch (entry.type()) {
         case SERVICE -> ctx("Minecraft:GetService(\"" + entry.name() + "\")", entry.name());
         case CLASS, ENUM, DATA_TYPE, LIBRARY -> null;
      };
      String extra = "";
      if (entry.type() == MLuaDocBuilder.DocEntryType.CLASS && entry.parentClass() != null) {
         extra += "<div class=\"inherits-from\">Inherits from " + linkType(entry.parentClass()) + "</div>";
      }
      extra += registeredGuideSteps(entry, guideSteps);
      extra += registeredMethodDocs(entry, methodDocs);
      extra += registeredSignatureDocs(entry, signatureDocs);
      extra += registeredServiceFunctions(entry, signatureDocs, serviceFunctions);
      extra += registeredMemberDocs(entry, memberDocs);

      Optional<Path> root = currentGameDir == null ? Optional.empty() : MLuaApiDocScanner.findSourceRoot(currentGameDir);
      Optional<String> sourcePath = root.isEmpty() ? Optional.empty() : resolveSourcePath(root.get(), entry.className());
      if (root.isPresent() && sourcePath.isPresent()) {
         extra += MLuaApiDocScanner.categorizedReferenceSectionsFromSourceRoot(root.get(), sourcePath.get(), entry.name());
         Set<String> scannedMethods = new java.util.HashSet<>();
         for (MLuaApiDocScanner.Entry scanned : MLuaApiDocScanner.getEntries(root.get(), sourcePath.get(), entry.name()).values()) {
            String methodName = signatureMethodName(scanned.signature, entry.name());
            if (methodName != null) scannedMethods.add(methodName);
         }
         LuaTable classTable = runtimeClassTable(entry);
         if (classTable != null) {
            String registeredMethods = registeredMethodReference(classTable,
               classTable.rawget("__type").optjstring(entry.name()), scannedMethods);
            if (!registeredMethods.isEmpty()) {
               extra += section("Registered Methods", "Methods exposed by the live class registration.", registeredMethods);
            }
         }
      } else {
         extra += fallbackReferenceContent(entry);
      }

      return content(entry.name(), entry.desc(), ctx, extra);
   }

   private static String registeredSignatureDocs(MLuaDocBuilder.DocEntry entry,
                                                  List<MLuaDocBuilder.SignatureDoc> signatureDocs) {
      List<MLuaDocBuilder.SignatureDoc> matching = signatureDocs.stream()
         .filter(signature -> entry.type() == MLuaDocBuilder.DocEntryType.CLASS
            && signature.receiver().equals(entry.name()))
         .toList();
      if (matching.isEmpty()) return "";
      StringBuilder methods = new StringBuilder();
      for (MLuaDocBuilder.SignatureDoc signature : matching) {
         String signatureText = signature.signature();
         if (signature.returns() != null && !signature.returns().isBlank()) {
            signatureText += ": " + signature.returns();
         }
         String methodName = signatureMethodName(signature.signature(), entry.name());
         String description = registeredMethodDescription(entry.name(), methodName);
         methods.append(memberFull("<code>" + htmlEscape(signatureText) + "</code>", null,
            description.isBlank() ? "" : htmlEscape(description), "<div class=\"member-flags\"><span class=\"badge shared\">API</span></div>"));
      }
      return section("Methods", "Typed signatures declared by the owning module.", methods.toString());
   }

   private static String registeredServiceFunctions(MLuaDocBuilder.DocEntry entry,
                                                    List<MLuaDocBuilder.SignatureDoc> signatures,
                                                    List<MLuaDocBuilder.ServiceFunctionDoc> serviceFunctions) {
      if (entry.type() != MLuaDocBuilder.DocEntryType.SERVICE) return "";
      List<MLuaDocBuilder.ServiceFunctionDoc> matching = serviceFunctions.stream()
         .filter(function -> function.service().equals(entry.name()))
         .toList();
      if (matching.isEmpty()) return "";
      StringBuilder methods = new StringBuilder();
      for (MLuaDocBuilder.ServiceFunctionDoc function : matching) {
         MLuaDocBuilder.SignatureDoc signature = signatures.stream()
            .filter(candidate -> (candidate.receiver().equals(entry.name())
               || candidate.receiver().equals(function.className()))
               && function.name().equals(signatureMethodName(candidate.signature(), candidate.receiver())))
            .findFirst().orElse(null);
         String methodName = signature == null ? entry.name() + ":" + function.name() : signature.signature();
         if (signature != null && signature.returns() != null && !signature.returns().isBlank()
            && signature.signature().substring(Math.max(0, signature.signature().lastIndexOf(')') + 1)).isBlank()) {
            methodName += ": " + signature.returns();
         }
         methods.append(memberFull("<code>" + htmlEscape(methodName) + "</code>",
            null, htmlEscape(function.description()), "<div class=\"member-flags\"><span class=\"badge shared\">Service method</span></div>"));
      }
      return section("Methods", "Functions declared by this service's registration contract.", methods.toString());
   }

   private static String registeredMethodDescription(String className, String methodName) {
      if (methodName == null || methodName.isBlank()) return "";
      LuaTable classTable = MLuaRuntime.get().classTable(className);
      if (classTable == null) return "";
      LuaTable descriptions = registeredClassMethodDescriptions(classTable);
      return descriptions == null ? "" : descriptions.get(methodName).optjstring("");
   }

   private static String registeredMethodDocs(MLuaDocBuilder.DocEntry entry,
                                               List<MLuaDocBuilder.MethodDoc> methodDocs) {
      if (entry.type() != MLuaDocBuilder.DocEntryType.CLASS) return "";
      List<MLuaDocBuilder.MethodDoc> matching = methodDocs.stream()
         .filter(method -> method.receiver().equals(entry.name()))
         .toList();
      if (matching.isEmpty()) return "";
      StringBuilder members = new StringBuilder();
      for (MLuaDocBuilder.MethodDoc method : matching) {
         StringBuilder description = new StringBuilder(htmlEscape(method.description()));
         if (method.parameters() != null && !method.parameters().isBlank()) {
            description.append("<p><strong>Parameters:</strong> ").append(htmlEscape(method.parameters())).append("</p>");
         }
         if (method.example() != null && !method.example().isBlank()) {
            description.append(codeBlock(method.example()));
         }
         String signature = method.signature();
         int closeParen = signature.lastIndexOf(')');
         boolean hasReturnType = closeParen >= 0 && !signature.substring(closeParen + 1).isBlank();
         if (!hasReturnType && method.returns() != null && !method.returns().isBlank()) {
            signature += ": " + method.returns();
         }
         String side = method.side() == null || method.side().isBlank()
            ? "shared:Shared" : method.side();
         members.append(memberFull("<code>" + htmlEscape(signature) + "</code>", null,
            description.toString(), registeredMethodFlags(side)));
      }
      return section("Methods", "Signatures and usage registered by the owning module.", members.toString());
   }

   private static String registeredMemberDocs(MLuaDocBuilder.DocEntry entry,
                                                List<MLuaDocBuilder.MemberDoc> memberDocs) {
      List<MLuaDocBuilder.MemberDoc> matching = memberDocs.stream()
         .filter(member -> member.receiver().equals(entry.name()))
         .toList();
      if (matching.isEmpty()) return "";
      StringBuilder properties = new StringBuilder();
      StringBuilder events = new StringBuilder();
      for (MLuaDocBuilder.MemberDoc member : matching) {
         String name = htmlEscape(member.signature());
         String type = member.kind() == MLuaDocBuilder.MemberKind.EVENT ? "Signal" : htmlEscape(member.type());
         StringBuilder description = new StringBuilder(htmlEscape(member.description()));
         if (member.kind() == MLuaDocBuilder.MemberKind.EVENT) {
            description.append("<p><strong>Arguments:</strong> ").append(htmlEscape(member.type())).append("</p>");
         }
         String side = member.side() == null || member.side().isBlank()
            ? "shared:Shared" : member.side();
         String rendered = memberFull("<code>" + name + "</code>", type,
            description.toString(), registeredMethodFlags(side));
         if (member.kind() == MLuaDocBuilder.MemberKind.PROPERTY) properties.append(rendered);
         else events.append(rendered);
      }
      StringBuilder sections = new StringBuilder();
      if (properties.length() > 0) sections.append(section("Properties", "Registered properties on this live API.", properties.toString()));
      if (events.length() > 0) sections.append(section("Events", "Registered signals and events on this live API.", events.toString()));
      return sections.toString();
   }

   private static String registeredMethodFlags(String side) {
      StringBuilder badges = new StringBuilder("<div class=\"member-flags\">");
      for (String badge : side.split(",")) {
         String[] parts = badge.trim().split(":", 2);
         if (parts.length != 2 || !parts[0].matches("[A-Za-z0-9_-]+")) continue;
         badges.append("<span class=\"badge ").append(parts[0]).append("\">")
            .append(htmlEscape(parts[1])).append("</span>");
      }
      return badges.append("</div>").toString();
   }

   private static String registeredGuideSteps(MLuaDocBuilder.DocEntry entry,
                                               List<MLuaDocBuilder.GuideStep> guideSteps) {
      List<MLuaDocBuilder.GuideStep> matching = guideSteps.stream()
         .filter(step -> step.receiver().equals(entry.name()))
         .toList();
      if (matching.isEmpty()) return "";
      StringBuilder html = new StringBuilder("<h2>Learn by Building</h2>");
      for (int index = 0; index < matching.size(); index++) {
         MLuaDocBuilder.GuideStep step = matching.get(index);
         html.append("<h3>").append(index + 1).append(". ").append(htmlEscape(step.title())).append("</h3>")
            .append("<p>").append(htmlEscape(step.explanation())).append("</p>")
            .append(codeBlock(step.example()));
      }
      return html.toString();
   }

   private static String fallbackReferenceContent(MLuaDocBuilder.DocEntry entry) {
      if (entry.type() != MLuaDocBuilder.DocEntryType.SERVICE
          && entry.type() != MLuaDocBuilder.DocEntryType.CLASS) return "";
      MLuaRuntime runtime = MLuaRuntime.get();
      String className = entry.type() == MLuaDocBuilder.DocEntryType.SERVICE
         ? runtime.serviceClassName(entry.name()) : entry.name();
      LuaTable classTable = runtime.classTable(className);
      StringBuilder details = new StringBuilder();
      if (entry.type() == MLuaDocBuilder.DocEntryType.SERVICE) {
         details.append("<p>Get this live service with <code>Minecraft:GetService(\"")
            .append(htmlEscape(entry.name())).append("\")</code>. Repeated calls in the same script context return the same service instance.</p>");
      }
      if (classTable != null) {
         details.append("<p>Registered class: <code>").append(htmlEscape(className)).append("</code></p>");
         String methods = registeredMethodReference(classTable, className);
         if (!methods.isEmpty()) details.append(section("Registered Methods", "Methods and descriptions supplied by class registration.", methods));
      } else {
         if (entry.className() != null && !entry.className().isBlank()) {
            details.append("<p>Backed by <code>").append(htmlEscape(entry.className())).append("</code>.</p>");
         } else {
         details.append("<p>Lua-registered class.</p>");
         }
      }
      if (entry.type() == MLuaDocBuilder.DocEntryType.CLASS
          && classTable == null && (entry.className() == null || entry.className().isBlank())) return "";
      return section("Reference", "", details.toString());
   }

   private static String registeredMethodReference(LuaTable classTable, String className) {
      return registeredMethodReference(classTable, className, Set.of());
   }

   private static String registeredMethodReference(LuaTable classTable, String className, Set<String> excludedNames) {
      LuaTable docs = registeredClassMethodDescriptions(classTable);
      if (docs == null) return "";
      List<String> names = new ArrayList<>();
      LuaValue key = LuaValue.NIL;
      while (true) {
         org.luaj.vm2.Varargs entry = docs.next(key);
         key = entry.arg1();
         if (key.isnil()) break;
         if (key.isstring()) names.add(key.tojstring());
      }
      names.sort(String.CASE_INSENSITIVE_ORDER);
      StringBuilder members = new StringBuilder();
      for (String name : names) {
         if (excludedNames.contains(name) || !name.matches("[A-Za-z_][A-Za-z0-9_]*")) continue;
         String signature = htmlEscape(className) + (name.equals("new")
            ? ".new" : ":" + name);
         String description = docs.get(name).optjstring("");
         members.append(memberFull(signature, null, htmlEscape(description), flags("shared:Shared")));
      }
      return members.toString();
   }

   private static LuaTable registeredClassMethodDescriptions(LuaTable classTable) {
      LuaValue ownValue = classTable.rawget("__mlua_doc_methods");
      LuaValue inheritedValue = classTable.rawget("__mlua_doc_inherited_methods");
      if (!ownValue.istable() && !inheritedValue.istable()) return null;
      LuaTable combined = new LuaTable();
      if (inheritedValue.istable()) copyEntries(inheritedValue.checktable(), combined, false);
      if (ownValue.istable()) copyEntries(ownValue.checktable(), combined, true);
      return combined;
   }

   private static void copyEntries(LuaTable source, LuaTable target, boolean replace) {
      LuaValue key = LuaValue.NIL;
      while (true) {
         org.luaj.vm2.Varargs entry = source.next(key);
         key = entry.arg1();
         if (key.isnil()) return;
         if (replace || target.rawget(key).isnil()) target.rawset(key, entry.arg(2));
      }
   }

   private static void appendRegisteredClassMethods(Path docs, List<MLuaDocBuilder.DocEntry> entries) throws IOException {
      for (MLuaDocBuilder.DocEntry entry : entries) {
         if (entry.type() != MLuaDocBuilder.DocEntryType.CLASS
            && entry.type() != MLuaDocBuilder.DocEntryType.SERVICE) continue;
         Path pagePath = docs.resolve(entry.filePath());
         if (!Files.isRegularFile(pagePath)) continue;

         LuaTable classTable = runtimeClassTable(entry);
         if (classTable == null) continue;
         String className = entry.type() == MLuaDocBuilder.DocEntryType.SERVICE
            ? entry.name() : classTable.rawget("__type").optjstring(entry.name());
         String html = Files.readString(pagePath, StandardCharsets.UTF_8);
         LuaTable methods = registeredClassMethodDescriptions(classTable);

         Set<String> alreadyDocumented = new java.util.HashSet<>();
         if (methods != null) {
            LuaValue key = LuaValue.NIL;
            while (true) {
               org.luaj.vm2.Varargs method = methods.next(key);
               key = method.arg1();
               if (key.isnil()) break;
               if (key.isstring() && hasDocumentedMethodMember(html, key.tojstring())) {
                  alreadyDocumented.add(key.tojstring());
               }
            }
         }

         String methodsReference = methods == null ? "" : registeredMethodReference(classTable, className, alreadyDocumented);
         String propertiesReference = registeredPropertyReference(classTable, className, html);
         String addition = (methodsReference.isEmpty() ? "" : section("Registered Methods",
            "Methods registered on this class and its parent classes.", methodsReference))
            + (propertiesReference.isEmpty() ? "" : section("Registered Properties",
            "Live properties registered on this class and its parent classes.", propertiesReference));
         if (addition.isEmpty()) continue;
         int insertion = html.lastIndexOf("</div></div></div><script>");
         if (insertion < 0) continue;
         html = html.substring(0, insertion) + addition + html.substring(insertion);
         Files.writeString(pagePath, html, StandardCharsets.UTF_8);
      }
   }

   private static String registeredPropertyReference(LuaTable classTable, String className, String html) {
      LuaTable properties = new LuaTable();
      collectRegisteredProperties(classTable, properties, new java.util.HashSet<>());
      List<String> names = new ArrayList<>();
      LuaValue key = LuaValue.NIL;
      while (true) {
         org.luaj.vm2.Varargs entry = properties.next(key);
         key = entry.arg1();
         if (key.isnil()) break;
         if (key.isstring()) names.add(key.tojstring());
      }
      names.sort(String.CASE_INSENSITIVE_ORDER);
      StringBuilder members = new StringBuilder();
      for (String name : names) {
         if (hasDocumentedPropertyMember(html, name)) continue;
         LuaValue description = properties.get(name);
         LuaTable descriptor = findRegisteredProperty(classTable, name, new java.util.HashSet<>());
         boolean readable = descriptor != null && descriptor.rawget("get").isfunction();
         boolean writable = descriptor != null && descriptor.rawget("set").isfunction();
         String access = readable && writable ? "read:Read,write:Write"
            : readable ? "read:Read" : writable ? "write:Write" : "";
         LuaValue type = descriptor == null ? LuaValue.NIL : descriptor.rawget("type");
         LuaValue side = descriptor == null ? LuaValue.NIL : descriptor.rawget("side");
         String availability = side.isstring() ? side.tojstring() : "shared:Shared";
         if (!access.isEmpty()) availability = access + "," + availability;
         String valueType = type.isstring() ? linkType(type.tojstring()) : "any";
         members.append(memberFull("<code>" + htmlEscape(className + "." + name) + "</code>", valueType,
            htmlEscape(description.optjstring("")), registeredMethodFlags(availability)));
      }
      return members.toString();
   }

   private static boolean hasDocumentedPropertyMember(String html, String propertyName) {
      String marker = "class=\"member-name\">";
      int from = 0;
      while (true) {
         int start = html.indexOf(marker, from);
         if (start < 0) return false;
         start += marker.length();
         int end = html.indexOf("</span>", start);
         if (end < 0) return false;
         if (html.substring(start, end).contains("." + propertyName)) return true;
         from = end + 7;
      }
   }

   private static void collectRegisteredProperties(LuaTable classTable, LuaTable output, Set<String> visited) {
      String className = classTable.rawget("__type").optjstring("");
      if (!visited.add(className)) return;
      LuaValue docs = classTable.rawget("__mlua_doc_properties");
      if (docs.istable()) copyEntries(docs.checktable(), output, false);
      forEachRegisteredParent(classTable, parentName -> {
         LuaTable parent = MLuaRuntime.get().classTable(parentName);
         if (parent != null) collectRegisteredProperties(parent, output, visited);
      });
   }

   private static LuaTable findRegisteredProperty(LuaTable classTable, String propertyName, Set<String> visited) {
      String className = classTable.rawget("__type").optjstring("");
      if (!visited.add(className)) return null;
      LuaValue own = classTable.rawget("__mlua_properties");
      LuaValue descriptor = own.istable() ? own.checktable().rawget(propertyName) : LuaValue.NIL;
      if (descriptor.istable()) return descriptor.checktable();
      LuaTable[] found = {null};
      forEachRegisteredParent(classTable, parentName -> {
         if (found[0] != null) return;
         LuaTable parent = MLuaRuntime.get().classTable(parentName);
         if (parent != null) found[0] = findRegisteredProperty(parent, propertyName, visited);
      });
      return found[0];
   }

   private static void forEachRegisteredParent(LuaTable classTable,
                                               java.util.function.Consumer<String> consumer) {
      LuaValue parents = classTable.rawget("__mlua_doc_parents");
      if (!parents.istable()) return;
      LuaValue key = LuaValue.NIL;
      while (true) {
         org.luaj.vm2.Varargs entry = parents.checktable().next(key);
         key = entry.arg1();
         if (key.isnil()) return;
         if (entry.arg(2).isstring()) consumer.accept(entry.arg(2).tojstring());
      }
   }

   private static boolean hasDocumentedMethodMember(String html, String methodName) {
      String marker = "class=\"member-name\">";
      int from = 0;
      while (true) {
         int start = html.indexOf(marker, from);
         if (start < 0) return false;
         start += marker.length();
         int end = html.indexOf("</span>", start);
         if (end < 0) return false;
         if (html.substring(start, end).contains(methodName + "(")) return true;
         from = end + 7;
      }
   }

   private static LuaTable runtimeClassTable(MLuaDocBuilder.DocEntry entry) {
      MLuaRuntime runtime = MLuaRuntime.get();
      if (entry.type() == MLuaDocBuilder.DocEntryType.SERVICE) {
         return runtime.classTable(runtime.serviceClassName(entry.name()));
      }
      return runtime.classTable(entry.name());
   }

   private static String signatureMethodName(String signature, String receiver) {
      if (signature == null) return null;
      String prefix = receiver + ":";
      if (!signature.startsWith(prefix)) return null;
      int start = prefix.length();
      int end = signature.indexOf('(', start);
      if (end < 0) end = signature.length();
      return signature.substring(start, end);
   }

   private static Optional<String> resolveSourcePath(Path sourceRoot, String className) {
      Path devRoot = sourceRoot.resolve("dev").resolve("mlua");
      if (!Files.isDirectory(devRoot)) {
         return Optional.empty();
      }

      try (var paths = Files.walk(devRoot)) {
         return paths
            .filter(Files::isRegularFile)
            .filter(path -> path.getFileName().toString().equals(className + ".java"))
            .map(path -> devRoot.relativize(path).toString().replace(File.separatorChar, '/'))
            .findFirst();
      } catch (IOException ignored) {
         return Optional.empty();
      }
   }

   static String typeHref(String type) {
      return docHrefIndex().get(normalizeDocAlias(type));
   }

   private static String escapeHtml(String text) {
      String var10000 = text.replace("&", "&amp;").replace("<", "&lt;");
      return var10000.replace(">", "&gt;");
   }

   private static String commandContextContent() {
      String ctxUsage = ctx("Commands:Register(\"ping\", { run = function(ctx) ctx:Reply(\"pong\") end, run_client = function(ctx) ctx:Reply(\"pong client\") end })", (String)null);
       String properties = member("CommandContext.Source", "CommandSourceStack", "The Forge command source stack.", flags("read:Read")) +
          member("CommandContext.Player", "Player", "The executing player, or nil if console.", flags("read:Read")) +
          member("CommandContext.IsPlayer", "boolean", "Whether the source is a player.", flags("read:Read")) +
          member("CommandContext.Position", "Vector3", "The source's position.", flags("read:Read"));
       String methods = memberFull("CommandContext:Reply(message)", "void", "Sends a chat reply to the command source.", flags("server:Server")) +
          memberFull("CommandContext:Broadcast(message)", "void", "Broadcasts a message to all players.", flags("server:Server")) +
          memberFull("CommandContext:Error(message)", "void", "Sends an error message to the source.", flags("server:Server"));
       return content("CommandContext", "An object passed to command handlers providing access to the command source, player, and reply utilities.", ctxUsage,
          section("Properties", "", properties) +
          section("Methods", "", methods));
    }

               private static final Map<String, String> ENUM_DESCRIPTIONS = Map.ofEntries(
       Map.entry("GameMode", "Controls the game mode of a player."),
       Map.entry("Difficulty", "Controls world difficulty level."),
       Map.entry("Weather", "Controls world weather state."),
       Map.entry("Direction", "Represents a cardinal direction."),
       Map.entry("Hand", "Represents a player's hand slot."),
       Map.entry("SoundCategory", "Categories for sound volume control."),
       Map.entry("EquipmentSlot", "Represents an equipment slot on an entity."),
       Map.entry("CoreGuiType", "Enum for CoreGui visibility control."),
       Map.entry("RenderLayer", "Enum for UI render layers."),
       Map.entry("TextXAlignment", "Horizontal text alignment."),
       Map.entry("TextYAlignment", "Vertical text alignment."),
       Map.entry("ScaleType", "Image scaling mode."),
       Map.entry("ResampleMode", "Image resampling mode."),
       Map.entry("ZIndexBehavior", "Controls Z-index sorting behavior."),
       Map.entry("CameraMode", "Controls whether the camera follows the player or is controlled by a script."),
       Map.entry("ViewMode", "Controls the local player's camera perspective."),
       Map.entry("Keys", "Key constants for defaults, rebinding, and input checks.")
   );

   private record EnumInfo(String name, String title, String fileName, String[][] constants,
                           String description, Map<String, String> luaValues) {
        String toContent() {
            return enumContent(name, description, constants, example(), relatedClass(), "shared:Shared");
        }
        String luaValueOf(String key) { return luaValues.getOrDefault(key, key); }
        private static String toSlug(String camel) {
            return camel.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
        }
       private String example() {
           return switch (name) {
               case "GameMode" -> "player:SetGameMode(Enum.GameMode.Creative)";
               case "Difficulty" -> "World:SetDifficulty(Enum.Difficulty.Normal)";
               case "Weather" -> "World:SetWeather(Enum.Weather.Clear)";
               case "Direction", "Hand", "SoundCategory", "EquipmentSlot" -> "local value = Enum." + name + "." + constants[0][0].substring(constants[0][0].lastIndexOf('.') + 1);
               case "CoreGuiType" -> "Gui:SetCoreGuiEnabled(Enum.CoreGuiType.Chat, false)";
               default -> null;
           };
       }
       private String relatedClass() {
           return switch (name) {
                case "GameMode" -> "player";
                case "Difficulty", "Weather" -> "world";
                default -> null;
           };
       }
   }

   private static List<EnumInfo> discoverEnums() {
      LuaTable enumRoot = EnumsModule.enumTable();
      List<String> enumNames = new ArrayList<>();
      LuaValue rootKey = LuaValue.NIL;
      while (true) {
         org.luaj.vm2.Varargs entry = enumRoot.next(rootKey);
         rootKey = entry.arg1();
         if (rootKey.isnil()) break;
         if (rootKey.isstring() && entry.arg(2).istable()) enumNames.add(rootKey.tojstring());
      }
      enumNames.sort(String::compareTo);

      Set<LuaTable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
      List<EnumInfo> result = new ArrayList<>();
      for (String enumName : enumNames) {
         LuaTable values = enumRoot.get(enumName).checktable();
         if (!seen.add(values)) continue;
         List<String> constantNames = new ArrayList<>();
         LuaValue key = LuaValue.NIL;
         while (true) {
            org.luaj.vm2.Varargs entry = values.next(key);
            key = entry.arg1();
            if (key.isnil()) break;
            if (key.isstring()) constantNames.add(key.tojstring());
         }
         constantNames.sort(String::compareTo);
         String[][] constants = new String[constantNames.size()][2];
         Map<String, String> luaValues = new HashMap<>();
         for (int index = 0; index < constantNames.size(); index++) {
            String constant = constantNames.get(index);
            LuaValue value = values.get(constant);
            constants[index] = new String[]{"Enum." + enumName + "." + constant, constant};
            luaValues.put(constant, value.isstring() ? value.tojstring() : constant);
         }
         if (constants.length == 0) continue;
         String description = ENUM_DESCRIPTIONS.getOrDefault(enumName,
            "Named values exposed by the " + enumName + " enum.");
         result.add(new EnumInfo(enumName, enumName, EnumInfo.toSlug(enumName), constants,
            description, Map.copyOf(luaValues)));
      }
      return List.copyOf(result);
   }

   private static String enumContent(String name, String desc, String[][] values, String example, String relatedClass, String... sideFlags) {
           StringBuilder rows = new StringBuilder();
           String enumHref = typeHref(name.toLowerCase());

          for(int var8 = 0; var8 < values.length; ++var8) {
             String[] v = values[var8];
             String[] parts = v[0].split("\\.");
             StringBuilder memberName = new StringBuilder();
             if (parts.length >= 3) {
                 memberName.append("<a href=\"../enums/enum.html\">Enum</a>.");
                 memberName.append("<a href=\"").append(enumHref != null ? enumHref : ("../enums/" + parts[1].toLowerCase(Locale.ROOT) + ".html")).append("\">").append(parts[1]).append("</a>.");
                 memberName.append("<a href=\"../libraries/enum-value.html\">").append(parts[2]).append("</a>");
             } else if (parts.length >= 2) {
                 memberName.append("<a href=\"../enums/enum.html\">Enum</a>.");
                 memberName.append("<a href=\"").append(enumHref != null ? enumHref : ("../enums/" + parts[1].toLowerCase(Locale.ROOT) + ".html")).append("\">").append(parts[1]).append("</a>");
             } else {
                 memberName.append(v[0]);
             }
             String[] allFlags = new String[2 + sideFlags.length];
             allFlags[0] = "read:Read";
             allFlags[1] = "write:Write";
             System.arraycopy(sideFlags, 0, allFlags, 2, sideFlags.length);
             rows.append(member(memberName.toString(), null, v[1], flags(allFlags)));
          }
      String ex = "";
      if (example != null && !example.isEmpty()) {
         ex = "<h2>Usage</h2>" + codeBlock(example);
      }

      String related = "";
      if (relatedClass != null) {
         related = "<p>See also: <a href=\"../classes/" + relatedClass + ".html\">" + relatedClass.substring(0, 1).toUpperCase() + relatedClass.substring(1) + "</a></p>";
      }

       return content(name, desc, (String)null, String.valueOf(rows) + ex + related);
    }

    private static String enumOverviewContent() {
        StringBuilder body = new StringBuilder();
        StringBuilder members = new StringBuilder();
        for (EnumInfo ei : discoverEnums()) {
            String href = typeHref(ei.fileName);
            String link = href != null ? "<a href=\"" + href + "\">" + ei.name + "</a>" : ei.name;
            members.append(member("Enum." + link, null, ei.description, flags("read:Read", "write:Write")));
        }
        body.append(section("All Enums", "", members.toString()));
        return content("Enum", "Enum types provide named constant values that can be read and written. Each enum entry is a value with a Name property and can be compared by identity.", null, body.toString());
    }

    private static String gettingStartedContent(List<MLuaDocBuilder.Contribution> contributions) {
      StringBuilder body = new StringBuilder();
      body.append(builtInGettingStartedContent());
      body.append(renderGettingStartedContributions(contributions));
      return content("Getting Started", "Install MLua, create a script, and make a first change in a world.", null,
         body.toString());
    }

    private static String builtInGettingStartedContent() {
       List<MLuaExampleScripts.ExampleScript> examples = MLuaExampleScripts.scripts();
       MLuaExampleScripts.ExampleScript server = examples.stream()
          .filter(example -> example.name().equals("01-basic-server.server.lua"))
          .findFirst().orElse(null);
       MLuaExampleScripts.ExampleScript registry = examples.stream()
          .filter(example -> example.name().equals("02-custom-item.registry.lua"))
          .findFirst().orElse(null);
       StringBuilder html = new StringBuilder();
       html.append("<div class=\"callout info\">These steps use the scripts folder inside your Minecraft game directory. A single player world still has a server side, so server scripts work there too.</div>")
          .append("<section class=\"tutorial-step\"><h3>1. Create a server script</h3>")
          .append("<p>Launch Minecraft once with MLua installed, then create this file. The <code>.server.lua</code> suffix tells MLua to run it on the server when a world starts.</p>")
          .append(codeBlock("<game directory>/mlua/scripts/hello.server.lua"))
          .append(codeBlock(server == null
             ? "local Players = Minecraft:GetService(\"Players\")\n\nPlayers.PlayerAdded:Connect(function(player)\n    player:SendMessage(\"Welcome to my world!\")\nend)"
             : server.source()))
          .append("<p>Start or rejoin a world and join with a player. The message is sent when the player connects. If you change the script, reload the world or restart the game so MLua loads it again.</p>")
          .append("</section><section class=\"tutorial-step\"><h3>2. Register an item</h3>")
          .append("<p>Content registration happens before a world loads. Save this as ")
          .append("<code>&lt;game directory&gt;/mlua/scripts/example-item.registry.lua</code>, then restart Minecraft before checking the creative inventory.</p>")
          .append(codeBlock(registry == null
             ? "local Registry = Minecraft:GetService(\"Registry\")\n\nRegistry.Items:Register(\"example_item\", {\n    maxStackSize = 16,\n    creativeTab = \"misc\"\n})"
             : registry.source()))
          .append("<p>The registry name becomes the item's namespaced id under your mod or script namespace. The <a href=\"../services/registry-items.html\">Items reference</a> lists the supported item properties and registration methods.</p>")
          .append("</section><section class=\"tutorial-step\"><h3>3. Explore the API</h3>")
          .append("<p>Scripts use <code>Minecraft:GetService(\"Name\")</code> to get a service, then use the service's methods and events. For example, <code>Players.PlayerAdded</code> is an event and <code>player:SendMessage(...)</code> is an instance method.</p>")
          .append("<p>Continue with <a href=\"../services/players.html\">Players</a>, <a href=\"../services/world.html\">World</a>, <a href=\"../services/registry.html\">Registry</a>, or browse the <a href=\"../api/lua.html\">Lua API inventory</a>. The generated docs are local HTML and can be opened offline.</p>")
          .append("</section>");
       return html.toString();
    }

    private static String renderGettingStartedContributions(List<MLuaDocBuilder.Contribution> contributions) {
       StringBuilder html = new StringBuilder();
       for (MLuaDocBuilder.Contribution contribution : contributions) {
          if (!MLuaDocBuilder.isGettingStartedSection(contribution.section())) continue;
          appendContributionBody(html, contribution, true);
       }
       return html.toString();
    }

   /** Renders every registered guide with the same section, step, example, and template layout. */
   private static void appendContributionBody(StringBuilder html, MLuaDocBuilder.Contribution contribution,
                                               boolean includeTitle) {
      if (includeTitle) {
         html.append("<h2>").append(htmlEscape(contribution.title())).append("</h2><p>")
            .append(htmlEscape(contribution.description())).append("</p>");
      }
      int number = 1;
      for (MLuaDocBuilder.GuideStep step : contribution.steps()) {
         html.append("<section class=\"tutorial-step\"><h3>Step ").append(number++).append(": ")
            .append(htmlEscape(step.title())).append("</h3><p>").append(htmlEscape(step.explanation()))
            .append("</p>").append(codeBlock(step.example())).append("</section>");
      }
      for (MLuaDocBuilder.Example example : contribution.examples()) {
         html.append("<section class=\"tutorial-step\"><h3>").append(htmlEscape(example.title()))
            .append("</h3><p>").append(htmlEscape(example.description())).append("</p>")
            .append(codeBlock(example.code())).append("</section>");
      }
      appendTemplates(html, contribution.templates());
   }

    private static void writeContributionGuides(Path docs, List<MLuaDocBuilder.Contribution> contributions) throws IOException {
       for (Map.Entry<String, List<MLuaDocBuilder.Contribution>> section : contributionSections(contributions).entrySet()) {
          if (MLuaDocBuilder.isGettingStartedSection(section.getValue().get(0).section())) continue;
          MLuaDocBuilder.Contribution first = section.getValue().get(0);
          String title = docSectionTitle(first.section());
          StringBuilder body = new StringBuilder();
          for (MLuaDocBuilder.Contribution contribution : section.getValue()) {
             appendContributionBody(body, contribution, true);
          }
          String current = contributionSectionPath(section.getKey());
          Path target = docs.resolve(current + ".html");
          write(target, page(title + " - MLua", sidebar(current),
             content(title, "Guides contributed by installed addons.", null, body.toString())));
       }
    }

    private static Map<String, List<MLuaDocBuilder.Contribution>> contributionSections(
          List<MLuaDocBuilder.Contribution> contributions) {
       Map<String, List<MLuaDocBuilder.Contribution>> sections = new java.util.TreeMap<>();
       for (MLuaDocBuilder.Contribution contribution : contributions) {
          sections.computeIfAbsent(MLuaDocBuilder.sectionKey(contribution.section()), ignored -> new ArrayList<>())
             .add(contribution);
       }
       return sections;
    }

    private static String contributionSectionPath(String sectionKey) {
       return "guides/contributed-" + sectionKey;
    }

    private static void appendTemplates(StringBuilder html, List<MLuaDocBuilder.Template> templates) {
       if (templates.isEmpty()) return;
       html.append("<h2>Script templates</h2>");
       for (MLuaDocBuilder.Template template : templates) {
          html.append("<section class=\"tutorial-step\"><h3>").append(htmlEscape(template.title()))
             .append("</h3><p>").append(htmlEscape(template.description())).append("</p><p>Save as <code>")
             .append(htmlEscape(template.filename())).append("</code>.</p>")
             .append(codeBlock(template.code())).append("</section>");
       }
    }

    private static void clearRegisteredAddonDocs(Path guides) throws IOException {
       if (!Files.isDirectory(guides)) return;
       try (var files = Files.list(guides)) {
          for (Path file : files.filter(Files::isRegularFile)
             .filter(path -> path.getFileName().toString().startsWith("registered-addon-")
                && path.getFileName().toString().endsWith(".html")).toList()) {
             Files.deleteIfExists(file);
          }
       }
    }

    private static void clearRegisteredGuidePages(Path guides) throws IOException {
       if (!Files.isDirectory(guides)) return;
       try (var files = Files.list(guides)) {
          for (Path file : files.filter(Files::isRegularFile)
             .filter(path -> path.getFileName().toString().startsWith("contributed-")
                && path.getFileName().toString().endsWith(".html")).toList()) {
             Files.deleteIfExists(file);
          }
       }
    }

   private static String docSectionTitle(String section) {
      StringBuilder title = new StringBuilder();
      for (String word : section.replaceAll("([a-z])([A-Z])", "$1 $2").split("[\\s_.-]+")) {
         if (word.isBlank()) continue;
         if (!title.isEmpty()) title.append(' ');
         title.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
      }
      return title.isEmpty() ? "Addon Guides" : title.toString();
   }

   private static String modIntegrationContent() {
      String var10003 = codeBlock("[[dependencies.yourmodid]]\nmodId=\"mlua\"\nmandatory=true\nversionRange=\"[0.2.0,)\"\nordering=\"AFTER\"\nside=\"BOTH\"");
      String gradleGroovy = codeBlock("repositories {\n    maven { url = uri(\"https://maven.pkg.github.com/encdata/mlua\") }\n}\n\ndependencies {\n    compileOnly fg.deobf(\"dev.mlua:mlua-api:0.2.0\")\n    runtimeOnly fg.deobf(\"dev.mlua:mlua:0.2.0\")\n}");
      String gradleKts = codeBlock("repositories {\n    maven(\"https://maven.pkg.github.com/encdata/mlua\")\n}\n\ndependencies {\n    compileOnly(fg.deobf(\"dev.mlua:mlua-api:0.2.0\"))\n    runtimeOnly(fg.deobf(\"dev.mlua:mlua:0.2.0\"))\n}");
      return content("Mod Integration", "Guide for Forge mod developers who want to integrate with MLua.", (String)null, "<h2>Dependency Setup</h2><p>Add MLua as a runtime dependency in your <code>mods.toml</code>:</p>" + var10003 + "<p>For Java compilation, depend on the published <code>mlua-api</code> artifact. The Forge runtime dependency remains the <code>mlua</code> mod.</p><h3>Gradle (Groovy DSL)</h3>" + gradleGroovy + "<h3>Gradle (Kotlin DSL)</h3>" + gradleKts + callout("info", "Use <code>compileOnly</code> for <code>mlua-api</code> so your addon compiles against the API jar without bundling it. At game runtime, Forge should load the real <code>mlua</code> mod jar.") + callout("warn", "<code>MLuaApi</code> requires the MLua runtime bridge installed by the Forge mod <code>mlua</code>. If that mod is not loaded, <code>MLuaApi</code> calls throw an exception instead of acting as a no-op.") + "<h2>API Compatibility</h2><p><code>mlua-api</code> is the stable Java integration surface for addon authors. The following packages are intended to remain source-compatible across patch releases unless explicitly documented otherwise:</p><ul><li><code>dev.mlua.api.*</code></li><li><code>dev.mlua.core.MLuaContextView</code></li><li><code>dev.mlua.core.service.*</code></li><li><code>dev.mlua.module.MLuaModule</code> and <code>dev.mlua.module.MLuaDocBuilder</code></li><li><code>dev.mlua.common.enums.*</code> used by the public API</li></ul><p>Behavior may still expand between releases, but breaking signature or package changes to those types should be treated as API-version changes. Runtime internals under other packages are not stable addon API and may change without notice.</p><h2>Bundling Scripts</h2><p>Place MLua scripts in your mod jar under <code>mlua/scripts/</code> at the jar root. MLua discovers them automatically.</p><h2>Java API</h2><p>Register content from Java in your mod constructor:</p>" + codeBlock("MLuaApi.registerItem(\"yourmodid\", \"ruby\", () -> new Item(new Item.Properties()));\nMLuaApi.registerBlock(\"yourmodid\", \"ruby_block\", () ->\n    new Block(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK))\n);\nMLuaApi.registerBlockItem(\"yourmodid\", \"ruby_block\");") + "<h2>Choosing the Right Hook</h2><ul><li><code>MLuaApi.registerService(...)</code>: expose a Lua-facing service that scripts access through <code>Minecraft:GetService(...)</code>.</li><li><code>MLuaApi.registerModule(...)</code>: advanced integration that contributes globals, services, listeners, or phase-aware behavior.</li><li><code>MLuaApi.registerGlobal(...)</code>: simple constants or helper values available without <code>GetService</code>.</li></ul><h2>Custom Services</h2><p>Register a custom Lua-accessible service. This is the recommended way to expose your mod's API to MLua:</p>" + codeBlock("MLuaApi.service(\"YourMod\", context -> new YourModLuaService(context))\n" +
         "    .description(\"Provides APIs for YourMod.\")\n" +
         "    .function(\"GetStatus\", \"Returns the current addon status.\")\n" +
         "    .function(\"SetMode\", \"Changes the addon mode.\")\n" +
         "    .register();") + "<h3>Live Properties</h3><p>If your service class extends <code>MLuaReflectiveService</code>, public zero-argument <code>getX()</code> methods and one-argument <code>setX(value)</code> methods become live Lua properties. The existing <code>GetX()</code> and <code>SetX(value)</code> form is also accepted:</p>" + codeBlock("-- Java\npublic class YourModService extends MLuaReflectiveService {\n    public String getStatus() { return \"Alive\"; }\n    public void setMode(String mode) { ... }\n}\n\n-- Lua\nlocal YourMod = Minecraft:GetService(\"YourMod\")\nprint(YourMod.Status) -- \"Alive\"\nYourMod.Mode = \"Custom\"") + "<h2>Custom Globals</h2><p>Expose a simple value or table as a global variable available in all scripts:</p>" + codeBlock("MLuaApi.registerGlobal(\"yourmodid\", \"YourGlobal\", LuaValue.valueOf(\"Hello\"));") + "<h2>Additional Script Roots</h2>" + codeBlock("MLuaApi.registerScriptRoot(\"yourmodid\", FMLPaths.GAMEDIR.get().resolve(\"mlua/scripts/yourmodid\"));") + callout("warn", "If you register an additional script root after registry scripts have already run, <code>.registry.lua</code> files in that root will not execute until the next load cycle.") + callout("tip", "MLua scripts are trusted. Expose APIs intentionally to avoid crashes or security issues."));
   }

   private static String addonsGuideContent() {
      return content("Addon API", "Learn how to extend MLua with custom Java-side services and globals.", (String)null,
         "<h2>Setup</h2>" +
         "<p>At compile time, depend on <code>mlua-api</code>. At runtime, your addon should still require the <code>mlua</code> Forge mod through <code>mods.toml</code>.</p>" +
         codeBlock("dependencies {\n    compileOnly fg.deobf(\"dev.mlua:mlua-api:0.2.0\")\n    runtimeOnly fg.deobf(\"dev.mlua:mlua:0.2.0\")\n}") +
         callout("warn", "<code>MLuaApi</code> only works after the Forge mod <code>mlua</code> installs its runtime bridge. Without that runtime mod present, <code>MLuaApi</code> calls throw an exception.") +
         "<h2>Compatibility Policy</h2>" +
         "<p><code>mlua-api</code> is the only supported Java integration surface for third-party addons. Addon code should avoid reaching into MLua runtime internals outside that API layer.</p>" +
         "<ul>" +
         "<li>Stable target: <code>dev.mlua.api.*</code>, <code>dev.mlua.core.MLuaContextView</code>, <code>dev.mlua.core.service.*</code>, <code>dev.mlua.module.*</code>, and public enums used by those types.</li>" +
         "<li>Unstable internals: runtime implementation packages such as registry internals, UI internals, bindings, transformers, and other non-API packages.</li>" +
         "<li>Rule of thumb: if an addon can be written against <code>mlua-api</code> alone, that usage is intended to be supported across patch releases.</li>" +
         "</ul>" +
         "<h2>Contribute Documentation from Lua</h2>" +
         "<p>Registry scripts can add ordered tutorial steps, examples, or reusable script templates to Getting Started, or publish a guide. Contributions that use the same section name appear together on one generated guide page. Each contribution is owned by the current mod, so its id must be unique within that mod. Write the section as <code>Getting Started</code>, <code>getting-started</code>, or <code>getting_started</code> to add it to the shared tutorial; other section names create their own guide page.</p>" +
         codeBlock("Docs:Register({\n" +
                   "    id = \"horror_intro\",\n" +
                   "    section = \"getting-started\",\n" +
                   "    title = \"Make a scripted encounter\",\n" +
                   "    description = \"Add a server script that tracks a player and reacts to nearby sounds.\",\n" +
                   "    order = 20,\n" +
                   "    steps = {\n" +
                   "        {\n" +
                   "            title = \"Create the server script\",\n" +
                   "            description = \"Server scripts run with access to authoritative world state.\",\n" +
                   "            example = \"-- horror.server.lua\\nlocal World = Minecraft:GetService('World')\\nprint('Encounter ready')\"\n" +
                   "        }\n" +
                   "    },\n" +
                   "    examples = {\n" +
                   "        { title = \"Play a cue\", description = \"Send a short warning to one player.\", code = \"player:SendMessage('You hear something nearby')\" }\n" +
                   "    },\n" +
                   "    templates = {\n" +
                   "        { title = \"Server encounter starter\", description = \"A server script entry point for an encounter.\", filename = \"encounter.server.lua\", code = \"local Players = Minecraft:GetService('Players')\\n\\nPlayers.PlayerAdded:Connect(function(player)\\n    player:SendMessage('The encounter is watching...')\\nend)\" }\n" +
                   "    }\n" +
                   "})") +
         "<p>Use another section such as <code>Horror</code> to publish a guide page. Contributions in the same section are combined, ordered by their <code>order</code> value, and rendered using the same steps, examples, and templates. API reference pages are discovered separately from documented class, service, enum, and global registrations.</p>" +
         "<h2>Contribute Documentation from a Java Module</h2>" +
         "<p>Modules can add the same kind of guide pages from <code>contributeDocs</code>. The contribution builder keeps the guide content together and uses the module id as its owner namespace.</p>" +
         codeBlock("import dev.mlua.module.MLuaDocBuilder;\n" +
                   "import dev.mlua.module.MLuaModule;\n\n" +
                   "public final class EncounterModule implements MLuaModule {\n" +
                   "    @Override public String id() { return \"horror\"; }\n\n" +
                   "    @Override\n" +
                   "    public void contributeDocs(MLuaDocBuilder docs) {\n" +
                   "        docs.contribution(MLuaDocBuilder.Contribution.builder(id(), \"horror:encounter\")\n" +
                   "            .section(\"Horror\")\n" +
                   "            .title(\"Build an encounter\")\n" +
                   "            .description(\"Add a scripted event that reacts to players.\")\n" +
                   "            .order(10)\n" +
                   "            .step(\"Players\", \"Listen for players\",\n" +
                   "                \"Connect to PlayerAdded before adding encounter behavior.\",\n" +
                   "                \"local Players = Minecraft:GetService('Players')\\n\\nPlayers.PlayerAdded:Connect(function(player)\\n    print(player.Name)\\nend)\")\n" +
                   "            .template(\"Server starter\", \"A server-side entry point.\",\n" +
                   "                \"encounter.server.lua\", \"local Players = Minecraft:GetService('Players')\")\n" +
                   "            .build());\n" +
                   "    }\n" +
                   "}") +
         "<h2>Naming Conventions</h2>" +
         "<p>To avoid collisions with other mods, always use your <strong>Mod ID</strong> as the service name or as a prefix. For example, if your mod ID is <code>vs2</code>, name your service <code>VS2</code> or <code>vs2</code>.</p>" +
         "<h2>Extend a Built-In Class</h2>" +
         "<p>Add a method to a class MLua already registers when every live instance needs the new capability. Register it during <code>registerClasses</code> and declare the owning module as a dependency. This example adds <code>Player:GetProfileLabel()</code> to existing players and players created later.</p>" +
         codeBlock("public final class ProfileAddon implements MLuaModule {\n" +
                   "    @Override public String id() { return \"profile_addon\"; }\n" +
                   "    @Override public List<String> dependencies() { return List.of(\"players\"); }\n\n" +
                   "    @Override\n" +
                   "    public void registerClasses(MLuaRegistration registration) {\n" +
                   "        registration.registerClassFunction(\"Player\", \"GetProfileLabel\",\n" +
                   "            new VarArgFunction() {\n" +
                   "                @Override public Varargs invoke(Varargs args) {\n" +
                   "                    LuaValue player = args.arg1();\n" +
                   "                    LuaValue name = player.get(\"GetDisplayName\").call(player);\n" +
                   "                    return LuaValue.valueOf(name.tojstring() + \" [profile]\");\n" +
                   "                }\n" +
                   "            },\n" +
                   "            \"Returns this player's display name with the profile addon label.\");\n" +
                   "    }\n" +
                   "}\n\n" +
                   "// During addon initialization:\n" +
                   "MLuaApi.registerModule(new ProfileAddon());") +
         "<p>Lua scripts call the extension like a built-in method:</p>" +
         codeBlock("Players.PlayerAdded:Connect(function(player)\n" +
                   "    print(player:GetProfileLabel())\n" +
                   "end)") +
         "<p>Use <code>registerClassWithParents</code> for base classes and <code>registerInstantiableClass</code> when an addon class should create live instances with <code>Class.new(fields)</code>. The generic constructor applies the initial fields, then runs each class's <code>Init(self, fields)</code> from base to derived. An applied mixin's Init is used when that class does not define its own. The constructor is itself registered with a description. Use <code>registerClassFunction</code> to add behavior to an existing class such as <code>Player</code>, <code>Entity</code>, <code>Item</code>, or <code>Inventory</code>. For reusable behavior that should not affect <code>IsA</code>, use <code>registerBehaviorMixin</code>, <code>registerBehaviorMixinFunction</code>, and <code>applyBehaviorMixin</code>. A class can define a method with the same name as a mixin method to override it; two applied mixins cannot supply the same method, and class property conflicts are rejected. Registry scripts can remove their own runtime contributions with <code>UnregisterClassFunction</code>, <code>UnregisterServiceFunction</code>, <code>UnregisterBehaviorMixinFunction</code>, and <code>RemoveBehaviorMixin</code>; function removal can pass the expected function value to avoid removing a replacement installed by another addon.</p>" +
         codeBlock("registration.registerInstantiableClass(\"EncounterState\", \"Instance\",\n" +
                   "    \"Stores live state for one scripted encounter.\");\n" +
                   "registration.registerClassFunction(\"EncounterState\", \"Init\",\n" +
                   "    new VarArgFunction() {\n" +
                   "        @Override public Varargs invoke(Varargs args) {\n" +
                   "            return LuaValue.NONE;\n" +
                   "        }\n" +
                   "    },\n" +
                   "    \"Initializes a new encounter state from its fields.\");") +
         "<h2>Register Lua Classes</h2>" +
         "<p>Registry scripts use the shared runtime class registry. <code>RegisterClass</code> accepts one superclass as a name, a registered class table, or a one-entry parent list. Lua class inheritance follows Java's single-superclass model; use behavior mixins to compose shared behavior without changing <code>IsA</code>. Instantiable classes that do not inherit from <code>Instance</code> receive it as their base class automatically. Use <code>GetClasses()</code> to enumerate registered class names or <code>GetClass(name)</code> to retrieve one class table. New Lua-authored, non-service classes without an inherited constructor receive a class-specific <code>Class.new(fields)</code> constructor that creates a live instance and calls its inherited <code>Init(self, fields)</code> method; subclasses get their own constructor. Replace that generated constructor with <code>RegisterClassFunction(className, \"new\", implementation, description)</code> when a class needs custom creation behavior. Existing native constructors stay inherited. Lua UI subclasses can inherit from registered controls and initialize extra behavior through <code>Init(self, fields)</code>. Java addon modules that provide their own renderable UI implementation can register it with <code>MLuaUIBindings.registerNativeClass</code>, supplying the Java factory, class description, and registered parent class name. <code>Minecraft:CreateInstance(class, fields)</code> remains available when code needs an explicit factory call. Registered methods are inherited by existing and future live instances, and their descriptions stay with the class metadata. Safe class removal waits until no live instances or child classes depend on the class; direct <code>UnregisterClass</code> remains available when a registry script intentionally needs to detach them immediately. Use <code>RegisterServiceClass(serviceName, description[, methodDescriptions])</code> or <code>RegisterServiceClass(serviceName, className, description[, methodDescriptions])</code> to register a described service class. A method description table registers instance dispatchers that call the corresponding method on the live service instance. Use <code>RegisterServiceFunction(serviceName, functionName, description)</code> to add one dispatcher, or <code>RegisterServiceClassFunction(serviceName, functionName, function, description)</code> to register a shared Lua implementation and its description on the service class; pass <code>serviceName, className, functionName, function, description</code> when selecting the class explicitly. A Lua-backed service runs its documented <code>Init(self, fields)</code> methods from base to derived once when its live instance is first created, with an empty fields table; that instance is shared across scripts on the same runtime side. <code>Minecraft:GetServiceNames()</code> lists services available to the current side. The four-argument <code>RegisterServiceFunction</code> form also accepts a Lua implementation and description. Remove these methods with <code>UnregisterServiceFunction(serviceName, functionName[, expectedFunction])</code>; when registering on a custom service class, pass <code>serviceName, className, functionName[, expectedFunction]</code>. The expected function protects a replacement installed by another addon.</p>" +
         codeBlock("Minecraft:RegisterServiceClass(\"Horror\", \"HorrorService\", \"Shared horror state and behavior for this runtime side.\")\n\n" +
                   "Minecraft:RegisterServiceClassFunction(\"Horror\", \"Init\", function(self)\n" +
                   "    self.Fear = 0\n" +
                   "end, \"Initializes the shared Horror service state.\")\n\n" +
                   "Minecraft:RegisterServiceClassFunction(\"Horror\", \"GetFear\", function(self)\n" +
                   "    return self.Fear or 0\n" +
                   "end, \"Returns the fear level stored by the shared Horror service instance.\")\n\n" +
                   "local Horror = Minecraft:GetService(\"Horror\")\n" +
                   "print(Horror:GetFear())") +
         "<p>Register live class properties with <code>RegisterClassProperty(className, propertyName, getter, setter, description)</code>. The getter and setter receive the instance as their first argument. Pass <code>nil</code> for a read-only setter; property reads and writes on existing instances use the registered accessors. Add type and side metadata with <code>RegisterClassProperty(className, propertyName, getter, setter, valueType, side, description)</code>; omit the setter for a read-only property. Use a Lua type name such as <code>Entity</code> and a side label such as <code>shared:Shared</code>, <code>server:Server</code>, or <code>client:Client</code>. Properties cannot replace methods or fields that already exist on live instances, and are inherited by subclasses.</p>" +
         codeBlock("Minecraft:RegisterClass(\"FearState\", \"Instance\", \"Stores state for a scripted fear encounter.\")\n\n" +
                   "Minecraft:RegisterClassProperty(\"FearState\", \"Target\",\n" +
                   "    function(self) return self.target end,\n" +
                   "    function(self, value) self.target = value end,\n" +
                   "    \"Entity\", \"shared:Shared\",\n" +
                   "    \"Gets or changes the entity this encounter is tracking.\")\n\n" +
                   "Minecraft:RegisterClassFunction(\"FearState\", \"Init\", function(self, fields)\n" +
                   "    self.Target = fields.Target\n" +
                   "    self.Alerted = false\n" +
                   "end, \"Initializes this encounter state from its constructor fields.\")\n\n" +
                   "Minecraft:RegisterClassFunction(\"FearState\", \"SetTarget\", function(self, target)\n" +
                   "    self.Target = target\n" +
                   "end, \"Sets the entity this encounter is tracking.\")\n\n" +
                   "local FearState = Minecraft:GetClass(\"FearState\")\n" +
                   "local state = FearState.new({ Target = nil })\n" +
                   "state:SetTarget(nil)") +
         "<p><code>Class.new(fields)</code> and <code>CreateInstance(class, fields)</code> both create a Lua-side instance, copy its initial fields, then call class and mixin <code>Init(self, fields)</code> methods from base to derived, once per class level. An applied mixin's Init is used only when the class has no own Init. Live instances cannot shadow registered methods with fields, including after construction; register shared behavior on the class or a mixin. Fields named <code>__type</code>, <code>__class</code>, or beginning with <code>__mlua_</code> are reserved for runtime metadata. Use game services such as <code>Entities</code> or <code>World</code> when you need to spawn or control a Minecraft entity.</p>" +
         "<h3>Share Behavior with Behavior Mixins</h3>" +
         "<p>Behavior mixins add reusable Lua methods without changing class ancestry. A class's own method wins over an applied mixin method; mixin methods win over inherited parent methods. Applying two mixins that provide the same method fails with a conflict naming the class and mixins, so addons must choose one implementation or give their methods distinct names. This registry feature is separate from <code>Minecraft:GetService(\"Mixin\")</code>, which transforms Java classes.</p>" +
         "<p>Register mixin methods and their descriptions together with the third argument to <code>RegisterBehaviorMixin</code>. The older <code>RegisterBehaviorMixinFunction</code> call remains useful when a mixin is assembled in separate registry scripts. Applying a mixin to a base class also makes it available to subclasses.</p>" +
         codeBlock("Minecraft:RegisterBehaviorMixin(\"DamageableBehavior\", \"Adds damage tracking to Lua class instances.\", {\n" +
                   "    ApplyDamage = {\n" +
                   "        implementation = function(self, amount)\n" +
                   "            self.Health = math.max(0, (self.Health or 100) - amount)\n" +
                   "        end,\n" +
                   "        description = \"Reduces this instance's health by an amount.\"\n" +
                   "    }\n" +
                   "})\n" +
                   "Minecraft:ApplyBehaviorMixin(\"FearState\", \"DamageableBehavior\")\n\n" +
                   "local state = Minecraft:CreateInstance(\"FearState\", { Health = 100 })\n" +
                   "state:ApplyDamage(12)\n" +
                   "assert(not state:IsA(\"DamageableBehavior\"))") +
         "<h3>Transform a Java Class from Lua</h3>" +
         "<p><code>Minecraft:GetService(\"Mixin\")</code> declares bytecode changes to Java classes. Put declarations in a <code>.mixin.lua</code>, <code>.client.mixin.lua</code>, or <code>.server.mixin.lua</code> script; the script runs during mixin registration before normal gameplay scripts. The loader maps the target names and the shared transformer applies the rule. This is separate from <code>RegisterBehaviorMixin</code>, which adds methods to MLua's Lua-side classes.</p>" +
         "<p>Start with a target class and method from the mixin completions for the Minecraft version you are supporting. If a class has multiple methods with that name, set the exact JVM <code>descriptor</code>; otherwise registration fails with the matching signatures.</p>" +
         codeBlock("-- example.client.mixin.lua\n" +
                   "local Mixin = Minecraft:GetService(\"Mixin\")\n\n" +
                   "Mixin:Target(\"net.minecraft.client.Minecraft\"):Inject(\"tick\", {\n" +
                   "    descriptor = \"()V\",\n" +
                   "    at = \"HEAD\",\n" +
                   "    callback = function(client)\n" +
                   "        -- Runs at the start of Minecraft.tick().\n" +
                   "    end\n" +
                   "})") +
         "<p>Use <code>Inject</code> for HEAD or RETURN callbacks, <code>Overwrite</code> to replace a method, and <code>Redirect</code> or <code>ModifyArg</code> to target an exact invocation descriptor. Keep target names and descriptors scoped to the Minecraft versions your addon supports.</p>" +
         "<h2>Creating an Addon Service</h2>" +
         "<p>The easiest way to expose your mod's API is by extending <code>MLuaReflectiveService</code>. This base class automatically exposes all public methods to Lua.</p>" +
         "<h3>1. Define the Service Class</h3>" +
         "<p>Create a class that extends <code>dev.mlua.core.service.MLuaReflectiveService</code>. Methods starting with <code>Get</code> or <code>Set</code> will be automatically converted to live properties.</p>" +
         codeBlock("public class MyAddonService extends MLuaReflectiveService {\n" +
                   "    private String status = \"Initial\";\n" +
                   "\n" +
                   "    public MyAddonService(MLuaService.Context context) {\n" +
                   "        super(context);\n" +
                   "    }\n" +
                   "\n" +
                   "    @Override\n" +
                   "    public String name() {\n" +
                   "        return \"MyAddon\";\n" +
                   "    }\n" +
                   "\n" +
                   "    // Exposes property MyAddon.Status\n" +
                   "    public String GetStatus() {\n" +
                   "        return this.status;\n" +
                   "    }\n" +
                   "\n" +
                   "    public void SetStatus(String value) {\n" +
                   "        this.status = value;\n" +
                   "    }\n" +
                   "\n" +
                   "    // Exposes method MyAddon:PerformAction(arg)\n" +
                   "    public void PerformAction(String arg) {\n" +
                   "        System.out.println(\"Action: \" + arg);\n" +
                   "    }\n" +
                   "}") +
         "<h3>2. Register the Service</h3>" +
         "<p>Register a service description and the description for each Lua function in the same registration call. The function names must match functions exposed by <code>toLua()</code>:</p>" +
         codeBlock("MLuaApi.service(\"MyAddon\", context -> new MyAddonService(context))\n" +
                   "    .description(\"Provides profile data and addon actions.\")\n" +
                   "    .function(\"GetStatus\", \"Returns the current addon status.\")\n" +
                   "    .function(\"SetStatus\", \"Changes the addon status.\")\n" +
                   "    .function(\"PerformAction\", \"Performs the named addon action.\")\n" +
                   "    .register();") +
         "<p>For a custom <code>MLuaModule</code>, put the same descriptions in its <code>ServiceEntry</code>. A service entry needs a service description and at least one described function; building an empty function contract fails immediately. Registration installs described methods on the service class; each call dispatches to the method on that context's live service instance. Choose the available game side with <code>.side(Side.CLIENT)</code> or <code>.side(Side.SERVER)</code> on the fluent declaration; services default to both sides. To choose a Lua class name different from the service lookup name, set <code>.className(&quot;AddonService&quot;)</code> before adding documented functions.</p>" +
         "<p>For a Java list property, declare the element type in Java and assign a dense Lua sequence: <code>SetFavorites(List&lt;String&gt; names)</code> accepts <code>MyAddon.Favorites = {\"torch\", \"key\"}</code>. The same table can be passed to an ordinary method such as <code>MyAddon:SaveFavorites({\"torch\", \"key\"})</code>; Java arrays work the same way. A Lua map table such as <code>{volume = 0.7}</code> converts to <code>Map&lt;String, Double&gt;</code>. Nested collection element types are preserved when the Java parameter uses generic types such as <code>List&lt;ItemSpec&gt;</code> or <code>Map&lt;String, List&lt;String&gt;&gt;</code>. Gaps or non-sequence keys are rejected for Java lists. Java list/array results become 1-based Lua tables, and maps become keyed tables. Unsupported Java result objects fail with their type name; register an exact adapter with <code>MLuaApi.registerLuaAdapter</code> to expose addon-owned types. Close its returned handle when the adapter should be removed. Service-specific conversions can still override <code>toJavaValue</code> or <code>toLuaValue</code>.</p>" +
         codeBlock("return List.of(new MLuaModule.ServiceEntry(\n" +
                   "    \"MyAddon\", MyAddonService::new,\n" +
                   "    \"Provides profile data and addon actions.\",\n" +
                   "    Map.of(\n" +
                   "        \"GetStatus\", \"Returns the current addon status.\",\n" +
                   "        \"SetStatus\", \"Changes the addon status.\",\n" +
                   "        \"PerformAction\", \"Performs the named addon action.\"))); ") +
         "<h3>3. Use in Lua</h3>" +
         "<p>Now you can access your service from any MLua script:</p>" +
         codeBlock("local MyAddon = Minecraft:GetService(\"MyAddon\")\n" +
                   "\n" +
                   "-- Access properties directly (no parenthesis needed)\n" +
                   "print(MyAddon.Status) -- Calls GetStatus()\n" +
                   "MyAddon.Status = \"Running\" -- Calls SetStatus(\"Running\")\n" +
                   "\n" +
                   "-- Call methods\n" +
                   "MyAddon:PerformAction(\"Hello from Lua!\")") +
         "<h2>Choosing the Right Hook</h2>" +
         "<ul>" +
         "<li><code>registerService</code>: expose a Lua-facing API as a named service.</li>" +
         "<li><code>registerModule</code>: advanced phase-aware integration, additional globals, listeners, or coordinated setup.</li>" +
         "<li><code>registerGlobal</code>: small constants or helper values that belong directly in the global scope.</li>" +
         "</ul>" +
         "<h2>Registering Globals</h2>" +
         "<p>If you want to put something directly in the global scope (available without <code>GetService</code>), use <code>registerGlobal</code>. You can filter globals by side (Client/Server) or by script type (e.g. only in <code>.mixin.lua</code> files).</p>" +
         codeBlock("// Global everywhere\n" +
                   "MLuaApi.registerGlobal(\"my_mod\", \"VERSION\", LuaValue.valueOf(\"1.0.0\"));\n\n" +
                   "// Global only on Client\n" +
                   "MLuaApi.registerGlobal(\"my_mod\", \"IS_CLIENT\", LuaValue.TRUE, Side.CLIENT);\n\n" +
                   "// Global only in Mixin scripts\n" +
                   "MLuaApi.registerGlobal(\"my_mod\", \"MIXIN_HELPER\", myHelperTable, Side.BOTH, ScriptType.MIXIN);") +
         "<h2>Using Java Context</h2>" +
         "<p>When your service is created, it receives an <code>MLuaService.Context</code>. This provides access to the public <code>MLuaContextView</code>, which is the stable execution-context contract for addons and avoids exposing runtime-only internals.</p>" +
         codeBlock("public MyAddonService(MLuaService.Context context) {\n" +
                   "    super(context);\n" +
                   "    MLuaContextView mlua = context.mlua();\n" +
                   "\n" +
                   "    boolean client = mlua.isClient();\n" +
                   "    boolean server = mlua.isServer();\n" +
                   "    boolean registry = mlua.isPhase(RegistrationPhase.REGISTRY);\n" +
                   "    String modId = mlua.ownerModId();\n" +
                   "}") +
         "<table>" +
         "<tr><th>Method</th><th>Description</th></tr>" +
         "<tr><td><code>isClient()</code> / <code>isServer()</code></td><td>Check the current physical side.</td></tr>" +
         "<tr><td><code>isRegistration()</code></td><td>True if currently in Mixin, Registry, or Commands phase.</td></tr>" +
         "<tr><td><code>isPhase(RegistrationPhase)</code></td><td>Check for a specific boot phase.</td></tr>" +
         "<tr><td><code>isScriptType(ScriptType)</code></td><td>Check if the current script is a Mixin, Server, Client, or Registry script.</td></tr>" +
         "<tr><td><code>ownerModId()</code></td><td>The ID of the mod that owns the Lua script currently being executed.</td></tr>" +
         "<tr><td><code>server()</code></td><td>Access the <code>MinecraftServer</code> instance. This is only meaningful during server-side execution.</td></tr>" +
         "<tr><td><code>filesRoot()</code></td><td>Get the unique persistent data path for the current mod: <code>run/mlua/data/&lt;modid&gt;/</code>. Use this for config files, caches, or per-mod logs.</td></tr>" +
         "</table>" +
         "<h2>Advanced: Custom Modules</h2>" +
         "<p>For more complex integrations (e.g. contributing to multiple phases, adding event listeners), implement the <code>MLuaModule</code> interface and register it via <code>MLuaApi.registerModule(module)</code>.</p>" +
         callout("tip", "Always use PascalCase (e.g. <code>GetSomething</code>) for methods if you want them to behave like properties (<code>Something</code>) in Lua.")
      );
   }

   private static String ctx(String code, String related) {
      String r = "";
      String var10000;
      if (related != null) {
         var10000 = related.substring(0, 1).toUpperCase();
         String cap = var10000 + related.substring(1);
         r = "<p>Type: <a href=\"../classes/" + related + ".html\">" + cap + "</a></p>";
      }

      String var10001 = code.replace("&", "&amp;").replace("<", "&lt;");
      var10000 = callout("info", "Obtain via: " + var10001.replace(">", "&gt;"));
      return var10000 + r;
   }

    private static String content(String title, String desc, String ctxHtml, String sections) {
       return "<div class=\"content-wrapper\"><div class=\"content\"><h1>" + title + "</h1><p style=\"font-size:18px;color:var(--text-muted);margin-bottom:24px\">" + desc + "</p>" + (ctxHtml != null ? ctxHtml : "") + sections + "</div></div>";
    }

                             }
