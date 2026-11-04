package dev.mlua.minecraft.binding;

import dev.mlua.runtime.registry.MLuaLoaderRegistry;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

/** Applies addon-authored item descriptions to whichever tooltip API a target exposes. */
public final class MLuaItemTooltip {
    private MLuaItemTooltip() { }

    public static void append(Item item, Consumer<Component> output) {
        if (item == null || output == null) return;
        var id = BuiltInRegistries.ITEM.getKey(item);
        if (id == null) return;
        List<String> description = MLuaLoaderRegistry.getItemDescription(id.getNamespace(), id.getPath());
        if (description == null) return;
        for (String line : description) {
            output.accept(Component.literal(line).withStyle(style -> style.withItalic(true).withColor(0x808080)));
        }
    }
}
