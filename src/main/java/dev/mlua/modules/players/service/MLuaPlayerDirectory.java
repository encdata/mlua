package dev.mlua.modules.players.service;

import dev.mlua.common.enums.Side;
import dev.mlua.core.service.MLuaService;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import dev.mlua.modules.players.MLuaClientPlayerAccess;
import dev.mlua.modules.players.MLuaPlayerJoinScope;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Resolves live player instances across the client and server player lists. */
final class MLuaPlayerDirectory {
    private static final ClassValue<ClientPlayerAccess> CLIENT_ACCESS = new ClassValue<>() {
        @Override
        protected ClientPlayerAccess computeValue(Class<?> playerType) {
            return new ClientPlayerAccess(resolveWrapper(playerType),
                method(playerType, "getName"), method(playerType, "getUUID"), method(playerType, "getId"));
        }
    };
    private static final ClassValue<Method> COMPONENT_TEXT_ACCESS = new ClassValue<>() {
        @Override
        protected Method computeValue(Class<?> componentType) {
            return method(componentType, "getString");
        }
    };

    private final MLuaService.Context context;

    MLuaPlayerDirectory(MLuaService.Context context) {
        this.context = context;
    }

    LuaTable getPlayers() {
        LuaTable players = new LuaTable();
        int index = 1;
        if (isClientSide()) {
            for (Object player : clientPlayers()) players.set(index++, clientPlayerTable(player));
            return players;
        }

        if (context.server() != null) {
            for (ServerPlayer player : context.server().getPlayerList().getPlayers()) {
                players.set(index++, MLuaPlayersService.playerTable(player, context.registration()));
            }
        }
        ServerPlayer joiningPlayer = joiningPlayer();
        if (joiningPlayer != null && (context.server() == null
                || context.server().getPlayerList().getPlayer(joiningPlayer.getUUID()) != joiningPlayer)) {
            players.set(index, MLuaPlayersService.playerTable(joiningPlayer, context.registration()));
        }
        return players;
    }

    LuaValue getPlayerByName(String name) {
        requirePlayerName(name, "Players:GetPlayerByName");
        if (!isClientSide()) {
            ServerPlayer player = context.server() == null ? null : context.server().getPlayerList().getPlayerByName(name);
            if (player == null) {
                ServerPlayer joining = joiningPlayer();
                if (joining != null && joining.getName().getString().equals(name)) player = joining;
            }
            return serverPlayerValue(player);
        }
        for (Object player : clientPlayers()) {
            if (clientPlayerName(player).equals(name)) return clientPlayerTable(player);
        }
        return LuaValue.NIL;
    }

    LuaValue getPlayerByUUID(String value) {
        UUID uuid = MLuaLuaArguments.uuid(value, "Players:GetPlayerByUUID", "uuid");
        ServerPlayer player = isClientSide() || context.server() == null
            ? null : context.server().getPlayerList().getPlayer(uuid);
        if (player == null && !isClientSide()) {
            ServerPlayer joining = joiningPlayer();
            if (joining != null && joining.getUUID().equals(uuid)) player = joining;
        }
        if (player != null) return MLuaPlayersService.playerTable(player, context.registration());
        if (isClientSide()) {
            for (Object local : clientPlayers()) {
                if (uuid.equals(invoke(CLIENT_ACCESS.get(local.getClass()).uuid(), local, "getUUID"))) {
                    return clientPlayerTable(local);
                }
            }
        }
        return LuaValue.NIL;
    }

    LuaValue getPlayerById(int id) {
        if (!isClientSide() && context.server() != null) {
            for (ServerPlayer player : context.server().getPlayerList().getPlayers()) {
                if (player.getId() == id) return MLuaPlayersService.playerTable(player, context.registration());
            }
        }
        if (isClientSide()) {
            for (Object player : clientPlayers()) {
                if (((Number) invoke(CLIENT_ACCESS.get(player.getClass()).entityId(), player, "getId")).intValue() == id) {
                    return clientPlayerTable(player);
                }
            }
        }
        return LuaValue.NIL;
    }

    LuaValue localPlayer() {
        if (!isClientSide()) return LuaValue.NIL;
        try {
            Object player = MLuaClientPlayerAccess.localPlayer();
            return player == null ? LuaValue.NIL : clientPlayerTable(player);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            throw new IllegalStateException("Unable to read the local client player ("
                + error.getClass().getSimpleName() + ": " + error.getMessage() + ")", error);
        }
    }

    int getPlayerCount() {
        return isClientSide() ? clientPlayers().size()
            : context.server() == null ? 0 : context.server().getPlayerCount();
    }

    private LuaValue serverPlayerValue(ServerPlayer player) {
        return player == null ? LuaValue.NIL : MLuaPlayersService.playerTable(player, context.registration());
    }

    private java.util.List<?> clientPlayers() {
        try {
            return MLuaClientPlayerAccess.players();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            throw new IllegalStateException("Unable to read players from the active client world", error);
        }
    }

    private String clientPlayerName(Object player) {
        Object component = invoke(CLIENT_ACCESS.get(player.getClass()).name(), player, "getName");
        try {
            return String.valueOf(COMPONENT_TEXT_ACCESS.get(component.getClass()).invoke(component));
        } catch (InvocationTargetException error) {
            throw invocationFailure("getString", error);
        } catch (ReflectiveOperationException | RuntimeException error) {
            throw new IllegalStateException("Unable to read client player name", error);
        }
    }

    private LuaTable clientPlayerTable(Object player) {
        try {
            return (LuaTable) CLIENT_ACCESS.get(player.getClass()).wrapper().invoke(
                null, player, context.registration());
        } catch (InvocationTargetException error) {
            throw invocationFailure("clientPlayerTable", error);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            throw new IllegalStateException("Unable to create a Lua client player instance", error);
        }
    }

    private ServerPlayer joiningPlayer() {
        ServerPlayer player = MLuaPlayerJoinScope.current();
        return player == null || context.server() != null && player.level().getServer() != context.server()
            ? null : player;
    }

    private boolean isClientSide() { return context.side() == Side.CLIENT; }

    private static void requirePlayerName(String name, String operation) {
        if (name == null || name.isBlank()) {
            throw new LuaError("[MLua] " + operation + " expects a non-empty player name.");
        }
    }

    private static Object invoke(Method method, Object target, String methodName) {
        try {
            return method.invoke(target);
        } catch (InvocationTargetException error) {
            throw invocationFailure(methodName, error);
        } catch (ReflectiveOperationException | RuntimeException error) {
            throw new IllegalStateException("Unable to call client player method '" + methodName + "'", error);
        }
    }

    private static RuntimeException invocationFailure(String methodName, InvocationTargetException error) {
        Throwable cause = error.getCause() == null ? error : error.getCause();
        if (cause instanceof RuntimeException runtime) return runtime;
        return new IllegalStateException("Client player method '" + methodName + "' failed", cause);
    }

    private static Method method(Class<?> type, String name) {
        try {
            return type.getMethod(name);
        } catch (NoSuchMethodException error) {
            throw new IllegalStateException("Client player type '" + type.getName()
                + "' does not expose '" + name + "'", error);
        }
    }

    private static Method resolveWrapper(Class<?> playerType) {
        Class<?> wrapperType;
        try {
            wrapperType = Class.forName("dev.mlua.modules.players.classes.MLuaClientPlayerWrapper", true,
                MLuaPlayerDirectory.class.getClassLoader());
        } catch (ClassNotFoundException error) {
            throw new IllegalStateException("Client player wrapper is unavailable on this runtime", error);
        }
        Method selected = null;
        for (Method candidate : wrapperType.getMethods()) {
            if (!candidate.getName().equals("clientPlayerTable") || candidate.getParameterCount() != 2
                || !candidate.getParameterTypes()[1].isAssignableFrom(dev.mlua.module.MLuaRegistration.class)
                || !candidate.getParameterTypes()[0].isAssignableFrom(playerType)) continue;
            if (selected == null || selected.getParameterTypes()[0].isAssignableFrom(candidate.getParameterTypes()[0])) {
                selected = candidate;
            }
        }
        if (selected == null) throw new IllegalStateException("No client player wrapper accepts " + playerType.getName());
        return selected;
    }

    private record ClientPlayerAccess(Method wrapper, Method name, Method uuid, Method entityId) { }
}
