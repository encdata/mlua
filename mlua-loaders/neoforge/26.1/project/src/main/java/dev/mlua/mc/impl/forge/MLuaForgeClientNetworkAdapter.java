package dev.mlua.mc.impl.forge;

import dev.mlua.minecraft.network.MLuaNetworkManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/** Registers clientbound MLua payloads without loading client networking classes on a server. */
@EventBusSubscriber(modid = "mlua", value = Dist.CLIENT)
public final class MLuaForgeClientNetworkAdapter {
    private MLuaForgeClientNetworkAdapter() {}

    @SubscribeEvent
    public static void registerPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(MLuaForgeNetworkAdapter.RemotePayload.TYPE,
            (payload, context) -> context.enqueueWork(() ->
                MLuaNetworkManager.get().receiveRemoteFromServer(payload.remoteName(), payload.arguments())));
        event.register(MLuaForgeNetworkAdapter.ReloadPayload.TYPE,
            (payload, context) -> context.enqueueWork(MLuaNetworkManager.get()::receiveClientReload));
        event.register(MLuaForgeNetworkAdapter.ClientCommandPayload.TYPE,
            (payload, context) -> context.enqueueWork(() -> MLuaNetworkManager.get()
                .receiveClientCommand(payload.commandName(), payload.arguments())));
        event.register(MLuaForgeNetworkAdapter.AnimationSyncPayload.TYPE,
            (payload, context) -> context.enqueueWork(() -> MLuaNetworkManager.get()
                .receiveClientAnimationSync(payload.toPacket())));
        event.register(MLuaForgeNetworkAdapter.DataSyncPayload.TYPE,
            (payload, context) -> context.enqueueWork(() -> MLuaNetworkManager.get()
                .receiveClientDataSync(payload.toPacket())));
    }
}
