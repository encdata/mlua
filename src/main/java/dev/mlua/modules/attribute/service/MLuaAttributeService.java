package dev.mlua.modules.attribute.service;

import dev.mlua.api.MLuaDescription;
import dev.mlua.core.service.MLuaServerThreadReflectiveService;
import dev.mlua.core.service.MLuaService;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.minecraft.binding.MLuaAttributeInstance;
import dev.mlua.modules.players.classes.MLuaEntityWrapper;
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
import org.luaj.vm2.LuaError;

@MLuaDescription("Reads and updates living entity attributes and transient modifiers on the server.")
public final class MLuaAttributeService extends MLuaServerThreadReflectiveService {
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
      AttributeInstance instance = findAttributeInstance(entity, attributeId);
      if (instance == null) return LuaValue.NIL;
      return MLuaAttributeInstance.snapshot(instance);
   }

   @MLuaDescription("Returns snapshots of every registered attribute available on this entity, keyed by namespaced attribute id.")
   public LuaTable GetAttributes(LuaValue entityValue) {
      this.requireServerSide(this.name());
      LivingEntity entity = extractEntity(entityValue);
      LuaTable result = new LuaTable();
      for (String id : registeredAttributeIds()) {
         AttributeInstance instance = findAttributeInstance(entity, id);
         if (instance != null) result.set(id, MLuaAttributeInstance.snapshot(instance));
      }
      return result;
   }

   @MLuaDescription("Returns the live AttributeInstance class for an entity attribute, or nil when the entity does not have it.")
   public LuaValue GetAttributeInstance(LuaValue entityValue, String attributeId) {
      this.requireServerSide(this.name());
      LivingEntity entity = extractEntity(entityValue);
      String id = attributeLocation(attributeId).toString();
      AttributeInstance instance = findAttributeInstance(entity, attributeId);
      if (instance == null) return LuaValue.NIL;
      return MLuaAttributeInstance.create(this.context.mlua().registration(), id, instance, entityValue.checktable());
   }

   @MLuaDescription("Sets an entity attribute's base value to a finite number.")
   public boolean SetBaseValue(LuaValue entityValue, String attributeId, double value) {
      this.requireServerSide(this.name());
      LivingEntity entity = extractEntity(entityValue);
      AttributeInstance instance = findAttributeInstance(entity, attributeId);
      if (instance == null) return false;
      MLuaAttributeInstance.finiteValue(value, "base value");
      instance.setBaseValue(value);
      return true;
   }

   @MLuaDescription("Resets an entity attribute's base value to its registered default. Returns false when the attribute is unavailable.")
   public boolean ResetBaseValue(LuaValue entityValue, String attributeId) {
      this.requireServerSide(this.name());
      LivingEntity entity = extractEntity(entityValue);
      AttributeInstance instance = findAttributeInstance(entity, attributeId);
      if (instance == null) return false;
      MLuaAttributeInstance.resetBaseValue(instance);
      return true;
   }

   @MLuaDescription("Returns the modifiers currently applied to an entity attribute.")
   public LuaTable GetModifiers(LuaValue entityValue, String attributeId) {
      this.requireServerSide(this.name());
      LivingEntity entity = extractEntity(entityValue);
      LuaTable result = new LuaTable();
      AttributeInstance instance = findAttributeInstance(entity, attributeId);
      if (instance == null) return result;
      return MLuaAttributeInstance.modifiers(instance);
   }

   @MLuaDescription("Checks whether a named MLua transient modifier is currently applied to an entity attribute.")
   public boolean HasModifier(LuaValue entityValue, String attributeId, String name) {
      this.requireServerSide(this.name());
      name = modifierName(name);
      LivingEntity entity = extractEntity(entityValue);
      AttributeInstance instance = findAttributeInstance(entity, attributeId);
      return instance != null && MLuaAttributeInstance.hasTransientModifier(instance, name);
   }

   @MLuaDescription("Adds or replaces a transient modifier by name using a finite amount and operation 0, 1, or 2.")
   public boolean AddModifier(LuaValue entityValue, String attributeId, String name, double amount,
                              Optional<Integer> operationValue) {
      this.requireServerSide(this.name());
      name = modifierName(name);
      LivingEntity entity = extractEntity(entityValue);
      AttributeInstance instance = findAttributeInstance(entity, attributeId);
      if (instance == null) return false;
      int operation = operationValue.orElse(0);
      MLuaAttributeInstance.addTransientModifier(instance, name, amount, operation);
      return true;
   }

   @MLuaDescription("Removes a transient modifier by name and returns whether it existed.")
   public boolean RemoveModifier(LuaValue entityValue, String attributeId, String name) {
      this.requireServerSide(this.name());
      name = modifierName(name);
      LivingEntity entity = extractEntity(entityValue);
      AttributeInstance instance = findAttributeInstance(entity, attributeId);
      if (instance == null) return false;
      return MLuaAttributeInstance.removeTransientModifier(instance, name);
   }

   @MLuaDescription("Returns the names of attributes available from Minecraft's attribute registry.")
   public LuaTable GetRegisteredAttributes() {
      this.requireServerSide(this.name());
      LuaTable result = new LuaTable();
      java.util.List<String> ids = registeredAttributeIds();
      for (int index = 0; index < ids.size(); index++) {
         result.set(index + 1, LuaValue.valueOf(ids.get(index)));
      }
      return result;
   }

   private static java.util.List<String> registeredAttributeIds() {
      java.util.List<String> ids = new java.util.ArrayList<>();
      for (Attribute attribute : BuiltInRegistries.ATTRIBUTE) {
         ResourceLocation key = BuiltInRegistries.ATTRIBUTE.getKey(attribute);
         if (key != null) ids.add(key.toString());
      }
      ids.sort(String::compareTo);
      return ids;
   }

   /** Resolves the registry entry and the entity's live instance using this MC version's registry API. */
   private static AttributeInstance findAttributeInstance(LivingEntity entity, String attributeId) {
//--version 1.20.1
      Attribute attribute = resolveAttribute(attributeId);
//--endversion
//--version 1.21.1,26.1
      Holder<Attribute> attribute = resolveAttribute(attributeId);
//--endversion
      return attribute == null ? null : entity.getAttribute(attribute);
   }

   private LivingEntity extractEntity(LuaValue value) {
      return (LivingEntity) MLuaEntityWrapper.requireLuaInstance(value, context.mlua().registration(), "LivingEntity", "LivingEntity");
   }

   private static String modifierName(String name) {
      return MLuaAttributeInstance.normalizeModifierName(name);
   }

   private static ResourceLocation attributeLocation(String id) {
      if (id == null || id.isBlank()) {
         throw new LuaError("[MLua] Attribute id must be a non-empty resource location.");
      }
      String value = id.trim();
      ResourceLocation location = MLuaResourceLocations.tryParseOrNull(value.contains(":") ? value : "minecraft:" + value);
      if (location == null) throw new LuaError("[MLua] Invalid attribute id: " + id);
      return location;
   }

//--version 1.20.1
   private static Attribute resolveAttribute(String id) {
      return BuiltInRegistries.ATTRIBUTE.get(attributeLocation(id));
   }
//--endversion
//--version 1.21.1
   private static Holder<Attribute> resolveAttribute(String id) {
      return BuiltInRegistries.ATTRIBUTE.getHolder(attributeLocation(id)).orElse(null);
   }
//--endversion
//--version 26.1
   private static Holder<Attribute> resolveAttribute(String id) {
      return BuiltInRegistries.ATTRIBUTE.get(attributeLocation(id)).orElse(null);
   }
//--endversion
}
