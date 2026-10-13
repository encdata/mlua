package dev.mlua.modules.config;

import dev.mlua.modules.config.Service.MLuaConfigService;
import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import java.util.List;
import java.util.Map;

public class ConfigModule implements MLuaModule {
    @Override
    public String id() { return "config"; }

    @Override
    public List<ServiceEntry> services() {
        ServiceEntry.Builder config = ServiceEntry.builder("Config", MLuaConfigService::new)
            .description("Reads and updates registered MLua configuration values.");
        config.function("Get", "Returns the current value for a registered configuration key.")
            .function("Set", "Updates a configuration value for the requested scope.")
            .function("Save", "Persists the current configuration values to disk.")
            .function("Exists", "Returns whether a configuration key is registered.");
        return List.of(config.build());
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.signature("Config", "Config:Get(path: string, scope?: string)", null);
        docs.signature("Config", "Config:Set(path: string, value: any, scope?: string)", null);
        docs.signature("Config", "Config:Save(scope?: string)", null);
        docs.signature("Config", "Config:Exists(path: string, scope?: string)", null);

        docs.service("Config", "MLuaConfigService",
            "Reads and updates registered MLua configuration values.", "services/config.html");
        docs.type("ConfigDefinition", null, "Options for registering one configuration value.");
        docs.field("ConfigDefinition", "type", "string", "Value type, such as boolean, number, string, or enum.");
        docs.field("ConfigDefinition", "default", "any", "Initial value for this configuration entry.");
        docs.field("ConfigDefinition", "description?", "string", "Optional explanation shown in the config screen.");
        docs.field("ConfigDefinition", "min?", "number", "Optional minimum for numeric values.");
        docs.field("ConfigDefinition", "max?", "number", "Optional maximum for numeric values.");
        docs.field("ConfigDefinition", "options?", "string[]", "Allowed values for enum entries.");
    }
}
