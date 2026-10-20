package dev.mlua.ci;

import dev.mlua.api.MLuaDescription;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.core.service.MLuaService;

@MLuaDescription("CI probe for Java contributed MLua services and service classes.")
public final class SmokeService extends MLuaReflectiveService {
    public SmokeService(MLuaService.Context context) {
        super(context);
    }

    @Override
    public String name() {
        return "CiSmokeService";
    }

    @MLuaDescription("Returns the current script side and records that the Java service was called.")
    public String GetSide() {
        String side = this.context.side().name();
        System.setProperty("mlua.ci.service." + side, "called");
        return side;
    }

    @MLuaDescription("Checks that a registered item's Minecraft rarity, fire resistance, food, and crafting remainder were applied.")
    public String VerifyItemProperties(Object itemStack, String expectedRarity, boolean expectedFireResistance,
                                       int expectedNutrition, double expectedSaturation,
                                       boolean expectedAlwaysEdible, boolean expectedFast,
                                       String expectedRemainder) {
        if (itemStack == null) throw new IllegalArgumentException("Item property smoke check requires an ItemStack");
        try {
            Object item = itemStack.getClass().getMethod("getItem").invoke(itemStack);
            Object rarity = readRarity(itemStack, item);
            String actualRarity = rarity instanceof Enum<?> value ? value.name() : String.valueOf(rarity);
            if (!expectedRarity.equalsIgnoreCase(actualRarity)) {
                throw new IllegalStateException("Expected item rarity " + expectedRarity + " but got " + actualRarity);
            }
            boolean fireResistant = readFireResistance(item);
            if (fireResistant != expectedFireResistance) {
                throw new IllegalStateException("Expected fire resistance " + expectedFireResistance
                    + " but got " + fireResistant);
            }
            Object food = readFood(itemStack, item);
            int nutrition = ((Number) invokeNoArg(food, "nutrition", "getNutrition")).intValue();
            double saturation = readSaturationModifier(food, nutrition, expectedSaturation);
            boolean alwaysEdible = (boolean) invokeNoArg(food, "canAlwaysEat", "alwaysEat", "canEatWhenFull");
            if (nutrition != expectedNutrition || Math.abs(saturation - expectedSaturation) > 0.0001
                || alwaysEdible != expectedAlwaysEdible) {
                throw new IllegalStateException("Expected food properties nutrition=" + expectedNutrition
                    + ", saturation=" + expectedSaturation + ", alwaysEdible=" + expectedAlwaysEdible
                    + " but got nutrition=" + nutrition + ", saturation=" + saturation
                    + ", alwaysEdible=" + alwaysEdible);
            }
            boolean fast = readFastFood(itemStack, food);
            if (fast != expectedFast) {
                throw new IllegalStateException("Expected fast food=" + expectedFast + " but got " + fast);
            }
            Object remainder = readCraftingRemainder(itemStack, item);
            String actualRemainder = registryKey(remainder);
            if (!expectedRemainder.equals(actualRemainder)) {
                throw new IllegalStateException("Expected crafting remainder " + expectedRemainder
                    + " but got " + actualRemainder);
            }
            return "verified";
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not inspect registered item properties: "
                + failure.getClass().getSimpleName() + ": " + failure.getMessage(), failure);
        }
    }

    @MLuaDescription("Checks that a scripted sword uses Minecraft's native tiered tool behavior.")
    public String VerifyTieredTool(Object itemStack) {
        if (itemStack == null) throw new IllegalArgumentException("Tiered tool smoke check requires an ItemStack");
        try {
            Object item = itemStack.getClass().getMethod("getItem").invoke(itemStack);
            for (Class<?> type = item.getClass(); type != null; type = type.getSuperclass()) {
                if (type.getSimpleName().equals("SwordItem")) return "verified";
            }
            Object tool = readDataComponent(itemStack, "TOOL");
            Object attributes = readDataComponent(itemStack, "ATTRIBUTE_MODIFIERS");
            if (tool != null && attributes != null) return "verified";
            throw new IllegalStateException("Registered sword has neither the legacy SwordItem class nor native tool components");
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not inspect registered sword behavior", failure);
        }
    }

    @MLuaDescription("Checks that a registered bow has native Minecraft bow behavior and its default durability.")
    public String VerifyNativeBow(Object itemStack, int expectedDurability) {
        if (itemStack == null) throw new IllegalArgumentException("Bow smoke check requires an ItemStack");
        try {
            Object item = itemStack.getClass().getMethod("getItem").invoke(itemStack);
            boolean nativeBow = false;
            for (Class<?> type = item.getClass(); type != null; type = type.getSuperclass()) {
                if (type.getSimpleName().equals("BowItem")) {
                    nativeBow = true;
                    break;
                }
            }
            int durability = ((Number) itemStack.getClass().getMethod("getMaxDamage").invoke(itemStack)).intValue();
            if (!nativeBow || durability != expectedDurability) {
                throw new IllegalStateException("Expected native BowItem with durability " + expectedDurability
                    + " but got nativeBow=" + nativeBow + ", durability=" + durability);
            }
            return "verified";
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not inspect registered bow behavior", failure);
        }
    }

    private static Object readFood(Object stack, Object item) throws ReflectiveOperationException {
        Object component = readDataComponent(stack, "FOOD");
        if (component != null) return component;
        for (var method : item.getClass().getMethods()) {
            if (!method.getName().equals("getFoodProperties")) continue;
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length == 0) return method.invoke(item);
            Object[] arguments = new Object[parameters.length];
            boolean acceptsStack = false;
            for (int index = 0; index < parameters.length; index++) {
                if (parameters[index].isInstance(stack)) {
                    arguments[index] = stack;
                    acceptsStack = true;
                }
            }
            if (acceptsStack) return method.invoke(item, arguments);
        }
        throw new NoSuchMethodException("ItemStack or Item exposes no food properties");
    }

    private static boolean readFastFood(Object stack, Object food) throws ReflectiveOperationException {
        Object legacyFast = invokeOptionalNoArg(food, "isFastFood", "fast");
        if (legacyFast instanceof Boolean value) return value;
        Object legacyEatSeconds = invokeOptionalNoArg(food, "eatSeconds");
        if (legacyEatSeconds instanceof Number seconds) return seconds.floatValue() <= 1.0F;
        Object consumable = readDataComponent(stack, "CONSUMABLE");
        if (consumable != null) {
            Object seconds = invokeNoArg(consumable, "consumeSeconds");
            return ((Number) seconds).floatValue() <= 1.0F;
        }
        throw new NoSuchMethodException("Food or item stack exposes no fast-consumption property");
    }

    private static Object readCraftingRemainder(Object stack, Object item) throws ReflectiveOperationException {
        Object legacy = invokeOptionalNoArg(item, "getCraftingRemainingItem", "getCraftingRemainder");
        if (legacy != null) {
            // 26.1 exposes the crafting remainder as an ItemStackTemplate, while
            // older targets return the Item directly. Normalize both shapes to Item.
            Object remainderStack = invokeOptionalNoArg(legacy, "create");
            if (remainderStack != null) {
                Object remainderItem = invokeOptionalNoArg(remainderStack, "getItem");
                if (remainderItem != null) return remainderItem;
            }
            return legacy;
        }
        Object remainder = readDataComponent(stack, "USE_REMAINDER");
        if (remainder == null) return null;
        Object template = invokeNoArg(remainder, "convertInto");
        Object remainderStack = invokeNoArg(template, "create");
        return remainderStack.getClass().getMethod("getItem").invoke(remainderStack);
    }

    private static Object readDataComponent(Object stack, String componentName) throws ReflectiveOperationException {
        Class<?> components;
        try {
            components = Class.forName("net.minecraft.core.component.DataComponents");
        } catch (ClassNotFoundException absentOnLegacyTarget) {
            return null;
        }
        Object component;
        try {
            component = components.getField(componentName).get(null);
        } catch (NoSuchFieldException unavailableOnTarget) {
            return null;
        }
        for (var method : stack.getClass().getMethods()) {
            if (method.getName().equals("get") && method.getParameterCount() == 1
                && method.getParameterTypes()[0].isInstance(component)) {
                return method.invoke(stack, component);
            }
        }
        throw new NoSuchMethodException("ItemStack exposes no component getter for " + componentName);
    }

    private static String registryKey(Object item) throws ReflectiveOperationException {
        if (item == null) return null;
        Class<?> registries = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
        Object registry = registries.getField("ITEM").get(null);
        // Invoke the exported API interface; Forge's NamespacedWrapper methods
        // are public on the implementation but inaccessible through reflection
        // because that implementation class is in a non-exported package.
        Class<?> registryApi = Class.forName("net.minecraft.core.Registry");
        Object key = registryApi.getMethod("getKey", Object.class).invoke(registry, item);
        return key == null ? null : key.toString();
    }

    private static Object invokeNoArg(Object target, String... names) throws ReflectiveOperationException {
        Object value = invokeOptionalNoArg(target, names);
        if (value == null) throw new NoSuchMethodException(target.getClass().getName() + " exposes none of "
            + String.join(", ", names));
        return value;
    }

    private static Object invokeOptionalNoArg(Object target, String... names) throws ReflectiveOperationException {
        for (String name : names) {
            try {
                return target.getClass().getMethod(name).invoke(target);
            } catch (NoSuchMethodException ignored) {
                // Try the next version-specific accessor.
            }
        }
        return null;
    }

    private static Object readRarity(Object stack, Object item) throws ReflectiveOperationException {
        try {
            return stack.getClass().getMethod("getRarity").invoke(stack);
        } catch (NoSuchMethodException ignored) {
            for (var method : item.getClass().getMethods()) {
                if (method.getName().equals("getRarity") && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isInstance(stack)) {
                    return method.invoke(item, stack);
                }
            }
            throw new NoSuchMethodException("Item or ItemStack exposes no supported getRarity method");
        }
    }

    private static boolean readFireResistance(Object item) throws ReflectiveOperationException {
        try {
            return (boolean) item.getClass().getMethod("isFireResistant").invoke(item);
        } catch (NoSuchMethodException ignored) {
            Class<?> dataComponents = Class.forName("net.minecraft.core.component.DataComponents");
            Object components = item.getClass().getMethod("components").invoke(item);
            Class<?> componentType = Class.forName("net.minecraft.core.component.DataComponentType");
            Class<?> componentMap = Class.forName("net.minecraft.core.component.DataComponentMap");
            var get = componentMap.getMethod("get", componentType);
            for (String name : new String[] {"FIRE_RESISTANT", "DAMAGE_RESISTANT"}) {
                try {
                    Object component = dataComponents.getField(name).get(null);
                    if (get.invoke(components, component) != null) return true;
                } catch (NoSuchFieldException ignoredField) {
                    // Minecraft renamed this component between supported versions.
                }
            }
            return false;
        }
    }

    private static double readSaturationModifier(Object food, int nutrition, double expectedModifier)
        throws ReflectiveOperationException {
        Object modifier = invokeOptionalNoArg(food, "saturationModifier", "getSaturationModifier",
            "saturationMod", "getSaturationMod");
        if (modifier instanceof Number value) return value.doubleValue();

        double saturation = ((Number) invokeNoArg(food, "saturation")).doubleValue();
        // Some Minecraft versions store the effective saturation amount (2 * nutrition * modifier)
        // while older APIs expose the modifier itself.
        double effectiveAmount = nutrition * 2.0 * expectedModifier;
        if (Math.abs(saturation - expectedModifier) < 0.0001) return saturation;
        if (Math.abs(saturation - effectiveAmount) < 0.0001) return expectedModifier;
        return nutrition > 0 ? saturation / (nutrition * 2.0) : saturation;
    }

    @MLuaDescription("Marks the native screen lifecycle smoke check as complete.")
    public String MarkNativeScreenLifecycleComplete() {
        System.setProperty("mlua.ci.native_screen_lifecycle", "complete");
        return "complete";
    }
}
