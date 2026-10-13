package dev.mlua.modules.chat;

import dev.mlua.module.MLuaDocBuilder;

import dev.mlua.loaderapi.EventBridge;
import dev.mlua.modules.chat.Service.MLuaChatService;
import dev.mlua.modules.players.Service.MLuaPlayersService;
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
        docs.signature("Chat", "Chat:Broadcast(message: string)", null);
        docs.signature("Chat", "Chat:SendMessage(target: string, message: string)", null);
        docs.signature("Chat", "Chat:MutePlayer(name: string, duration?: number)", "Duration is in seconds; omitted or non-positive means permanent.");
        docs.signature("Chat", "Chat:UnmutePlayer(name: string)", null);
        docs.signature("Chat", "Chat:IsMuted(name: string)", null);
        docs.signature("Chat", "Chat:GetMutedPlayers()", null);
        docs.signature("Chat", "Chat:GetMessageHistory(player: string, count?: number)", "Count defaults to 10 messages.");
        docs.signature("Chat", "Chat:ClearChat(player: string)", null);
        docs.signature("Chat", "Chat:GetOnlinePlayers()", null);
        docs.signature("Chat", "Chat:BroadcastToPermission(permission: string, message: string)", null);
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
            .description("Server chat messaging, moderation, history, and message events.");
        chat.function("Broadcast", "Sends a chat message to every online player.")
            .function("SendMessage", "Sends a chat message to one online player by name.")
            .function("MutePlayer", "Prevents a player from sending chat; duration is in seconds, or omitted for a permanent mute.")
            .function("UnmutePlayer", "Allows a muted player to send chat again.")
            .function("IsMuted", "Returns whether a player is currently muted.")
            .function("GetMutedPlayers", "Returns the names of currently muted players.")
            .function("GetMessageHistory", "Returns recent recorded chat messages for a player.")
            .function("ClearChat", "Clears a player's visible chat by sending blank lines.")
            .function("GetOnlinePlayers", "Returns the names of online players.")
            .function("BroadcastToPermission", "Sends a message to server operators, or to everyone when permission is empty.");
        return List.of(chat.build());
    }

    @Override
    public void registerEvents(EventBridge events) {
        events.onServerChat((playerObj, message) -> {
            ServerPlayer player = (ServerPlayer) playerObj;
            String username = player.getName().getString();
            String originalMessage = message;
            MLuaRuntime runtime = MLuaRuntime.get();
            MLuaRuntimeSession session = MLuaRuntimeSession.forServer(player.level().getServer());
            if (session == null) session = runtime.session();
            Object runtimeOwner = session;

            if (MLuaChatService.isPlayerMuted(runtimeOwner, username)) {
                player.sendSystemMessage(Component.literal("You are muted and cannot send chat messages."));
                return EventBridge.ChatResult.cancelled();
            }

            MLuaChatService.addMessageToHistory(runtimeOwner, username, originalMessage);

            LuaTable args = new LuaTable();
            var registration = session.registration() != null ? session.registration() : runtime;
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
