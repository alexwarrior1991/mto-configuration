package com.alejandro.mtoconfiguration.service.commons;

import com.alejandro.mtoconfiguration.core.exception.ConcurrencyException;
import com.alejandro.mtoconfiguration.core.exception.NotFoundException;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.StationDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.DisconnectorFunctionDTO;
import com.alejandro.mtoconfiguration.service.infraestructure.StationService;
import com.alejandro.mtoconfiguration.service.lov.DisconnectorFunctionService;
import com.alejandro.mtoconfiguration.support.PostgresTestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bloqueo optimista contra PostgreSQL real: el {@code versionNumber} que el cliente leyo decide si
 * su escritura entra.
 *
 * <p>Los tests unitarios prueban la comparacion; aqui se prueba lo que solo se ve con Hibernate y
 * una base de datos: que el servicio compara con la version <b>guardada</b> (el {@code @Version} de
 * JPA nunca ve el conflicto, porque la entidad se carga en la misma transaccion en la que se
 * escribe), que la version sube al escribir, y que un conflicto deshace la peticion entera sin
 * dejar nada a medias.</p>
 *
 * <p>La escritura concurrente se simula con un {@code update} directo: es lo que veria el servicio
 * si otra peticion hubiese guardado entre la lectura y la escritura del cliente.</p>
 */
@SpringBootTest
@DisplayName("Bloqueo optimista con el versionNumber leido")
class OptimisticLockingIT {

    private static final long FUNCTION = -31L;
    private static final long PACKAGE = -32L;
    private static final long STATION = -33L;
    private static final long DISCONNECTOR = -34L;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.registerProperties(registry);
        registry.add("app.lov.seed-on-startup", () -> "false");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private StationService stationService;
    @Autowired
    private DisconnectorFunctionService disconnectorFunctionService;

    @BeforeEach
    void inserta() {
        limpia();
        jdbcTemplate.update("""
                insert into disconnector_function (id, code, description, enabled, create_date,
                        create_user, version_date, version_user, version_number)
                values (?, 'IT-LOCK', 'Bloqueo optimista', true, now(), 'test', now(), 'test', 1)
                """, FUNCTION);
        jdbcTemplate.update("""
                insert into execution_package (id, name, enabled, deleted, initial_package,
                        length, start_date, end_date, create_date, create_user,
                        version_date, version_user, version_number)
                values (?, 'IT-LOCK', true, false, false, 0, date '2020-01-01',
                        date '2020-12-31', now(), 'test', now(), 'test', 1)
                """, PACKAGE);
        jdbcTemplate.update("""
                insert into station (id, name, execution_package_id, deleted, create_date,
                        create_user, version_date, version_user, version_number)
                values (?, 'EST-IT', ?, false, now(), 'test', now(), 'test', 1)
                """, STATION, PACKAGE);
        jdbcTemplate.update("""
                insert into disconnector (id, name, onload, station_id, disconnector_function_id,
                        deleted, create_date, create_user, version_date, version_user, version_number)
                values (?, 'SECC-IT', false, ?, ?, false, now(), 'test', now(), 'test', 1)
                """, DISCONNECTOR, STATION, FUNCTION);
    }

    @AfterEach
    void limpia() {
        jdbcTemplate.update("delete from outbox_message where aggregate_type in ('station', 'disconnector')"
                + " and aggregate_id in (?, ?)", String.valueOf(STATION), String.valueOf(DISCONNECTOR));
        jdbcTemplate.update("delete from disconnector_aud where id = ?", DISCONNECTOR);
        jdbcTemplate.update("delete from station_aud where id = ?", STATION);
        jdbcTemplate.update("delete from disconnector where id = ?", DISCONNECTOR);
        jdbcTemplate.update("delete from station where id = ?", STATION);
        jdbcTemplate.update("delete from execution_package where id = ?", PACKAGE);
        jdbcTemplate.update("delete from disconnector_function_aud where id = ?", FUNCTION);
        jdbcTemplate.update("delete from disconnector_function where id = ?", FUNCTION);
    }

    private Map<String, Object> fila(String tabla, long id) {
        return jdbcTemplate.queryForMap("select name, version_number from " + tabla + " where id = ?", id);
    }

    @Test
    @DisplayName("quien guarda con una version vieja recibe el conflicto y no pisa al que guardo antes")
    void padreDesactualizado() {
        StationDTO primera = stationService.getById(STATION);
        StationDTO segunda = stationService.getById(STATION);

        primera.setName("EST-A");
        stationService.update(primera);

        assertThat(fila("station", STATION))
                .containsEntry("name", "EST-A")
                .containsEntry("version_number", 2);

        segunda.setName("EST-B");
        assertThatThrownBy(() -> stationService.update(segunda))
                .isInstanceOf(ConcurrencyException.class)
                .hasMessageStartingWith("Station " + STATION);

        assertThat(fila("station", STATION))
                .as("la segunda escritura no ha entrado")
                .containsEntry("name", "EST-A")
                .containsEntry("version_number", 2);
    }

    @Test
    @DisplayName("un hijo que otro guardo desde la lectura tumba la peticion del padre entera")
    void hijoDesactualizado() {
        StationDTO leida = stationService.getById(STATION);
        assertThat(leida.getDisconnectors()).singleElement()
                .satisfies(seccionador -> assertThat(seccionador.getVersionNumber()).isEqualTo(1));

        // Otra peticion guarda el seccionador mientras el cliente edita la estacion.
        jdbcTemplate.update("update disconnector set name = 'SECC-OTRO', version_number = 2 where id = ?",
                DISCONNECTOR);

        leida.setName("EST-MIA");
        leida.getDisconnectors().getFirst().setName("SECC-MIO");

        assertThatThrownBy(() -> stationService.update(leida))
                .isInstanceOf(ConcurrencyException.class)
                .hasMessageStartingWith("Disconnector " + DISCONNECTOR);

        assertThat(fila("disconnector", DISCONNECTOR))
                .as("el cambio del otro se conserva")
                .containsEntry("name", "SECC-OTRO")
                .containsEntry("version_number", 2);
        assertThat(fila("station", STATION))
                .as("y el padre tampoco se ha escrito: la peticion entera se deshace")
                .containsEntry("name", "EST-IT")
                .containsEntry("version_number", 1);
    }

    @Test
    @DisplayName("sin versionNumber no se comprueba nada: escribe sobre lo que haya")
    void sinVersion() {
        StationDTO dto = stationService.getById(STATION);
        dto.setVersionNumber(null);
        dto.getDisconnectors().forEach(seccionador -> seccionador.setVersionNumber(null));

        jdbcTemplate.update("update station set version_number = 5 where id = ?", STATION);

        dto.setName("EST-SIN-VERSION");
        stationService.update(dto);

        assertThat(fila("station", STATION))
                .containsEntry("name", "EST-SIN-VERSION")
                .containsEntry("version_number", 6);
    }

    @Test
    @DisplayName("un catalogo responde con la version nueva, que vale para la siguiente, y rechaza la vieja")
    void catalogo() {
        DisconnectorFunctionDTO leida = disconnectorFunctionService.findById(FUNCTION);
        assertThat(leida.getVersionNumber()).isEqualTo(1);

        leida.setDescription("Primera");
        DisconnectorFunctionDTO respuesta = disconnectorFunctionService.update(FUNCTION, leida);
        assertThat(respuesta.getVersionNumber())
                .as("la respuesta trae la version que se acaba de escribir, no la anterior")
                .isEqualTo(2);

        respuesta.setDescription("Segunda");
        disconnectorFunctionService.update(FUNCTION, respuesta);

        leida.setDescription("Tercera");
        assertThatThrownBy(() -> disconnectorFunctionService.update(FUNCTION, leida))
                .isInstanceOf(ConcurrencyException.class);

        assertThat(jdbcTemplate.queryForMap(
                "select description, version_number from disconnector_function where id = ?", FUNCTION))
                .containsEntry("description", "Segunda")
                .containsEntry("version_number", 3);
    }

    @Test
    @DisplayName("modificar o borrar una estacion que no existe es un NotFoundException, no un 500")
    void inexistente() {
        StationDTO fantasma = stationService.getById(STATION);
        fantasma.setId(-999L);

        assertThatThrownBy(() -> stationService.update(fantasma))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Station Object not found with id -999");
        assertThatThrownBy(() -> stationService.delete(fantasma))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Station Object not found with id -999");
    }
}
