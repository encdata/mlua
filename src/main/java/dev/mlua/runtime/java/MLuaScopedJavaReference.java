package dev.mlua.runtime.java;

/** A Java object reference that must validate its lifetime before the Java bridge unwraps it. */
public interface MLuaScopedJavaReference {
    Object unwrap();
}
