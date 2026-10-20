package dev.mlua.modules.config;

import dev.mlua.modules.config.service.MLuaConfigService;
import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import java.util.List;
import java.util.Map;

public class ConfigModule implements MLuaModule {
    @Override
    public String id() { return "config"; }

    @Override
    public List<ServiceEntry> services() {
        return List.of(ServiceEntry.builder("Config", MLuaConfigService::new)
            .reflective(MLuaConfigService.class)
            .build());
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.register(MLuaDocBuilder.Registration.signature("Config", "Config:Get(path: string, scope?: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Config", "Config:GetAll(scope: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Config", "Config:Set(path: string, value: any, scope?: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Config", "Config:SetMany(scope: string, values: {[string]: any}): number", "number of values changed; updates validate before applying and save once"));
        docs.register(MLuaDocBuilder.Registration.signature("Config", "Config:Save(scope?: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Config", "Config:Exists(path: string, scope?: string)", null));

        docs.register(MLuaDocBuilder.Registration.service("Config", "MLuaConfigService",
            "Reads and updates registered MLua configuration values.", "services/config.html"));
        docs.register(MLuaDocBuilder.Registration.type("ConfigDefinition", null, "Options for registering one configuration value."));
        docs.register(MLuaDocBuilder.Registration.field("ConfigDefinition", "type?", "string", "Value type: boolean, int, long, double, string, or enum. Inferred from default when omitted."));
        docs.register(MLuaDocBuilder.Registration.field("ConfigDefinition", "default", "any", "Initial value for this configuration entry."));
        docs.register(MLuaDocBuilder.Registration.field("ConfigDefinition", "description?", "string", "Optional explanation shown in the config screen."));
        docs.register(MLuaDocBuilder.Registration.field("ConfigDefinition", "min?", "number", "Optional lower bound for numeric values. The default must meet it; saved and assigned values are clamped."));
        docs.register(MLuaDocBuilder.Registration.field("ConfigDefinition", "max?", "number", "Optional upper bound for numeric values. The default must meet it; saved and assigned values are clamped."));
        docs.register(MLuaDocBuilder.Registration.field("ConfigDefinition", "options?", "string[]", "Allowed values for an enum. Supplying options infers enum type when type is omitted; the default must match one option."));
        docs.register(MLuaDocBuilder.Registration.guideStep("Config", "Update several settings together",
            "SetMany takes an explicit scope and a table keyed by registered config paths. It checks every key and value before applying changes, then saves the config file once.",
            "local Config = Minecraft:GetService(\"Config\")\nlocal changed = Config:SetMany(\"common\", {\n    enabled = true,\n    max_charges = 4,\n    mode = \"nightmare\",\n})\nprint(\"Updated settings:\", changed)"));
    }
}
