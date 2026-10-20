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
        docs.register(MLuaDocBuilder.Registration.signature("Chat", "Chat:Broadcast(message: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Chat", "Chat:SendMessage(target: string, message: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Chat", "Chat:MutePlayer(name: string, duration?: number)", "Duration is in seconds; omitted or non-positive means permanent."));
        docs.register(MLuaDocBuilder.Registration.signature("Chat", "Chat:UnmutePlayer(name: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Chat", "Chat:IsMuted(name: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Chat", "Chat:GetMutedPlayers()", null));
        docs.register(MLuaDocBuilder.Registration.signature("Chat", "Chat:GetMessageHistory(player: string, count?: number)", "Returns up to 100 recent messages in chronological order; count defaults to 10."));
        docs.register(MLuaDocBuilder.Registration.signature("Chat", "Chat:ClearChat(player: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Chat", "Chat:GetOnlinePlayers()", null));
        docs.register(MLuaDocBuilder.Registration.signature("Chat", "Chat:BroadcastToPermission(permission: string, message: string)", "An empty permission broadcasts to everyone; any non-empty value currently targets operators."));
        docs.register(MLuaDocBuilder.Registration.signature("Chat", "Chat:BroadcastToPermissionLevel(level: number, message: string)", "Sends to players with the vanilla permission level; level must be an integer from 0 to 4."));
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
