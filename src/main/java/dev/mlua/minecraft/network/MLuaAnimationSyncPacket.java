package dev.mlua.minecraft.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record MLuaAnimationSyncPacket(int entityId, UUID entityUuid, ResourceLocation dimension, List<BonePoseEntry> poses) {
    public static final int MAX_BONES = dev.mlua.minecraft.animation.MLuaAnimation.MAX_BONES_PER_KEYFRAME;
    public static final int MAX_BONE_NAME_LENGTH = 128;

    public MLuaAnimationSyncPacket {
        if (poses == null || poses.size() > MAX_BONES) {
            throw new IllegalArgumentException("Animation sync pose count must be between 0 and " + MAX_BONES);
        }
        poses = List.copyOf(poses);
    }

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
        if (count < 0 || count > MAX_BONES) {
            throw new IllegalArgumentException("Animation sync pose count exceeds " + MAX_BONES);
        }
        List<BonePoseEntry> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(new BonePoseEntry(buf));
        }
        return list;
    }

    public record BonePoseEntry(String boneName, float x, float y, float z, float xRot, float yRot, float zRot) {

        public BonePoseEntry {
            if (boneName == null || boneName.isBlank() || boneName.length() > MAX_BONE_NAME_LENGTH) {
                throw new IllegalArgumentException("Animation bone names must contain 1-" + MAX_BONE_NAME_LENGTH + " characters");
            }
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
                    || !Float.isFinite(xRot) || !Float.isFinite(yRot) || !Float.isFinite(zRot)) {
                throw new IllegalArgumentException("Animation sync transforms must contain finite values");
            }
        }

        public BonePoseEntry(FriendlyByteBuf buf) {
            this(buf.readUtf(MAX_BONE_NAME_LENGTH), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat());
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(boneName, MAX_BONE_NAME_LENGTH);
            buf.writeFloat(x);
            buf.writeFloat(y);
            buf.writeFloat(z);
            buf.writeFloat(xRot);
            buf.writeFloat(yRot);
            buf.writeFloat(zRot);
        }
    }
}
