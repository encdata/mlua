package dev.mlua.common.mixin;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Process-wide mapping resolver installed by the active loader provider. */
public final class MLuaMappingResolvers {
   private static final AtomicReference<MLuaMappingResolver> ACTIVE = new AtomicReference<>();

   private MLuaMappingResolvers() {
   }

   public static MLuaMappingResolver current() {
      MLuaMappingResolver resolver = ACTIVE.get();
      return resolver != null ? resolver : MLuaMixinTargetMapper.legacyResolver();
   }

   public static void install(MLuaMappingResolver resolver) {
      ACTIVE.set(Objects.requireNonNull(resolver, "resolver"));
   }

   public static void clear() {
      ACTIVE.set(null);
   }

   public static boolean isInstalled() {
      return ACTIVE.get() != null;
   }
}
