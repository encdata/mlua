package dev.mlua.common.enums;

import java.util.Set;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.WeakHashMap;

public enum ScriptType {
   MIXIN(".mixin.lua"),
   CLIENT_MIXIN(".client.mixin.lua"),
   SERVER_MIXIN(".server.mixin.lua"),
   REGISTRY(".registry.lua"),
   COMMANDS(".commands.lua"),
   SERVER(".server.lua"),
   CLIENT(".client.lua"),
   ENTRYPOINT(".lua"),
   MODULE(".lua"),
   SHARED(".lua");

   private final String suffix;
   private static final Set<String> ENTRYPOINT_NAMES = Set.of(
      "run_mixin", "run_registry", "run_commands", "run_server", "run_client",
      "run_server_stopping", "run_world_load", "run_world_unload");
   private static final Map<String, Set<String>> ENTRYPOINT_CACHE =
      Collections.synchronizedMap(new WeakHashMap<>());

   private ScriptType(String suffix) {
      this.suffix = suffix;
   }

   public String suffix() {
      return this.suffix;
   }

   /** Resolves the three supported Lua mixin filename forms consistently in every loader. */
   public static ScriptType mixinType(String fileName) {
      if (fileName == null) return null;
      if (fileName.endsWith(CLIENT_MIXIN.suffix)) return CLIENT_MIXIN;
      if (fileName.endsWith(SERVER_MIXIN.suffix)) return SERVER_MIXIN;
      if (fileName.endsWith(MIXIN.suffix)) return MIXIN;
      return null;
   }

   /** Applies the shared suffix and entrypoint rules used by runtime and platform loaders. */
   public static ScriptType classify(String fileName, String source) {
      String name = fileName == null ? "" : fileName;
      ScriptType mixin = mixinType(name);
      if (mixin != null) return mixin;
      if (name.endsWith(".register.lua") || name.endsWith(REGISTRY.suffix)) return REGISTRY;
      if (name.endsWith(COMMANDS.suffix)) return COMMANDS;
      if (name.endsWith(SERVER.suffix)) return SERVER;
      if (name.endsWith(CLIENT.suffix)) return CLIENT;
      return declaresAnyEntrypoint(source) ? ENTRYPOINT : MODULE;
   }

   /** Returns whether a generic script declares a named lifecycle entrypoint. */
   public static boolean declaresEntrypoint(String source, String name) {
      if (source == null || name == null || !ENTRYPOINT_NAMES.contains(name)) return false;
      return declaredEntrypoints(source).contains(name);
   }

   private static boolean declaresAnyEntrypoint(String source) {
      if (source == null) return false;
      return !declaredEntrypoints(source).isEmpty();
   }

   private static Set<String> declaredEntrypoints(String source) {
      synchronized (ENTRYPOINT_CACHE) {
         Set<String> cached = ENTRYPOINT_CACHE.get(source);
         if (cached != null) return cached;
      }
      Set<String> discovered = scanEntrypoints(scan(source));
      Set<String> result = discovered.isEmpty() ? Set.of() : Set.copyOf(discovered);
      synchronized (ENTRYPOINT_CACHE) {
         ENTRYPOINT_CACHE.put(source, result);
      }
      return result;
   }

   /**
    * Recognizes only global function declarations and direct global assignments. A lexer is used instead
    * of a source regex so code-looking text in comments and Lua strings cannot change script lifecycle.
    */
   private static Set<String> scanEntrypoints(java.util.List<Token> tokens) {
      Set<String> discovered = new HashSet<>();
      for (int index = 0; index < tokens.size(); index++) {
         Token token = tokens.get(index);
         if ("function".equals(token.text()) && index + 2 < tokens.size()) {
            Token name = tokens.get(index + 1);
            Token openParen = tokens.get(index + 2);
            boolean local = index > 0 && "local".equals(tokens.get(index - 1).text());
            if (!local && name.identifier() && "(".equals(openParen.text())
                  && ENTRYPOINT_NAMES.contains(name.text())) discovered.add(name.text());
         }
         if (token.identifier() && index + 3 < tokens.size()
               && "=".equals(tokens.get(index + 1).text())
               && "function".equals(tokens.get(index + 2).text())
               && "(".equals(tokens.get(index + 3).text())
               && ENTRYPOINT_NAMES.contains(token.text())
               && isGlobalAssignment(tokens, index)) discovered.add(token.text());
      }
      return discovered;
   }

   private static boolean isGlobalAssignment(java.util.List<Token> tokens, int nameIndex) {
      if (nameIndex > 0) {
         Token previous = tokens.get(nameIndex - 1);
         if (".".equals(previous.text()) || ":".equals(previous.text())
               || "{".equals(previous.text()) || ",".equals(previous.text())) return false;
      }
      // A comma immediately before the name denotes a table field or a local name list.
      // Stop at a previous statement when checking whether a local declaration owns the name.
      int candidateLine = tokens.get(nameIndex).line();
      for (int index = nameIndex - 1; index >= 0; index--) {
         Token token = tokens.get(index);
         if ("local".equals(token.text())) return false;
         if (";".equals(token.text()) || "then".equals(token.text()) || "do".equals(token.text())
               || "else".equals(token.text()) || "elseif".equals(token.text()) || "end".equals(token.text())
               || "return".equals(token.text())) return true;
         if ("=".equals(token.text())) return true;
         if (token.line() < candidateLine && !",".equals(token.text())) return true;
      }
      return true;
   }

   private static java.util.List<Token> scan(String source) {
      java.util.ArrayList<Token> tokens = new java.util.ArrayList<>();
      int line = 1;
      for (int index = 0; index < source.length();) {
         char current = source.charAt(index);
         if (current == '\n' || current == '\r') {
            line++;
            index++;
            continue;
         }
         if (Character.isWhitespace(current)) {
            index++;
            continue;
         }
         if (current == '-' && index + 1 < source.length() && source.charAt(index + 1) == '-') {
            index += 2;
            int longEquals = longBracketEquals(source, index);
            if (longEquals >= 0) {
               index += longEquals + 2;
               int close = longBracketEnd(source, index, longEquals);
               while (index < close) {
                  char value = source.charAt(index++);
                  if (value == '\n' || value == '\r') line++;
               }
               index = Math.min(source.length(), close + longEquals + 2);
            } else {
               while (index < source.length() && source.charAt(index) != '\n'
                     && source.charAt(index) != '\r') index++;
            }
            continue;
         }
         if (current == '\'' || current == '"') {
            char quote = current;
            index++;
            while (index < source.length()) {
               char value = source.charAt(index++);
               if (value == '\n' || value == '\r') line++;
               if (value == '\\' && index < source.length()) {
                  char escaped = source.charAt(index++);
                  if (escaped == '\n' || escaped == '\r') line++;
               } else if (value == quote) break;
            }
            continue;
         }
         if (current == '[') {
            int longEquals = longBracketEquals(source, index);
            if (longEquals >= 0) {
               index += longEquals + 2;
               int close = longBracketEnd(source, index, longEquals);
               while (index < close) {
                  char value = source.charAt(index++);
                  if (value == '\n' || value == '\r') line++;
               }
               index = Math.min(source.length(), close + longEquals + 2);
               continue;
            }
         }
         if (isIdentifierStart(current)) {
            int start = index++;
            while (index < source.length() && isIdentifierPart(source.charAt(index))) index++;
            tokens.add(new Token(source.substring(start, index), line, true));
            continue;
         }
         tokens.add(new Token(String.valueOf(current), line, false));
         index++;
      }
      return tokens;
   }

   private static int longBracketEquals(String source, int index) {
      if (index >= source.length() || source.charAt(index) != '[') return -1;
      int cursor = index + 1;
      while (cursor < source.length() && source.charAt(cursor) == '=') cursor++;
      return cursor < source.length() && source.charAt(cursor) == '[' ? cursor - index - 1 : -1;
   }

   private static int longBracketEnd(String source, int from, int equalsCount) {
      for (int index = from; index < source.length(); index++) {
         if (source.charAt(index) != ']') continue;
         int cursor = index + 1;
         int equals = 0;
         while (cursor < source.length() && source.charAt(cursor) == '=' && equals <= equalsCount) {
            cursor++;
            equals++;
         }
         if (equals == equalsCount && cursor < source.length() && source.charAt(cursor) == ']') return index;
      }
      return source.length();
   }

   private static boolean isIdentifierStart(char value) {
      return value == '_' || value >= 'A' && value <= 'Z' || value >= 'a' && value <= 'z';
   }

   private static boolean isIdentifierPart(char value) {
      return isIdentifierStart(value) || value >= '0' && value <= '9';
   }

   private record Token(String text, int line, boolean identifier) {
   }

   public boolean isMixin() {
      return this == MIXIN || this == CLIENT_MIXIN || this == SERVER_MIXIN;
   }

   /** Whether this declared script type may provide the named entrypoint on the requested side. */
   public boolean supportsEntrypoint(String name, Side side) {
      if (name == null || side == null || !ENTRYPOINT_NAMES.contains(name)) return false;
      if (isServerLifecycleEntrypoint(name) && side != Side.SERVER && side != Side.BOTH) return false;
      if (isWorldLifecycleEntrypoint(name) && side != Side.SERVER && side != Side.CLIENT && side != Side.BOTH) return false;
      if ("run_client".equals(name) && side != Side.CLIENT && side != Side.BOTH) return false;
      boolean sideMatches = side == Side.BOTH || switch (this) {
         case CLIENT, CLIENT_MIXIN -> side == Side.CLIENT;
         case SERVER, SERVER_MIXIN, MIXIN, REGISTRY -> side == Side.SERVER;
         case COMMANDS, ENTRYPOINT, MODULE, SHARED -> true;
      };
      if (!sideMatches) return false;
      return switch (this) {
         case CLIENT_MIXIN, SERVER_MIXIN, MIXIN -> "run_mixin".equals(name);
         case REGISTRY -> "run_registry".equals(name);
         case COMMANDS -> "run_commands".equals(name);
         case SERVER -> "run_server".equals(name) || "run_server_stopping".equals(name)
            || "run_world_load".equals(name) || "run_world_unload".equals(name);
         case CLIENT -> "run_client".equals(name) || isWorldLifecycleEntrypoint(name);
         case ENTRYPOINT, MODULE, SHARED -> true;
      };
   }

   private static boolean isServerLifecycleEntrypoint(String name) {
      return "run_registry".equals(name) || "run_server".equals(name)
         || "run_server_stopping".equals(name);
   }

   private static boolean isWorldLifecycleEntrypoint(String name) {
      return "run_world_load".equals(name) || "run_world_unload".equals(name);
   }

   /** Runtime side used to execute the mixin script; generic mixin scripts are server-side. */
   public Side mixinSide() {
      return switch (this) {
         case MIXIN, SERVER_MIXIN -> Side.SERVER;
         case CLIENT_MIXIN -> Side.CLIENT;
         default -> null;
      };
   }
}
