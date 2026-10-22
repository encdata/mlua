package dev.mlua.runtime.client;

import dev.mlua.runtime.assets.MLuaGeneratedAssetPack;
import dev.mlua.core.MLuaLogger;
import java.util.Arrays;
import net.minecraft.network.chat.Component;
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

        Pack pack = Pack.readMetaAndCreate(
            PACK_ID,
            Component.literal("MLua Generated Assets"),
            true,
            ignored -> MLuaGeneratedAssetPack.createPack(),
            PackType.SERVER_DATA,
            Pack.Position.TOP,
            PackSource.BUILT_IN
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
