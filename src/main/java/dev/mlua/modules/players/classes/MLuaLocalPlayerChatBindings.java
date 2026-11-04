package dev.mlua.modules.players.classes;

import dev.mlua.common.mixin.MLuaMappingResolvers;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.mc.impl.core.MCComponentImpl;
import dev.mlua.mcapi.core.MCComponent;
import dev.mlua.minecraft.client.MLuaMinecraftClient;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import static dev.mlua.modules.players.classes.MLuaPlayerWrapper.varArg;

/** Owns local-player chat display and submission through Minecraft's native chat path. */
final class MLuaLocalPlayerChatBindings {
    private static final ClassValue<Method> CHAT_MESSAGE_ADD_METHOD = new ClassValue<>() {
        @Override
        protected Method computeValue(Class<?> chatType) {
            for (Method method : chatType.getMethods()) {
                if (!method.getName().equals("addMessage")) continue;
                Class<?>[] parameters = method.getParameterTypes();
                if ((parameters.length == 1 || parameters.length == 4)
                    && Component.class.isAssignableFrom(parameters[0])) return method;
            }
            throw new IllegalStateException("No compatible ChatComponent.addMessage overload is available");
        }
    };
    private static final ClassValue<Optional<Method>> CHAT_SUBMISSION_METHOD = new ClassValue<>() {
        @Override
        protected Optional<Method> computeValue(Class<?> connectionType) {
            return Optional.ofNullable(findClientChatSubmissionMethod(connectionType));
        }
    };

    private MLuaLocalPlayerChatBindings() { }

    static void bind(LuaTable table, LocalPlayer raw, dev.mlua.mcapi.entity.MCLocalPlayer player) {
        MLuaClassMethods.setInstanceMethod(table, "SendMessage", varArg(args -> {
            String rawMsg = args.checkjstring(2);
            Component component = parseDisplayComponent(rawMsg);

            try {
                Minecraft minecraft = MLuaMinecraftClient.instance();
                if (minecraft.gui != null && minecraft.gui.getChat() != null) {
                    addLocalChatMessage(minecraft.gui.getChat(), component);
                } else {
                    player.displayClientMessage(MCComponentImpl.fromReal(component), false);
                }
            } catch (Exception ignored) {
                player.displayClientMessage(MCComponentImpl.fromReal(component), false);
            }
            return LuaValue.NONE;
        }), "Sends a chat message to this player. On the client, displays the message locally.");
        MLuaClassMethods.setInstanceMethod(table, "SendChatMessage", varArg(args -> {
            MLuaLocalPlayerBindings.requireActiveLocalPlayer(raw, "Player:SendChatMessage");
            String message = args.checkjstring(2);
            Object connection = MLuaMinecraftClient.instance().getConnection();
            if (connection == null) throw new LuaError("[MLua] Player:SendChatMessage requires an active client connection.");
            Method chatSubmission = CHAT_SUBMISSION_METHOD.get(connection.getClass()).orElse(null);
            if (chatSubmission == null) {
                throw new LuaError("[MLua] This Minecraft version does not expose the local chat submission method.");
            }
            int submitted = 0;
            for (String line : message.split("\\R", -1)) {
                if (line.isEmpty()) continue;
                try {
                    chatSubmission.invoke(connection, line);
                    submitted++;
                } catch (java.lang.reflect.InvocationTargetException error) {
                    Throwable cause = error.getCause() == null ? error : error.getCause();
                    throw new LuaError("[MLua] Player:SendChatMessage failed after " + submitted
                        + " line(s): " + cause.getMessage());
                } catch (ReflectiveOperationException error) {
                    throw new LuaError("[MLua] Player:SendChatMessage could not submit chat in this Minecraft version.");
                }
            }
            return LuaValue.valueOf(submitted);
        }), "Submits each non-empty line through Minecraft's normal local-player chat path. This sends chat to the connected server when multiplayer is active and returns the number of submitted lines.");
        MLuaClassMethods.setInstanceMethod(table, "SendActionBar", varArg(args -> {
            Component component = parseDisplayComponent(args.checkjstring(2));

            try {
                MLuaMinecraftClient.showActionBar(component);
            } catch (Exception ignored) {
                player.displayClientMessage(MCComponentImpl.fromReal(component), true);
            }
            return LuaValue.NONE;
        }), "Sends an action bar message to this player.");
    }

    private static Component parseDisplayComponent(String rawMessage) {
        try {
            return Component.literal(MCComponent.fromJson(rawMessage).getString());
        } catch (Exception ignored) {
            return Component.literal(rawMessage);
        }
    }

    private static Method findClientChatSubmissionMethod(Class<?> connectionType) {
        try {
            LinkedHashSet<String> candidates = new LinkedHashSet<>();
            for (String name : List.of("sendChat", "sendChatMessage")) {
                try {
                    String mapped = MLuaMappingResolvers.current().mapMethod(
                        "net.minecraft.client.multiplayer.ClientPacketListener", name,
                        "(Ljava/lang/String;)V").name();
                    if (mapped != null && !mapped.isBlank()) candidates.add(mapped);
                } catch (RuntimeException ignored) { }
                candidates.add(name);
            }
            for (String name : candidates) {
                try {
                    Method method = connectionType.getMethod(name, String.class);
                    if (method.getReturnType() == Void.TYPE) return method;
                } catch (ReflectiveOperationException ignored) { }
                try {
                    Method method = connectionType.getDeclaredMethod(name, String.class);
                    if (method.getReturnType() == Void.TYPE && method.trySetAccessible()) return method;
                } catch (ReflectiveOperationException | RuntimeException ignored) { }
            }
        } catch (RuntimeException ignored) { }
        return null;
    }

    private static void addLocalChatMessage(Object chat, Component component) throws ReflectiveOperationException {
        Method method = CHAT_MESSAGE_ADD_METHOD.get(chat.getClass());
        Class<?>[] parameters = method.getParameterTypes();
        Object[] arguments = parameters.length == 1 ? new Object[]{component}
            : new Object[]{component, null, defaultMessageSource(parameters[2]), null};
        method.invoke(chat, arguments);
    }

    private static Object defaultMessageSource(Class<?> sourceType) {
        if (!sourceType.isEnum()) return null;
        Object[] values = sourceType.getEnumConstants();
        for (Object value : values) {
            String name = ((Enum<?>) value).name();
            if (name.equalsIgnoreCase("SYSTEM") || name.equalsIgnoreCase("SYSTEM_MESSAGE")) return value;
        }
        return values.length == 0 ? null : values[0];
    }
}
