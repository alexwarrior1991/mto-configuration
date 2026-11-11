package com.alejandro.mtoconfiguration.configuration.cache;

import com.alejandro.mtoconfiguration.service.commons.BaseService;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * Vacia lo que una escritura de catalogo deja obsoleto, tras su commit.
 *
 * <p>No son solo las claves del propio LOV. Un DTO de entidad lleva embebida la entrada de catalogo
 * entera (codigo y descripcion): el esquema de via, y los DTO que si se cachean
 * ({@code BaseService.isCacheable()}; hoy el seccionador, con su {@code disconnectorFunction}, y el
 * brazo, con su {@code steadyArmType}). Sin vaciarlos, un seccionador leido despues de corregir la
 * descripcion de su funcion seguia saliendo con la vieja hasta el TTL (seis horas en
 * {@code normal:item}).</p>
 *
 * <p>La lista de servicios no se escribe a mano: son <b>todos los que se declaran cacheables</b>,
 * preguntados al contexto. Un servicio que pase a cachearse queda cubierto sin tocar nada aqui. Se
 * vacian todos con cualquier catalogo, sin mirar cual embebe cual: un catalogo se edita casi nunca
 * y son cuatro patrones mas por servicio en la misma conexion, y a cambio no hay un mapa
 * catalogo-servicio que alguien tenga que acordarse de mantener.</p>
 */
@Component
public class LovCacheEvictionListener {

    private final RedisCacheEvictService redisCacheEvictService;
    private final ObjectProvider<BaseService<?, ?>> services;

    /** Los nombres no cambian con el contexto arrancado: se calculan una vez, al primer evento. */
    private volatile List<String> cacheableServiceNames;

    public LovCacheEvictionListener(RedisCacheEvictService redisCacheEvictService,
                                    ObjectProvider<BaseService<?, ?>> services) {
        this.redisCacheEvictService = redisCacheEvictService;
        this.services = services;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLovCacheEviction(LovCacheEvictionEvent event) {
        // Las claves del LOV, las de los servicios cacheables y el esquema de via, en una sola conexion.
        redisCacheEvictService.evictAfterLovWrite(event.lovName(), cacheableServiceNames());
    }

    /**
     * Nombre con el que cada servicio cacheable escribe sus claves: el de su clase, sin el proxy,
     * igual que {@code RedisCacheKeyGenerator} y que el evento de {@code BaseService}.
     */
    public List<String> cacheableServiceNames() {
        List<String> names = cacheableServiceNames;

        if (names == null) {
            names = services.stream()
                    .filter(BaseService::isCacheable)
                    .map(service -> AopUtils.getTargetClass(service).getSimpleName())
                    .distinct()
                    .sorted()
                    .toList();
            cacheableServiceNames = names;
        }

        return names;
    }
}
