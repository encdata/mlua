package dev.mlua.runtime.client;

import dev.mlua.runtime.assets.MLuaGeneratedAssetPack;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.Pack.Position;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;

@EventBusSubscriber(modid = "mlua")
public final class MLuaPackFinder {
    private static final AtomicBoolean ENABLED = new AtomicBoolean();

    private MLuaPackFinder() {}

    public static void registerResourcePack() {
        ENABLED.set(true);
    }

    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (!ENABLED.get()) return;
        PackType packType = event.getPackType();
        if (packType != PackType.CLIENT_RESOURCES && packType != PackType.SERVER_DATA) return;

        MLuaGeneratedAssetPack.generate();
        if (!MLuaGeneratedAssetPack.hasGeneratedContent()) return;

        event.addRepositorySource(packs -> {
            Component title = Component.literal(
                packType == PackType.CLIENT_RESOURCES ? "MLua Generated Assets" : "MLua Generated Data");
            PackLocationInfo location = new PackLocationInfo(
                "mlua_generated_assets", title, PackSource.BUILT_IN, Optional.empty());
            Pack.ResourcesSupplier resources = new Pack.ResourcesSupplier() {
                @Override
                public PackResources openPrimary(PackLocationInfo packInfo) {
                    return MLuaGeneratedAssetPack.createPack();
                }

                @Override
                public PackResources openFull(
                    PackLocationInfo packInfo,
                    Pack.Metadata metadata
                ) {
                    return MLuaGeneratedAssetPack.createPack();
                }
            };
            Pack pack = Pack.readMetaAndCreate(
                location,
                resources,
                packType,
                new PackSelectionConfig(true, Position.TOP, false));
            if (pack != null) packs.accept(pack);
        });
    }
}
