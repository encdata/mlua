package dev.mlua.minecraft.binding;

import java.util.Locale;
import java.util.Optional;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

public final class MLuaBlockStateProperties {
    private MLuaBlockStateProperties() {
    }

    public static BlockState apply(BlockState state, LuaValue values) {
        if (values.isnil()) return state;
        if (!values.istable()) throw new LuaError("[MLua] Block state properties must be a table of property names and values.");
        LuaTable table = values.checktable();
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs entry = table.next(key);
            if (entry.arg1().isnil()) break;
            key = entry.arg1();
            if (!key.isstring()) throw new LuaError("[MLua] Block state property names must be strings.");
            String name = key.tojstring().toLowerCase(Locale.ROOT);
            Property<?> property = state.getBlock().getStateDefinition().getProperty(name);
            if (property == null) throw new LuaError("[MLua] Block '" + state.getBlock() + "' has no state property '" + name + "'.");
            LuaValue value = entry.arg(2);
            if (!(value.isstring() || value.isnumber() || value.isboolean())) {
                throw new LuaError("[MLua] Block state property '" + name + "' must be a string, number, or boolean.");
            }
            state = withProperty(state, property, value.tojstring());
        }
        return state;
    }

    public static LuaTable read(BlockState state) {
        LuaTable values = new LuaTable();
        for (Property<?> property : state.getProperties()) {
            addValue(values, state, property);
        }
        return values;
    }

    private static <T extends Comparable<T>> void addValue(LuaTable values, BlockState state, Property<T> property) {
        T value = state.getValue(property);
        if (value instanceof Boolean booleanValue) {
            values.set(property.getName(), LuaValue.valueOf(booleanValue));
        } else if (value instanceof Number numberValue) {
            values.set(property.getName(), LuaValue.valueOf(numberValue.doubleValue()));
        } else {
            values.set(property.getName(), property.getName(value));
        }
    }

    private static <T extends Comparable<T>> BlockState withProperty(BlockState state, Property<T> property, String value) {
        Optional<T> parsed = property.getValue(value);
        if (parsed.isEmpty()) {
            throw new LuaError("[MLua] Invalid value '" + value + "' for block state property '" + property.getName() + "'.");
        }
        return state.setValue(property, parsed.get());
    }

}
