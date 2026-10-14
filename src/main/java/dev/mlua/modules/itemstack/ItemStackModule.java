package dev.mlua.modules.itemstack;

import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.minecraft.binding.MLuaItemWrapper;
import dev.mlua.modules.itemstack.Service.MLuaItemStackService;
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
        docs.service("ItemStackService", "MLuaItemStackService",
            "Create, clone, inspect, and mutate Minecraft ItemStack values.",
            "services/item-stack.html");
        docs.clazz("Item", "MLuaItemWrapper", "A live item stack instance with count, durability, display name, tags, and enchantments.", "classes/item.html", "Instance");
        docs.clazz("BlockItem", "MLuaItemWrapper", "An item instance that places its associated block and inherits the item stack API.", "classes/block-item.html", "Item");
        docs.clazz("Weapon", "MLuaItemWrapper", "A scriptable weapon base for melee, ranged, thrown, and other item actions.", "classes/weapon.html", "Item");
        docs.signature("Item", "Item:GetRemainingDurability()", "number");
        docs.signature("Item", "Item:GetDamagePercent()", "number");
        docs.signature("Item", "Item:IsEnchantable()", "boolean");
        docs.signature("Item", "Item:IsStackable()", "boolean");
        docs.guideStep("Item", "Read and change a live item stack",
            "Item values are live stack wrappers returned by player inventory and held-item methods. Use Item.Find to create a stack when you need one directly.",
            "local item = Item.Find(\"minecraft:torch\", 4)\nprint(item:GetId(), item:GetCount(), item:IsStackable())\nif item:IsDamageable() then\n    print(item.RemainingDurability, item.DamagePercent)\nend\nitem:SetCount(8)");
        docs.guideStep("Weapon", "Build a weapon from item actions",
            "Weapon is a reusable Item subclass. Combine onUse and onAttackEntity callbacks with registered projectile types to define thrown, ranged, melee, or hybrid behavior without a weapon-specific item kind. Attack callbacks expose Attacker and Target as live entity instances; Player is also available when the attacker is a player.",
            "Minecraft:RegisterClass(\"TunedWeapon\", \"Weapon\", \"A reusable weapon behavior base.\")\nMinecraft:RegisterClass(\"ThrownWeaponProjectile\", \"Projectile\", \"A projectile fired by a weapon action.\")\n\nlocal thrown = Registry.Projectiles:Register(\"thrown_weapon\", {\n    className = \"ThrownWeaponProjectile\",\n    Speed = 48,\n    Damage = 7,\n    Gravity = true\n})\n\nRegistry.Items:Register(\"field_weapon\", {\n    className = \"TunedWeapon\",\n    durability = 320,\n    onUse = function(context)\n        if not context.Player then return false end\n        thrown:Fire({ Owner = context.Player })\n        context.Item:SetDamage(context.Item:GetDamage() + 1)\n        return true\n    end,\n    onAttackEntity = function(context)\n        context.Attacker:SetNbt(\"last_weapon_target\", context.Target:GetUUID())\n    end\n})");
    }
}
