package dev.mlua.mc.impl.network;

import dev.mlua.mcapi.network.MCConnection;
import dev.mlua.mcapi.network.MCPacket;
import io.netty.channel.Channel;
import java.lang.reflect.Field;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

public class MCConnectionImpl implements MCConnection {
    private static final Field CHANNEL_FIELD;
    static {
        try {
            CHANNEL_FIELD = Connection.class.getDeclaredField("channel");
            CHANNEL_FIELD.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to access Connection.channel field", e);
        }
    }

    private final Connection inner;

    public MCConnectionImpl(Connection inner) {
        this.inner = inner;
    }

    public static MCConnectionImpl fromReal(Connection inner) {
        return inner == null ? null : new MCConnectionImpl(inner);
    }

    public static MCConnection fromPlayerListener(ServerGamePacketListenerImpl listener) {
        if (listener == null) return null;
        return new MCConnection() {
            @Override
            public void send(MCPacket packet) {
                if (!(packet instanceof MCPacketImpl impl)) {
                    throw new IllegalArgumentException("Unsupported Minecraft packet adapter");
                }
                listener.send((Packet<?>) impl.inner());
            }

            @Override
            public ConnectionProtocol getProtocol() {
                return ConnectionProtocol.PLAY;
            }
        };
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
        try {
            Channel channel = (Channel) CHANNEL_FIELD.get(inner);
            net.minecraft.network.ConnectionProtocol protocol = channel.attr(Connection.ATTRIBUTE_PROTOCOL).get();
            if (protocol == net.minecraft.network.ConnectionProtocol.HANDSHAKING) {
                return ConnectionProtocol.HANDSHAKING;
            } else if (protocol == net.minecraft.network.ConnectionProtocol.PLAY) {
                return ConnectionProtocol.PLAY;
            } else if (protocol == net.minecraft.network.ConnectionProtocol.STATUS) {
                return ConnectionProtocol.STATUS;
            } else if (protocol == net.minecraft.network.ConnectionProtocol.LOGIN) {
                return ConnectionProtocol.LOGIN;
            }
        } catch (Exception e) {
        }
        return ConnectionProtocol.HANDSHAKING;
    }
}
