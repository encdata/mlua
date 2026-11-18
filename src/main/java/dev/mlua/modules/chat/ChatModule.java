package dev.mlua.modules.chat;

import dev.mlua.module.MLuaDocBuilder;

import dev.mlua.loaderapi.EventBridge;
import dev.mlua.modules.chat.service.MLuaChatService;
import dev.mlua.modules.players.service.MLuaPlayersService;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.MLuaRuntimeSession;
import dev.mlua.common.enums.Side;
import dev.mlua.module.MLuaModule;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

public class ChatModule implements MLuaModule {
    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.register(MLuaDocBuilder.Registration.signature("Chat", "Chat:Broadcast(message: string)", "Sends a system chat message to every online player."));
        docs.register(MLuaDocBuilder.Registration.signature("Chat", "Chat:SendMessage(target: string, message: string)", "Sends a system chat message to one online player by their exact player name."));
        docs.register(MLuaDocBuilder.Registration.signature("Chat", "Chat:MutePlayer(name: string, duration?: number)", "Duration is in seconds; omitted or non-positive means permanent."));
        docs.register(MLuaDocBuilder.Registration.signature("Chat", "Chat:UnmutePlayer(name: string)", "Removes the player's mute."));
        docs.register(MLuaDocBuilder.Registration.signature("Chat", "Chat:IsMuted(name: string)", "Returns true while the player has an active mute."));
        docs.register(MLuaDocBuilder.Registration.signature("Chat", "Chat:GetMutedPlayers()", "Returns the names of players with active mutes; expired timed mutes are removed."));
        docs.register(MLuaDocBuilder.Registration.signature("Chat", "Chat:GetMessageHistory(player: string, count?: number)", "Returns up to 100 recent messages in chronological order; count defaults to 10."));
        docs.register(MLuaDocBuilder.Registration.signature("Chat", "Chat:ClearChat(player: string)", "Sends 100 blank lines to that player's chat; this only hides recent messages in the client view."));
        docs.register(MLuaDocBuilder.Registration.signature("Chat", "Chat:GetOnlinePlayers()", "Returns the current online player names, or an empty list when no server is available."));
        docs.register(MLuaDocBuilder.Registration.signature("Chat", "Chat:BroadcastToPermission(permission: string, message: string)", "An empty permission broadcasts to everyone; any non-empty value currently targets operators."));
        docs.register(MLuaDocBuilder.Registration.signature("Chat", "Chat:BroadcastToPermissionLevel(level: number, message: string)", "Sends to players with the vanilla permission level; level must be an integer from 0 to 4."));
        docs.register(MLuaDocBuilder.Registration.event("Chat", "Chat.OnMessage", "event: table",
            "Fires on the server before a player's message is broadcast. The event has Player, Username, UUID, and Message fields. Set Message to a new string to replace the message, or return false from a listener to cancel it.",
            "server:Server"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Chat", "Filter or rewrite chat messages",
            "Connect to OnMessage from a server script. The listener receives one event table; return false to block the message, or update Message to send different text. The original message is recorded in history before listeners run.",
            "local Chat = Minecraft:GetService(\"Chat\")\n\nChat.OnMessage:Connect(function(event)\n    if event.Message:find(\"spoiler\") then\n        return false\n    end\n\n    if event.Message:find(\"radio\") then\n        event.Message = \"[RADIO] \" .. event.Message\n    end\nend)"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Chat", "Mute a player and inspect recent messages",
            "Use GetOnlinePlayers to get names for SendMessage, MutePlayer, and the other player methods. MutePlayer duration is seconds; omit it or pass zero or a negative value for a permanent mute. GetMessageHistory returns up to 100 messages, oldest first; count defaults to 10. Muted messages are not added to history, while accepted messages are recorded before OnMessage listeners run.",
            "local Chat = Minecraft:GetService(\"Chat\")\nlocal players = Chat:GetOnlinePlayers()\nlocal name = players[1]\nif not name then return end\n\nChat:MutePlayer(name, 60)\nprint(\"muted:\", Chat:IsMuted(name))\nfor _, message in ipairs(Chat:GetMessageHistory(name, 10)) do\n    print(message)\nend\n\nChat:UnmutePlayer(name)\nChat:SendMessage(name, \"you can chat again\")"));
    }

    @Override
    public String id() { return "chat"; }

    @Override
    public Side side() { return Side.SERVER; }

    @Override
    public void clearRuntimeState(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side) {
        if (side == Side.CLIENT) return;
        MLuaChatService.clearRuntime(runtimeSession);
    }

    @Override
    public List<ServiceEntry> services() {
        ServiceEntry.Builder chat = ServiceEntry.builder("Chat", MLuaChatService::new)
            .reflective(MLuaChatService.class);
        return List.of(chat.build());
    }

    @Override
    public void registerEvents(EventBridge events) {
        events.onServerChat((playerObj, message) -> {
            ServerPlayer player = (ServerPlayer) playerObj;
            String username = player.getName().getString();
            String originalMessage = message;
            MLuaRuntimeSession session = MLuaRuntime.sessionForLevel(player.level());
            Object runtimeOwner = session;

            if (MLuaChatService.isPlayerMuted(runtimeOwner, username)) {
                player.sendSystemMessage(Component.literal("You are muted and cannot send chat messages."));
                return EventBridge.ChatResult.cancelled();
            }

            MLuaChatService.addMessageToHistory(runtimeOwner, username, originalMessage);

            LuaTable args = new LuaTable();
            var registration = MLuaRuntime.registrationForLevel(player.level());
            args.set("Player", MLuaPlayersService.playerTable(player, registration));
            args.set("Message", originalMessage);
            args.set("Username", username);
            args.set("UUID", player.getUUID().toString());

            boolean cancelled = MLuaChatService.onMessage(runtimeOwner).fireCancellableOnSession(session, Side.SERVER, args);

            if (cancelled) {
                return EventBridge.ChatResult.cancelled();
            }

            LuaValue modified = args.get("Message");
            if (modified.isstring()) {
                String newMessage = modified.tojstring();
                if (!newMessage.equals(originalMessage)) {
                    return EventBridge.ChatResult.replace(newMessage);
                }
            }
            return EventBridge.ChatResult.pass(originalMessage);
        });
    }
}
