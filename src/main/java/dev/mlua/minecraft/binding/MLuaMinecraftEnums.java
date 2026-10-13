package dev.mlua.minecraft.binding;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import dev.mlua.common.enums.CoreGuiType;
import dev.mlua.common.enums.RenderLayer;
import dev.mlua.modules.enums.MLuaEnumValue;
import java.util.function.Function;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.GameType;

public final class MLuaMinecraftEnums {
   private MLuaMinecraftEnums() {
   }

   public static LuaTable gameModeTable() {
      return MLuaEnumValue.enumConstants(GameType.class, (Function)null);
   }

   public static LuaTable difficultyTable() {
      return MLuaEnumValue.enumConstants(Difficulty.class, (Function)null);
   }

   public static LuaTable directionTable() {
      return MLuaEnumValue.enumConstants(Direction.class, (Function)null);
   }

   public static LuaTable handTable() {
      return MLuaEnumValue.enumConstants(InteractionHand.class, (Function)null);
   }

   public static LuaTable equipmentSlotTable() {
      return MLuaEnumValue.enumConstants(EquipmentSlot.class, (Function)null);
   }

   public static LuaTable soundCategoryTable() {
      return MLuaEnumValue.enumConstants(SoundSource.class, (Function)null);
   }

   public static LuaTable coreGuiTypeTable() {
      return MLuaEnumValue.enumConstants(CoreGuiType.class, (Function)null);
   }

   public static LuaTable renderLayerTable() {
      return MLuaEnumValue.enumConstants(RenderLayer.class, (Function)null);
   }

   public static LuaTable zIndexBehaviorTable() {
      return MLuaEnumValue.enumConstants(dev.mlua.common.enums.ZIndexBehavior.class, (Function)null);
   }

   public static LuaTable resampleModeTable() {
      return MLuaEnumValue.enumConstants(dev.mlua.common.enums.ResampleMode.class, (Function)null);
   }

   public static LuaTable scaleTypeTable() {
      return MLuaEnumValue.enumConstants(dev.mlua.common.enums.ScaleType.class, (Function)null);
   }

   public static LuaTable textXAlignmentTable() {
      return MLuaEnumValue.enumConstants(dev.mlua.common.enums.TextXAlignment.class, (Function)null);
   }

   public static LuaTable textYAlignmentTable() {
      return MLuaEnumValue.enumConstants(dev.mlua.common.enums.TextYAlignment.class, (Function)null);
   }

   public static LuaTable weatherTable() {
      LuaTable t = new LuaTable();
      LuaTable clear = weatherEntry("Clear");
      LuaTable rain = weatherEntry("Rain");
      LuaTable thunder = weatherEntry("Thunder");
      t.set("Clear", clear);
      t.set("Rain", rain);
      t.set("Thunder", thunder);
      return t;
   }

   private static LuaTable weatherEntry(String name) {
      LuaTable entry = new LuaTable();
      entry.set("Name", name);
      LuaTable meta = new LuaTable();
      meta.set("__isWeather", LuaValue.TRUE);
      meta.set("__weatherName", name);
      entry.setmetatable(meta);
      return entry;
   }

   public static String weatherToJava(LuaValue value) {
      if (value.istable()) {
         LuaTable t = value.checktable();
         LuaValue meta = t.getmetatable();
         if (meta.istable()) {
            LuaValue name = meta.checktable().get("__weatherName");
            if (!name.isnil()) {
               return name.tojstring();
            }
         }
      }

      return value.isstring() ? value.tojstring() : "clear";
   }

   public static GameType resolveGameMode(LuaValue value) {
      GameType type;
      if (value.isstring()) {
         type = GameType.byName(value.tojstring(), (GameType)null);
         if (type != null) {
            return type;
         } else {
            throw new LuaError("[MLua] Unknown game mode: " + value.tojstring());
         }
      } else {
         if (MLuaEnumValue.isEnum(value, GameType.class)) {
            type = (GameType)MLuaEnumValue.toJava(value, GameType.class);
            if (type != null) {
               return type;
            }
         }

         throw new LuaError("[MLua] GameMode must be a GameMode constant or string");
      }
   }

   public static Difficulty resolveDifficulty(LuaValue value) {
      Difficulty d;
      if (value.isstring()) {
         d = Difficulty.byName(value.tojstring());
         if (d != null) {
            return d;
         } else {
            throw new LuaError("[MLua] Unknown difficulty: " + value.tojstring());
         }
      } else {
         if (MLuaEnumValue.isEnum(value, Difficulty.class)) {
            d = (Difficulty)MLuaEnumValue.toJava(value, Difficulty.class);
            if (d != null) {
               return d;
            }
         }

         throw new LuaError("[MLua] Difficulty must be a Difficulty constant or string");
      }
   }

   public static LuaValue gameModeOf(GameType type) {
      LuaValue value = MLuaEnumValue.forJava(type);
      return value != null ? value : LuaValue.valueOf(type.getName());
   }

   public static LuaValue difficultyOf(Difficulty d) {
      LuaValue value = MLuaEnumValue.forJava(d);
      return value != null ? value : LuaValue.valueOf(d.getKey());
   }

   public static Direction resolveDirection(LuaValue value) {
      if (value.isstring()) {
         try {
            return Direction.valueOf(value.tojstring().toUpperCase());
         } catch (IllegalArgumentException var2) {
            throw new LuaError("[MLua] Unknown direction: " + value.tojstring());
         }
      } else {
         if (MLuaEnumValue.isEnum(value, Direction.class)) {
            Direction d = (Direction)MLuaEnumValue.toJava(value, Direction.class);
            if (d != null) {
               return d;
            }
         }

         throw new LuaError("[MLua] Direction must be a Direction constant or string");
      }
   }

   public static InteractionHand resolveHand(LuaValue value) {
      if (value.isstring()) {
         try {
            return InteractionHand.valueOf(value.tojstring().toUpperCase());
         } catch (IllegalArgumentException var2) {
            throw new LuaError("[MLua] Unknown hand: " + value.tojstring());
         }
      } else {
         if (MLuaEnumValue.isEnum(value, InteractionHand.class)) {
            InteractionHand h = (InteractionHand)MLuaEnumValue.toJava(value, InteractionHand.class);
            if (h != null) {
               return h;
            }
         }

         throw new LuaError("[MLua] Hand must be a Hand constant or string");
      }
   }

   public static EquipmentSlot resolveSlot(LuaValue value) {
      if (value.isstring()) {
         try {
            return EquipmentSlot.valueOf(value.tojstring().toUpperCase());
         } catch (IllegalArgumentException var2) {
            throw new LuaError("[MLua] Unknown slot: " + value.tojstring());
         }
      } else {
         if (MLuaEnumValue.isEnum(value, EquipmentSlot.class)) {
            EquipmentSlot s = (EquipmentSlot)MLuaEnumValue.toJava(value, EquipmentSlot.class);
            if (s != null) {
               return s;
            }
         }

         throw new LuaError("[MLua] EquipmentSlot must be a EquipmentSlot constant or string");
      }
   }

   public static SoundSource resolveSoundCategory(LuaValue value) {
      if (value.isstring()) {
         try {
            return SoundSource.valueOf(value.tojstring().toUpperCase());
         } catch (IllegalArgumentException var2) {
            throw new LuaError("[MLua] Unknown sound category: " + value.tojstring());
         }
      } else {
         if (MLuaEnumValue.isEnum(value, SoundSource.class)) {
            SoundSource s = (SoundSource)MLuaEnumValue.toJava(value, SoundSource.class);
            if (s != null) {
               return s;
            }
         }

         throw new LuaError("[MLua] SoundCategory must be a SoundCategory constant or string");
      }
   }
}
