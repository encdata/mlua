package dev.mlua.modules.combat;

import dev.mlua.core.MLuaLogger;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.luaj.vm2.LuaTable;

public final class MLuaDamageSourceRegistry {
   private static final Map<Key, DamageSourceDef> DAMAGE_SOURCES = new ConcurrentHashMap<>();
   private static final DamageSourceDef GENERIC = new DamageSourceDef(false, false, false, false, false, false, false);

   private MLuaDamageSourceRegistry() {
   }

   public static DamageSourceDef register(String name, LuaTable props) {
      return register(null, "", name, props);
   }

   public static DamageSourceDef register(Object runtimeOwner, String ownerModId, String name, LuaTable props) {
      if (ownerModId != null && !ownerModId.isBlank()) {
         dev.mlua.runtime.registry.MLuaResourceNameValidator.validate(ownerModId);
      }
      dev.mlua.runtime.registry.MLuaResourceNameValidator.validate(name);
      boolean bypassArmor = props != null && (props.get("bypassesArmor").optboolean(false) || props.get("BypassesArmor").optboolean(false));
      boolean isFire = props != null && (props.get("isFire").optboolean(false) || props.get("IsFire").optboolean(false));
      boolean isExplosion = props != null && (props.get("isExplosion").optboolean(false) || props.get("IsExplosion").optboolean(false));
      boolean isMagic = props != null && (props.get("isMagic").optboolean(false) || props.get("IsMagic").optboolean(false));
      boolean isProjectile = props != null && (props.get("isProjectile").optboolean(false) || props.get("IsProjectile").optboolean(false));
      boolean isFall = props != null && (props.get("isFall").optboolean(false) || props.get("IsFall").optboolean(false));
      boolean isLightning = props != null && (props.get("isLightning").optboolean(false) || props.get("IsLightning").optboolean(false));
      DamageSourceDef definition = new DamageSourceDef(bypassArmor, isFire, isExplosion, isMagic, isProjectile, isFall, isLightning);
      Key key = new Key(runtimeOwner, ownerModId, name);
      if (DAMAGE_SOURCES.putIfAbsent(key, definition) != null) {
         throw new IllegalStateException("[MLua] Damage source '" + ownerModId + ":" + name + "' is already registered");
      }
      MLuaLogger.LOGGER.info("Registered custom damage source '{}:{}'", ownerModId, name);
      return definition;
   }

   public static boolean unregister(Object runtimeOwner, String ownerModId, String name, DamageSourceDef expected) {
      if (expected == null) return false;
      Key key = new Key(runtimeOwner, ownerModId, name);
      final boolean[] removed = {false};
      DAMAGE_SOURCES.computeIfPresent(key, (ignored, current) -> {
         if (current == expected) {
            removed[0] = true;
            return null;
         }
         return current;
      });
      return removed[0];
   }

   public static DamageSourceDef get(String name) {
      return get(null, "", name);
   }

   public static DamageSourceDef get(Object runtimeOwner, String ownerModId, String name) {
      DamageSourceDef registered = DAMAGE_SOURCES.get(new Key(runtimeOwner, ownerModId, name));
      if (registered != null) return registered;
      return "generic".equals(name) ? GENERIC : null;
   }

   public static boolean contains(String name) {
      return contains(null, "", name);
   }

   public static boolean contains(Object runtimeOwner, String ownerModId, String name) {
      return get(runtimeOwner, ownerModId, name) != null;
   }

   public static Set<String> names() {
      java.util.HashSet<String> names = new java.util.HashSet<>(DAMAGE_SOURCES.keySet().stream().filter(key -> key.runtimeOwner == null && key.ownerModId.isEmpty()).map(Key::name).toList());
      names.add("generic");
      return Set.copyOf(names);
   }

   public static Set<String> names(Object runtimeOwner, String ownerModId) {
      java.util.HashSet<String> names = new java.util.HashSet<>(DAMAGE_SOURCES.keySet().stream().filter(key -> key.runtimeOwner == runtimeOwner && key.ownerModId.equals(ownerModId)).map(Key::name).toList());
      names.add("generic");
      return Set.copyOf(names);
   }

   public static void clearOwned(Object runtimeOwner, String ownerModId) {
      DAMAGE_SOURCES.keySet().removeIf(key -> key.runtimeOwner == runtimeOwner && key.ownerModId.equals(ownerModId));
   }

   public static void clearRuntime(Object runtimeOwner) {
      DAMAGE_SOURCES.keySet().removeIf(key -> key.runtimeOwner == runtimeOwner);
   }

   public static DamageSourceDef getUnique(Object runtimeOwner, String name) {
      DamageSourceDef found = null;
      for (Map.Entry<Key, DamageSourceDef> entry : DAMAGE_SOURCES.entrySet()) {
         if (entry.getKey().runtimeOwner != runtimeOwner || !entry.getKey().name.equals(name)) continue;
         if (found != null) return null;
         found = entry.getValue();
      }
      return found;
   }

   public record Key(Object runtimeOwner, String ownerModId, String name) {
      public Key { if (ownerModId == null) ownerModId = ""; }
   }

   public record DamageSourceDef(boolean bypassesArmor, boolean isFire, boolean isExplosion, boolean isMagic, boolean isProjectile, boolean isFall, boolean isLightning) {
   }
}
