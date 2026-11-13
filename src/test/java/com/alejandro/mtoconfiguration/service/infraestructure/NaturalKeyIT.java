package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.core.exception.BaseException;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.DisconnectorDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SectionInsulatorDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.StationDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.DisconnectorFunctionDTO;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.DisconnectorRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.SectionInsulatorRepository;
import com.alejandro.mtoconfiguration.support.PostgresTestDatabase;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La clave natural del seccionador y la del aislador de sección, por la API y contra PostgreSQL.
 *
 * <p>El importador del maestro identifica un aislador por su estación y su nombre
 * ({@code SectionInsulatorRepository.findByNameIgnoreCaseAndStationId}) y un seccionador por su
 * paquete y su nombre ({@code DisconnectorRepository.findByNameInPackage}), y no carga la fila cuyo
 * nombre ya está dos veces. La API no miraba ninguna de las dos: aceptaba el segundo «S-12» de la
 * misma estación, y a partir de ahí ninguna carga podía ya con ese nombre. Aquí se fija que lo
 * rechaza por todos sus caminos (el alta, la modificación, el lote y el hijo que viaja dentro del
 * {@code PUT} de su estación), como los demás maestros: 409 {@code BUS-002}.
 *
 * <p>Las dos claves se guardan distinto, y por eso los dos rechazos son excepciones distintas con el
 * mismo código: la del aislador es un índice único parcial ({@code V31}) y la del seccionador, que
 * depende del paquete de su estación, de la vía de su poste o de su vía propia y no cabe en un
 * índice, la comprueba {@code DisconnectorNaturalKeyGuard} después del flush.
 */
@SpringBootTest
@DisplayName("La clave natural del seccionador y del aislador, por la API")
class NaturalKeyIT {

    private static final long PACKAGE = -711L;
    private static final long OTHER_PACKAGE = -712L;
    private static final long STATION = -713L;
    private static final long STATION_2 = -714L;
    private static final long FOREIGN_STATION = -715L;
    private static final long TRACK = -716L;
    private static final long FOREIGN_TRACK = -717L;
    private static final long PROFILE = -718L;
    private static final long FUNCTION = -719L;
    private static final String FUNCTION_CODE = "IT-NK-FN";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.registerProperties(registry);
        registry.add("app.lov.seed-on-startup", () -> "false");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private DisconnectorService disconnectorService;
    @Autowired
    private SectionInsulatorService sectionInsulatorService;
    @Autowired
    private StationService stationService;
    @Autowired
    private DisconnectorRepository disconnectorRepository;
    @Autowired
    private SectionInsulatorRepository sectionInsulatorRepository;

    @BeforeEach
    void inserta() {
        limpia();
        jdbcTemplate.update("""
                insert into disconnector_function (id, code, description, enabled, create_date,
                        create_user, version_date, version_user, version_number)
                values (?, ?, 'Clave natural', true, now(), 'test', now(), 'test', 1)
                """, FUNCTION, FUNCTION_CODE);
        paquete(PACKAGE, "IT-NK");
        paquete(OTHER_PACKAGE, "IT-NK-OTRO");
        estacion(STATION, "IT-NK-STATION", PACKAGE);
        estacion(STATION_2, "IT-NK-STATION-2", PACKAGE);
        estacion(FOREIGN_STATION, "IT-NK-STATION-OTRO", OTHER_PACKAGE);
        via(TRACK, "IT-NK-TRACK", PACKAGE);
        via(FOREIGN_TRACK, "IT-NK-TRACK-OTRO", OTHER_PACKAGE);
        jdbcTemplate.update("""
                insert into profile (id, profile_id, kilometric_point, track_id, profile_status_id,
                        deleted, create_date, create_user, version_date, version_user, version_number)
                values (?, 'IT-NK-P1', 1500.000, ?, (select id from profile_status where code = 'PROVISIONAL'),
                        false, now(), 'test', now(), 'test', 1)
                """, PROFILE, TRACK);
    }

    private void paquete(long id, String name) {
        jdbcTemplate.update("""
                insert into execution_package (id, name, enabled, deleted, initial_package,
                        length, start_date, end_date, create_date, create_user,
                        version_date, version_user, version_number)
                values (?, ?, true, false, false, 0, date '2020-01-01',
                        date '2020-12-31', now(), 'test', now(), 'test', 1)
                """, id, name);
    }

    private void estacion(long id, String name, long packageId) {
        jdbcTemplate.update("""
                insert into station (id, name, execution_package_id, deleted, create_date, create_user,
                        version_date, version_user, version_number)
                values (?, ?, ?, false, now(), 'test', now(), 'test', 1)
                """, id, name, packageId);
    }

    private void via(long id, String name, long packageId) {
        jdbcTemplate.update("""
                insert into track (id, name, status, deleted, execution_package_id, create_date,
                        create_user, version_date, version_user, version_number)
                values (?, ?, true, false, ?, now(), 'test', now(), 'test', 1)
                """, id, name, packageId);
    }

    @AfterEach
    void limpia() {
        jdbcTemplate.update("delete from outbox_message where payload::text like '%IT-NK%'");
        for (String tabla : List.of("disconnector", "section_insulator")) {
            jdbcTemplate.update("delete from " + tabla + "_aud where upper(name) like 'IT-NK%'");
            jdbcTemplate.update("delete from " + tabla + " where upper(name) like 'IT-NK%'");
        }
        jdbcTemplate.update("delete from profile_aud where id = ?", PROFILE);
        jdbcTemplate.update("delete from profile where id = ?", PROFILE);
        jdbcTemplate.update("delete from track_aud where id in (?, ?)", TRACK, FOREIGN_TRACK);
        jdbcTemplate.update("delete from track where id in (?, ?)", TRACK, FOREIGN_TRACK);
        jdbcTemplate.update("delete from station_aud where id in (?, ?, ?)", STATION, STATION_2, FOREIGN_STATION);
        jdbcTemplate.update("delete from station where id in (?, ?, ?)", STATION, STATION_2, FOREIGN_STATION);
        jdbcTemplate.update("delete from execution_package_aud where id in (?, ?)", PACKAGE, OTHER_PACKAGE);
        jdbcTemplate.update("delete from execution_package where id in (?, ?)", PACKAGE, OTHER_PACKAGE);
        jdbcTemplate.update("delete from disconnector_function where id = ?", FUNCTION);
    }

    private static DisconnectorDTO seccionador(String name) {
        DisconnectorFunctionDTO function = new DisconnectorFunctionDTO();
        function.setCode(FUNCTION_CODE);
        DisconnectorDTO dto = new DisconnectorDTO();
        dto.setName(name);
        dto.setOnLoad(false);
        dto.setDisconnectorFunction(function);
        return dto;
    }

    private static DisconnectorDTO seccionadorEn(String name, Long stationId) {
        DisconnectorDTO dto = seccionador(name);
        dto.setStationId(stationId);
        return dto;
    }

    private static SectionInsulatorDTO aislador(String name, Long stationId) {
        SectionInsulatorDTO dto = new SectionInsulatorDTO();
        dto.setName(name);
        dto.setEnabled(true);
        dto.setStationId(stationId);
        dto.setKp(new BigDecimal("1200.000"));
        return dto;
    }

    private long vivos(String tabla, String name) {
        return jdbcTemplate.queryForObject(
                "select count(*) from " + tabla + " where upper(name) = upper(?) and deleted = false",
                Long.class, name);
    }

    /** El rechazo del seccionador: lo que el manejador de errores convierte en un 409 {@code BUS-002}. */
    private static void esUnRepetido(Throwable thrown) {
        assertThat(thrown).isInstanceOf(BaseException.class);
        assertThat(((BaseException) thrown).getErrors())
                .singleElement()
                .satisfies(alert -> {
                    assertThat(alert.getMessage()).isEqualTo(ErrorCodes.DUPLICATED_RESOURCE);
                    assertThat(alert.getFields()).containsExactly("name");
                });
    }

    @Nested
    @DisplayName("el seccionador: su nombre dentro de su paquete")
    class Seccionador {

        @Test
        @DisplayName("un segundo con el mismo nombre en la misma estación no entra, tampoco con otras mayúsculas")
        void altaRepetida() {
            disconnectorService.create(seccionadorEn("IT-NK-S1", STATION));

            assertThatThrownBy(() -> disconnectorService.create(seccionadorEn("it-nk-s1", STATION)))
                    .satisfies(NaturalKeyIT::esUnRepetido);

            assertThat(vivos("disconnector", "IT-NK-S1")).as("la transacción se deshace").isEqualTo(1);
        }

        @Test
        @DisplayName("la clave es el paquete, no la estación: tampoco en otra estación suya, ni sin estación en una de sus vías o de sus postes")
        void elPaqueteEnteroCuenta() {
            disconnectorService.create(seccionadorEn("IT-NK-S2", STATION));

            assertThatThrownBy(() -> disconnectorService.create(seccionadorEn("IT-NK-S2", STATION_2)))
                    .satisfies(NaturalKeyIT::esUnRepetido);

            DisconnectorDTO enPlenaVia = seccionador("IT-NK-S2");
            enPlenaVia.setTrackId(TRACK);
            enPlenaVia.setKp("98375.5");
            assertThatThrownBy(() -> disconnectorService.create(enPlenaVia))
                    .satisfies(NaturalKeyIT::esUnRepetido);

            DisconnectorDTO enUnPoste = seccionador("IT-NK-S2");
            enUnPoste.setProfileId(PROFILE);
            assertThatThrownBy(() -> disconnectorService.create(enUnPoste))
                    .satisfies(NaturalKeyIT::esUnRepetido);

            assertThat(vivos("disconnector", "IT-NK-S2")).isEqualTo(1);
            assertThat(disconnectorRepository.findByNameInPackage("IT-NK-S2", PACKAGE))
                    .as("lo que busca el importador sigue siendo uno solo")
                    .hasSize(1);
        }

        @Test
        @DisplayName("el mismo nombre en otro paquete sí entra")
        void otroPaquete() {
            disconnectorService.create(seccionadorEn("IT-NK-S3", STATION));
            disconnectorService.create(seccionadorEn("IT-NK-S3", FOREIGN_STATION));

            DisconnectorDTO enViaAjena = seccionador("IT-NK-S3B");
            enViaAjena.setTrackId(FOREIGN_TRACK);
            disconnectorService.create(enViaAjena);
            disconnectorService.create(seccionadorEn("IT-NK-S3B", STATION));

            assertThat(vivos("disconnector", "IT-NK-S3")).isEqualTo(2);
            assertThat(vivos("disconnector", "IT-NK-S3B")).isEqualTo(2);
        }

        @Test
        @DisplayName("renombrar uno con el nombre de otro de su paquete no entra; guardarlo con su propio nombre, sí")
        void modificacion() {
            disconnectorService.create(seccionadorEn("IT-NK-S4", STATION));
            DisconnectorDTO otro = disconnectorService.create(seccionadorEn("IT-NK-S5", STATION_2));

            otro.setOnLoad(true);
            DisconnectorDTO guardado = disconnectorService.update(otro);
            assertThat(guardado.getOnLoad()).isTrue();

            guardado.setName("IT-NK-S4");
            assertThatThrownBy(() -> disconnectorService.update(guardado))
                    .satisfies(NaturalKeyIT::esUnRepetido);

            assertThat(vivos("disconnector", "IT-NK-S4")).isEqualTo(1);
            assertThat(vivos("disconnector", "IT-NK-S5")).isEqualTo(1);
        }

        @Test
        @DisplayName("moverlo a un paquete donde ya está su nombre no entra")
        void cambioDePaquete() {
            disconnectorService.create(seccionadorEn("IT-NK-S6", STATION));
            DisconnectorDTO ajeno = disconnectorService.create(seccionadorEn("IT-NK-S6", FOREIGN_STATION));

            ajeno.setStationId(STATION_2);
            assertThatThrownBy(() -> disconnectorService.update(ajeno))
                    .satisfies(NaturalKeyIT::esUnRepetido);
        }

        @Test
        @DisplayName("un lote con el mismo nombre dos veces no entra, ni entero ni a medias")
        void lote() {
            assertThatThrownBy(() -> disconnectorService.bulkCreate(List.of(
                    seccionadorEn("IT-NK-S7", STATION), seccionadorEn("IT-NK-S7", STATION_2))))
                    .satisfies(NaturalKeyIT::esUnRepetido);

            assertThat(vivos("disconnector", "IT-NK-S7")).isZero();
        }

        @Test
        @DisplayName("dentro del PUT de su estación tampoco: ni uno nuevo, ni uno suyo renombrado, con el nombre de otro del paquete")
        void anidadoEnSuEstacion() {
            disconnectorService.create(seccionadorEn("IT-NK-S8", STATION_2));
            DisconnectorDTO suyo = disconnectorService.create(seccionadorEn("IT-NK-S9", STATION));

            StationDTO conUnoNuevo = estacionLeida();
            conUnoNuevo.setDisconnectors(new ArrayList<>(List.of(suyo, seccionador("IT-NK-S8"))));
            assertThatThrownBy(() -> stationService.update(conUnoNuevo))
                    .satisfies(NaturalKeyIT::esUnRepetido);

            StationDTO conElSuyoRenombrado = estacionLeida();
            DisconnectorDTO renombrado = disconnectorService.getById(suyo.getId());
            renombrado.setName("it-nk-s8");
            conElSuyoRenombrado.setDisconnectors(new ArrayList<>(List.of(renombrado)));
            assertThatThrownBy(() -> stationService.update(conElSuyoRenombrado))
                    .satisfies(NaturalKeyIT::esUnRepetido);

            assertThat(vivos("disconnector", "IT-NK-S8")).isEqualTo(1);
            assertThat(vivos("disconnector", "IT-NK-S9")).as("ninguna de las dos escrituras ha dejado nada").isEqualTo(1);

            StationDTO conOtroNombre = estacionLeida();
            conOtroNombre.setDisconnectors(new ArrayList<>(List.of(suyo, seccionador("IT-NK-S11"))));
            stationService.update(conOtroNombre);
            assertThat(vivos("disconnector", "IT-NK-S11")).isEqualTo(1);
        }

        @Test
        @DisplayName("uno borrado no ocupa su nombre")
        void elBorradoNoCuenta() {
            DisconnectorDTO primero = disconnectorService.create(seccionadorEn("IT-NK-S10", STATION));
            disconnectorService.delete(primero);

            disconnectorService.create(seccionadorEn("IT-NK-S10", STATION));

            assertThat(vivos("disconnector", "IT-NK-S10")).isEqualTo(1);
        }
    }

    /** La estación del test, como la devolvería una lista: sin hijos, que es «no los toques». */
    private StationDTO estacionLeida() {
        StationDTO dto = new StationDTO();
        dto.setId(STATION);
        dto.setName("IT-NK-STATION");
        dto.setExecutionPackageId(PACKAGE);
        dto.setTracks(null);
        dto.setDisconnectors(null);
        dto.setSectionInsulators(null);
        return dto;
    }

    @Nested
    @DisplayName("el aislador: su nombre dentro de su estación")
    class Aislador {

        @Test
        @DisplayName("un segundo con el mismo nombre en la misma estación no entra, tampoco con otras mayúsculas")
        void altaRepetida() {
            sectionInsulatorService.create(aislador("IT-NK-A1", STATION));

            assertThatThrownBy(() -> sectionInsulatorService.create(aislador("it-nk-a1", STATION)))
                    .isInstanceOf(DataIntegrityViolationException.class);

            assertThat(vivos("section_insulator", "IT-NK-A1")).isEqualTo(1);
            assertThat(sectionInsulatorRepository.findByNameIgnoreCaseAndStationId("IT-NK-A1", STATION))
                    .as("lo que busca el importador sigue siendo uno solo")
                    .isPresent();
        }

        @Test
        @DisplayName("el mismo nombre en otra estación sí entra")
        void otraEstacion() {
            sectionInsulatorService.create(aislador("IT-NK-A2", STATION));
            sectionInsulatorService.create(aislador("IT-NK-A2", STATION_2));

            assertThat(vivos("section_insulator", "IT-NK-A2")).isEqualTo(2);
        }

        @Test
        @DisplayName("renombrar uno con el nombre de otro de su estación no entra")
        void modificacion() {
            sectionInsulatorService.create(aislador("IT-NK-A3", STATION));
            SectionInsulatorDTO otro = sectionInsulatorService.create(aislador("IT-NK-A4", STATION));

            otro.setName("IT-NK-A3");
            assertThatThrownBy(() -> sectionInsulatorService.update(otro))
                    .isInstanceOf(DataIntegrityViolationException.class);

            assertThat(vivos("section_insulator", "IT-NK-A3")).isEqualTo(1);
            assertThat(vivos("section_insulator", "IT-NK-A4")).isEqualTo(1);
        }

        @Test
        @DisplayName("un lote con el mismo nombre dos veces no entra")
        void lote() {
            assertThatThrownBy(() -> sectionInsulatorService.bulkCreate(List.of(
                    aislador("IT-NK-A5", STATION), aislador("IT-NK-A5", STATION))))
                    .isInstanceOf(DataIntegrityViolationException.class);

            assertThat(vivos("section_insulator", "IT-NK-A5")).isZero();
        }

        @Test
        @DisplayName("dentro del PUT de su estación tampoco")
        void anidadoEnSuEstacion() {
            StationDTO conDosIguales = estacionLeida();
            SectionInsulatorDTO uno = aislador("IT-NK-A6", null);
            SectionInsulatorDTO otro = aislador("IT-NK-A6", null);
            conDosIguales.setSectionInsulators(new ArrayList<>(List.of(uno, otro)));

            assertThatThrownBy(() -> stationService.update(conDosIguales))
                    .isInstanceOf(DataIntegrityViolationException.class);

            assertThat(vivos("section_insulator", "IT-NK-A6")).isZero();
        }

        @Test
        @DisplayName("uno borrado no ocupa su nombre")
        void elBorradoNoCuenta() {
            SectionInsulatorDTO primero = sectionInsulatorService.create(aislador("IT-NK-A7", STATION));
            sectionInsulatorService.delete(primero);

            sectionInsulatorService.create(aislador("IT-NK-A7", STATION));

            assertThat(vivos("section_insulator", "IT-NK-A7")).isEqualTo(1);
        }
    }
}
