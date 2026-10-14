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
        docs.register(MLuaDocBuilder.Registration.service("Attribute", "MLuaAttributeService", "Service for managing entity attribute modifiers.", "services/attribute.html"));
    }
}
