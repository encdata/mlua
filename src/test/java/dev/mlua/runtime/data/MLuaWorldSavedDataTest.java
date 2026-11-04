package dev.mlua.runtime.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MLuaWorldSavedDataTest {
    @Test
    void saveAndLoadPreserveEachDataScope() {
        MLuaWorldSavedData data = new MLuaWorldSavedData();
        data.persistentData().putInt("world", 11);
        data.syncedData().putInt("world", 12);
        data.playerPersistentData().putInt("player", 21);
        data.playerSyncedData().putInt("player", 22);
        data.entityPersistentData().putInt("entity", 31);
        data.entitySyncedData().putInt("entity", 32);

        CompoundTag saved = data.save(new CompoundTag());
        MLuaWorldSavedData loaded = MLuaWorldSavedData.load(saved);

        assertEquals(11, loaded.persistentData().getInt("world"));
        assertEquals(12, loaded.syncedData().getInt("world"));
        assertEquals(21, loaded.playerPersistentData().getInt("player"));
        assertEquals(22, loaded.playerSyncedData().getInt("player"));
        assertEquals(31, loaded.entityPersistentData().getInt("entity"));
        assertEquals(32, loaded.entitySyncedData().getInt("entity"));
    }
}
