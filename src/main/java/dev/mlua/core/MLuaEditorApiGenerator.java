package dev.mlua.core;

import dev.mlua.module.MLuaDocBuilder;
import java.io.IOException;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

/** Writes the Lua language-server and VS Code sidecars from the same registrations as the docs. */
final class MLuaEditorApiGenerator {
   private MLuaEditorApiGenerator() { }

   static void write(Path gameDir, MLuaDocBuilder builder, List<String> globalNames,
                     List<String> enumNames) throws IOException {
      Path api = gameDir.resolve("mlua").resolve("docs").resolve("api");
      Files.createDirectories(api);
      Files.writeString(api.resolve("mlua.d.lua"), declarations(builder), StandardCharsets.UTF_8);
      List<String> globals = registeredGlobals(builder, globalNames);
      Files.writeString(api.resolve("settings.json"), settings(globals), StandardCharsets.UTF_8);
      Path project = findProjectRoot(gameDir);
      if (project != null) {
         Path extension = project.resolve("vscode-extension").resolve("extension.js");
         String old = Files.readString(extension, StandardCharsets.UTF_8);
         int runtimeStart = old.indexOf("function activate(context)");
         if (runtimeStart >= 0) {
            Files.writeString(api.resolve("extension.js"), extensionData(builder, globals, enumNames)
               + mixinData(scanMinecraftClasses())
               + old.substring(runtimeStart), StandardCharsets.UTF_8);
         }
      }
   }

   private static String declarations(MLuaDocBuilder builder) {
      StringBuilder out = new StringBuilder("-- MLua Lua API\n-- Generated from registered MLua API declarations.\n---@meta\n\n");
      Map<String, MLuaDocBuilder.DocEntry> types = new LinkedHashMap<>();
      for (MLuaDocBuilder.DocEntry entry : builder.entries()) {
         if (entry.type() == MLuaDocBuilder.DocEntryType.CLASS || entry.type() == MLuaDocBuilder.DocEntryType.SERVICE
             || entry.type() == MLuaDocBuilder.DocEntryType.DATA_TYPE) types.putIfAbsent(luaTypeName(entry), entry);
      }
      for (MLuaDocBuilder.TypeDoc type : builder.types()) {
         types.putIfAbsent(type.name(), new MLuaDocBuilder.DocEntry(MLuaDocBuilder.DocEntryType.DATA_TYPE,
            type.name(), type.name(), type.description(), (String) null));
      }
      for (MLuaDocBuilder.DocEntry entry : builder.entries()) {
         if (entry.type() == MLuaDocBuilder.DocEntryType.LIBRARY
             && isReferencedType(entry.name(), builder)) types.putIfAbsent(entry.name(),
                new MLuaDocBuilder.DocEntry(MLuaDocBuilder.DocEntryType.DATA_TYPE, entry.name(), entry.name(), entry.desc(), (String) null));
      }
      for (MLuaDocBuilder.MethodDoc method : builder.methods()) addReferencedType(types, method.returns());
      for (MLuaDocBuilder.SignatureDoc signature : builder.signatures()) addReferencedType(types, signature.returns());
      for (MLuaDocBuilder.MemberDoc member : builder.members()) addReferencedType(types, member.type());
      for (Map.Entry<String, MLuaDocBuilder.DocEntry> pair : types.entrySet()) {
         String name = pair.getKey();
         MLuaDocBuilder.DocEntry entry = pair.getValue();
         String parent = entry.parentClass();
         out.append("---@class ").append(name);
         if (parent != null && !parent.isBlank()) out.append(" : ").append(parent);
         out.append('\n');
         appendDoc(out, entry.desc());
         for (MLuaDocBuilder.FieldDoc field : builder.fields()) {
            if (!field.typeName().equals(entry.name())) continue;
            out.append("---@field ").append(field.name()).append(' ').append(luaType(field.valueType())).append('\n');
            appendDoc(out, field.description());
         }
         for (MLuaDocBuilder.MemberDoc member : builder.members()) {
            if (!member.receiver().equals(entry.name()) || member.kind() != MLuaDocBuilder.MemberKind.PROPERTY) continue;
            out.append("---@field ").append(memberName(member.signature(), entry.name())).append(' ')
               .append(luaType(member.type())).append('\n');
            appendDoc(out, member.description());
         }
         out.append(name).append(" = {}\n");
         Set<String> written = new LinkedHashSet<>();
         for (MLuaDocBuilder.MethodDoc method : builder.methods()) {
            if (!method.receiver().equals(entry.name())) continue;
            appendMethod(out, name, method.signature(), method.returns(), method.description());
            written.add(method.signature());
         }
         for (MLuaDocBuilder.SignatureDoc signature : builder.signatures()) {
            if (!signature.receiver().equals(entry.name()) || written.contains(signature.signature())) continue;
            appendMethod(out, name, signature.signature(), signature.returns(), null);
         }
         if (entry.type() == MLuaDocBuilder.DocEntryType.SERVICE) appendServiceFunctions(out, builder, entry, name);
         if (entry.constants() != null) {
            for (String[] constant : entry.constants()) {
               if (constant.length > 1) out.append("---@type ").append(luaType(constant[1])).append('\n');
               out.append(name).append('.').append(constant[0]).append(" = nil\n");
            }
         }
         out.append('\n');
      }
      appendLibraryDeclarations(out, builder, types);
      appendGetService(out, builder);
      return out.toString();
   }

   private static void appendLibraryDeclarations(StringBuilder out, MLuaDocBuilder builder,
                                                 Map<String, MLuaDocBuilder.DocEntry> types) {
      Set<String> declared = new LinkedHashSet<>(types.keySet());
      Set<String> otherReceivers = new LinkedHashSet<>();
      for (MLuaDocBuilder.MethodDoc method : builder.methods()) {
         if (!declared.contains(method.receiver()) && builder.entries().stream()
             .noneMatch(entry -> entry.type() == MLuaDocBuilder.DocEntryType.LIBRARY
                && entry.name().equals(method.receiver())) && !method.receiver().equals("Globals")) {
            otherReceivers.add(method.receiver());
         }
      }
      for (String receiver : otherReceivers) {
         out.append("---@class ").append(receiver).append('\n').append(receiver).append(" = {}\n\n");
      }
      for (MLuaDocBuilder.MethodDoc method : builder.methods()) {
         MLuaDocBuilder.DocEntry library = builder.entries().stream()
            .filter(entry -> entry.type() == MLuaDocBuilder.DocEntryType.LIBRARY
               && entry.name().equals(method.receiver())).findFirst().orElse(null);
         if (!method.receiver().equals("Globals") && declared.contains(method.receiver())) continue;
         String target = library == null && !method.receiver().equals("Globals") ? method.receiver() : null;
         appendMethod(out, target, method.signature(), method.returns(), method.description());
      }
      for (MLuaDocBuilder.SignatureDoc signature : builder.signatures()) {
         if (declared.contains(signature.receiver()) || signature.receiver().equals("Minecraft")) continue;
         boolean libraryOrGlobal = builder.entries().stream().anyMatch(entry ->
            entry.type() == MLuaDocBuilder.DocEntryType.LIBRARY && entry.name().equals(signature.receiver()))
            || signature.receiver().equals("Globals");
         if (libraryOrGlobal) appendMethod(out, null, signature.signature(), signature.returns(), null);
      }
      out.append('\n');
   }

   private static void appendServiceFunctions(StringBuilder out, MLuaDocBuilder builder,
                                               MLuaDocBuilder.DocEntry entry, String typeName) {
      for (MLuaDocBuilder.ServiceFunctionDoc function : builder.serviceFunctions()) {
         if (!function.service().equals(entry.name())) continue;
         String signature = builder.signatures().stream()
            .filter(item -> (item.receiver().equals(entry.name()) || item.receiver().equals(function.className()))
               && item.signature().contains(function.name() + "("))
            .map(MLuaDocBuilder.SignatureDoc::signature).findFirst().orElse(typeName + ":" + function.name() + "()");
         if (builder.methods().stream().anyMatch(method -> method.signature().equals(signature))
             || builder.signatures().stream().anyMatch(item -> item.signature().equals(signature))) continue;
         String returns = builder.signatures().stream().filter(item -> item.signature().equals(signature))
            .map(MLuaDocBuilder.SignatureDoc::returns).findFirst().orElse(null);
         appendMethod(out, typeName, signature, returns, function.description());
      }
   }

   private static void appendMethod(StringBuilder out, String owner, String signature, String returns, String description) {
      String value = signature == null ? "" : signature;
      int colon = value.lastIndexOf(':');
      int dot = value.lastIndexOf('.');
      int sep = Math.max(colon, dot);
      int open = value.indexOf('(', sep + 1);
      if (open < 0) return;
      int close = value.lastIndexOf(')');
      if (close < open) close = value.length();
      String methodName = value.substring(sep + 1, open).trim();
      if (sep < 0) methodName = value.substring(0, open).trim();
      if (!methodName.matches("[A-Za-z_][A-Za-z0-9_]*")) return;
      String args = value.substring(open + 1, close).trim();
      appendDoc(out, description);
      List<String> parameters = splitTypes(args);
      List<String> luaParameters = new ArrayList<>();
      for (String parameter : parameters) {
         String trimmed = parameter.trim();
         if (trimmed.isEmpty()) continue;
         int typeAt = trimmed.indexOf(':');
         String name = typeAt < 0 ? trimmed : trimmed.substring(0, typeAt).trim();
         String type = typeAt < 0 ? null : trimmed.substring(typeAt + 1).trim();
         boolean optional = name.endsWith("?");
         if (optional) name = name.substring(0, name.length() - 1);
         if (type != null && !type.isEmpty()) {
            if (type.endsWith("?")) { optional = true; type = type.substring(0, type.length() - 1); }
            out.append("---@param ").append(name).append(' ').append(luaType(type));
            if (optional) out.append("|nil");
            out.append('\n');
         }
         luaParameters.add(name);
      }
      if (returns != null && !returns.isBlank() && !returns.equalsIgnoreCase("void")) {
         out.append("---@return ").append(luaType(returns)).append('\n');
      }
      out.append("function ");
      if (sep < 0) out.append(methodName);
      else out.append(owner == null ? value.substring(0, sep) : owner).append(colon > dot ? ':' : '.').append(methodName);
      out.append('(').append(String.join(", ", luaParameters)).append(") end\n");
   }

   private static void appendGetService(StringBuilder out, MLuaDocBuilder builder) {
      out.append("---@class Minecraft\nMinecraft = {}\n");
      for (MLuaDocBuilder.DocEntry entry : builder.entries()) {
         if (entry.type() != MLuaDocBuilder.DocEntryType.SERVICE) continue;
         String serviceType = luaTypeName(entry);
         out.append("---@return ").append(serviceType).append('\n')
            .append("---@param name string\nfunction Minecraft:GetService(name) end\n");
      }
      for (MLuaDocBuilder.SignatureDoc signature : builder.signatures()) {
         if (signature.receiver().equals("Minecraft")) appendMethod(out, "Minecraft", signature.signature(), signature.returns(), null);
      }
   }

   private static String extensionData(MLuaDocBuilder builder, List<String> globalNames, List<String> enumNames) {
      StringBuilder out = new StringBuilder("// Generated from MLua API registrations.\n");
      jsArray(out, "GLOBAL_NAMES", globalNames);
      List<String> minecraftMethods = builder.signatures().stream().filter(s -> s.receiver().equals("Minecraft"))
         .map(s -> methodShape(s.signature())).filter(s -> !s.isBlank()).toList();
      jsArray(out, "MINECRAFT_METHODS", minecraftMethods);
      List<String> serviceNames = builder.entries().stream().filter(e -> e.type() == MLuaDocBuilder.DocEntryType.SERVICE)
         .map(MLuaDocBuilder.DocEntry::name).toList();
      jsArray(out, "SERVICE_NAMES", serviceNames);
      out.append("const SERVICE_METHODS = {\n");
      List<MLuaDocBuilder.DocEntry> services = builder.entries().stream()
         .filter(e -> e.type() == MLuaDocBuilder.DocEntryType.SERVICE).toList();
      for (int i = 0; i < services.size(); i++) {
         MLuaDocBuilder.DocEntry service = services.get(i);
         List<String> methods = new ArrayList<>();
         for (MLuaDocBuilder.ServiceFunctionDoc function : builder.serviceFunctions()) {
            if (function.service().equals(service.name())) {
               String signature = builder.signatures().stream()
                  .filter(s -> (s.receiver().equals(service.name()) || s.receiver().equals(function.className()))
                     && s.signature().contains(function.name() + "("))
                  .map(MLuaDocBuilder.SignatureDoc::signature).findFirst().orElse(function.name() + "()");
               methods.add(methodShape(signature));
            }
         }
         out.append("  ").append(jsString(service.name())).append(": ").append(jsArray(methods));
         if (i + 1 < services.size()) out.append(',');
         out.append('\n');
      }
      out.append("};\n");
      List<String> registeredEnums = builder.entries().stream()
         .filter(e -> e.type() == MLuaDocBuilder.DocEntryType.ENUM).map(MLuaDocBuilder.DocEntry::name).toList();
      jsArray(out, "ENUM_CATEGORIES", registeredEnums.isEmpty() ? enumNames : registeredEnums);
      // Keep the current extension's runtime and mixin completion data; those are not API prose.
      return out.toString();
   }

   private static String settings(List<String> globals) {
      StringBuilder out = new StringBuilder("{\n  \"Lua.workspace.checkThirdParty\": false,\n  \"Lua.workspace.library\": [\n    \"${workspaceFolder}/.vscode/mlua\"\n  ],\n  \"Lua.diagnostics.globals\": [\n    ");
      for (int i = 0; i < globals.size(); i++) {
         if (i > 0) out.append(", ");
         out.append(jsonString(globals.get(i)));
      }
      return out.append("\n  ]\n}\n").toString();
   }

   private static List<String> registeredGlobals(MLuaDocBuilder builder, List<String> discovered) {
      Set<String> names = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
      if (discovered != null) names.addAll(discovered);
      if (names.isEmpty()) {
         for (MLuaDocBuilder.DocEntry entry : builder.entries()) {
            if (entry.type() == MLuaDocBuilder.DocEntryType.DATA_TYPE
                || entry.type() == MLuaDocBuilder.DocEntryType.LIBRARY) names.add(entry.name());
         }
      }
      return List.copyOf(names);
   }

   private static Map<String, List<String>> scanMinecraftClasses() {
      Map<String, List<String>> result = new java.util.TreeMap<>();
      String classpath = System.getProperty("mlua.minecraft.classpath");
      if (classpath == null || classpath.isBlank()) classpath = System.getProperty("java.class.path", "");
      for (String item : classpath.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
         File file = new File(item);
         if (!file.isFile() || !file.getName().endsWith(".jar")) continue;
         try (ZipFile archive = new ZipFile(file)) {
            Enumeration<? extends ZipEntry> entries = archive.entries();
            while (entries.hasMoreElements()) {
               ZipEntry entry = entries.nextElement();
               String path = entry.getName();
               if (!path.startsWith("net/minecraft/") || !path.endsWith(".class") || path.contains("$")) continue;
               String className = path.substring(0, path.length() - 6).replace('/', '.');
               List<String> methods = new ArrayList<>();
               try (InputStream input = archive.getInputStream(entry)) {
                  JavaClass parsed = new ClassParser(input, path).parse();
                  for (Method method : parsed.getMethods()) {
                     String name = method.getName();
                     if (!method.isPrivate() && !method.isStatic() && !name.startsWith("<")
                         && !name.equals("m_") && name.length() <= 60 && !methods.contains(name)) methods.add(name);
                  }
               } catch (Exception ignored) { }
               result.putIfAbsent(className, methods);
            }
         } catch (IOException ignored) { }
      }
      return result;
   }

   private static String mixinData(Map<String, List<String>> classes) {
      StringBuilder out = new StringBuilder();
      jsArray(out, "MIXIN_CLASSES", classes.keySet());
      out.append("const MIXIN_METHODS = {\n");
      int index = 0;
      for (Map.Entry<String, List<String>> entry : classes.entrySet()) {
         out.append("  ").append(jsString(entry.getKey())).append(": ")
            .append(jsArray(entry.getValue().stream().sorted().map(name -> name + "()").toList()));
         if (++index < classes.size()) out.append(',');
         out.append('\n');
      }
      return out.append("};\n\n").toString();
   }

   private static Path findProjectRoot(Path gameDir) {
      Path current = gameDir.toAbsolutePath().normalize();
      for (int i = 0; current != null && i < 6; i++, current = current.getParent()) {
         if (Files.isRegularFile(current.resolve("vscode-extension").resolve("extension.js"))) return current;
      }
      return null;
   }

   private static String luaTypeName(MLuaDocBuilder.DocEntry entry) {
      return entry.type() == MLuaDocBuilder.DocEntryType.SERVICE && entry.className() != null
         ? entry.className() : entry.name();
   }

   private static boolean isReferencedType(String name, MLuaDocBuilder builder) {
      return builder.methods().stream().anyMatch(method -> containsType(method.returns(), name))
         || builder.signatures().stream().anyMatch(signature -> containsType(signature.returns(), name))
         || builder.members().stream().anyMatch(member -> containsType(member.type(), name));
   }

   private static void addReferencedType(Map<String, MLuaDocBuilder.DocEntry> types, String declaration) {
      if (declaration == null) return;
      for (String name : declaration.split("[|,<>?\\s]+")) {
         if (!name.matches("[A-Z][A-Za-z0-9_]*") || Set.of("Any", "Void", "Nil", "String", "Number", "Boolean", "Table").contains(name)) continue;
         types.putIfAbsent(name, new MLuaDocBuilder.DocEntry(MLuaDocBuilder.DocEntryType.DATA_TYPE,
            name, name, null, (String) null));
      }
   }

   private static boolean containsType(String declaration, String type) {
      return declaration != null && java.util.Arrays.stream(declaration.split("[|,<>?\\s]+"))
         .anyMatch(type::equals);
   }

   private static String memberName(String signature, String owner) {
      String value = signature == null ? "" : signature;
      int split = Math.max(value.lastIndexOf('.'), value.lastIndexOf(':'));
      return split < 0 ? value : value.substring(split + 1);
   }

   private static void appendDoc(StringBuilder out, String text) {
      if (text == null || text.isBlank()) return;
      for (String line : text.split("\\R")) out.append("--- ").append(line.replace("\r", "")).append('\n');
   }

   private static String luaType(String value) {
      if (value == null || value.isBlank()) return "any";
      return value.replace("void", "nil").replace("string?", "string|nil").replace("boolean?", "boolean|nil");
   }

   private static List<String> splitTypes(String arguments) {
      if (arguments == null || arguments.isBlank()) return List.of();
      List<String> values = new ArrayList<>();
      int depth = 0;
      int start = 0;
      for (int i = 0; i < arguments.length(); i++) {
         char character = arguments.charAt(i);
         if (character == '<' || character == '(' || character == '[') depth++;
         else if (character == '>' || character == ')' || character == ']') depth--;
         else if (character == ',' && depth == 0) {
            values.add(arguments.substring(start, i));
            start = i + 1;
         }
      }
      values.add(arguments.substring(start));
      return values;
   }

   private static String methodShape(String signature) {
      if (signature == null) return "";
      int split = Math.max(signature.lastIndexOf(':'), signature.lastIndexOf('.'));
      int open = signature.indexOf('(', split + 1);
      if (open < 0) return signature.substring(split + 1) + "()";
      return signature.substring(split + 1).replaceAll(":\\s*", "").trim();
   }

   private static void jsArray(StringBuilder out, String name, Iterable<String> values) {
      out.append("const ").append(name).append(" = [\n");
      for (String value : values) out.append("  ").append(jsString(value)).append(",\n");
      out.append("];\n");
   }

   private static String jsArray(Iterable<String> values) {
      StringBuilder out = new StringBuilder("[");
      boolean first = true;
      for (String value : values) {
         if (!first) out.append(", ");
         first = false;
         out.append(jsString(value));
      }
      return out.append(']').toString();
   }

   private static String jsString(String value) {
      return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
         .replace("\n", "\\n").replace("\r", "\\r") + "\"";
   }

   private static String jsonString(String value) { return jsString(value); }
}
