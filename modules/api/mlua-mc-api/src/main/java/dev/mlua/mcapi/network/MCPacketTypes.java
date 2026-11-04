package dev.mlua.mcapi.network;

import java.util.UUID;

public final class MCPacketTypes {
    private MCPacketTypes() {}

    // TODO: replace int with actual packet type references per-loader
    public static final int ClientboundSetTitleTextPacket = 0;
    public static final int ClientboundSetSubtitleTextPacket = 1;
    public static final int ClientboundSetActionBarTextPacket = 2;
    public static final int ClientboundSetTitlesAnimationPacket = 3;
    public static final int ClientboundClearTitlesPacket = 4;
    public static final int ClientboundAddEntityPacket = 5;
}
