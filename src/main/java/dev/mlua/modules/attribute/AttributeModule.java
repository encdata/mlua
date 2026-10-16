package dev.mlua.modules.attribute;

import dev.mlua.minecraft.binding.MLuaAttributeInstance;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.module.MLuaModule.ServiceEntry;
import dev.mlua.modules.attribute.Service.MLuaAttributeService;
import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import java.util.List;

public class AttributeModule implements MLuaModule {
    @Override public String id() { return "attribute"; }
    @Override public int priority() { return 12; }
    @Override public List<String> dependencies() { return List.of("core-classes"); }
    @Override public void registerClasses(MLuaRegistration registration) {
        MLuaAttributeInstance.registerClass(registration);
    }
    @Override public List<ServiceEntry> services() {
        return List.of(ServiceEntry.builder("Attribute", MLuaAttributeService::new)
            .reflective(MLuaAttributeService.class)
            .build());
    }
    @Override public void contributeDocs(MLuaDocBuilder docs) {
        docs.register(MLuaDocBuilder.Registration.service("Attribute", "MLuaAttributeService", "Reads and updates living entity attributes and transient modifiers on the server.", "services/attribute.html"));
        docs.register(MLuaDocBuilder.Registration.signature("Attribute", "Attribute:GetAttributeInstance(entity: LivingEntity, id: string): AttributeInstance?", "live AttributeInstance class or nil"));
        docs.register(MLuaDocBuilder.Registration.signature("Attribute", "Attribute:ResetBaseValue(entity: LivingEntity, id: string): boolean", "true when the base value was reset"));
        docs.register(MLuaDocBuilder.Registration.signature("Attribute", "Attribute:HasModifier(entity: LivingEntity, id: string, name: string): boolean", "whether the named transient modifier exists"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Attribute", "Apply a named transient modifier",
            "Attribute modifiers use a stable name as their identifier. Adding the same name replaces its previous transient modifier, so scripts can update an effect without stacking duplicates. Operations are 0 for addition, 1 for multiply base, and 2 for multiply total.",
            "local Attribute = Minecraft:GetService(\"Attribute\")\nlocal id\nfor _, registeredId in ipairs(Attribute:GetRegisteredAttributes()) do\n    if registeredId:find(\"attack_damage\", 1, true) then\n        id = registeredId\n        break\n    end\nend\n\nif id then\n    local attack = Attribute:GetAttributeInstance(player, id)\n    if attack then\n        Attribute:AddModifier(player, id, \"rage_effect\", 3.0, 0)\n        if Attribute:HasModifier(player, id, \"rage_effect\") then\n            print(attack.Value, attack.BaseValue, attack.DefaultValue)\n        end\n        Attribute:RemoveModifier(player, id, \"rage_effect\")\n        Attribute:ResetBaseValue(player, id)\n    end\nend"));
    }
}
