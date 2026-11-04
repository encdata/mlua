package dev.mlua.loaderapi;

import java.nio.file.Path;
import java.util.Collection;

public interface PlatformBridge {
    Path gameDir();
    Path configDir();
    boolean hasMod(String modId);
    boolean isClient();
    boolean isProduction();
    String loaderName();
    Collection<ScriptRootRef> bundledScriptRoots();
    default Path mluaDataDir() { return gameDir().resolve("mlua").resolve("data"); }
    default Path mluaScriptDir() { return gameDir().resolve("mlua").resolve("scripts"); }
}
