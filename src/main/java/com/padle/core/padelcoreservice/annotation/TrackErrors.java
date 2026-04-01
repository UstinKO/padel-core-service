package com.padle.core.padelcoreservice.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Аннотация для отслеживания ошибок в методе
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TrackErrors {
    /**
     * Имя метрики для ошибок
     */
    String name() default "";

    /**
     * Типы исключений для отслеживания (по умолчанию все)
     */
    Class<? extends Throwable>[] exceptions() default {};

    /**
     * Дополнительные теги
     */
    String[] tags() default {};
}