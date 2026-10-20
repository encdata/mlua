package dev.mlua.modules.players;

import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.MLuaContextView;
import dev.mlua.common.enums.Side;
import dev.mlua.loaderapi.EventBridge;
import dev.mlua.loaderapi.LoaderApi;
import dev.mlua.modules.players.service.MLuaPlayersService;
import dev.mlua.modules.players.classes.MLuaEntityWrapper;
import dev.mlua.runtime.data.MLuaDataManager;
import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaRegistration;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;

public class PlayersModule implements MLuaModule {
    private final MLuaPlayerJoinDispatcher playerJoinDispatcher = new MLuaPlayerJoinDispatcher();

    @Override
    public void clearRuntimeState(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side) {
        MLuaEntityWrapper.clearCache(runtime);
    }

    @Override
    public void onRuntimeTick(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side, double deltaTime) {
        MLuaEntityWrapper.cleanCache();
    }

    @Override public void onReload() {
        this.playerJoinDispatcher.clear();
        clearState();
    }
    @Override public void onReload(dev.mlua.common.enums.Side side) {
        if (side == dev.mlua.common.enums.Side.SERVER || side == dev.mlua.common.enums.Side.BOTH) {
            this.playerJoinDispatcher.clear();
        }
        if (side == dev.mlua.common.enums.Side.CLIENT || side == dev.mlua.common.enums.Side.BOTH) {
            MLuaDataManager.clearClientCache();
        }
    }
    @Override public void onUnload() {
        this.playerJoinDispatcher.clear();
        clearState();
    }

    private static void clearState() {
        MLuaDataManager.clearClientCache();
    }
    @Override
    public String id() { return "players"; }

    @Override
    public List<String> dependencies() { return List.of("core-classes", "effects"); }

    @Override
    public int priority() { return 10; }

    @Override
    public List<ServiceEntry> services() {
        ServiceEntry.Builder players = ServiceEntry.builder("Players", MLuaPlayersService::new)
            .reflective(MLuaPlayersService.class);
        return List.of(players.build());
    }

    @Override
    public void registerClasses(MLuaRegistration registration) {
        MLuaEntityClassRegistration.register(registration);
        MLuaPlayerClassRegistration.register(registration);
    }

    @Override
    public void contributeGlobals(Globals globals, MLuaContextView context) {
        globals.set("Entity", context.registration().classTable("Entity"));
        globals.set("LivingEntity", context.registration().classTable("LivingEntity"));
        globals.set("Mob", context.registration().classTable("Mob"));
        globals.set("Player", context.registration().classTable("Player"));
    }

    @Override
    public void registerEvents(EventBridge events) {
        events.onPlayerJoin(this.playerJoinDispatcher::enqueue);
        events.onServerTickEnd(this.playerJoinDispatcher::dispatchPending);

        events.onPlayerLeave(playerObj -> {
            ServerPlayer player = (ServerPlayer) playerObj;
            var session = MLuaRuntime.sessionForLevel(player.level());
            dev.mlua.modules.events.EventsModule.forgetPlayerStepPositions(player.getUUID(), session);
            var registration = MLuaRuntime.registrationForLevel(player.level());
            MLuaPlayersService.PLAYER_REMOVING.fireInlineOnSession(session, Side.SERVER, MLuaPlayersService.playerTable(player, registration));
            MLuaEntityWrapper.removeFromCache(player);
        });

        if (LoaderApi.platform().isClient()) {
            events.onClientDisconnect(() -> {
                MLuaDataManager.clearClientCache();
                MLuaEntityWrapper.clearSideCache(Side.CLIENT);
            });
        }
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.register(MLuaDocBuilder.Registration.signature("LivingEntity", "LivingEntity:GiveEffect(effect: string|table, duration: whole number?, amplifier: whole number?, ambient: boolean?, showParticles: boolean?, showIcon: boolean?)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:GetInventory()", "Container"));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:GetOpenContainer()", "Container"));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:GetEnderChest()", "Container"));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:SendMessage(message: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:SendActionBar(message: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:SendTitle(title: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:GiveItem(item: Item)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:GiveEffect(effect: string|table, duration: whole number?, amplifier: whole number?, ambient: boolean?, showParticles: boolean?, showIcon: boolean?)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:GiveExperience(amount: number)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:GiveExperienceLevels(levels: number)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:SetGameMode(mode: Enum.GameMode|string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:SetPosition(position: Vector3|CFrame)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:Teleport(position: Vector3|CFrame)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:LookAt(target: Vector3|CFrame|Entity)", null));
        docs.register(MLuaDocBuilder.Registration.method("Entity", "Entity:Attack()", "Requests a normal local-player attack against this entity through Minecraft's active game mode. The server remains authoritative and validates the hit.", null, "boolean", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("Entity", "Entity:attack()", "Requests a normal local-player attack against this entity through Minecraft's active game mode. The server remains authoritative and validates the hit.", null, "boolean", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("Player", "Player:Jump()", "Requests a local-player jump through Minecraft's normal movement logic.", null, "void", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("Player", "Player:SetLocalGameMode(mode: Enum.GameMode|string)", "Changes the game mode this client assigns to its local player, making the client behave as though the player were in that mode. This does not change the server's mode or grant server permissions; the server may overwrite the local mode.", "mode", "void", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("Player", "Player:ResetLocalGameMode()", "Restores the local client's game mode to the latest mode reported by the server. Errors if that mode is unavailable.", null, "void", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("Player", "Player:CanFly()", "Returns the local player's may-fly ability flag.", null, "boolean", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("Player", "Player:SetCanFly(allowed: boolean)", "Changes the local may-fly ability flag. Turning it off also stops local flight. In multiplayer, server ability updates can replace this value.", "allowed", "void", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("Player", "Player:IsFlying()", "Returns the local player's flying flag.", null, "boolean", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("Player", "Player:SetFlying(flying: boolean)", "Changes the local flying flag. To enable it outside a flight-enabled game mode, call SetCanFly(true) first. A server may replace both flags.", "flying", "void", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("Player", "Player:SetSprinting(sprinting: boolean)", "Sets the local sprint input state; normal game and server movement rules still apply.", "sprinting", "void", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("Player", "Player:Turn(mouseDelta: Vector2)", "Applies raw horizontal and vertical mouse-turn deltas through Minecraft's normal player turn method. This is a low-level input primitive; it does not select or track a target.", "Vector2 mouse delta", "void", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:Turn(mouseDeltaX: number, mouseDeltaY: number)", "void"));
        docs.register(MLuaDocBuilder.Registration.method("Player", "Player:SwingArm(hand?: string)", "Starts the local arm swing for 'main' or 'off'; defaults to 'main'.", "hand", "void", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("Player", "Player:AttackEntity(entity: Entity)", "Sends a normal local attack request for this entity and returns true when submitted. Minecraft's game mode and server validate the action.", "entity", "boolean", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("Player", "Player:UseItem(hand?: string)", "Uses the item in 'main' or 'off'; defaults to 'main'.", "hand", "void", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("Player", "Player:SendChatMessage(message: string)", "Submits each non-empty line through Minecraft's normal local-player chat path. This reaches the connected server when multiplayer is active; use SendMessage to display local text instead. Returns the number of lines submitted.", "message", "number", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:GetRotationRadians()", "Vector2"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:SetRotationRadians(rotation: Vector2)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:SetCFrame(transform: CFrame)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:AttachModel(model: RenderObject)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:DetachModel(model: RenderObject)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.method("Entity", "Entity:AttachModel(model: RenderObject)",
            "Attaches a live Mesh or BBModel RenderObject owned by this runtime to this entity. The object follows the entity and participates in its model render pass.",
            "A live Mesh or BBModel RenderObject", "boolean", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("Entity", "Entity:DetachModel(model: RenderObject)",
            "Detaches this runtime's model from this entity and changes it to the Manual target, so it stops rendering automatically. Returns false if the model is not attached to this entity; submit it later with RenderContext:DrawObject or assign another target.",
            "A live Mesh or BBModel RenderObject", "boolean", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:LoadAnimation(animation: Animation|KeyframeDefinition[]|RenderObject)", "AnimationTrack|RenderObject"));
        docs.register(MLuaDocBuilder.Registration.method("Entity", "Entity:LoadAnimation(animation: Animation|KeyframeDefinition[])",
            "Creates a controllable animation track for this entity. A RenderObject argument still attaches its model for compatibility; prefer AttachModel for model attachment. Call AnimationTrack:Play() to start a track.",
            "Animation instance or keyframe array", "AnimationTrack|RenderObject", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:PlaySound(sound: string, volume: number?, pitch: number?)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:SetData(key: string, value: any)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:GetData(key: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:RemoveData(key: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:SetSlot(slot: number, item: Item)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:SetHotbarSlot(slot: number, item: Item)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:SetArmorSlot(slot: number, item: Item)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:SetOffhandSlot(item: Item)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:SetInventory(items: Item[])", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:SetViewMode(mode: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:HasPermission(level: number)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:DistanceTo(target: Vector3|CFrame|Entity)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:GetNearbyPlayers(radius: number)", "Player[]"));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:HasEffect(effect: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:RemoveEffect(effect: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:SetFood(food: number)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:SetFoodLevel(level: number)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:SetSaturation(saturation: number)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:CauseFoodExhaustion(amount: number)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:SetHealth(health: number)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:SetSelectedSlot(slot: number)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Players", "Players:GetLocalPlayer()", "Player?"));
        docs.register(MLuaDocBuilder.Registration.signature("Players", "Players:GetPlayers()", "Player[]"));
        docs.register(MLuaDocBuilder.Registration.signature("Players", "Players:GetPlayerByName(name: string)", "Player?"));
        docs.register(MLuaDocBuilder.Registration.signature("Players", "Players:GetPlayerByUUID(uuid: string)", "Player?"));
        docs.register(MLuaDocBuilder.Registration.signature("Players", "Players:GetPlayerById(id: number)", "Player?"));
        docs.register(MLuaDocBuilder.Registration.signature("Players", "Players:GetPlayerCount()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("Players", "Players:GetMaxPlayers()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("Players", "Players:Broadcast(message: string)", "void"));
        docs.register(MLuaDocBuilder.Registration.service("Players", "MLuaPlayersService",
                "Manages player connections and provides player instances.",
                "services/players.html"));
        docs.register(MLuaDocBuilder.Registration.property("Players", "Players.LocalPlayer", "Player",
            "The local player on the client; nil on the server.", "client:Client"));
        docs.register(MLuaDocBuilder.Registration.event("Players", "Players.PlayerAdded", "player: Player",
            "Fires when a player joins the game. Server listeners run synchronously on the server thread, so world changes finish before the listener returns.",
            "server:Server,client:Client"));
        docs.register(MLuaDocBuilder.Registration.event("Players", "Players.PlayerRemoving", "player: Player",
            "Fires when a player leaves the game. Listeners run synchronously on the server thread.", "server:Server"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Players", "Find a player", "Look up a connected player by exact name, then use the live Player instance.",
            "local Players = Minecraft:GetService(\"Players\")\n" +
            "local player = Players:GetPlayerByName(\"Alex\")\n" +
            "if player then\n" +
            "    player:SendMessage(\"Welcome!\")\n" +
            "end"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Players", "Script mob navigation", "Mob:NavigateTo uses Minecraft's native path navigation. Call it from server-side entity behavior, then check the path before choosing another destination.",
            "local function updatePatrol(mob, waypoint)\n" +
            "    if mob:IsNavigationDone() then\n" +
            "        mob:NavigateTo(waypoint, 1.1)\n" +
            "    end\n" +
            "end\n\n" +
            "local waypoint = Vector3.new(24, 64, -12)\n" +
            "updatePatrol(scriptedMob, waypoint)"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Players", "Apply server-side damage", "LivingEntity:Damage uses Minecraft's damage pipeline and returns true only when the target accepted the hit. Pass an attacker to preserve kill credit and damage attribution.",
            "local accepted = target:Damage(6, \"generic\", attacker)\n" +
            "if accepted then\n" +
            "    attacker:SendMessage(\"Hit confirmed\")\n" +
            "end\n\n" +
            "target:Heal(4)"));
        docs.register(MLuaDocBuilder.Registration.clazz("Object", "LuaTable",
                "Root class in MLua's Lua class hierarchy.",
                "classes/object.html", null));
        docs.register(MLuaDocBuilder.Registration.clazz("Instance", "LuaTable",
                "Base class for live wrappers around Minecraft objects.",
                "classes/instance.html", "Object"));
        docs.register(MLuaDocBuilder.Registration.clazz("Entity", "MLuaEntityWrapper",
                "A live entity instance with position, movement, health, and interaction APIs.",
                "classes/entity.html", "Instance"));
        docs.register(MLuaDocBuilder.Registration.clazz("LivingEntity", "MLuaEntityWrapper",
                "A living entity with health, damage, equipment, and attributes.",
                "classes/living-entity.html", "Entity"));
        docs.register(MLuaDocBuilder.Registration.clazz("Mob", "MLuaEntityWrapper",
                "A Minecraft mob with AI targeting, visibility checks, and persistence controls.",
                "classes/mob.html", "LivingEntity"));
        docs.register(MLuaDocBuilder.Registration.property("Mob", "mob.Target", "LivingEntity|nil", "This mob's current AI target. Readable and writable.", "server:Server"));
        docs.register(MLuaDocBuilder.Registration.property("Mob", "mob.PersistenceRequired", "boolean", "Whether this mob is marked to persist without despawning.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.Name", "string", "The entity's current name.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.UUID", "string", "The entity's UUID.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.Level", "Level", "The lightweight live dimension wrapper containing this entity.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.World", "Level", "Alias for Level. Use Minecraft:GetService(\"World\"):Get(entity.Dimension) to access the full world service instance.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.Nbt", "Nbt|table", "The entity's persistent data. Server-side only; assigning a table or Nbt value replaces it.", "server:Server"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.Position", "Vector3", "The entity's current position. Readable and writable.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.Velocity", "Vector3", "The entity's current velocity. Readable and writable.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.Rotation", "Vector2", "The entity's pitch and yaw. Readable and writable.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.IsAlive", "boolean", "Whether the entity is alive.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.IsOnGround", "boolean", "Whether the entity is on the ground.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.IsInWater", "boolean", "Whether the entity is in water.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.IsOnFire", "boolean", "Whether the entity is on fire.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.IsMoving", "boolean", "Whether the entity's total velocity exceeds 0.05 blocks per tick.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.IsFalling", "boolean", "Whether the entity is airborne and moving downward faster than 0.08 blocks per tick.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.CustomName", "string|nil", "The entity's custom name. Readable and writable.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.CustomNameVisible", "boolean", "Whether the custom name is shown above this entity. Readable and writable.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.Glowing", "boolean", "Whether this entity has a glowing outline. Readable and writable.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.NoGravity", "boolean", "Whether gravity is disabled. Readable and writable.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.Invulnerable", "boolean", "Whether this entity is invulnerable. Readable and writable.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.Silent", "boolean", "Whether this entity is silent. Readable and writable.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.FireTicks", "number", "Remaining fire duration in ticks. Readable and writable.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.EyePosition", "Vector3", "This entity's eye position.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.Width", "number", "This entity's bounding-box width.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.Height", "number", "This entity's bounding-box height.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.EyeHeight", "number", "This entity's eye height above its position.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.Vehicle", "Entity|nil", "The entity this entity is riding, or nil.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.RootVehicle", "Entity", "The topmost vehicle in this entity's riding chain.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.Passengers", "Entity[]", "Entities currently riding this entity.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.IsPassenger", "boolean", "Whether this entity is riding another entity.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.IsVehicle", "boolean", "Whether this entity has passengers.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.IsInLava", "boolean", "Whether this entity is in lava.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.IsUnderWater", "boolean", "Whether this entity is fully underwater.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.FallDistance", "number", "The entity's current fall distance. Readable and writable.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("LivingEntity", "entity.Health", "number", "The entity's current health. Readable and writable.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("LivingEntity", "entity.MaxHealth", "number", "The entity's maximum health.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("LivingEntity", "entity.ActiveEffects", "table[]", "Active potion effects with identifier, duration, amplifier, ambient, and visibility fields.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("LivingEntity", "entity.Armor", "number", "The entity's armor value.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("LivingEntity", "entity.Absorption", "number", "The entity's current absorption health. Readable and writable.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("LivingEntity", "entity.IsJumping", "boolean", "Whether the entity is airborne and moving upward faster than 0.08 blocks per tick.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("LivingEntity", "entity.IsSwimming", "boolean", "Whether Minecraft currently considers the entity to be swimming.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.clazz("Player", "MLuaPlayerWrapper",
                "A live player instance with player identity, inventory, messaging, and player controls.",
                "classes/player.html", "LivingEntity"));
        docs.register(MLuaDocBuilder.Registration.property("Player", "player.Name", "string", "The player's username.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Player", "player.UUID", "string", "The player's UUID.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Player", "player.Nbt", "Nbt|table", "The player's persistent data. Server-side only; assigning a table or Nbt value replaces it.", "server:Server"));
        docs.register(MLuaDocBuilder.Registration.property("Player", "player.Position", "Vector3", "The player's current position. Readable and writable.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Player", "player.Health", "number", "The player's current health. Readable and writable.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Player", "player.MaxHealth", "number", "The player's maximum health.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Player", "player.FoodLevel", "number", "The player's current food level. Readable and writable.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Player", "player.Food", "number", "Compatibility alias for player.FoodLevel. Readable and writable.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Player", "player.Saturation", "number", "The player's current food saturation. Readable and writable.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Player", "player.Velocity", "Vector3", "The player's current velocity. Readable and writable.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Player", "player.Rotation", "Vector2", "The player's pitch and yaw. Readable and writable.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Player", "player.ViewMode", "ViewMode", "The local player's camera perspective. Readable and writable.", "client:Client"));
        docs.register(MLuaDocBuilder.Registration.property("Player", "player.MayFly", "boolean", "The local client's may-fly flag. Readable and writable on the client; a server ability update can replace it.", "client:Client"));
        docs.register(MLuaDocBuilder.Registration.property("Player", "player.Flying", "boolean", "The local client's flying flag. Readable and writable on the client; a server ability update can replace it.", "client:Client"));
        docs.register(MLuaDocBuilder.Registration.method("Player", "Player:GetGameMode()", "Returns the mode this Player instance currently treats as active: the locally assigned mode for the local client player, the server-reported mode for remote client players, and the authoritative mode on the server.", null, "GameMode|string", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Player", "Player:GetServerGameMode()", "Returns the mode reported by the server. For a local client player this can differ from GetGameMode() after SetLocalGameMode().", null, "GameMode|string", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.property("Player", "player.GameMode", "GameMode|string", "The mode this Player instance currently treats as active. Local client player: mode assigned locally and used by client-side behavior. Remote client player: mode reported by the server. Server-side player: authoritative mode.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Player", "player.ServerGameMode", "GameMode|string", "The mode reported by the server. For the local client player this may differ from GameMode after SetLocalGameMode().", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Player", "player.SelectedSlot", "number", "The selected hotbar slot using a one-based index.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Player", "player.Armor", "number", "The player's current armor value.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Player", "player.IsAlive", "boolean", "Whether the player is alive.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Player", "player.IsOnGround", "boolean", "Whether the player is on the ground.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Player", "player.IsInWater", "boolean", "Whether the player is in water.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Player", "player.IsOnFire", "boolean", "Whether the player is on fire.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Player", "player.IsMoving", "boolean", "Whether the player's total velocity exceeds 0.05 blocks per tick.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Player", "player.IsFalling", "boolean", "Whether the player is airborne and moving downward faster than 0.08 blocks per tick.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Player", "player.IsJumping", "boolean", "Whether the player is airborne and moving upward faster than 0.08 blocks per tick.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Player", "player.IsSwimming", "boolean", "Whether Minecraft currently considers the player to be swimming.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Player", "Use a live player instance", "Player values returned by the Players service stay connected to the live entity.",
            "local Players = Minecraft:GetService(\"Players\")\n" +
            "local player = Players:GetLocalPlayer()\n" +
            "if player then\n" +
            "    print(player.Name, player.Position)\n" +
            "    player:SendMessage(\"Hello!\")\n" +
            "end"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Player", "Change local game mode and flight",
            "These methods change the client's local game-mode and ability state. A multiplayer server can replace that state with its authoritative values.",
            "local player = Minecraft:GetService(\"Players\"):GetLocalPlayer()\n" +
            "if player then\n" +
            "    player:SetLocalGameMode(Enum.GameMode.Creative)\n" +
            "    player:SetCanFly(true)\n" +
            "    player:SetFlying(true)\n" +
            "    player:ResetLocalGameMode() -- restore the latest server-reported mode\n" +
            "end"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Player", "Charge food exhaustion for a custom action",
            "CauseFoodExhaustion adds to Minecraft's normal exhaustion meter, so custom sprinting, heavy attacks, or tools can share the vanilla hunger system. Call this helper from a server-side action callback.",
            "local function spendStamina(player, exhaustion)\n    player:CauseFoodExhaustion(exhaustion)\nend"));
    }
}
