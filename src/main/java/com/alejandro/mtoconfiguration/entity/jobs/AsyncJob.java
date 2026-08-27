package com.alejandro.mtoconfiguration.entity.jobs;

import com.alejandro.mtoconfiguration.enums.jobs.JobStatus;
import com.alejandro.mtoconfiguration.enums.jobs.JobType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Trabajo en segundo plano con estado persistido.
 *
 * <p>La tabla es lo que hace que la operacion sea <b>realmente</b> asincrona: la peticion HTTP que
 * la lanza termina de inmediato con un 202 y el identificador de esta fila, y el progreso deja de
 * depender de que el cliente siga conectado. Guardarlo en memoria habria bastado para la demo y
 * habria fallado en lo unico que importa: un reinicio del proceso, o una consulta desde la replica
 * que no lanzo el trabajo.</p>
 *
 * <p>No extiende {@code BaseEntity} a proposito. No es un dato maestro: no se audita con Envers, no
 * publica eventos de dominio, no se cachea y no tiene borrado logico. Colgarlo de la jerarquia de
 * negocio solo le habria traido columnas y comportamiento que no usa.</p>
 *
 * <p>Del fichero de una exportacion se guarda <b>solo el nombre</b>, nunca la ruta absoluta: el
 * directorio de salida es configuracion del entorno ({@code app.jobs.profile.export-directory}) y
 * se resuelve en el momento de la descarga. Asi la fila sigue siendo valida si el despliegue cambia
 * de directorio, y no hay forma de que un nombre guardado apunte fuera de el.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "async_job")
public class AsyncJob {

    /**
     * Identificador publico del trabajo.
     *
     * <p>UUID y no una secuencia: el identificador se devuelve al cliente en el 202 y en la
     * cabecera {@code Location}, y un contador correlativo dejaria contar cuantas exportaciones
     * hace el sistema y sondear las de otros. Ademas lo genera la aplicacion, de modo que el
     * {@code Location} se puede construir sin haber ido todavia a la base de datos.</p>
     */
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, updatable = false, length = 40)
    private JobType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private JobStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** Instante en que el hilo de fondo cogio el trabajo. Nulo mientras esta PENDING. */
    private Instant startedAt;

    /** Instante en que alcanzo un estado terminal. */
    private Instant finishedAt;

    /**
     * Ultima señal de vida del proceso que ejecuta el trabajo.
     *
     * <p>Es lo que hace que los topes de concurrencia sean de todo el despliegue y no de cada
     * replica: el cupo lo ocupan los trabajos que <b>laten</b>, no los que tienen la fila en
     * RUNNING. Sin esto, una replica que muriera a mitad de una exportacion se llevaria un hueco
     * para siempre, y el sintoma —los trabajos empiezan a rechazarse sin motivo aparente— aparece
     * mucho despues de la causa.</p>
     *
     * <p>Lo refresca un unico UPDATE por replica y pasada, no uno por trabajo, y es independiente
     * del progreso: un elemento que tarde cinco minutos no puede hacer que el trabajo parezca
     * muerto.</p>
     */
    @Column(nullable = false)
    private Instant heartbeatAt;

    /** Via exportada. Solo para {@link JobType#PROFILE_EXPORT}. */
    private Long trackId;

    /** Formato de columnas del CSV. Solo para {@link JobType#PROFILE_EXPORT}. */
    @Column(length = 30)
    private String mapperType;

    /**
     * Nombre del CSV generado, sin directorio. Solo para {@link JobType#PROFILE_EXPORT} y solo
     * cuando el trabajo termino bien.
     */
    @Column(length = 255)
    private String fileName;

    /**
     * Elementos que hay que procesar.
     *
     * <p>Admite nulo porque en una exportacion no se conoce de antemano: la via se recorre por
     * ventanas y contar antes seria una consulta extra sobre la misma tabla. Se rellena al final
     * con las filas escritas.</p>
     */
    private Integer totalItems;

    @Column(nullable = false)
    private int processedItems;

    @Column(nullable = false)
    private int successfulItems;

    @Column(nullable = false)
    private int failedItems;

    /** Motivo del fallo global (FAILED) o del rechazo (REJECTED). */
    @Column(length = 1000)
    private String errorMessage;

    /**
     * Primeros errores por elemento, en JSON y <b>acotados</b> por
     * {@code app.jobs.profile.max-item-errors}.
     *
     * <p>Acotarlos no es una optimizacion menor: una carga de cien mil elementos con un mapeo mal
     * hecho produce cien mil errores identicos, y guardarlos enteros convierte cada fila de esta
     * tabla en varios megas y cada respuesta del endpoint de estado en una descarga. Con los
     * primeros N se diagnostica igual de bien; el recuento total sigue en {@link #failedItems}.</p>
     *
     * <p>{@code LONGVARCHAR} y no {@code @Lob}: sobre PostgreSQL, {@code @Lob} en un String crea una
     * columna {@code oid} que apunta a {@code pg_largeobject}, solo legible con la transaccion
     * abierta y que queda huerfana al borrar la fila. Mismo motivo que en {@code outbox_message}.
     */
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(columnDefinition = "text")
    private String errorDetailsJson;

    /**
     * Usuario que lanzo el trabajo, capturado en el hilo de la peticion.
     *
     * <p>Admite nulo: {@code CurrentUserService} devuelve vacio cuando no hay usuario autenticado y
     * aqui se respeta esa distincion en lugar de inventar un «system» que borraria la diferencia
     * entre un trabajo de una persona y uno de un proceso.</p>
     */
    @Column(length = 150)
    private String createdBy;
}
