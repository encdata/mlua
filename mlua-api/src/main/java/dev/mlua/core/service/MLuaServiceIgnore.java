package dev.mlua.core.service;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Prevents a public Java helper method from being exported as a reflective Lua service function. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MLuaServiceIgnore {
}
