package dev.mlua.runtime.client;

import dev.mlua.runtime.assets.MLuaGeneratedAssetPack;
import dev.mlua.core.MLuaLogger;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;

public final class MLuaPackFinder {
    public static final String PACK_ID = "mlua_generated_assets";
    private static final RepositorySource REPOSITORY_SOURCE = consumer -> {
        if (!MLuaGeneratedAssetPack.hasGeneratedContent()) {
            return;
        }

        Pack.ResourcesSupplier supplier = new Pack.ResourcesSupplier() {
            @Override
            public PackResources openPrimary(PackLocationInfo info) {
                return MLuaGeneratedAssetPack.createPack();
            }

            @Override
            public PackResources openFull(PackLocationInfo info, Pack.Metadata metadata) {
                return MLuaGeneratedAssetPack.createPack();
            }
        };
        Pack pack = Pack.readMetaAndCreate(
            new PackLocationInfo(PACK_ID, Component.literal("MLua Generated Assets"), PackSource.BUILT_IN, Optional.empty()),
            supplier,
            PackType.SERVER_DATA,
            new PackSelectionConfig(true, Pack.Position.TOP, false)
        );

        if (pack != null) {
            consumer.accept(pack);
        }
    };

    private MLuaPackFinder() {
    }

    public static void registerResourcePack() {
        try {
            MLuaGeneratedAssetPack.generate();
        } catch (Throwable throwable) {
            MLuaLogger.LOGGER.debug("[MLua] Could not generate Fabric resource pack", throwable);
        }
    }

    public static RepositorySource repositorySource() {
        return REPOSITORY_SOURCE;
    }

    public static RepositorySource[] withRepositorySource(RepositorySource[] sources) {
        if (sources == null || sources.length == 0) {
            return new RepositorySource[] { REPOSITORY_SOURCE };
        }
        for (RepositorySource source : sources) {
            if (source == REPOSITORY_SOURCE) {
                return sources;
            }
        }
        RepositorySource[] updated = Arrays.copyOf(sources, sources.length + 1);
        updated[sources.length] = REPOSITORY_SOURCE;
        return updated;
    }
}
