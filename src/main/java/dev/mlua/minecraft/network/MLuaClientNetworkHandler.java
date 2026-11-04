package dev.mlua.minecraft.network;

import dev.mlua.core.MLuaLogger;
import dev.mlua.core.MLuaThreadHandoff;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.minecraft.MLuaDimensionId;
import dev.mlua.minecraft.animation.MLuaBonePose;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.minecraft.client.MLuaMinecraftClient;
import dev.mlua.runtime.data.MLuaDataManager;
import dev.mlua.runtime.data.MLuaDataSyncPacket;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;

/** Client-only packet work, loaded lazily so the common network manager stays dedicated-server safe. */
public final class MLuaClientNetworkHandler {
    private MLuaClientNetworkHandler() {}

    public static void handlePacket(MLuaNetworkManager manager, Object clientObject, FriendlyByteBuf buf) {
        Minecraft client = (Minecraft) clientObject;
        int type;
        try {
            type = buf.readByte();
        } catch (Exception ignored) {
            return;
        }

        switch (type) {
            case MLuaNetworkWireCodec.TYPE_NETWORK -> {
                String remoteName = buf.readUtf(128);
                List<MLuaNetworkValue> arguments = MLuaNetworkWireCodec.readArgumentList(buf);
                MLuaThreadHandoff.execute(client::isSameThread, client,
                    () -> manager.receiveRemoteFromServer(remoteName, arguments));
            }
            case MLuaNetworkWireCodec.TYPE_RELOAD -> MLuaThreadHandoff.execute(
                client::isSameThread, client, manager::receiveClientReload);
            case MLuaNetworkWireCodec.TYPE_CLIENT_COMMAND -> {
                String commandName = buf.readUtf(128);
                List<MLuaNetworkValue> arguments = MLuaNetworkWireCodec.readArgumentList(buf);
                MLuaThreadHandoff.execute(client::isSameThread, client,
                    () -> manager.receiveClientCommand(commandName, arguments));
            }
            case MLuaNetworkWireCodec.TYPE_ANIMATION_SYNC -> {
                byte[] payload = readRemaining(buf);
                MLuaThreadHandoff.execute(client::isSameThread, client,
                    () -> applyAnimationPayload(payload));
            }
            case MLuaNetworkWireCodec.TYPE_DATA_SYNC -> {
                byte[] payload = readRemaining(buf);
                MLuaThreadHandoff.execute(client::isSameThread, client,
                    () -> applyDataPayload(payload));
            }
            case MLuaNetworkWireCodec.TYPE_HANDSHAKE -> { }
            default -> MLuaLogger.LOGGER.warn("[MLua] Unknown client packet type: {}", type);
        }
    }

    public static void applyAnimationSync(MLuaAnimationSyncPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
                || !MLuaDimensionId.nativeId(minecraft.level).equals(packet.dimension())) return;
        Entity entity = minecraft.level.getEntity(packet.entityId());
        if (entity == null || !entity.getUUID().equals(packet.entityUuid())) return;

        Map<String, MLuaCFrame> poses = new HashMap<>();
        for (MLuaAnimationSyncPacket.BonePoseEntry entry : packet.poses()) {
            poses.put(entry.boneName(), MLuaCFrame.at(entry.x(), entry.y(), entry.z())
                .mul(MLuaCFrame.angles(entry.xRot(), entry.yRot(), entry.zRot())));
        }
        MLuaBonePose.replaceSyncedPose(MLuaEntityScope.of(entity), poses);
    }

    public static Connection resolveConnection() {
        ClientPacketListener listener = MLuaMinecraftClient.instance().getConnection();
        if (listener == null) return null;

        try {
            Object value = listener.getClass().getMethod("getConnection").invoke(listener);
            if (value instanceof Connection connection) return connection;
        } catch (ReflectiveOperationException ignored) {
        }

        try {
            Field field = findField(listener.getClass(), "connection");
            if (field == null) return null;
            Object value = field.get(listener);
            return value instanceof Connection connection ? connection : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static byte[] readRemaining(FriendlyByteBuf buf) {
        byte[] payload = new byte[buf.readableBytes()];
        buf.readBytes(payload);
        return payload;
    }

    private static void applyAnimationPayload(byte[] bytes) {
        FriendlyByteBuf payload = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
        try {
            applyAnimationSync(new MLuaAnimationSyncPacket(payload));
        } catch (RuntimeException failure) {
            MLuaLogger.LOGGER.warn("[MLua] Failed to decode client animation sync payload", failure);
        } finally {
            ReferenceCountUtil.safeRelease(payload);
        }
    }

    private static void applyDataPayload(byte[] bytes) {
        FriendlyByteBuf payload = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
        try {
            MLuaDataManager.handleClientSync(new MLuaDataSyncPacket(payload));
        } catch (RuntimeException failure) {
            MLuaLogger.LOGGER.warn("[MLua] Failed to decode client data sync payload", failure);
        } finally {
            ReferenceCountUtil.safeRelease(payload);
        }
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
