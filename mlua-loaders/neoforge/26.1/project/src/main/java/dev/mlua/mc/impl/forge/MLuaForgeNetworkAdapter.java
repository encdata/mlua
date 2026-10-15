package dev.mlua.mc.impl.forge;

import dev.mlua.core.MLuaLogger;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.minecraft.network.MLuaAnimationSyncPacket;
import dev.mlua.minecraft.network.MLuaClientCommandPacket;
import dev.mlua.minecraft.network.MLuaNetworkManager;
import dev.mlua.minecraft.network.MLuaNetworkPacket;
import dev.mlua.minecraft.network.MLuaNetworkSerializer;
import dev.mlua.minecraft.network.MLuaNetworkValue;
import dev.mlua.minecraft.network.MLuaReloadPacket;
import dev.mlua.runtime.data.MLuaDataSyncPacket;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@EventBusSubscriber(modid = "mlua")
public final class MLuaForgeNetworkAdapter {
    private static final String DEFAULT_PROTOCOL_VERSION = "2";
    private static final Identifier REMOTE_ID = MLuaResourceLocations.build("mlua", "remote");
    private static final Identifier RELOAD_ID = MLuaResourceLocations.build("mlua", "reload");
    private static final Identifier CLIENT_COMMAND_ID = MLuaResourceLocations.build("mlua", "client_command");
    private static final Identifier ANIMATION_SYNC_ID = MLuaResourceLocations.build("mlua", "animation_sync");
    private static final Identifier DATA_SYNC_ID = MLuaResourceLocations.build("mlua", "data_sync");

    private MLuaForgeNetworkAdapter() {
    }

    @SubscribeEvent
    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(DEFAULT_PROTOCOL_VERSION);
        registrar.playBidirectional(RemotePayload.TYPE, RemotePayload.CODEC,
            MLuaForgeNetworkAdapter::handleRemoteServer);
        registrar.playToClient(ReloadPayload.TYPE, ReloadPayload.CODEC);
        registrar.playToClient(ClientCommandPayload.TYPE, ClientCommandPayload.CODEC);
        registrar.playToClient(AnimationSyncPayload.TYPE, AnimationSyncPayload.CODEC);
        registrar.playToClient(DataSyncPayload.TYPE, DataSyncPayload.CODEC);
    }

    public static boolean hasChannel(Connection connection) {
        return connection != null && NetworkRegistry.hasChannel(connection, null, REMOTE_ID);
    }

    private static void handleRemoteServer(RemotePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sender) {
                MLuaNetworkManager.get().receiveRemoteFromClient(sender, payload.remoteName(), payload.arguments());
            } else {
                MLuaLogger.LOGGER.warn("MLua Network payload '{}' had no server sender", payload.remoteName());
            }
        });
    }

    private static List<MLuaNetworkValue> readArguments(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > 64) {
            throw new IllegalArgumentException("[MLua] Invalid network argument count: " + count);
        }

        List<MLuaNetworkValue> arguments = new ArrayList<>(count);
        for (int i = 0; i < count; ++i) {
            arguments.add(MLuaNetworkSerializer.readPacketValue(buf));
        }
        return arguments;
    }

    private static void writeArguments(RegistryFriendlyByteBuf buf, List<MLuaNetworkValue> arguments) {
        if (arguments.size() > 64) {
            throw new IllegalArgumentException("[MLua] Invalid network argument count: " + arguments.size());
        }
        buf.writeVarInt(arguments.size());
        for (MLuaNetworkValue argument : arguments) {
            MLuaNetworkSerializer.writePacketValue(argument, buf);
        }
    }

    public record RemotePayload(String remoteName, List<MLuaNetworkValue> arguments) implements CustomPacketPayload {
        public static final Type<RemotePayload> TYPE = new Type<>(REMOTE_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, RemotePayload> CODEC = CustomPacketPayload.codec(RemotePayload::write, RemotePayload::new);

        public RemotePayload(MLuaNetworkPacket packet) {
            this(packet.remoteName(), packet.arguments());
        }

        public RemotePayload(RegistryFriendlyByteBuf buf) {
            this(buf.readUtf(128), readArguments(buf));
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeUtf(this.remoteName, 128);
            writeArguments(buf, this.arguments);
        }

        @Override
        public Type<RemotePayload> type() {
            return TYPE;
        }
    }

    public record ReloadPayload() implements CustomPacketPayload {
        public static final Type<ReloadPayload> TYPE = new Type<>(RELOAD_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, ReloadPayload> CODEC = CustomPacketPayload.codec(ReloadPayload::write, ReloadPayload::new);

        public ReloadPayload(MLuaReloadPacket packet) {
            this();
        }

        public ReloadPayload(RegistryFriendlyByteBuf buf) {
            this();
        }

        private void write(RegistryFriendlyByteBuf buf) {
        }

        @Override
        public Type<ReloadPayload> type() {
            return TYPE;
        }
    }

    public record ClientCommandPayload(String commandName, List<MLuaNetworkValue> arguments) implements CustomPacketPayload {
        public static final Type<ClientCommandPayload> TYPE = new Type<>(CLIENT_COMMAND_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, ClientCommandPayload> CODEC = CustomPacketPayload.codec(ClientCommandPayload::write, ClientCommandPayload::new);

        public ClientCommandPayload(MLuaClientCommandPacket packet) {
            this(packet.commandName(), packet.arguments());
        }

        public ClientCommandPayload(RegistryFriendlyByteBuf buf) {
            this(buf.readUtf(128), readArguments(buf));
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeUtf(this.commandName, 128);
            writeArguments(buf, this.arguments);
        }

        @Override
        public Type<ClientCommandPayload> type() {
            return TYPE;
        }
    }

    public record AnimationSyncPayload(int entityId, UUID entityUuid, Identifier dimension,
                                       List<MLuaAnimationSyncPacket.BonePoseEntry> poses) implements CustomPacketPayload {
        public static final Type<AnimationSyncPayload> TYPE = new Type<>(ANIMATION_SYNC_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, AnimationSyncPayload> CODEC = CustomPacketPayload.codec(AnimationSyncPayload::write, AnimationSyncPayload::new);

        public AnimationSyncPayload(MLuaAnimationSyncPacket packet) {
            this(packet.entityId(), packet.entityUuid(), packet.dimension(), packet.poses());
        }

        public AnimationSyncPayload(RegistryFriendlyByteBuf buf) {
            this(buf.readVarInt(), buf.readUUID(), buf.readIdentifier(), readPoses(buf));
        }

        public MLuaAnimationSyncPacket toPacket() {
            return new MLuaAnimationSyncPacket(this.entityId, this.entityUuid, this.dimension, this.poses);
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeVarInt(this.entityId);
            buf.writeUUID(this.entityUuid);
            buf.writeIdentifier(this.dimension);
            buf.writeVarInt(this.poses.size());
            for (MLuaAnimationSyncPacket.BonePoseEntry pose : this.poses) {
                pose.encode(buf);
            }
        }

        private static List<MLuaAnimationSyncPacket.BonePoseEntry> readPoses(RegistryFriendlyByteBuf buf) {
            int count = buf.readVarInt();
            if (count < 0 || count > 1024) {
                throw new IllegalArgumentException("[MLua] Invalid animation pose count: " + count);
            }
            List<MLuaAnimationSyncPacket.BonePoseEntry> poses = new ArrayList<>(count);
            for (int i = 0; i < count; ++i) {
                poses.add(new MLuaAnimationSyncPacket.BonePoseEntry(buf));
            }
            return poses;
        }

        @Override
        public Type<AnimationSyncPayload> type() {
            return TYPE;
        }
    }

    public record DataSyncPayload(MLuaDataSyncPacket.Scope scope, UUID targetId, CompoundTag data) implements CustomPacketPayload {
        public static final Type<DataSyncPayload> TYPE = new Type<>(DATA_SYNC_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, DataSyncPayload> CODEC = CustomPacketPayload.codec(DataSyncPayload::write, DataSyncPayload::new);

        public DataSyncPayload(MLuaDataSyncPacket packet) {
            this(packet.scope(), packet.targetId(), packet.data());
        }

        public DataSyncPayload(RegistryFriendlyByteBuf buf) {
            this(readScope(buf.readVarInt()), buf.readBoolean() ? buf.readUUID() : null, readTag(buf));
        }

        public MLuaDataSyncPacket toPacket() {
            return new MLuaDataSyncPacket(this.scope, this.targetId, this.data);
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeVarInt(this.scope.ordinal());
            buf.writeBoolean(this.targetId != null);
            if (this.targetId != null) {
                buf.writeUUID(this.targetId);
            }
            writeTag(buf, this.data);
        }

        private static CompoundTag readTag(RegistryFriendlyByteBuf buf) {
            CompoundTag tag = buf.readNbt();
            return tag != null ? tag : new CompoundTag();
        }

        private static MLuaDataSyncPacket.Scope readScope(int ordinal) {
            MLuaDataSyncPacket.Scope[] scopes = MLuaDataSyncPacket.Scope.values();
            if (ordinal < 0 || ordinal >= scopes.length) {
                throw new IllegalArgumentException("[MLua] Invalid data sync scope: " + ordinal);
            }
            return scopes[ordinal];
        }

        private static void writeTag(RegistryFriendlyByteBuf buf, CompoundTag tag) {
            buf.writeNbt(tag == null ? new CompoundTag() : tag);
        }

        @Override
        public Type<DataSyncPayload> type() {
            return TYPE;
        }
    }
}
