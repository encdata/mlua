package dev.mlua.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Declares the compatibility contract of a public MLua API surface. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
public @interface MLuaApiStatus {
    Stability value() default Stability.STABLE;
    String since() default "1.0.0";
    String replacement() default "";

    enum Stability {
        STABLE,
        EXPERIMENTAL,
        DEPRECATED
    }
}
