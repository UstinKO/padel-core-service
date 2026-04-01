package com.padle.core.padelcoreservice.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Аннотация для подсчета количества вызовов метода
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Counted {
    /**
     * Имя метрики (если не указано, используется имя класса + метод)
     */
    String name() default "";

    /**
     * Дополнительные теги (например: "type=registration,status=success")
     */
    String[] tags() default {};

    /**
     * Описание метрики
     */
    String description() default "Method call counter";
}