package dev.mlua.modules.players;

import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.module.MLuaRegistration;
import java.util.List;

/** Registers the Player class methods, properties, and documented client controls. */
final class MLuaPlayerClassRegistration {
    private MLuaPlayerClassRegistration() {}

    static void register(MLuaRegistration registration) {
        registration.registerClass("Player", "LivingEntity",
            "A live player instance with player identity, inventory, messaging, and player controls.");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "SendMessage",
            "Sends a chat message to this player. On the client, displays the message locally.");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "SendChatMessage",
            "Submits chat through the local client's normal chat path.");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "GetDisplayName",
            "Returns this player's display name.");
        MLuaClassMethods.registerInstanceProperty(registration, "Player", "DisplayName", "GetDisplayName", null,
            "string", "shared:Shared", "This player's display name.");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "SendActionBar",
            "Sends an action bar message to this player.");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "SendTitle",
            "Displays a title to this player.");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "GetGameMode",
            "Returns the game mode visible to this Player instance. The local client player reports the locally selected mode; server-side players report authoritative mode; remote client players report the mode received from the server.");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "GetServerGameMode",
            "Returns the mode reported by the server. On the server this is authoritative; on a client it is the server-reported mode even when the local player's selected mode differs.");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "SetLocalGameMode",
            "Changes the game mode this client assigns to its local player, making the client behave as though the player were in that mode. This is a local client-side override: it does not change the server's mode or grant server permissions, and the server may overwrite it.");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "ResetLocalGameMode",
            "Restores the local client's game mode to the latest mode reported by the server.");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "CanFly",
            "Returns whether the local player has the may-fly ability flag.");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "SetCanFly",
            "Changes the local may-fly ability flag. In multiplayer, server ability updates can replace this value.");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "IsFlying",
            "Returns whether the local player currently has the flying flag set.");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "SetFlying",
            "Changes the local flying flag. A server may replace this value.");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "Jump",
            "Requests a jump from the local player using Minecraft's normal movement rules.");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "SetSprinting",
            "Sets the local player's sprint state; Minecraft and the server still apply their normal movement rules.");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "Turn",
            "Applies raw mouse-turn deltas to the local player view using Minecraft's normal turn method.");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "SwingArm",
            "Plays the local player's arm swing for the selected hand.");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "AttackEntity",
            "Requests a normal Minecraft attack against an entity and returns true when submitted. The game mode and server validate the hit.");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "UseItem",
            "Requests the normal item-use action for the selected hand.");
        MLuaClassMethods.registerInstanceProperty(registration, "Player", "GameMode", "GetGameMode", null,
            "GameMode", "shared:Shared", "The mode this Player instance currently treats as active. For the local client player it is the mode assigned locally and used by client-side behavior; on the server it is authoritative, and for remote client players it is server-reported.");
        MLuaClassMethods.registerInstanceProperty(registration, "Player", "ServerGameMode", "GetServerGameMode", null,
            "GameMode|string", "shared:Shared", "The mode reported by the server. For the local client player this may differ from GameMode after SetLocalGameMode().");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "SetGameMode",
            "Changes this player's game mode. This method is server-side only.");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "GetInventory",
            "Returns this player's live Inventory instance.");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "GetEnderChest",
            "Returns this player's live ender chest as a Container instance.");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "GetOpenContainer",
            "Returns the live Container instance for the menu this player currently has open. Writes stop working when the menu closes or is replaced.");
        MLuaClassMethods.registerInstanceProperty(registration, "Player", "Inventory", "GetInventory", null,
            "Inventory", "shared:Shared", "Returns this player's live Inventory instance.");
        MLuaClassMethods.registerInstanceProperty(registration, "Player", "EnderChest", "GetEnderChest", null,
            "Container", "shared:Shared", "Returns this player's live ender chest as a Container instance.");
        MLuaClassMethods.registerInstanceProperty(registration, "Player", "OpenContainer", "GetOpenContainer", null,
            "Container", "shared:Shared", "Returns the live Container instance for the menu this player currently has open. Writes stop working when the menu closes or is replaced.");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "GetFood",
            "Returns this player's food level.");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "SetFood",
            "Sets this player's food level to a whole number from 0 to 20.");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "GetFoodLevel",
            "Returns this player's food level.");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "SetFoodLevel",
            "Sets this player's food level to a whole number from 0 to 20.");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "GetSaturation",
            "Returns this player's food saturation.");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "SetSaturation",
            "Sets this player's food saturation to a finite, non-negative value.");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "CauseFoodExhaustion",
            "Adds food exhaustion to this player; server-side only for authoritative changes.");
        for (String method : List.of("GetDisplayName", "SendMessage", "SendTitle", "SendActionBar",
                "GetEyeCFrame", "GetCameraCFrame", "GiveItem", "ClearInventory", "GiveEffect",
                "RemoveEffect", "HasEffect", "ClearEffects", "PlaySound",
                "GetNearbyPlayers", "GetPersistentData", "GetSyncedData",
                "SetData", "GetData", "RemoveData", "HasPermission", "IsOperator")) {
            String description = switch (method) {
                case "GetDisplayName" -> "Returns this player's display name.";
                case "SendMessage" -> "Sends a chat message to this player.";
                case "SendTitle" -> "Sends a title to this player's screen.";
                case "SendActionBar" -> "Sends an action bar message to this player.";
                case "GetEyeCFrame" -> "Returns the player's eye position and view rotation.";
                case "GetCameraCFrame" -> "Returns the local player's camera transform; client-side only.";
                case "GiveItem" -> "Gives an item to this player.";
                case "ClearInventory" -> "Clears this player's inventory.";
                case "GiveEffect" -> "Applies a potion effect to this player and returns whether Minecraft accepted it. Duration defaults to 600 ticks; amplifier is zero-based and defaults to 0.";
                case "RemoveEffect" -> "Removes a potion effect from this player.";
                case "HasEffect" -> "Checks whether this player has a potion effect.";
                case "ClearEffects" -> "Removes all potion effects from this player.";
                case "PlaySound" -> "Plays a sound for this player; optional volume and pitch must be finite and non-negative.";
                case "GetNearbyPlayers" -> "Returns other players within a box expanded around this player's bounds by a finite, non-negative radius.";
                case "GetPersistentData" -> "Returns this player's persistent data.";
                case "GetSyncedData" -> "Returns this player's synchronized data.";
                case "SetData" -> "Sets a value in this player's persistent data.";
                case "GetData" -> "Reads a value from this player's persistent data.";
                case "RemoveData" -> "Removes a value from this player's persistent data.";
                case "HasPermission" -> "Checks this player's permission level using a whole-number level.";
                default -> "Checks whether this player is a server operator.";
            };
            MLuaClassMethods.registerInstanceMethod(registration, "Player", method, description);
        }
        String[][] inventoryAndStateMethods = {
            {"GetExperienceLevel", "Returns this player's current experience level."},
            {"GetExperienceProgress", "Returns this player's progress toward the next experience level."},
            {"GiveExperience", "Adds a whole-number amount of experience points to this player; server-side only."},
            {"GiveExperienceLevels", "Adds a whole-number amount of experience levels to this player; server-side only."},
            {"IsOnline", "Whether this player is currently connected to or present in the client world, regardless of health."},
            {"IsCreative", "Whether this player is in Creative mode."},
            {"IsSpectator", "Whether this player is in Spectator mode."},
            {"IsSleeping", "Whether this player is sleeping."},
            {"IsCrouching", "Whether this player is crouching."},
            {"IsSprinting", "Whether this player is sprinting."},
            {"GetBlockPosition", "Returns this player's block-aligned position."},
            {"GetLookVector", "Returns the direction this player is looking."},
            {"GetSelectedSlot", "Returns the selected hotbar slot using a one-based integer index."},
            {"SetSelectedSlot", "Selects a hotbar slot using a whole-number index from 1 to 9. Client calls require the active local player and Minecraft's client thread; server calls require a server-side player."},
            {"GetHeldItem", "Returns the item in this player's main hand, or nil."},
            {"GetOffhandItem", "Returns the item in this player's offhand, or nil."},
            {"GetViewMode", "Returns the local player's view mode; client-side only."},
            {"SetViewMode", "Sets the local player's view mode. Client calls require the active local player and Minecraft's client thread."},
            {"GetSlot", "Returns a live inventory slot using a one-based whole-number index."},
            {"SetSlot", "Sets or clears an inventory slot using a one-based whole-number index."},
            {"SetHotbarSlot", "Sets or clears a hotbar slot using a one-based index from 1 to 9."},
            {"SetArmorSlot", "Sets or clears an armor slot using a one-based index from 1 to 4."},
            {"SetOffhandSlot", "Sets or clears this player's offhand item."},
            {"GetInventorySize", "Returns this player's inventory slot count."},
            {"GetHotbar", "Returns live Slot instances for the nine hotbar positions."},
            {"GetArmorContents", "Returns the same live Slot instances exposed by Inventory for the four armor positions."},
            {"GetOffhandSlot", "Returns the same live Slot instance exposed by Inventory for the offhand."},
            {"GetOpenContainerTitle", "Returns the title of this player's current menu."},
            {"GetOpenContainerSize", "Returns the slot count of this player's current menu."},
            {"SetInventory", "Validates the item table before replacing this player's inventory; server-side only, and omitted slots are cleared."},
            {"GetIsLocalPlayer", "Whether this Player instance represents the local client player."},
            {"GetEffects", "Returns ActivePotionEffect instances for the effects currently applied to this player."}
        };
        for (String[] method : inventoryAndStateMethods) {
            MLuaClassMethods.registerInstanceMethod(registration, "Player", method[0], method[1]);
        }
        MLuaClassMethods.registerInstanceProperty(registration, "Player", "IsLocalPlayer", "GetIsLocalPlayer", null,
            "boolean", "client:Client", "Whether this Player instance represents the local client player.");
        MLuaClassMethods.registerInstanceProperty(registration, "Player", "FoodLevel", "GetFoodLevel", "SetFoodLevel",
            "number", "shared:Shared", "This player's food level.");
        MLuaClassMethods.registerInstanceProperty(registration, "Player", "Food", "GetFood", "SetFood",
            "number", "shared:Shared", "Compatibility alias for FoodLevel.");
        MLuaClassMethods.registerInstanceProperty(registration, "Player", "Saturation", "GetSaturation", "SetSaturation",
            "number", "shared:Shared", "This player's food saturation.");
        MLuaClassMethods.registerInstanceProperty(registration, "Player", "ViewMode", "GetViewMode", "SetViewMode",
            "ViewMode", "client:Client", "The local player's view mode; client-side only.");
        MLuaClassMethods.registerInstanceProperty(registration, "Player", "MayFly", "CanFly", "SetCanFly",
            "boolean", "client:Client", "The local client's may-fly flag. Writing this property is client-side only.");
        MLuaClassMethods.registerInstanceProperty(registration, "Player", "Flying", "IsFlying", "SetFlying",
            "boolean", "client:Client", "The local client's flying flag. Writing this property is client-side only.");
        MLuaClassMethods.registerInstanceProperty(registration, "Player", "SelectedSlot", "GetSelectedSlot", null,
            "number", "shared:Shared", "The selected hotbar slot using a one-based index.");
    }
}
