package dev.mlua.runtime.registry;

import com.mojang.datafixers.types.Type;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.mlua.runtime.client.MLuaVisualBlockEntity;
import dev.mlua.minecraft.MLuaResourceLocations;
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
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class MLuaForgeRegistry {
   private static final MLuaForgeRegistry INSTANCE = new MLuaForgeRegistry();
   private static final ResourceKey<Registry<CreativeModeTab>> CREATIVE_MODE_TAB_REGISTRY = resolveCreativeModeTabRegistryKey();
   private final Map<String, DeferredRegister<Item>> itemRegisters = new HashMap<>();
   private final Map<String, DeferredRegister<Block>> blockRegisters = new HashMap<>();
   private final Map<String, DeferredRegister<CreativeModeTab>> tabRegisters = new HashMap<>();
   private final Map<String, DeferredRegister<EntityType<?>>> entityRegisters = new HashMap<>();
   private final Map<String, DeferredRegister<ParticleType<?>>> particleRegisters = new HashMap<>();
   private final Map<String, DeferredRegister<MobEffect>> mobEffectRegisters = new HashMap<>();
   private final Map<String, DeferredRegister<Potion>> potionRegisters = new HashMap<>();
   private final Map<String, DeferredRegister<BlockEntityType<?>>> blockEntityRegisters = new HashMap<>();
   private final Map<String, RegistryObject<Item>> items = new HashMap<>();
   private final Map<String, RegistryObject<Block>> blocks = new HashMap<>();
   private final Map<String, RegistryObject<CreativeModeTab>> tabs = new HashMap<>();
   private final Map<String, RegistryObject<EntityType<?>>> entities = new HashMap<>();
   private final Map<String, RegistryObject<ParticleType<?>>> particles = new HashMap<>();
   private final Map<String, RegistryObject<MobEffect>> mobEffects = new HashMap<>();
   private final Map<String, RegistryObject<Potion>> potions = new HashMap<>();
   private final Map<String, AtomicReference<RegistryObject<BlockEntityType<?>>>> visualBlockEntityTypes = new HashMap<>();
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

   public static MLuaForgeRegistry get() {
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

   public RegistryObject<Item> registerItem(String modId, String name, Supplier<Item> supplier) {
      this.ensureOpen(modId, "item", name);
      String key = this.key(modId, name);
      if (this.items.containsKey(key)) {
         throw this.duplicate("item", key);
      } else {
         RegistryObject<Item> object = this.itemRegister(modId).register(name, supplier);
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
         RegistryObject<Block> object = this.blockRegister(modId).register(name, supplier);
         this.blocks.put(key, object);
         return object;
      }
   }

   public void setItemVisuals(String modId, String name, MLuaTextureDescriptor texture, String model) {
      String key = this.key(modId, name);
      if (texture != null && !texture.isEmpty()) {
         this.itemTextures.put(key, texture);
      }

      if (model != null && !model.isBlank()) {
         this.itemModels.put(key, model);
      }

   }

   public void setItemCopy(String modId, String name, String copy) {
      String key = this.key(modId, name);
      if (copy != null && !copy.isBlank()) {
         this.itemCopies.put(key, copy);
      }

   }

   public void setItemCopyModel(String modId, String name, boolean copyModel) {
      String key = this.key(modId, name);
      if (copyModel) {
         this.itemCopyModels.put(key, true);
      }

   }

   public void setBlockVisuals(String modId, String name, MLuaTextureDescriptor texture, String model) {
      String key = this.key(modId, name);
      if (texture != null && !texture.isEmpty()) {
         this.blockTextures.put(key, texture);
      }

      if (model != null && !model.isBlank()) {
         this.blockModels.put(key, model);
      }

   }

   public void setBlockCopy(String modId, String name, String copy) {
      String key = this.key(modId, name);
      if (copy != null && !copy.isBlank()) {
         this.blockCopies.put(key, copy);
      }

   }

   public void setBlockCopyModel(String modId, String name, boolean copyModel) {
      String key = this.key(modId, name);
      if (copyModel) {
         this.blockCopyModels.put(key, true);
      }

   }

    public void setItemDescription(String modId, String name, List<String> description) {
        String key = this.key(modId, name);
        if (description != null && !description.isEmpty()) {
            this.itemDescriptions.put(key, description);
        }
    }

    public List<String> getItemDescription(String modId, String name) {
        return this.itemDescriptions.get(this.key(modId, name));
    }

    public void setBlockDescription(String modId, String name, List<String> description) {
        String key = this.key(modId, name);
        if (description != null && !description.isEmpty()) {
            this.blockDescriptions.put(key, description);
        }
    }

    public List<String> getBlockDescription(String modId, String name) {
        return this.blockDescriptions.get(this.key(modId, name));
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
         if (!this.blockEntityRegisters.containsKey(modId)) {
            DeferredRegister<BlockEntityType<?>> reg = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, modId);
            this.blockEntityRegisters.put(modId, reg);
            AtomicReference<RegistryObject<BlockEntityType<?>>> ref = (AtomicReference)this.visualBlockEntityTypes.computeIfAbsent(modId, (id) -> {
               return new AtomicReference();
            });
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

   public Map<String, RegistryObject<Item>> getRegisteredItems() {
      return this.items;
   }

   public Map<String, RegistryObject<Block>> getRegisteredBlocks() {
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
         RegistryObject<CreativeModeTab> object = this.tabRegister(modId).register(name, supplier);
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
         RegistryObject<EntityType<?>> object = this.entityRegister(modId).register(name, supplier);
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
      this.ensureOpen(modId, "particle", name);
      String key = this.key(modId, name);
      if (this.particles.containsKey(key)) {
         throw this.duplicate("particle", key);
      } else {
         RegistryObject<ParticleType<?>> object = this.particleRegister(modId).register(name, () -> {
            return new SimpleParticleType(overrideLimiter);
         });
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
         RegistryObject<Potion> object = this.potionRegister(modId).register(name, supplier);
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
         RegistryObject<MobEffect> object = this.mobEffectRegister(modId).register(name, supplier);
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

   private DeferredRegister<Item> itemRegister(String modId) {
      return (DeferredRegister)this.itemRegisters.computeIfAbsent(modId, (id) -> {
         return this.attachIfNeeded(id, DeferredRegister.create(ForgeRegistries.ITEMS, id));
      });
   }

   private DeferredRegister<Block> blockRegister(String modId) {
      return (DeferredRegister)this.blockRegisters.computeIfAbsent(modId, (id) -> {
         return this.attachIfNeeded(id, DeferredRegister.create(ForgeRegistries.BLOCKS, id));
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
         return this.attachIfNeeded(id, DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, id));
      });
   }

   private DeferredRegister<ParticleType<?>> particleRegister(String modId) {
      return (DeferredRegister)this.particleRegisters.computeIfAbsent(modId, (id) -> {
         return this.attachIfNeeded(id, DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, id));
      });
   }

   private DeferredRegister<MobEffect> mobEffectRegister(String modId) {
      return (DeferredRegister)this.mobEffectRegisters.computeIfAbsent(modId, (id) -> {
         return this.attachIfNeeded(id, DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, id));
      });
   }

   private DeferredRegister<Potion> potionRegister(String modId) {
      return (DeferredRegister)this.potionRegisters.computeIfAbsent(modId, (id) -> {
         return this.attachIfNeeded(id, DeferredRegister.create(ForgeRegistries.POTIONS, id));
      });
   }

   public boolean isRegisteredItem(String namespace, String path) {
      String key = namespace + ":" + path;
      if (this.items.containsKey(key)) {
         return true;
      } else {
         String name = path.contains("/") ? path.substring(path.lastIndexOf(47) + 1) : path;
         return this.items.containsKey(namespace + ":" + name);
      }
   }

   public boolean isRegisteredBlock(String namespace, String path) {
      String key = namespace + ":" + path;
      if (this.blocks.containsKey(key)) {
         return true;
      } else {
         String name = path.contains("/") ? path.substring(path.lastIndexOf(47) + 1) : path;
         return this.blocks.containsKey(namespace + ":" + name);
      }
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
