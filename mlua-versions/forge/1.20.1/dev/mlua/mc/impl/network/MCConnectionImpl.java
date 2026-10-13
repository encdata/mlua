package dev.mlua.mc.impl.network;

import dev.mlua.mcapi.network.MCConnection;
import dev.mlua.mcapi.network.MCPacket;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;

public class MCConnectionImpl implements MCConnection {
    private final Connection inner;

    public MCConnectionImpl(Connection inner) {
        this.inner = inner;
    }

    public static MCConnectionImpl fromReal(Connection inner) {
        return inner == null ? null : new MCConnectionImpl(inner);
    }

    public Connection inner() {
        return inner;
    }

    @Override
    public void send(MCPacket packet) {
        if (packet instanceof MCPacketImpl impl) {
            inner.send((Packet<?>) impl.inner());
        }
    }

    @Override
    public ConnectionProtocol getProtocol() {
        net.minecraft.network.ConnectionProtocol protocol = inner.channel().attr(Connection.ATTRIBUTE_PROTOCOL).get();
        if (protocol == net.minecraft.network.ConnectionProtocol.HANDSHAKING) {
            return ConnectionProtocol.HANDSHAKING;
        } else if (protocol == net.minecraft.network.ConnectionProtocol.PLAY) {
            return ConnectionProtocol.PLAY;
        } else if (protocol == net.minecraft.network.ConnectionProtocol.STATUS) {
            return ConnectionProtocol.STATUS;
        } else if (protocol == net.minecraft.network.ConnectionProtocol.LOGIN) {
            return ConnectionProtocol.LOGIN;
        }
        return ConnectionProtocol.HANDSHAKING;
    }
}
