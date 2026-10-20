package dev.mlua.minecraft.binding;

import dev.mlua.module.MLuaRegistration;
import java.util.Set;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Typed, chainable property instances used by Registry item, block, and entity registration. */
public final class MLuaRegistryProperties {
   private static final String STATE = "__mlua_registry_properties";
   private static final Set<String> WHOLE_NUMBER_PROPERTIES = Set.of(
      "maxStackSize", "durability", "nutrition", "lightLevel", "trackingRange", "updateInterval", "aiInterval");

   private MLuaRegistryProperties() { }

   public static void registerClasses(MLuaRegistration registration) {
      registration.registerClass("RegistryProperties", registration.requireInstanceBaseClassName(),
         "A chainable set of native Minecraft registration properties.");
      registration.registerClass("ItemProperties", "RegistryProperties",
         "Item settings corresponding to Minecraft Item.Properties.");
      registration.registerClass("BlockProperties", "RegistryProperties",
         "Block settings corresponding to Minecraft BlockBehaviour.Properties.");
      registration.registerClass("EntityProperties", "RegistryProperties",
         "Entity type settings and scripted behavior callbacks for a registered mob.");
      common(registration, "Class", "className", "Selects a registered Lua class derived from the content's Minecraft class.");
      method(registration, "RegistryProperties", "Build", (properties, args) -> copy(properties.values),
         "Returns a plain property table that can be passed to a Registry:Register method.");

      description(registration, "ItemProperties");
      description(registration, "BlockProperties");
      content(registration, "Copy", "copy", "Copies native defaults from an existing Minecraft item or block id.");
      content(registration, "CopyModel", "copyModel", "Copies the model from the selected native item or block.");
      item(registration, "StacksTo", "maxStackSize", "Sets the maximum stack size.");
      item(registration, "Durability", "durability", "Sets item durability and forces a maximum stack size of one.");
      item(registration, "Rarity", "rarity", "Sets the Minecraft item rarity.");
      item(registration, "FireResistant", "fireResistant", "Makes the item immune to fire and lava damage.");
      item(registration, "CraftRemainder", "craftRemainder", "Sets the item returned after crafting.");
      item(registration, "Kind", "kind", "Selects the registered item implementation type.");
      item(registration, "Tier", "tier", "Sets the tool tier for a sword, pickaxe, axe, shovel, or hoe.");
      texture(registration, "ItemProperties");
      method(registration, "ItemProperties", "Model", (properties, args) -> model(properties, "model", args.arg(2)),
         "Sets the generated item model resource; texture-only items can omit this and use the default generated model.");
      item(registration, "AttackDamage", "attackDamage", "Sets the tool's attack damage where supported.");
      item(registration, "AttackSpeed", "attackSpeed", "Sets the tool's attack speed where supported.");
      method(registration, "ItemProperties", "Food", (properties, args) -> {
         LuaValue food = args.arg(2);
         if (!food.istable()) throw new LuaError("[MLua] ItemProperties:Food expects a food properties table.");
         properties.values.set("food", copy(food.checktable()));
         return properties.instance;
      }, "Sets food properties using nutrition, saturation, and optional meat, fast, and alwaysEat values.");
      item(registration, "Nutrition", "nutrition", "Sets food nutrition and enables food behavior.");
      item(registration, "Saturation", "saturation", "Sets food saturation and enables food behavior.");
      item(registration, "Meat", "meat", "Marks the food as meat.");
      item(registration, "AlwaysEdible", "alwaysEat", "Allows the food to be eaten when the hunger bar is full.");
      item(registration, "Fast", "fast", "Reduces the food eating duration.");
      callback(registration, "ItemProperties", "OnUse", "onUse", "Registers the item use callback.");
      callback(registration, "ItemProperties", "OnUseOnBlock", "onUseOnBlock", "Registers the item use-on-block callback.");
      callback(registration, "ItemProperties", "OnInventoryTick", "onInventoryTick", "Registers the item inventory tick callback.");
      callback(registration, "ItemProperties", "OnCrafted", "onCrafted", "Registers the item crafted callback.");
      callback(registration, "ItemProperties", "OnConsume", "onConsume", "Registers the item consume callback.");
      callback(registration, "ItemProperties", "OnAttackEntity", "onAttackEntity", "Registers the item attack-entity callback.");

      method(registration, "BlockProperties", "Strength", (properties, args) -> {
         LuaValue hardness = args.arg(2);
         requireNumber(properties, "hardness", hardness);
         properties.values.set("strength", hardness);
         LuaValue resistance = args.arg(3);
         if (!resistance.isnil()) {
            requireNumber(properties, "resistance", resistance);
            properties.values.set("resistance", resistance);
         }
         return properties.instance;
      }, "Sets block hardness and optionally its explosion resistance.");
      method(registration, "BlockProperties", "Container", (properties, args) -> {
         LuaValue definition = args.arg(2);
         if (definition.istable() && registration.isInstanceOf(definition, "ContainerType")) {
            LuaValue getId = definition.get("GetId");
            if (!getId.isfunction()) {
               throw new LuaError("[MLua] BlockProperties:Container received an invalid ContainerType instance.");
            }
            LuaValue id = getId.invoke(LuaValue.varargsOf(new LuaValue[]{definition})).arg1();
            if (!id.isstring() || id.tojstring().isBlank()) {
               throw new LuaError("[MLua] BlockProperties:Container could not read the ContainerType id.");
            }
            return set(properties, "container", id);
         }
         if (definition.isstring()) {
            if (definition.tojstring().isBlank()) {
               throw new LuaError("[MLua] BlockProperties:Container expects a ContainerType, registered id, or definition table.");
            }
            return set(properties, "container", definition);
         }
         if (!definition.istable()) {
            throw new LuaError("[MLua] BlockProperties:Container expects a ContainerType, registered id, or definition table.");
         }
         return set(properties, "container", copy(definition.checktable()));
      }, "Attaches a registered ContainerType by instance or id, or snapshots an inline container definition to this block.");
      method(registration, "BlockProperties", "BlockEntity", (properties, args) -> {
         LuaValue definition = args.arg(2);
         if (!definition.istable()) {
            throw new LuaError("[MLua] BlockProperties:BlockEntity expects a definition table.");
         }
         return set(properties, "blockEntity", copy(definition.checktable()));
      }, "Snapshots a block entity class and lifecycle callback definition for this block.");
      texture(registration, "BlockProperties");
      method(registration, "BlockProperties", "Model", (properties, args) -> model(properties, "model", args.arg(2)),
         "Sets the block model resource used by the generated blockstate.");
      block(registration, "Resistance", "resistance", "Sets explosion resistance; Minecraft also requires a hardness value.");
      block(registration, "RequiresCorrectToolForDrops", "requiresCorrectTool", "Requires the correct tool to drop this block's item.");
      block(registration, "NoCollision", "noCollision", "Removes the block's collision shape.");
      block(registration, "NoOcclusion", "noOcclusion", "Prevents the block from occluding neighboring faces.");
      block(registration, "RandomTicks", "randomTicks", "Enables random block ticks for scripted random-tick callbacks.");
      block(registration, "IgnitedByLava", "ignitedByLava", "Allows lava to ignite this block.");
      block(registration, "Replaceable", "replaceable", "Allows this block to be replaced by placement and fluid rules.");
      block(registration, "LightLevel", "lightLevel", "Sets emitted light from zero through fifteen.");
      block(registration, "Friction", "friction", "Sets the movement friction multiplier on this block.");
      block(registration, "SpeedFactor", "speedFactor", "Sets the movement speed multiplier on this block.");
      block(registration, "JumpFactor", "jumpFactor", "Sets the jump multiplier on this block.");
      block(registration, "Sound", "sound", "Sets the Minecraft block sound type.");
      block(registration, "MapColor", "mapColor", "Sets the map color used for this block.");
      block(registration, "PushReaction", "pushReaction", "Sets the block reaction to pistons.");
      block(registration, "Instrument", "instrument", "Sets the note-block instrument for this block.");
      callback(registration, "BlockProperties", "OnRightClick", "onRightClick", "Registers the block interaction callback.");
      callback(registration, "BlockProperties", "OnPlace", "onPlace", "Registers the block placement callback.");
      callback(registration, "BlockProperties", "OnBreak", "onBreak", "Registers the block break callback.");
      callback(registration, "BlockProperties", "OnStepOn", "onStepOn", "Registers the entity step-on callback.");
      callback(registration, "BlockProperties", "OnEntityInside", "onEntityInside", "Registers the entity-inside callback.");
      callback(registration, "BlockProperties", "OnNeighborChanged", "onNeighborChanged", "Registers the neighbor-change callback.");
      callback(registration, "BlockProperties", "OnRemove", "onRemove", "Registers the block removal callback.");
      callback(registration, "BlockProperties", "OnRandomTick", "onRandomTick", "Registers the random block tick callback.");
      callback(registration, "BlockProperties", "OnTick", "onTick", "Registers the scheduled block tick callback.");
      callback(registration, "BlockProperties", "OnFallOn", "onFallOn", "Registers the entity fall-on callback.");
      callback(registration, "BlockProperties", "OnProjectileHit", "onProjectileHit", "Registers the projectile hit callback.");

      entity(registration, "Category", "category", "Sets the Minecraft mob category.");
      entity(registration, "TrackingRange", "trackingRange", "Sets the entity tracking range in chunks.");
      entity(registration, "UpdateInterval", "updateInterval", "Sets the entity network update interval in ticks.");
      entity(registration, "AIInterval", "aiInterval", "Sets how often scripted AI decisions are updated in ticks.");
      entity(registration, "ShadowRadius", "shadowRadius", "Sets the entity shadow radius.");
      texture(registration, "EntityProperties");
      method(registration, "EntityProperties", "Model", (properties, args) -> model(properties, "model", args.arg(2)),
         "Sets the entity model resource.");
      method(registration, "EntityProperties", "Size", (properties, args) -> {
         LuaValue width = args.arg(2);
         LuaValue height = args.arg(3);
         requireNumber(properties, "width", width);
         requireNumber(properties, "height", height);
         properties.values.set("width", width);
         properties.values.set("height", height);
         return properties.instance;
      }, "Sets the entity's bounding box width and height in blocks.");
      method(registration, "EntityProperties", "Attributes", (properties, args) -> {
         LuaValue attributes = args.arg(2);
         if (!attributes.istable()) throw new LuaError("[MLua] EntityProperties:Attributes expects an attribute table.");
         properties.values.set("attributes", copy(attributes.checktable()));
         return properties.instance;
      }, "Sets the entity's base Minecraft attributes.");
      String[] attributeNames = {"MaxHealth", "MovementSpeed", "AttackDamage", "Armor", "ArmorToughness",
         "AttackSpeed", "KnockbackResistance", "FollowRange"};
      String[] attributeKeys = {"maxHealth", "movementSpeed", "attackDamage", "armor", "armorToughness",
         "attackSpeed", "knockbackResistance", "followRange"};
      for (int index = 0; index < attributeNames.length; index++) {
         attribute(registration, attributeNames[index], attributeKeys[index]);
      }
      callback(registration, "EntityProperties", "AI", "AI", "Registers scripted AI behavior for this entity.", true);
      callback(registration, "EntityProperties", "OnTick", "onTick", "Registers the entity tick callback.");
      callback(registration, "EntityProperties", "OnHurt", "onHurt", "Registers the entity hurt callback.");
      callback(registration, "EntityProperties", "OnDeath", "onDeath", "Registers the entity death callback.");
      callback(registration, "EntityProperties", "OnSpawn", "onSpawn", "Registers the entity spawn callback.");
      callback(registration, "EntityProperties", "OnInteract", "onInteract", "Registers the entity interaction callback.");
      callback(registration, "EntityProperties", "OnTargetChanged", "onTargetChanged", "Registers the entity target-change callback.");
      callback(registration, "EntityProperties", "OnRemove", "onRemove", "Registers the entity removal callback.");
   }

   public static LuaTable create(MLuaRegistration registration, String className) {
      if (!"ItemProperties".equals(className) && !"BlockProperties".equals(className)
         && !"EntityProperties".equals(className)) {
         throw new LuaError("[MLua] Unsupported registry property class '" + className + "'.");
      }
      LuaTable instance = registration.createClassInstance(className);
      instance.rawset(STATE, LuaValue.userdataOf(new State(instance, new LuaTable(), className)));
      return instance;
   }

   /** Returns a copy of a builder's data, or the original plain Lua table. */
   public static LuaTable table(MLuaRegistration registration, LuaValue value, String expectedClass, String method) {
      if (value.isnil()) return new LuaTable();
      if (!value.istable()) throw new LuaError("[MLua] " + method + " expects a property table or " + expectedClass + " instance.");
      // The private state marker preserves the builder's exact domain even
      // though Lua class metadata may report a shared inherited base class.
      if (!value.checktable().rawget(STATE).isnil()) {
         State state = state(value);
         if (!expectedClass.equals(state.className)) {
            throw new LuaError("[MLua] " + method + " received " + state.className + "; expected " + expectedClass + ".");
         }
         return dev.mlua.runtime.registry.MLuaDynamicRegistryCatalog.copyTable(state.values);
      }
      String actualClass = registration.classNameOf(value);
      if (actualClass != null) {
         throw new LuaError("[MLua] " + method + " expects a plain property table or " + expectedClass
            + " instance; received " + actualClass + ".");
      }
      return value.checktable();
   }

   /** Allows reusable registered content types to snapshot a matching fluent properties instance. */
   public static LuaTable optionalTable(MLuaRegistration registration, LuaValue value, String domain, String method) {
      if (value.isnil()) return null;
      String expectedClass = switch (domain.toLowerCase(java.util.Locale.ROOT)) {
         case "item" -> "ItemProperties";
         case "block" -> "BlockProperties";
         case "entity" -> "EntityProperties";
         default -> null;
      };
      if (!value.istable()) throw new LuaError("[MLua] " + method + " expects a property table.");
      if (!value.checktable().rawget(STATE).isnil() || registration.isInstanceOf(value, "RegistryProperties")) {
         if (expectedClass == null) {
            throw new LuaError("[MLua] " + method + " does not accept a " + registration.classNameOf(value) + " instance for domain '" + domain + "'.");
         }
         return table(registration, value, expectedClass, method);
      }
      String actualClass = registration.classNameOf(value);
      if (actualClass != null) {
         throw new LuaError("[MLua] " + method + " expects a plain property table or matching properties instance; received " + actualClass + ".");
      }
      return value.checktable();
   }

   private static void common(MLuaRegistration registration, String name, String key, String description) {
      method(registration, "RegistryProperties", name, (properties, args) -> set(properties, key, args.arg(2)), description);
   }

   private static void content(MLuaRegistration registration, String name, String key, String description) {
      method(registration, "ItemProperties", name, (properties, args) -> set(properties, key, args.arg(2)), description);
      method(registration, "BlockProperties", name, (properties, args) -> set(properties, key, args.arg(2)), description);
   }

   private static void item(MLuaRegistration registration, String name, String key, String description) {
      method(registration, "ItemProperties", name, (properties, args) -> set(properties, key, args.arg(2)), description);
   }

   private static void description(MLuaRegistration registration, String className) {
      method(registration, className, "Description", (properties, args) -> {
         LuaValue value = args.arg(2);
         if (value.isstring()) {
            properties.values.set("description", value);
            return properties.instance;
         }
         if (!value.istable()) {
            throw new LuaError("[MLua] " + className + ":Description expects a string or a dense array of strings.");
         }
         LuaTable lines = value.checktable();
         int length = lines.rawlen();
         int entries = 0;
         LuaValue key = LuaValue.NIL;
         while (true) {
            Varargs entry = lines.next(key);
            key = entry.arg1();
            if (key.isnil()) break;
            if (!key.isint() || key.toint() < 1 || key.toint() > length) {
               throw new LuaError("[MLua] " + className + ":Description expects a dense array of strings with no extra keys.");
            }
            entries++;
         }
         if (entries != length) {
            throw new LuaError("[MLua] " + className + ":Description expects a dense array with no missing entries.");
         }
         for (int index = 1; index <= length; index++) {
            if (!lines.rawget(index).isstring()) {
               throw new LuaError("[MLua] " + className + ":Description array entries must be strings.");
            }
         }
         properties.values.set("description", copy(lines));
         return properties.instance;
      }, "Sets one tooltip line or a dense array of tooltip lines; arrays are snapshotted when called.");
   }

   private static void block(MLuaRegistration registration, String name, String key, String description) {
      method(registration, "BlockProperties", name, (properties, args) -> set(properties, key, args.arg(2)), description);
   }

   private static void entity(MLuaRegistration registration, String name, String key, String description) {
      method(registration, "EntityProperties", name, (properties, args) -> set(properties, key, args.arg(2)), description);
   }

   private static void texture(MLuaRegistration registration, String className) {
      method(registration, className, "Texture", (properties, args) -> {
         LuaValue value = args.arg(2);
         if (!value.isstring() && !value.istable()) {
            throw new LuaError("[MLua] " + className + ":Texture expects a texture id or a face-to-texture table.");
         }
         if (value.isstring() && value.tojstring().isBlank()) {
            throw new LuaError("[MLua] " + className + ":Texture expects a non-empty texture resource id.");
         }
         if (value.istable()) {
            LuaTable faces = value.checktable();
            LuaValue key = LuaValue.NIL;
            while (true) {
               Varargs entry = faces.next(key);
               key = entry.arg1();
               if (key.isnil()) break;
               if (entry.arg(2).isstring() && entry.arg(2).tojstring().isBlank()) {
                  throw new LuaError("[MLua] " + className + ":Texture face values must be non-empty resource ids.");
               }
            }
         }
         try {
            dev.mlua.runtime.registry.MLuaTextureDescriptor.fromLua(value);
         } catch (IllegalArgumentException invalid) {
            throw new LuaError("[MLua] " + className
               + ":Texture expects a string or a table mapping face names to texture resource ids.");
         }
         if (value.istable()) value = copy(value.checktable());
         properties.values.set("texture", value);
         return properties.instance;
      }, "Sets one texture id or a face-to-texture table, validates its value types, and snapshots tables when called.");
   }

   private static LuaValue model(State properties, String key, LuaValue value) {
      if (!value.isstring() || value.tojstring().isBlank()) {
         throw new LuaError("[MLua] " + properties.className + ":Model expects a non-empty model resource id.");
      }
      return set(properties, key, value);
   }

   private static void callback(MLuaRegistration registration, String className, String methodName,
                                String key, String description) {
      callback(registration, className, methodName, key, description, false);
   }

   private static void callback(MLuaRegistration registration, String className, String methodName,
                                String key, String description, boolean tableAllowed) {
      method(registration, className, methodName, (properties, args) -> {
         LuaValue value = args.arg(2);
         if (!value.isfunction() && !(tableAllowed && value.istable())) {
            throw new LuaError("[MLua] " + className + ":" + methodName + " expects "
               + (tableAllowed ? "a function or state table." : "a function."));
         }
         return set(properties, key, value);
      }, description);
   }

   private static void attribute(MLuaRegistration registration, String name, String key) {
      method(registration, "EntityProperties", name, (properties, args) -> {
         LuaValue value = args.arg(2);
         requireNumber(properties, key, value);
         LuaValue attributes = properties.values.get("attributes");
         LuaTable target = attributes.istable() ? attributes.checktable() : new LuaTable();
         target.set(key, value);
         properties.values.set("attributes", target);
         return properties.instance;
      }, "Sets the entity's " + key + " attribute.");
   }

   private static LuaValue set(State state, String key, LuaValue value) {
      if (value.isnil()) throw new LuaError("[MLua] Property method cannot receive nil.");
      validateValue(state, key, value);
      state.values.set(key, value);
      return state.instance;
   }

   private static void validateValue(State state, String key, LuaValue value) {
      int type = value.type();
      String expected = switch (key) {
         case "className", "description", "copy", "rarity", "craftRemainder", "kind", "tier",
            "sound", "mapColor", "pushReaction", "instrument", "category", "texture", "model" -> "string";
         case "copyModel", "fireResistant", "meat", "alwaysEat", "fast", "requiresCorrectTool",
            "noCollision", "noOcclusion", "randomTicks", "ignitedByLava", "replaceable" -> "boolean";
         case "maxStackSize", "durability", "attackDamage", "attackSpeed", "nutrition", "saturation",
            "resistance", "lightLevel", "friction", "speedFactor", "jumpFactor", "width", "height",
            "trackingRange", "updateInterval", "aiInterval", "shadowRadius" -> "number";
         case "food", "attributes" -> "table";
         default -> null;
      };
      if (expected == null) return;
      boolean valid = switch (expected) {
         case "string" -> type == LuaValue.TSTRING;
         case "boolean" -> type == LuaValue.TBOOLEAN;
         case "number" -> type == LuaValue.TNUMBER;
         case "table" -> value.istable();
         default -> false;
      };
      if (!valid) {
         throw new LuaError("[MLua] " + state.className + " property '" + key + "' expects a " + expected + ".");
      }
      if ("number".equals(expected)) {
         requireNumber(state, key, value);
         double number = value.todouble();
         if (WHOLE_NUMBER_PROPERTIES.contains(key) && number != Math.rint(number)) {
            throw new LuaError("[MLua] " + state.className + " property '" + key + "' must be a whole number.");
         }
      }
   }

   private static void requireNumber(State state, String key, LuaValue value) {
      if (value.type() != LuaValue.TNUMBER) {
         throw new LuaError("[MLua] " + state.className + " property '" + key + "' expects a number.");
      }
      if (!Double.isFinite(value.todouble())) {
         throw new LuaError("[MLua] " + state.className + " property '" + key + "' must be finite.");
      }
   }

   private static void method(MLuaRegistration registration, String className, String name,
                              BuilderMethod implementation, String description) {
      registration.registerClassFunction(className, name, new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            State properties = state(args.arg1());
            LuaValue result = implementation.apply(properties, args);
            if (result == null) {
               throw new LuaError("[MLua] " + properties.className + ":" + name
                  + " returned Java null; registry property methods must return a Lua value.");
            }
            return result;
         }
      }, description);
   }

   private static State state(LuaValue value) {
      if (!value.istable()) throw new LuaError("[MLua] Expected a RegistryProperties instance.");
      LuaValue holder = value.checktable().rawget(STATE);
      Object raw = holder.isuserdata() ? holder.touserdata() : null;
      if (!(raw instanceof State state)) throw new LuaError("[MLua] Invalid RegistryProperties instance.");
      return state;
   }

   private static LuaTable copy(LuaTable source) {
      return dev.mlua.runtime.registry.MLuaDynamicRegistryCatalog.copyTable(source);
   }

   @FunctionalInterface private interface BuilderMethod { LuaValue apply(State properties, Varargs args); }
   private record State(LuaTable instance, LuaTable values, String className) { }
}
