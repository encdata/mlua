package dev.mlua.modules.javabridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import dev.mlua.common.mixin.MLuaClassViewer;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaError;

final class MLuaJavaReflectionCatalogTest {
    @Test
    void cachesInheritedInstanceFieldLookups() {
        MLuaJavaReflectionCatalog catalog = new MLuaJavaReflectionCatalog();

        Field first = catalog.field(Child.class, "count", false);
        Field second = catalog.field(Child.class, "count", false);

        assertSame(Base.class, first.getDeclaringClass());
        assertSame(first, second);
    }

    @Test
    void resolvesPublicInterfaceFieldsOnClasses() {
        MLuaJavaReflectionCatalog catalog = new MLuaJavaReflectionCatalog();

        Field field = catalog.field(Implementation.class, "TOKEN", true);

        assertSame(Contract.class, field.getDeclaringClass());
    }

    @Test
    void reportsNormalizedFieldAmbiguity() {
        MLuaJavaReflectionCatalog catalog = new MLuaJavaReflectionCatalog();

        LuaError error = assertThrows(LuaError.class,
            () -> catalog.field(AmbiguousFields.class, "fooBar", false));

        assertTrue(error.getMessage().contains("ambiguous"));
    }

    @Test
    void reportsUnrelatedInterfaceFieldAmbiguity() {
        MLuaJavaReflectionCatalog catalog = new MLuaJavaReflectionCatalog();

        LuaError error = assertThrows(LuaError.class,
            () -> catalog.field(ConflictingImplementation.class, "TOKEN", true));

        assertTrue(error.getMessage().contains("interface field 'TOKEN' is ambiguous"));
    }

    @Test
    void resolvesExactOverloadByCachedDescriptor() throws Exception {
        MLuaJavaReflectionCatalog catalog = new MLuaJavaReflectionCatalog();
        Method expected = Overloads.class.getDeclaredMethod("send", String.class);
        String descriptor = MLuaClassViewer.descriptor(expected);

        Method first = catalog.exactMethod(Overloads.class, "send", descriptor, false);
        Method second = catalog.exactMethod(Overloads.class, "send", descriptor, false);

        assertEquals(expected, first);
        assertSame(first, second);
        assertNull(catalog.exactMethod(Overloads.class, "send", descriptor, true));
    }

    private static class Base {
        private int count;
    }

    private static final class Child extends Base { }

    private interface Contract {
        String TOKEN = "one";
    }

    private static final class Implementation implements Contract { }

    private interface OtherContract {
        String TOKEN = "two";
    }

    private static final class ConflictingImplementation implements Contract, OtherContract { }

    private static final class AmbiguousFields {
        private int foo_bar;
        private int foobar;
    }

    private static final class Overloads {
        public void send(String value) { }
        public void send(int value) { }
    }
}
