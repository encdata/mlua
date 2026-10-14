package dev.mlua.modules.tag;

import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.modules.tag.Service.MLuaTagService;
import java.util.List;

public class TagModule implements MLuaModule {
    @Override
    public String id() { return "tag"; }

    @Override
    public List<String> dependencies() { return List.of("core-classes"); }

    @Override
    public int priority() { return 12; }

    @Override
    public List<ServiceEntry> services() {
        ServiceEntry.Builder tag = ServiceEntry.builder("Tag", MLuaTagService::new)
            .reflective(MLuaTagService.class);
        return List.of(tag.build());
    }

    @Override
    public void registerClasses(MLuaRegistration registration) {
        registration.registerClass("TagBuilder", registration.requireInstanceBaseClassName(),
            "A mutable builder for collecting item, block, or entity type tag values.");
        MLuaClassMethods.registerInstanceMethod(registration, "TagBuilder", "Add",
            "Adds a registry identifier to this tag.");
        MLuaClassMethods.registerInstanceMethod(registration, "TagBuilder", "SetReplace",
            "Sets whether this tag replaces existing values.");
        MLuaClassMethods.registerInstanceMethod(registration, "TagBuilder", "Build",
            "Serializes this tag builder as JSON.");
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.service("Tag", "MLuaTagService",
                "Service for creating tag builders.",
                "services/tag.html");
    }
}
