package dev.mlua.loader.impl.forge;

import dev.mlua.loaderapi.NetworkContext;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class ForgeNetworkContext implements NetworkContext {
    private final NetworkEvent.Context context;

    public ForgeNetworkContext(Supplier<NetworkEvent.Context> supplier) {
        this.context = supplier.get();
    }

    @Override
    public void enqueueWork(Runnable task) {
        context.enqueueWork(task);
    }

    @Override
    public void setPacketHandled(boolean handled) {
        context.setPacketHandled(handled);
    }

    @Override
    public String getSenderUuid() {
        var sender = context.getSender();
        return sender != null ? sender.getUUID().toString() : null;
    }

    @Override
    public boolean isClientSide() {
        return context.getDirection().getReceptionSide().isClient();
    }
}
