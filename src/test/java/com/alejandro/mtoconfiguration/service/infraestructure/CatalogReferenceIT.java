package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.core.exception.ValidationException;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.DisconnectorDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ProfileDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.StationDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.CantileverTypeDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.DisconnectorFunctionDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.PoleTypeDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.SectioningDTO;
import com.alejandro.mtoconfiguration.support.PostgresTestDatabase;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Una referencia a catalogo informada que el catalogo no tiene, de punta a punta: los servicios
 * reales contra PostgreSQL.
 *
 * <p>El mapper resolvia la referencia por su codigo y, si no existia, la dejaba a {@code null} sin
 * decir nada: un {@code PUT /disconnectors/{id}} con el codigo de la funcion mal escrito respondia
 * 200, borraba la funcion y publicaba el seccionador sin ella ({@code mto-maintenance} elige sus
 * tareas por esa funcion). Ahora la peticion se rechaza con un 400 sobre el campo, y no queda ni el
 * cambio ni el evento. {@code null} («no la toques») y {@code {}} («vaciala») siguen como estaban
 * (README_API.md §3).
 */
@SpringBootTest
@DisplayName("Una referencia a catalogo que no existe rechaza la peticion")
class CatalogReferenceIT {

    private static final long FUNCTION = -701L;
    private static final long CANTILEVER_TYPE = -702L;
    private static final long POLE_TYPE = -703L;
    private static final long PACKAGE = -704L;
    private static final long STATION = -705L;
    private static final long TRACK = -706L;
    private static final long PROFILE = -707L;
    private static final long CANTILEVER = -708L;
    private static final long DISCONNECTOR = -709L;
    private static final String LOV_CODE = "IT-REF";

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
    private StationService stationService;
    @Autowired
    private ProfileService profileService;

    private long marca;

    @BeforeEach
    void inserta() {
        limpia();
        lov("disconnector_function", FUNCTION);
        lov("cantilever_type", CANTILEVER_TYPE);
        lov("pole_type", POLE_TYPE);
        jdbcTemplate.update("""
                insert into execution_package (id, name, enabled, deleted, initial_package,
                        length, start_date, end_date, create_date, create_user,
                        version_date, version_user, version_number)
                values (?, 'IT-REF', true, false, false, 0, date '2020-01-01',
                        date '2020-12-31', now(), 'test', now(), 'test', 1)
                """, PACKAGE);
        jdbcTemplate.update("""
                insert into station (id, name, execution_package_id, deleted, create_date,
                        create_user, version_date, version_user, version_number)
                values (?, 'IT-REF-STATION', ?, false, now(), 'test', now(), 'test', 1)
                """, STATION, PACKAGE);
        jdbcTemplate.update("""
                insert into track (id, name, status, deleted, execution_package_id, create_date,
                        create_user, version_date, version_user, version_number)
                values (?, 'IT-REF-TRACK', true, false, ?, now(), 'test', now(), 'test', 1)
                """, TRACK, PACKAGE);
        jdbcTemplate.update("""
                insert into profile (id, profile_id, kilometric_point, track_id, profile_status_id,
                        pole_type_id, deleted, create_date, create_user, version_date, version_user,
                        version_number)
                values (?, 'IT-REF-A', 1.000, ?, (select id from profile_status where code = 'PROVISIONAL'),
                        ?, false, now(), 'test', now(), 'test', 1)
                """, PROFILE, TRACK, POLE_TYPE);
        jdbcTemplate.update("""
                insert into cantilever (id, cw_height, stagger, profile_id, cantilever_type_id, deleted,
                        create_date, create_user, version_date, version_user, version_number)
                values (?, 5.500, 200, ?, ?, false, now(), 'test', now(), 'test', 1)
                """, CANTILEVER, PROFILE, CANTILEVER_TYPE);
        jdbcTemplate.update("""
                insert into disconnector (id, name, onload, station_id, disconnector_function_id,
                        deleted, create_date, create_user, version_date, version_user, version_number)
                values (?, 'IT-REF-DISC', true, ?, ?, false, now(), 'test', now(), 'test', 1)
                """, DISCONNECTOR, STATION, FUNCTION);
        marca = jdbcTemplate.queryForObject(
                "select coalesce(max(sequence_number), 0) from outbox_message", Long.class);
    }

    private void lov(String table, long id) {
        jdbcTemplate.update("insert into " + table + " (id, code, description, enabled, create_date,"
                + " create_user, version_date, version_user, version_number)"
                + " values (?, ?, 'Referencias a catalogo', true, now(), 'test', now(), 'test', 1)", id, LOV_CODE);
    }

    @AfterEach
    void limpia() {
        jdbcTemplate.update("delete from outbox_message where payload like '%IT-REF%'");
        jdbcTemplate.update("delete from outbox_message where aggregate_type = 'cantilever' and aggregate_id = ?",
                String.valueOf(CANTILEVER));
        jdbcTemplate.update("delete from disconnector_aud where station_id = ?", STATION);
        jdbcTemplate.update("delete from disconnector where station_id = ?", STATION);
        jdbcTemplate.update("delete from cantilever_aud where profile_id = ?", PROFILE);
        jdbcTemplate.update("delete from cantilever where profile_id = ?", PROFILE);
        jdbcTemplate.update("delete from profile_aud where track_id = ?", TRACK);
        jdbcTemplate.update("delete from profile where track_id = ?", TRACK);
        jdbcTemplate.update("delete from track_aud where id = ?", TRACK);
        jdbcTemplate.update("delete from track where id = ?", TRACK);
        jdbcTemplate.update("delete from station_aud where id = ?", STATION);
        jdbcTemplate.update("delete from station where id = ?", STATION);
        jdbcTemplate.update("delete from execution_package_aud where id = ?", PACKAGE);
        jdbcTemplate.update("delete from execution_package where id = ?", PACKAGE);
        for (String table : List.of("disconnector_function", "cantilever_type", "pole_type")) {
            jdbcTemplate.update("delete from " + table + "_aud where code = ?", LOV_CODE);
            jdbcTemplate.update("delete from " + table + " where code = ?", LOV_CODE);
        }
    }

    /** El 400: un solo error, {@code BUS-001}, sobre ese campo; y ni un evento en el outbox. */
    private void rechazaSobre(String field, ThrowingCallable write) {
        assertThatThrownBy(write).isInstanceOfSatisfying(ValidationException.class, rejected ->
                assertThat(rejected.getErrors()).singleElement().satisfies(alert -> {
                    assertThat(alert.getMessage()).isEqualTo(ErrorCodes.BUSINESS_RULE_VIOLATION);
                    assertThat(alert.getFields()).first().isEqualTo(field);
                }));
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from outbox_message where sequence_number > ?", Long.class, marca)).isZero();
    }

    private Long funcionGuardada() {
        return jdbcTemplate.queryForObject(
                "select disconnector_function_id from disconnector where id = ?", Long.class, DISCONNECTOR);
    }

    private Long tipoDePosteGuardado() {
        return jdbcTemplate.queryForObject("select pole_type_id from profile where id = ?", Long.class, PROFILE);
    }

    @Test
    @DisplayName("un seccionador con el codigo de su funcion mal escrito no se guarda: 400 sobre disconnectorFunction, y conserva la que tenia")
    void laFuncionDeUnSeccionador() {
        DisconnectorDTO seccionador = disconnectorService.getById(DISCONNECTOR);
        seccionador.getDisconnectorFunction().setCode("IT-REF-NO-EXISTE");

        rechazaSobre("disconnectorFunction", () -> disconnectorService.update(seccionador));

        assertThat(funcionGuardada()).isEqualTo(FUNCTION);
    }

    @Test
    @DisplayName("escrito desde su estacion, el error dice su ruta: disconnectors[0].disconnectorFunction")
    void laFuncionDeUnSeccionadorDentroDeSuEstacion() {
        StationDTO estacion = stationService.getById(STATION);
        estacion.getDisconnectors().iterator().next().getDisconnectorFunction().setCode("IT-REF-NO-EXISTE");

        rechazaSobre("disconnectors[0].disconnectorFunction", () -> stationService.update(estacion));

        assertThat(funcionGuardada()).isEqualTo(FUNCTION);
    }

    @Test
    @DisplayName("al dar de alta, y en un lote con su posicion, tambien se rechaza")
    void enElAltaYEnUnLote() {
        DisconnectorFunctionDTO noExiste = new DisconnectorFunctionDTO();
        noExiste.setCode("IT-REF-NO-EXISTE");
        DisconnectorFunctionDTO existe = new DisconnectorFunctionDTO();
        existe.setCode(LOV_CODE);

        rechazaSobre("disconnectorFunction", () -> disconnectorService.create(nuevo("IT-REF-NEW", noExiste)));
        rechazaSobre("[1].disconnectorFunction", () -> disconnectorService.bulkCreate(
                List.of(nuevo("IT-REF-NEW-1", existe), nuevo("IT-REF-NEW-2", noExiste))));

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from disconnector where name like 'IT-REF-NEW%'", Long.class)).isZero();
    }

    private static DisconnectorDTO nuevo(String name, DisconnectorFunctionDTO function) {
        DisconnectorDTO dto = new DisconnectorDTO();
        dto.setName(name);
        dto.setOnLoad(true);
        dto.setStationId(STATION);
        dto.setDisconnectorFunction(function);
        return dto;
    }

    @Test
    @DisplayName("en un perfil: su tipo de poste, un seccionamiento de su lista y el tipo de una mensula suya, cada uno en su campo")
    void lasReferenciasDeUnPerfil() {
        ProfileDTO conPoste = profileService.getById(PROFILE);
        conPoste.getPoleType().setCode("IT-REF-NO-EXISTE");
        rechazaSobre("poleType", () -> profileService.update(conPoste));

        ProfileDTO conSeccionamiento = profileService.getById(PROFILE);
        SectioningDTO seccionamiento = new SectioningDTO();
        seccionamiento.setCode("IT-REF-NO-EXISTE");
        conSeccionamiento.setSectionings(new ArrayList<>(List.of(seccionamiento)));
        rechazaSobre("sectionings[0]", () -> profileService.update(conSeccionamiento));

        ProfileDTO conMensula = profileService.getById(PROFILE);
        CantileverTypeDTO tipo = new CantileverTypeDTO();
        tipo.setCode("IT-REF-NO-EXISTE");
        conMensula.getCantilevers().getFirst().setCantileverType(tipo);
        rechazaSobre("cantilevers[0].cantileverType", () -> profileService.update(conMensula));

        assertThat(tipoDePosteGuardado()).isEqualTo(POLE_TYPE);
        assertThat(jdbcTemplate.queryForObject("select cantilever_type_id from cantilever where id = ?",
                Long.class, CANTILEVER)).isEqualTo(CANTILEVER_TYPE);
    }

    @Test
    @DisplayName("null sigue siendo «no la toques», {} sigue vaciandola, y solo con su id se resuelve por el")
    void nullYVaciaSiguenComoEstaban() {
        ProfileDTO sinTocar = profileService.getById(PROFILE);
        sinTocar.setPoleType(null);
        ProfileDTO guardado = profileService.update(sinTocar);
        assertThat(tipoDePosteGuardado()).isEqualTo(POLE_TYPE);

        guardado.setPoleType(new PoleTypeDTO());
        guardado = profileService.update(guardado);
        assertThat(tipoDePosteGuardado()).isNull();
        assertThat(profileService.getById(PROFILE).getPoleType()).isNull();

        PoleTypeDTO soloId = new PoleTypeDTO();
        soloId.setId(POLE_TYPE);
        guardado.setPoleType(soloId);
        guardado = profileService.update(guardado);
        assertThat(tipoDePosteGuardado()).isEqualTo(POLE_TYPE);
        assertThat(guardado.getPoleType().getCode()).isEqualTo(LOV_CODE);

        // La funcion de un seccionador no se puede quitar: es obligatoria, y vacia la rechaza su validador.
        DisconnectorDTO seccionador = disconnectorService.getById(DISCONNECTOR);
        seccionador.setDisconnectorFunction(new DisconnectorFunctionDTO());
        assertThatThrownBy(() -> disconnectorService.update(seccionador)).isInstanceOf(ValidationException.class);
        assertThat(funcionGuardada()).isEqualTo(FUNCTION);
    }
}
