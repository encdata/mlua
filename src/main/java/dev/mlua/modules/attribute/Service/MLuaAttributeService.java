package dev.mlua.modules.attribute.Service;

import dev.mlua.api.MLuaDescription;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.core.service.MLuaService;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.minecraft.binding.MLuaAttributeInstance;
import dev.mlua.modules.players.Class.MLuaEntityWrapper;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
//--version 1.21.1,26.1
import net.minecraft.core.Holder;
//--endversion
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

@MLuaDescription("Reads and updates living entity attributes and transient modifiers on the server.")
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

   @MLuaDescription("Returns a snapshot containing the base, effective, and default values for a registered attribute.")
   public LuaValue GetAttribute(LuaValue entityValue, String attributeId) {
      this.requireServerSide(this.name());
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
      return MLuaAttributeInstance.snapshot(instance);
   }

   @MLuaDescription("Returns the live AttributeInstance class for an entity attribute, or nil when the entity does not have it.")
   public LuaValue GetAttributeInstance(LuaValue entityValue, String attributeId) {
      this.requireServerSide(this.name());
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
      String id = MLuaResourceLocations.parse(attributeId.contains(":") ? attributeId : "minecraft:" + attributeId).toString();
      return MLuaAttributeInstance.create(this.context.mlua().registration(), id, instance, entityValue.checktable());
   }

   @MLuaDescription("Sets an entity attribute's base value to a finite number.")
   public boolean SetBaseValue(LuaValue entityValue, String attributeId, double value) {
      this.requireServerSide(this.name());
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
      MLuaAttributeInstance.finiteValue(value, "base value");
      instance.setBaseValue(value);
      return true;
   }

   @MLuaDescription("Resets an entity attribute's base value to its registered default. Returns false when the attribute is unavailable.")
   public boolean ResetBaseValue(LuaValue entityValue, String attributeId) {
      this.requireServerSide(this.name());
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
      MLuaAttributeInstance.resetBaseValue(instance);
      return true;
   }

   @MLuaDescription("Returns the modifiers currently applied to an entity attribute.")
   public LuaTable GetModifiers(LuaValue entityValue, String attributeId) {
      this.requireServerSide(this.name());
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
      return MLuaAttributeInstance.modifiers(instance);
   }

   @MLuaDescription("Checks whether a named MLua transient modifier is currently applied to an entity attribute.")
   public boolean HasModifier(LuaValue entityValue, String attributeId, String name) {
      this.requireServerSide(this.name());
      LivingEntity entity = extractEntity(entityValue);
//--version 1.20.1
      Attribute attribute = resolveAttribute(attributeId);
//--endversion
//--version 1.21.1,26.1
      Holder<Attribute> attribute = resolveAttribute(attributeId);
//--endversion
      if (attribute == null) return false;
      AttributeInstance instance = entity.getAttribute(attribute);
      return instance != null && MLuaAttributeInstance.hasTransientModifier(instance, name);
   }

   @MLuaDescription("Adds or replaces a transient modifier by name using a finite amount and operation 0, 1, or 2.")
   public boolean AddModifier(LuaValue entityValue, String attributeId, String name, double amount,
                              Optional<Integer> operationValue) {
      this.requireServerSide(this.name());
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
      MLuaAttributeInstance.addTransientModifier(instance, name, amount, operation);
      return true;
   }

   @MLuaDescription("Removes a transient modifier by name and returns whether it existed.")
   public boolean RemoveModifier(LuaValue entityValue, String attributeId, String name) {
      this.requireServerSide(this.name());
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
      return MLuaAttributeInstance.removeTransientModifier(instance, name);
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
}
