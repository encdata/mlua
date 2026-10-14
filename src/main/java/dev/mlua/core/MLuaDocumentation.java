package dev.mlua.core;

import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModuleLoader;
import dev.mlua.core.engine.MLuaLuaApiCrawler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class MLuaDocumentation {
   private static volatile Path currentGameDir;
   private static volatile MLuaDocBuilder currentBuilder;
   private static volatile String generatedInventory = "";
   private static volatile List<String> generatedGlobalNames = List.of();
   private static volatile List<String> generatedEnumNames = List.of();
   private static final Map<String, String> TYPE_ROUTES = new ConcurrentHashMap<>();
   private static final Map<String, String> TYPE_NAMES = new ConcurrentHashMap<>();

   private MLuaDocumentation() { }

   public static synchronized void generate(Path gameDir) {
      currentGameDir = gameDir;
      MLuaExampleScripts.copy(gameDir);
      MLuaDocBuilder builder = new MLuaDocBuilder();
      MLuaModuleLoader.get().generateDocs(builder);
      MLuaRuntime.get().documentationContributions().forEach(contribution ->
         builder.register(MLuaDocBuilder.Registration.contribution(contribution)));
      currentBuilder = builder;
      logWarnings(builder);
      publishSite();
   }

   public static synchronized void writeGeneratedLuaApi(Path gameDir, String inventoryHtml) {
      currentGameDir = gameDir;
      generatedInventory = inventoryHtml == null ? "" : inventoryHtml;
      ensureBuilder();
      publishSite();
   }

   public static synchronized void writeGeneratedLuaApi(Path gameDir,
                                                         dev.mlua.core.engine.MLuaLuaApiDocSnapshot.DocumentationSnapshot snapshot) {
      currentGameDir = gameDir;
      generatedInventory = snapshot == null ? "" : snapshot.inventoryHtml();
      generatedGlobalNames = snapshot == null ? List.of() : snapshot.globalNames();
      generatedEnumNames = snapshot == null ? List.of() : snapshot.enumNames();
      ensureBuilder();
      publishSite();
   }

   public static synchronized void writeRuntimeLuaApi(Path gameDir, String side, String phase,
                                                       String scriptName, String inventoryHtml) {
      writeRuntimeLuaApi(gameDir, side, phase, scriptName,
         new MLuaLuaApiCrawler.CrawlResult(inventoryHtml == null ? "" : inventoryHtml));
   }

   public static synchronized void writeRuntimeLuaApi(Path gameDir, String side, String phase,
                                                       String scriptName, MLuaLuaApiCrawler.CrawlResult crawl) {
      currentGameDir = gameDir;
      String info = "<p>Captured from registered Lua API tables while loading <code>"
         + escape(scriptName) + "</code> on the " + escape(side) + " side during the "
         + escape(phase) + " phase.</p>" + (crawl == null ? "" : crawl.inventoryHtml());
      generatedInventory = info;
      generatedGlobalNames = crawl == null ? List.of() : crawl.globalNames();
      generatedEnumNames = crawl == null ? List.of() : crawl.enumNames();
      ensureBuilder();
      publishSite();
   }

   private static void ensureBuilder() {
      if (currentBuilder != null) return;
      MLuaDocBuilder builder = new MLuaDocBuilder();
      MLuaModuleLoader.get().generateDocs(builder);
      MLuaRuntime.get().documentationContributions().forEach(contribution ->
         builder.register(MLuaDocBuilder.Registration.contribution(contribution)));
      currentBuilder = builder;
      logWarnings(builder);
   }

   private static void logWarnings(MLuaDocBuilder builder) {
      for (String warning : builder.warnings()) MLuaLogger.LOGGER.warn("MLua documentation: {}", warning);
   }

   private static void publishSite() {
      if (currentGameDir == null) return;
      Path docs = currentGameDir.resolve("mlua").resolve("docs");
      try {
         Files.createDirectories(docs);
         MLuaDocumentationSite.write(docs.resolve("index.html"), currentBuilder,
            generatedInventory, iconDataUri());
         MLuaEditorApiGenerator.write(currentGameDir, currentBuilder, generatedGlobalNames, generatedEnumNames);
         removeLegacyHtml(docs);
      } catch (IOException | RuntimeException error) {
         MLuaLogger.LOGGER.warn("Could not generate offline MLua documentation at {}", docs, error);
      }
   }

   private static void removeLegacyHtml(Path docs) throws IOException {
      try (var paths = Files.walk(docs)) {
         for (Path path : paths.filter(Files::isRegularFile)
            .filter(path -> !path.equals(docs.resolve("index.html")))
            .filter(path -> path.getFileName().toString().endsWith(".html")).toList()) {
            Files.deleteIfExists(path);
         }
      }
   }

   private static String iconDataUri() throws IOException {
      try (var icon = MLuaDocumentation.class.getResourceAsStream("/Icon.png")) {
         if (icon == null) throw new IOException("Missing /Icon.png resource");
         return "data:image/png;base64," + Base64.getEncoder().encodeToString(icon.readAllBytes());
      }
   }

   static String linkMemberParts(String signature, String returnType) {
      if (signature == null || signature.contains("</a>")) return signature == null ? "" : signature;
      StringBuilder result = new StringBuilder();
      int split = Math.max(signature.lastIndexOf('.'), signature.lastIndexOf(':'));
      if (split <= 0) return escape(signature);
      String prefix = signature.substring(0, split);
      String rest = signature.substring(split + 1);
      int argsAt = rest.indexOf('(');
      String member = argsAt < 0 ? rest : rest.substring(0, argsAt);
      String args = argsAt < 0 ? "" : rest.substring(argsAt);
      String prefixRoute = routeForType(prefix);
      result.append(prefixRoute == null ? escape(prefix)
         : "<a href=\"" + prefixRoute + "\">" + escape(prefix) + "</a>");
      result.append(escape(signature.substring(split, split + 1)));
      String memberRoute = routeForType(member);
      result.append(memberRoute == null ? escape(member)
         : "<a href=\"" + memberRoute + "\">" + escape(member) + "</a>");
      result.append(escape(args));
      if (returnType != null && !returnType.isBlank()) {
         result.append(" <span class=\"return-type\">→ ").append(linkType(returnType)).append("</span>");
      }
      return result.toString();
   }

   public static String linkType(String type) {
      if (type == null || type.isBlank()) return "";
      StringBuilder out = new StringBuilder();
      String[] parts = type.split("(\\s*\\|\\s*|\\s*,\\s*)", -1);
      for (int index = 0; index < parts.length; index++) {
         if (index > 0) out.append(type.contains("|") ? " | " : ", ");
         String part = parts[index].trim();
         String suffix = part.endsWith("[]") ? "[]" : "";
         String base = suffix.isEmpty() ? part : part.substring(0, part.length() - 2);
         String route = routeForType(base);
         out.append(route == null ? escape(base) : "<a href=\"" + route + "\">" + escape(base) + "</a>")
            .append(suffix);
      }
      return out.toString();
   }

   static String typeHref(String type) { return routeForType(type); }

   static List<String> typeNames() { return List.copyOf(TYPE_NAMES.values()); }

   public static Map<String, String> registeredReturnTypes() {
      MLuaDocBuilder builder = currentBuilder;
      if (builder == null) return Map.of();
      Map<String, String> returns = new LinkedHashMap<>();
      builder.methods().forEach(method -> putReturn(returns, method.signature(), method.returns(), method.receiver()));
      builder.signatures().forEach(signature -> putReturn(returns, signature.signature(), signature.returns(), signature.receiver()));
      for (MLuaDocBuilder.ServiceFunctionDoc function : builder.serviceFunctions()) {
         builder.signatures().stream()
            .filter(signature -> (signature.receiver().equals(function.service())
               || signature.receiver().equals(function.className()))
               && signature.signature().contains(function.name() + "("))
            .findFirst().ifPresent(signature -> putReturn(returns,
               function.service() + "." + function.name() + "()", signature.returns(), function.service()));
      }
      return Map.copyOf(returns);
   }

   private static void putReturn(Map<String, String> returns, String signature, String returnType, String receiver) {
      if (signature == null || returnType == null || returnType.isBlank()) return;
      int open = signature.indexOf('(');
      if (open < 0) return;
      String path = signature.substring(0, open).trim().replace(':', '.');
      if (!path.isBlank()) returns.put(path, returnType);
      int separator = Math.max(path.lastIndexOf('.'), path.lastIndexOf(':'));
      if (separator >= 0 && receiver != null && !receiver.isBlank()) {
         String name = path.substring(separator + 1);
         returns.put(receiver + "." + name, returnType);
      }
   }

   private static String routeForType(String type) {
      if (type == null) return null;
      String route = TYPE_ROUTES.get(type.toLowerCase(Locale.ROOT));
      if (route != null) return route;
      return switch (type.toLowerCase(Locale.ROOT)) {
         case "vector2", "vector3", "cframe", "color3", "udim", "udim2" -> "#/api/types/" + slug(type);
         default -> null;
      };
   }

   static String escape(String value) {
      if (value == null) return "";
      return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
         .replace("\"", "&quot;").replace("'", "&#39;");
   }

   static String slug(String value) {
      return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
   }

   static void replaceTypeRoutes(Map<String, String> routes) {
      TYPE_ROUTES.clear();
      TYPE_NAMES.clear();
      routes.forEach((name, route) -> {
         TYPE_ROUTES.put(name.toLowerCase(Locale.ROOT), route);
         TYPE_NAMES.put(name.toLowerCase(Locale.ROOT), name);
      });
   }
}
