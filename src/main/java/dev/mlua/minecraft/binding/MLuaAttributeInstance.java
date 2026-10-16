package dev.mlua.minecraft.binding;

import dev.mlua.core.classes.MLuaClassMethods;
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
        register(registration, "SetBaseValue", "Sets this attribute's base value to a finite number.", args -> {
            attribute(args.arg1().checktable()).setBaseValue(finiteValue(args.arg(2), "base value"));
            return LuaValue.NONE;
        });
        register(registration, "GetDefaultValue", "Returns this attribute's registered default value.", args -> LuaValue.valueOf(defaultValue(args.arg1().checktable())));
        register(registration, "GetModifiers", "Returns the modifiers currently applied to this attribute.", args -> modifiers(args.arg1().checktable()));
        register(registration, "AddModifier", "Adds or replaces a transient modifier with a finite amount and operation 0, 1, or 2.", args -> addModifier(args.arg1().checktable(), args));
        register(registration, "RemoveModifier", "Removes a transient modifier by its identifier and returns whether it existed.", args -> removeModifier(args.arg1().checktable(), args.checkjstring(2)));
        MLuaClassMethods.registerInstanceProperty(registration, "AttributeInstance", "Id", "GetId", null,
            "string", "shared:Shared", "This attribute's namespaced identifier.");
        MLuaClassMethods.registerInstanceProperty(registration, "AttributeInstance", "Value", "GetValue", null,
            "number", "shared:Shared", "The effective value after modifiers.");
        MLuaClassMethods.registerInstanceProperty(registration, "AttributeInstance", "BaseValue", "GetBaseValue", "SetBaseValue",
            "number", "shared:Shared", "The base value before modifiers.");
        MLuaClassMethods.registerInstanceProperty(registration, "AttributeInstance", "DefaultValue", "GetDefaultValue", null,
            "number", "shared:Shared", "The registered default value.");
        MLuaClassMethods.registerInstanceProperty(registration, "AttributeInstance", "Modifiers", "GetModifiers", null,
            "table", "shared:Shared", "The modifiers currently applied to this attribute.");
    }

    public static LuaTable create(MLuaRegistration registration, String id, AttributeInstance attribute,
                                  LuaTable owner) {
        LuaValue cacheValue = owner.rawget("__mlua_attribute_instances");
        LuaTable cache;
        if (cacheValue.istable()) {
            cache = cacheValue.checktable();
        } else {
            cache = new LuaTable();
            owner.rawset("__mlua_attribute_instances", cache);
        }
        LuaValue cachedValue = cache.rawget(id);
        if (cachedValue.istable()) {
            LuaTable cached = cachedValue.checktable();
            LuaValue cachedAttribute = cached.rawget(BACKING);
            if (cachedAttribute.isuserdata(AttributeInstance.class)
                && cachedAttribute.touserdata(AttributeInstance.class) == attribute) {
                if (cached.rawget("__mlua_attribute_initializing").toboolean()) {
                    throw new LuaError("[MLua] Circular attribute initialization for '" + id + "'.");
                }
                return cached;
            }
        }
        LuaTable instance = registration.attachClassInstance(new LuaTable(), "AttributeInstance", null);
        instance.rawset(BACKING, LuaValue.userdataOf(attribute));
        instance.rawset(ID, LuaValue.valueOf(id));
        instance.rawset("__mlua_attribute_initializing", LuaValue.TRUE);
        cache.rawset(id, instance);
        try {
            registration.initializeClassInstance(instance, "AttributeInstance", null);
            instance.rawset("__mlua_attribute_initializing", LuaValue.NIL);
            return instance;
        } catch (RuntimeException | Error failure) {
            instance.rawset("__mlua_attribute_initializing", LuaValue.NIL);
            cache.rawset(id, LuaValue.NIL);
            throw failure;
        }
    }

    private static void register(MLuaRegistration registration, String name, String description,
                                 java.util.function.Function<Varargs, LuaValue> implementation) {
        registration.registerClassFunction("AttributeInstance", name, new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) { return implementation.apply(args); }
        }, description);
    }

    private static AttributeInstance attribute(LuaTable instance) {
        LuaValue value = instance.rawget(BACKING);
        if (value.isuserdata(AttributeInstance.class)) return (AttributeInstance) value.checkuserdata(AttributeInstance.class);
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
        double amount = finiteValue(args.arg(3), "modifier amount");
        int operation = operation(args);
        addTransientModifier(attribute, name, amount, operation);
        return LuaValue.TRUE;
    }

    public static void addTransientModifier(AttributeInstance attribute, String name, double amount, int operation) {
        finiteValue(amount, "modifier amount");
        if (operation < 0 || operation > 2) {
            throw new LuaError("[MLua] Attribute modifier operation must be a whole number from 0 to 2.");
        }
        //--version 1.20.1
        java.util.UUID uuid = MLuaAttributeModifierIds.legacy(name);
        attribute.removeModifier(uuid);
        attribute.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
            uuid, name, amount, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.fromValue(operation)));
        //--endversion
        //--version 1.21.1
        net.minecraft.resources.ResourceLocation id = modifierId(name);
        attribute.removeModifier(id);
        attribute.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(id, amount,
            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.BY_ID.apply(operation)));
        //--endversion
        //--version 26.1
        net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.fromNamespaceAndPath("mlua", MLuaAttributeModifierIds.path(name));
        attribute.removeModifier(id);
        attribute.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(id, amount,
            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.BY_ID.apply(operation)));
        //--endversion
    }

    public static boolean removeTransientModifier(AttributeInstance attribute, String name) {
        String id = modifierIdString(name);
        boolean present = attribute.getModifiers().stream().anyMatch(modifier -> modifierId(modifier).equals(id));
        if (!present) return false;
//--version 1.20.1
        attribute.removeModifier(MLuaAttributeModifierIds.legacy(name));
//--endversion
//--version 1.21.1
        attribute.removeModifier(modifierId(name));
//--endversion
//--version 26.1
        attribute.removeModifier(net.minecraft.resources.Identifier.fromNamespaceAndPath("mlua", MLuaAttributeModifierIds.path(name)));
//--endversion
        return true;
    }

    private static String modifierIdString(String name) {
//--version 1.20.1
        return MLuaAttributeModifierIds.legacy(name).toString();
//--endversion
//--version 1.21.1
        return modifierId(name).toString();
//--endversion
//--version 26.1
        return net.minecraft.resources.Identifier.fromNamespaceAndPath("mlua", MLuaAttributeModifierIds.path(name)).toString();
//--endversion
    }

    private static String modifierId(net.minecraft.world.entity.ai.attributes.AttributeModifier modifier) {
//--version 1.20.1
        return modifier.getId().toString();
//--endversion
//--version 1.21.1,26.1
        return modifier.id().toString();
//--endversion
    }

    public static double finiteValue(LuaValue value, String name) {
        if (!value.isnumber() || !Double.isFinite(value.todouble())) {
            throw new LuaError("[MLua] Attribute " + name + " must be a finite number.");
        }
        return value.todouble();
    }

   public static double finiteValue(double value, String name) {
      if (!Double.isFinite(value)) {
         throw new LuaError("[MLua] Attribute " + name + " must be a finite number.");
      }
      return value;
   }

    private static int operation(Varargs args) {
        LuaValue value = args.arg(4);
        if (value.isnil()) return 0;
        if (!value.isnumber()) throw new LuaError("[MLua] Attribute modifier operation must be a whole number from 0 to 2.");
        double number = value.todouble();
        if (!Double.isFinite(number) || number != Math.rint(number) || number < 0 || number > 2) {
            throw new LuaError("[MLua] Attribute modifier operation must be a whole number from 0 to 2.");
        }
        return (int)number;
    }

    private static LuaValue removeModifier(LuaTable instance, String name) {
        return LuaValue.valueOf(removeTransientModifier(attribute(instance), name));
    }

    //--version 1.21.1
    private static net.minecraft.resources.ResourceLocation modifierId(String name) {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("mlua", MLuaAttributeModifierIds.path(name));
    }
    //--endversion

}
