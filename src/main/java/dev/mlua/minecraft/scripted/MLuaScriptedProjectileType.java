package dev.mlua.minecraft.scripted;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import dev.mlua.loaderapi.RegistryHandle;
import java.lang.reflect.Method;

public final class MLuaScriptedProjectileType {
   public static final String MOD_ID = "mlua";
   public static final String ENTITY_NAME = "scripted_projectile";
   private static RegistryHandle<EntityType<MLuaScriptedProjectile>> PROJECTILE_REGISTRY_OBJECT;

   private MLuaScriptedProjectileType() {}

   @SuppressWarnings("unchecked")
   public static EntityType<MLuaScriptedProjectile> createType() {
      try {
         Class<?> builderClass = EntityType.Builder.class;
         EntityType.EntityFactory<MLuaScriptedProjectile> factory = MLuaScriptedProjectile::new;
          Object builder = invokeStatic(builderClass, new String[]{"of", "create", "m_20704_", "method_5903"},
            new Class<?>[]{EntityType.EntityFactory.class, MobCategory.class},
            factory, MobCategory.MISC);
          builder = invoke(builder, new String[]{"sized", "m_20699_", "method_17687"},
            new Class<?>[]{float.class, float.class}, 0.25f, 0.25f);
          builder = invoke(builder, new String[]{"clientTrackingRange", "m_20702_", "maxTrackingRange", "method_27299"},
            new Class<?>[]{int.class}, 10);
          builder = invoke(builder, new String[]{"updateInterval", "m_20717_", "trackingTickInterval", "method_27300"},
            new Class<?>[]{int.class}, 2);
          return (EntityType<MLuaScriptedProjectile>) invoke(builder, new String[]{"build", "m_20712_", "method_5905"},
            new Class<?>[]{String.class}, MOD_ID + ":" + ENTITY_NAME);
      } catch (ReflectiveOperationException e) {
         throw new IllegalStateException("Unable to create MLua scripted projectile type", e);
      }
   }

   public static void setRegistryHandle(RegistryHandle<EntityType<MLuaScriptedProjectile>> obj) {
      PROJECTILE_REGISTRY_OBJECT = obj;
   }

   public static EntityType<MLuaScriptedProjectile> get() {
      return PROJECTILE_REGISTRY_OBJECT != null ? PROJECTILE_REGISTRY_OBJECT.get() : null;
   }

   private static Object invokeStatic(Class<?> owner, String[] names, Class<?>[] parameterTypes, Object... args) throws ReflectiveOperationException {
      Method method = findMethod(owner, names, parameterTypes);
      return method.invoke(null, args);
   }

   private static Object invoke(Object target, String[] names, Class<?>[] parameterTypes, Object... args) throws ReflectiveOperationException {
      Method method = findMethod(target.getClass(), names, parameterTypes);
      return method.invoke(target, args);
   }

   private static Method findMethod(Class<?> owner, String[] names, Class<?>[] parameterTypes) throws NoSuchMethodException {
      for (String name : names) {
         try {
            Method method = owner.getMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
         } catch (NoSuchMethodException ignored) {
         }
      }
      throw new NoSuchMethodException(owner.getName() + "#" + String.join("/", names));
   }
}
