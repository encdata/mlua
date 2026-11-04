package dev.mlua.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Adds documentation to a Lua-facing service class or exposed function. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Inherited
@MLuaApiStatus(since = "1.0.0")
public @interface MLuaDescription {
   String value();
}
