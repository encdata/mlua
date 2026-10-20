package dev.mlua.modules.itemstack;

import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.minecraft.binding.MLuaItemWrapper;
import dev.mlua.modules.itemstack.service.MLuaItemStackService;
import java.util.List;

public final class ItemStackModule implements MLuaModule {
    @Override
    public List<String> dependencies() {
        return List.of("registry");
    }

    @Override
    public void registerClasses(MLuaRegistration registration) {
        MLuaItemWrapper.classTable(registration);
    }

    @Override
    public String id() {
        return "itemstack";
    }

    @Override
    public List<ServiceEntry> services() {
        ServiceEntry.Builder itemStack = ServiceEntry.builder("ItemStackService", MLuaItemStackService::new)
            .reflective(MLuaItemStackService.class);
        return List.of(itemStack.build());
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.register(MLuaDocBuilder.Registration.service("ItemStackService", "MLuaItemStackService",
            "Create, clone, inspect, and mutate Minecraft ItemStack values.",
            "services/item-stack.html"));
        docs.register(MLuaDocBuilder.Registration.clazz("Item", "MLuaItemWrapper", "A live item stack instance with count, durability, display name, tags, and enchantments.", "classes/item.html", "Instance"));
        docs.register(MLuaDocBuilder.Registration.clazz("BlockItem", "MLuaItemWrapper", "An item instance that places its associated block and inherits the item stack API.", "classes/block-item.html", "Item"));
        docs.register(MLuaDocBuilder.Registration.clazz("Weapon", "MLuaItemWrapper", "A scriptable weapon base for melee, ranged, thrown, and other item actions.", "classes/weapon.html", "Item"));
        docs.register(MLuaDocBuilder.Registration.signature("Item", "Item.Find(id: string, count?: whole number >= 0)", "Item"));
        docs.register(MLuaDocBuilder.Registration.signature("Item", "Item.Class.new(id: string, count?: whole number >= 0)", "Item"));
        docs.register(MLuaDocBuilder.Registration.signature("Item", "Item:GetDisplayName()", "string"));
        docs.register(MLuaDocBuilder.Registration.signature("Item", "Item:GetDisplayComponent()", "Component"));
        docs.register(MLuaDocBuilder.Registration.signature("Item", "Item:SetDisplayName(name: string|Component)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Item", "Item:SetLore(lore: string|string[])", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Item", "Item:SetCount(count: whole number >= 0)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Item", "Item:SetDamage(damage: whole number >= 0)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Item", "Item:GetTag(key: string)", "any"));
        docs.register(MLuaDocBuilder.Registration.signature("Item", "Item:GetTagKeys()", "string[]"));
        docs.register(MLuaDocBuilder.Registration.signature("Item", "Item:SetTag(key: string, value: any)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Item", "Item:HasTagKey(key: string)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Item", "Item:GetNbt()", "Nbt"));
        docs.register(MLuaDocBuilder.Registration.signature("Item", "Item:SetNbt(nbt: Nbt|table)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Item", "Item:SetNbt(key: string, value: any)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Item", "Item:GetRemainingDurability()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("Item", "Item:GetDurabilityRemaining()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("Item", "Item:GetDamagePercent()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("ItemStackService", "ItemStackService:GetDamage(item: Item)", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("ItemStackService", "ItemStackService:GetMaxDamage(item: Item)", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("ItemStackService", "ItemStackService:GetRemainingDurability(item: Item)", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("ItemStackService", "ItemStackService:GetDamagePercent(item: Item)", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("ItemStackService", "ItemStackService:SetDamage(item: Item, damage: whole number >= 0)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Item", "Item:IsEnchantable()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Item", "Item:IsStackable()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Item", "Item:CanStackWith(other: Item)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Item", "Item:Grow(amount: whole number >= 0)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Item", "Item:Shrink(amount: whole number >= 0)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Item", "Item:Split(amount: whole number >= 0)", "Item"));
        docs.register(MLuaDocBuilder.Registration.signature("Item", "Item:Clone()", "Item"));
        docs.register(MLuaDocBuilder.Registration.signature("Item", "Item:Copy()", "Item"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Item", "Read and change a live item stack",
            "Item values are live typed stack instances returned by player inventory and held-item methods. Use Item.Find or Item.Class.new, or call Item(id, count), to create one directly, then pass that instance to inventory and container methods.",
            "local item = Item.Find(\"minecraft:torch\", 4)\nprint(item:GetId(), item:GetCount(), item:IsStackable())\nif item:IsDamageable() then\n    print(item.RemainingDurability, item.DamagePercent)\nend\nlocal half = item:Split(2)\nitem:Grow(1)\nprint(item.Count, half.Count)"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Weapon", "Build a weapon from item actions",
            "Weapon is a reusable Item subclass. Combine onUse and onAttackEntity callbacks with registered projectile types to define thrown, ranged, melee, or hybrid behavior without a weapon-specific item kind. Attack callbacks expose Attacker and Target as live entity instances; Player is also available when the attacker is a player. This callback runs after the target is attacked, so it cannot cancel the hit. For onUse, return true when the callback handled the interaction; false lets Minecraft continue its normal item-use behavior. The onAttackEntity return value controls native tool hit handling.",
            "Minecraft:RegisterClass(\"TunedWeapon\", \"Weapon\", \"A reusable weapon behavior base.\")\nMinecraft:RegisterClass(\"ThrownWeaponProjectile\", \"Projectile\", \"A projectile fired by a weapon action.\")\n\nlocal thrown = Registry.Projectiles:Register(\"thrown_weapon\", {\n    className = \"ThrownWeaponProjectile\",\n    Speed = 48,\n    Damage = 7,\n    Gravity = true\n})\n\nRegistry.Items:Register(\"field_weapon\", {\n    className = \"TunedWeapon\",\n    durability = 320,\n    onUse = function(context)\n        if not context.Player then return false end\n        thrown:Fire({ Owner = context.Player })\n        context.Item:SetDamage(context.Item:GetDamage() + 1)\n        return true\n    end,\n    onAttackEntity = function(context)\n        context.Attacker:SetNbt(\"last_weapon_target\", context.Target:GetUUID())\n        return true\n    end\n})"));
        docs.register(MLuaDocBuilder.Registration.contribution(MLuaDocBuilder.Contribution.builder(id(), "itemstack:weapon-action-walkthrough")
            .section("Weapons")
            .title("Build a weapon from a projectile and item actions")
            .description("Keep content registration in a .registry.lua file. Register a projectile once, then let an item action fire it. The item remains a Minecraft item; Lua defines what its actions do.")
            .order(20)
            .step("Registry script", "Register a reusable weapon class",
                "Register classes before the item that uses them. Put the shared action on the class so every item instance uses the same behavior.",
                "Minecraft:RegisterClass(\"SignalWeapon\", \"Weapon\", \"A weapon that launches a signal bolt.\")\nMinecraft:RegisterClassFunction(\"SignalWeapon\", \"Launch\", function(self, projectile, player)\n    if not player then return false end\n    projectile:Fire({ Owner = player })\n    return true\nend, \"Launches this weapon's projectile for a player.\")")
            .step("Registry script", "Define the projectile",
                "Projectile registration returns a reusable projectile type handle. Set its movement and damage once; the item action can fire that same definition whenever the player uses the item.",
                "local Registry = Minecraft:GetService(\"Registry\")\n\nlocal signalBolt = Registry.Projectiles:Register(\"signal_bolt\", {\n    Speed = 42,\n    Damage = 5,\n    Gravity = false\n})")
            .step("Registry script", "Connect the item action",
                "Continue in the same registry file. The callback receives a context with the live player and item. It delegates to the Launch method on SignalWeapon; the returned boolean indicates whether the use interaction was handled.",
                "Registry.Items:Register(\"signal_launcher\", {\n    className = \"SignalWeapon\",\n    maxStackSize = 1,\n    durability = 180,\n    onUse = function(context)\n        return context.Item:Launch(signalBolt, context.Player)\n    end\n})")
            .step("Registry script", "Add a second action without changing the item type",
                "If the weapon should also react to melee hits, replace the previous Registry.Items:Register call with this complete definition. Do not register the same id twice. onAttackEntity runs after Minecraft applies the hit, so use it for effects or records rather than canceling damage.",
                "Registry.Items:Register(\"signal_launcher\", {\n    className = \"SignalWeapon\",\n    maxStackSize = 1,\n    durability = 180,\n    onUse = function(context)\n        return context.Item:Launch(signalBolt, context.Player)\n    end,\n    onAttackEntity = function(context)\n        context.Target:SetGlowing(true)\n        return true\n    end\n})")
            .step("In game", "Give yourself the registered item",
                "Save the registry script and restart Minecraft so the item and projectile are registered. Replace myaddon with your mod id, give yourself the item, then use it and hit a mob to exercise the two actions.",
                "/give @s myaddon:signal_launcher")
            .build()));
    }
}
