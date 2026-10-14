package dev.mlua.modules.enchantment;

import dev.mlua.modules.enchantment.Service.MLuaEnchantmentService;
import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import java.util.List;

public class EnchantmentModule implements MLuaModule {
    @Override public String id() { return "enchantment"; }
    @Override public int priority() { return 12; }
    @Override public List<ServiceEntry> services() {
        return List.of(ServiceEntry.builder("Enchantment", MLuaEnchantmentService::new)
            .reflective(MLuaEnchantmentService.class)
            .build());
    }
    @Override public void contributeDocs(MLuaDocBuilder docs) {
        docs.service("Enchantment", "MLuaEnchantmentService", "Service for managing enchantments on items.", "services/enchantment.html");
    }
}
