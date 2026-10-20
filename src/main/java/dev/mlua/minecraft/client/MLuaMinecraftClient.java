package dev.mlua.minecraft.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

public final class MLuaMinecraftClient {
   private static final Method GET_INSTANCE = findInstanceMethod();

   private MLuaMinecraftClient() {
   }

   public static Minecraft instance() {
      if (GET_INSTANCE != null) {
         try {
            return (Minecraft)GET_INSTANCE.invoke((Object)null);
         } catch (Exception var1) {
         }
      }

      throw new IllegalStateException("[MLua] Failed to resolve Minecraft client instance.");
   }

   public static LocalPlayer localPlayer() {
      Minecraft minecraft = instance();
      String[] var1 = new String[]{"player", "player"};
      int var2 = var1.length;

      int var3;
      for(var3 = 0; var3 < var2; ++var3) {
         String name = var1[var3];

         try {
            Field field = Minecraft.class.getDeclaredField(name);
            if (LocalPlayer.class.isAssignableFrom(field.getType())) {
               field.setAccessible(true);
               return (LocalPlayer)field.get(minecraft);
            }
         } catch (Exception var6) {
         }
      }

      Field[] var8 = Minecraft.class.getDeclaredFields();
      var2 = var8.length;

      for(var3 = 0; var3 < var2; ++var3) {
         Field field = var8[var3];
         if (!Modifier.isStatic(field.getModifiers()) && LocalPlayer.class.isAssignableFrom(field.getType())) {
            try {
               field.setAccessible(true);
               return (LocalPlayer)field.get(minecraft);
            } catch (Exception var7) {
            }
         }
      }

      return null;
   }

   public static void showActionBar(Component message) {
      Minecraft minecraft = instance();
      if (minecraft.gui != null) {
         minecraft.gui.setOverlayMessage(message, false);
      }
   }

   private static Method findInstanceMethod() {
      String[] var0 = new String[]{"getInstance", "getInstance"};
      int var1 = var0.length;

      int var2;
      for(var2 = 0; var2 < var1; ++var2) {
         String name = var0[var2];

         try {
            Method method = Minecraft.class.getMethod(name);
            if (Modifier.isStatic(method.getModifiers()) && method.getReturnType() == Minecraft.class) {
               method.setAccessible(true);
               return method;
            }
         } catch (NoSuchMethodException var5) {
         }
      }

      Method[] var6 = Minecraft.class.getMethods();
      var1 = var6.length;

      for(var2 = 0; var2 < var1; ++var2) {
         Method method = var6[var2];
         if (Modifier.isStatic(method.getModifiers()) && method.getParameterCount() == 0 && method.getReturnType() == Minecraft.class) {
            method.setAccessible(true);
            return method;
         }
      }

      return null;
   }
}
