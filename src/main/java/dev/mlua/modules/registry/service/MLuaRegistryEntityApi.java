package dev.mlua.modules.registry.service;

import java.util.List;

import java.util.Map;
import java.util.function.Supplier;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType.Builder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import dev.mlua.core.MLuaLogger;
import dev.mlua.loaderapi.RegistryHandle;
import dev.mlua.loaderapi.RegistryKind;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import dev.mlua.minecraft.binding.MLuaRegistryProperties;
import dev.mlua.minecraft.scripted.MLuaBehaviorRegistry;
import dev.mlua.minecraft.scripted.MLuaScriptedEntity;
import dev.mlua.runtime.registry.MLuaLoaderRegistry;
import dev.mlua.runtime.registry.MLuaTextureDescriptor;

/** Installs scriptable entity and spawn egg registration endpoints. */
final class MLuaRegistryEntityApi {
   private MLuaRegistryEntityApi() { }

   static void install(MLuaRegistryService service, LuaTable entities) {
      entities.set("Register", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            service.ensureRegistryPhase();
            String name = args.checkjstring(2);
            String owner = service.apiContext().mlua().ownerModId();
            MLuaRegistryContentValidation.requireLocalRegistryName(owner, name, "Registry.Entities:Register");
            LuaTable propsTable = MLuaRegistryProperties.table(service.apiContext().mlua().registration(),
               args.arg(3), "EntityProperties", "Registry.Entities:Register");
            String key = owner + ":" + name;
            dev.mlua.module.MLuaRegistration registration = service.apiContext().mlua().registration();
            String className = MLuaRegistryContentValidation.requireContentClass(registration, propsTable, "ScriptedEntity");
            int aiInterval = MLuaLuaArguments.optionalWholeInt(
                propsTable == null ? LuaValue.NIL : propsTable.get("aiInterval"), 5,
                "Registry.Entities:Register", "aiInterval");
            int trackingRange = MLuaLuaArguments.optionalWholeInt(
                propsTable == null ? LuaValue.NIL : propsTable.get("trackingRange"), 8,
                "Registry.Entities:Register", "trackingRange");
            int updateInterval = MLuaLuaArguments.optionalWholeInt(
                propsTable == null ? LuaValue.NIL : propsTable.get("updateInterval"), 3,
                "Registry.Entities:Register", "updateInterval");
            if (trackingRange < 0) {
                throw new LuaError("[MLua] Registry.Entities:Register trackingRange must be zero or greater.");
            }
            if (updateInterval < 1) {
                throw new LuaError("[MLua] Registry.Entities:Register updateInterval must be greater than zero.");
            }
             LuaValue onTick = propsTable != null ? propsTable.get("onTick") : LuaValue.NIL;
             LuaValue onHurt = propsTable != null ? propsTable.get("onHurt") : LuaValue.NIL;
             LuaValue onDeath = propsTable != null ? propsTable.get("onDeath") : LuaValue.NIL;
             LuaValue onAI = propsTable != null ? propsTable.get("AI") : LuaValue.NIL;
             LuaValue onSpawn = propsTable != null ? propsTable.get("onSpawn") : LuaValue.NIL;
             LuaValue onInteract = propsTable != null ? propsTable.get("onInteract") : LuaValue.NIL;
             LuaValue onTargetChanged = propsTable != null ? propsTable.get("onTargetChanged") : LuaValue.NIL;
             LuaValue onRemove = propsTable != null ? propsTable.get("onRemove") : LuaValue.NIL;
             MLuaBehaviorRegistry.validateEntityAI(onAI);
             MLuaRegistryContentValidation.validateCallback("onTick", onTick);
             MLuaRegistryContentValidation.validateCallback("onHurt", onHurt);
             MLuaRegistryContentValidation.validateCallback("onDeath", onDeath);
             MLuaRegistryContentValidation.validateCallback("onSpawn", onSpawn);
             MLuaRegistryContentValidation.validateCallback("onInteract", onInteract);
             MLuaRegistryContentValidation.validateCallback("onTargetChanged", onTargetChanged);
             MLuaRegistryContentValidation.validateCallback("onRemove", onRemove);
             LuaValue attributesValue = propsTable != null ? propsTable.get("attributes") : LuaValue.NIL;
             if (!attributesValue.isnil() && !attributesValue.istable()) {
                throw new LuaError("[MLua] Registry.Entities:Register attributes must be a table.");
             }
             LuaTable attr = attributesValue.istable() ? attributesValue.checktable() : null;
             Map<String, Double> additionalAttributes = MLuaRegistryEntityDefinitions.additionalAttributes(attr);
             double maxHealth = MLuaRegistryEntityDefinitions.attributeValue(attr, "maxHealth", 20.0);
             double speed = MLuaRegistryEntityDefinitions.attributeValue(attr, "movementSpeed", 0.25);
             double attack = MLuaRegistryEntityDefinitions.attributeValue(attr, "attackDamage", 2.0);
             double armor = MLuaRegistryEntityDefinitions.attributeValue(attr, "armor", 0.0);
             double armorToughness = MLuaRegistryEntityDefinitions.attributeValue(attr, "armorToughness", 0.0);
             double attackSpeed = MLuaRegistryEntityDefinitions.attributeValue(attr, "attackSpeed", 4.0);
             double knockbackResistance = MLuaRegistryEntityDefinitions.attributeValue(attr, "knockbackResistance", 0.0);
             double followRange = MLuaRegistryEntityDefinitions.attributeValue(attr, "followRange", 16.0);
             double width = propsTable != null ? propsTable.get("width").optdouble(0.6) : 0.6;
             double height = propsTable != null ? propsTable.get("height").optdouble(1.8) : 1.8;
             MLuaRegistryEntityDefinitions.requirePositiveSize("width", width);
             MLuaRegistryEntityDefinitions.requirePositiveSize("height", height);
             MLuaTextureDescriptor entityTexture = MLuaRegistryVisualDefinitions.textureDescriptor(propsTable, "texture", "Texture", "entityTexture", "EntityTexture").resolve(owner, "entity");
             String entityModel = MLuaRegistryPropertyReaders.stringProperty(propsTable, "model", "Model", "entityModel", "EntityModel");
             float shadowRadius = propsTable != null ? (float)propsTable.get("shadowRadius").optdouble(0.5) : 0.5F;
             if (!Float.isFinite(shadowRadius) || shadowRadius < 0.0F) {
                throw new LuaError("[MLua] Registry.Entities:Register shadowRadius must be a finite non-negative number.");
             }
             MobCategory category = MLuaRegistryEntityDefinitions.mobCategory(propsTable);
             EntityType.EntityFactory<MLuaScriptedEntity> factory = (type, level) -> {
                return new MLuaScriptedEntity((EntityType<? extends MLuaScriptedEntity>)(EntityType<?>)type, level, key, className);
             };
             EntityType.Builder<MLuaScriptedEntity> builder = Builder.of(factory, category).sized((float)width, (float)height).clientTrackingRange(trackingRange).updateInterval(updateInterval);
             Supplier<AttributeSupplier> attrSupplier = () -> {
                AttributeSupplier.Builder attributeBuilder = Mob.createMobAttributes()
                   .add(Attributes.MAX_HEALTH, maxHealth)
                   .add(Attributes.MOVEMENT_SPEED, speed)
                   .add(Attributes.ATTACK_DAMAGE, attack)
                   .add(Attributes.ARMOR, armor)
                   .add(Attributes.ARMOR_TOUGHNESS, armorToughness)
                   .add(Attributes.ATTACK_SPEED, attackSpeed)
                   .add(Attributes.KNOCKBACK_RESISTANCE, knockbackResistance)
                   .add(Attributes.FOLLOW_RANGE, followRange);
                additionalAttributes.forEach((attributeId, value) ->
                   MLuaRegistryEntityDefinitions.addAttribute(attributeBuilder, attributeId, value));
                return attributeBuilder.build();
             };
             service.registerEntityVisual(owner, name,
                entityTexture.all() != null ? entityTexture.all() : "minecraft:textures/entity/zombie/zombie.png",
                entityModel, shadowRadius);
             MLuaLoaderRegistry.registerEntity(owner, name, () -> {
                //--version 26.1
                ResourceKey<EntityType<?>> entityKey = ResourceKey.create(
                   Registries.ENTITY_TYPE, MLuaResourceLocations.parse(owner + ":" + name));
                return builder.build(entityKey);
                //--endversion
                //--version 1.20.1,1.21.1
                return builder.build(owner + ":" + name);
                //--endversion
             }, attrSupplier);
             service.replaceBehaviors("entity", key, Map.of(
                "onTick", onTick, "onHurt", onHurt, "onDeath", onDeath, "AI", onAI,
                "onSpawn", onSpawn, "onInteract", onInteract, "onTargetChanged", onTargetChanged,
                "onRemove", onRemove));
             service.setEntityAIInterval(key, Math.max(1, Math.min(120, aiInterval)));
             MLuaLogger.LOGGER.info("Registered MLua scripted entity '{}' with AI callback={}", key, !onAI.isnil());
             return service.registryEntry(RegistryKind.ENTITY, owner, name);
         }
      });
      entities.set("Properties", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            return MLuaRegistryProperties.create(service.apiContext().mlua().registration(), "EntityProperties");
         }
      });
      entities.set("RegisterSpawnEgg", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            service.ensureRegistryPhase();
            String name = args.checkjstring(2);
            String owner = service.apiContext().mlua().ownerModId();
            MLuaRegistryContentValidation.requireLocalRegistryName(owner, name, "Registry.Entities:RegisterSpawnEgg");
            LuaTable propsTable = args.arg(3).opttable((LuaTable)null);
            List<String> description = MLuaRegistryDescriptions.read(propsTable);

            String entityName = propsTable != null ? propsTable.get("entity").optjstring(name) : name;
            if (!entityName.contains(":")) entityName = owner + ":" + entityName;
            var entityId = MLuaResourceLocations.tryParseOrNull(entityName);
            if (entityId == null || !entityId.toString().equals(entityName)) {
               throw new LuaError("[MLua] Registry.Entities:RegisterSpawnEgg entity must be a valid lowercase namespaced entity id.");
            }

            int primaryColor = MLuaRegistryEntityDefinitions.colorProperty(propsTable, "primaryColor", 16777215);
            int secondaryColor = MLuaRegistryEntityDefinitions.colorProperty(propsTable, "secondaryColor", 0);
            RegistryHandle<EntityType<?>> entityType = MLuaLoaderRegistry.get(
               RegistryKind.ENTITY, entityId.getNamespace(), entityId.getPath());

            if (entityType == null) {
               throw new LuaError("[MLua] Entity '" + entityName + "' not registered yet. Register the entity before its spawn egg.");
            }

            Map<String, LuaValue> itemCallbacks = MLuaRegistryLuaValues.collectCallbacks(propsTable,
               "onUse", "onUseOnBlock", "onInventoryTick", "onCrafted", "onConsume", "onAttackEntity");
            MLuaLoaderRegistry.registerSpawnEgg(owner, name, entityType, primaryColor, secondaryColor);
            service.updateVisualMetadata(RegistryKind.ITEM, owner, name, previous ->
               new MLuaRegistryVisualOperations.Metadata(previous.copy(), previous.copyModel(),
                  description, previous.texture(), previous.model()));
            service.replaceBehaviors("item", owner + ":" + name, itemCallbacks);
            return service.registryEntry(RegistryKind.ITEM, owner, name);
         }
      });
   }
}
