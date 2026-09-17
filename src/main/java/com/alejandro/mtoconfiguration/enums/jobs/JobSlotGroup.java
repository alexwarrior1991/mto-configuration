package com.alejandro.mtoconfiguration.enums.jobs;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Grupo de trabajos que compiten por el mismo cupo de ejecucion simultanea.
 *
 * <p>Exportaciones y cargas masivas van separadas porque no compiten por lo mismo: una es una
 * lectura larga sobre una via entera y la otra una rafaga de escrituras. Con un cupo unico, una
 * exportacion en curso bloquearia una carga a la que no estorbaba.</p>
 *
 * <p>La pertenencia la declara {@link JobType}, no este enumerado, y {@link #types()} la calcula al
 * llamarla en lugar de guardarla en una constante. No es un capricho: con la lista escrita aqui,
 * los dos enumerados se referencian mutuamente <b>durante su inicializacion estatica</b>, y el
 * segundo en cargarse ve las constantes del primero todavia a null. Falla con un
 * {@code NoClassDefFoundError} que no menciona el ciclo por ningun lado y que, ademas, depende de
 * cual de los dos toque alguien primero.</p>
 */
public enum JobSlotGroup {

    /** Exportaciones: lecturas largas que retienen una conexion mientras recorren la via. */
    EXPORT(8_474_001L),

    /** Cargas masivas: rafagas de escritura sobre las mismas tablas. */
    BULK(8_474_002L),

    /**
     * Republicado de datos maestros: recorrido largo de lectura que ademas escribe outbox.
     *
     * <p>Cupo propio y no {@link #BULK} porque no compiten por lo mismo y porque comparten mal:
     * un republicado recorre decenas de miles de filas, asi que meterlo en el cupo de las cargas
     * —que es de uno— dejaria bloqueada cualquier importacion durante todo el recorrido, y al
     * reves. Que sea una operacion de explotacion puntual es justo el motivo para que no tenga
     * que esperar detras de la carga nocturna.</p>
     */
    REPUBLISH(8_474_003L);

    /**
     * Clave del cerrojo consultivo de PostgreSQL que serializa el «mira si hay hueco y coge uno».
     *
     * <p>El valor concreto da igual mientras sea <b>estable</b> y no lo use nadie mas en la
     * aplicacion —hoy no hay ningun otro {@code pg_advisory_lock}—. Dos claves distintas hacen
     * ademas que pedir un hueco de exportacion no espere por uno de carga masiva.</p>
     */
    private final long lockKey;

    JobSlotGroup(long lockKey) {
        this.lockKey = lockKey;
    }

    public long getLockKey() {
        return lockKey;
    }

    /** Tipos que consumen cupo de este grupo. */
    public Set<JobType> types() {
        return Arrays.stream(JobType.values())
                .filter(type -> type.getSlotGroup() == this)
                .collect(Collectors.toUnmodifiableSet());
    }
}
