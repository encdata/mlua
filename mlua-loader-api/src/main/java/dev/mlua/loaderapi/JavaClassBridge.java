package dev.mlua.loaderapi;

/** Loader-owned class resolution used by the public Java Lua service. */
public interface JavaClassBridge {
   Class<?> resolve(String canonicalName, ClassLoader... preferredLoaders);
}
