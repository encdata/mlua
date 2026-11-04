package dev.mlua.modules.events.service;

import dev.mlua.core.signal.MLuaSignal;

/** Owns the built-in event signals and their public Lua descriptions. */
final class MLuaBuiltInEventSignals {
    private MLuaBuiltInEventSignals() { }

    static final MLuaSignal SERVER_TICK = new MLuaSignal();
    static final MLuaSignal CLIENT_TICK = new MLuaSignal();
    static final MLuaSignal RENDER_STEPPED = new MLuaSignal();
    static final MLuaSignal PRE_RENDER = new MLuaSignal();
    static final MLuaSignal POST_RENDER = new MLuaSignal();
    static final MLuaSignal ENTITY_HURT = new MLuaSignal();
    static final MLuaSignal ENTITY_DEATH = new MLuaSignal();
    static final MLuaSignal BLOCK_BREAK = new MLuaSignal();
    static final MLuaSignal BLOCK_PLACE = new MLuaSignal();
    static final MLuaSignal ITEM_USE = new MLuaSignal();
    static final MLuaSignal ENTITY_INTERACT = new MLuaSignal();
    static final MLuaSignal PLAYER_RESPAWN = new MLuaSignal();
    static final MLuaSignal ENTITY_SPAWN = new MLuaSignal();
    static final MLuaSignal ENTITY_UNLOAD = new MLuaSignal();
    static final MLuaSignal WORLD_LOAD = new MLuaSignal();
    static final MLuaSignal WORLD_UNLOAD = new MLuaSignal();
    static final MLuaSignal BLOCK_RIGHT_CLICK = new MLuaSignal();
    static final MLuaSignal ITEM_DROP = new MLuaSignal();
    static final MLuaSignal ITEM_PICKUP = new MLuaSignal();
    static final MLuaSignal ITEM_CRAFT = new MLuaSignal();
    static final MLuaSignal CONTAINER_OPEN = new MLuaSignal();
    static final MLuaSignal CONTAINER_CLOSE = new MLuaSignal();
    static final MLuaSignal PROJECTILE_HIT = new MLuaSignal();
    static final MLuaSignal EXPLOSION = new MLuaSignal();
    static final MLuaSignal ADVANCEMENT = new MLuaSignal();

    static {
      MLuaEventSignalRegistry.registerBuiltIn("ServerTick", SERVER_TICK, "Fires after the server tick with its delta time in seconds.");
      MLuaEventSignalRegistry.registerBuiltIn("ClientTick", CLIENT_TICK, "Fires after the client tick with its delta time in seconds.");
      MLuaEventSignalRegistry.registerBuiltIn("RenderStepped", RENDER_STEPPED, "Fires at the start of each client render tick, before Minecraft draws the frame, with the frame delta time in seconds.");
      MLuaEventSignalRegistry.registerBuiltIn("PreRender", PRE_RENDER, "Fires at the start of each client render tick, immediately before RenderStepped, with the frame duration in seconds.");
      MLuaEventSignalRegistry.registerBuiltIn("PostRender", POST_RENDER, "Fires at the end of each client render tick, after Minecraft draws the frame, with that frame's duration in seconds.");
      MLuaEventSignalRegistry.registerBuiltIn("EntityHurt", ENTITY_HURT, "Fires on the server after the entity passes its invulnerability check and before armor and health mitigation. Amount is the damage value at the loader hook, not final health loss; earlier processing such as shield blocking may already have changed it. Set Cancel to prevent health damage.");
      MLuaEventSignalRegistry.registerBuiltIn("EntityDeath", ENTITY_DEATH, "Fires after a living entity dies on the server.");
      MLuaEventSignalRegistry.registerBuiltIn("BlockBreak", BLOCK_BREAK, "Fires before a player breaks a block; set Cancel to prevent the break.");
      MLuaEventSignalRegistry.registerBuiltIn("BlockPlace", BLOCK_PLACE, "Fires after an entity places a block on the server.");
      MLuaEventSignalRegistry.registerBuiltIn("ItemUse", ITEM_USE, "Fires when a player uses an item; set Cancel to prevent the use.");
      MLuaEventSignalRegistry.registerBuiltIn("EntityInteract", ENTITY_INTERACT, "Fires when a player interacts with a living entity.");
      MLuaEventSignalRegistry.registerBuiltIn("PlayerRespawn", PLAYER_RESPAWN, "Fires after a player respawns on the server.");
      MLuaEventSignalRegistry.registerBuiltIn("EntitySpawn", ENTITY_SPAWN, "Fires when an entity is added to a server world.");
      MLuaEventSignalRegistry.registerBuiltIn("EntityUnload", ENTITY_UNLOAD, "Fires on the client when an entity leaves the loaded client world, including chunk unloads and dimension changes.");
      MLuaEventSignalRegistry.registerBuiltIn("WorldLoad", WORLD_LOAD, "Fires when a server or client world becomes available. The event contains Level, Dimension, and Side.");
      MLuaEventSignalRegistry.registerBuiltIn("WorldUnload", WORLD_UNLOAD, "Fires when a server or client world is removed. The event contains Level, Dimension, and Side.");
      MLuaEventSignalRegistry.registerBuiltIn("BlockRightClick", BLOCK_RIGHT_CLICK, "Fires when a player uses an item on a block.");
      MLuaEventSignalRegistry.registerBuiltIn("ItemDrop", ITEM_DROP, "Fires when a player drops an item stack.");
      MLuaEventSignalRegistry.registerBuiltIn("ItemPickup", ITEM_PICKUP, "Fires after a player picks up items. The Item field contains a copy of the stack amount actually transferred, including partial pickups.");
      MLuaEventSignalRegistry.registerBuiltIn("ItemCraft", ITEM_CRAFT, "Fires when a player crafts an item stack.");
      MLuaEventSignalRegistry.registerBuiltIn("ContainerOpen", CONTAINER_OPEN, "Fires after a player opens a menu. The event table contains a live Container wrapper with slot mutation enabled while that menu remains open; retained wrappers become read-only after it closes or is replaced.");
      MLuaEventSignalRegistry.registerBuiltIn("ContainerClose", CONTAINER_CLOSE, "Fires while a player closes a menu. The event table contains a read-only Container wrapper for inspecting final slot contents.");
      MLuaEventSignalRegistry.registerBuiltIn("ProjectileHit", PROJECTILE_HIT, "Fires when a projectile collides with a block or entity.");
      MLuaEventSignalRegistry.registerBuiltIn("Explosion", EXPLOSION, "Fires when a server explosion starts, with its level and center position.");
      MLuaEventSignalRegistry.registerBuiltIn("AdvancementCompleted", ADVANCEMENT, "Fires when a player completes an advancement.");
    }

}
