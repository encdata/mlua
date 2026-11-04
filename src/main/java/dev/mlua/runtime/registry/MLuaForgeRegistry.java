package dev.mlua.runtime.registry;

import com.mojang.datafixers.types.Type;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.mlua.loaderapi.GeneratedAssetKind;
import dev.mlua.loaderapi.EntityVisualInfo;
import dev.mlua.runtime.client.MLuaVisualBlockEntity;
import dev.mlua.minecraft.MLuaResourceLocations;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityType.Builder;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class MLuaForgeRegistry {
   private static final MLuaForgeRegistry INSTANCE = new MLuaForgeRegistry();
   private final MLuaForgeDeferredRegisters deferredRegisters = new MLuaForgeDeferredRegisters();
   private final Map<String, RegistryObject<Item>> items = new HashMap<>();
   private final Map<String, RegistryObject<Block>> blocks = new HashMap<>();
   private final Map<String, RegistryObject<CreativeModeTab>> tabs = new HashMap<>();
   private final Map<String, RegistryObject<EntityType<?>>> entities = new HashMap<>();
   private final Map<String, RegistryObject<ParticleType<?>>> particles = new HashMap<>();
   private final Map<String, RegistryObject<MobEffect>> mobEffects = new HashMap<>();
   private final Map<String, RegistryObject<Potion>> potions = new HashMap<>();
   private final Map<String, AtomicReference<RegistryObject<BlockEntityType<?>>>> visualBlockEntityTypes = new HashMap<>();
   private final MLuaForgeVisualMetadata visualMetadata = new MLuaForgeVisualMetadata();
   private final MLuaForgeGeneratedData generatedData = new MLuaForgeGeneratedData();
   private final Map<String, Supplier<AttributeSupplier>> entityAttributes = new HashMap<>();
   private boolean frozen;

   public static MLuaForgeRegistry get() {
      return INSTANCE;
   }

   public void attach(IEventBus bus) {
      this.attach("mlua", bus);
   }

   public void attach(String modId, IEventBus bus) {
      MLuaResourceNameValidator.validate(modId);
      this.deferredRegisters.attach(modId, bus);
   }

   public void freeze() {
      this.frozen = true;
   }

   public RegistryObject<Item> registerItem(String modId, String name, Supplier<Item> supplier) {
      this.ensureOpen(modId, "item", name);
      String key = this.key(modId, name);
      if (this.items.containsKey(key)) {
         throw this.duplicate("item", key);
      } else {
         RegistryObject<Item> object = this.deferredRegisters.item(modId).register(name, supplier);
         this.items.put(key, object);
         return object;
      }
   }

   public RegistryObject<Block> registerBlock(String modId, String name, Supplier<Block> supplier) {
      this.ensureOpen(modId, "block", name);
      String key = this.key(modId, name);
      if (this.blocks.containsKey(key)) {
         throw this.duplicate("block", key);
      } else {
         RegistryObject<Block> object = this.deferredRegisters.block(modId).register(name, supplier);
         this.blocks.put(key, object);
         return object;
      }
   }

   public void setItemVisuals(String modId, String name, MLuaTextureDescriptor texture, String model) {
      this.setItemTexture(modId, name, texture);
      this.setItemModel(modId, name, model);
   }

   public void setItemTexture(String modId, String name, MLuaTextureDescriptor texture) {
      this.visualMetadata.setItemTexture(this.key(modId, name), texture);
   }

   public void setItemModel(String modId, String name, String model) {
      this.visualMetadata.setItemModel(this.key(modId, name), model);
   }

   public void setItemCopy(String modId, String name, String copy) {
      this.visualMetadata.setItemCopy(this.key(modId, name), copy);
   }

   public void setItemCopyModel(String modId, String name, boolean copyModel) {
      this.visualMetadata.setItemCopyModel(this.key(modId, name), copyModel);
   }

   public void setBlockVisuals(String modId, String name, MLuaTextureDescriptor texture, String model) {
      this.setBlockTexture(modId, name, texture);
      this.setBlockModel(modId, name, model);
   }

   public void setBlockTexture(String modId, String name, MLuaTextureDescriptor texture) {
      this.visualMetadata.setBlockTexture(this.key(modId, name), texture);
   }

   public void setBlockModel(String modId, String name, String model) {
      this.visualMetadata.setBlockModel(this.key(modId, name), model);
   }

   public void setBlockCopy(String modId, String name, String copy) {
      this.visualMetadata.setBlockCopy(this.key(modId, name), copy);
   }

   public void setBlockCopyModel(String modId, String name, boolean copyModel) {
      this.visualMetadata.setBlockCopyModel(this.key(modId, name), copyModel);
   }

    public void setItemDescription(String modId, String name, List<String> description) {
        this.visualMetadata.setItemDescription(this.key(modId, name), description);
    }

    public List<String> getItemDescription(String modId, String name) {
        return this.visualMetadata.itemDescription(modId, name);
    }

    public void setBlockDescription(String modId, String name, List<String> description) {
        this.visualMetadata.setBlockDescription(this.key(modId, name), description);
    }

    public List<String> getBlockDescription(String modId, String name) {
        return this.visualMetadata.blockDescription(modId, name);
    }

    public Supplier<BlockEntityType<MLuaVisualBlockEntity>> visualBlockEntitySupplier(String modId) {
      AtomicReference<RegistryObject<BlockEntityType<?>>> ref = (AtomicReference)this.visualBlockEntityTypes.computeIfAbsent(modId, (id) -> {
         return new AtomicReference();
      });
      return () -> {
         RegistryObject<BlockEntityType<?>> object = (RegistryObject)ref.get();
         if (object == null) {
            return null;
         } else {
            BlockEntityType<MLuaVisualBlockEntity> type = (BlockEntityType)object.get();
            return type;
         }
      };
   }

   public Map<String, RegistryObject<BlockEntityType<?>>> getVisualBlockEntityTypes() {
      Map<String, RegistryObject<BlockEntityType<?>>> result = new HashMap<>();
      Iterator var2 = this.visualBlockEntityTypes.entrySet().iterator();

      while(var2.hasNext()) {
         Map.Entry<String, AtomicReference<RegistryObject<BlockEntityType<?>>>> entry = (Map.Entry)var2.next();
         RegistryObject<BlockEntityType<?>> value = (RegistryObject)((AtomicReference)entry.getValue()).get();
         if (value != null) {
            result.put((String)entry.getKey(), value);
         }
      }

      return result;
   }

   public void registerVisualBlockEntityTypes() {
      Iterator var1 = ((Set)this.blocks.keySet().stream().map((k) -> {
         return k.substring(0, k.indexOf(58));
      }).collect(Collectors.toSet())).iterator();

      while(var1.hasNext()) {
         String modId = (String)var1.next();
         AtomicReference<RegistryObject<BlockEntityType<?>>> ref = this.visualBlockEntityTypes.computeIfAbsent(modId, (id) -> new AtomicReference());
         if (ref.get() == null) {
            DeferredRegister<BlockEntityType<?>> reg = this.deferredRegisters.blockEntity(modId);
            ref.set(reg.register("visual_block_entity", () -> {
               List<Block> modBlocks = this.blocks.entrySet().stream().filter((entry) -> {
                  return ((String)entry.getKey()).startsWith(modId + ":");
               }).map((entry) -> {
                  return (Block)((RegistryObject)entry.getValue()).get();
               }).filter(Objects::nonNull).toList();
               return Builder.of(MLuaVisualBlockEntity::new, (Block[])modBlocks.toArray((x$0) -> {
                  return new Block[x$0];
               })).build((Type)null);
            }));
         }
      }

   }

   public MLuaTextureDescriptor getItemTexture(String namespace, String path) {
      return this.visualMetadata.itemTexture(namespace, path);
   }

   public MLuaTextureDescriptor getBlockTexture(String namespace, String path) {
      return this.visualMetadata.blockTexture(namespace, path);
   }

   public String getItemModel(String namespace, String path) {
      return this.visualMetadata.itemModel(namespace, path);
   }

   public String getBlockModel(String namespace, String path) {
      return this.visualMetadata.blockModel(namespace, path);
   }

   public Map<String, String> getItemModels() {
      return this.visualMetadata.itemModels();
   }

   public Map<String, String> getBlockModels() {
      return this.visualMetadata.blockModels();
   }

   public Map<String, RegistryObject<Item>> getRegisteredItems() {
      return this.items;
   }

   public Map<String, RegistryObject<Block>> getRegisteredBlocks() {
      return this.blocks;
   }

   public Map<String, String> getItemCopies() {
      return this.visualMetadata.itemCopies();
   }

   public Map<String, String> getBlockCopies() {
      return this.visualMetadata.blockCopies();
   }

   public Map<String, Boolean> getItemCopyModels() {
      return this.visualMetadata.itemCopyModels();
   }

   public Map<String, Boolean> getBlockCopyModels() {
      return this.visualMetadata.blockCopyModels();
   }

   public void registerRecipe(String modId, String name, JsonObject definition) {
      this.ensureOpen(modId, "recipe", name);
      String id = this.contentId(modId, name).toString();
      this.generatedData.registerRecipe(id, definition);
   }

   public void registerLootTable(String modId, String name, JsonObject definition) {
      this.ensureOpen(modId, "loot table", name);
      String id = this.contentId(modId, name).toString();
      this.generatedData.registerLootTable(id, definition);
   }

   public void registerBlockState(String modId, String name, JsonObject definition) {
      this.ensureOpen(modId, "blockstate", name);
      String id = this.contentId(modId, name).toString();
      this.generatedData.registerBlockState(id, definition);
   }

   public boolean unregisterGeneratedAsset(GeneratedAssetKind kind, String modId, String name, JsonObject expectedDefinition) {
      String id = this.contentId(modId, name).toString();
      return this.generatedData.unregister(kind, id, expectedDefinition);
   }

   public boolean restoreGeneratedAsset(GeneratedAssetKind kind, String modId, String name,
                                        JsonObject expectedCurrent, JsonObject previousDefinition) {
      String id = this.contentId(modId, name).toString();
      return this.generatedData.restore(kind, id, expectedCurrent, previousDefinition);
   }

   public void registerItemTag(String modId, String name, boolean replace, JsonArray values) {
      this.ensureOpen(modId, "item tag", name);
      this.generatedData.registerTag(GeneratedAssetKind.ITEM_TAG, this.contentId(modId, name).toString(), replace, values);
   }

   public void registerBlockTag(String modId, String name, boolean replace, JsonArray values) {
      this.ensureOpen(modId, "block tag", name);
      this.generatedData.registerTag(GeneratedAssetKind.BLOCK_TAG, this.contentId(modId, name).toString(), replace, values);
   }

   public void registerEntityTypeTag(String modId, String name, boolean replace, JsonArray values) {
      this.ensureOpen(modId, "entity_type tag", name);
      this.generatedData.registerTag(GeneratedAssetKind.ENTITY_TYPE_TAG, this.contentId(modId, name).toString(), replace, values);
   }

   public Map<String, JsonObject> getRecipes() {
      return this.generatedData.recipes();
   }

   public Map<String, JsonObject> getLootTables() {
      return this.generatedData.lootTables();
   }

   public Map<String, JsonObject> getItemTags() {
      return this.generatedData.itemTags();
   }

   public Map<String, JsonObject> getBlockStates() {
      return this.generatedData.blockStates();
   }

   public Map<String, JsonObject> getBlockTags() {
      return this.generatedData.blockTags();
   }

   public Map<String, JsonObject> getEntityTypeTags() {
      return this.generatedData.entityTypeTags();
   }

   public RegistryObject<Item> registerBlockItem(String modId, String blockName) {
      RegistryObject<Block> block = this.getBlock(modId, blockName);
      if (block == null) {
         throw new IllegalArgumentException("[MLua] Block item requires a registered block: " + modId + ":" + blockName);
      }
      return this.registerItem(modId, blockName,
         () -> new MLuaNamedBlockItem(block.get(), new Item.Properties(), blockName));
   }

   public RegistryObject<CreativeModeTab> registerCreativeTab(String modId, String name, Supplier<CreativeModeTab> supplier) {
      this.ensureOpen(modId, "creative tab", name);
      String key = this.key(modId, name);
      if (this.tabs.containsKey(key)) {
         throw this.duplicate("creative tab", key);
      } else {
         RegistryObject<CreativeModeTab> object = this.deferredRegisters.tab(modId).register(name, supplier);
         this.tabs.put(key, object);
         return object;
      }
   }

   public RegistryObject<Item> getItem(String modId, String name) {
      return this.items.get(this.key(modId, name));
   }

   public RegistryObject<Block> getBlock(String modId, String name) {
      return this.blocks.get(this.key(modId, name));
   }

   public RegistryObject<CreativeModeTab> getCreativeTab(String modId, String name) {
      return this.tabs.get(this.key(modId, name));
   }

   public RegistryObject<ParticleType<?>> getParticle(String modId, String name) {
      return this.particles.get(this.key(modId, name));
   }

   public RegistryObject<Potion> getPotion(String modId, String name) {
      return this.potions.get(this.key(modId, name));
   }

   public RegistryObject<MobEffect> getMobEffect(String modId, String name) {
      return this.mobEffects.get(this.key(modId, name));
   }

   public Map<String, RegistryObject<CreativeModeTab>> getCreativeTabs() {
      return this.tabs;
   }

   public Map<String, RegistryObject<ParticleType<?>>> getParticles() {
      return this.particles;
   }

   public Map<String, RegistryObject<Potion>> getPotions() {
      return this.potions;
   }

   public Map<String, RegistryObject<MobEffect>> getMobEffects() {
      return this.mobEffects;
   }

   public RegistryObject<EntityType<?>> registerEntity(String modId, String name, Supplier<EntityType<?>> supplier, Supplier<AttributeSupplier> attributes) {
      this.ensureOpen(modId, "entity", name);
      String key = this.key(modId, name);
      if (this.entities.containsKey(key)) {
         throw this.duplicate("entity", key);
      } else {
         RegistryObject<EntityType<?>> object = this.deferredRegisters.entity(modId).register(name, supplier);
         this.entities.put(key, object);
         this.entityAttributes.put(key, attributes);
         return object;
      }
   }

   public RegistryObject<Item> registerSpawnEgg(String modId, String name, Supplier<EntityType<?>> entityType, int primaryColor, int secondaryColor) {
      RegistryObject<Item> object = this.registerItem(modId, name, () -> {
         return new MLuaNamedSpawnEggItem((EntityType)entityType.get(), primaryColor, secondaryColor, new Item.Properties(), modId, name);
      });
      this.setItemVisuals(modId, name, MLuaTextureDescriptor.empty(), "minecraft:item/template_spawn_egg");
      return object;
   }

   public RegistryObject<EntityType<?>> getEntity(String modId, String name) {
      return this.entities.get(this.key(modId, name));
   }

   public RegistryObject<ParticleType<?>> registerParticle(String modId, String name, boolean overrideLimiter) {
      return this.registerParticle(modId, name, () -> new SimpleParticleType(overrideLimiter));
   }

   public RegistryObject<ParticleType<?>> registerParticle(String modId, String name,
                                                           Supplier<ParticleType<?>> supplier) {
      this.ensureOpen(modId, "particle", name);
      Objects.requireNonNull(supplier, "supplier");
      String key = this.key(modId, name);
      if (this.particles.containsKey(key)) {
         throw this.duplicate("particle", key);
      } else {
         RegistryObject<ParticleType<?>> object = this.deferredRegisters.particle(modId).register(name, supplier);
         this.particles.put(key, object);
         return object;
      }
   }

   public RegistryObject<Potion> registerPotion(String modId, String name, Supplier<Potion> supplier) {
      this.ensureOpen(modId, "potion", name);
      String key = this.key(modId, name);
      if (this.potions.containsKey(key)) {
         throw this.duplicate("potion", key);
      } else {
         RegistryObject<Potion> object = this.deferredRegisters.potion(modId).register(name, supplier);
         this.potions.put(key, object);
         return object;
      }
   }

   public RegistryObject<MobEffect> registerMobEffect(String modId, String name, Supplier<MobEffect> supplier) {
      this.ensureOpen(modId, "mob effect", name);
      String key = this.key(modId, name);
      if (this.mobEffects.containsKey(key)) {
         throw this.duplicate("mob effect", key);
      } else {
         RegistryObject<MobEffect> object = this.deferredRegisters.mobEffect(modId).register(name, supplier);
         this.mobEffects.put(key, object);
         return object;
      }
   }

   public Map<String, RegistryObject<EntityType<?>>> getEntities() {
      return this.entities;
   }

   public void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
      Iterator var2 = this.entities.entrySet().iterator();

      while(var2.hasNext()) {
         Map.Entry<String, RegistryObject<EntityType<?>>> entry = (Map.Entry)var2.next();
         EntityType<?> type = (EntityType)((RegistryObject)entry.getValue()).get();
         if (type != null) {
            Supplier<AttributeSupplier> attrSupplier = (Supplier)this.entityAttributes.get(entry.getKey());
            if (attrSupplier != null) {
               event.put((EntityType)type, (AttributeSupplier)attrSupplier.get());
            }
         }
      }

   }

   public boolean isRegisteredItem(String namespace, String path) {
      return this.items.containsKey(namespace + ":" + path);
   }

   public boolean isRegisteredBlock(String namespace, String path) {
      return this.blocks.containsKey(namespace + ":" + path);
   }

   private void ensureOpen(String modId, String type, String name) {
      MLuaResourceNameValidator.validate(modId);
      if (name == null || name.isBlank()) {
         throw new IllegalArgumentException("[MLua] Invalid " + type + " name: " + name);
      }
      this.contentId(modId, name);
      if (this.frozen) {
         throw new IllegalStateException("[MLua] Cannot register " + type + " '" + name + "': registries are already frozen. Put this in a .registry.lua script.");
      }
   }

   public void setEntityVisuals(String modId, String name, String texture, String model, float shadowRadius) {
      this.visualMetadata.setEntityVisual(this.key(modId, name), new MLuaEntityVisualDescriptor(texture, model, shadowRadius));
   }

   public MLuaEntityVisualDescriptor getEntityVisual(String key) {
      return this.visualMetadata.entityVisual(key);
   }

   public boolean restoreEntityVisual(String modId, String name, EntityVisualInfo expectedCurrent,
                                      EntityVisualInfo previousVisual) {
      return this.visualMetadata.restoreEntityVisual(this.key(modId, name), expectedCurrent, previousVisual);
   }

   private ResourceLocation contentId(String modId, String name) {
      String id = name.contains(":") ? name : modId + ":" + name;
      return MLuaResourceLocations.parse(id);
   }

   private String key(String modId, String name) {
      MLuaResourceNameValidator.validate(modId);
      MLuaResourceNameValidator.validate(name);
      return modId + ":" + name;
   }

   private IllegalArgumentException duplicate(String type, String key) {
      return new IllegalArgumentException("[MLua] Duplicate " + type + " registration: " + key);
   }
}
