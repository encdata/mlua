package dev.mlua.modules.coreclasses;

import dev.mlua.core.classes.MLuaClassRegistry;
import dev.mlua.module.MLuaRegistration;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import static org.junit.jupiter.api.Assertions.assertSame;

final class CoreClassesModuleTest {
    @Test
    void instanceReturnsItsImmediateSuperclassClassTable() {
        TestRegistration registration = new TestRegistration();
        new CoreClassesModule().registerClasses(registration);
        registration.registerClass("Encounter", "Instance", "A scripted encounter instance.");

        LuaTable instance = registration.createClassInstance("Encounter", null);
        LuaTable superclass = instance.get("GetSuperclass").call(instance).checktable();

        assertSame(registration.classTable("Instance"), superclass);
    }

    private static final class TestRegistration implements MLuaRegistration {
        private final MLuaClassRegistry classes = new MLuaClassRegistry();

        @Override
        public LuaTable registerClass(String name, String parentName) {
            return classes.register(name, parentName);
        }

        @Override
        public LuaTable registerClassTable(String name, LuaTable classTable, String parentName) {
            return classes.register(name, classTable, parentName);
        }

        @Override
        public LuaTable classTable(String name) {
            return classes.get(name);
        }

        @Override
        public java.util.Set<String> classNames() {
            return classes.names();
        }

        @Override
        public LuaTable registerClassProperty(String className, String propertyName,
                                              LuaValue getter, LuaValue setter, String description) {
            return classes.registerProperty(className, propertyName, getter, setter, description);
        }

        @Override
        public void refreshClassMetadata(String className) {
            classes.refreshMetadata(className);
        }

        @Override
        public String classNameOf(LuaValue instance) {
            return classes.classNameOf(instance);
        }

        @Override
        public LuaTable attachClassInstance(LuaTable instance, String name, String parentName) {
            return classes.attachInstance(instance, name, parentName);
        }
    }
}
