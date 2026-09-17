package com.alejandro.mtoconfiguration.enums.jobs;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Que se republica en un trabajo de tipo {@link JobType#MASTER_DATA_REPUBLISH}.
 *
 * <p>Solo tres de las ocho entidades de datos maestros: son las que el consumidor de mantenimiento
 * materializa como activos de catenaria. Las otras cinco —paquete de ejecucion, estacion, via,
 * mensula y pendulo— se publican igual cuando cambian, pero ningun consumidor las necesita de golpe
 * y republicarlas seria escribir decenas de miles de filas de outbox para que alguien las ignore.</p>
 *
 * <p>El valor que viaja en la peticion es el <b>mismo nombre logico que lleva el evento</b>
 * ({@code @PublishMasterDataEvent}): asi quien lee la cola y quien lanza el republicado hablan el
 * mismo idioma, en vez de tener que traducir {@code SECTION_INSULATOR} a {@code section-insulator}
 * mentalmente.</p>
 */
public enum MasterDataRepublishTarget {

    /** Perfiles. Admite acotar por via. */
    PROFILE("profile"),

    /** Seccionadores. Admite acotar por estacion. */
    DISCONNECTOR("disconnector"),

    /** Aisladores de seccion. Admite acotar por estacion. */
    SECTION_INSULATOR("section-insulator"),

    /** Los tres, enteros. No admite filtros: ver {@code MasterDataRepublishJobService}. */
    ALL("all");

    private final String parameter;

    MasterDataRepublishTarget(String parameter) {
        this.parameter = parameter;
    }

    /** Valor tal y como viaja en el parametro {@code entity} de la peticion. */
    public String getParameter() {
        return parameter;
    }

    /** Tipos concretos que recorre esta seleccion, en orden estable. */
    public List<MasterDataRepublishTarget> expand() {
        return this == ALL
                ? List.of(PROFILE, DISCONNECTOR, SECTION_INSULATOR)
                : List.of(this);
    }

    /**
     * Resuelve el parametro de la peticion, sin distinguir mayusculas.
     *
     * <p>Devuelve {@code null} ante un valor desconocido en lugar de lanzar: quien llama es el
     * servicio, que convierte el fallo en una {@code ValidationException} con el resto de las
     * comprobaciones de parametros y asi el cliente recibe UN solo 400 con todos los problemas,
     * no el primero que se encontro.</p>
     */
    public static MasterDataRepublishTarget fromParameter(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim().toLowerCase();

        return Arrays.stream(values())
                .filter(target -> target.parameter.equals(normalized))
                .findFirst()
                .orElse(null);
    }

    /** Valores admitidos, para el mensaje de error y la documentacion del endpoint. */
    public static String admittedValues() {
        return Arrays.stream(values())
                .map(MasterDataRepublishTarget::getParameter)
                .collect(Collectors.joining(", "));
    }
}
