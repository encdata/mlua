package dev.mlua.modules.attribute.Service;

import dev.mlua.api.MLuaDescription;
import dev.mlua.common.enums.Side;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.core.service.MLuaService;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.minecraft.binding.MLuaAttributeModifierIds;
import dev.mlua.modules.players.Class.MLuaEntityWrapper;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
//--version 1.20.1
import java.util.UUID;
//--endversion
//--version 1.21.1,26.1
import net.minecraft.core.Holder;
//--endversion
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

@MLuaDescription("Service for managing entity attribute modifiers.")
public final class MLuaAttributeService extends MLuaReflectiveService {
   public MLuaAttributeService(MLuaService.Context context) {
      super(context);
   }

   @Override
   public String name() {
      return "Attribute";
   }

   @Override
   public LuaTable toLua() {
      LuaTable table = super.toLua();
      table.set("__side", "Server");
      return table;
   }

   @MLuaDescription("Returns an entity attribute value by registered attribute name.")
   public LuaValue GetAttribute(LuaValue entityValue, String attributeId) {
      requireServer();
      LivingEntity entity = extractEntity(entityValue);
//--version 1.20.1
      Attribute attribute = resolveAttribute(attributeId);
//--endversion
//--version 1.21.1,26.1
      Holder<Attribute> attribute = resolveAttribute(attributeId);
//--endversion
      if (attribute == null) return LuaValue.NIL;
      AttributeInstance instance = entity.getAttribute(attribute);
      if (instance == null) return LuaValue.NIL;
      LuaTable result = new LuaTable();
      result.set("Base", LuaValue.valueOf(instance.getBaseValue()));
      result.set("Value", LuaValue.valueOf(instance.getValue()));
//--version 1.20.1
      result.set("DefaultValue", LuaValue.valueOf(instance.getAttribute().getDefaultValue()));
//--endversion
//--version 1.21.1,26.1
      result.set("DefaultValue", LuaValue.valueOf(instance.getAttribute().value().getDefaultValue()));
//--endversion
      return result;
   }

   @MLuaDescription("Sets an entity attribute's base value.")
   public boolean SetBaseValue(LuaValue entityValue, String attributeId, double value) {
      requireServer();
      LivingEntity entity = extractEntity(entityValue);
//--version 1.20.1
      Attribute attribute = resolveAttribute(attributeId);
//--endversion
//--version 1.21.1,26.1
      Holder<Attribute> attribute = resolveAttribute(attributeId);
//--endversion
      if (attribute == null) return false;
      AttributeInstance instance = entity.getAttribute(attribute);
      if (instance == null) return false;
      instance.setBaseValue(value);
      return true;
   }

   @MLuaDescription("Returns the modifiers currently applied to an entity attribute.")
   public LuaTable GetModifiers(LuaValue entityValue, String attributeId) {
      requireServer();
      LivingEntity entity = extractEntity(entityValue);
//--version 1.20.1
      Attribute attribute = resolveAttribute(attributeId);
//--endversion
//--version 1.21.1,26.1
      Holder<Attribute> attribute = resolveAttribute(attributeId);
//--endversion
      LuaTable result = new LuaTable();
      if (attribute == null) return result;
      AttributeInstance instance = entity.getAttribute(attribute);
      if (instance == null) return result;
      int index = 1;
      for (AttributeModifier modifier : instance.getModifiers()) {
         LuaTable entry = new LuaTable();
//--version 1.20.1
         entry.set("Id", modifier.getId().toString());
         entry.set("Name", modifier.getName());
         entry.set("Amount", LuaValue.valueOf(modifier.getAmount()));
         entry.set("Operation", LuaValue.valueOf(modifier.getOperation().toValue()));
//--endversion
//--version 1.21.1,26.1
         entry.set("Id", modifier.id().toString());
         entry.set("Name", modifier.id().toString());
         entry.set("Amount", LuaValue.valueOf(modifier.amount()));
         entry.set("Operation", LuaValue.valueOf(modifier.operation().id()));
//--endversion
         result.set(index++, entry);
      }
      return result;
   }

   @MLuaDescription("Adds or replaces a modifier on an entity attribute.")
   public boolean AddModifier(LuaValue entityValue, String attributeId, String name, double amount,
                              Optional<Integer> operationValue) {
      requireServer();
      LivingEntity entity = extractEntity(entityValue);
//--version 1.20.1
      Attribute attribute = resolveAttribute(attributeId);
//--endversion
//--version 1.21.1,26.1
      Holder<Attribute> attribute = resolveAttribute(attributeId);
//--endversion
      if (attribute == null) return false;
      AttributeInstance instance = entity.getAttribute(attribute);
      if (instance == null) return false;
      int operation = operationValue.orElse(0);
//--version 1.20.1
      UUID uuid = MLuaAttributeModifierIds.legacy(name);
      AttributeModifier modifier = new AttributeModifier(uuid, name, amount,
         AttributeModifier.Operation.fromValue(operation));
//--endversion
//--version 1.21.1,26.1
      ResourceLocation id = ResourceLocation.fromNamespaceAndPath("mlua", MLuaAttributeModifierIds.path(name));
      AttributeModifier modifier = new AttributeModifier(id, amount, AttributeModifier.Operation.BY_ID.apply(operation));
//--endversion
      instance.addTransientModifier(modifier);
      return true;
   }

   @MLuaDescription("Removes an attribute modifier by identifier.")
   public boolean RemoveModifier(LuaValue entityValue, String attributeId, String name) {
      requireServer();
      LivingEntity entity = extractEntity(entityValue);
//--version 1.20.1
      Attribute attribute = resolveAttribute(attributeId);
//--endversion
//--version 1.21.1,26.1
      Holder<Attribute> attribute = resolveAttribute(attributeId);
//--endversion
      if (attribute == null) return false;
      AttributeInstance instance = entity.getAttribute(attribute);
      if (instance == null) return false;
//--version 1.20.1
      UUID id = MLuaAttributeModifierIds.legacy(name);
//--endversion
//--version 1.21.1,26.1
      ResourceLocation id = ResourceLocation.fromNamespaceAndPath("mlua", MLuaAttributeModifierIds.path(name));
//--endversion
      instance.removeModifier(id);
      return true;
   }

   @MLuaDescription("Returns the names of attributes available from Minecraft's attribute registry.")
   public LuaTable GetRegisteredAttributes() {
      LuaTable result = new LuaTable();
      int index = 1;
      for (Attribute attribute : BuiltInRegistries.ATTRIBUTE) {
         ResourceLocation key = BuiltInRegistries.ATTRIBUTE.getKey(attribute);
         if (key != null) result.set(index++, LuaValue.valueOf(key.toString()));
      }
      return result;
   }

   private LivingEntity extractEntity(LuaValue value) {
      return (LivingEntity) MLuaEntityWrapper.requireLuaInstance(value, context.mlua().registration(), "LivingEntity", "LivingEntity");
   }

//--version 1.20.1
   private static Attribute resolveAttribute(String id) {
      ResourceLocation key = MLuaResourceLocations.tryParseOrNull(id.contains(":") ? id : "minecraft:" + id);
      return key == null ? null : BuiltInRegistries.ATTRIBUTE.get(key);
   }
//--endversion
//--version 1.21.1
   private static Holder<Attribute> resolveAttribute(String id) {
      ResourceLocation key = MLuaResourceLocations.tryParseOrNull(id.contains(":") ? id : "minecraft:" + id);
      return key == null ? null : BuiltInRegistries.ATTRIBUTE.getHolder(key).orElse(null);
   }
//--endversion
//--version 26.1
   private static Holder<Attribute> resolveAttribute(String id) {
      ResourceLocation key = MLuaResourceLocations.tryParseOrNull(id.contains(":") ? id : "minecraft:" + id);
      return key == null ? null : BuiltInRegistries.ATTRIBUTE.get(key).orElse(null);
   }
//--endversion

   private void requireServer() {
      if (this.context.mlua().side() != Side.SERVER) {
         throw new LuaError("[MLua] Attribute service methods are server-side only");
      }
   }
}
