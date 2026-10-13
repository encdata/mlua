package dev.mlua.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MLuaApiDocScanner {
   private static final Pattern TABLE_EXPORT = Pattern.compile("\\.(?:rawset|set)\\(\"([A-Za-z_][A-Za-z0-9_]*)\"");
   private static final Pattern TABLE_EXPORT_ON = Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_]*)\\.(?:rawset|set)\\(\"([A-Za-z_][A-Za-z0-9_]*)\"");
   private static final Pattern TABLE_DECL = Pattern.compile("\\bLuaTable\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=");
   private static final Pattern TABLE_PATH_ASSIGN = Pattern.compile("\\bLuaTable\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(?:[A-Za-z_][A-Za-z0-9_]*\\.)?[A-Za-z_][A-Za-z0-9_]*\\(\"([A-Za-z_][A-Za-z0-9_.]*)\"");
   private static final Pattern TABLE_CHILD_LINK = Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_]*)\\.(?:rawset|set)\\(\"([A-Za-z_][A-Za-z0-9_]*)\"\\s*,\\s*([A-Za-z_][A-Za-z0-9_]*)\\)");
   private static final Pattern SWITCH_CASE = Pattern.compile("case\\s+\"([A-Za-z_][A-Za-z0-9_]*)\"\\s*:");
   private static final Pattern LUA_PROPERTY_KEY = Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_]*)\\.(?:get|getOrDefault|containsKey)\\(\"([A-Za-z_][A-Za-z0-9_]*)\"");
   private static final Pattern TABLE_SET_TYPE = Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_]*)\\.(?:rawset|set)\\(\"__type\"\\s*,\\s*\"([A-Za-z_][A-Za-z0-9_]*)\"");
   private static final Pattern METHOD_ASSIGN = Pattern.compile("\\.(?:rawset|set)\\(\"([A-Za-z_][A-Za-z0-9_]*)\"\\s*,");
   private static final Pattern ASSIGNMENT_DECL = Pattern.compile("\\.set\\(\"([A-Za-z_][A-Za-z0-9_]*)\"\\s*,\\s*new\\s+VarArgFunction\\(\\)\\s*\\{");
   private static final Pattern METHOD_DECL = Pattern.compile("(?:private|public|protected)\\s+(?:static\\s+)?(?:final\\s+)?(?:\\w+\\s+)?(\\w+)\\s*\\(");
   private static final Pattern METATABLE_OPERATOR = Pattern.compile("metatable\\.set\\(LuaValue\\.([A-Z]+)");

   private MLuaApiDocScanner() {
   }

   static String completeApiContent(Path gameDir) {
      Optional<Path> sourceRoot = findSourceRoot(gameDir);
      if (sourceRoot.isEmpty()) {
         return "<h2>Complete Reference</h2><p>Full development reference is unavailable because <code>src/main/java</code> was not found.</p>";
      } else {
         Path root = (Path)sourceRoot.get();
         StringBuilder html = new StringBuilder();
         html.append("<h2>Complete Reference</h2>");
         html.append("<p>Full Lua-facing reference from <code>").append(escape(root.toString())).append("</code>.</p>");
         html.append(scanClass(root, "Player", "minecraft/service/MLuaPlayerWrapper.java", "player"));
         html.append(scanClass(root, "Entity", "minecraft/service/MLuaEntityWrapper.java", "entity"));
         html.append(scanClass(root, "Camera", "minecraft/service/MLuaCameraService.java", "Camera"));
         html.append(scanClass(root, "Registry", "minecraft/service/MLuaRegistryService.java", "Registry"));
         html.append(scanClass(root, "CFrame", "math/MLuaCFrame.java", "CFrame"));
         html.append(scanClass(root, "ScreenGui", "minecraft/ui/element/ScreenGui.java", "ScreenGui"));
         html.append(scanClass(root, "Frame", "minecraft/ui/element/Frame.java", "Frame"));
         html.append(scanClass(root, "ImageLabel", "minecraft/ui/element/ImageLabel.java", "ImageLabel"));
         html.append(scanClass(root, "TextLabel", "minecraft/ui/element/TextLabel.java", "TextLabel"));
         html.append(scanClass(root, "ImageButton", "minecraft/ui/element/ImageButton.java", "ImageButton"));
         html.append(scanClass(root, "TextButton", "minecraft/ui/element/TextButton.java", "TextButton"));
         html.append(scanClass(root, "Luau Globals and Libraries", "core/engine/MLuaLuauTypes.java", ""));
         return html.toString();
      }
   }

    static String referenceSection(Path gameDir, String title, String sourcePath, String receiver) {
       Optional<Path> sourceRoot = findSourceRoot(gameDir);
       if (sourceRoot.isEmpty()) return "";
       return referenceSectionFromSourceRoot((Path)sourceRoot.get(), title, sourcePath, receiver);
    }

    static String categorizedReferenceSections(Path gameDir, String sourcePath, String receiver) {
       Optional<Path> sourceRoot = findSourceRoot(gameDir);
       if (sourceRoot.isEmpty()) return section("Reference", "<p>No Lua exports found.</p>");
       return categorizedReferenceSectionsFromSourceRoot((Path)sourceRoot.get(), sourcePath, receiver);
    }

    static String referenceSectionFromSourceRoot(Path sourceRoot, String title, String sourcePath, String receiver) {
        return categorizedReferenceSectionsFromSourceRoot(sourceRoot, sourcePath, receiver);
     }

    static String categorizedReferenceSectionsFromSourceRoot(Path sourceRoot, String sourcePath, String receiver) {
       Map<String, Entry> allEntries = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
       collectEntriesRecursive(sourceRoot, sourcePath, receiver, allEntries, null);
       if (allEntries.isEmpty()) {
          return section("Reference", "<p>No Lua exports found.</p>");
       }

       List<Entry> properties = new ArrayList();
       List<Entry> methods = new ArrayList();
       List<Entry> signals = new ArrayList();
       List<Entry> signalMethods = new ArrayList();
       for (Entry entry : allEntries.values()) {
          if ("signal api".equals(entry.source)) signalMethods.add(entry);
          else if ("Signal".equals(entry.type)) signals.add(entry);
          else if (isMethodEntry(entry)) methods.add(entry);
          else properties.add(entry);
       }

       StringBuilder html = new StringBuilder();
       if (!properties.isEmpty()) {
          html.append(section("Properties", members(properties)));
       }
       if (!signals.isEmpty()) {
          html.append(section("Signals", members(signals)));
       }
       if (!methods.isEmpty()) {
          html.append(section("Methods", members(methods)));
       }
       if (!signalMethods.isEmpty()) {
          html.append(section("Signal Methods", members(signalMethods)));
       }
       Path resolved = resolveSourceFile(sourceRoot, sourcePath);
       String displayPath = resolved == null ? sourcePath : sourceRoot.resolve("dev").resolve("mlua").relativize(resolved).toString().replace(File.separatorChar, '/');
       html.append(section("Reference",
          "<p>Scanned from <code>" + escape(displayPath) + "</code>.</p>"));
       return html.toString();
    }

    private static void collectEntriesRecursive(Path sourceRoot, String sourcePath, String receiver, Map<String, Entry> allEntries, String inheritedFrom) {
        Map<String, Entry> entries = getEntries(sourceRoot, sourcePath, receiver);
        for (Entry entry : entries.values()) {
            if (inheritedFrom != null) {
                entry.inherited = true;
                // Append inheritance note
                entry.source = "inherited from " + inheritedFrom;
            }
            allEntries.putIfAbsent(entry.name, entry);
        }

        addSignalMembers(entries, allEntries);
        
        String parentPath = getParentSourcePathFromSourcePath(sourcePath);
        if (parentPath != null) {
            String parentClassName = getClassNameFromSourcePath(parentPath);
            collectEntriesRecursive(sourceRoot, parentPath, receiver, allEntries, parentClassName);
        }
    }

    private static String getParentSourcePathFromSourcePath(String sourcePath) {
        switch (sourcePath) {
            case "minecraft/ui/element/ImageButton.java": return "minecraft/ui/element/GuiButton.java";
            case "minecraft/ui/element/TextButton.java": return "minecraft/ui/element/GuiButton.java";
            case "minecraft/ui/element/GuiButton.java": return "minecraft/ui/element/GuiObject.java";
            case "minecraft/ui/element/ImageLabel.java": return "minecraft/ui/element/GuiObject.java";
            case "minecraft/ui/element/TextLabel.java": return "minecraft/ui/element/GuiObject.java";
            case "minecraft/ui/element/Frame.java": return "minecraft/ui/element/GuiObject.java";
            case "minecraft/ui/element/GuiObject.java": return "minecraft/ui/element/GuiBase2d.java";
            case "minecraft/ui/element/GuiBase2d.java": return "minecraft/ui/element/GuiBase.java";
            case "minecraft/ui/element/ScreenGui.java": return "minecraft/ui/element/LayerCollector.java";
            default: return null;
        }
    }

    private static String getClassNameFromSourcePath(String sourcePath) {
        String fileName = sourcePath.substring(sourcePath.lastIndexOf('/') + 1);
        return fileName.replace(".java", "");
    }

   static boolean hasSourceRoot(Path gameDir) {
      return findSourceRoot(gameDir).isPresent();
   }

    private static String scanClass(Path sourceRoot, String title, String sourcePath, String receiver) {
       Path file = resolveSourceFile(sourceRoot, sourcePath);
       if (!Files.isRegularFile(file, new LinkOption[0])) {
          return section(title, "<p>Source file not found: <code>" + escape(file.toString()) + "</code></p>");
       } else {
          try {
             String source = Files.readString(file);
             Map<String, Entry> entries = getEntries(sourceRoot, sourcePath, receiver);
              String classType = scanType(source);
              String side = scanSide(source);
              applyClassTypeInference(entries, classType);
             if (side != null) {
                for (Entry e : entries.values()) {
                   e.side = side;
                }
             }
            if (entries.isEmpty()) {
               return section(title, "<p>No Lua exports found.</p>");
            } else {
               String content = members(entries.values());
                String keys = propertyKeys(source);
                if (!keys.isEmpty()) {
                   content = content + section("Property Keys", "<p class=\"section-desc\">These are Lua table keys. Read via <code>table.key</code> or <code>table[\"key\"]</code>. Write via <code>table.key = value</code> or <code>table.set(\"key\", value)</code>.</p>" + keys);
                }

               String extra = specialNotes(source, sourcePath, receiver);
               if (!extra.isEmpty()) {
                  content = content + extra;
               }

               return section(title, content);
            }
         } catch (IOException var10) {
            return section(title, "<p>Could not scan source: " + escape(var10.getMessage()) + "</p>");
         }
      }
   }

    private static void scanTableExports(String source, Map<String, Entry> entries, String receiver) {
       Matcher matcher = TABLE_EXPORT.matcher(source);

      while(matcher.find()) {
         String name = matcher.group(1);
         if (!name.startsWith("__")) {
            String line = lineAt(source, matcher.start());
            boolean signal = isSignalLine(line);
            boolean function = !signal && looksLikeFunction(name, line);
            String access = function ? "Call" : "Read";
            String signature = signature(receiver, name, function);
            Entry entry = (Entry)entries.get(name);
            if (entry == null || signal) {
               entry = new Entry(name, signature, signal ? "Signal" : inferType(name, function), access);
               entries.put(name, entry);
            }
            entry.access = mergeAccess(entry.access, access);
            entry.source = "table export";
            if (signal) {
               entry.type = "Signal";
            }
         }
      }

    }

    private static void scanTableExports(String source, Map<String, Entry> entries, String receiver, String tableVar) {
      Matcher matcher = TABLE_EXPORT_ON.matcher(source);

      while(matcher.find()) {
         if (!matcher.group(1).equals(tableVar)) {
            continue;
         }
         String name = matcher.group(2);
         if (!name.startsWith("__")) {
            String line = lineAt(source, matcher.start());
            boolean signal = isSignalLine(line);
            boolean function = !signal && looksLikeFunction(name, line);
            String access = function ? "Call" : "Read";
            String signature = signature(receiver, name, function);
            Entry entry = (Entry)entries.get(name);
            if (entry == null || signal) {
               entry = new Entry(name, signature, signal ? "Signal" : inferType(name, function), access);
               entries.put(name, entry);
            }
            entry.access = mergeAccess(entry.access, access);
            entry.source = "table export";
            if (signal) {
               entry.type = "Signal";
            }
         }
      }

   }

   static List<String> discoverReceiverPaths(Path sourceRoot, String sourcePath, String rootReceiver) {
      Path file = resolveSourceFile(sourceRoot, sourcePath);
      if (!Files.isRegularFile(file, new LinkOption[0])) {
         return List.of();
      }
      try {
         String source = Files.readString(file);
         Map<String, String> varPaths = discoverTablePaths(source, rootReceiver);
         List<String> paths = new ArrayList<>();
         for (String path : varPaths.values()) {
            if (path == null || path.isBlank() || paths.contains(path)) {
               continue;
            }
            paths.add(path);
         }
         paths.sort(String::compareTo);
         return paths;
      } catch (IOException ignored) {
         return List.of();
      }
   }

   private static boolean isSignalLine(String line) {
      return line.contains("signalInstance(") || line.contains("new MLuaSignal(");
   }

   private static String scanType(String source) {
      Matcher matcher = TABLE_EXPORT.matcher(source);
      while (matcher.find()) {
         if ("__type".equals(matcher.group(1))) {
            String line = lineAt(source, matcher.start());
            int valStart = line.indexOf('"', line.indexOf("__type") + 7);
            if (valStart >= 0) {
               int valEnd = line.indexOf('"', valStart + 1);
               if (valEnd >= 0) {
                  return line.substring(valStart + 1, valEnd);
               }
            }
         }
      }
      return null;
   }

    private static String scanSide(String source) {
       Matcher matcher = TABLE_EXPORT.matcher(source);
       while (matcher.find()) {
          if ("__side".equals(matcher.group(1))) {
             String line = lineAt(source, matcher.start());
             int valStart = line.indexOf('"', line.indexOf("__side") + 7);
             if (valStart >= 0) {
                int valEnd = line.indexOf('"', valStart + 1);
                if (valEnd >= 0) {
                   return line.substring(valStart + 1, valEnd);
                }
             }
          }
       }
       return null;
    }

    private static void applyClassTypeInference(Map<String, Entry> entries, String classType) {
      String classStem = null;
      if (classType != null) {
         String t = classType.toLowerCase(Locale.ROOT);
         classStem = t.endsWith("s") ? t.substring(0, t.length() - 1) : t;
      }
      for (Entry entry : entries.values()) {
         if ("any".equals(entry.type) || "function".equals(entry.type)) {
            String n = entry.name.toLowerCase(Locale.ROOT);
            if (n.contains("signal") || n.contains("event") || (n.contains("player") && (n.contains("added") || n.contains("removing"))) || n.equals("heartbeat") || n.equals("stepped") || n.equals("renderstepped") || n.equals("prerender") || n.equals("postrender")) entry.type = "Signal";
            else if (n.contains("tick") && !n.contains("ticking")) entry.type = "Signal";
            else if ((n.contains("player") || n.contains("players")) && (n.contains("count") || n.contains("max"))) entry.type = "number";
            else if (n.contains("player") && !n.contains("players") && !n.contains("added") && !n.contains("removing") && !n.contains("count") && !n.contains("max") && !n.contains("list") && !n.contains("all")) entry.type = "Player";
            else if (n.contains("block") && !n.contains("blocks") && !n.contains("list")) entry.type = "Block";
            else if ((n.contains("entity") || n.contains("mob")) && !n.contains("entities") && !n.contains("list")) entry.type = "Entity";
            else if (n.contains("magnitude") || n.contains("distance")) entry.type = "number";
            else if (n.contains("gameid") || n.contains("placeid") || n.contains("jobid") || n.contains("modid") || n.contains("translationkey") || n.contains("descriptionid") || n.contains("icon")) entry.type = "string";
            else if (n.contains("ambient") || n.contains("showparticles") || n.contains("isruntime")) entry.type = "boolean";
            else if ("color".equals(n)) entry.type = "number";
            else if (classStem != null && n.contains(classStem) && !n.contains("added") && !n.contains("removing") && !n.contains("count") && !n.contains("max") && !(n.contains(classStem) && n.length() > classStem.length() && n.endsWith("s"))) {
               String cap = Character.toUpperCase(classStem.charAt(0)) + classStem.substring(1);
               entry.type = cap;
            }
         }
      }
   }

   private static void scanMetatableProperties(String source, Map<String, Entry> entries, String receiver) {
     List<String> readProps = new ArrayList();
     List<String> writeProps = new ArrayList();
     scanSwitchCasesAfter(source, "meta.set(\"__index\"", "meta.set(\"__newindex\"", readProps);
     scanSwitchCasesAfter(source, "meta.set(\"__newindex\"", "table.setmetatable", writeProps);

     // Also scan for writeProperties(LuaTable table) { table.set(...) }
     Matcher matcher = Pattern.compile("table\\.set\\(\"([A-Za-z_][A-Za-z0-9_]*)\"").matcher(source);
     while(matcher.find()){
         String prop = matcher.group(1);
         if(!writeProps.contains(prop)) writeProps.add(prop);
         if (prop.startsWith("__")) continue;

         String line = lineAt(source, matcher.start());
         if (isSignalLine(line)) {
            Entry signalEntry = new Entry(prop, propertyName(receiver, prop), "Signal", "Read");
            signalEntry.source = "table export";
            entries.put(prop, signalEntry);
         }
     }

     Iterator var5;
     String prop;
     Entry entry;
     for(var5 = readProps.iterator(); var5.hasNext(); entry.source = "metatable") {
        prop = (String)var5.next();
        entry = (Entry)entries.get(prop);
        if (entry == null) {
           entry = new Entry(prop, propertyName(receiver, prop), inferType(prop, false), "Read");
           entries.put(prop, entry);
        }
        entry.access = mergeAccess(entry.access, "Read");
     }

     for(var5 = writeProps.iterator(); var5.hasNext(); entry.source = "metatable") {
        prop = (String)var5.next();
        entry = (Entry)entries.get(prop);
        if (entry == null) {
           entry = new Entry(prop, propertyName(receiver, prop), inferType(prop, false), "Write");
           entries.put(prop, entry);
        }
        entry.access = mergeAccess(entry.access, "Write");
     }

   }
   private static void scanOperators(String source, Map<String, Entry> entries, String receiver) {
      Matcher matcher = METATABLE_OPERATOR.matcher(source);

      while(matcher.find()) {
         String op = matcher.group(1);
         String var10000;
         switch (op) {
            case "ADD":
               var10000 = "+ Vector3";
               break;
            case "SUB":
               var10000 = "- Vector3";
               break;
            case "MUL":
               var10000 = "* CFrame";
               break;
            case "DIV":
               var10000 = "/ scalar";
               break;
            default:
               var10000 = op;
         }

         String symbol = var10000;
         String name = receiver != null && !receiver.isBlank() ? receiver + " " + symbol : symbol;
         entries.putIfAbsent("operator:" + op, new Entry("operator:" + op, name, inferOperatorType(op, receiver), "Operator"));
      }

   }

   private static String inferOperatorType(String op, String receiver) {
      if ("MUL".equals(op) && "CFrame".equals(receiver)) {
         return "CFrame";
      } else if (("ADD".equals(op) || "SUB".equals(op)) && "CFrame".equals(receiver)) {
         return "CFrame";
      } else {
         return receiver != null && !receiver.isBlank() ? receiver : "any";
      }
   }

    static String propertyKeys(String source) {
       Map<String, Entry> keys = new TreeMap(String.CASE_INSENSITIVE_ORDER);
       Matcher matcher = LUA_PROPERTY_KEY.matcher(source);

       Map<String, String> varTypes = new HashMap<>();
       Matcher typeMatcher = TABLE_SET_TYPE.matcher(source);
       while (typeMatcher.find()) {
          varTypes.put(typeMatcher.group(1), typeMatcher.group(2));
       }

       List<String> methodNames = new ArrayList<>();
       List<Integer> methodStarts = new ArrayList<>();
       Matcher methodMatcher = ASSIGNMENT_DECL.matcher(source);
       while (methodMatcher.find()) {
          methodStarts.add(methodMatcher.start());
          methodNames.add(methodMatcher.group(1));
       }

       while(matcher.find()) {
          String varName = matcher.group(1);
          String name = matcher.group(2);
          String type = inferType(name, false);
          String ownedType = varTypes.get(varName);

          String methodPrefix = null;
          int pos = matcher.start();
          for (int i = methodStarts.size() - 1; i >= 0; i--) {
             if (methodStarts.get(i) < pos) {
                methodPrefix = methodNameToPrefix(methodNames.get(i));
                break;
             }
          }

          String prefix = "";
          if (methodPrefix != null && !methodPrefix.isBlank()) {
             prefix = methodPrefix + ".";
          } else if (ownedType != null && !ownedType.isBlank() && !"any".equals(ownedType)) {
             prefix = ownedType + ".";
          }
          
          keys.putIfAbsent(name, new Entry(name, prefix + name, type, "Read"));
       }

       return keys.isEmpty() ? "" : members(keys.values());
    }

   private static String specialNotes(String source, String sourcePath, String receiver) {
      StringBuilder out = new StringBuilder();
      if (sourcePath.endsWith("MLuaInputService.java")) {
         String inputShape = "<table><tr><th>Field</th><th>Type</th><th>Meaning</th></tr><tr><td><code>label</code></td><td>string</td><td>Visible name in Controls; defaults to the bind name.</td></tr><tr><td><code>category</code></td><td>string</td><td>Controls menu category; defaults to <code>key.categories.mlua</code>.</td></tr><tr><td><code>defaultKey</code></td><td>string or <code>Keys.*</code></td><td>Default input. Use <code>Keys.Space</code>, <code>Keys.MouseLeft</code>, or a raw key string.</td></tr><tr><td><code>Name</code></td><td>string</td><td>Optional key name when using <code>RegisterKeybinds</code> with array-style entries.</td></tr></table><div class=\"callout info\">Input is client-side only. A <code>.client.lua</code> file can call <code>InputService:IsKeyDown(name)</code>, <code>ListenForKeybind(name)</code>, and <code>SetKeybind(name, key)</code>.</div><pre><code>" + escape("local InputService = Minecraft:GetService(\"InputService\")\n\nInputService:RegisterKeybind(\"jump\", {\n    label = \"Jump\",\n    category = \"key.categories.mlua\",\n    defaultKey = Keys.Space\n})\n\nInputService:RegisterKeybinds({\n    sprint = {\n        label = \"Sprint\",\n        defaultKey = Keys.LeftShift\n    }\n})") + "</code></pre>";
         out.append(section("Keybind Input Shape", inputShape));
         String returnedShape = "<table><tr><th>Field</th><th>Type</th><th>Meaning</th></tr><tr><td><code>Name</code></td><td>string</td><td>Bind name.</td></tr><tr><td><code>Key</code></td><td>string</td><td>Current key string.</td></tr><tr><td><code>IsDown</code></td><td>boolean</td><td>Whether the key is currently held.</td></tr><tr><td><code>Signal</code></td><td>Signal</td><td>Fires when the bind is triggered.</td></tr><tr><td><code>Listen</code></td><td>function</td><td>Alias for the bind signal.</td></tr><tr><td><code>SetKey</code></td><td>function</td><td>Updates and saves the binding.</td></tr></table>";
         out.append(section("Returned Keybind Table", returnedShape));
      }

      return out.toString();
   }

   private static void scanSwitchCasesAfter(String source, String startNeedle, String endNeedle, List<String> out) {
      int pos = 0;

      while(true) {
         int start = source.indexOf(startNeedle, pos);
         if (start < 0) {
            return;
         }

         int end = source.indexOf(endNeedle, start + startNeedle.length());
         if (end < 0) {
            end = Math.min(source.length(), start + 8000);
         }

         Matcher matcher = SWITCH_CASE.matcher(source.substring(start, end));

         while(matcher.find()) {
            String name = matcher.group(1);
            if (!out.contains(name)) {
               out.add(name);
            }
         }

         pos = end;
      }
   }

   private static boolean looksLikeFunction(String name, String line) {
      if (!line.contains("Function") && !line.contains("luaFn") && !line.contains("varArg") && !line.contains("fn(")) {
         return name.startsWith("Get") || name.startsWith("Set") || name.startsWith("Is") || name.startsWith("Has") || name.startsWith("Register") || name.startsWith("Remove") || name.startsWith("Clear") || name.startsWith("Give") || name.startsWith("Play") || name.startsWith("Look") || name.startsWith("Teleport") || name.startsWith("Damage") || name.startsWith("Kill") || name.startsWith("Distance");
      } else {
         return true;
      }
   }

   private static String signature(String receiver, String name, boolean function) {
      if (!function) {
         return propertyName(receiver, name);
      } else if (receiver != null && !receiver.isBlank()) {
         return Character.isUpperCase(receiver.charAt(0)) ? receiver + ":" + name + "(...)" : receiver + ":" + name + "(...)";
      } else {
         return name + "(...)";
      }
   }

   private static String propertyName(String receiver, String name) {
      return receiver != null && !receiver.isBlank() ? receiver + "." + name : name;
   }

   private static String inferType(String name, boolean function) {
      if (!function) {
         String n = name.toLowerCase(Locale.ROOT);
         if (n.contains("cframe")) {
            return "CFrame";
         } else if (!n.contains("position") && !n.contains("velocity") && !n.contains("lookvector")) {
            if (n.contains("rotation")) {
               return "Vector2";
            } else if (!n.contains("health") && !n.contains("food") && !n.contains("saturation") && !n.contains("level") && !n.contains("progress") && !n.contains("slot") && !n.equals("x") && !n.equals("y") && !n.equals("z") && !n.contains("yaw") && !n.contains("pitch") && !n.contains("roll")) {
               if (!n.startsWith("is") && !n.contains("alive") && !n.contains("ground") && !n.contains("water") && !n.contains("fire")) {
                  if (n.contains("player")) return "Player";
                  if (n.contains("block")) return "Block";
                  if (n.contains("entity") || n.contains("mob")) return "Entity";
                  if (n.contains("signal") || n.contains("event")) return "Signal";
                  if (n.contains("magnitude") || n.contains("distance")) return "number";
                  return !n.contains("name") && !n.contains("uuid") && !n.contains("type") && !n.contains("world") && !n.contains("dimension") && !n.contains("gamemode") ? "any" : "string";
               } else {
                  return "boolean";
               }
            } else {
               return "number";
            }
         } else {
            return "Vector3";
         }
      } else if (!name.startsWith("Is") && !name.startsWith("Has") && !name.endsWith("Exists")) {
         if (name.contains("CFrame")) {
            return "CFrame";
         } else if (!name.contains("Position") && !name.contains("Vector") && !name.contains("Velocity")) {
            if (name.contains("Rotation")) {
               return "Vector2";
            } else if (!name.contains("FOV") && !name.contains("Health") && !name.contains("Food") && !name.contains("Level") && !name.contains("Slot") && !name.contains("Experience") && !name.contains("Distance")) {
                if (name.startsWith("Get") && (name.contains("Name") || name.contains("UUID") || name.contains("Type") || name.contains("World") || name.contains("Dimension") || name.contains("GameMode") || name.contains("Registered") || name.contains("Command"))) {
                   if (name.contains("Player") || name.contains("Block") || name.contains("Entity")) return name.contains("Player") ? "Player" : name.contains("Block") ? "Block" : "Entity";
                   if (name.contains("Registered") || name.contains("Command")) return "table";
                   return "string";
               } else {
                  boolean isVoidish = name.startsWith("Set") || name.startsWith("Register") || name.startsWith("Remove") || name.startsWith("Clear") || name.startsWith("Give") || (name.startsWith("Play") && !name.startsWith("Player")) || name.startsWith("Look") || name.startsWith("Teleport") || name.startsWith("Kill") || name.startsWith("Damage");
                  return isVoidish ? "void" : "function";
               }
            } else {
               return "number";
            }
         } else {
            return "Vector3";
         }
      } else {
         return "boolean";
      }
   }

   private static String table(Iterable<Entry> entries) {
      List<Entry> list = new ArrayList();
      Objects.requireNonNull(list);
      entries.forEach(list::add);
      list.sort(Comparator.comparing((e) -> {
         return e.signature.toLowerCase(Locale.ROOT);
      }));
      StringBuilder sb = new StringBuilder();
      sb.append("<table><tr><th>Name</th><th>Type</th><th>Access</th><th>Source</th></tr>");
      Iterator var3 = list.iterator();

      while(var3.hasNext()) {
         Entry entry = (Entry)var3.next();
         sb.append("<tr><td><code>").append(escape(entry.signature)).append("</code></td><td>").append(MLuaDocumentation.linkType(entry.type)).append("</td><td>").append(escape(entry.access)).append("</td><td>").append(escape(entry.source)).append("</td></tr>");
      }

      sb.append("</table>");
      return sb.toString();
   }

   private static String members(Iterable<Entry> entries) {
      List<Entry> list = new ArrayList();
      Objects.requireNonNull(list);
      entries.forEach(list::add);
      list.sort(Comparator.comparing((e) -> {
         return e.signature.toLowerCase(Locale.ROOT);
      }));
      StringBuilder sb = new StringBuilder();
      Iterator var3 = list.iterator();

      while(var3.hasNext()) {
         Entry entry = (Entry)var3.next();
          String badges = badges(entry.access, entry.side);
          if (entry.inherited) {
              badges = "<span class=\"badge inherited\">inherited</span>" + badges;
          }
          sb.append("<div class=\"member\"><div class=\"member-header\"><span class=\"member-name\">").append(renderEntryName(entry)).append("</span><div class=\"member-flags\">").append(badges).append("</div></div><div class=\"member-desc\">").append(description(entry)).append("</div></div>");
      }

      return sb.toString();
   }

   private static String renderEntryName(Entry entry) {
      if ("signal api".equals(entry.source)) {
         return renderSignalApiName(entry.signature);
      }
      return MLuaDocumentation.linkMemberParts(entry.signature, entry.type);
   }

   private static String renderSignalApiName(String signature) {
      int dot = signature.indexOf('.');
      int colon = signature.indexOf(':', dot + 1);
      if (dot <= 0 || colon <= dot) {
         return MLuaDocumentation.linkMemberParts(signature, null);
      }

      String prefix = signature.substring(0, dot);
      String signalName = signature.substring(dot + 1, colon);
      String methodAndArgs = signature.substring(colon);

      StringBuilder result = new StringBuilder();
      String prefixHref = MLuaDocumentation.typeHref(prefix.toLowerCase(Locale.ROOT));
      String signalHref = MLuaDocumentation.typeHref("signal");

      if (prefixHref != null) {
         result.append("<a href=\"").append(prefixHref).append("\">").append(prefix).append("</a>");
      } else {
         result.append(prefix);
      }
      result.append(".");
      if (signalHref != null) {
         result.append("<a href=\"").append(signalHref).append("\">").append(signalName).append("</a>");
      } else {
         result.append(signalName);
      }
      result.append(methodAndArgs);
      return result.toString();
   }

    private static String badges(String access, String side) {
       StringBuilder sb = new StringBuilder();
       String[] var2 = access.split("/");
       int var3 = var2.length;

       for(int var4 = 0; var4 < var3; ++var4) {
          String part = var2[var4];
          String var10000;
          switch (part) {
             case "Read":
                var10000 = "read";
                break;
             case "Write":
                var10000 = "write";
                break;
             case "Call":
             case "Operator":
                var10000 = "shared";
                break;
             default:
                var10000 = "read";
          }

          String cls = var10000;
          sb.append("<span class=\"badge ").append(cls).append("\">").append(escape(part)).append("</span>");
       }

       if (side != null) {
          String sideCls = side.equalsIgnoreCase("Both") ? "both" : side.toLowerCase(Locale.ROOT);
          sb.append("<span class=\"badge ").append(sideCls).append("\">").append(escape(side)).append("</span>");
       }

       return sb.toString();
    }

   private static String description(Entry entry) {
       if ("Operator".equals(entry.access)) {
          return "Operator overload.";
       } else if ("Signal".equals(entry.type) && !"signal api".equals(entry.source)) {
          return "Signal property.";
       } else if ("table export".equals(entry.source)) {
          return "Callable Lua member.";
       } else if ("signal api".equals(entry.source)) {
          return switch (entry.name) {
             case "Connect" -> "Connects a callback function to this signal.";
             case "Once" -> "Connects a callback that disconnects itself after the first fire.";
             case "Wait" -> "Yields until this signal fires and returns the fired arguments.";
             case "Fire" -> "Fires this signal manually.";
             default -> "Signal method.";
          };
       } else if ("metatable".equals(entry.source)) {
          return "Lua property.";
       } else if ("source scan".equals(entry.source)) {
          String typeLabel = entry.type != null && !entry.type.isBlank() ? entry.type : "any";
          return "Value type: <code>" + typeLabel + "</code>.";
       } else {
          return "Lua property key.";
       }
    }

   private static boolean isMethodEntry(Entry entry) {
      return entry.signature.contains(":") || "Call".equals(entry.access) || "Operator".equals(entry.access);
   }

   private static String section(String title, String content) {
      String var10000 = slug(title);
      return "<h2 id=\"" + var10000 + "\">" + escape(title) + "</h2>" + content;
   }

   static Optional<Path> findSourceRoot(Path gameDir) {
      List<Path> candidates = List.of(gameDir.resolve("../src/main/java").normalize(), gameDir.resolve("src/main/java").normalize(), Path.of("").toAbsolutePath().resolve("src/main/java").normalize(), Path.of("").toAbsolutePath().resolve("../src/main/java").normalize());
      Iterator var2 = candidates.iterator();

      Path candidate;
      do {
         if (!var2.hasNext()) {
            return Optional.empty();
         }

         candidate = (Path)var2.next();
      } while(!Files.isRegularFile(candidate.resolve("dev/mlua/core/MLuaDocumentation.java"), new LinkOption[0]));

      return Optional.of(candidate);
   }

   private static String lineAt(String source, int index) {
      int start = source.lastIndexOf(10, index);
      int end = source.indexOf(10, index);
      if (start < 0) {
         start = 0;
      }

      if (end < 0) {
         end = source.length();
      }

       return source.substring(start, end);
    }

    private static String methodNameToPrefix(String methodName) {
       String lower = methodName.toLowerCase(Locale.ROOT);
       if (lower.contains("blockitem")) return "BlockItem";
       if (lower.contains("config")) return "Config";
       if (lower.contains("block")) return "Block";
       if (lower.contains("item")) return "Item";
       if (lower.contains("entity")) return "Entity";
       if (lower.contains("effect") || lower.contains("potion")) return "Effect";
       if (lower.contains("spawnegg")) return "SpawnEgg";
       if (lower.contains("creative")) return "CreativeTab";
       if (lower.contains("particle")) return "Particle";
       return null;
    }

    private static String mergeAccess(String a, String b) {
      Map<String, Boolean> parts = new LinkedHashMap();
      String[] var3 = a.split("/");
      int var4 = var3.length;

      int var5;
      String p;
      for(var5 = 0; var5 < var4; ++var5) {
         p = var3[var5];
         parts.put(p, true);
      }

      var3 = b.split("/");
      var4 = var3.length;

      for(var5 = 0; var5 < var4; ++var5) {
         p = var3[var5];
         parts.put(p, true);
      }

      return String.join("/", parts.keySet());
   }

   private static String slug(String s) {
      return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
   }

   private static String escape(String text) {
      return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
   }

   static Map<String, Entry> getEntries(Path sourceRoot, String sourcePath, String receiver) {
      Path file = resolveSourceFile(sourceRoot, sourcePath);
      if (!Files.isRegularFile(file, new LinkOption[0])) {
         return new TreeMap();
      } else {
          try {
             String source = Files.readString(file);
             Map<String, Entry> entries = new TreeMap(String.CASE_INSENSITIVE_ORDER);
             String tableVar = tableVariableForReceiver(source, receiver);
             if (tableVar != null) {
                scanTableExports(source, entries, receiver, tableVar);
                return entries;
             }
             scanTableExports(source, entries, receiver);
             scanMetatableProperties(source, entries, receiver);
             scanOperators(source, entries, receiver);
             return entries;
          } catch (IOException var6) {
             return new TreeMap();
         }
      }
   }

   private static String tableVariableForReceiver(String source, String receiver) {
      Map<String, String> varPaths = discoverTablePaths(source, receiver);
      for (Map.Entry<String, String> entry : varPaths.entrySet()) {
         if (receiver.equals(entry.getValue())) {
            return entry.getKey();
         }
      }
      return null;
   }

   private static Map<String, String> discoverTablePaths(String source, String rootReceiver) {
      Map<String, String> paths = new LinkedHashMap<>();
      Map<String, String[]> childLinks = new LinkedHashMap<>();
      Map<String, Boolean> tableVars = new LinkedHashMap<>();

      Matcher declMatcher = TABLE_DECL.matcher(source);
      while (declMatcher.find()) {
         tableVars.put(declMatcher.group(1), true);
      }

      Matcher pathAssignMatcher = TABLE_PATH_ASSIGN.matcher(source);
      while (pathAssignMatcher.find()) {
         String varName = pathAssignMatcher.group(1);
         String path = pathAssignMatcher.group(2);
         tableVars.put(varName, true);
         if (path.contains(".")) {
            paths.put(varName, path);
         }
      }

      Matcher typeMatcher = TABLE_SET_TYPE.matcher(source);
      while (typeMatcher.find()) {
         String varName = typeMatcher.group(1);
         String path = typeMatcher.group(2);
         tableVars.put(varName, true);
         paths.put(varName, path);
      }

      Matcher childLinkMatcher = TABLE_CHILD_LINK.matcher(source);
      while (childLinkMatcher.find()) {
         String parentVar = childLinkMatcher.group(1);
         String fieldName = childLinkMatcher.group(2);
         String childVar = childLinkMatcher.group(3);
         if (!tableVars.containsKey(childVar)) {
            continue;
         }
         childLinks.put(childVar, new String[]{parentVar, fieldName});
      }

      if (rootReceiver != null && !rootReceiver.isBlank() && !rootReceiver.contains(".") && tableVars.containsKey("table")) {
         paths.putIfAbsent("table", rootReceiver);
      }

      boolean changed;
      do {
         changed = false;
         for (Map.Entry<String, String[]> entry : childLinks.entrySet()) {
            String childVar = entry.getKey();
            if (paths.containsKey(childVar)) {
               continue;
            }
            String parentVar = entry.getValue()[0];
            String fieldName = entry.getValue()[1];
            String parentPath = paths.get(parentVar);
            if (parentPath == null || parentPath.isBlank()) {
               continue;
            }
            paths.put(childVar, parentPath + "." + fieldName);
            changed = true;
         }
      } while (changed);

      return paths;
   }

   private static Path resolveSourceFile(Path sourceRoot, String sourcePath) {
      Path devRoot = sourceRoot.resolve("dev").resolve("mlua");
      Path direct = devRoot.resolve(sourcePath.replace("/", File.separator));
      if (Files.isRegularFile(direct, new LinkOption[0])) {
         return direct;
      }

      String fileName = sourcePath;
      int slash = Math.max(sourcePath.lastIndexOf('/'), sourcePath.lastIndexOf('\\'));
      if (slash >= 0) {
         fileName = sourcePath.substring(slash + 1);
      }
      final String targetFileName = fileName;

      try (var paths = Files.walk(devRoot)) {
         return paths
            .filter(Files::isRegularFile)
            .filter(path -> path.getFileName().toString().equals(targetFileName))
            .findFirst()
            .orElse(direct);
      } catch (IOException ignored) {
         return direct;
      }
   }

   private static void addSignalMembers(Map<String, Entry> entries, Map<String, Entry> allEntries) {
      for (Entry entry : entries.values()) {
         if (!"Signal".equals(entry.type)) {
            continue;
         }
         String receiver = entry.signature;
         addSignalMethod(allEntries, receiver, "Connect", "Connection", "Call");
         addSignalMethod(allEntries, receiver, "Once", "Connection", "Call");
         addSignalMethod(allEntries, receiver, "Wait", "any", "Call");
         addSignalMethod(allEntries, receiver, "Fire", "void", "Call");
      }
   }

   private static void addSignalMethod(Map<String, Entry> allEntries, String receiver, String methodName, String type, String access) {
      String key = receiver + ":" + methodName;
      String signature = receiver + ":" + methodName + "(...)";
      Entry existing = allEntries.get(key);
      if (existing != null) {
         existing.access = mergeAccess(existing.access, access);
         return;
      }

      Entry entry = new Entry(key, signature, type, access);
      entry.source = "signal api";
      allEntries.put(key, entry);
   }

   static final class Entry {
      final String name;
      final String signature;
      String type;
      String access;
      String source;
      String side;
      boolean inherited;

      Entry(String name, String signature, String type, String access) {
         this.name = name;
         this.signature = signature;
         this.type = type;
         this.access = access;
         this.source = "source scan";
         this.side = null;
         this.inherited = false;
      }
   }
}
