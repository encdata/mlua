package dev.mlua.modules.client.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaClientReflectionTest {
    @Test
    void resolvesInheritedPrivateMembersAndCachesTheirFallbackNames() {
        var client = new ClientFixture();
        assertEquals("1.21-test", MLuaClientReflection.invoke(client, "missingName", "getVersion"));
        assertEquals("1.21-test", MLuaClientReflection.invoke(client, "getVersion"));
        assertEquals(854, MLuaClientReflection.field(client, "missingField", "width"));
        assertEquals(854, MLuaClientReflection.field(client, "width"));
    }

    @Test
    void absentMembersReturnNullButAccessorFailuresAreNotSilentlySkipped() {
        assertNull(MLuaClientReflection.invoke(new ClientFixture(), "notAnAccessor"));
        var failure = assertThrows(IllegalStateException.class,
            () -> MLuaClientReflection.invoke(new ThrowingFixture(), "broken", "fallback"));
        assertTrue(failure.getMessage().contains("broken"));
        assertEquals("valid", MLuaClientReflection.invoke(new ThrowingFixture(), "fallback"));
        assertThrows(AssertionError.class,
            () -> MLuaClientReflection.invoke(new FatalFixture(), "fatal"));
    }

    private static class BaseFixture {
        private final int width = 854;
        private String getVersion() { return "1.21-test"; }
    }

    private static final class ClientFixture extends BaseFixture { }

    private static final class ThrowingFixture {
        public String broken() { throw new IllegalArgumentException("client getter failed"); }
        public String fallback() { return "valid"; }
    }

    private static final class FatalFixture {
        public String fatal() { throw new AssertionError("fatal fixture error"); }
    }
}
