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
        registration.registerClass("ItemTagBuilder", "TagBuilder", "A builder for item tags.");
        registration.registerClass("BlockTagBuilder", "TagBuilder", "A builder for block tags.");
        registration.registerClass("EntityTypeTagBuilder", "TagBuilder", "A builder for entity type tags.");
        MLuaClassMethods.registerInstanceMethod(registration, "TagBuilder", "Add",
            "Adds a registry identifier to this tag.");
        MLuaClassMethods.registerInstanceMethod(registration, "TagBuilder", "AddAll",
            "Adds a dense array of registry identifiers to this tag.");
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
        docs.clazz("ItemTagBuilder", "MLuaTagService", "A live builder for item registry tags.", "classes/item-tag-builder.html", "TagBuilder");
        docs.clazz("BlockTagBuilder", "MLuaTagService", "A live builder for block registry tags.", "classes/block-tag-builder.html", "TagBuilder");
        docs.clazz("EntityTypeTagBuilder", "MLuaTagService", "A live builder for entity type registry tags.", "classes/entity-type-tag-builder.html", "TagBuilder");
        docs.method("TagBuilder", "TagBuilder:Add(id: string): TagBuilder", "Adds an item, block, entity type id, or #tag reference. Duplicate values are ignored.", "a resource identifier, optionally prefixed with # for another tag", "TagBuilder", "shared:Shared", null);
        docs.method("TagBuilder", "TagBuilder:AddAll(ids: string[]): TagBuilder", "Adds each identifier from a dense array, ignoring duplicates. The array is validated before any values are added.", "a dense array of resource identifiers, optionally prefixed with # for tag references", "TagBuilder", "shared:Shared", null);
        docs.method("TagBuilder", "TagBuilder:Remove(id: string): boolean", "Removes the exact value, returning whether it was present.", "the identifier as it was added, including its optional # prefix", "boolean", "shared:Shared", null);
        docs.method("TagBuilder", "TagBuilder:Contains(id: string): boolean", "Checks whether the exact value is present.", "the identifier as it was added, including its optional # prefix", "boolean", "shared:Shared", null);
        docs.method("TagBuilder", "TagBuilder:Clear(): TagBuilder", "Removes all values from this builder.", null, "TagBuilder", "shared:Shared", null);
        docs.method("TagBuilder", "TagBuilder:GetValues(): string[]", "Returns the current values in insertion order.", null, "string[]", "shared:Shared", null);
        docs.method("TagBuilder", "TagBuilder:SetReplace(replace: boolean): TagBuilder", "Sets whether the generated tag replaces existing values.", null, "TagBuilder", "shared:Shared", null);
        docs.method("TagBuilder", "TagBuilder:Build(): string", "Serializes the tag definition as JSON.", null, "string", "shared:Shared", null);
        docs.signature("Tag", "Tag:Items(): ItemTagBuilder", "Builds an item tag.");
        docs.signature("Tag", "Tag:Blocks(): BlockTagBuilder", "Builds a block tag.");
        docs.signature("Tag", "Tag:EntityTypes(): EntityTypeTagBuilder", "Builds an entity type tag.");
        docs.guideStep("Tag", "Build and register an item tag",
            "Tag builders keep insertion order, ignore duplicate values, and can reference another tag with a leading #. Build returns JSON accepted by the matching Registry.Tags registration method.",
            "local Tag = Minecraft:GetService(\"Tag\")\nlocal Registry = Minecraft:GetService(\"Registry\")\n\nlocal materials = Tag.Items()\n    :Add(\"minecraft:iron_ingot\")\n    :Add(\"minecraft:gold_ingot\")\n    :Add(\"#minecraft:coals\")\n\nif materials:Contains(\"minecraft:gold_ingot\") then\n    materials:Remove(\"minecraft:gold_ingot\")\nend\n\nRegistry.Tags:RegisterItem(\"signal_materials\", materials:Build())");
    }
}
