package dev.mlua.modules.attribute.Service;

import dev.mlua.common.enums.Side;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServiceBase;
import dev.mlua.minecraft.MLuaResourceLocations;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class MLuaAttributeService extends MLuaServiceBase {
   public MLuaAttributeService(MLuaService.Context context) {
      super(context);
   }

   @Override
   public String name() {
      return "Attribute";
   }

   @Override
   public LuaTable toLua() {
      LuaTable table = new LuaTable();
      table.set("__side", "Server");
      table.set("GetAttribute", fn(args -> {
         requireServer();
         LivingEntity entity = extractEntity(args.arg(2));
         Holder<Attribute> attr = resolveAttribute(args.checkjstring(3));
         if (attr == null) {
            return LuaValue.NIL;
         }
         AttributeInstance instance = entity.getAttribute(attr);
         if (instance == null) {
            return LuaValue.NIL;
         }
         LuaTable result = new LuaTable();
         result.set("Base", LuaValue.valueOf(instance.getBaseValue()));
         result.set("Value", LuaValue.valueOf(instance.getValue()));
         result.set("DefaultValue", LuaValue.valueOf(instance.getAttribute().value().getDefaultValue()));
         return result;
      }));
      table.set("SetBaseValue", fn(args -> {
         requireServer();
         LivingEntity entity = extractEntity(args.arg(2));
         Holder<Attribute> attr = resolveAttribute(args.checkjstring(3));
         if (attr == null) {
            return LuaValue.FALSE;
         }
         AttributeInstance instance = entity.getAttribute(attr);
         if (instance == null) {
            return LuaValue.FALSE;
         }
         instance.setBaseValue(args.checkdouble(4));
         return LuaValue.TRUE;
      }));
      table.set("GetModifiers", fn(args -> {
         requireServer();
         LivingEntity entity = extractEntity(args.arg(2));
         Holder<Attribute> attr = resolveAttribute(args.checkjstring(3));
         if (attr == null) {
            return new LuaTable();
         }
         AttributeInstance instance = entity.getAttribute(attr);
         if (instance == null) {
            return new LuaTable();
         }
         LuaTable list = new LuaTable();
         int i = 1;
         for (AttributeModifier mod : instance.getModifiers()) {
            LuaTable entry = new LuaTable();
            entry.set("Id", mod.id().toString());
            entry.set("Name", mod.id().toString());
            entry.set("Amount", LuaValue.valueOf(mod.amount()));
            entry.set("Operation", LuaValue.valueOf(mod.operation().id()));
            list.set(i++, entry);
         }
         return list;
      }));
      table.set("AddModifier", fn(args -> {
         requireServer();
         LivingEntity entity = extractEntity(args.arg(2));
         Holder<Attribute> attr = resolveAttribute(args.checkjstring(3));
         if (attr == null) {
            return LuaValue.FALSE;
         }
         AttributeInstance instance = entity.getAttribute(attr);
         if (instance == null) {
            return LuaValue.FALSE;
         }
         String name = args.checkjstring(4);
         double amount = args.checkdouble(5);
         int operation = args.optint(6, 0);
         ResourceLocation id = ResourceLocation.fromNamespaceAndPath("mlua", "modifier_" + name.toLowerCase().replaceAll("[^a-z0-9_/.-]", "_"));
         AttributeModifier modifier = new AttributeModifier(id, amount, AttributeModifier.Operation.BY_ID.apply(operation));
         instance.addTransientModifier(modifier);
         return LuaValue.TRUE;
      }));
      table.set("RemoveModifier", fn(args -> {
         requireServer();
         LivingEntity entity = extractEntity(args.arg(2));
         Holder<Attribute> attr = resolveAttribute(args.checkjstring(3));
         if (attr == null) {
            return LuaValue.FALSE;
         }
         AttributeInstance instance = entity.getAttribute(attr);
         if (instance == null) {
            return LuaValue.FALSE;
         }
         String name = args.checkjstring(4);
         ResourceLocation id = ResourceLocation.fromNamespaceAndPath("mlua", "modifier_" + name.toLowerCase().replaceAll("[^a-z0-9_/.-]", "_"));
         instance.removeModifier(id);
         return LuaValue.TRUE;
      }));
      table.set("GetRegisteredAttributes", fn(args -> {
         LuaTable result = new LuaTable();
         int i = 1;
         for (Attribute attr : BuiltInRegistries.ATTRIBUTE) {
            ResourceLocation key = BuiltInRegistries.ATTRIBUTE.getKey(attr);
            if (key != null) {
               result.set(i++, LuaValue.valueOf(key.toString()));
            }
         }
         return result;
      }));
      return table;
   }

   private static LivingEntity extractEntity(LuaValue val) {
      LuaTable t = val.checktable();
      LuaValue entityObj = t.get("__entity");
      if (entityObj.isuserdata(LivingEntity.class)) {
         return (LivingEntity) entityObj.checkuserdata(LivingEntity.class);
      }
      throw new LuaError("[MLua] Expected an Entity table");
   }

   private static Holder<Attribute> resolveAttribute(String id) {
      ResourceLocation key = MLuaResourceLocations.tryParseOrNull(id.contains(":") ? id : "minecraft:" + id);
      return key == null ? null : BuiltInRegistries.ATTRIBUTE.getHolder(key).orElse(null);
   }

   private void requireServer() {
      if (this.context.mlua().side() != Side.SERVER) {
         throw new LuaError("[MLua] Attribute service methods are server-side only");
      }
   }

   private static VarArgFunction fn(final Function<Varargs, LuaValue> body) {
      return new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            try {
               return (Varargs) body.apply(args);
            } catch (LuaError error) {
               throw error;
            } catch (Exception exception) {
               throw new LuaError("[MLua] " + exception.getMessage());
            }
         }
      };
   }

}
