package dev.mlua.runtime.registry;

import java.util.List;
import java.lang.reflect.Proxy;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** 26.1 NeoForge tooltip bridge; kept target-owned because the inherited 1.21 helper has the old signature. */
public final class MLuaItemHooks {
    private MLuaItemHooks() {}
    public static Component itemName(String registryName) {
        return Component.literal(MLuaRegistryNameHelper.displayName(registryName));
    }
    public static void appendDescriptionTooltip(Item item, ItemStack stack, Item.TooltipContext context,
                                                 net.minecraft.world.item.component.TooltipDisplay display,
                                                 Consumer<Component> tooltip, TooltipFlag flag) {
        var id = BuiltInRegistries.ITEM.getKey(item);
        if (id == null) return;
        List<String> desc = MLuaLoaderRegistry.getItemDescription(id.getNamespace(), id.getPath());
        if (desc != null) for (String line : desc)
            tooltip.accept(Component.literal(line).withStyle(style -> style.withItalic(true).withColor(0x808080)));
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void initializeClient(Consumer consumer) {
        try {
            Class<?> extensionsClass = Class.forName(
                "net.neoforged.neoforge.client.extensions.common.IClientItemExtensions");
            Object extensions = Proxy.newProxyInstance(
                MLuaItemHooks.class.getClassLoader(),
                new Class<?>[]{extensionsClass},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "MLuaItemExtensions";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == (args != null && args.length > 0 ? args[0] : null);
                            default -> method.invoke(proxy, args);
                        };
                    }

                    Class<?> returnType = method.getReturnType();
                    if (returnType == boolean.class) return false;
                    if (returnType == byte.class) return (byte) 0;
                    if (returnType == short.class) return (short) 0;
                    if (returnType == int.class) return 0;
                    if (returnType == long.class) return 0L;
                    if (returnType == float.class) return 0.0f;
                    if (returnType == double.class) return 0.0d;
                    if (returnType == char.class) return '\0';
                    return null;
                });
            consumer.accept(extensions);
        } catch (ClassNotFoundException ignored) {
            // The target can run without NeoForge's client extension API on the classpath.
        }
    }
}
