package com.alejandro.mtoconfiguration.masterdata.messaging;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Anotación para personalizar el nombre de la entidad en los eventos de datos maestros.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PublishMasterDataEvent {
    /**
     * Nombre lógico de la entidad (ej: "station", "track").
     * Si no se informa, se usará el nombre de la clase normalizado.
     */
    String name() default "";
}
