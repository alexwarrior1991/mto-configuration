package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.model.commons.PageableDTO;
import com.alejandro.mtoconfiguration.model.commons.SearchRequestDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.CantileverDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.DisconnectorDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ProfileDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SectionInsulatorDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SectionInsulatorSwitchDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.TrackDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.DisconnectorFilter;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.ProfileFilter;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.SectionInsulatorFilter;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.TrackFilter;
import com.alejandro.mtoconfiguration.support.PostgresTestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las listas de vias, perfiles, seccionadores y aisladores llamadas desde fuera de una transaccion,
 * que es como las llama su controlador: las filtradas ({@code POST /{recurso}/filter}, y las de una
 * estacion de los dos ultimos), las genericas de {@code BaseService} ({@code GET /{recurso}/paged} y
 * {@code POST /{recurso}/search}) y la lista entera, que solo sirve {@code GET /async/{recurso}}.
 *
 * <p>Con {@code open-in-view} a false, mapear la fila recorre lo perezoso: las estaciones de la via
 * ({@code stationIds}), las mensulas del perfil, el perfil del que cuelga un seccionador
 * ({@code profileCode}, {@code profileKp}) y las agujas del aislador. Sin transaccion en el servicio
 * eso es {@code LazyInitializationException}, y las rutas respondian 500 {@code TEC-999} en cuanto
 * habia datos: el backoffice no podia abrir Vias ni Perfiles, y una lista de seccionadores o de
 * aisladores con datos tampoco se podia leer. Los tests con transaccion alrededor no lo veian, porque
 * la sesion seguia abierta al mapear. Estaciones, paquetes, mensulas y brazos no entran: su fila solo
 * lee el id de sus referencias, que el proxy tiene sin ir a la base, y el brazo de una mensula se carga
 * con ella, porque es el lado inverso de un uno a uno.
 *
 * <p>Los datos se insertan con SQL, como en {@code GetByIdOutsideTransactionIT}: darlos de alta por
 * el servicio no taparia nada aqui, pero asi la prueba no depende de validadores ni de catalogos. Se
 * afirma sobre lo que solo esta en la coleccion, que es lo que obliga a inicializarla.
 */
@SpringBootTest
@DisplayName("Listas sin transaccion del llamante")
class FilterOutsideTransactionIT {

    private static final long PACKAGE = -71L;
    private static final long STATION = -72L;
    private static final long TRACK = -73L;
    private static final long PROFILE = -74L;
    private static final long CANTILEVER = -75L;
    private static final long DISCONNECTOR = -76L;
    private static final long INSULATOR = -77L;
    private static final long SWITCH = -78L;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.registerProperties(registry);
        registry.add("app.lov.seed-on-startup", () -> "false");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TrackService trackService;
    @Autowired
    private ProfileService profileService;
    @Autowired
    private DisconnectorService disconnectorService;
    @Autowired
    private SectionInsulatorService sectionInsulatorService;

    @BeforeEach
    void inserta() {
        jdbcTemplate.update("""
                insert into execution_package (id, name, enabled, deleted, initial_package,
                        length, start_date, end_date, create_date, create_user,
                        version_date, version_user, version_number)
                values (?, 'IT-FILTER', true, false, false, 0, date '2020-01-01',
                        date '2020-12-31', now(), 'test', now(), 'test', 1)
                """, PACKAGE);
        jdbcTemplate.update("""
                insert into station (id, name, deleted, create_date, create_user,
                        version_date, version_user, version_number)
                values (?, 'IT-FILTER-STATION', false, now(), 'test', now(), 'test', 1)
                """, STATION);
        jdbcTemplate.update("""
                insert into track (id, name, status, deleted, execution_package_id, create_date,
                        create_user, version_date, version_user, version_number)
                values (?, 'IT-FILTER-TRACK', true, false, ?, now(), 'test', now(), 'test', 1)
                """, TRACK, PACKAGE);
        jdbcTemplate.update("insert into track_station (track_id, station_id) values (?, ?)", TRACK, STATION);
        jdbcTemplate.update("""
                insert into profile (id, profile_id, kilometric_point, track_id, deleted, create_date,
                        create_user, version_date, version_user, version_number)
                values (?, 'IT-FILTER-PROFILE', 1.000, ?, false, now(), 'test', now(), 'test', 1)
                """, PROFILE, TRACK);
        jdbcTemplate.update("""
                insert into cantilever (id, profile_id, deleted, create_date, create_user,
                        version_date, version_user, version_number)
                values (?, ?, false, now(), 'test', now(), 'test', 1)
                """, CANTILEVER, PROFILE);
        jdbcTemplate.update("""
                insert into disconnector (id, name, onload, profile_id, station_id, deleted, create_date,
                        create_user, version_date, version_user, version_number)
                values (?, 'IT-FILTER-DISCONNECTOR', true, ?, ?, false, now(), 'test', now(), 'test', 1)
                """, DISCONNECTOR, PROFILE, STATION);
        jdbcTemplate.update("""
                insert into section_insulator (id, name, status, station_id, track_id, kilometric_point,
                        installation_type, deleted, create_date, create_user, version_date, version_user,
                        version_number)
                values (?, 'IT-FILTER-INSULATOR', true, ?, ?, 1.500, 'IN_TRACK', false, now(), 'test',
                        now(), 'test', 1)
                """, INSULATOR, STATION, TRACK);
        jdbcTemplate.update("""
                insert into section_insulator_switch (id, code, section_insulator_id, track_id,
                        kilometric_point, turnout_denominator, status, deleted, create_date, create_user,
                        version_date, version_user, version_number)
                values (?, 'W31', ?, ?, 1.500, 9, true, false, now(), 'test', now(), 'test', 1)
                """, SWITCH, INSULATOR, TRACK);
    }

    @AfterEach
    void limpia() {
        jdbcTemplate.update("delete from section_insulator_switch where id = ?", SWITCH);
        jdbcTemplate.update("delete from section_insulator where id = ?", INSULATOR);
        jdbcTemplate.update("delete from disconnector where id = ?", DISCONNECTOR);
        jdbcTemplate.update("delete from cantilever where id = ?", CANTILEVER);
        jdbcTemplate.update("delete from profile where id = ?", PROFILE);
        jdbcTemplate.update("delete from track_station where track_id = ?", TRACK);
        jdbcTemplate.update("delete from track where id = ?", TRACK);
        jdbcTemplate.update("delete from station where id = ?", STATION);
        jdbcTemplate.update("delete from execution_package where id = ?", PACKAGE);
    }

    @Test
    @DisplayName("la fila de una via trae sus estaciones")
    void laFilaDeUnaViaTraeSusEstaciones() {
        Page<TrackDTO> page = trackService.getTracks(PageRequest.of(0, 10),
                new TrackFilter("IT-FILTER-TRACK", null, null, null, null));

        assertThat(page.getContent()).singleElement()
                .satisfies(track -> assertThat(track.getStationIds()).containsExactly(STATION));
    }

    @Test
    @DisplayName("un perfil de la lista trae sus mensulas")
    void unPerfilDeLaListaTraeSusMensulas() {
        Page<ProfileDTO> page = profileService.getProfiles(PageRequest.of(0, 10),
                new ProfileFilter("IT-FILTER-PROFILE", null, TRACK, null, null, null, null, null, null, null,
                        null, null, null, null));

        assertThat(page.getContent()).singleElement()
                .satisfies(profile -> assertThat(profile.getCantilevers())
                        .extracting(CantileverDTO::getId).containsExactly(CANTILEVER));
    }

    @Test
    @DisplayName("un seccionador de la lista trae el codigo y el KP de su perfil")
    void unSeccionadorDeLaListaTraeSuPerfil() {
        Page<DisconnectorDTO> page = disconnectorService.getDisconnectors(PageRequest.of(0, 10),
                new DisconnectorFilter("IT-FILTER-DISCONNECTOR", null, null, null, null));

        assertThat(page.getContent()).singleElement().satisfies(this::traeSuPerfil);
    }

    @Test
    @DisplayName("los seccionadores de una estacion, por id o por nombre, traen su perfil")
    void losSeccionadoresDeUnaEstacionTraenSuPerfil() {
        List<DisconnectorDTO> porId = disconnectorService.getDisconnectorByStationId(STATION);
        List<DisconnectorDTO> porNombre = disconnectorService.getDisconnectorsByStationName("IT-FILTER-STATION");

        assertThat(porId).singleElement().satisfies(this::traeSuPerfil);
        assertThat(porNombre).singleElement().satisfies(this::traeSuPerfil);
    }

    @Test
    @DisplayName("un aislador de la lista trae sus agujas")
    void unAisladorDeLaListaTraeSusAgujas() {
        Page<SectionInsulatorDTO> page = sectionInsulatorService.getSectionInsulators(PageRequest.of(0, 10),
                new SectionInsulatorFilter("IT-FILTER-INSULATOR", null, null, null, null, null, null));

        assertThat(page.getContent()).singleElement().satisfies(this::traeSusAgujas);
    }

    @Test
    @DisplayName("los aisladores de una estacion, por id o por nombre, traen sus agujas")
    void losAisladoresDeUnaEstacionTraenSusAgujas() {
        List<SectionInsulatorDTO> porId = sectionInsulatorService.getSectionInsulatorsByStationId(STATION);
        List<SectionInsulatorDTO> porNombre =
                sectionInsulatorService.getSectionInsulatorsByStationName("IT-FILTER-STATION");

        assertThat(porId).singleElement().satisfies(this::traeSusAgujas);
        assertThat(porNombre).singleElement().satisfies(this::traeSusAgujas);
    }

    @Test
    @DisplayName("la pagina (/paged) y la busqueda (/search) de vias traen las estaciones de cada via")
    void pagedYSearchDeViasTraenSusEstaciones() {
        for (Page<TrackDTO> page : List.of(
                trackService.findAll(primeraPaginaPorId()),
                trackService.search(busqueda("name", "IT-FILTER-TRACK")))) {
            assertThat(page.getContent()).filteredOn(track -> track.getId() == TRACK).singleElement()
                    .satisfies(track -> assertThat(track.getStationIds()).containsExactly(STATION));
        }
    }

    @Test
    @DisplayName("la pagina (/paged) y la busqueda (/search) de perfiles traen sus mensulas")
    void pagedYSearchDePerfilesTraenSusMensulas() {
        for (Page<ProfileDTO> page : List.of(
                profileService.findAll(primeraPaginaPorId()),
                profileService.search(busqueda("profileId", "IT-FILTER-PROFILE")))) {
            assertThat(page.getContent()).filteredOn(profile -> profile.getId() == PROFILE).singleElement()
                    .satisfies(profile -> assertThat(profile.getCantilevers())
                            .extracting(CantileverDTO::getId).containsExactly(CANTILEVER));
        }
    }

    @Test
    @DisplayName("la pagina (/paged) y la busqueda (/search) de seccionadores traen su perfil")
    void pagedYSearchDeSeccionadoresTraenSuPerfil() {
        // DisconnectorService es cacheable: su pagina y su busqueda se mapean dentro de PageCacheService.
        for (Page<DisconnectorDTO> page : List.of(
                disconnectorService.findAll(primeraPaginaPorId()),
                disconnectorService.search(busqueda("name", "IT-FILTER-DISCONNECTOR")))) {
            assertThat(page.getContent()).filteredOn(disconnector -> disconnector.getId() == DISCONNECTOR)
                    .singleElement().satisfies(this::traeSuPerfil);
        }
    }

    @Test
    @DisplayName("la pagina (/paged) y la busqueda (/search) de aisladores traen sus agujas")
    void pagedYSearchDeAisladoresTraenSusAgujas() {
        for (Page<SectionInsulatorDTO> page : List.of(
                sectionInsulatorService.findAll(primeraPaginaPorId()),
                sectionInsulatorService.search(busqueda("name", "IT-FILTER-INSULATOR")))) {
            assertThat(page.getContent()).filteredOn(insulator -> insulator.getId() == INSULATOR)
                    .singleElement().satisfies(this::traeSusAgujas);
        }
    }

    @Test
    @DisplayName("la lista entera (GET /async/{recurso}) de seccionadores y de aisladores trae su perfil y sus agujas")
    void laListaEnteraTraeLoPerezoso() {
        assertThat(disconnectorService.findAll()).filteredOn(disconnector -> disconnector.getId() == DISCONNECTOR)
                .singleElement().satisfies(this::traeSuPerfil);
        assertThat(sectionInsulatorService.findAll()).filteredOn(insulator -> insulator.getId() == INSULATOR)
                .singleElement().satisfies(this::traeSusAgujas);
    }

    /** Los ids de prueba son negativos: ordenados por id van primero, haya lo que haya en la base. */
    private static PageRequest primeraPaginaPorId() {
        return PageRequest.of(0, 50, Sort.by("id"));
    }

    /** Una busqueda por un filtro de texto, como la manda {@code POST /{recurso}/search}. */
    private static SearchRequestDTO busqueda(String filtro, String valor) {
        PageableDTO pageable = new PageableDTO();
        pageable.setPage(0);
        pageable.setSize(10);
        SearchRequestDTO request = new SearchRequestDTO();
        request.setFilters(new HashMap<>(Map.of(filtro, valor)));
        request.setPageable(pageable);
        return request;
    }

    private void traeSuPerfil(DisconnectorDTO disconnector) {
        assertThat(disconnector.getProfileId()).isEqualTo(PROFILE);
        assertThat(disconnector.getProfileCode()).isEqualTo("IT-FILTER-PROFILE");
        assertThat(new BigDecimal(disconnector.getProfileKp())).isEqualByComparingTo("1");
    }

    private void traeSusAgujas(SectionInsulatorDTO insulator) {
        assertThat(insulator.getSwitches())
                .extracting(SectionInsulatorSwitchDTO::getCode).containsExactly("W31");
    }
}
