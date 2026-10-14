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
        ServiceEntry.Builder attribute = ServiceEntry.builder("Attribute", MLuaAttributeService::new)
            .description("Service for managing entity attribute modifiers.");
        attribute.function("GetAttribute", "Returns an entity attribute value by registered attribute name.")
            .function("SetBaseValue", "Sets an entity attribute's base value.")
            .function("GetModifiers", "Returns the modifiers currently applied to an entity attribute.")
            .function("AddModifier", "Adds or replaces a modifier on an entity attribute.")
            .function("RemoveModifier", "Removes an attribute modifier by identifier.")
            .function("GetRegisteredAttributes", "Returns the names of attributes registered with MLua.");
        return List.of(attribute.build());
    }
    @Override public void contributeDocs(MLuaDocBuilder docs) {
        docs.service("Attribute", "MLuaAttributeService", "Service for managing entity attribute modifiers.", "services/attribute.html");
    }
}
