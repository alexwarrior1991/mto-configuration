package com.alejandro.mtoconfiguration.configuration.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Borra claves de Redis por patron ({@code SCAN} + {@code DEL}): la otra mitad del circuito de
 * invalidacion, cuyos eventos publican {@code BaseService} y {@code AbstractLovCrudService} y
 * reciben {@code CacheEvictionListener} y {@code LovCacheEvictionListener} tras el commit.
 *
 * <p><b>Todo lo que vacia una escritura va por una sola conexion</b>
 * ({@link #evictAfterInfrastructureWrite}, {@link #evictAfterLovWrite}). Una escritura de perfil
 * son trece patrones (los cuatro suyos, los cuatro de cada servicio que deja obsoleto y el esquema
 * de via), y con una conexion por patron, con Redis caido, cada escritura pagaba trece intentos de
 * conexion fallidos y trece trazas completas. Los tests de integracion corren sin Redis e importan
 * el maestro de perfiles dos veces (mas de 23.000 escrituras de perfil): asi se tumbo el CI.
 *
 * <p><b>Respeta el cortocircuito</b> de {@link RedisCacheAvailability}, igual que la lectura
 * ({@code ResilientCache}): mientras la cache esta degradada no se intenta nada, y un fallo de
 * conexion aqui la degrada. Sin eso, con Redis inalcanzable (no rechazando, sino sin responder),
 * cada escritura esperaria el timeout de conexion antes de volver.
 *
 * <p><b>Lo que no se pudo vaciar no se pierde</b>: queda pendiente y se vacia con la siguiente
 * tanda que encuentre Redis. Sin eso, un corte de un segundo abriria el cortocircuito treinta y
 * todas las escrituras de esa ventana dejarian datos viejos hasta el TTL (seis horas un elemento).
 * Los patrones posibles son finitos (cuatro por servicio, cinco por catalogo y el esquema), asi
 * que el pendiente tiene techo. Lo que queda fuera: si tras el corte no se escribe nada, lo
 * pendiente espera a la siguiente escritura o al TTL, y un reinicio de la aplicacion lo pierde.
 *
 * <p>Un fallo no se propaga: la invalidacion ocurre despues del commit, y convertir una caida de
 * Redis en un error para quien ya guardo seria peor que servir datos obsoletos un rato.
 */
@Service
public class RedisCacheEvictService {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheEvictService.class);

    /**
     * Prefijo de las claves del esquema de via ({@code TrackSchematicService:getSchematic:<id>}),
     * el nombre que {@code RedisCacheKeyGenerator} saca de la clase del servicio.
     * {@code TrackSchematicServiceTest} lo pina contra el generador real.
     */
    public static final String TRACK_SCHEMATIC_KEY_PREFIX = "TrackSchematicService:";

    private final RedisConnectionFactory redisConnectionFactory;
    private final String applicationName;
    private final RedisCacheAvailability availability;

    /** Patrones que una tanda anterior no pudo vaciar: el cortocircuito estaba abierto o fallo Redis. */
    private final Set<String> pending = ConcurrentHashMap.newKeySet();

    public RedisCacheEvictService(
            RedisConnectionFactory redisConnectionFactory,
            @Value("${cache.application:mto-configuration}") String applicationName,
            RedisCacheAvailability availability) {
        this.redisConnectionFactory = redisConnectionFactory;
        this.applicationName = applicationName;
        this.availability = availability;
    }

    /**
     * Lo que vacia una escritura de infraestructura, en una conexion: las cuatro caches de cada
     * servicio de la lista (el que escribio y los que deja obsoletos,
     * {@code CacheEvictionListener.DEPENDENT_SERVICES}) y el esquema de via.
     */
    public void evictAfterInfrastructureWrite(List<String> serviceNames) {
        List<String> patterns = new ArrayList<>();
        serviceNames.forEach(serviceName -> patterns.addAll(normalServicePatterns(serviceName)));
        patterns.add(trackSchematicPattern());
        evictByPatterns(serviceNames + " + track schematics", patterns);
    }

    /** Lo que vacia una escritura de catalogo, en una conexion: las claves del LOV y el esquema de via. */
    public void evictAfterLovWrite(String lovName) {
        List<String> patterns = new ArrayList<>(lovPatterns(lovName));
        patterns.add(trackSchematicPattern());
        evictByPatterns("LOV " + lovName + " + track schematics", patterns);
    }

    public void evictNormalServiceCaches(String serviceName) {
        evictByPatterns(serviceName, normalServicePatterns(serviceName));
    }

    /**
     * Vacia el esquema de <b>todas</b> las vias ({@code TrackSchematicService}).
     *
     * <p>Va en cada escritura de infraestructura o de catalogo: el esquema de una via depende de
     * ocho maestros (paquete, estacion, via, perfil, mensula, brazo, seccionador y aislador) y de
     * sus LOV, y el evento de invalidacion solo trae el nombre del servicio que escribio, no la
     * via. Barrer las claves de todas las vias (como mucho una por via) es un {@code SCAN} mas;
     * discriminar cual no seria posible sin cambiar lo que publica cada servicio.
     */
    public void evictTrackSchematics() {
        evictByPatterns("track schematics", List.of(trackSchematicPattern()));
    }

    public void evictLovCaches() {
        evictByPatterns("all LOV", allLovPatterns());
    }

    /**
     * Invalidación granular de un LOV concreto.
     * Los patrones están anclados a los sufijos reales (get&lt;Lov&gt;By* y get&lt;Lov&gt;List)
     * para no arrastrar LOV con prefijo común (Anchorage vs AnchorageFoundation,
     * Portal vs PortalType, Foundation vs FoundationType).
     */
    public void evictLovCaches(String lovName) {
        evictByPatterns("LOV " + lovName, lovPatterns(lovName));
    }

    private List<String> normalServicePatterns(String serviceName) {
        return List.of(
                applicationName + "::" + CacheNames.NORMAL_ITEM + "::" + serviceName + ":*",
                applicationName + "::" + CacheNames.NORMAL_LIST + "::" + serviceName + ":*",
                applicationName + "::" + CacheNames.NORMAL_PAGE + "::" + serviceName + ":*",
                applicationName + "::" + CacheNames.NORMAL_SEARCH + "::" + serviceName + ":*");
    }

    private String trackSchematicPattern() {
        return applicationName + "::" + CacheNames.NORMAL_ITEM + "::" + TRACK_SCHEMATIC_KEY_PREFIX + "*";
    }

    private List<String> allLovPatterns() {
        return List.of(
                applicationName + "::" + CacheNames.LOV_ITEM + "::*",
                applicationName + "::" + CacheNames.LOV_LIST + "::*");
    }

    private List<String> lovPatterns(String lovName) {
        if (StringUtils.isBlank(lovName)) {
            return allLovPatterns();
        }
        return List.of(
                // Claves de MasterDataService: getXByIdAndMapToDTO / getXByCodeAndMapToDTO / getXList
                lovPattern(CacheNames.LOV_ITEM, masterDataItemKeyPattern(lovName)),
                lovPattern(CacheNames.LOV_LIST, masterDataListKeyPattern(lovName)),
                // Clave del resolver código -> id (LovReferenceResolver)
                lovPattern(CacheNames.LOV_ITEM, lovIdByCodeKeyPattern(lovName)),
                // Claves del servicio LOV concreto (AbstractLovCrudService)
                lovPattern(CacheNames.LOV_ITEM, lovServiceKeyPattern(lovName)),
                lovPattern(CacheNames.LOV_LIST, lovServiceKeyPattern(lovName)));
    }

    private String masterDataItemKeyPattern(String lovName) {
        return "MasterDataService:get" + lovName + "By*";
    }

    private String masterDataListKeyPattern(String lovName) {
        return "MasterDataService:get" + lovName + "List";
    }

    private String lovIdByCodeKeyPattern(String lovName) {
        return "lovIdByCode:" + lovName + ":*";
    }

    private String lovServiceKeyPattern(String lovName) {
        return lovName + "Service:find*";
    }

    private String lovPattern(String cacheName, String keyPattern) {
        return applicationName + "::" + cacheName + "::" + keyPattern;
    }

    /**
     * Una tanda: lo pendiente mas los patrones de esta escritura, por la misma conexion. Con el
     * cortocircuito abierto no se intenta nada y todo queda pendiente; el primer fallo corta la
     * tanda (sin conexion no hay patron que valga), deja pendiente lo que faltaba y degrada la
     * cache, que ya escribe la traza una vez por ventana.
     */
    private void evictByPatterns(String scope, List<String> patterns) {
        if (!availability.isAvailable()) {
            pending.addAll(patterns);
            log.debug("Redis cache degraded: eviction for {} left pending ({} patterns pending)", scope, pending.size());
            return;
        }

        List<String> drained = takePending();
        Set<String> batch = new LinkedHashSet<>(drained);
        batch.addAll(patterns);
        List<String> ordered = new ArrayList<>(batch);

        int done = 0;
        long deleted = 0;
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            for (String pattern : ordered) {
                deleted += deleteMatching(connection, pattern);
                done++;
            }
            log.info("Redis cache eviction completed for {}{}: {} keys deleted over {} patterns",
                    scope, drained.isEmpty() ? "" : " (+" + drained.size() + " pending)", deleted, ordered.size());
        } catch (Exception e) {
            pending.addAll(ordered.subList(done, ordered.size()));
            availability.markDegraded("eviction", e);
            log.error("Redis cache eviction failed for {} ({} of {} patterns left pending): {}",
                    scope, ordered.size() - done, ordered.size(), e.toString());
            log.debug("Redis cache eviction failure for {}", scope, e);
        }
    }

    /**
     * Saca lo pendiente de uno en uno: lo que otra escritura deje pendiente mientras esta tanda
     * corre se queda para la siguiente, en vez de borrarlo aqui sin haberlo vaciado.
     */
    private List<String> takePending() {
        List<String> taken = new ArrayList<>();
        for (String pattern : pending) {
            if (pending.remove(pattern)) {
                taken.add(pattern);
            }
        }
        return taken;
    }

    private long deleteMatching(RedisConnection connection, String pattern) {
        ScanOptions options = ScanOptions.scanOptions()
                .match(pattern)
                .count(1000)
                .build();

        List<byte[]> keysToDelete = new ArrayList<>();
        try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
            while (cursor.hasNext()) {
                keysToDelete.add(cursor.next());
            }
        }

        if (!keysToDelete.isEmpty()) {
            connection.keyCommands().del(keysToDelete.toArray(new byte[0][]));
        }

        log.debug("Redis cache eviction pattern {}: {} keys deleted", pattern, keysToDelete.size());
        return keysToDelete.size();
    }
}
