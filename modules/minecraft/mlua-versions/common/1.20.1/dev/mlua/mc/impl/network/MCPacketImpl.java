package dev.mlua.mc.impl.network;

import dev.mlua.mcapi.network.MCPacket;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;

public class MCPacketImpl implements MCPacket {
    private final Packet<?> inner;

    public MCPacketImpl(Packet<?> inner) {
        this.inner = inner;
    }

    public static MCPacketImpl fromReal(Packet<?> inner) {
        return inner == null ? null : new MCPacketImpl(inner);
    }

    public Packet<?> inner() {
        return inner;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void handle(Object listener) {
        if (listener instanceof PacketListener packetListener) {
            ((Packet<PacketListener>) inner).handle(packetListener);
        }
    }
}
