package com.alejandro.mtoconfiguration.enums.jobs;

/**
 * Clase de trabajo que ejecuta un {@link com.alejandro.mtoconfiguration.entity.jobs.AsyncJob}.
 *
 * <p>El valor se persiste como texto ({@code @Enumerated(STRING)}) y viaja en la respuesta HTTP,
 * asi que <b>renombrar una constante rompe a los clientes y deja filas ilegibles</b>: para retirar
 * un tipo, anadir el nuevo y migrar los datos, nunca renombrar en sitio.</p>
 *
 * <p>Al anadir un valor hay que ampliar tambien la restriccion CHECK de {@code async_job.job_type}
 * con una migracion de Flyway. {@code ddl-auto: validate} NO comprueba los CHECK, de modo que el
 * fallo no saldria al arrancar: saldria en el primer INSERT, ya en produccion.</p>
 */
public enum JobType {

    /** Volcado a CSV de los perfiles de una via. Produce un fichero descargable. */
    PROFILE_EXPORT(JobSlotGroup.EXPORT),

    /** Alta masiva de perfiles, elemento a elemento y con progreso parcial. */
    PROFILE_BULK_CREATE(JobSlotGroup.BULK),

    /** Modificacion masiva de perfiles, elemento a elemento y con progreso parcial. */
    PROFILE_BULK_UPDATE(JobSlotGroup.BULK),

    /**
     * Carga del catalogo maestro de LOVs desde {@code lov-master.xlsx}.
     *
     * <p>Va en el cupo de cargas masivas porque es lo que es: una rafaga de escrituras
     * sobre las tablas LOV. Compartir cupo con las cargas de perfiles es deliberado,
     * ya que ambas compiten por las mismas conexiones y por la invalidacion de cache.
     */
    LOV_IMPORT(JobSlotGroup.BULK);

    private final JobSlotGroup slotGroup;

    JobType(JobSlotGroup slotGroup) {
        this.slotGroup = slotGroup;
    }

    /** Cupo contra el que compite este tipo de trabajo. */
    public JobSlotGroup getSlotGroup() {
        return slotGroup;
    }
}
