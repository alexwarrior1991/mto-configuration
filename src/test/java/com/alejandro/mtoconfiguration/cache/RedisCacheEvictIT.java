package com.alejandro.mtoconfiguration.cache;

import com.alejandro.mtoconfiguration.configuration.cache.RedisCacheEvictService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Invalidacion de cache contra un Redis real: que el SCAN borre lo que debe y <b>solo</b> lo que
 * debe.
 *
 * <p>El test unitario comprueba que los patrones generados son los esperados. Esto comprueba lo
 * otro: que Redis, aplicando esos patrones, deja el keyspace como se pretende. Son cosas distintas
 * —un patron puede leerse bien y casar distinto de lo que uno cree— y aqui la diferencia se paga
 * cara: si la invalidacion granular de {@code Anchorage} arrastra {@code AnchorageFoundation} se
 * vacia media cache sin motivo, y si no arrastra lo suyo se sirven datos viejos.
 *
 * <p>Las claves se escriben <b>a mano</b>, sin pasar por {@code @Cacheable}. No es un atajo: los
 * aciertos de cache de este proyecto son inestables en el entorno de test y estan en cuarentena
 * (ver {@code RedisCacheIT}). Apoyar este test en esa ruta lo haria fallar por un motivo que no
 * tiene nada que ver con lo que se quiere probar. Escribiendo las claves directamente se ejercita
 * exactamente la unidad que interesa: el SCAN y el DEL.
 */
@Testcontainers(disabledWithoutDocker = true)
@ImportAutoConfiguration(DataRedisAutoConfiguration.class)
@SpringBootTest(classes = RedisCacheEvictService.class)
class RedisCacheEvictIT {

    private static final String APP = "mto-configuration";

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private RedisCacheEvictService service;

    @Autowired
    private StringRedisTemplate redis_;

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("cache.application", () -> APP);
    }

    @BeforeEach
    void limpiarYSembrar() {
        redis_.getConnectionFactory().getConnection().serverCommands().flushDb();

        // Un ejemplar de cada familia de clave que genera la aplicacion.
        escribir(
                // Servicios normales
                APP + "::normal:item::ProfileService:getById(1)",
                APP + "::normal:list::ProfileService:findAll",
                APP + "::normal:page::ProfileService:findAll(0,20)",
                APP + "::normal:search::ProfileService:search(x)",
                APP + "::normal:item::TrackService:getById(1)",
                // MasterDataService
                APP + "::lov:item::MasterDataService:getAnchorageByCodeAndMapToDTO(A)",
                APP + "::lov:list::MasterDataService:getAnchorageList",
                APP + "::lov:item::MasterDataService:getAnchorageFoundationByIdAndMapToDTO(1)",
                APP + "::lov:list::MasterDataService:getAnchorageFoundationList",
                APP + "::lov:item::MasterDataService:getPortalByCodeAndMapToDTO(P)",
                APP + "::lov:item::MasterDataService:getPortalTypeByCodeAndMapToDTO(P)",
                APP + "::lov:list::MasterDataService:getFoundationList",
                APP + "::lov:list::MasterDataService:getFoundationTypeList",
                // LovReferenceResolver
                APP + "::lov:item::lovIdByCode:Anchorage:A",
                APP + "::lov:item::lovIdByCode:AnchorageFoundation:A",
                // Servicios de catalogo
                APP + "::lov:item::AnchorageService:findById(1)",
                APP + "::lov:list::AnchorageService:findAll",
                APP + "::lov:item::AnchorageFoundationService:findById(1)");
    }

    private void escribir(String... claves) {
        for (String clave : claves) {
            redis_.opsForValue().set(clave, "valor");
        }
    }

    private Set<String> clavesVivas() {
        return redis_.keys("*");
    }

    @Test
    @DisplayName("invalidar un servicio borra sus cuatro caches y no toca las de otro servicio")
    void invalidarUnServicio() {
        service.evictNormalServiceCaches("ProfileService");

        assertThat(clavesVivas())
                .noneMatch(clave -> clave.contains("ProfileService"))
                .contains(APP + "::normal:item::TrackService:getById(1)");
    }

    @Test
    @DisplayName("invalidar un servicio no toca las caches de listas de valores")
    void invalidarUnServicioNoTocaLasLov() {
        int lovAntes = (int) clavesVivas().stream().filter(c -> c.contains("::lov:")).count();

        service.evictNormalServiceCaches("ProfileService");

        assertThat(clavesVivas()).filteredOn(c -> c.contains("::lov:")).hasSize(lovAntes);
    }

    @Test
    @DisplayName("invalidar Anchorage NO arrastra AnchorageFoundation")
    void anchorageNoArrastraAnchorageFoundation() {
        // El caso que justifica que los patrones esten anclados a los sufijos reales.
        service.evictLovCaches("Anchorage");

        assertThat(clavesVivas())
                .as("todo lo de Anchorage debe desaparecer")
                .doesNotContain(
                        APP + "::lov:item::MasterDataService:getAnchorageByCodeAndMapToDTO(A)",
                        APP + "::lov:list::MasterDataService:getAnchorageList",
                        APP + "::lov:item::lovIdByCode:Anchorage:A",
                        APP + "::lov:item::AnchorageService:findById(1)",
                        APP + "::lov:list::AnchorageService:findAll")
                .as("y nada de AnchorageFoundation debe caer con ello")
                .contains(
                        APP + "::lov:item::MasterDataService:getAnchorageFoundationByIdAndMapToDTO(1)",
                        APP + "::lov:list::MasterDataService:getAnchorageFoundationList",
                        APP + "::lov:item::lovIdByCode:AnchorageFoundation:A",
                        APP + "::lov:item::AnchorageFoundationService:findById(1)");
    }

    @Test
    @DisplayName("invalidar Portal NO arrastra PortalType")
    void portalNoArrastraPortalType() {
        service.evictLovCaches("Portal");

        assertThat(clavesVivas())
                .doesNotContain(APP + "::lov:item::MasterDataService:getPortalByCodeAndMapToDTO(P)")
                .contains(APP + "::lov:item::MasterDataService:getPortalTypeByCodeAndMapToDTO(P)");
    }

    @Test
    @DisplayName("invalidar Foundation NO arrastra FoundationType")
    void foundationNoArrastraFoundationType() {
        service.evictLovCaches("Foundation");

        assertThat(clavesVivas())
                .doesNotContain(APP + "::lov:list::MasterDataService:getFoundationList")
                .contains(APP + "::lov:list::MasterDataService:getFoundationTypeList");
    }

    @Test
    @DisplayName("invalidar sin nombre barre TODAS las caches de listas de valores")
    void invalidarTodasLasLov() {
        service.evictLovCaches();

        assertThat(clavesVivas())
                .noneMatch(clave -> clave.contains("::lov:"))
                .as("pero deja intactas las de entidades normales")
                .contains(APP + "::normal:item::ProfileService:getById(1)");
    }

    @Test
    @DisplayName("un nombre en blanco degrada a barrer todas, que es el lado seguro")
    void nombreEnBlanco() {
        service.evictLovCaches("  ");

        assertThat(clavesVivas()).noneMatch(clave -> clave.contains("::lov:"));
    }

    @Test
    @DisplayName("invalidar un LOV que no tiene ninguna clave no borra nada de lo demas")
    void lovSinClaves() {
        Set<String> antes = clavesVivas();

        service.evictLovCaches("Sectioning");

        assertThat(clavesVivas()).containsExactlyInAnyOrderElementsOf(antes);
    }

    @Test
    @DisplayName("el prefijo de aplicacion acota el barrido: no se tocan claves ajenas")
    void noTocaClavesDeOtraAplicacion() {
        // Si varias aplicaciones comparten Redis, el prefijo es lo unico que las separa.
        escribir("otra-app::lov:item::MasterDataService:getAnchorageList",
                "otra-app::normal:item::ProfileService:getById(1)");

        service.evictLovCaches();
        service.evictNormalServiceCaches("ProfileService");

        assertThat(clavesVivas()).containsAll(List.of(
                "otra-app::lov:item::MasterDataService:getAnchorageList",
                "otra-app::normal:item::ProfileService:getById(1)"));
    }
}
