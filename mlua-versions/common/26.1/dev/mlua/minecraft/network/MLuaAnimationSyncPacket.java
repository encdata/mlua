package dev.mlua.minecraft.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

public record MLuaAnimationSyncPacket(int entityId, UUID entityUuid, Identifier dimension, List<BonePoseEntry> poses) {
    public MLuaAnimationSyncPacket(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readUUID(), buf.readIdentifier(), readPoses(buf));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeUUID(entityUuid);
        buf.writeIdentifier(dimension);
        buf.writeVarInt(poses.size());
        for (BonePoseEntry entry : poses) entry.encode(buf);
    }

    private static List<BonePoseEntry> readPoses(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<BonePoseEntry> poses = new ArrayList<>(count);
        for (int i = 0; i < count; i++) poses.add(new BonePoseEntry(buf));
        return poses;
    }

    public record BonePoseEntry(String boneName, float xRot, float yRot, float zRot) {
        public BonePoseEntry(FriendlyByteBuf buf) {
            this(buf.readUtf(32), buf.readFloat(), buf.readFloat(), buf.readFloat());
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(boneName, 32);
            buf.writeFloat(xRot);
            buf.writeFloat(yRot);
            buf.writeFloat(zRot);
        }
    }
}
