package dev.mlua.core.engine;

public record MLuaValue(Object raw) {
   public static final MLuaValue NIL = new MLuaValue((Object)null);

   public MLuaValue(Object raw) {
      this.raw = raw;
   }

   public Object raw() {
      return this.raw;
   }
}
