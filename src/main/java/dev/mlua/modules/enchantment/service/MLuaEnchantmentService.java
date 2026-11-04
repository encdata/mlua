package dev.mlua.modules.enchantment.service;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import dev.mlua.core.service.MLuaService;
import dev.mlua.api.MLuaDescription;
import dev.mlua.core.service.MLuaServerThreadReflectiveService;
import java.util.Optional;

@MLuaDescription("Service for managing enchantments on items.")
public final class MLuaEnchantmentService extends MLuaServerThreadReflectiveService {
    public MLuaEnchantmentService(MLuaService.Context context) {
        super(context);
    }

    @Override
    public String name() {
        return "Enchantment";
    }

    @Override
    public LuaTable toLua() {
        LuaTable table = super.toLua();
        table.set("__side", "Both");
        return table;
    }

    @MLuaDescription("Returns details for an enchantment by identifier.")
    public LuaTable GetEnchantment(String id) {
        return MLuaEnchantmentOperations.getEnchantment(id);
    }

    @MLuaDescription("Returns the registered enchantments.")
    public LuaTable GetAllEnchantments() {
        return MLuaEnchantmentOperations.getAllEnchantments();
    }

    @MLuaDescription("Adds an enchantment and level to an item stack.")
    public boolean AddEnchantment(LuaValue itemValue, String id, Optional<Integer> requestedLevel) {
        return MLuaEnchantmentOperations.addEnchantment(itemValue, id, requestedLevel);
    }

    @MLuaDescription("Removes an enchantment from an item stack.")
    public boolean RemoveEnchantment(LuaValue itemValue, String id) {
        return MLuaEnchantmentOperations.removeEnchantment(itemValue, id);
    }

    @MLuaDescription("Returns a map from enchantment id to level for an item stack.")
    public LuaTable GetEnchantments(LuaValue itemValue) {
        return MLuaEnchantmentOperations.getEnchantments(itemValue);
    }

    @MLuaDescription("Returns whether an item stack has an enchantment.")
    public boolean HasEnchantment(LuaValue itemValue, String id) {
        return MLuaEnchantmentOperations.hasEnchantment(itemValue, id);
    }

}
