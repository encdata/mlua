package dev.mlua.runtime.neoforge;

import com.mojang.datafixers.types.Type;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.mlua.loaderapi.GeneratedAssetKind;
import dev.mlua.loaderapi.EntityVisualInfo;
import dev.mlua.runtime.client.MLuaVisualBlockEntity;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.runtime.registry.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
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
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;


public final class MLuaNeoForgeRegistry {
   private static final MLuaNeoForgeRegistry INSTANCE = new MLuaNeoForgeRegistry();
   private static final ResourceKey<Registry<CreativeModeTab>> CREATIVE_MODE_TAB_REGISTRY = resolveCreativeModeTabRegistryKey();
   private final Map<String, DeferredRegister<Item>> itemRegisters = new HashMap<>();
   private final Map<String, DeferredRegister<Block>> blockRegisters = new HashMap<>();
   private final Map<String, DeferredRegister<CreativeModeTab>> tabRegisters = new HashMap<>();
   private final Map<String, DeferredRegister<EntityType<?>>> entityRegisters = new HashMap<>();
   private final Map<String, DeferredRegister<ParticleType<?>>> particleRegisters = new HashMap<>();
   private final Map<String, DeferredRegister<MobEffect>> mobEffectRegisters = new HashMap<>();
   private final Map<String, DeferredRegister<Potion>> potionRegisters = new HashMap<>();
   private final Map<String, DeferredRegister<BlockEntityType<?>>> blockEntityRegisters = new HashMap<>();
   private final Map<String, Supplier<Item>> items = new HashMap<>();
   private final Map<String, Supplier<Block>> blocks = new HashMap<>();
   private final Map<String, Supplier<CreativeModeTab>> tabs = new HashMap<>();
   private final Map<String, Supplier<EntityType<?>>> entities = new HashMap<>();
   private final Map<String, Supplier<ParticleType<?>>> particles = new HashMap<>();
   private final Map<String, Supplier<MobEffect>> mobEffects = new HashMap<>();
   private final Map<String, Supplier<Potion>> potions = new HashMap<>();
   private final Map<String, AtomicReference<Supplier<BlockEntityType<?>>>> visualBlockEntityTypes = new HashMap<>();
   private final Map<String, MLuaTextureDescriptor> itemTextures = new HashMap<>();
   private final Map<String, MLuaTextureDescriptor> blockTextures = new HashMap<>();
   private final Map<String, String> itemModels = new HashMap<>();
   private final Map<String, String> blockModels = new HashMap<>();
    private final Map<String, String> itemCopies = new HashMap<>();
    private final Map<String, String> blockCopies = new HashMap<>();
    private final Map<String, Boolean> itemCopyModels = new HashMap<>();
    private final Map<String, Boolean> blockCopyModels = new HashMap<>();
   private final Map<String, List<String>> itemDescriptions = new HashMap<>();
   private final Map<String, List<String>> blockDescriptions = new HashMap<>();
   private final Map<String, JsonObject> recipes = new HashMap<>();
   private final Map<String, JsonObject> lootTables = new HashMap<>();
   private final Map<String, JsonObject> blockStates = new HashMap<>();
   private final Map<String, JsonObject> itemTags = new HashMap<>();
   private final Map<String, JsonObject> blockTags = new HashMap<>();
   private final Map<String, JsonObject> entityTypeTags = new HashMap<>();
    private final Map<String, MLuaEntityVisualDescriptor> entityVisuals = new HashMap<>();
    private final Map<String, Supplier<AttributeSupplier>> entityAttributes = new HashMap<>();
   private final Map<String, IEventBus> attachedBuses = new HashMap<>();
   private final Set<String> attachedModIds = new HashSet();
   private boolean frozen;

   public static MLuaNeoForgeRegistry get() {
      return INSTANCE;
   }

   public void attach(IEventBus bus) {
      this.attach("mlua", bus);
   }

   public void attach(String modId, IEventBus bus) {
      MLuaResourceNameValidator.validate(modId);
      this.attachedBuses.put(modId, bus);
      if (this.attachedModIds.add(modId)) {
         DeferredRegister<Item> itemReg = (DeferredRegister)this.itemRegisters.get(modId);
         DeferredRegister<Block> blockReg = (DeferredRegister)this.blockRegisters.get(modId);
         DeferredRegister<CreativeModeTab> tabReg = (DeferredRegister)this.tabRegisters.get(modId);
         DeferredRegister<EntityType<?>> entityReg = (DeferredRegister)this.entityRegisters.get(modId);
         DeferredRegister<ParticleType<?>> particleReg = (DeferredRegister)this.particleRegisters.get(modId);
         DeferredRegister<MobEffect> mobEffectReg = (DeferredRegister)this.mobEffectRegisters.get(modId);
         DeferredRegister<Potion> potionReg = (DeferredRegister)this.potionRegisters.get(modId);
         DeferredRegister<BlockEntityType<?>> blockEntityReg = (DeferredRegister)this.blockEntityRegisters.get(modId);
         if (itemReg != null) {
            itemReg.register(bus);
         }

         if (blockReg != null) {
            blockReg.register(bus);
         }

         if (tabReg != null) {
            tabReg.register(bus);
         }

         if (entityReg != null) {
            entityReg.register(bus);
         }

         if (particleReg != null) {
            particleReg.register(bus);
         }

         if (mobEffectReg != null) {
            mobEffectReg.register(bus);
         }

         if (potionReg != null) {
            potionReg.register(bus);
         }

         if (blockEntityReg != null) {
            blockEntityReg.register(bus);
         }

      }
   }

   public void freeze() {
      this.frozen = true;
   }

   public Supplier<Item> registerItem(String modId, String name, Supplier<Item> supplier) {
      this.ensureOpen(modId, "item", name);
      String key = this.key(modId, name);
      if (this.items.containsKey(key)) {
         throw this.duplicate("item", key);
      } else {
         Supplier<Item> object = this.itemRegister(modId).register(name, supplier);
         this.items.put(key, object);
         return object;
      }
   }

   public Supplier<Block> registerBlock(String modId, String name, Supplier<Block> supplier) {
      this.ensureOpen(modId, "block", name);
      String key = this.key(modId, name);
      if (this.blocks.containsKey(key)) {
         throw this.duplicate("block", key);
      } else {
         Supplier<Block> object = this.blockRegister(modId).register(name, supplier);
         this.blocks.put(key, object);
         return object;
      }
   }

   public void setItemVisuals(String modId, String name, MLuaTextureDescriptor texture, String model) {
      this.setItemTexture(modId, name, texture);
      this.setItemModel(modId, name, model);
   }

   public void setItemTexture(String modId, String name, MLuaTextureDescriptor texture) {
      this.setMetadata(this.itemTextures, this.key(modId, name), texture == null || texture.isEmpty() ? null : texture);
   }

   public void setItemModel(String modId, String name, String model) {
      this.setMetadata(this.itemModels, this.key(modId, name), model == null || model.isBlank() ? null : model);
   }

   public void setItemCopy(String modId, String name, String copy) {
      this.setMetadata(this.itemCopies, this.key(modId, name), copy == null || copy.isBlank() ? null : copy);
   }

   public void setItemCopyModel(String modId, String name, boolean copyModel) {
      String key = this.key(modId, name);
      if (copyModel) {
         this.itemCopyModels.put(key, true);
      } else {
         this.itemCopyModels.remove(key);
      }

   }

   public void setBlockVisuals(String modId, String name, MLuaTextureDescriptor texture, String model) {
      this.setBlockTexture(modId, name, texture);
      this.setBlockModel(modId, name, model);
   }

   public void setBlockTexture(String modId, String name, MLuaTextureDescriptor texture) {
      this.setMetadata(this.blockTextures, this.key(modId, name), texture == null || texture.isEmpty() ? null : texture);
   }

   public void setBlockModel(String modId, String name, String model) {
      this.setMetadata(this.blockModels, this.key(modId, name), model == null || model.isBlank() ? null : model);
   }

   public void setBlockCopy(String modId, String name, String copy) {
      this.setMetadata(this.blockCopies, this.key(modId, name), copy == null || copy.isBlank() ? null : copy);
   }

   public void setBlockCopyModel(String modId, String name, boolean copyModel) {
      String key = this.key(modId, name);
      if (copyModel) {
         this.blockCopyModels.put(key, true);
      } else {
         this.blockCopyModels.remove(key);
      }

   }

    public void setItemDescription(String modId, String name, List<String> description) {
        String key = this.key(modId, name);
        if (description == null) {
            this.itemDescriptions.remove(key);
        } else {
            this.itemDescriptions.put(key, List.copyOf(description));
        }
    }

    public List<String> getItemDescription(String modId, String name) {
        return this.getVisual(this.itemDescriptions, modId, name, null);
    }

    public void setBlockDescription(String modId, String name, List<String> description) {
        String key = this.key(modId, name);
        if (description == null) {
            this.blockDescriptions.remove(key);
        } else {
            this.blockDescriptions.put(key, List.copyOf(description));
        }
    }

    public List<String> getBlockDescription(String modId, String name) {
        return this.getVisual(this.blockDescriptions, modId, name, null);
    }

    public Supplier<BlockEntityType<MLuaVisualBlockEntity>> visualBlockEntitySupplier(String modId) {
      AtomicReference<Supplier<BlockEntityType<?>>> ref = (AtomicReference)this.visualBlockEntityTypes.computeIfAbsent(modId, (id) -> {
         return new AtomicReference();
      });
      return () -> {
         Supplier<BlockEntityType<?>> object = (Supplier)ref.get();
         if (object == null) {
            return null;
         } else {
            BlockEntityType<MLuaVisualBlockEntity> type = (BlockEntityType)object.get();
            return type;
         }
      };
   }

   public Map<String, Supplier<BlockEntityType<?>>> getVisualBlockEntityTypes() {
      Map<String, Supplier<BlockEntityType<?>>> result = new HashMap<>();
      Iterator var2 = this.visualBlockEntityTypes.entrySet().iterator();

      while(var2.hasNext()) {
         Map.Entry<String, AtomicReference<Supplier<BlockEntityType<?>>>> entry = (Map.Entry)var2.next();
         Supplier<BlockEntityType<?>> value = (Supplier)((AtomicReference)entry.getValue()).get();
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
         if (!this.blockEntityRegisters.containsKey(modId)) {
            DeferredRegister<BlockEntityType<?>> reg = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, modId);
            this.blockEntityRegisters.put(modId, reg);
            AtomicReference<Supplier<BlockEntityType<?>>> ref = (AtomicReference)this.visualBlockEntityTypes.computeIfAbsent(modId, (id) -> {
               return new AtomicReference();
            });
            ref.set(reg.register("visual_block_entity", () -> {
               List<Block> modBlocks = this.blocks.entrySet().stream().filter((entry) -> {
                  return ((String)entry.getKey()).startsWith(modId + ":");
               }).map((entry) -> {
                  return (Block)((Supplier)entry.getValue()).get();
               }).filter(Objects::nonNull).toList();
               return Builder.of(MLuaVisualBlockEntity::new, (Block[])modBlocks.toArray((x$0) -> {
                  return new Block[x$0];
               })).build((Type)null);
            }));
         }
      }

   }

   public MLuaTextureDescriptor getItemTexture(String namespace, String path) {
      return (MLuaTextureDescriptor)this.getVisual(this.itemTextures, namespace, path, MLuaTextureDescriptor.empty());
   }

   public MLuaTextureDescriptor getBlockTexture(String namespace, String path) {
      return (MLuaTextureDescriptor)this.getVisual(this.blockTextures, namespace, path, MLuaTextureDescriptor.empty());
   }

   public String getItemModel(String namespace, String path) {
      return this.getVisual(this.itemModels, namespace, path, (String)null);
   }

   public String getBlockModel(String namespace, String path) {
      return this.getVisual(this.blockModels, namespace, path, (String)null);
   }

   public Map<String, String> getItemModels() {
      return this.itemModels;
   }

   public Map<String, String> getBlockModels() {
      return this.blockModels;
   }

   public Map<String, Supplier<Item>> getRegisteredItems() {
      return this.items;
   }

   public Map<String, Supplier<Block>> getRegisteredBlocks() {
      return this.blocks;
   }

   public Map<String, String> getItemCopies() {
      return this.itemCopies;
   }

   public Map<String, String> getBlockCopies() {
      return this.blockCopies;
   }

   public Map<String, Boolean> getItemCopyModels() {
      return this.itemCopyModels;
   }

   public Map<String, Boolean> getBlockCopyModels() {
      return this.blockCopyModels;
   }

   public void registerRecipe(String modId, String name, JsonObject definition) {
      this.ensureOpen(modId, "recipe", name);
      String id = this.contentId(modId, name).toString();
      this.putUnique(this.recipes, "recipe", id, definition.deepCopy());
   }

   public void registerLootTable(String modId, String name, JsonObject definition) {
      this.ensureOpen(modId, "loot table", name);
      String id = this.contentId(modId, name).toString();
      this.putUnique(this.lootTables, "loot table", id, definition.deepCopy());
   }

   public void registerBlockState(String modId, String name, JsonObject definition) {
      this.ensureOpen(modId, "blockstate", name);
      String id = this.contentId(modId, name).toString();
      this.putUnique(this.blockStates, "blockstate", id, definition.deepCopy());
   }

   public boolean unregisterGeneratedAsset(GeneratedAssetKind kind, String modId, String name, JsonObject expectedDefinition) {
      String id = this.contentId(modId, name).toString();
      Map<String, JsonObject> assets = switch (kind) {
         case RECIPE -> this.recipes;
         case LOOT_TABLE -> this.lootTables;
         case BLOCKSTATE -> this.blockStates;
         case ITEM_TAG, BLOCK_TAG, ENTITY_TYPE_TAG -> throw new IllegalArgumentException("Use tag rollback for tag assets.");
      };
      JsonObject current = assets.get(id);
      return current != null && current.equals(expectedDefinition) && assets.remove(id, current);
   }

   public boolean restoreGeneratedAsset(GeneratedAssetKind kind, String modId, String name,
                                        JsonObject expectedCurrent, JsonObject previousDefinition) {
      String id = this.contentId(modId, name).toString();
      Map<String, JsonObject> assets = switch (kind) {
         case RECIPE -> this.recipes;
         case LOOT_TABLE -> this.lootTables;
         case BLOCKSTATE -> this.blockStates;
         case ITEM_TAG -> this.itemTags;
         case BLOCK_TAG -> this.blockTags;
         case ENTITY_TYPE_TAG -> this.entityTypeTags;
      };
      JsonObject current = assets.get(id);
      if (current == null || !current.equals(expectedCurrent)) return false;
      if (previousDefinition == null) return assets.remove(id, current);
      assets.put(id, previousDefinition.deepCopy());
      return true;
   }

   public void registerItemTag(String modId, String name, boolean replace, JsonArray values) {
      this.registerTag(this.itemTags, modId, "item tag", name, replace, values);
   }

   public void registerBlockTag(String modId, String name, boolean replace, JsonArray values) {
      this.registerTag(this.blockTags, modId, "block tag", name, replace, values);
   }

   public void registerEntityTypeTag(String modId, String name, boolean replace, JsonArray values) {
      this.registerTag(this.entityTypeTags, modId, "entity_type tag", name, replace, values);
   }

   public Map<String, JsonObject> getRecipes() {
      return this.recipes;
   }

   public Map<String, JsonObject> getLootTables() {
      return this.lootTables;
   }

   public Map<String, JsonObject> getItemTags() {
      return this.itemTags;
   }

   public Map<String, JsonObject> getBlockStates() {
      return this.blockStates;
   }

   public Map<String, JsonObject> getBlockTags() {
      return this.blockTags;
   }

   public Map<String, JsonObject> getEntityTypeTags() {
      return this.entityTypeTags;
   }

   public Supplier<Item> registerBlockItem(String modId, String blockName) {
      Supplier<Block> block = this.getBlock(modId, blockName);
      if (block == null) {
         throw new IllegalArgumentException("[MLua] Block item requires a registered block: " + modId + ":" + blockName);
      }
      return this.registerItem(modId, blockName,
         () -> new MLuaNamedBlockItem(block.get(), new Item.Properties(), blockName));
   }

   public Supplier<CreativeModeTab> registerCreativeTab(String modId, String name, Supplier<CreativeModeTab> supplier) {
      this.ensureOpen(modId, "creative tab", name);
      String key = this.key(modId, name);
      if (this.tabs.containsKey(key)) {
         throw this.duplicate("creative tab", key);
      } else {
         Supplier<CreativeModeTab> object = this.tabRegister(modId).register(name, supplier);
         this.tabs.put(key, object);
         return object;
      }
   }

   public Supplier<Item> getItem(String modId, String name) {
      return this.items.get(this.key(modId, name));
   }

   public Supplier<Block> getBlock(String modId, String name) {
      return this.blocks.get(this.key(modId, name));
   }

   public Supplier<CreativeModeTab> getCreativeTab(String modId, String name) {
      return this.tabs.get(this.key(modId, name));
   }

   public Supplier<ParticleType<?>> getParticle(String modId, String name) {
      return this.particles.get(this.key(modId, name));
   }

   public Supplier<Potion> getPotion(String modId, String name) {
      return this.potions.get(this.key(modId, name));
   }

   public Supplier<MobEffect> getMobEffect(String modId, String name) {
      return this.mobEffects.get(this.key(modId, name));
   }

   public Map<String, Supplier<CreativeModeTab>> getCreativeTabs() {
      return this.tabs;
   }

   public Map<String, Supplier<ParticleType<?>>> getParticles() {
      return this.particles;
   }

   public Map<String, Supplier<Potion>> getPotions() {
      return this.potions;
   }

   public Map<String, Supplier<MobEffect>> getMobEffects() {
      return this.mobEffects;
   }

   public Supplier<EntityType<?>> registerEntity(String modId, String name, Supplier<EntityType<?>> supplier, Supplier<AttributeSupplier> attributes) {
      this.ensureOpen(modId, "entity", name);
      String key = this.key(modId, name);
      if (this.entities.containsKey(key)) {
         throw this.duplicate("entity", key);
      } else {
         Supplier<EntityType<?>> object = this.entityRegister(modId).register(name, supplier);
         this.entities.put(key, object);
         this.entityAttributes.put(key, attributes);
         return object;
      }
   }

   public Supplier<Item> registerSpawnEgg(String modId, String name, Supplier<EntityType<?>> entityType, int primaryColor, int secondaryColor) {
      Supplier<Item> object = this.registerItem(modId, name, () -> {
         return new MLuaNamedSpawnEggItem((EntityType)entityType.get(), primaryColor, secondaryColor, new Item.Properties(), modId, name);
      });
      this.setItemVisuals(modId, name, MLuaTextureDescriptor.empty(), "minecraft:item/template_spawn_egg");
      return object;
   }

   public Supplier<EntityType<?>> getEntity(String modId, String name) {
      return this.entities.get(this.key(modId, name));
   }

   public Supplier<ParticleType<?>> registerParticle(String modId, String name, boolean overrideLimiter) {
      return this.registerParticle(modId, name, () -> new SimpleParticleType(overrideLimiter));
   }

   public Supplier<ParticleType<?>> registerParticle(String modId, String name,
                                                           Supplier<ParticleType<?>> supplier) {
      this.ensureOpen(modId, "particle", name);
      Objects.requireNonNull(supplier, "supplier");
      String key = this.key(modId, name);
      if (this.particles.containsKey(key)) {
         throw this.duplicate("particle", key);
      } else {
         Supplier<ParticleType<?>> object = this.particleRegister(modId).register(name, supplier);
         this.particles.put(key, object);
         return object;
      }
   }

   public Supplier<Potion> registerPotion(String modId, String name, Supplier<Potion> supplier) {
      this.ensureOpen(modId, "potion", name);
      String key = this.key(modId, name);
      if (this.potions.containsKey(key)) {
         throw this.duplicate("potion", key);
      } else {
         Supplier<Potion> object = this.potionRegister(modId).register(name, supplier);
         this.potions.put(key, object);
         return object;
      }
   }

   public Supplier<MobEffect> registerMobEffect(String modId, String name, Supplier<MobEffect> supplier) {
      this.ensureOpen(modId, "mob effect", name);
      String key = this.key(modId, name);
      if (this.mobEffects.containsKey(key)) {
         throw this.duplicate("mob effect", key);
      } else {
         Supplier<MobEffect> object = this.mobEffectRegister(modId).register(name, supplier);
         this.mobEffects.put(key, object);
         return object;
      }
   }

   public Map<String, Supplier<EntityType<?>>> getEntities() {
      return this.entities;
   }

   public void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
      Iterator var2 = this.entities.entrySet().iterator();

      while(var2.hasNext()) {
         Map.Entry<String, Supplier<EntityType<?>>> entry = (Map.Entry)var2.next();
         EntityType<?> type = (EntityType)((Supplier)entry.getValue()).get();
         if (type != null) {
            Supplier<AttributeSupplier> attrSupplier = (Supplier)this.entityAttributes.get(entry.getKey());
            if (attrSupplier != null) {
               event.put((EntityType)type, (AttributeSupplier)attrSupplier.get());
            }
         }
      }

   }

   private DeferredRegister<Item> itemRegister(String modId) {
      return (DeferredRegister)this.itemRegisters.computeIfAbsent(modId, (id) -> {
         return this.attachIfNeeded(id, DeferredRegister.create(Registries.ITEM, id));
      });
   }

   private DeferredRegister<Block> blockRegister(String modId) {
      return (DeferredRegister)this.blockRegisters.computeIfAbsent(modId, (id) -> {
         return this.attachIfNeeded(id, DeferredRegister.create(Registries.BLOCK, id));
      });
   }

   private DeferredRegister<CreativeModeTab> tabRegister(String modId) {
      return (DeferredRegister)this.tabRegisters.computeIfAbsent(modId, (id) -> {
         return this.attachIfNeeded(id, DeferredRegister.create(CREATIVE_MODE_TAB_REGISTRY, id));
      });
   }

   private static ResourceKey<Registry<CreativeModeTab>> resolveCreativeModeTabRegistryKey() {
      int var2;
      int var3;
      Object value;
      ResourceKey key;
      try {
         Class<?> registriesClass = Class.forName("net.minecraft.core.registries.Registries");
         Field[] var1 = registriesClass.getFields();
         var2 = var1.length;

         for(var3 = 0; var3 < var2; ++var3) {
            Field field = var1[var3];
            if (Modifier.isStatic(field.getModifiers()) && ResourceKey.class.isAssignableFrom(field.getType())) {
               value = field.get((Object)null);
               if (value instanceof ResourceKey) {
                  key = (ResourceKey)value;
                  if (String.valueOf(key).contains("creative_mode_tab")) {
                     return key;
                  }
               }
            }
         }
      } catch (Exception var10) {
      }

      try {
         ResourceLocation id = MLuaResourceLocations.build("minecraft", "creative_mode_tab");
         String[] var12 = new String[]{"createRegistryKey", "createRegistryKey"};
         var2 = var12.length;

         for(var3 = 0; var3 < var2; ++var3) {
            String name = var12[var3];

            try {
               Method method = ResourceKey.class.getMethod(name, ResourceLocation.class);
               Object created = method.invoke((Object)null, id);
               if (created instanceof ResourceKey<?> createdKey) {
                  return (ResourceKey<Registry<CreativeModeTab>>)createdKey;
               }
            } catch (NoSuchMethodException var8) {
            }
         }

         Method[] var13 = ResourceKey.class.getMethods();
         var2 = var13.length;

         for(var3 = 0; var3 < var2; ++var3) {
            Method method = var13[var3];
            if (Modifier.isStatic(method.getModifiers()) && method.getParameterCount() == 1 && method.getParameterTypes()[0] == ResourceLocation.class && ResourceKey.class.isAssignableFrom(method.getReturnType())) {
               value = method.invoke((Object)null, id);
               if (value instanceof ResourceKey) {
                  key = (ResourceKey)value;
                  return key;
               }
            }
         }
      } catch (Exception var9) {
      }

      throw new IllegalStateException("[MLua] Failed to resolve creative mode tab registry key.");
   }

   private DeferredRegister<EntityType<?>> entityRegister(String modId) {
      return (DeferredRegister)this.entityRegisters.computeIfAbsent(modId, (id) -> {
         return this.attachIfNeeded(id, DeferredRegister.create(Registries.ENTITY_TYPE, id));
      });
   }

   private DeferredRegister<ParticleType<?>> particleRegister(String modId) {
      return (DeferredRegister)this.particleRegisters.computeIfAbsent(modId, (id) -> {
         return this.attachIfNeeded(id, DeferredRegister.create(Registries.PARTICLE_TYPE, id));
      });
   }

   private DeferredRegister<MobEffect> mobEffectRegister(String modId) {
      return (DeferredRegister)this.mobEffectRegisters.computeIfAbsent(modId, (id) -> {
         return this.attachIfNeeded(id, DeferredRegister.create(Registries.MOB_EFFECT, id));
      });
   }

   private DeferredRegister<Potion> potionRegister(String modId) {
      return (DeferredRegister)this.potionRegisters.computeIfAbsent(modId, (id) -> {
         return this.attachIfNeeded(id, DeferredRegister.create(Registries.POTION, id));
      });
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
      this.entityVisuals.put(this.key(modId, name), new MLuaEntityVisualDescriptor(texture, model, shadowRadius));
   }

   public MLuaEntityVisualDescriptor getEntityVisual(String key) {
      return (MLuaEntityVisualDescriptor)this.entityVisuals.get(key);
   }

   public boolean restoreEntityVisual(String modId, String name, EntityVisualInfo expectedCurrent,
                                      EntityVisualInfo previousVisual) {
      String key = this.key(modId, name);
      MLuaEntityVisualDescriptor current = this.entityVisuals.get(key);
      if (current == null || expectedCurrent == null
          || !java.util.Objects.equals(current.texture(), expectedCurrent.texture())
          || !java.util.Objects.equals(current.model(), expectedCurrent.model())
          || Float.compare(current.shadowRadius(), expectedCurrent.shadowRadius()) != 0) return false;
      if (previousVisual == null) return this.entityVisuals.remove(key, current);
      this.entityVisuals.put(key, new MLuaEntityVisualDescriptor(
         previousVisual.texture(), previousVisual.model(), previousVisual.shadowRadius()));
      return true;
   }

   private net.minecraft.resources.ResourceLocation contentId(String modId, String name) {
      String id = name.contains(":") ? name : modId + ":" + name;
      return MLuaResourceLocations.parse(id);
   }

   private void registerTag(Map<String, JsonObject> destination, String modId, String type, String name, boolean replace, JsonArray values) {
      this.ensureOpen(modId, type, name);
      String id = this.contentId(modId, name).toString();
      JsonObject tag = destination.computeIfAbsent(id, (key) -> {
         JsonObject created = new JsonObject();
         created.addProperty("replace", false);
         created.add("values", new JsonArray());
         return created;
      });
      if (replace) {
         JsonArray freshValues = new JsonArray();
         values.forEach(freshValues::add);
         tag.addProperty("replace", true);
         tag.add("values", freshValues);
      } else {
         JsonArray currentValues = tag.getAsJsonArray("values");
         values.forEach(currentValues::add);
      }
   }

   private void putUnique(Map<String, JsonObject> destination, String type, String id, JsonObject definition) {
      if (destination.putIfAbsent(id, definition) != null) {
         throw this.duplicate(type, id);
      }
   }

   private <T> DeferredRegister<T> attachIfNeeded(String modId, DeferredRegister<T> register) {
      IEventBus bus = (IEventBus)this.attachedBuses.get(modId);
      if (bus == null) {
         bus = (IEventBus)this.attachedBuses.get("mlua");
      }

      if (bus != null) {
         register.register(bus);
      }

      return register;
   }

   private String key(String modId, String name) {
      MLuaResourceNameValidator.validate(modId);
      MLuaResourceNameValidator.validate(name);
      return modId + ":" + name;
   }

   private <T> void setMetadata(Map<String, T> metadata, String key, T value) {
      if (value == null) {
         metadata.remove(key);
      } else {
         metadata.put(key, value);
      }
   }

   private <T> T getVisual(Map<String, T> visuals, String namespace, String path, T fallback) {
      String direct = namespace + ":" + path;
      T value = visuals.get(direct);
      if (value != null) {
         return value;
      } else {
         String name = path.contains("/") ? path.substring(path.lastIndexOf(47) + 1) : path;
         value = visuals.get(namespace + ":" + name);
         return value != null ? value : fallback;
      }
   }

   private IllegalArgumentException duplicate(String type, String key) {
      return new IllegalArgumentException("[MLua] Duplicate " + type + " registration: " + key);
   }
}
