package dev.mlua.modules.registry.service;

import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;

/** Resolves block mining properties into the vanilla tags used during data generation. */
record MLuaRegistryMiningProperties(String mineableTag, String requiredTierTag) {
    static MLuaRegistryMiningProperties read(LuaTable properties, Map<String, Object> convertedProperties) {
        String mineableWith = MLuaRegistryPropertyReaders.stringProperty(properties,
                "mineableWith", "MineableWith", "toolType", "ToolType", "effectiveToolTag", "EffectiveToolTag");
        String mineableTag = mineableWith == null || mineableWith.isBlank()
                ? null : "mineable/" + normalizeToolName(mineableWith);

        String requiredTier = MLuaRegistryPropertyReaders.stringProperty(properties,
                "requiredToolTier", "RequiredToolTier", "toolTier", "ToolTier", "minToolTier", "MinToolTier");
        if (requiredTier != null && !requiredTier.isBlank()) {
            requiredTier = requiredToolTierTag(requiredTier);
            convertedProperties.put("requiresCorrectTool", true);
        }
        return new MLuaRegistryMiningProperties(mineableTag, requiredTier);
    }

    private static String normalizeToolName(String value) {
        return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "pick", "pickaxe" -> "pickaxe";
            case "axe" -> "axe";
            case "shovel", "spade" -> "shovel";
            case "hoe" -> "hoe";
            default -> throw new LuaError("[MLua] Unsupported mineableWith/toolType value: " + value);
        };
    }

    private static String requiredToolTierTag(String value) {
        return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "wood", "wooden", "stone" -> "needs_stone_tool";
            case "iron" -> "needs_iron_tool";
            case "diamond", "netherite" -> "needs_diamond_tool";
            default -> throw new LuaError("[MLua] Unsupported requiredToolTier/toolTier value: " + value);
        };
    }
}
