package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.CantileverDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ProfileDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.TrackDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.ProfileFilter;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las listas filtradas de vias y perfiles ({@code POST /tracks/filter}, {@code POST /profiles/filter})
 * llamadas desde fuera de una transaccion, que es como las llama su controlador.
 *
 * <p>Con {@code open-in-view} a false, mapear la fila recorre una coleccion perezosa: las estaciones
 * de la via ({@code stationIds}) y las mensulas del perfil. Sin transaccion en el servicio eso es
 * {@code LazyInitializationException}, y las dos rutas respondian 500 {@code TEC-999} en cuanto habia
 * datos: el backoffice no podia abrir Vias ni Perfiles. Los tests con transaccion alrededor no lo
 * veian, porque la sesion seguia abierta al mapear.
 *
 * <p>Los datos se insertan con SQL, como en {@code GetByIdOutsideTransactionIT}: darlos de alta por
 * el servicio no taparia nada aqui, pero asi la prueba no depende de validadores ni de catalogos. Se
 * afirma sobre lo que solo esta en la coleccion, que es lo que obliga a inicializarla.
 */
@SpringBootTest
@DisplayName("Listas filtradas sin transaccion del llamante")
class FilterOutsideTransactionIT {

    private static final long PACKAGE = -71L;
    private static final long STATION = -72L;
    private static final long TRACK = -73L;
    private static final long PROFILE = -74L;
    private static final long CANTILEVER = -75L;

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
    }

    @AfterEach
    void limpia() {
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
}
