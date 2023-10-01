package edu.iu.spi;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Copied from IU JEE 6
 */
@Retention(RUNTIME)
@Target(TYPE)
public @interface DefaultInterceptor {
}
