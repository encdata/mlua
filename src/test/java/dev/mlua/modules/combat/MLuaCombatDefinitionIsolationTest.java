package dev.mlua.modules.combat;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import dev.mlua.core.MLuaRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaCombatDefinitionIsolationTest {
    @Test
    void damageSourcesAreScopedBySessionAndModAndCleanedIndependently() {
        Object firstSession = new Object();
        Object secondSession = new Object();
        LuaTable firstProps = new LuaTable();
        firstProps.set("isFire", LuaValue.TRUE);
        LuaTable secondProps = new LuaTable();
        secondProps.set("isExplosion", LuaValue.TRUE);
        LuaTable otherOwnerProps = new LuaTable();
        otherOwnerProps.set("isMagic", LuaValue.TRUE);

        MLuaDamageSourceRegistry.register(firstSession, "one", "impact", firstProps);
        MLuaDamageSourceRegistry.register(firstSession, "two", "impact", otherOwnerProps);
        MLuaDamageSourceRegistry.register(secondSession, "one", "impact", secondProps);

        assertTrue(MLuaDamageSourceRegistry.get(firstSession, "one", "impact").isFire());
        assertTrue(MLuaDamageSourceRegistry.get(firstSession, "two", "impact").isMagic());
        assertTrue(MLuaDamageSourceRegistry.get(secondSession, "one", "impact").isExplosion());
        MLuaDamageSourceRegistry.clearOwned(firstSession, "one");
        assertNull(MLuaDamageSourceRegistry.get(firstSession, "one", "impact"));
        assertNotNull(MLuaDamageSourceRegistry.get(firstSession, "two", "impact"));
        MLuaDamageSourceRegistry.clearRuntime(firstSession);
        assertNull(MLuaDamageSourceRegistry.get(firstSession, "two", "impact"));
        assertNotNull(MLuaDamageSourceRegistry.get(secondSession, "one", "impact"));
        MLuaDamageSourceRegistry.clearRuntime(secondSession);
    }

    @Test
    void projectileDefinitionsWithEqualNamesRemainIndependent() {
        MLuaRuntime runtime = MLuaRuntime.get();
        if (runtime.classTable("Projectile") == null) {
            runtime.registerClass("Projectile", null, "Base class for script-defined projectiles.");
        }
        if (runtime.classTable("IsolationTestProjectile") == null) {
            runtime.registerClass("IsolationTestProjectile", "Projectile", "Projectile used by an isolation test.");
        }
        Object firstSession = new Object();
        Object secondSession = new Object();
        LuaTable firstProps = new LuaTable();
        firstProps.set("Speed", LuaValue.valueOf(20));
        LuaTable secondProps = new LuaTable();
        secondProps.set("Speed", LuaValue.valueOf(45));
        LuaTable otherOwnerProps = new LuaTable();
        otherOwnerProps.set("Speed", LuaValue.valueOf(30));
        firstProps.set("ClassName", LuaValue.valueOf("IsolationTestProjectile"));
        secondProps.set("ClassName", LuaValue.valueOf("IsolationTestProjectile"));
        otherOwnerProps.set("ClassName", LuaValue.valueOf("IsolationTestProjectile"));

        MLuaProjectileDefinition.register(firstSession, "one", "bolt", firstProps, null);
        MLuaProjectileDefinition.register(firstSession, "two", "bolt", otherOwnerProps, null);
        MLuaProjectileDefinition.register(secondSession, "one", "bolt", secondProps, null);

        assertEquals(20, MLuaProjectileDefinition.get(firstSession, "one", "bolt").speed(), 0.0001);
        assertEquals(30, MLuaProjectileDefinition.get(firstSession, "two", "bolt").speed(), 0.0001);
        assertEquals(45, MLuaProjectileDefinition.get(secondSession, "one", "bolt").speed(), 0.0001);
        MLuaProjectileDefinition.clearRuntime(firstSession);
        assertNull(MLuaProjectileDefinition.get(firstSession, "one", "bolt"));
        assertNull(MLuaProjectileDefinition.get(firstSession, "two", "bolt"));
        assertNotNull(MLuaProjectileDefinition.get(secondSession, "one", "bolt"));
        MLuaProjectileDefinition.clearRuntime(secondSession);
    }
}
