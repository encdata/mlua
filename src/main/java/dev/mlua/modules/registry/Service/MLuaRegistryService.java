package dev.mlua.modules.registry.Service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import dev.mlua.core.MLuaLogger;
import dev.mlua.common.enums.RegistrationPhase;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServiceBase;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.loaderapi.GeneratedAssetKind;
import dev.mlua.loaderapi.RegistryHandle;
import dev.mlua.loaderapi.RegistryKind;
import dev.mlua.runtime.registry.MLuaAssetResolver;
import dev.mlua.runtime.registry.MLuaBlockFactory;
import dev.mlua.runtime.registry.MLuaCreativeTabFactory;
import dev.mlua.runtime.config.MLuaConfigRegistry;
import dev.mlua.runtime.registry.MLuaItemFactory;
import dev.mlua.runtime.registry.MLuaLoaderRegistry;
import dev.mlua.runtime.registry.MLuaNamedItem;
import dev.mlua.runtime.registry.MLuaRegistryNameHelper;
import dev.mlua.runtime.registry.MLuaTextureDescriptor;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.minecraft.binding.MLuaRegistryEntry;
import dev.mlua.minecraft.scripted.MLuaBehaviorRegistry;
import dev.mlua.minecraft.scripted.MLuaScriptedBlock;
import dev.mlua.minecraft.scripted.MLuaScriptedEntity;
import dev.mlua.minecraft.scripted.MLuaScriptedBlockItem;
import dev.mlua.minecraft.scripted.MLuaScriptedItem;
import dev.mlua.minecraft.scripted.MLuaScriptedMobEffect;
import dev.mlua.modules.combat.MLuaDamageSourceRegistry;
import dev.mlua.modules.combat.MLuaProjectileDefinition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
//--version 26.1
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
//--endversion
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityType.Builder;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.level.block.Block;

public final class MLuaRegistryService extends MLuaServiceBase {
   private static final Map<String, RegistryEndpoint> REGISTRY_ENDPOINTS = registryEndpoints();
   private final MLuaItemFactory itemFactory = new MLuaItemFactory();
   private final MLuaBlockFactory blockFactory = new MLuaBlockFactory();
   private final MLuaCreativeTabFactory tabFactory = new MLuaCreativeTabFactory();

   public MLuaRegistryService(MLuaService.Context context) {
      super(context);
   }

   private static Map<String, RegistryEndpoint> registryEndpoints() {
      Map<String, RegistryEndpoint> endpoints = new HashMap<>();
      addEndpoint(endpoints, "Items", "Register", "item", "Items", RegistryKind.ITEM, List.of("item", "items"), "Register:3");
      addEndpoint(endpoints, "Blocks", "Register", "block", "Blocks", RegistryKind.BLOCK, List.of("block", "blocks"), "Register:3");
      addEndpoint(endpoints, "Blocks", "RegisterItem", "item", "Items", null, List.of("block_item", "blockitem", "blockitems"));
      addEndpoint(endpoints, "Entities", "Register", "entity", "Entities", RegistryKind.ENTITY, List.of("entity", "entities", "entity_type", "entity_types"), "Register:3");
      addEndpoint(endpoints, "Effects", "Register", "effect", "Effects", RegistryKind.MOB_EFFECT, List.of("effect", "effects", "mob_effect", "mob_effects", "mobeffect"), "Register:3");
      addEndpoint(endpoints, "Potions", "Register", "potion", "Potions", RegistryKind.POTION, List.of("potion", "potions"), "Register:3");
      addEndpoint(endpoints, "Particles", "Register", "particle", "Particles", RegistryKind.PARTICLE, List.of("particle", "particles"), "Register:3");
      addEndpoint(endpoints, "Containers", "Register", "container", "Containers", null, List.of("container", "containers"), "Register:3");
      addEndpoint(endpoints, "Projectiles", "Register", "projectile", "Projectiles", null, List.of("projectile", "projectiles"), "Register:3");
      addEndpoint(endpoints, "DataPack", "Register", "datapack", "DataPack", null, List.of("data", "datapack", "data_pack"), "Register:4");
      addEndpoint(endpoints, "Tabs", "Register", "tab", "Tabs", RegistryKind.CREATIVE_TAB, List.of("tab", "tabs", "creative_tab", "creative_tabs"), "Register:3");
      addEndpoint(endpoints, "Recipes", "Register", "recipe", "Recipes", null, List.of("recipe", "recipes"), "Register:3", "RegisterShaped:3", "RegisterShapeless:3", "RegisterSmelting:3", "RegisterStonecutting:3");
      addEndpoint(endpoints, "Loot", "Register", "loot", "Loot", null, List.of("loot"), "Register:3", "RegisterEntity:3", "RegisterSimpleBlock:3", "RegisterSelfBlock:3", "RegisterSilkTouchBlock:3", "RegisterOreBlock:3");
      addEndpoint(endpoints, "Tags", "Register", "tag", "Tags", null, List.of("tag", "tags"), "Register:4", "RegisterItem:3", "RegisterBlock:3", "RegisterEntity:3");
      addEndpoint(endpoints, "Config", "RegisterCommon", "config", "Config", null, List.of("config"), "Register:4", "RegisterCommon:3", "RegisterClient:3", "RegisterServer:3");
      addEndpoint(endpoints, "DamageSource", "Register", "damage_source", "DamageSource", null, List.of("damage_source", "damagesource"), "Register:3");
      return Map.copyOf(endpoints);
   }

   private static void addEndpoint(Map<String, RegistryEndpoint> endpoints, String table, String registerMethod,
                                   String typeDomain, String typeTable, RegistryKind registryKind,
                                   List<String> aliases,
                                   String... typedDefinitions) {
      RegistryEndpoint endpoint = new RegistryEndpoint(table, registerMethod, typeDomain, typeTable, registryKind,
         List.of(typedDefinitions));
      for (String alias : aliases) {
         RegistryEndpoint previous = endpoints.putIfAbsent(alias, endpoint);
         if (previous != null) throw new ExceptionInInitializerError("Duplicate Registry category alias: " + alias);
      }
   }

   private record RegistryEndpoint(String table, String registerMethod, String typeDomain,
                                    String typeTable, RegistryKind registryKind,
                                    List<String> typedDefinitions) { }

   public String name() {
      return "Registry";
   }

   public LuaTable toLua() {
      LuaTable table = new LuaTable();
      LuaTable items = registryTable();
      LuaTable blocks = registryTable();
      LuaTable entities = registryTable();
      LuaTable effects = registryTable();
      LuaTable potions = registryTable();
      LuaTable particles = registryTable();
      LuaTable recipes = registryTable();
      LuaTable loot = registryTable();
      LuaTable tags = registryTable();
      LuaTable containers = registryTable();
      LuaTable projectiles = registryTable();
      LuaTable tabs = registryTable();
      LuaTable config = registryTable();
      LuaTable damageSource = registryTable();
      LuaTable dataPack = registryTable();
      LuaTable contentTypes = registryTable();
      table.set("__side", "Registry");
      table.set("Items", items);
      table.set("Blocks", blocks);
      table.set("Entities", entities);
      table.set("Effects", effects);
      table.set("Potions", potions);
      table.set("Particles", particles);
      table.set("Recipes", recipes);
      table.set("Loot", loot);
      table.set("Tags", tags);
      table.set("Containers", containers);
      table.set("Projectiles", projectiles);
      table.set("Tabs", tabs);
      table.set("Config", config);
      table.set("DamageSource", damageSource);
      table.set("DataPack", dataPack);
      table.set("ContentTypes", contentTypes);
      dataPack.set("Register", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            MLuaRegistryService.this.ensureRegistryPhase();
            String resourceType = args.checkjstring(2);
            String id = args.checkjstring(3);
            String owner = MLuaRegistryService.this.context.mlua().ownerModId();
            LuaTable definition = MLuaRegistryService.this.context.mlua().registration().resolveContentType(
               "datapack", owner, args.checktable(4));
            JsonObject json = MLuaRegistryService.jsonObjectArg(definition, "data pack resource");
            try {
               MLuaLoaderRegistry.registerGeneratedDataResource(owner, resourceType, id, json);
            } catch (IllegalArgumentException | IllegalStateException error) {
               throw new LuaError("[MLua] " + error.getMessage());
            }
            return LuaValue.NONE;
         }
      });
      containers.set("Register", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            MLuaRegistryService.this.ensureRegistryPhase();
            String name = args.checkjstring(2);
            LuaTable definition = args.checktable(3);
            int rows = definition.get("Rows").optint(3);
            String title = definition.get("Title").optjstring(MLuaRegistryNameHelper.displayName(name));
            String owner = MLuaRegistryService.this.context.mlua().ownerModId();
            MLuaBehaviorRegistry.registerContainerType(owner + ":" + name, rows, title);
            return LuaValue.NONE;
         }
      });
      items.set("Register", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaRegistryService.this.ensureRegistryPhase();
            String name = args.checkjstring(2);
            String owner = MLuaRegistryService.this.context.mlua().ownerModId();
            LuaTable propsTable = MLuaRegistryService.this.context.mlua().registration().resolveContentType("item", owner,
               args.arg(3).opttable((LuaTable)null));
            Map<String, Object> props = MLuaLuaBindings.mapFromLua(propsTable);
            MLuaRegistryService.this.itemFactory.validateTypeDefinition(props);
            String key = owner + ":" + name;
            boolean hasCallbacks = registerItemCallbacks(propsTable, key);
            boolean copyModel = MLuaRegistryService.booleanProperty(propsTable, "copyModel", "CopyModel");
            MLuaTextureDescriptor itemTexture = MLuaRegistryService.textureDescriptor(propsTable, "itemTexture", "texture", "textures", "ItemTexture", "Texture", "Textures").resolve(owner, "item");
            String itemModel = MLuaRegistryService.resolvedModel(owner, "item", propsTable, "model", "Model", "itemModel", "ItemModel");
            MLuaLoaderRegistry.setItemCopy(owner, name, propsTable != null ? propsTable.get("copy").optjstring((String)null) : null);
            MLuaLoaderRegistry.setItemCopyModel(owner, name, copyModel);
            MLuaLoaderRegistry.setItemDescription(owner, name, MLuaRegistryService.descriptionList(propsTable));
            if (hasCallbacks) {
               MLuaLoaderRegistry.register(RegistryKind.ITEM, owner, name, () -> {
                  return MLuaRegistryService.this.itemFactory.createScripted(props, key);
               });
            } else {
               MLuaLoaderRegistry.register(RegistryKind.ITEM, owner, name, () -> {
                  return MLuaRegistryService.this.itemFactory.createNamed(props, name);
               });
            }

            MLuaLoaderRegistry.setItemVisuals(owner, name, itemTexture, itemModel);
            return LuaValue.NONE;
         }
      });
      blocks.set("Register", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaRegistryService.this.ensureRegistryPhase();
            String name = args.checkjstring(2);
            String owner = MLuaRegistryService.this.context.mlua().ownerModId();
            LuaTable propsTable = MLuaRegistryService.this.context.mlua().registration().resolveContentType("block", owner,
               args.arg(3).opttable((LuaTable)null));
            Map<String, Object> props = new HashMap(MLuaLuaBindings.mapFromLua(propsTable));
            MLuaBlockFactory.validateTypeDefinition(props);
            String key = owner + ":" + name;
            MLuaBehaviorRegistry.clearBlockCallbacks(key);
            MLuaBehaviorRegistry.ContainerDefinition containerDefinition = null;
            if (propsTable != null) {
               LuaValue containerValue = propsTable.get("container");
               if (containerValue.isnil()) containerValue = propsTable.get("Container");
               if (!containerValue.isnil()) {
                  if (containerValue.istable()) {
                     LuaTable containerTable = containerValue.checktable();
                     int rows = containerTable.get("Rows").optint(3);
                     String title = containerTable.get("Title").optjstring(MLuaRegistryNameHelper.displayName(name));
                     MLuaBehaviorRegistry.registerBlockContainer(key, rows, title);
                  } else if (containerValue.isstring()) {
                     String typeName = containerValue.checkjstring();
                     String typeKey = typeName.contains(":") ? typeName : owner + ":" + typeName;
                     MLuaBehaviorRegistry.ContainerDefinition definition = MLuaBehaviorRegistry.getContainerType(typeKey);
                     if (definition == null) throw new LuaError("[MLua] Block Container references unregistered container type '" + typeName + "'");
                     MLuaBehaviorRegistry.registerBlockContainer(key, definition);
                  } else {
                     throw new LuaError("[MLua] Block Container must be a registered type name or a table with Rows and optional Title");
                  }
                  containerDefinition = MLuaBehaviorRegistry.getBlockContainer(key);
               }
            }
            boolean copyModel = MLuaRegistryService.booleanProperty(propsTable, "copyModel", "CopyModel");
            MLuaTextureDescriptor texture = MLuaRegistryService.textureDescriptor(propsTable, "textures", "texture", "Textures", "Texture", "blockTexture", "BlockTexture").resolve(owner, "block");
            String model = MLuaRegistryService.resolvedModel(owner, "block", propsTable, "model", "Model", "blockModel", "BlockModel");
            JsonObject blockState = MLuaRegistryService.optionalJsonObject(propsTable, "blockState", "blockstate", "BlockState");
            MLuaLoaderRegistry.setBlockCopy(owner, name, propsTable != null ? propsTable.get("copy").optjstring((String)null) : null);
            MLuaLoaderRegistry.setBlockCopyModel(owner, name, copyModel);
            MLuaLoaderRegistry.setBlockDescription(owner, name, MLuaRegistryService.descriptionList(propsTable));
            MLuaRegistryService.this.applyMiningProperties(owner, name, propsTable, props);
            if (blockState != null) {
               MLuaLoaderRegistry.registerGeneratedAsset(GeneratedAssetKind.BLOCKSTATE, owner, name, blockState);
            }
            if (propsTable != null) {
               boolean hasBlockCallback = registerCallbacks("block", key, propsTable,
                  "onRightClick", "onPlace", "onBreak", "onStepOn", "onEntityInside",
                  "onNeighborChanged", "onRemove", "onRandomTick", "onTick", "onFallOn", "onProjectileHit");
               if (!propsTable.get("onRandomTick").isnil()) ((Map)props).put("randomTicks", true);

               boolean hasBlockCallbacks = containerDefinition != null || hasBlockCallback;
               if (hasBlockCallbacks) {
                  MLuaLoaderRegistry.register(RegistryKind.BLOCK, owner, name, () -> {
                     return MLuaRegistryService.this.blockFactory.createScripted(props, key, MLuaLoaderRegistry.visualBlockEntitySupplier(owner));
                  });
                  MLuaLoaderRegistry.setBlockVisuals(owner, name, texture, model);
                  return LuaValue.NONE;
               }
            }

            MLuaLoaderRegistry.register(RegistryKind.BLOCK, owner, name, () -> {
               return MLuaRegistryService.this.blockFactory.create(props, name, MLuaLoaderRegistry.visualBlockEntitySupplier(owner));
            });
            MLuaLoaderRegistry.setBlockVisuals(owner, name, texture, model);
            return LuaValue.NONE;
         }
      });
      blocks.set("RegisterItem", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaRegistryService.this.ensureRegistryPhase();
            String name = args.checkjstring(2);
            String owner = MLuaRegistryService.this.context.mlua().ownerModId();
            LuaTable propsTable = args.arg(3).opttable((LuaTable)null);
            Map<String, Object> props = propsTable != null ? MLuaLuaBindings.mapFromLua(propsTable) : Map.of();
            String key = owner + ":" + name;
            boolean hasCallbacks = registerItemCallbacks(propsTable, key);

            MLuaLoaderRegistry.setItemDescription(owner, name, MLuaRegistryService.descriptionList(propsTable));
            RegistryHandle<Block> blockRO = MLuaLoaderRegistry.get(RegistryKind.BLOCK, owner, name);
            if (blockRO == null) {
               throw new LuaError("[MLua] Block '" + key + "' not found. Register the block before its block item.");
            }

            if (hasCallbacks) {
               MLuaLoaderRegistry.register(RegistryKind.ITEM, owner, name, () -> {
                  return new MLuaScriptedBlockItem((Block)blockRO.get(), MLuaRegistryService.this.itemFactory.makeProperties(props), key);
               });
            } else {
               MLuaLoaderRegistry.register(RegistryKind.ITEM, owner, name, () -> {
                  return new dev.mlua.runtime.registry.MLuaNamedBlockItem((Block)blockRO.get(), new net.minecraft.world.item.Item.Properties(), key);
               });
            }

            return LuaValue.NONE;
         }
      });
      tabs.set("Register", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaRegistryService.this.ensureRegistryPhase();
            Map<String, Object> props = args.arg(3).istable() ? MLuaLuaBindings.mapFromLua(args.arg(3).checktable()) : Map.of();
            String name = args.checkjstring(2);
            MLuaLoaderRegistry.register(RegistryKind.CREATIVE_TAB, MLuaRegistryService.this.context.mlua().ownerModId(), name, () -> {
               return MLuaRegistryService.this.tabFactory.create(MLuaRegistryService.this.context.mlua().ownerModId(), name, props);
            });
            return LuaValue.NONE;
         }
      });
      recipes.set("Register", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaRegistryService.this.ensureRegistryPhase();
            String owner = MLuaRegistryService.this.context.mlua().ownerModId();
            String name = args.checkjstring(2);
            MLuaLoaderRegistry.registerGeneratedAsset(GeneratedAssetKind.RECIPE, owner, name, MLuaRegistryService.jsonObjectArg(args.arg(3), "recipe"));
            return LuaValue.NONE;
         }
      });
      recipes.set("RegisterShaped", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaRegistryService.this.ensureRegistryPhase();
            String owner = MLuaRegistryService.this.context.mlua().ownerModId();
            String name = args.checkjstring(2);
            MLuaLoaderRegistry.registerGeneratedAsset(GeneratedAssetKind.RECIPE, owner, name, MLuaRegistryService.shapedRecipe(args.arg(3)));
            return LuaValue.NONE;
         }
      });
      recipes.set("RegisterShapeless", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaRegistryService.this.ensureRegistryPhase();
            String owner = MLuaRegistryService.this.context.mlua().ownerModId();
            String name = args.checkjstring(2);
            MLuaLoaderRegistry.registerGeneratedAsset(GeneratedAssetKind.RECIPE, owner, name, MLuaRegistryService.shapelessRecipe(args.arg(3)));
            return LuaValue.NONE;
         }
      });
      recipes.set("RegisterSmelting", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaRegistryService.this.ensureRegistryPhase();
            String owner = MLuaRegistryService.this.context.mlua().ownerModId();
            String name = args.checkjstring(2);
            MLuaLoaderRegistry.registerGeneratedAsset(GeneratedAssetKind.RECIPE, owner, name, MLuaRegistryService.cookingRecipe("minecraft:smelting", args.arg(3)));
            return LuaValue.NONE;
         }
      });
      recipes.set("RegisterStonecutting", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaRegistryService.this.ensureRegistryPhase();
            String owner = MLuaRegistryService.this.context.mlua().ownerModId();
            String name = args.checkjstring(2);
            MLuaLoaderRegistry.registerGeneratedAsset(GeneratedAssetKind.RECIPE, owner, name, MLuaRegistryService.stonecuttingRecipe(args.arg(3)));
            return LuaValue.NONE;
         }
      });
      loot.set("Register", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaRegistryService.this.ensureRegistryPhase();
            String owner = MLuaRegistryService.this.context.mlua().ownerModId();
            String name = args.checkjstring(2);
            MLuaLoaderRegistry.registerGeneratedAsset(GeneratedAssetKind.LOOT_TABLE, owner, name, MLuaRegistryService.jsonObjectArg(args.arg(3), "loot table"));
            return LuaValue.NONE;
         }
      });
      loot.set("RegisterEntity", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaRegistryService.this.ensureRegistryPhase();
            String owner = MLuaRegistryService.this.context.mlua().ownerModId();
            String entityName = args.checkjstring(2);
            String lootName = "entities/" + entityName;
            MLuaLoaderRegistry.registerGeneratedAsset(GeneratedAssetKind.LOOT_TABLE, owner, lootName, MLuaRegistryService.jsonObjectArg(args.arg(3), "entity loot table"));
            return LuaValue.NONE;
         }
      });
      blocks.set("RegisterState", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaRegistryService.this.ensureRegistryPhase();
            String owner = MLuaRegistryService.this.context.mlua().ownerModId();
            String name = args.checkjstring(2);
            MLuaLoaderRegistry.registerGeneratedAsset(GeneratedAssetKind.BLOCKSTATE, owner, name, MLuaRegistryService.jsonObjectArg(args.arg(3), "blockstate"));
            return LuaValue.NONE;
         }
      });
      loot.set("RegisterSimpleBlock", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaRegistryService.this.ensureRegistryPhase();
            String owner = MLuaRegistryService.this.context.mlua().ownerModId();
            String blockName = args.checkjstring(2);
            String dropId = args.narg() >= 3 && !args.arg(3).isnil() ? args.arg(3).checkjstring() : MLuaRegistryService.this.qualifyOwnBlock(blockName);
            String lootName = blockName.contains(":") ? "blocks/" + blockName.substring(blockName.indexOf(58) + 1) : "blocks/" + blockName;
            MLuaLoaderRegistry.registerGeneratedAsset(GeneratedAssetKind.LOOT_TABLE, owner, lootName, MLuaRegistryService.simpleBlockLoot(dropId));
            return LuaValue.NONE;
         }
      });
      loot.set("RegisterSelfBlock", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaRegistryService.this.ensureRegistryPhase();
            String owner = MLuaRegistryService.this.context.mlua().ownerModId();
            String blockName = args.checkjstring(2);
            String lootName = blockName.contains(":") ? "blocks/" + blockName.substring(blockName.indexOf(58) + 1) : "blocks/" + blockName;
            MLuaLoaderRegistry.registerGeneratedAsset(GeneratedAssetKind.LOOT_TABLE, owner, lootName, MLuaRegistryService.simpleBlockLoot(MLuaRegistryService.this.qualifyOwnBlock(blockName)));
            return LuaValue.NONE;
         }
      });
      loot.set("RegisterSilkTouchBlock", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaRegistryService.this.ensureRegistryPhase();
            String owner = MLuaRegistryService.this.context.mlua().ownerModId();
            String blockName = args.checkjstring(2);
            String normalDrop = args.checkjstring(3);
            int count = args.narg() >= 4 && !args.arg(4).isnil() ? args.arg(4).checkint() : 1;
            String lootName = blockName.contains(":") ? "blocks/" + blockName.substring(blockName.indexOf(58) + 1) : "blocks/" + blockName;
            MLuaLoaderRegistry.registerGeneratedAsset(GeneratedAssetKind.LOOT_TABLE, owner, lootName, MLuaRegistryService.silkTouchBlockLoot(MLuaRegistryService.this.qualifyOwnBlock(blockName), normalDrop, count));
            return LuaValue.NONE;
         }
      });
      loot.set("RegisterOreBlock", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaRegistryService.this.ensureRegistryPhase();
            String owner = MLuaRegistryService.this.context.mlua().ownerModId();
            String blockName = args.checkjstring(2);
            String dropId = args.checkjstring(3);
            int minCount = args.narg() >= 4 && !args.arg(4).isnil() ? args.arg(4).checkint() : 1;
            int maxCount = args.narg() >= 5 && !args.arg(5).isnil() ? args.arg(5).checkint() : minCount;
            String lootName = blockName.contains(":") ? "blocks/" + blockName.substring(blockName.indexOf(58) + 1) : "blocks/" + blockName;
            MLuaLoaderRegistry.registerGeneratedAsset(GeneratedAssetKind.LOOT_TABLE, owner, lootName, MLuaRegistryService.oreBlockLoot(MLuaRegistryService.this.qualifyOwnBlock(blockName), dropId, minCount, maxCount));
            return LuaValue.NONE;
         }
      });
      tags.set("Register", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaRegistryService.this.ensureRegistryPhase();
            MLuaRegistryService.this.registerTag(args.checkjstring(2), args.checkjstring(3), args.arg(4));
            return LuaValue.NONE;
         }
      });
      tags.set("RegisterItem", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaRegistryService.this.ensureRegistryPhase();
            MLuaRegistryService.this.registerTag("items", args.checkjstring(2), args.arg(3));
            return LuaValue.NONE;
         }
      });
      tags.set("AddToItem", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaRegistryService.this.ensureRegistryPhase();
            MLuaRegistryService.this.appendTagValues("items", args.checkjstring(2), args.arg(3));
            return LuaValue.NONE;
         }
      });
      tags.set("RegisterBlock", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaRegistryService.this.ensureRegistryPhase();
            MLuaRegistryService.this.registerTag("blocks", args.checkjstring(2), args.arg(3));
            return LuaValue.NONE;
         }
      });
      tags.set("AddToBlock", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaRegistryService.this.ensureRegistryPhase();
            MLuaRegistryService.this.appendTagValues("blocks", args.checkjstring(2), args.arg(3));
            return LuaValue.NONE;
         }
      });
      tags.set("RegisterEntity", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaRegistryService.this.ensureRegistryPhase();
            MLuaRegistryService.this.registerTag("entity_types", args.checkjstring(2), args.arg(3));
            return LuaValue.NONE;
         }
      });
      tags.set("AddToEntity", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaRegistryService.this.ensureRegistryPhase();
            MLuaRegistryService.this.appendTagValues("entity_types", args.checkjstring(2), args.arg(3));
            return LuaValue.NONE;
         }
      });
      particles.set("Register", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaRegistryService.this.ensureRegistryPhase();
            String name = args.checkjstring(2);
            LuaTable propsTable = args.arg(3).opttable((LuaTable)null);
            boolean overrideLimiter = propsTable != null && propsTable.get("overrideLimiter").optboolean(false);
            MLuaLoaderRegistry.registerParticle(MLuaRegistryService.this.context.mlua().ownerModId(), name, overrideLimiter);
            return LuaValue.NONE;
         }
      });
      potions.set("Register", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaRegistryService.this.ensureRegistryPhase();
            String name = args.checkjstring(2);
            LuaTable propsTable = args.arg(3).opttable((LuaTable)null);
            List<MobEffectInstance> effects = MLuaRegistryService.potionEffects(propsTable);
            MLuaLoaderRegistry.register(RegistryKind.POTION, MLuaRegistryService.this.context.mlua().ownerModId(), name, () -> {
               //--version 26.1
               return new Potion(MLuaRegistryService.this.context.mlua().ownerModId() + ":" + name,
                  effects.toArray(MobEffectInstance[]::new));
               //--endversion
               //--version 1.20.1,1.21.1
               return new Potion((MobEffectInstance[])effects.toArray((x$0) -> {
                  return new MobEffectInstance[x$0];
               }));
               //--endversion
            });
            return LuaValue.NONE;
         }
      });
      effects.set("Register", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaRegistryService.this.ensureRegistryPhase();
            String name = args.checkjstring(2);
            String owner = MLuaRegistryService.this.context.mlua().ownerModId();
            LuaTable propsTable = args.arg(3).opttable((LuaTable)null);
            String key = owner + ":" + name;
            LuaValue onTick = MLuaRegistryService.callbackProperty(propsTable, "onTick", "OnTick");
            LuaValue onApplied = MLuaRegistryService.callbackProperty(propsTable, "onApplied", "OnApplied");
            LuaValue onEnding = MLuaRegistryService.callbackProperty(propsTable, "onEnding", "OnEnding");
            MLuaRegistryService.validateCallback("onTick", onTick);
            MLuaRegistryService.validateCallback("onApplied", onApplied);
            MLuaRegistryService.validateCallback("onEnding", onEnding);
            MLuaBehaviorRegistry.registerBehaviors("effect", key, Map.of(
               "onTick", onTick, "onApplied", onApplied, "onEnding", onEnding));

            MobEffectCategory category = MLuaRegistryService.effectCategory(propsTable);
            int color = MLuaRegistryService.colorProperty(propsTable, 16777215, "color", "Color");
            String descriptionId = MLuaRegistryService.stringProperty(propsTable, "translation", "Translation", "descriptionId", "DescriptionId", "name", "Name");
            String iconValue = MLuaRegistryService.stringProperty(propsTable, "icon", "Icon");
            boolean visibleInInventory = MLuaRegistryService.hasProperty(propsTable, "visibleInInventory", "VisibleInInventory") ? MLuaRegistryService.booleanProperty(propsTable, "visibleInInventory", "VisibleInInventory") : true;
            boolean visibleInGui = MLuaRegistryService.hasProperty(propsTable, "visibleInGui", "VisibleInGui") ? MLuaRegistryService.booleanProperty(propsTable, "visibleInGui", "VisibleInGui") : true;
            boolean instant = MLuaRegistryService.booleanProperty(propsTable, "instant", "Instant");
            int tickInterval = MLuaRegistryService.intProperty(propsTable, 1, "tickInterval", "TickInterval");
            MLuaLoaderRegistry.register(RegistryKind.MOB_EFFECT, owner, name, () -> {
               return new MLuaScriptedMobEffect(category, color, key, MLuaRegistryService.effectDescriptionId(owner, name, descriptionId), iconValue != null && !iconValue.isBlank() ? MLuaAssetResolver.resolveTexture(owner, iconValue, "mob_effect") : null, visibleInInventory, visibleInGui, instant, tickInterval);
            });
            return LuaValue.NONE;
         }
      });
      config.set("Register", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaRegistryService.this.ensureRegistryPhase();
            String scope = MLuaConfigRegistry.scopeFromString(args.checkjstring(2));
            String name = args.checkjstring(3);
            MLuaConfigRegistry.get().register(MLuaRegistryService.this.context.mlua().ownerModId(), scope, name, args.checktable(4));
            return LuaValue.NONE;
         }
      });
      config.set("RegisterCommon", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaRegistryService.this.ensureRegistryPhase();
            MLuaConfigRegistry.get().register(MLuaRegistryService.this.context.mlua().ownerModId(), "common", args.checkjstring(2), args.checktable(3));
            return LuaValue.NONE;
         }
      });
      config.set("RegisterClient", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaRegistryService.this.ensureRegistryPhase();
            MLuaConfigRegistry.get().register(MLuaRegistryService.this.context.mlua().ownerModId(), "client", args.checkjstring(2), args.checktable(3));
            return LuaValue.NONE;
         }
      });
      config.set("RegisterServer", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaRegistryService.this.ensureRegistryPhase();
            MLuaConfigRegistry.get().register(MLuaRegistryService.this.context.mlua().ownerModId(), "server", args.checkjstring(2), args.checktable(3));
            return LuaValue.NONE;
         }
      });
      damageSource.set("Register", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaRegistryService.this.ensureRegistryPhase();
            Object runtimeOwner = MLuaRegistryService.this.context.mlua().registration() instanceof dev.mlua.core.MLuaRuntime runtime ? runtime.session() : null;
            MLuaDamageSourceRegistry.register(runtimeOwner, MLuaRegistryService.this.context.mlua().ownerModId(), args.checkjstring(2), args.arg(3).opttable((LuaTable)null));
            return LuaValue.NONE;
         }
      });
      entities.set("Register", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaRegistryService.this.ensureRegistryPhase();
            String name = args.checkjstring(2);
            String owner = MLuaRegistryService.this.context.mlua().ownerModId();
            LuaTable propsTable = args.arg(3).opttable((LuaTable)null);
            String key = owner + ":" + name;
             LuaValue onTick = propsTable != null ? propsTable.get("onTick") : LuaValue.NIL;
             LuaValue onHurt = propsTable != null ? propsTable.get("onHurt") : LuaValue.NIL;
             LuaValue onDeath = propsTable != null ? propsTable.get("onDeath") : LuaValue.NIL;
             LuaValue onAI = propsTable != null ? propsTable.get("AI") : LuaValue.NIL;
             LuaValue onSpawn = propsTable != null ? propsTable.get("onSpawn") : LuaValue.NIL;
             LuaValue onInteract = propsTable != null ? propsTable.get("onInteract") : LuaValue.NIL;
             LuaValue onRemove = propsTable != null ? propsTable.get("onRemove") : LuaValue.NIL;
             MLuaRegistryService.validateEntityAI(onAI);
             MLuaRegistryService.validateCallback("onTick", onTick);
             MLuaRegistryService.validateCallback("onHurt", onHurt);
             MLuaRegistryService.validateCallback("onDeath", onDeath);
             MLuaRegistryService.validateCallback("onSpawn", onSpawn);
             MLuaRegistryService.validateCallback("onInteract", onInteract);
             MLuaRegistryService.validateCallback("onRemove", onRemove);
             MLuaBehaviorRegistry.registerBehaviors("entity", key, Map.of(
                "onTick", onTick, "onHurt", onHurt, "onDeath", onDeath, "AI", onAI,
                "onSpawn", onSpawn, "onInteract", onInteract, "onRemove", onRemove));
             MLuaBehaviorRegistry.registerEntityAIInterval(key, Math.max(1, Math.min(120,
                propsTable != null ? propsTable.get("aiInterval").optint(5) : 5)));

             LuaTable attr = propsTable != null ? propsTable.get("attributes").opttable((LuaTable)null) : null;
             double maxHealth = MLuaRegistryService.attrValue(attr, "maxHealth", 20.0);
             double speed = MLuaRegistryService.attrValue(attr, "movementSpeed", 0.25);
             double attack = MLuaRegistryService.attrValue(attr, "attackDamage", 2.0);
             double armor = MLuaRegistryService.attrValue(attr, "armor", 0.0);
             double armorToughness = MLuaRegistryService.attrValue(attr, "armorToughness", 0.0);
             double attackSpeed = MLuaRegistryService.attrValue(attr, "attackSpeed", 4.0);
             double knockbackResistance = MLuaRegistryService.attrValue(attr, "knockbackResistance", 0.0);
             double followRange = MLuaRegistryService.attrValue(attr, "followRange", 16.0);
             double width = propsTable != null ? propsTable.get("width").optdouble(0.6) : 0.6;
             double height = propsTable != null ? propsTable.get("height").optdouble(1.8) : 1.8;
             int trackingRange = propsTable != null ? propsTable.get("trackingRange").optint(8) : 8;
             int updateInterval = propsTable != null ? propsTable.get("updateInterval").optint(3) : 3;
             MLuaTextureDescriptor entityTexture = MLuaRegistryService.textureDescriptor(propsTable, "texture", "Texture", "entityTexture", "EntityTexture").resolve(owner, "entity");
             String entityModel = MLuaRegistryService.stringProperty(propsTable, "model", "Model", "entityModel", "EntityModel");
             float shadowRadius = propsTable != null ? (float)propsTable.get("shadowRadius").optdouble(0.5) : 0.5F;
             MobCategory category = MLuaRegistryService.mobCategory(propsTable);
             EntityType.EntityFactory<MLuaScriptedEntity> factory = (type, level) -> {
                return new MLuaScriptedEntity((EntityType<? extends MLuaScriptedEntity>)(EntityType<?>)type, level, key);
             };
             EntityType.Builder<MLuaScriptedEntity> builder = Builder.of(factory, category).sized((float)width, (float)height).clientTrackingRange(trackingRange).updateInterval(updateInterval);
             Supplier<AttributeSupplier> attrSupplier = () -> {
                return Mob.createMobAttributes()
                   .add(Attributes.MAX_HEALTH, maxHealth)
                   .add(Attributes.MOVEMENT_SPEED, speed)
                   .add(Attributes.ATTACK_DAMAGE, attack)
                   .add(Attributes.ARMOR, armor)
                   .add(Attributes.ARMOR_TOUGHNESS, armorToughness)
                   .add(Attributes.ATTACK_SPEED, attackSpeed)
                   .add(Attributes.KNOCKBACK_RESISTANCE, knockbackResistance)
                   .add(Attributes.FOLLOW_RANGE, followRange)
                   .build();
             };
             MLuaLoaderRegistry.setEntityVisual(owner, name, entityTexture.all() != null ? entityTexture.all() : "minecraft:textures/entity/zombie/zombie.png", entityModel, shadowRadius);
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
             MLuaLogger.LOGGER.info("Registered MLua scripted entity '{}' with AI callback={}", key, !onAI.isnil());
             return LuaValue.NONE;
         }
      });
      entities.set("RegisterSpawnEgg", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaRegistryService.this.ensureRegistryPhase();
            String name = args.checkjstring(2);
            String owner = MLuaRegistryService.this.context.mlua().ownerModId();
            LuaTable propsTable = args.arg(3).opttable((LuaTable)null);

            String entityName = propsTable != null ? propsTable.get("entity").optjstring(name) : name;
            if (!entityName.contains(":")) {
               entityName = owner + ":" + entityName;
            }

            int primaryColor = propsTable != null ? (int)propsTable.get("primaryColor").optdouble(16777215.0) : 16777215;
            int secondaryColor = propsTable != null ? (int)propsTable.get("secondaryColor").optdouble(0.0) : 0;
            RegistryHandle<EntityType<?>> entityType = MLuaLoaderRegistry.get(RegistryKind.ENTITY, entityName.split(":")[0], entityName.split(":")[1]);

            if (entityType == null) {
               throw new LuaError("[MLua] Entity '" + entityName + "' not registered yet. Register the entity before its spawn egg.");
            }

            registerItemCallbacks(propsTable, owner + ":" + name);
            MLuaLoaderRegistry.setItemDescription(owner, name, MLuaRegistryService.descriptionList(propsTable));
            MLuaLoaderRegistry.registerSpawnEgg(owner, name, entityType, primaryColor, secondaryColor);
            return LuaValue.NONE;
         }
      });
       projectiles.set("Register", new VarArgFunction() {
          public Varargs invoke(Varargs args) {
             MLuaRegistryService.this.ensureRegistryPhase();
             String name = args.checkjstring(2);
             LuaTable props = args.arg(3).opttable((LuaTable)null);
             if (props == null) {
                throw new LuaError("[MLua] Registry.Projectiles:Register requires a properties table");
             }
             Object runtimeOwner = MLuaRegistryService.this.context.mlua().registration() instanceof dev.mlua.core.MLuaRuntime runtime ? runtime.session() : null;
             String ownerModId = MLuaRegistryService.this.context.mlua().ownerModId();
             MLuaProjectileDefinition.register(runtimeOwner, ownerModId, name, props,
                   MLuaLuaBindings.boundContext(MLuaRegistryService.this.context.mlua().globals()));
             return MLuaProjectileDefinition.createTypeLuaTable(runtimeOwner, ownerModId, name,
                MLuaRegistryService.this.context.mlua().registration());
          }
       });
      table.set("Register", new VarArgFunction() {
          @Override public Varargs invoke(Varargs args) {
             String category = args.checkjstring(2);
             RegistryEndpoint endpoint = resolveEndpoint(category, "category");
             LuaTable target = table.get(endpoint.table()).checktable();
             LuaValue register = target.get(endpoint.registerMethod());
             if (!register.isfunction()) throw new LuaError("[MLua] Registry category '" + category + "' does not support generic registration");
             if ("tag".equals(endpoint.typeDomain()) || "datapack".equals(endpoint.typeDomain())) {
                return register.invoke(LuaValue.varargsOf(new LuaValue[]{target, args.arg(3), args.arg(4), args.arg(5)}));
             }
             return register.invoke(LuaValue.varargsOf(new LuaValue[]{target, args.arg(3), args.arg(4)}));
          }
       });
      table.set("RegisterType", new VarArgFunction() {
          @Override public Varargs invoke(Varargs args) {
             RegistryEndpoint endpoint = resolveEndpoint(args.checkjstring(2), "type domain");
             LuaTable target = table.get(endpoint.typeTable()).checktable();
             return target.get("RegisterType").invoke(LuaValue.varargsOf(new LuaValue[]{target, args.arg(3), args.arg(4)}));
          }
       });
       contentTypes.set("Register", new VarArgFunction() {
          @Override public Varargs invoke(Varargs args) {
             MLuaRegistryService.this.ensureRegistryPhase();
             String domain = args.checkjstring(2);
             String name = args.checkjstring(3);
             LuaTable definition = args.checktable(4);
             MLuaRegistryService.this.registerContentType(domain, name, definition);
             return LuaValue.NONE;
          }
       });
       contentTypes.set("GetTypes", new VarArgFunction() {
          @Override public Varargs invoke(Varargs args) {
             String domain = args.checkjstring(2);
             return typeCatalog(MLuaRegistryService.this.context.mlua().registration().contentTypes(domain,
                MLuaRegistryService.this.context.mlua().ownerModId()));
          }
       });
       contentTypes.set("Resolve", new VarArgFunction() {
          @Override public Varargs invoke(Varargs args) {
             String domain = args.checkjstring(2);
             LuaTable definition = args.checktable(3);
             return MLuaRegistryService.this.context.mlua().registration().resolveContentType(domain,
                MLuaRegistryService.this.context.mlua().ownerModId(), definition);
          }
       });
      table.set("GetTypes", new VarArgFunction() {
          @Override public Varargs invoke(Varargs args) {
             RegistryEndpoint endpoint = resolveEndpoint(args.checkjstring(2), "type domain");
             LuaTable target = table.get(endpoint.typeTable()).checktable();
             return target.get("GetTypes").invoke(target);
          }
       });
      table.set("ResolveType", new VarArgFunction() {
          @Override public Varargs invoke(Varargs args) {
             RegistryEndpoint endpoint = resolveEndpoint(args.checkjstring(2), "type domain");
             LuaTable target = table.get(endpoint.typeTable()).checktable();
             return target.get("ResolveType").invoke(LuaValue.varargsOf(new LuaValue[]{target, args.arg(3)}));
          }
       });
       table.set("GetRegistered", new VarArgFunction() {
          @Override public Varargs invoke(Varargs args) {
             RegistryKind kind = MLuaRegistryEntry.kind(args.checkjstring(2));
             LuaTable result = new LuaTable();
             int index = 1;
             for (String id : MLuaLoaderRegistry.getRegisteredIds(kind).stream().sorted().toList()) {
                result.set(index++, LuaValue.valueOf(id));
             }
             return result;
          }
       });
       table.set("GetKinds", new VarArgFunction() {
          @Override public Varargs invoke(Varargs args) {
             LuaTable result = new LuaTable();
             int index = 1;
             for (RegistryKind kind : RegistryKind.values()) {
                result.set(index++, LuaValue.valueOf(kind.name().toLowerCase(java.util.Locale.ROOT)));
             }
             return result;
          }
       });
       table.set("GetEntry", new VarArgFunction() {
          @Override public Varargs invoke(Varargs args) {
             RegistryKind kind = MLuaRegistryEntry.kind(args.checkjstring(2));
             return MLuaRegistryEntry.create(MLuaRegistryService.this.context.mlua().registration(), kind,
                args.checkjstring(3));
          }
       });
       table.set("Exists", new VarArgFunction() {
          @Override public Varargs invoke(Varargs args) {
             RegistryKind kind = MLuaRegistryEntry.kind(args.checkjstring(2));
             LuaTable entry = MLuaRegistryEntry.create(MLuaRegistryService.this.context.mlua().registration(), kind,
                args.checkjstring(3));
             return entry.get("Exists").invoke(entry).arg1();
          }
       });
       for (RegistryEndpoint endpoint : REGISTRY_ENDPOINTS.values().stream().distinct().toList()) {
          if (!endpoint.table().equals(endpoint.typeTable())) continue;
          LuaTable target = table.get(endpoint.table()).checktable();
          installContentTypeApi(target, endpoint.typeDomain(), endpoint.typedDefinitions().toArray(String[]::new));
          if (endpoint.registryKind() != null) installRegistryEntryApi(target, endpoint.registryKind());
          documentRegistryTable(target, endpoint.table());
       }
       documentRegistryTable(contentTypes, "ContentTypes");
       wrapTypedDefinition(entities, "item", "RegisterSpawnEgg:3");
       wrapTypedDefinition(blocks, "item", "RegisterItem:3");
       return table;
    }

   private static LuaTable registryTable() {
      LuaTable table = new LuaTable();
      table.set("__side", "Registry");
      return table;
   }

   private static void documentRegistryTable(LuaTable target, String category) {
      target.rawset("__mlua_doc_description", LuaValue.valueOf(
         "Registry." + category + " provides the registration and lookup operations available for this content category."));
      LuaTable methods = new LuaTable();
      Map<String, String> descriptions = Map.of(
         "Register", "Registers a definition during the registry phase; accepted fields depend on the content category.",
         "RegisterType", "Defines addon-owned reusable defaults that other definitions in this category can inherit.",
         "ResolveType", "Returns a copy of the definition with its registered type defaults merged in.",
         "GetTypes", "Lists the reusable types available to this addon for the category.",
         "GetId", "Resolves a short name in the addon's namespace or validates a full namespaced identifier.",
         "GetEntry", "Creates a RegistryEntry view for the resolved identifier.",
         "Exists", "Checks whether the resolved identifier exists in Minecraft's live registry.",
         "RegisterItem", "Registers an item form for a previously registered block.");
      descriptions.forEach((name, description) -> {
         if (target.rawget(name).isfunction()) methods.rawset(name, LuaValue.valueOf(description));
      });
      target.rawset("__mlua_doc_methods", methods);
   }

   private VarArgFunction contentTypeRegistrar(String domain) {
      return new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            MLuaRegistryService.this.ensureRegistryPhase();
            String name = args.checkjstring(2);
            LuaTable definition = args.checktable(3);
            MLuaRegistryService.this.registerContentType(domain, name, definition);
            return LuaValue.NONE;
         }
      };
   }

   private void registerContentType(String domain, String name, LuaTable definition) {
      String base = stringProperty(definition, "base", "Base");
      String description = stringProperty(definition, "description", "Description");
      LuaValue defaultsValue = definition.get("defaults");
      if (defaultsValue.isnil()) defaultsValue = definition.get("Defaults");
      if (!defaultsValue.isnil() && !defaultsValue.istable()) {
         throw new LuaError("[MLua] Content type Defaults must be a property table");
      }
      this.context.mlua().registration().registerContentType(domain, this.context.mlua().ownerModId(), name,
         base, description, defaultsValue.istable() ? defaultsValue.checktable() : null);
   }

   private void installContentTypeApi(LuaTable target, String domain, String... methods) {
      target.set("RegisterType", contentTypeRegistrar(domain));
      target.set("ResolveType", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            LuaTable definition = args.checktable(2);
            return MLuaRegistryService.this.context.mlua().registration().resolveContentType(domain,
               MLuaRegistryService.this.context.mlua().ownerModId(), definition);
         }
      });
      target.set("GetTypes", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            Map<String, String> types = new java.util.TreeMap<>();
            if ("item".equals(domain)) types.putAll(MLuaItemFactory.registeredTypes());
            if ("block".equals(domain)) types.putAll(MLuaBlockFactory.registeredTypes());
            types.putAll(MLuaRegistryService.this.context.mlua().registration().contentTypes(domain,
               MLuaRegistryService.this.context.mlua().ownerModId()));
            return MLuaRegistryService.typeCatalog(types);
         }
      });
      wrapTypedDefinition(target, domain, methods);
   }

   private void installRegistryEntryApi(LuaTable target, RegistryKind kind) {
      target.set("GetId", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            return LuaValue.valueOf(MLuaRegistryService.this.registryId(args.checkjstring(2)));
         }
      });
      target.set("GetEntry", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            return MLuaRegistryEntry.create(MLuaRegistryService.this.context.mlua().registration(), kind,
               MLuaRegistryService.this.registryId(args.checkjstring(2)));
         }
      });
      target.set("Exists", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            LuaTable entry = MLuaRegistryEntry.create(MLuaRegistryService.this.context.mlua().registration(), kind,
               MLuaRegistryService.this.registryId(args.checkjstring(2)));
            return entry.get("Exists").invoke(entry).arg1();
         }
      });
   }

   private String registryId(String name) {
      String candidate = name.trim();
      if (!candidate.contains(":")) candidate = this.context.mlua().ownerModId() + ":" + candidate;
      net.minecraft.resources.ResourceLocation id = MLuaResourceLocations.tryParseOrNull(candidate);
      if (id == null) throw new LuaError("[MLua] Invalid registry identifier: " + name);
      return id.toString();
   }

   private static RegistryEndpoint resolveEndpoint(String category, String purpose) {
      String key = category.trim().toLowerCase(java.util.Locale.ROOT);
      RegistryEndpoint endpoint = REGISTRY_ENDPOINTS.get(key);
      if (endpoint == null) throw new LuaError("[MLua] Unknown or unsupported Registry " + purpose + " '" + category + "'");
      return endpoint;
   }

   private void wrapTypedDefinition(LuaTable target, String domain, String... methodIndexes) {
      for (String methodIndex : methodIndexes) {
         int split = methodIndex.lastIndexOf(':');
         String methodName = methodIndex.substring(0, split);
         int definitionIndex = Integer.parseInt(methodIndex.substring(split + 1));
         LuaValue original = target.get(methodName);
         if (!original.isfunction()) continue;
         target.set(methodName, new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
               LuaValue definition = args.arg(definitionIndex);
               if (!definition.istable()) return original.invoke(args);
               LuaTable resolved = MLuaRegistryService.this.context.mlua().registration().resolveContentType(
                  domain, MLuaRegistryService.this.context.mlua().ownerModId(), definition.checktable());
               LuaValue[] forwarded = new LuaValue[args.narg()];
               for (int index = 1; index <= args.narg(); index++) forwarded[index - 1] = args.arg(index);
               forwarded[definitionIndex - 1] = resolved;
               return original.invoke(LuaValue.varargsOf(forwarded));
            }
         });
      }
   }

   private void ensureRegistryPhase() {
      if (this.context.mlua().phase() != RegistrationPhase.REGISTRY) {
         throw new LuaError("[MLua] Cannot register content after registries are frozen. Put this in a .registry.lua script.");
      }
   }

   private void registerTag(String kind, String name, LuaValue definition) {
      TagRegistration tag = parseTagRegistration(definition);
      String owner = this.context.mlua().ownerModId();
      switch (normalizeTagKind(kind)) {
         case "items" -> MLuaLoaderRegistry.registerTag(RegistryKind.ITEM, owner, name, tag.replace(), tag.values());
         case "blocks" -> MLuaLoaderRegistry.registerTag(RegistryKind.BLOCK, owner, name, tag.replace(), tag.values());
         case "entity_types" -> MLuaLoaderRegistry.registerTag(RegistryKind.ENTITY, owner, name, tag.replace(), tag.values());
         default -> throw new LuaError("[MLua] Unsupported tag kind: " + kind);
      }
   }

   private void applyMiningProperties(String owner, String name, LuaTable propsTable, Map<String, Object> props) {
      String mineableWith = stringProperty(propsTable, "mineableWith", "MineableWith", "toolType", "ToolType", "effectiveToolTag", "EffectiveToolTag");
      if (mineableWith != null && !mineableWith.isBlank()) {
         this.registerSingleBlockTag(owner, "mineable/" + normalizeToolName(mineableWith), owner + ":" + name);
      }

      String requiredTier = stringProperty(propsTable, "requiredToolTier", "RequiredToolTier", "toolTier", "ToolTier", "minToolTier", "MinToolTier");
      if (requiredTier != null && !requiredTier.isBlank()) {
         this.registerSingleBlockTag(owner, requiredToolTierTag(requiredTier), owner + ":" + name);
         props.put("requiresCorrectTool", true);
      }
   }

   private void registerSingleBlockTag(String owner, String tagName, String blockId) {
      JsonArray values = new JsonArray();
      values.add(blockId);
      MLuaLoaderRegistry.registerTag(RegistryKind.BLOCK, owner, tagName, false, values);
   }

   private static String normalizeToolName(String value) {
      return switch (value.trim().toLowerCase()) {
         case "pick", "pickaxe" -> "pickaxe";
         case "axe" -> "axe";
         case "shovel", "spade" -> "shovel";
         case "hoe" -> "hoe";
         default -> throw new LuaError("[MLua] Unsupported mineableWith/toolType value: " + value);
      };
   }

   private static String requiredToolTierTag(String value) {
      return switch (value.trim().toLowerCase()) {
         case "wood", "wooden", "stone" -> "needs_stone_tool";
         case "iron" -> "needs_iron_tool";
         case "diamond", "netherite" -> "needs_diamond_tool";
         default -> throw new LuaError("[MLua] Unsupported requiredToolTier/toolTier value: " + value);
      };
   }

    private static MobCategory mobCategory(LuaTable table) {
      String value = stringProperty(table, "category", "Category", "mobCategory", "MobCategory");
      if (value == null || value.isBlank()) {
         return MobCategory.CREATURE;
      }

      return switch (value.trim().toLowerCase()) {
         case "creature" -> MobCategory.CREATURE;
         case "monster" -> MobCategory.MONSTER;
         case "ambient" -> MobCategory.AMBIENT;
         case "water_creature", "watercreature" -> MobCategory.WATER_CREATURE;
         case "water_ambient", "waterambient" -> MobCategory.WATER_AMBIENT;
         case "misc" -> MobCategory.MISC;
         case "underground_water_creature", "undergroundwatercreature" -> MobCategory.UNDERGROUND_WATER_CREATURE;
         case "axolotls", "axolotl" -> MobCategory.AXOLOTLS;
         default -> throw new LuaError("[MLua] Unsupported entity category: " + value);
      };
   }

   private void appendTagValues(String kind, String name, LuaValue valuesValue) {
      if (!valuesValue.istable()) {
         throw new LuaError("[MLua] Tag values must be a table");
      }

      String owner = this.context.mlua().ownerModId();
      JsonElement valuesElement = luaToJson(valuesValue);
      if (!valuesElement.isJsonArray()) {
         throw new LuaError("[MLua] Tag values must be an array-style table");
      }

      JsonArray values = valuesElement.getAsJsonArray();
      switch (normalizeTagKind(kind)) {
         case "items" -> MLuaLoaderRegistry.registerTag(RegistryKind.ITEM, owner, name, false, values);
         case "blocks" -> MLuaLoaderRegistry.registerTag(RegistryKind.BLOCK, owner, name, false, values);
         case "entity_types" -> MLuaLoaderRegistry.registerTag(RegistryKind.ENTITY, owner, name, false, values);
         default -> throw new LuaError("[MLua] Unsupported tag kind: " + kind);
      }
   }

   private String qualifyOwnBlock(String blockName) {
      return blockName.contains(":") ? blockName : this.context.mlua().ownerModId() + ":" + blockName;
   }

   private static LuaTable typeCatalog(Map<String, String> types) {
      LuaTable result = new LuaTable();
      types.forEach((name, description) -> result.set(name, description));
      return result;
   }

   private static boolean registerItemCallbacks(LuaTable propsTable, String key) {
      return registerCallbacks("item", key, propsTable,
         "onUse", "onUseOnBlock", "onInventoryTick", "onCrafted", "onConsume", "onAttackEntity");
   }

   private static boolean registerCallbacks(String domain, String key, LuaTable properties, String... eventNames) {
      Map<String, LuaValue> callbacks = new HashMap<>();
      if (properties == null) {
         MLuaBehaviorRegistry.registerBehaviors(domain, key, callbacks);
         return false;
      }
      for (String eventName : eventNames) {
         LuaValue callback = properties.get(eventName);
         if (callback.isnil()) continue;
         if (!callback.isfunction()) {
            throw new LuaError("[MLua] Registry callback '" + eventName + "' must be a function");
         }
      }
      for (String eventName : eventNames) {
         LuaValue callback = properties.get(eventName);
         if (!callback.isnil()) callbacks.put(eventName, callback);
      }
      MLuaBehaviorRegistry.registerBehaviors(domain, key, callbacks);
      return !callbacks.isEmpty();
   }

   private static void validateCallback(String name, LuaValue callback) {
      if (!callback.isnil() && !callback.isfunction()) {
         throw new LuaError("[MLua] Registry callback '" + name + "' must be a function");
      }
   }

   private static void validateEntityAI(LuaValue ai) {
      if (ai.isnil() || ai.isfunction()) return;
      if (!ai.istable()) throw new LuaError("[MLua] Entity AI must be a function or a state table.");
      LuaTable definition = ai.checktable();
      LuaValue initial = definition.get("initial");
      LuaValue statesValue = definition.get("states");
      if (!initial.isstring() || initial.tojstring().isBlank() || !statesValue.istable()) {
         throw new LuaError("[MLua] State-based AI requires an 'initial' state name and a 'states' table.");
      }
      LuaTable states = statesValue.checktable();
      if (states.get(initial.tojstring()).isnil()) {
         throw new LuaError("[MLua] State-based AI initial state '" + initial.tojstring() + "' is not defined.");
      }
      LuaValue key = LuaValue.NIL;
      while (true) {
         Varargs entry = states.next(key);
         key = entry.arg1();
         if (key.isnil()) break;
         LuaValue state = entry.arg(2);
         if (!key.isstring() || key.tojstring().isBlank() || !state.isfunction() && !state.istable()) {
            throw new LuaError("[MLua] Each AI state must have a name and be a function or state table.");
         }
         if (state.istable()) {
            LuaTable callbacks = state.checktable();
            for (String callbackName : List.of("onEnter", "update", "onExit")) {
               LuaValue callback = callbacks.get(callbackName);
               if (!callback.isnil() && !callback.isfunction()) {
                  throw new LuaError("[MLua] AI state '" + key.tojstring() + "' callback '" + callbackName + "' must be a function.");
               }
            }
         }
      }
   }

   private static double attrValue(LuaTable attrs, String key, double fallback) {
      return attrs != null ? attrs.get(key).optdouble(fallback) : fallback;
   }

   private static JsonObject jsonObjectArg(LuaValue value, String type) {
      JsonElement element;
      if (value.isstring()) {
         try {
            element = JsonParser.parseString(value.checkjstring());
         } catch (JsonParseException error) {
            throw new LuaError("[MLua] Invalid " + type + " JSON: " + error.getMessage());
         }
      } else {
         element = luaToJson(value);
      }
      if (!element.isJsonObject()) {
         throw new LuaError("[MLua] " + type + " definition must be a table or JSON object");
      }

      return element.getAsJsonObject();
   }

   private static JsonObject optionalJsonObject(LuaTable table, String... keys) {
      if (table == null) {
         return null;
      }

      for (String key : keys) {
         LuaValue value = table.get(key);
         if (!value.isnil()) {
            return jsonObjectArg(value, key);
         }
      }

      return null;
   }

   private static TagRegistration parseTagRegistration(LuaValue value) {
      if (value.isstring()) {
         JsonObject object = jsonObjectArg(value, "tag");
         JsonElement entries = object.get("values");
         if (entries == null || !entries.isJsonArray()) {
            throw new LuaError("[MLua] Tag JSON must contain a values array");
         }
         JsonElement replaceValue = object.get("replace");
         if (replaceValue != null && (!replaceValue.isJsonPrimitive()
             || !replaceValue.getAsJsonPrimitive().isBoolean())) {
            throw new LuaError("[MLua] Tag replace must be a boolean");
         }
         boolean replace = replaceValue != null && replaceValue.getAsBoolean();
         return new TagRegistration(replace, entries.getAsJsonArray());
      }
      if (!value.istable()) {
         throw new LuaError("[MLua] Tag definition must be a table or JSON object");
      }

      LuaTable table = value.checktable();
      boolean structured = !table.get("values").isnil() || !table.get("replace").isnil();
      boolean replace = structured && table.get("replace").optboolean(false);
      LuaValue valuesValue = structured ? table.get("values") : value;
      JsonElement valuesElement = luaToJson(valuesValue);
      if (!valuesElement.isJsonArray()) {
         throw new LuaError("[MLua] Tag values must be an array-style table");
      }

      return new TagRegistration(replace, valuesElement.getAsJsonArray());
   }

   private static JsonObject shapedRecipe(LuaValue value) {
      LuaTable table = checkedTable(value, "shaped recipe");
      JsonArray pattern = requiredStringArray(table.get("pattern"), "pattern");
      if (pattern.size() < 1 || pattern.size() > 3) {
         throw new LuaError("[MLua] Shaped recipes require 1-3 rows");
      }
      int width = pattern.get(0).getAsString().length();
      if (width < 1 || width > 3) throw new LuaError("[MLua] Shaped rows must contain 1-3 characters");
      for (JsonElement row : pattern) {
         if (row.getAsString().length() != width) {
            throw new LuaError("[MLua] All Shaped rows must have the same width");
         }
      }
      JsonObject keys = requiredObject(table.get("key"), "key");
      boolean hasIngredient = false;
      for (JsonElement row : pattern) {
         for (char symbol : row.getAsString().toCharArray()) {
            if (symbol == ' ') continue;
            hasIngredient = true;
            if (!keys.has(String.valueOf(symbol))) {
               throw new LuaError("[MLua] Shaped pattern has no key for '" + symbol + "'");
            }
         }
      }
      if (!hasIngredient) throw new LuaError("[MLua] Shaped pattern must contain an ingredient");
      for (String symbol : keys.keySet()) {
         if (symbol.length() != 1 || symbol.equals(" ")) {
            throw new LuaError("[MLua] Shaped keys must be one non-space character");
         }
         boolean used = false;
         for (JsonElement row : pattern) {
            if (row.getAsString().contains(symbol)) used = true;
         }
         if (!used) throw new LuaError("[MLua] Shaped key '" + symbol + "' is not used");
      }
      JsonObject recipe = new JsonObject();
      recipe.addProperty("type", "minecraft:crafting_shaped");
      recipe.add("pattern", pattern);
      recipe.add("key", keys);
      recipe.add("result", recipeResult(table.get("result")));
      return recipe;
   }

   private static JsonObject shapelessRecipe(LuaValue value) {
      LuaTable table = checkedTable(value, "shapeless recipe");
      JsonArray ingredients = requiredArray(table.get("ingredients"), "ingredients");
      if (ingredients.size() < 1 || ingredients.size() > 9) {
         throw new LuaError("[MLua] Shapeless recipes require 1-9 ingredients");
      }
      JsonObject recipe = new JsonObject();
      recipe.addProperty("type", "minecraft:crafting_shapeless");
      recipe.add("ingredients", ingredients);
      recipe.add("result", recipeResult(table.get("result")));
      return recipe;
   }

   private static JsonObject cookingRecipe(String type, LuaValue value) {
      LuaTable table = checkedTable(value, type + " recipe");
      JsonObject recipe = new JsonObject();
      recipe.addProperty("type", type);
      recipe.add("ingredient", singleIngredient(table.get("ingredient"), "ingredient"));
      String resultId = requiredString(table.get("result"), "result");
      MLuaResourceLocations.parse(resultId);
      //--version 1.20.1
      recipe.addProperty("result", resultId);
      //--endversion
      //--version 1.21.1
      recipe.add("result", itemStackResult(resultId, 1));
      //--endversion
      //--version 26.1
      recipe.add("result", itemStackResult(resultId, 1));
      //--endversion
      double experience = table.get("experience").optdouble(0.0);
      int cookingTime = table.get("cookingTime").optint(table.get("cookingtime").optint(200));
      if (!Double.isFinite(experience) || experience < 0) {
         throw new LuaError("[MLua] Cooking experience must be finite and non-negative");
      }
      if (cookingTime < 1) throw new LuaError("[MLua] Cooking time must be at least 1 tick");
      recipe.addProperty("experience", experience);
      recipe.addProperty("cookingtime", cookingTime);
      return recipe;
   }

   private static JsonObject stonecuttingRecipe(LuaValue value) {
      LuaTable table = checkedTable(value, "stonecutting recipe");
      JsonObject recipe = new JsonObject();
      recipe.addProperty("type", "minecraft:stonecutting");
      recipe.add("ingredient", singleIngredient(table.get("ingredient"), "ingredient"));
      String resultId = requiredString(table.get("result"), "result");
      MLuaResourceLocations.parse(resultId);
      int count = table.get("count").optint(1);
      if (count < 1) throw new LuaError("[MLua] Stonecutting count must be at least 1");
      //--version 1.20.1
      recipe.addProperty("result", resultId);
      recipe.addProperty("count", count);
      //--endversion
      //--version 1.21.1
      recipe.add("result", itemStackResult(resultId, count));
      //--endversion
      //--version 26.1
      recipe.add("result", itemStackResult(resultId, count));
      //--endversion
      return recipe;
   }

   private static JsonObject itemStackResult(String itemId, int count) {
      JsonObject result = new JsonObject();
      result.addProperty("id", itemId);
      result.addProperty("count", count);
      return result;
   }

   private static JsonObject simpleBlockLoot(String dropId) {
      JsonObject loot = new JsonObject();
      loot.addProperty("type", "minecraft:block");
      JsonArray pools = new JsonArray();
      JsonObject pool = new JsonObject();
      pool.addProperty("rolls", 1);
      JsonArray entries = new JsonArray();
      JsonObject entry = new JsonObject();
      entry.addProperty("type", "minecraft:item");
      entry.addProperty("name", dropId);
      entries.add(entry);
      pool.add("entries", entries);
      pools.add(pool);
      loot.add("pools", pools);
      return loot;
   }

   private static JsonObject silkTouchBlockLoot(String blockId, String normalDropId, int count) {
      JsonObject loot = new JsonObject();
      loot.addProperty("type", "minecraft:block");
      JsonArray pools = new JsonArray();
      JsonObject pool = new JsonObject();
      pool.addProperty("rolls", 1);
      JsonArray entries = new JsonArray();
      JsonObject alternatives = new JsonObject();
      alternatives.addProperty("type", "minecraft:alternatives");
      JsonArray children = new JsonArray();
      JsonObject silk = itemEntry(blockId, 1);
      silk.add("conditions", matchToolEnchantCondition("minecraft:silk_touch", 1));
      children.add(silk);
      children.add(itemEntry(normalDropId, count));
      alternatives.add("children", children);
      entries.add(alternatives);
      pool.add("entries", entries);
      pools.add(pool);
      loot.add("pools", pools);
      return loot;
   }

   private static JsonObject oreBlockLoot(String blockId, String dropId, int minCount, int maxCount) {
      JsonObject loot = new JsonObject();
      loot.addProperty("type", "minecraft:block");
      JsonArray pools = new JsonArray();
      JsonObject pool = new JsonObject();
      pool.addProperty("rolls", 1);
      JsonArray entries = new JsonArray();
      JsonObject alternatives = new JsonObject();
      alternatives.addProperty("type", "minecraft:alternatives");
      JsonArray children = new JsonArray();
      JsonObject silk = itemEntry(blockId, 1);
      silk.add("conditions", matchToolEnchantCondition("minecraft:silk_touch", 1));
      children.add(silk);
      JsonObject normal = itemEntry(dropId, 1);
      JsonArray functions = new JsonArray();
      JsonObject setCount = new JsonObject();
      setCount.addProperty("function", "minecraft:set_count");
      JsonObject count = new JsonObject();
      count.addProperty("min", minCount);
      count.addProperty("max", maxCount);
      setCount.add("count", count);
      functions.add(setCount);
      JsonObject bonus = new JsonObject();
      bonus.addProperty("function", "minecraft:apply_bonus");
      bonus.addProperty("enchantment", "minecraft:fortune");
      bonus.addProperty("formula", "minecraft:ore_drops");
      functions.add(bonus);
      normal.add("functions", functions);
      children.add(normal);
      alternatives.add("children", children);
      entries.add(alternatives);
      pool.add("entries", entries);
      pools.add(pool);
      loot.add("pools", pools);
      return loot;
   }

   private static JsonObject itemEntry(String itemId, int count) {
      JsonObject entry = new JsonObject();
      entry.addProperty("type", "minecraft:item");
      entry.addProperty("name", itemId);
      if (count != 1) {
         JsonArray functions = new JsonArray();
         JsonObject setCount = new JsonObject();
         setCount.addProperty("function", "minecraft:set_count");
         setCount.addProperty("count", count);
         functions.add(setCount);
         entry.add("functions", functions);
      }
      return entry;
   }

   private static JsonArray matchToolEnchantCondition(String enchantment, int minLevel) {
      JsonArray conditions = new JsonArray();
      JsonObject matchTool = new JsonObject();
      matchTool.addProperty("condition", "minecraft:match_tool");
      JsonObject predicate = new JsonObject();
      JsonArray enchantments = new JsonArray();
      JsonObject enchantmentEntry = new JsonObject();
      enchantmentEntry.addProperty("enchantment", enchantment);
      JsonObject levels = new JsonObject();
      levels.addProperty("min", minLevel);
      enchantmentEntry.add("levels", levels);
      enchantments.add(enchantmentEntry);
      predicate.add("enchantments", enchantments);
      matchTool.add("predicate", predicate);
      conditions.add(matchTool);
      return conditions;
   }

   private static LuaTable checkedTable(LuaValue value, String type) {
      if (!value.istable()) {
         throw new LuaError("[MLua] " + type + " definition must be a table");
      }

      return value.checktable();
   }

   private static JsonArray requiredArray(LuaValue value, String field) {
      JsonElement element = luaToJson(value);
      if (!element.isJsonArray()) {
         throw new LuaError("[MLua] " + field + " must be an array-style table");
      }

      return element.getAsJsonArray();
   }

   private static JsonArray requiredStringArray(LuaValue value, String field) {
      if (!value.istable()) {
         throw new LuaError("[MLua] " + field + " must be an array-style table of strings");
      }

      LuaTable table = value.checktable();
      JsonArray result = new JsonArray();
      for(int i = 1; !table.get(i).isnil(); ++i) {
         LuaValue entry = table.get(i);
         if (!entry.isstring()) {
            throw new LuaError("[MLua] " + field + " entries must be strings");
         }

         result.add(entry.tojstring());
      }

      return result;
   }

   private static JsonObject requiredObject(LuaValue value, String field) {
      JsonElement element = luaToJson(value);
      if (!element.isJsonObject()) {
         throw new LuaError("[MLua] " + field + " must be a table with string keys");
      }

      return element.getAsJsonObject();
   }

   private static String requiredString(LuaValue value, String field) {
      if (!value.isstring()) {
         throw new LuaError("[MLua] " + field + " must be a string");
      }

      return value.tojstring();
   }

   private static JsonElement singleIngredient(LuaValue value, String field) {
      JsonElement element = luaToJson(value);
      if (!element.isJsonObject()) {
         throw new LuaError("[MLua] " + field + " must be a table like { item = \"namespace:id\" } or { tag = \"namespace:path\" }");
      }

      return element;
   }

   private static JsonObject recipeResult(LuaValue value) {
      JsonElement element = luaToJson(value);
      if (!element.isJsonObject()) {
         throw new LuaError("[MLua] result must be a table");
      }
      JsonObject result = element.getAsJsonObject();
      //--version 1.21.1
      if (result.has("item") && !result.has("id")) {
         result.add("id", result.remove("item"));
      }
      //--endversion
      //--version 26.1
      if (result.has("item") && !result.has("id")) {
         result.add("id", result.remove("item"));
      }
      //--endversion
      return result;
   }

   private static String normalizeTagKind(String value) {
      String normalized = value.trim().toLowerCase();
      return switch (normalized) {
         case "item", "items" -> "items";
         case "block", "blocks" -> "blocks";
         case "entity", "entities", "entity_type", "entity_types" -> "entity_types";
         default -> normalized;
      };
   }

   private static JsonElement luaToJson(LuaValue value) {
      if (value.isnil()) {
         return JsonNull.INSTANCE;
      }
      if (value.isboolean()) {
         return new JsonPrimitive(value.toboolean());
      }
      if (value.isnumber()) {
         double numeric = value.todouble();
         long integral = value.tolong();
         if (numeric == (double)integral) {
            return new JsonPrimitive(integral);
         }
         return new JsonPrimitive(numeric);
      }
      if (value.isstring()) {
         return new JsonPrimitive(value.tojstring());
      }
      if (!value.istable()) {
         throw new LuaError("[MLua] Unsupported JSON value type: " + value.typename());
      }

      LuaTable table = value.checktable();
      if (isArrayTable(table)) {
         JsonArray array = new JsonArray();
         for(int i = 1; !table.get(i).isnil(); ++i) {
            array.add(luaToJson(table.get(i)));
         }
         return array;
      }

      JsonObject object = new JsonObject();
      LuaValue key = LuaValue.NIL;
      while(true) {
         Varargs next = table.next(key);
         key = next.arg1();
         if (key.isnil()) {
            return object;
         }

         object.add(key.tojstring(), luaToJson(next.arg(2)));
      }
   }

   private static boolean isArrayTable(LuaTable table) {
      return !table.get(1).isnil();
   }

   private static MLuaTextureDescriptor textureDescriptor(LuaTable table, String... keys) {
      if (table == null) {
         return MLuaTextureDescriptor.empty();
      } else {
         String[] var2 = keys;
         int var3 = keys.length;

         for(int var4 = 0; var4 < var3; ++var4) {
            String key = var2[var4];
            LuaValue value = table.get(key);
            if (!value.isnil()) {
               return MLuaTextureDescriptor.fromLua(value);
            }
         }

         return MLuaTextureDescriptor.empty();
      }
   }

   private static String stringProperty(LuaTable table, String... keys) {
      if (table == null) {
         return null;
      } else {
         String[] var2 = keys;
         int var3 = keys.length;

         for(int var4 = 0; var4 < var3; ++var4) {
            String key = var2[var4];
            LuaValue value = table.get(key);
            if (value.isstring()) {
               return value.tojstring();
            }
         }

         return null;
      }
   }

   private static boolean booleanProperty(LuaTable table, String... keys) {
      if (table == null) {
         return false;
      } else {
         String[] var2 = keys;
         int var3 = keys.length;

         for(int var4 = 0; var4 < var3; ++var4) {
            String key = var2[var4];
            LuaValue value = table.get(key);
            if (!value.isnil()) {
               return value.optboolean(false);
            }
         }

         return false;
      }
   }

   private static boolean hasProperty(LuaTable table, String... keys) {
      if (table == null) {
         return false;
      } else {
         String[] var2 = keys;
         int var3 = keys.length;

         for(int var4 = 0; var4 < var3; ++var4) {
            String key = var2[var4];
            if (!table.get(key).isnil()) {
               return true;
            }
         }

         return false;
      }
   }

   private static LuaValue callbackProperty(LuaTable table, String... keys) {
      if (table == null) {
         return LuaValue.NIL;
      } else {
         String[] var2 = keys;
         int var3 = keys.length;

         for(int var4 = 0; var4 < var3; ++var4) {
            String key = var2[var4];
            LuaValue value = table.get(key);
            if (!value.isnil()) {
               return value;
            }
         }

         return LuaValue.NIL;
      }
   }

   private static int intProperty(LuaTable table, int fallback, String... keys) {
      if (table == null) {
         return fallback;
      } else {
         String[] var3 = keys;
         int var4 = keys.length;

         for(int var5 = 0; var5 < var4; ++var5) {
            String key = var3[var5];
            LuaValue value = table.get(key);
            if (!value.isnil()) {
               return value.optint(fallback);
            }
         }

         return fallback;
      }
   }

   private static int colorProperty(LuaTable table, int fallback, String... keys) {
      if (table == null) {
         return fallback;
      } else {
         String[] var3 = keys;
         int var4 = keys.length;

         for(int var5 = 0; var5 < var4; ++var5) {
            String key = var3[var5];
            LuaValue value = table.get(key);
            if (value.isnumber()) {
               return (int)value.tolong();
            }

            if (value.isstring()) {
               String text = value.tojstring().trim();
               if (text.startsWith("#")) {
                  text = text.substring(1);
               } else if (text.startsWith("0x") || text.startsWith("0X")) {
                  text = text.substring(2);
               }

               try {
                  return (int)Long.parseLong(text, 16);
               } catch (NumberFormatException var9) {
                  throw new LuaError("[MLua] Invalid color value: " + value.tojstring());
               }
            }
         }

         return fallback;
      }
   }

   private static MobEffectCategory effectCategory(LuaTable table) {
      String value = stringProperty(table, "category", "Category");
      if (value == null || value.isBlank()) {
         return MobEffectCategory.NEUTRAL;
      } else {
         String normalized = value.trim().toLowerCase();
         switch (normalized) {
            case "beneficial":
            case "benefit":
            case "good":
               return MobEffectCategory.BENEFICIAL;
            case "harmful":
            case "bad":
            case "negative":
               return MobEffectCategory.HARMFUL;
            case "neutral":
               return MobEffectCategory.NEUTRAL;
            default:
               throw new LuaError("[MLua] Unknown mob effect category: " + value);
         }
      }
   }

   private static String effectDescriptionId(String owner, String name, String explicit) {
      return explicit != null && !explicit.isBlank() ? explicit : "effect." + owner + "." + name;
   }

    private static java.util.List<String> descriptionList(LuaTable table) {
        if (table == null) return null;
        LuaValue desc = table.get("description");
        if (desc.isnil()) {
            desc = table.get("tooltip");
            if (desc.isnil()) return null;
        }
        if (desc.isstring()) {
            return java.util.List.of(desc.tojstring());
        }
        if (desc.istable()) {
            LuaTable t = desc.checktable();
            java.util.ArrayList<String> lines = new java.util.ArrayList();
            for (int i = 1; ; i++) {
                LuaValue line = t.get(i);
                if (line.isnil()) break;
                if (line.isstring()) lines.add(line.tojstring());
            }
            return lines.isEmpty() ? null : lines;
        }
        return null;
    }

    private static String resolvedModel(String owner, String defaultFolder, LuaTable table, String... keys) {
      String model = stringProperty(table, keys);
      return model != null && !model.isBlank() ? MLuaAssetResolver.resolveModelString(owner, model, defaultFolder) : null;
   }

   private static List<MobEffectInstance> potionEffects(LuaTable propsTable) {
      LuaValue effectsValue = propsTable != null ? propsTable.get("effects") : LuaValue.NIL;
      if (effectsValue.isnil()) {
         return List.of();
      } else if (!effectsValue.istable()) {
         throw new LuaError("[MLua] Potion effects must be a table");
      } else {
         List<MobEffectInstance> effects = new ArrayList();
         LuaTable effectsTable = effectsValue.checktable();
         int i = 1;

         while(true) {
            LuaValue entry = effectsTable.get(i);
            if (entry.isnil()) {
               return effects;
            }

            effects.add(potionEffect(entry.checktable()));
            ++i;
         }
      }
   }

   private static MobEffectInstance potionEffect(LuaTable table) {
      String effectId = table.get("effect").checkjstring();
      //--version 1.21.1
      var effect = BuiltInRegistries.MOB_EFFECT.getHolder(MLuaResourceLocations.parse(effectId)).orElse(null);
      //--endversion
      //--version 26.1
      var effect = BuiltInRegistries.MOB_EFFECT.get(MLuaResourceLocations.parse(effectId)).orElse(null);
      //--endversion
      //--version 1.20.1
      MobEffect effect = (MobEffect)BuiltInRegistries.MOB_EFFECT.get(MLuaResourceLocations.parse(effectId));
      //--endversion
      if (effect == null) {
         throw new LuaError("[MLua] Unknown potion effect: " + effectId);
      } else {
         int duration = table.get("duration").optint(600);
         int amplifier = table.get("amplifier").optint(0);
         boolean ambient = table.get("ambient").optboolean(false);
         boolean visible = table.get("visible").optboolean(true);
         boolean showIcon = table.get("showIcon").optboolean(true);
         return new MobEffectInstance(effect, duration, amplifier, ambient, visible, showIcon);
      }
   }

   private static record TagRegistration(boolean replace, JsonArray values) {
   }
}
