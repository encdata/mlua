package dev.mlua.modules.players;

import dev.mlua.loaderapi.LoaderApi;
import java.util.List;

public final class MLuaClientPlayerAccess {
    private MLuaClientPlayerAccess() {}

    public static Object localPlayer() { return LoaderApi.client().localPlayer(); }
    public static Object level() { return LoaderApi.client().level(); }
    public static List<?> players() { return LoaderApi.client().players(); }
}
