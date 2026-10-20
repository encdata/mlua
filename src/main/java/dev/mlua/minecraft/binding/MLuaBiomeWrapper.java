package dev.mlua.minecraft.binding;

import dev.mlua.math.MLuaVector;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.core.engine.MLuaLuauTypes;
import dev.mlua.minecraft.MLuaResourceLocations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Position-aware, read-only Lua view over a registered Minecraft biome. */
public final class MLuaBiomeWrapper {
    private MLuaBiomeWrapper() { }

    public static LuaTable wrap(Holder<Biome> holder, Level level, BlockPos position, MLuaRegistration registration) {
        classTable(registration);
        LuaTable fields = new LuaTable();
        fields.set("__biome_holder", LuaValue.userdataOf(holder));
        fields.set("__level", LuaValue.userdataOf(level));
        fields.set("__position", LuaValue.userdataOf(position.immutable()));
        return registration.createClassInstance("Biome", fields);
    }

    public static LuaTable classTable(MLuaRegistration registration) {
        LuaTable type = registration.registerClass("Biome", registration.requireInstanceBaseClassName(),
            "A read-only view of a registered biome sampled in a dimension at a block position.");
        if (type.rawget("__initialized").toboolean()) return type;
        type.rawset("__initialized", LuaValue.TRUE);

        register(registration, "GetId", "Returns this biome's registered identifier.", (self, args) -> LuaValue.valueOf(id(holder(self), level(self))));
        register(registration, "GetPosition", "Returns the block position used to sample this biome.", (self, args) -> {
            BlockPos position = position(self);
            return MLuaVector.vector3(position.getX(), position.getY(), position.getZ());
        });
        register(registration, "GetTags", "Returns the registered biome tag identifiers.", (self, args) -> {
            LuaTable tags = new LuaTable();
            int index = 1;
            for (TagKey<Biome> tag : holder(self).tags().toList()) tags.set(index++, tag.location().toString());
            return tags;
        });
        register(registration, "HasTag", "Checks whether this biome has a registered tag.", (self, args) -> {
            ResourceLocation location = MLuaResourceLocations.parse(args.checkjstring(2));
            return LuaValue.valueOf(holder(self).is(TagKey.create(Registries.BIOME, location)));
        });
        register(registration, "GetTemperature", "Returns the biome's base temperature.", (self, args) -> LuaValue.valueOf(biome(self).getBaseTemperature()));
        register(registration, "GetPrecipitation", "Returns the precipitation type at the sampled position.", (self, args) -> LuaValue.valueOf(precipitation(self)));
        register(registration, "GetSkyColor", "Returns the biome sky color as a Color3.", (self, args) -> color(self, "getSkyColor", "skyColor"));
        register(registration, "GetFogColor", "Returns the biome fog color as a Color3.", (self, args) -> color(self, "getFogColor", "fogColor"));
        register(registration, "GetWaterColor", "Returns the biome water color as a Color3.", (self, args) -> color(self, "getWaterColor", "waterColor"));
        register(registration, "GetWaterFogColor", "Returns the biome water fog color as a Color3.", (self, args) -> color(self, "getWaterFogColor", "waterFogColor"));
        register(registration, "GetFoliageColor", "Returns the biome foliage color as a Color3.", (self, args) -> color(self, "getFoliageColor", "foliageColor"));
        register(registration, "GetGrassColor", "Returns the biome grass color at the sampled X/Z position as a Color3.", MLuaBiomeWrapper::grassColor);
        register(registration, "IsCold", "Checks whether Minecraft considers the biome cold enough to snow at the sampled position.",
            (self, args) -> LuaValue.valueOf(cold(self)));
        register(registration, "IsHot", "Checks whether the biome's base temperature is above one.",
            (self, args) -> LuaValue.valueOf(biome(self).getBaseTemperature() > 1.0F));

        property(registration, "Id", "GetId", "string", "This biome's registered identifier.");
        property(registration, "Position", "GetPosition", "Vector3", "The block position used to sample this biome.");
        property(registration, "Temperature", "GetTemperature", "number", "The biome's base temperature.");
        property(registration, "Precipitation", "GetPrecipitation", "string", "Precipitation at the sampled position: rain, snow, or none.");
        property(registration, "Tags", "GetTags", "string[]", "Registered biome tags.");
        property(registration, "SkyColor", "GetSkyColor", "Color3", "The biome sky color.");
        property(registration, "FogColor", "GetFogColor", "Color3", "The biome fog color.");
        property(registration, "WaterColor", "GetWaterColor", "Color3", "The biome water color.");
        property(registration, "WaterFogColor", "GetWaterFogColor", "Color3", "The biome water fog color.");
        property(registration, "FoliageColor", "GetFoliageColor", "Color3", "The biome foliage color.");
        property(registration, "GrassColor", "GetGrassColor", "Color3", "The biome grass color at the sampled X/Z position.");
        return type;
    }

    private static void register(MLuaRegistration registration, String name, String description, BiomeFunction function) {
        registration.registerClassFunction("Biome", name, new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) { return function.apply(args.arg1(), args); }
        }, description);
        MLuaClassMethods.registerInstanceMethod(registration, "Biome", name, description);
    }

    private static void property(MLuaRegistration registration, String name, String getter, String type, String description) {
        MLuaClassMethods.registerInstanceProperty(registration, "Biome", name, getter, null,
            type, "shared:Shared", description);
    }

    private static Holder<Biome> holder(LuaValue instance) {
        LuaValue value = instance.checktable().rawget("__biome_holder");
        if (!value.isuserdata(Holder.class)) throw new LuaError("[MLua] Invalid Biome instance.");
        @SuppressWarnings("unchecked") Holder<Biome> holder = (Holder<Biome>)value.touserdata(Holder.class);
        return holder;
    }

    private static Biome biome(LuaValue instance) { return holder(instance).value(); }

    private static Level level(LuaValue instance) {
        LuaValue value = instance.checktable().rawget("__level");
        if (!value.isuserdata(Level.class)) throw new LuaError("[MLua] Biome instance has no live dimension.");
        return (Level)value.touserdata(Level.class);
    }

    private static BlockPos position(LuaValue instance) {
        LuaValue value = instance.checktable().rawget("__position");
        if (!value.isuserdata(BlockPos.class)) throw new LuaError("[MLua] Biome instance has no sampled position.");
        return (BlockPos)value.touserdata(BlockPos.class);
    }

    private static String id(Holder<Biome> holder, Level level) {
        Object key = holder.unwrapKey().orElse(null);
        Object location = invoke(key, new String[]{"location", "identifier", "id"});
        return location == null ? "unknown" : location.toString();
    }

    private static LuaValue color(LuaValue self, String direct, String... accessors) {
        Biome biome = biome(self);
        Object target = invoke(biome, new String[]{"getSpecialEffects", "getVisualEffects"});
        if (target == null) target = biome;
        Object value = invoke(target, new String[]{direct});
        if (!(value instanceof Number)) value = invoke(target, accessors);
        return MLuaLuauTypes.color3FromArgb(value instanceof Number number ? number.intValue() : 0xFF808080);
    }

    private static LuaValue grassColor(LuaValue self, Varargs args) {
        Biome biome = biome(self);
        BlockPos position = position(self);
        Object target = invoke(biome, new String[]{"getSpecialEffects", "getVisualEffects"});
        if (target == null) target = biome;
        Object value = invoke(target, new String[]{"getGrassColor"}, position.getX(), position.getZ());
        if (!(value instanceof Number)) value = invoke(target, new String[]{"grassColor"});
        return MLuaLuauTypes.color3FromArgb(value instanceof Number number ? number.intValue() : 0xFF808080);
    }

    private static String precipitation(LuaValue self) {
        Object value = invoke(biome(self), new String[]{"getPrecipitationAt"}, position(self));
        if (value == null) value = invoke(biome(self), new String[]{"getPrecipitationAt"}, position(self), level(self).getSeaLevel());
        return value == null ? "none" : value.toString().toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean cold(LuaValue self) {
        Object value = invoke(biome(self), new String[]{"coldEnoughToSnow"}, position(self));
        if (value == null) value = invoke(biome(self), new String[]{"coldEnoughToSnow"}, position(self), level(self).getSeaLevel());
        return Boolean.TRUE.equals(value);
    }

    private static Object invoke(Object target, String[] names, Object... arguments) {
        if (target == null) return null;
        for (String name : names) for (java.lang.reflect.Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != arguments.length) continue;
            try { return method.invoke(target, arguments); } catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        return null;
    }

    @FunctionalInterface
    private interface BiomeFunction { LuaValue apply(LuaValue self, Varargs args); }

}
