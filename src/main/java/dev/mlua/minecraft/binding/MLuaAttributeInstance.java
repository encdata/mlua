package dev.mlua.minecraft.binding;

import dev.mlua.module.MLuaRegistration;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class MLuaAttributeInstance {
    private static final String BACKING = "__mlua_attribute_instance";
    private static final String ID = "__mlua_attribute_id";

    private MLuaAttributeInstance() { }

    public static void registerClass(MLuaRegistration registration) {
        registration.registerClass("AttributeInstance", registration.requireInstanceBaseClassName(),
            "A live entity attribute with its base value, effective value, and registered default.");
        register(registration, "GetId", "Returns this attribute's namespaced identifier.", args -> LuaValue.valueOf(id(args.arg1().checktable())));
        register(registration, "GetValue", "Returns this attribute's effective value after modifiers.", args -> LuaValue.valueOf(attribute(args.arg1().checktable()).getValue()));
        register(registration, "GetBaseValue", "Returns this attribute's base value before modifiers.", args -> LuaValue.valueOf(attribute(args.arg1().checktable()).getBaseValue()));
        register(registration, "SetBaseValue", "Sets this attribute's base value.", args -> {
            attribute(args.arg1().checktable()).setBaseValue(args.checkdouble(2));
            return LuaValue.NONE;
        });
        register(registration, "GetDefaultValue", "Returns this attribute's registered default value.", args -> LuaValue.valueOf(defaultValue(args.arg1().checktable())));
        register(registration, "GetModifiers", "Returns the modifiers currently applied to this attribute.", args -> modifiers(args.arg1().checktable()));
        register(registration, "AddModifier", "Adds or replaces a transient modifier on this attribute.", args -> addModifier(args.arg1().checktable(), args));
        register(registration, "RemoveModifier", "Removes a transient modifier by its identifier.", args -> removeModifier(args.arg1().checktable(), args.checkjstring(2)));
        registration.registerClassProperty("AttributeInstance", "Id", getter("GetId"), null,
            "string", "server:Server", "This attribute's namespaced identifier.");
        registration.registerClassProperty("AttributeInstance", "Value", getter("GetValue"), null,
            "number", "server:Server", "The effective value after modifiers.");
        registration.registerClassProperty("AttributeInstance", "BaseValue", getter("GetBaseValue"), setter("SetBaseValue"),
            "number", "server:Server", "The base value before modifiers.");
        registration.registerClassProperty("AttributeInstance", "DefaultValue", getter("GetDefaultValue"), null,
            "number", "server:Server", "The registered default value.");
        registration.registerClassProperty("AttributeInstance", "Modifiers", getter("GetModifiers"), null,
            "table", "server:Server", "The modifiers currently applied to this attribute.");
    }

    public static LuaTable create(MLuaRegistration registration, String id, AttributeInstance attribute) {
        LuaTable instance = registration.createClassInstance("AttributeInstance");
        instance.rawset(BACKING, LuaValue.userdataOf(attribute));
        instance.rawset(ID, LuaValue.valueOf(id));
        return instance;
    }

    private static void register(MLuaRegistration registration, String name, String description,
                                 java.util.function.Function<Varargs, LuaValue> implementation) {
        registration.registerClassFunction("AttributeInstance", name, new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) { return implementation.apply(args); }
        }, description);
    }

    private static LuaValue getter(String methodName) {
        return new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return args.arg1().checktable().get(methodName).invoke(args.arg1()).arg1();
            }
        };
    }

    private static LuaValue setter(String methodName) {
        return new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return args.arg1().checktable().get(methodName).invoke(LuaValue.varargsOf(new LuaValue[]{args.arg1(), args.arg(2)}));
            }
        };
    }

    private static AttributeInstance attribute(LuaTable instance) {
        LuaValue value = instance.rawget(BACKING);
        if (value.isuserdata(AttributeInstance.class)) return value.checkuserdata(AttributeInstance.class);
        throw new LuaError("[MLua] AttributeInstance has no live Minecraft attribute.");
    }

    private static String id(LuaTable instance) {
        LuaValue value = instance.rawget(ID);
        if (value.isstring()) return value.tojstring();
        throw new LuaError("[MLua] AttributeInstance has no registered identifier.");
    }

    private static double defaultValue(LuaTable instance) {
        AttributeInstance attribute = attribute(instance);
        //--version 1.20.1
        return attribute.getAttribute().getDefaultValue();
        //--endversion
        //--version 1.21.1,26.1
        return attribute.getAttribute().value().getDefaultValue();
        //--endversion
    }

    private static LuaTable modifiers(LuaTable instance) {
        LuaTable result = new LuaTable();
        int index = 1;
        for (net.minecraft.world.entity.ai.attributes.AttributeModifier modifier : attribute(instance).getModifiers()) {
            LuaTable entry = new LuaTable();
            //--version 1.20.1
            entry.set("Id", modifier.getId().toString());
            entry.set("Name", modifier.getName());
            entry.set("Amount", LuaValue.valueOf(modifier.getAmount()));
            entry.set("Operation", LuaValue.valueOf(modifier.getOperation().toValue()));
            //--endversion
            //--version 1.21.1,26.1
            entry.set("Id", modifier.id().toString());
            entry.set("Name", modifier.id().toString());
            entry.set("Amount", LuaValue.valueOf(modifier.amount()));
            entry.set("Operation", LuaValue.valueOf(modifier.operation().id()));
            //--endversion
            result.set(index++, entry);
        }
        return result;
    }

    private static LuaValue addModifier(LuaTable instance, Varargs args) {
        AttributeInstance attribute = attribute(instance);
        String name = args.checkjstring(2);
        double amount = args.checkdouble(3);
        int operation = args.optint(4, 0);
        //--version 1.20.1
        java.util.UUID uuid = java.util.UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        attribute.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
            uuid, name, amount, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.fromValue(operation)));
        //--endversion
        //--version 1.21.1
        net.minecraft.resources.ResourceLocation id = modifierId(name);
        attribute.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(id, amount,
            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.BY_ID.apply(operation)));
        //--endversion
        //--version 26.1
        net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.fromNamespaceAndPath("mlua", modifierPath(name));
        attribute.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(id, amount,
            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.BY_ID.apply(operation)));
        //--endversion
        return LuaValue.TRUE;
    }

    private static LuaValue removeModifier(LuaTable instance, String name) {
        AttributeInstance attribute = attribute(instance);
        //--version 1.20.1
        attribute.removeModifier(java.util.UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        //--endversion
        //--version 1.21.1
        attribute.removeModifier(modifierId(name));
        //--endversion
        //--version 26.1
        attribute.removeModifier(net.minecraft.resources.Identifier.fromNamespaceAndPath("mlua", modifierPath(name)));
        //--endversion
        return LuaValue.TRUE;
    }

    //--version 1.21.1
    private static net.minecraft.resources.ResourceLocation modifierId(String name) {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("mlua", modifierPath(name));
    }
    //--endversion

    private static String modifierPath(String name) {
        return "modifier_" + name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_/.-]", "_");
    }

}
