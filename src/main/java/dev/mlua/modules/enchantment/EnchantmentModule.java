package dev.mlua.modules.enchantment;

import dev.mlua.modules.enchantment.Service.MLuaEnchantmentService;
import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import java.util.List;

public class EnchantmentModule implements MLuaModule {
    @Override public String id() { return "enchantment"; }
    @Override public int priority() { return 12; }
    @Override public List<ServiceEntry> services() {
        ServiceEntry.Builder enchantment = ServiceEntry.builder("Enchantment", MLuaEnchantmentService::new)
            .description("Service for managing enchantments on items.");
        enchantment.function("GetEnchantment", "Returns details for an enchantment by identifier.")
            .function("GetAllEnchantments", "Returns the registered enchantments.")
            .function("AddEnchantment", "Adds an enchantment and level to an item stack.")
            .function("RemoveEnchantment", "Removes an enchantment from an item stack.")
            .function("GetEnchantments", "Returns enchantments currently on an item stack.")
            .function("HasEnchantment", "Returns whether an item stack has an enchantment.");
        return List.of(enchantment.build());
    }
    @Override public void contributeDocs(MLuaDocBuilder docs) {
        docs.service("Enchantment", "MLuaEnchantmentService", "Service for managing enchantments on items.", "services/enchantment.html");
    }
}
