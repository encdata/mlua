package dev.mlua.modules.javabridge.service;

import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

/** Implements Lua indexing, assignment, and length for Java arrays and collections. */
final class MLuaJavaCollectionAccess {
    private final MLuaJavaService service;

    MLuaJavaCollectionAccess(MLuaJavaService service) {
        this.service = service;
    }

    LuaValue read(Object instance, Type declaredType, LuaValue key) {
        if (instance.getClass().isArray() && key.type() == LuaValue.TNUMBER) {
            int index = zeroBasedIndex(key);
            int length = Array.getLength(instance);
            if (index < 0 || index >= length) {
                return LuaValue.NIL;
            }
            Object value = Array.get(instance, index);
            return value == null ? MLuaJavaService.JAVA_NULL : service.wrapJavaValue(value, instance.getClass().getComponentType());
        }

        if (instance instanceof List<?> list && key.type() == LuaValue.TNUMBER) {
            int index = zeroBasedIndex(key);
            if (index < 0 || index >= list.size()) {
                return LuaValue.NIL;
            }
            return service.wrapJavaValue(list.get(index), service.typeArgument(declaredType, 0));
        }

        if (instance instanceof Map<?, ?> map) {
            Object convertedKey = service.convertLuaValue(key, service.typeArgument(declaredType, 0));
            try {
                // Lua has one numeric key type while Java maps may distinguish Integer,
                // Long, and Double keys. Prefer the declared conversion when it exists,
                // then resolve an integral Lua number against the actual map key types.
                Object mapKey = map.containsKey(convertedKey) ? convertedKey : resolveMapKey(map, key);
                if (map.containsKey(mapKey)) {
                    return service.wrapJavaValue(map.get(mapKey), service.typeArgument(declaredType, 1));
                }
            } catch (ClassCastException | NullPointerException | IllegalArgumentException rejectedKey) {
                throw collectionRejection("read key from", instance, rejectedKey);
            }
        }

        return LuaValue.NIL;
    }

    boolean write(Object instance, Type declaredType, LuaValue key, LuaValue value) {
        if (instance.getClass().isArray() && key.type() == LuaValue.TNUMBER) {
            int index = zeroBasedIndex(key);
            int length = Array.getLength(instance);
            if (index < 0 || index >= length) {
                throw new LuaError("[MLua] Array index must be an integer between 1 and " + length);
            }
            Object converted = service.convertLuaValue(value, instance.getClass().getComponentType());
            try {
                Array.set(instance, index, converted);
            } catch (ArrayStoreException | IllegalArgumentException rejectedValue) {
                throw collectionRejection("store value in", instance, rejectedValue);
            }
            return true;
        }

        if (instance instanceof List<?> rawList && key.type() == LuaValue.TNUMBER) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) rawList;
            int index = zeroBasedIndex(key);
            if (index < 0 || index >= list.size()) {
                throw new LuaError("[MLua] List index must be an integer between 1 and " + list.size());
            }
            Object converted = service.convertLuaValue(value, service.typeArgument(declaredType, 0));
            try {
                list.set(index, converted);
            } catch (UnsupportedOperationException error) {
                throw new LuaError("[MLua] Java list " + instance.getClass().getName() + " is not writable");
            } catch (ClassCastException | NullPointerException | IllegalArgumentException rejectedValue) {
                throw collectionRejection("write value to", instance, rejectedValue);
            }
            return true;
        }

        if (instance instanceof Map<?, ?> rawMap) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> map = (Map<Object, Object>) rawMap;
            Object convertedKey = service.convertLuaValue(key, service.typeArgument(declaredType, 0));
            Object convertedValue = service.convertLuaValue(value, service.typeArgument(declaredType, 1));
            try {
                Object mapKey = map.containsKey(convertedKey) ? convertedKey : resolveMapKey(map, key);
                map.put(mapKey, convertedValue);
            } catch (UnsupportedOperationException error) {
                throw new LuaError("[MLua] Java map " + instance.getClass().getName() + " is not writable");
            } catch (ClassCastException | NullPointerException | IllegalArgumentException rejectedEntry) {
                throw collectionRejection("write entry to", instance, rejectedEntry);
            }
            return true;
        }

        return false;
    }

    private LuaError collectionRejection(String operation, Object collection, RuntimeException cause) {
        String detail = cause.getMessage();
        return new LuaError("[MLua] Java collection " + collection.getClass().getName()
            + " rejected an attempt to " + operation + " it"
            + (detail == null || detail.isBlank() ? "." : ": " + detail));
    }

    Integer length(Object instance) {
        if (instance.getClass().isArray()) {
            return Array.getLength(instance);
        }
        if (instance instanceof Collection<?> collection) {
            return collection.size();
        }
        if (instance instanceof Map<?, ?> map) {
            return map.size();
        }
        return null;
    }

    private int zeroBasedIndex(LuaValue key) {
        double number = key.todouble();
        if (!Double.isFinite(number) || number < 1 || number > Integer.MAX_VALUE
            || number != Math.rint(number)) {
            return -1;
        }
        return (int) number - 1;
    }

    Object unwrapMapKey(LuaValue key) {
        if (key.type() == LuaValue.TSTRING) {
            return key.tojstring();
        }
        if (key.type() == LuaValue.TNUMBER) {
            return key.todouble();
        }
        if (key.type() == LuaValue.TBOOLEAN) {
            return key.toboolean();
        }
        return service.unwrapJavaValue(key);
    }

    private Object resolveMapKey(Map<?, ?> map, LuaValue key) {
        Object direct = unwrapMapKey(key);
        if (map.containsKey(direct) || key.type() != LuaValue.TNUMBER) return direct;
        double number = key.todouble();
        if (!Double.isFinite(number) || number != Math.rint(number)) return direct;
        if (number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE) {
            int integer = (int) number;
            if (map.containsKey(integer)) return integer;
            if (integer >= Short.MIN_VALUE && integer <= Short.MAX_VALUE && map.containsKey((short) integer)) {
                return (short) integer;
            }
            if (integer >= Byte.MIN_VALUE && integer <= Byte.MAX_VALUE && map.containsKey((byte) integer)) {
                return (byte) integer;
            }
        }
        if (number >= Long.MIN_VALUE && number <= Long.MAX_VALUE) {
            long integer = (long) number;
            if (map.containsKey(integer)) return integer;
        }
        return direct;
    }

}
