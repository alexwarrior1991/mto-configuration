package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/**
 * Ajustes de los trabajos en segundo plano ({@code app.jobs.*}).
 *
 * <p>Los topes de concurrencia son el motivo principal de que esto sea configurable. Un trabajo de
 * fondo no es gratis aunque corra en un hilo virtual: consume una conexion de HikariCP durante todo
 * su recorrido y, en el caso de la exportacion, tambien disco. Diez exportaciones simultaneas de
 * vias grandes vacian el pool y dejan sin conexiones a las peticiones normales, que es la forma mas
 * tonta de tumbar la API con trabajo que nadie tenia prisa por recibir.</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.jobs")
public class AsyncJobProperties {

    private final ProfileJobs profile = new ProfileJobs();

    @Getter
    @Setter
    public static class ProfileJobs {

        /**
         * Exportaciones de perfiles simultaneas.
         *
         * <p>Dos: cada una mantiene abierta una transaccion de solo lectura mientras recorre la via
         * entera por ventanas, asi que el numero es directamente conexiones del pool retenidas
         * durante minutos.</p>
         */
        private int exportMaxConcurrency = 2;

        /**
         * Cargas masivas de perfiles simultaneas.
         *
         * <p>Una. Una carga masiva escribe, y escribe mucho: cada elemento abre su propia
         * transaccion, toca la tabla, publica su evento en el outbox e invalida cache. Dejar correr
         * varias a la vez multiplica la contencion sobre las mismas filas sin acabar antes.</p>
         */
        private int bulkMaxConcurrency = 1;

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
         * Cada cuantos elementos se refresca el progreso en la base de datos.
         *
         * <p>No se escribe en cada elemento a proposito: seria un UPDATE por elemento, es decir,
         * duplicar el numero de escrituras de la carga entera solo para mover un contador. Con 25,
         * quien sondea el estado ve avanzar el trabajo y el coste es del 4%.</p>
         */
        private int progressFlushInterval = 25;

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
}
