package dev.mlua.common.enums;

import java.util.regex.Pattern;

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
   private static final Pattern ENTRYPOINT_PATTERN = Pattern.compile("\\brun_(mixin|registry|commands|server|client)\\b");

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
      return source != null && ENTRYPOINT_PATTERN.matcher(source).find() ? ENTRYPOINT : MODULE;
   }

   public boolean isMixin() {
      return this == MIXIN || this == CLIENT_MIXIN || this == SERVER_MIXIN;
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
