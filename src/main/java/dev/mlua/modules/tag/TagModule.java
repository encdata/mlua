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
        MLuaClassMethods.registerInstanceMethod(registration, "TagBuilder", "Remove",
            "Removes a registry identifier from this tag.");
        MLuaClassMethods.registerInstanceMethod(registration, "TagBuilder", "Contains",
            "Checks whether this tag contains a registry identifier.");
        MLuaClassMethods.registerInstanceMethod(registration, "TagBuilder", "Clear",
            "Removes all registry identifiers from this tag.");
        MLuaClassMethods.registerInstanceMethod(registration, "TagBuilder", "GetValues",
            "Returns the current tag values as a dense Lua array.");
        MLuaClassMethods.registerInstanceMethod(registration, "TagBuilder", "SetReplace",
            "Sets whether this tag replaces existing values.");
        MLuaClassMethods.registerInstanceMethod(registration, "TagBuilder", "Build",
            "Serializes this tag builder as JSON.");
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.service("Tag", "MLuaTagService",
                "Creates mutable item, block, and entity type tag builders for registration through Registry.Tags.",
                "services/tag.html");
        docs.clazz("TagBuilder", "MLuaTagService", "A live builder for collecting and editing registry tag values.", "classes/tag-builder.html", "Instance");
        docs.method("TagBuilder", "TagBuilder:Add(id: string): TagBuilder", "Adds an item, block, entity type id, or #tag reference. Duplicate values are ignored.", "a resource identifier, optionally prefixed with # for another tag", "TagBuilder", "shared:Shared", null);
        docs.method("TagBuilder", "TagBuilder:Remove(id: string): boolean", "Removes the exact value, returning whether it was present.", "the identifier as it was added, including its optional # prefix", "boolean", "shared:Shared", null);
        docs.method("TagBuilder", "TagBuilder:Contains(id: string): boolean", "Checks whether the exact value is present.", "the identifier as it was added, including its optional # prefix", "boolean", "shared:Shared", null);
        docs.method("TagBuilder", "TagBuilder:Clear(): TagBuilder", "Removes all values from this builder.", null, "TagBuilder", "shared:Shared", null);
        docs.method("TagBuilder", "TagBuilder:GetValues(): string[]", "Returns the current values in insertion order.", null, "string[]", "shared:Shared", null);
        docs.method("TagBuilder", "TagBuilder:SetReplace(replace: boolean): TagBuilder", "Sets whether the generated tag replaces existing values.", null, "TagBuilder", "shared:Shared", null);
        docs.method("TagBuilder", "TagBuilder:Build(): string", "Serializes the tag definition as JSON.", null, "string", "shared:Shared", null);
        docs.signature("Tag", "Tag:Items(): TagBuilder", "Builds an item tag.");
        docs.signature("Tag", "Tag:Blocks(): TagBuilder", "Builds a block tag.");
        docs.signature("Tag", "Tag:EntityTypes(): TagBuilder", "Builds an entity type tag.");
        docs.guideStep("Tag", "Build and register an item tag",
            "Tag builders keep insertion order, ignore duplicate values, and can reference another tag with a leading #. Build returns JSON accepted by the matching Registry.Tags registration method.",
            "local Tag = Minecraft:GetService(\"Tag\")\nlocal Registry = Minecraft:GetService(\"Registry\")\n\nlocal materials = Tag.Items()\n    :Add(\"minecraft:iron_ingot\")\n    :Add(\"minecraft:gold_ingot\")\n    :Add(\"#minecraft:coals\")\n\nif materials:Contains(\"minecraft:gold_ingot\") then\n    materials:Remove(\"minecraft:gold_ingot\")\nend\n\nRegistry.Tags:RegisterItem(\"signal_materials\", materials:Build())");
    }
}
