package com.alejandro.mtoconfiguration.configuration.cache;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;

/**
 * Vacia las caches de infraestructura tras el commit de una escritura.
 *
 * <p>El evento solo trae el nombre del servicio que escribio, y la invalidacion es por servicio
 * ({@code RedisCacheEvictService.evictNormalServiceCaches}). Eso basta cuando cada servicio es el
 * unico que toca su tabla y su DTO solo lleva datos de su fila; aqui no es asi, y por eso este
 * listener vacia tambien lo de otros servicios ({@link #DEPENDENT_SERVICES}) y el esquema de via.
 */
@Component
public class CacheEvictionListener {

    /**
     * Servicios cuyas caches quedan obsoletas cuando escribe OTRO servicio, y por eso se vacian
     * tambien con su evento. Solo hay dos servicios cacheables ({@code DisconnectorService} y
     * {@code SteadyArmService}, ver {@code BaseService.isCacheable()}), y sus entradas dependen de
     * escrituras ajenas por dos caminos:
     * <ul>
     *   <li><b>Campos copiados de otra entidad</b>: {@code DisconnectorDTO.profileCode} y
     *       {@code profileKp} salen del perfil ({@code DisconnectorMapper}). Cambiar el
     *       {@code profileId} o el {@code kp} de un perfil deja mintiendo al seccionador cacheado
     *       hasta el TTL (seis horas en {@code normal:item}).</li>
     *   <li><b>Filas escritas anidadas</b>: un seccionador se modifica tambien dentro de un
     *       {@code PUT /profiles/{id}} (1:1) o de un {@code PUT /stations/{id}} (su coleccion), y
     *       un brazo dentro de un {@code PUT /cantilevers/{id}} (1:1); y como las vias reconcilian
     *       sus perfiles, las estaciones sus vias y los paquetes sus vias y estaciones
     *       ({@code TrackMapper}, {@code StationMapper}, {@code ExecutionPackageMapper}), esas
     *       escrituras llegan hasta el seccionador y el brazo publicando solo el nombre del
     *       padre.</li>
     * </ul>
     * Una clave de este mapa es quien escribe; sus valores, a quien deja obsoleto.
     * {@code CacheEvictionListenerTest} comprueba que todos son servicios que existen.
     */
    static final Map<String, List<String>> DEPENDENT_SERVICES = Map.of(
            "ProfileService", List.of("DisconnectorService", "SteadyArmService"),
            "TrackService", List.of("DisconnectorService", "SteadyArmService"),
            "StationService", List.of("DisconnectorService", "SteadyArmService"),
            "ExecutionPackageService", List.of("DisconnectorService", "SteadyArmService"),
            "CantileverService", List.of("SteadyArmService"));

    private final RedisCacheEvictService redisCacheEvictService;

    public CacheEvictionListener(RedisCacheEvictService redisCacheEvictService) {
        this.redisCacheEvictService = redisCacheEvictService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCacheEviction(CacheEvictionEvent event) {
        redisCacheEvictService.evictNormalServiceCaches(event.serviceName());
        for (String dependent : DEPENDENT_SERVICES.getOrDefault(event.serviceName(), List.of())) {
            redisCacheEvictService.evictNormalServiceCaches(dependent);
        }
        // El esquema de via depende de los ocho maestros y el evento solo dice quien escribio.
        redisCacheEvictService.evictTrackSchematics();
    }
}
