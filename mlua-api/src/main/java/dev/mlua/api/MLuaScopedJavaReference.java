package dev.mlua.api;

/**
 * A Java value exposed to Lua with a shorter lifetime than its script runtime.
 * Bridges must unwrap it before passing the native value into Java APIs.
 */
public interface MLuaScopedJavaReference {
    /** Returns the native value while its owning scope is still valid. */
    Object unwrap();
}
