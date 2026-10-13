package dev.mlua.module;

import dev.mlua.common.enums.Side;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Declares a module's runtime side before the loader constructs the module. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MLuaModuleSide {
    Side value();
}
