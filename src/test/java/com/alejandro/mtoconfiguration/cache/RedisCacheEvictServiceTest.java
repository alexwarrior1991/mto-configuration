package com.alejandro.mtoconfiguration.cache;

import com.alejandro.mtoconfiguration.configuration.cache.RedisCacheEvictService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisKeyCommands;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Patrones de invalidacion que se mandan a Redis.
 *
 * <p>Es la otra mitad del circuito de caché: {@code BaseService} y {@code AbstractLovCrudService}
 * publican el evento, los listeners lo reciben y esta clase construye el patron {@code SCAN} que
 * borra las claves. Todo el patron se arma concatenando cadenas, asi que un fallo aqui no da
 * error: simplemente no se borra lo que habia que borrar y el servicio sigue devolviendo datos
 * viejos hasta que expire el TTL.
 *
 * <p>Lo mas delicado son los patrones de un LOV concreto. Estan anclados a los sufijos reales
 * ({@code By*}, {@code List}, {@code Service:find*}) precisamente para que invalidar
 * {@code Anchorage} no arrastre {@code AnchorageFoundation}, ni {@code Portal} a
 * {@code PortalType}, ni {@code Foundation} a {@code FoundationType}. Sin ese anclaje se borraria
 * de mas —lo que solo cuesta rendimiento— pero sobre todo se pierde la garantia de que la
 * invalidacion granular es granular.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisCacheEvictServiceTest {

    private static final String APP = "mto-configuration";

    @Mock
    private RedisConnectionFactory connectionFactory;
    @Mock
    private RedisConnection connection;
    @Mock
    private RedisKeyCommands keyCommands;
    @Mock
    private Cursor<byte[]> cursor;

    private RedisCacheEvictService service;

    @BeforeEach
    void setUp() {
        when(connectionFactory.getConnection()).thenReturn(connection);
        when(connection.keyCommands()).thenReturn(keyCommands);
        doReturn(cursor).when(keyCommands).scan(any(ScanOptions.class));
        when(cursor.hasNext()).thenReturn(false);

        service = new RedisCacheEvictService(connectionFactory, APP);
    }

    /** Los patrones enviados a SCAN, en orden. */
    private List<String> patronesUsados() {
        ArgumentCaptor<ScanOptions> captor = ArgumentCaptor.forClass(ScanOptions.class);
        verify(keyCommands, org.mockito.Mockito.atLeastOnce()).scan(captor.capture());
        return captor.getAllValues().stream().map(ScanOptions::getPattern).toList();
    }

    @Nested
    @DisplayName("Entidades normales")
    class Normales {

        @Test
        @DisplayName("se barren las cuatro cachés del servicio, y solo las suyas")
        void cuatroCaches() {
            service.evictNormalServiceCaches("ProfileService");

            assertThat(patronesUsados()).containsExactly(
                    APP + "::normal:item::ProfileService:*",
                    APP + "::normal:list::ProfileService:*",
                    APP + "::normal:page::ProfileService:*",
                    APP + "::normal:search::ProfileService:*");
        }

        @Test
        @DisplayName("el nombre del servicio ancla el patron: no arrastra a otro servicio")
        void noArrastraOtrosServicios() {
            service.evictNormalServiceCaches("TrackService");

            assertThat(patronesUsados()).allSatisfy(patron ->
                    assertThat(patron).contains("TrackService:").doesNotContain("ProfileService"));
        }
    }

    @Nested
    @DisplayName("Listas de valores")
    class Lov {

        @Test
        @DisplayName("sin nombre de LOV se barren las dos caches enteras")
        void todasLasLov() {
            service.evictLovCaches();

            assertThat(patronesUsados()).containsExactly(
                    APP + "::lov:item::*",
                    APP + "::lov:list::*");
        }

        @Test
        @DisplayName("un nombre en blanco degrada a barrer las dos caches enteras")
        void nombreEnBlanco() {
            // Vale mas pasarse borrando que dejar datos obsoletos sirviendose.
            service.evictLovCaches("   ");

            assertThat(patronesUsados()).containsExactly(
                    APP + "::lov:item::*",
                    APP + "::lov:list::*");
        }

        @Test
        @DisplayName("un LOV concreto barre sus cinco familias de claves")
        void lovConcreto() {
            service.evictLovCaches("Anchorage");

            assertThat(patronesUsados()).containsExactly(
                    // MasterDataService: getXBy... y getXList
                    APP + "::lov:item::MasterDataService:getAnchorageBy*",
                    APP + "::lov:list::MasterDataService:getAnchorageList",
                    // LovReferenceResolver: codigo -> id
                    APP + "::lov:item::lovIdByCode:Anchorage:*",
                    // AbstractLovCrudService: findById / findByCode / findAll
                    APP + "::lov:item::AnchorageService:find*",
                    APP + "::lov:list::AnchorageService:find*");
        }
    }

    @Nested
    @DisplayName("Prefijos comunes")
    class Prefijos {

        /**
         * Comprueba que el patron generado para {@code lov} NO casaria con una clave real de
         * {@code otroLov}. Es la propiedad que hace granular a la invalidacion granular.
         */
        private void noSeArrastran(String lov, String claveDelOtro) {
            service.evictLovCaches(lov);

            assertThat(patronesUsados())
                    .as("ningun patron de %s debe casar con %s", lov, claveDelOtro)
                    .noneSatisfy(patron -> assertThat(claveDelOtro).matches(comoRegex(patron)));
        }

        /** Traduce un patron glob de Redis a una expresion regular equivalente. */
        private String comoRegex(String patron) {
            return java.util.Arrays.stream(patron.split("\\*", -1))
                    .map(java.util.regex.Pattern::quote)
                    .collect(java.util.stream.Collectors.joining(".*"));
        }

        @Test
        @DisplayName("invalidar Anchorage no arrastra AnchorageFoundation")
        void anchorageNoArrastraAnchorageFoundation() {
            noSeArrastran("Anchorage",
                    APP + "::lov:item::MasterDataService:getAnchorageFoundationByIdAndMapToDTO(1)");
        }

        @Test
        @DisplayName("invalidar Portal no arrastra PortalType")
        void portalNoArrastraPortalType() {
            noSeArrastran("Portal",
                    APP + "::lov:item::MasterDataService:getPortalTypeByCodeAndMapToDTO(A)");
        }

        @Test
        @DisplayName("invalidar Foundation no arrastra FoundationType")
        void foundationNoArrastraFoundationType() {
            noSeArrastran("Foundation",
                    APP + "::lov:list::MasterDataService:getFoundationTypeList");
        }

        @Test
        @DisplayName("tampoco se arrastran las claves del servicio ni las del resolver")
        void tampocoServicioNiResolver() {
            noSeArrastran("Anchorage", APP + "::lov:item::AnchorageFoundationService:findById(1)");
            noSeArrastran("Anchorage", APP + "::lov:item::lovIdByCode:AnchorageFoundation:A");
        }

        @Test
        @DisplayName("pero si casa con las claves propias del LOV invalidado")
        void siCasaConLasPropias() {
            // La contraparte: si el anclaje fuese tan estricto que no casara ni con las suyas,
            // los tests anteriores pasarian sin que se borrase nada.
            service.evictLovCaches("Anchorage");

            assertThat(patronesUsados())
                    .anySatisfy(patron -> assertThat(APP + "::lov:item::MasterDataService:getAnchorageByCodeAndMapToDTO(A)")
                            .matches(comoRegex(patron)))
                    .anySatisfy(patron -> assertThat(APP + "::lov:item::AnchorageService:findById(1)")
                            .matches(comoRegex(patron)))
                    .anySatisfy(patron -> assertThat(APP + "::lov:item::lovIdByCode:Anchorage:A")
                            .matches(comoRegex(patron)));
        }
    }

    @Nested
    @DisplayName("Borrado y fallos")
    class BorradoYFallos {

        @Test
        @DisplayName("las claves encontradas se borran en una sola llamada")
        void borraLoEncontrado() {
            byte[] primera = "k1".getBytes(StandardCharsets.UTF_8);
            byte[] segunda = "k2".getBytes(StandardCharsets.UTF_8);
            when(cursor.hasNext()).thenReturn(true, true, false);
            when(cursor.next()).thenReturn(primera, segunda);

            service.evictLovCaches();

            verify(keyCommands, org.mockito.Mockito.atLeastOnce()).del(primera, segunda);
        }

        @Test
        @DisplayName("sin claves que casen no se llama a del")
        void sinClavesNoBorra() {
            service.evictLovCaches();

            verify(keyCommands, never()).del(any(byte[][].class));
        }

        @Test
        @DisplayName("si Redis falla no se propaga la excepcion")
        void redisCaidoNoRompe() {
            // La invalidacion ocurre despues de confirmar la transaccion: si aqui se propagara el
            // fallo, una caida de Redis convertiria un guardado ya escrito en un error para el
            // cliente. Se prefiere servir datos obsoletos hasta que expire el TTL.
            when(connectionFactory.getConnection()).thenThrow(new IllegalStateException("Redis caido"));

            assertThatCode(() -> service.evictNormalServiceCaches("ProfileService"))
                    .doesNotThrowAnyException();
        }
    }
}
