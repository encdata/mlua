package dev.mlua.modules.tag;

import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.modules.tag.service.MLuaTagService;
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
        MLuaTagService.registerBuilderClasses(registration);
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.register(MLuaDocBuilder.Registration.service("Tag", "MLuaTagService",
                "Creates mutable item, block, and entity type tag builders for registration through Registry.Tags.",
                "services/tag.html"));
        docs.register(MLuaDocBuilder.Registration.clazz("TagBuilder", "MLuaTagService", "A live builder for collecting and editing registry tag values.", "classes/tag-builder.html", "Instance"));
        docs.register(MLuaDocBuilder.Registration.clazz("ItemTagBuilder", "MLuaTagService", "A live builder for item registry tags.", "classes/item-tag-builder.html", "TagBuilder"));
        docs.register(MLuaDocBuilder.Registration.clazz("BlockTagBuilder", "MLuaTagService", "A live builder for block registry tags.", "classes/block-tag-builder.html", "TagBuilder"));
        docs.register(MLuaDocBuilder.Registration.clazz("EntityTypeTagBuilder", "MLuaTagService", "A live builder for entity type registry tags.", "classes/entity-type-tag-builder.html", "TagBuilder"));
        docs.register(MLuaDocBuilder.Registration.method("TagBuilder", "TagBuilder:Add(id: string): TagBuilder", "Adds an item, block, entity type id, or #tag reference. Duplicate values are ignored. Each builder accepts up to " + dev.mlua.modules.tag.service.MLuaTagService.MAX_VALUES_PER_TAG + " distinct values.", "a resource identifier, optionally prefixed with # for another tag", "TagBuilder", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("TagBuilder", "TagBuilder:AddAll(ids: string[]): TagBuilder", "Adds each identifier from a dense array, ignoring duplicates. The array is validated before any values are added and is limited to " + dev.mlua.modules.tag.service.MLuaTagService.MAX_VALUES_PER_TAG + " entries.", "a dense array of resource identifiers, optionally prefixed with # for tag references", "TagBuilder", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("TagBuilder", "TagBuilder:SetValues(ids: string[]): TagBuilder", "Replaces the current values with a fully validated dense array. Validation completes before the builder changes, and duplicate identifiers are kept only at their first position.", "a dense array of resource identifiers, optionally prefixed with # for tag references", "TagBuilder", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("TagBuilder", "TagBuilder:Remove(id: string): boolean", "Removes the exact value, returning whether it was present.", "the identifier as it was added, including its optional # prefix", "boolean", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("TagBuilder", "TagBuilder:Contains(id: string): boolean", "Checks whether the exact value is present.", "the identifier as it was added, including its optional # prefix", "boolean", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("TagBuilder", "TagBuilder:Clear(): TagBuilder", "Removes all values from this builder.", null, "TagBuilder", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("TagBuilder", "TagBuilder:GetValues(): string[]", "Returns the current values in insertion order.", null, "string[]", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("TagBuilder", "TagBuilder:SetReplace(replace: boolean): TagBuilder", "Sets whether the generated tag replaces existing values.", null, "TagBuilder", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("TagBuilder", "TagBuilder:GetReplace(): boolean", "Returns whether the generated tag replaces existing values.", null, "boolean", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("TagBuilder", "TagBuilder:Build(): string", "Serializes the tag definition as JSON.", null, "string", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.signature("Tag", "Tag:Items(): ItemTagBuilder", "Builds an item tag."));
        docs.register(MLuaDocBuilder.Registration.signature("Tag", "Tag:Blocks(): BlockTagBuilder", "Builds a block tag."));
        docs.register(MLuaDocBuilder.Registration.signature("Tag", "Tag:EntityTypes(): EntityTypeTagBuilder", "Builds an entity type tag."));
        docs.register(MLuaDocBuilder.Registration.guideStep("Tag", "Build and register an item tag",
            "Tag builders keep insertion order, ignore duplicate values, and can reference another tag with a leading #. Build returns JSON accepted by the matching Registry.Tags registration method.",
            "local Tag = Minecraft:GetService(\"Tag\")\nlocal Registry = Minecraft:GetService(\"Registry\")\n\nlocal materials = Tag.Items()\n    :Add(\"minecraft:iron_ingot\")\n    :Add(\"minecraft:gold_ingot\")\n    :Add(\"#minecraft:coals\")\n\nif materials:Contains(\"minecraft:gold_ingot\") then\n    materials:Remove(\"minecraft:gold_ingot\")\nend\n\nRegistry.Tags:RegisterItem(\"signal_materials\", materials:Build())"));
    }
}
