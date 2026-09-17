package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.entity.commons.IEntity;
import com.alejandro.mtoconfiguration.masterdata.messaging.MasterDataEventPublisher;
import com.alejandro.mtoconfiguration.masterdata.messaging.MasterDataOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.LongFunction;

/**
 * Publica un lote del republicado: <b>lee y escribe dentro de la misma transaccion</b>.
 *
 * <h2>Por que es un bean aparte del runner</h2>
 *
 * <p>Porque un {@code @Transactional} autoinvocado no pasa por el proxy de Spring y no abre
 * transaccion ninguna. Si este metodo viviera dentro de {@code MasterDataRepublishJobRunner}, la
 * anotacion seria decorativa y cada lectura y cada escritura irian en su propia transaccion
 * implicita, que es justo lo que este diseño existe para impedir.</p>
 *
 * <h2>Por que lectura y publicacion comparten transaccion</h2>
 *
 * <p>La fila de outbox recibe su {@code sequence_number} de la base al insertarla, y el consumidor
 * descarta por marca de agua lo que llega con un numero menor del que ya aplico. Si el trabajo
 * leyera la entidad, alguien la editase y el trabajo escribiera su fila despues, el republicado
 * viajaria con un numero <b>mas alto</b> y datos <b>mas viejos</b>: pisaria la edicion en destino,
 * en silencio y sin que nada fallara aqui.</p>
 *
 * <p>Compartir transaccion estrecha esa ventana de todo el trabajo a un lote, no la cierra: sin
 * bloqueo pesimista, una edicion confirmada entre la lectura y el INSERT todavia puede quedar con
 * un numero menor. Cerrarla del todo exigiria bloquear cada fila leida, precio que un republicado
 * puntual no justifica cobrarle al trafico normal; lo correcto es lanzarlo en una ventana sin
 * ediciones.</p>
 *
 * <h2>Por que devuelve el resultado en vez de tocar el progreso</h2>
 *
 * <p>Porque los contadores tienen que contar lo que se <b>confirmo</b>. Escribiendolos desde dentro,
 * un lote que fallara al hacer commit —basta un elemento que deje la transaccion marcada para
 * rollback— dejaria contados como buenos unos eventos que nunca llegaron a existir. Devolviendo el
 * resultado, el runner lo aplica solo despues de que la transaccion haya terminado bien.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
class MasterDataRepublishBatchPublisher {

    /** Elemento que no se pudo republicar, con lo que hace falta para contarlo. */
    record FailedItem(int index, String code, String message) {
    }

    /** Lo que confirmo un lote. */
    record BatchResult(int succeeded, List<FailedItem> failures) {
    }

    private final MasterDataEventPublisher masterDataEventPublisher;

    /**
     * Relee cada id con el grafo de mensajeria y escribe su evento como {@code UPDATED}.
     *
     * <p>El metodo es publico aunque la clase no lo sea: publico es la visibilidad sobre la que las
     * reglas de proxy de Spring no admiten matices, y la clase package-private ya impide que esto
     * se use desde fuera del paquete. Un {@code @Transactional} que no llegara a aplicarse no
     * fallaria por ningun sitio: dejaria cada lectura y cada escritura en su propia transaccion
     * implicita —justo lo que este diseño existe para impedir— y nadie se enteraria.</p>
     *
     * @param reader    el {@code findByIdForMessaging} del repositorio que toque; el mismo que usa
     *                  el listener de un cambio real, para que el payload republicado sea identico
     *                  y no haya un select por relacion en el mapper
     * @param indexBase posicion del primer elemento del lote dentro del trabajo, para que el indice
     *                  de un error apunte al elemento y no a su posicion dentro del lote
     */
    @Transactional
    public BatchResult publishBatch(List<Long> ids,
                                    String entityName,
                                    LongFunction<Optional<? extends IEntity>> reader,
                                    int indexBase) {

        int succeeded = 0;
        List<FailedItem> failures = new ArrayList<>();

        for (int i = 0; i < ids.size(); i++) {
            Long id = ids.get(i);
            int index = indexBase + i;

            Optional<? extends IEntity> entity = reader.apply(id);

            if (entity.isEmpty()) {
                // Se borro entre que se leyo la pagina de ids y se llego a el. No es un fallo del
                // republicado —su borrado ya publico su propio evento DELETED—, pero se cuenta
                // para que el recuento cuadre con el total anunciado en el 202.
                log.debug("El {} id={} ya no existe; no se republica", entityName, id);
                failures.add(new FailedItem(index, "NotFound",
                        "El elemento %d ya no existe".formatted(id)));
                continue;
            }

            masterDataEventPublisher.publish(entity.get(), MasterDataOperation.UPDATED);
            succeeded++;
        }

        return new BatchResult(succeeded, List.copyOf(failures));
    }
}
