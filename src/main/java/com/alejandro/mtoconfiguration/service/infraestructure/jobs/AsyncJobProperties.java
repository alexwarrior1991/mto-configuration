package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Ajustes de los trabajos en segundo plano ({@code app.jobs.*}).
 *
 * <p>Los topes de concurrencia son el motivo principal de que esto sea configurable. Un trabajo de
 * fondo no es gratis aunque corra en un hilo virtual: consume una conexion de HikariCP durante todo
 * su recorrido y, en el caso de la exportacion, tambien disco. Diez exportaciones simultaneas de
 * vias grandes vacian el pool y dejan sin conexiones a las peticiones normales, que es la forma mas
 * tonta de tumbar la API con trabajo que nadie tenia prisa por recibir.</p>
 *
 * <p>Los topes son <b>de todo el despliegue</b>, no de cada replica: se cuentan contra la tabla y
 * no contra un semaforo en memoria, que era uno por JVM y multiplicaba el limite por el numero de
 * replicas. Ver {@code AsyncJobStore.createClaimingSlot}.</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.jobs")
public class AsyncJobProperties {

    private final Heartbeat heartbeat = new Heartbeat();
    private final Purge purge = new Purge();
    private final ProfileJobs profile = new ProfileJobs();
    private final LovJobs lov = new LovJobs();

    /** Cada cuanto se refresca la foto que publican las metricas. */
    private Duration metricsRefreshDelay = Duration.ofSeconds(30);

    /**
     * Señal de vida de los trabajos en curso.
     *
     * <p>Es lo que permite que los topes sean de clúster sin que un proceso muerto se lleve un hueco
     * para siempre: un trabajo ocupa sitio mientras late, no mientras su fila diga RUNNING. Sin
     * esto, una réplica que muriera a mitad de una exportación dejaría el tope reducido de forma
     * permanente y nadie sabría por qué empiezan a rechazarse trabajos.</p>
     */
    @Getter
    @Setter
    public static class Heartbeat {

        /**
         * Cada cuanto late un trabajo en curso.
         *
         * <p>Es un UPDATE por réplica y pasada —no por trabajo—: todos los trabajos vivos de la
         * réplica se refrescan con una sola consulta.</p>
         */
        private Duration interval = Duration.ofSeconds(15);

        /**
         * Silencio a partir del cual se da un trabajo por muerto y se libera su hueco.
         *
         * <p>Holgado respecto a {@link #interval} a propósito: con varios latidos de margen, una
         * pausa larga de GC o un pico de carga en la base de datos no bastan para que un trabajo
         * perfectamente vivo sea declarado difunto y su hueco entregado a otro.</p>
         */
        private Duration timeout = Duration.ofMinutes(2);

        /**
         * Cada cuanto se marcan como fallidos los trabajos que dejaron de latir.
         *
         * <p>No hace falta que sea agresivo: el hueco ya queda libre en cuanto el latido se enfría,
         * porque el recuento de ocupación solo mira los que laten. Esto solo corrige el estado que
         * se le enseña a quien consulta, para que un trabajo difunto no aparezca eternamente como
         * RUNNING.</p>
         */
        private Duration reaperFixedDelay = Duration.ofMinutes(1);
    }

    /**
     * Borrado de los trabajos viejos y de sus ficheros.
     *
     * <p>Sin esto {@code async_job} solo crece y {@code export-directory} acumula CSV para siempre.
     * El disco es el que primero da problemas: una exportación de una vía grande son megas, y no
     * hay nada que los borre.</p>
     */
    @Getter
    @Setter
    public static class Purge {

        private boolean enabled = true;

        /** Margen para investigar un trabajo con su fila y su fichero delante. */
        private Duration retention = Duration.ofDays(7);

        /** Se borra por lotes: un DELETE de golpe hincha el WAL y bloquea el vacuum de la tabla. */
        private int batchSize = 200;

        /** Tope de trabajo por pasada, para que la primera ejecución no dure horas. */
        private int maxBatchesPerRun = 20;

        private Duration fixedDelay = Duration.ofHours(1);
    }

    @Getter
    @Setter
    public static class ProfileJobs {

        /**
         * Exportaciones de perfiles simultaneas <b>en todo el despliegue</b>.
         *
         * <p>Dos: cada una mantiene abierta una transaccion de solo lectura mientras recorre la via
         * entera por ventanas, asi que el numero es directamente conexiones del pool retenidas
         * durante minutos.</p>
         */
        private int exportMaxConcurrency = 2;

        /**
         * Cargas masivas de perfiles simultaneas <b>en todo el despliegue</b>.
         *
         * <p>Una. Una carga masiva escribe, y escribe mucho: cada elemento abre su propia
         * transaccion, toca la tabla, publica su evento en el outbox e invalida cache. Dejar correr
         * varias a la vez multiplica la contencion sobre las mismas filas sin acabar antes.</p>
         */
        private int bulkMaxConcurrency = 1;

        /**
         * Elementos como maximo en una carga masiva.
         *
         * <p>El riesgo aparece antes de que exista el trabajo: el cuerpo entero se deserializa en
         * memoria en el hilo de la peticion, asi que un cliente con un fichero enorme tumba el
         * proceso sin llegar a crear ninguna fila. El tope al menos convierte eso en un 400.</p>
         */
        private int maxBulkItems = 50_000;

        /**
         * Directorio de los CSV generados.
         *
         * <p>Relativo al directorio de trabajo del proceso, como el {@code exports} que ya usaba el
         * endpoint antiguo. En un despliegue con varias replicas debe apuntar a almacenamiento
         * compartido, o la descarga fallara cuando la sirva una replica distinta de la que
         * genero el fichero.</p>
         */
        private Path exportDirectory = Path.of("exports");

        /**
         * Cada cuanto TIEMPO se refresca el progreso en la base de datos.
         *
         * <p>Por tiempo y no por numero de elementos, que es como estaba y era un error: un
         * elemento de una carga masiva es una transaccion completa, pero un elemento de una
         * exportacion es una linea de un CSV, tres ordenes de magnitud mas barata. Con una cadencia
         * por recuento, el mismo numero que resultaba despreciable en la carga (un UPDATE cada 25
         * INSERT) se convertia en cuatro mil escrituras para exportar una via de cien mil perfiles.
         * Por tiempo, las dos clases de trabajo se comportan bien con un unico valor.</p>
         *
         * <p>Ojo con subir los topes de concurrencia: durante una exportacion este volcado ocurre
         * <b>dentro</b> de la transaccion de solo lectura que recorre la via, y al ir en
         * {@code REQUIRES_NEW} pide una SEGUNDA conexion del pool mientras la primera sigue
         * retenida. Es decir, cada exportacion en curso puede llegar a ocupar dos conexiones a la
         * vez, y la suma de los topes debe quedar holgadamente por debajo del tamano del pool de
         * HikariCP.</p>
         */
        private Duration progressFlushInterval = Duration.ofSeconds(2);

        /**
         * Errores por elemento que se guardan, como maximo.
         *
         * <p>Una carga de cien mil elementos con un mapeo mal hecho produce cien mil errores
         * identicos. Guardarlos todos convierte la fila del trabajo en varios megas y la respuesta
         * del endpoint de estado en una descarga; con los primeros N se diagnostica igual, y el
         * recuento completo sigue estando en {@code failedItems}.</p>
         */
        private int maxItemErrors = 50;

        /** Longitud maxima del mensaje de cada error por elemento. */
        private int maxItemErrorMessageLength = 500;
    }

    /**
     * Ajustes de la importacion del catalogo maestro de LOVs ({@code app.jobs.lov.*}).
     *
     * <p>Es un trabajo de administracion que se lanza muy de vez en cuando —los datos
     * maestros, por definicion, casi no cambian—, asi que los numeros son deliberadamente
     * conservadores: lo que importa aqui es no estorbar al trafico normal, no acabar rapido.</p>
     */
    @Getter
    @Setter
    public static class LovJobs {

        // El tope de simultaneidad NO se configura aqui: LOV_IMPORT pertenece al grupo de
        // cupo BULK, asi que lo gobierna app.jobs.profile.bulk-max-concurrency junto con las
        // cargas masivas de perfiles. Es lo que se quiere —dos escrituras masivas a la vez
        // sobre las mismas tablas solo se estorban—, y anadir aqui una segunda perilla que
        // AsyncJobStore.maxConcurrencyOf no consulta seria un ajuste muerto.

        /**
         * Directorio donde se deja el informe descargable.
         *
         * <p>Mismo aviso que el de las exportaciones de perfiles: con varias replicas tiene que
         * apuntar a almacenamiento COMPARTIDO, o la descarga dara 410 cuando la atienda una
         * replica distinta de la que genero el informe.</p>
         */
        private Path reportDirectory = Path.of("lov-imports");

        // El volcado de progreso y el tope de errores por fila tampoco se configuran aqui: el
        // trabajo reutiliza ProfileJobProgress, que lee app.jobs.profile.progress-flush-interval
        // y app.jobs.profile.max-item-errors. Duplicarlos aqui daria dos perillas para lo mismo
        // y solo una de ellas tendria efecto.
    }

}
