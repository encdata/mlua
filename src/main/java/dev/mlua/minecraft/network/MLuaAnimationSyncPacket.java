package dev.mlua.minecraft.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record MLuaAnimationSyncPacket(int entityId, UUID entityUuid, ResourceLocation dimension, List<BonePoseEntry> poses) {

    public MLuaAnimationSyncPacket(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readUUID(), buf.readResourceLocation(), readPoses(buf));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeUUID(entityUuid);
        buf.writeResourceLocation(dimension);
        buf.writeVarInt(poses.size());
        for (BonePoseEntry entry : poses) {
            entry.encode(buf);
        }
    }

    private static List<BonePoseEntry> readPoses(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<BonePoseEntry> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(new BonePoseEntry(buf));
        }
        return list;
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
