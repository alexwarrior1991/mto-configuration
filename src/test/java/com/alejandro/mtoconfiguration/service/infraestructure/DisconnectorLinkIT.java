package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.core.exception.ValidationException;
import com.alejandro.mtoconfiguration.enums.infrastructure.DisconnectorDriveType;
import com.alejandro.mtoconfiguration.masterdata.messaging.mapper.ProfileMasterDataPayloadMapper;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.DisconnectorDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ProfileDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.StationDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.DisconnectorFilter;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.schematic.TrackSchematicDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.schematic.TrackSchematicDTO.ProfileNode;
import com.alejandro.mtoconfiguration.model.synchronous.lov.DisconnectorFunctionDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.ProfileStatusDTO;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.ProfileRepository;
import com.alejandro.mtoconfiguration.support.PostgresTestDatabase;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El vinculo entre un perfil y su seccionador, de punta a punta: los servicios reales contra
 * PostgreSQL.
 *
 * <p>El vinculo es del seccionador ({@code disconnector.profile_id}): el perfil es el lado inverso
 * de la relacion, y su {@code disconnector} es solo de salida (README_API.md §4). Antes el perfil lo
 * escribia, y mal: mandar otro seccionador copiaba sus datos sobre el que ya colgaba (o creaba una
 * copia si no colgaba ninguno), {@code null} no lo desvinculaba aunque la respuesta lo diera por
 * hecho, y un seccionador guardado despues de leer el perfil impedia guardar el perfil. Ademas, un
 * seccionador borrado seguia ocupando su perfil, porque el indice unico de {@code profile_id} contaba
 * tambien las filas borradas (V24 lo hace parcial).
 *
 * <p>El punto de partida se inserta con SQL, como en {@code FilterOutsideTransactionIT}: dos perfiles
 * con su seccionador y uno libre en la misma via. Lo que se prueba se escribe por los servicios,
 * que es por donde entra una peticion.
 */
@SpringBootTest
@DisplayName("El vinculo entre un perfil y su seccionador")
class DisconnectorLinkIT {

    private static final long FUNCTION = -91L;
    private static final long PACKAGE = -92L;
    private static final long STATION = -93L;
    private static final long TRACK = -94L;
    private static final long OTHER_TRACK = -90L;
    private static final long PROFILE_A = -95L;
    private static final long PROFILE_B = -96L;
    private static final long PROFILE_FREE = -97L;
    private static final long DISCONNECTOR_A = -98L;
    private static final long DISCONNECTOR_B = -99L;
    private static final String FUNCTION_CODE = "IT-LINK";
    private static final String NEW_PROFILE = "IT-LINK-NUEVO";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.registerProperties(registry);
        registry.add("app.lov.seed-on-startup", () -> "false");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ProfileService profileService;
    @Autowired
    private DisconnectorService disconnectorService;
    @Autowired
    private StationService stationService;
    @Autowired
    private TrackSchematicService trackSchematicService;
    @Autowired
    private ProfileRepository profileRepository;
    @Autowired
    private ProfileMasterDataPayloadMapper profilePayloadMapper;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void inserta() {
        limpia();
        jdbcTemplate.update("""
                insert into disconnector_function (id, code, description, enabled, create_date,
                        create_user, version_date, version_user, version_number)
                values (?, ?, 'Vinculo perfil-seccionador', true, now(), 'test', now(), 'test', 1)
                """, FUNCTION, FUNCTION_CODE);
        jdbcTemplate.update("""
                insert into execution_package (id, name, enabled, deleted, initial_package,
                        length, start_date, end_date, create_date, create_user,
                        version_date, version_user, version_number)
                values (?, 'IT-LINK', true, false, false, 0, date '2020-01-01',
                        date '2020-12-31', now(), 'test', now(), 'test', 1)
                """, PACKAGE);
        jdbcTemplate.update("""
                insert into station (id, name, execution_package_id, deleted, create_date,
                        create_user, version_date, version_user, version_number)
                values (?, 'IT-LINK-STATION', ?, false, now(), 'test', now(), 'test', 1)
                """, STATION, PACKAGE);
        jdbcTemplate.update("""
                insert into track (id, name, status, deleted, execution_package_id, create_date,
                        create_user, version_date, version_user, version_number)
                values (?, 'IT-LINK-TRACK', true, false, ?, now(), 'test', now(), 'test', 1)
                """, TRACK, PACKAGE);
        jdbcTemplate.update("insert into track_station (track_id, station_id) values (?, ?)", TRACK, STATION);
        jdbcTemplate.update("""
                insert into track (id, name, status, deleted, execution_package_id, create_date,
                        create_user, version_date, version_user, version_number)
                values (?, 'IT-LINK-TRACK-2', true, false, ?, now(), 'test', now(), 'test', 1)
                """, OTHER_TRACK, PACKAGE);
        perfil(PROFILE_A, "IT-LINK-A", "1.000");
        perfil(PROFILE_B, "IT-LINK-B", "2.000");
        perfil(PROFILE_FREE, "IT-LINK-LIBRE", "3.000");
        seccionador(DISCONNECTOR_A, "IT-LINK-A", PROFILE_A);
        seccionador(DISCONNECTOR_B, "IT-LINK-B", PROFILE_B);
    }

    private void perfil(long id, String code, String kp) {
        jdbcTemplate.update("""
                insert into profile (id, profile_id, kilometric_point, track_id, profile_status_id,
                        deleted, create_date, create_user, version_date, version_user, version_number)
                values (?, ?, ?::numeric, ?, (select id from profile_status where code = 'PROVISIONAL'),
                        false, now(), 'test', now(), 'test', 1)
                """, id, code, kp, TRACK);
    }

    private void seccionador(long id, String name, long profileId) {
        jdbcTemplate.update("""
                insert into disconnector (id, name, onload, profile_id, station_id,
                        disconnector_function_id, deleted, create_date, create_user, version_date,
                        version_user, version_number)
                values (?, ?, true, ?, ?, ?, false, now(), 'test', now(), 'test', 1)
                """, id, name, profileId, STATION, FUNCTION);
    }

    @AfterEach
    void limpia() {
        jdbcTemplate.update("""
                delete from outbox_message
                 where (aggregate_type = 'disconnector'
                        and aggregate_id in (select id::text from disconnector where name like 'IT-LINK%'))
                    or (aggregate_type = 'profile'
                        and aggregate_id in (select id::text from profile where profile_id like 'IT-LINK%'))
                """);
        jdbcTemplate.update("delete from disconnector_aud where id in (select id from disconnector where name like 'IT-LINK%')");
        jdbcTemplate.update("delete from disconnector where name like 'IT-LINK%'");
        jdbcTemplate.update("delete from profile_aud where id in (select id from profile where profile_id like 'IT-LINK%')");
        jdbcTemplate.update("delete from profile where profile_id like 'IT-LINK%'");
        jdbcTemplate.update("delete from track_station where track_id = ?", TRACK);
        jdbcTemplate.update("delete from track where id in (?, ?)", TRACK, OTHER_TRACK);
        jdbcTemplate.update("delete from station where id = ?", STATION);
        jdbcTemplate.update("delete from execution_package where id = ?", PACKAGE);
        jdbcTemplate.update("delete from disconnector_function_aud where id = ?", FUNCTION);
        jdbcTemplate.update("delete from disconnector_function where id = ?", FUNCTION);
    }

    private Map<String, Object> fila(long disconnectorId) {
        return jdbcTemplate.queryForMap("select name, profile_id, deleted from disconnector where id = ?",
                disconnectorId);
    }

    @Test
    @DisplayName("modificar un perfil con otro seccionador dentro no toca a ninguno de los dos")
    void otroSeccionadorNoSeCopiaNiSeMueve() {
        ProfileDTO perfil = profileService.getById(PROFILE_A);
        perfil.setDisconnector(disconnectorService.getById(DISCONNECTOR_B));

        ProfileDTO guardado = profileService.update(perfil);

        assertThat(fila(DISCONNECTOR_A)).containsEntry("name", "IT-LINK-A").containsEntry("profile_id", PROFILE_A);
        assertThat(fila(DISCONNECTOR_B)).containsEntry("name", "IT-LINK-B").containsEntry("profile_id", PROFILE_B);
        assertThat(guardado.getDisconnector().getId()).isEqualTo(DISCONNECTOR_A);
    }

    @Test
    @DisplayName("con el seccionador a null el perfil lo conserva, y la respuesta lo dice")
    void nullNoDesvincula() {
        ProfileDTO perfil = profileService.getById(PROFILE_A);
        perfil.setDisconnector(null);

        ProfileDTO guardado = profileService.update(perfil);

        assertThat(fila(DISCONNECTOR_A)).containsEntry("profile_id", PROFILE_A);
        assertThat(guardado.getDisconnector()).as("la respuesta no puede decir que se ha desvinculado").isNotNull();
        assertThat(guardado.getDisconnector().getId()).isEqualTo(DISCONNECTOR_A);
    }

    @Test
    @DisplayName("dar de alta un perfil con un seccionador dentro no crea ninguno")
    void altaNoCreaSeccionador() {
        ProfileDTO nuevo = new ProfileDTO();
        nuevo.setProfileId(NEW_PROFILE);
        nuevo.setKp("4.000");
        nuevo.setTrackId(TRACK);
        ProfileStatusDTO status = new ProfileStatusDTO();
        status.setCode("PROVISIONAL");
        nuevo.setProfileStatus(status);
        DisconnectorFunctionDTO function = new DisconnectorFunctionDTO();
        function.setCode(FUNCTION_CODE);
        DisconnectorDTO dentro = new DisconnectorDTO();
        dentro.setName("IT-LINK-DENTRO");
        dentro.setOnLoad(true);
        dentro.setStationId(STATION);
        dentro.setDisconnectorFunction(function);
        nuevo.setDisconnector(dentro);

        ProfileDTO creado = profileService.create(nuevo);

        assertThat(creado.getDisconnector()).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from disconnector where name = 'IT-LINK-DENTRO'", Integer.class)).isZero();
    }

    @Test
    @DisplayName("un seccionador guardado despues de leer el perfil ya no impide guardarlo, ni se pisa")
    void seccionadorDesactualizadoNoEsConflicto() {
        ProfileDTO perfil = profileService.getById(PROFILE_A);
        DisconnectorDTO seccionador = disconnectorService.getById(DISCONNECTOR_A);
        seccionador.setName("IT-LINK-A-RENOMBRADO");
        disconnectorService.update(seccionador);

        perfil.setSpan(new BigDecimal("47.970"));
        profileService.update(perfil);

        assertThat(fila(DISCONNECTOR_A)).containsEntry("name", "IT-LINK-A-RENOMBRADO");
        assertThat(jdbcTemplate.queryForObject("select span from profile where id = ?", BigDecimal.class, PROFILE_A))
                .isEqualByComparingTo("47.970");
    }

    @Test
    @DisplayName("un seccionador se mueve de perfil con su PUT, y cada perfil enseña el suyo")
    void seMueveConSuPut() {
        DisconnectorDTO seccionador = disconnectorService.getById(DISCONNECTOR_A);
        seccionador.setProfileId(PROFILE_FREE);

        disconnectorService.update(seccionador);

        assertThat(profileService.getById(PROFILE_FREE).getDisconnector().getId()).isEqualTo(DISCONNECTOR_A);
        assertThat(profileService.getById(PROFILE_A).getDisconnector()).isNull();
    }

    @Test
    @DisplayName("un seccionador se desvincula de su poste con su PUT, y el perfil deja de enseñarlo")
    void seDesvinculaConSuPut() {
        DisconnectorDTO seccionador = disconnectorService.getById(DISCONNECTOR_A);
        seccionador.setProfileId(null);

        DisconnectorDTO guardado = disconnectorService.update(seccionador);

        assertThat(guardado.getProfileId()).isNull();
        assertThat(guardado.getProfileCode()).isNull();
        assertThat(fila(DISCONNECTOR_A)).containsEntry("profile_id", null);
        assertThat(profileService.getById(PROFILE_A).getDisconnector()).isNull();
    }

    @Test
    @DisplayName("se da de alta un seccionador sin poste, con su estado normal, su accionamiento, su KP y su vía (V25, V26)")
    void altaSinPoste() {
        DisconnectorFunctionDTO function = new DisconnectorFunctionDTO();
        function.setCode(FUNCTION_CODE);
        DisconnectorDTO nuevo = new DisconnectorDTO();
        nuevo.setName("IT-LINK-SIN-POSTE");
        nuevo.setOnLoad(false);
        nuevo.setNormallyOpen(true);
        nuevo.setDriveType(DisconnectorDriveType.MANUAL);
        nuevo.setKp("98375.5");
        nuevo.setTrackId(TRACK);
        nuevo.setStationId(STATION);
        nuevo.setDisconnectorFunction(function);

        DisconnectorDTO creado = disconnectorService.create(nuevo);

        assertThat(creado.getProfileId()).isNull();
        assertThat(creado.getNormallyOpen()).isTrue();
        assertThat(creado.getDriveType()).isEqualTo(DisconnectorDriveType.MANUAL);
        assertThat(new BigDecimal(creado.getKp())).isEqualByComparingTo("98375.5");
        assertThat(creado.getTrackId()).isEqualTo(TRACK);
        Map<String, Object> fila = jdbcTemplate.queryForMap(
                "select profile_id, normally_open, drive_type, kilometric_point, track_id from disconnector where id = ?",
                creado.getId());
        assertThat(fila)
                .containsEntry("profile_id", null)
                .containsEntry("normally_open", true)
                .containsEntry("drive_type", "MANUAL")
                .containsEntry("track_id", TRACK);
        assertThat((BigDecimal) fila.get("kilometric_point")).isEqualByComparingTo("98375.5");
    }

    /**
     * La vía conectada (V27) es la otra de las dos que el seccionador pone en paralelo. Con poste, la
     * suya es la del perfil, que el DTO no trae: la mira DisconnectorMapper con la entidad mapeada,
     * y lo que rechaza no llega a la base. Sin poste, la suya va en el DTO y la mira el validador.
     */
    @Test
    @DisplayName("la vía conectada se guarda también en uno en un poste, y nunca puede ser la suya (V27)")
    void viaConectada() {
        DisconnectorDTO enPoste = disconnectorService.getById(DISCONNECTOR_A);
        enPoste.setConnectedTrackId(OTHER_TRACK);

        assertThat(disconnectorService.update(enPoste).getConnectedTrackId()).isEqualTo(OTHER_TRACK);
        assertThat(conectada(DISCONNECTOR_A)).isEqualTo(OTHER_TRACK);
        assertThat(disconnectorService.getById(DISCONNECTOR_A).getConnectedTrackId()).isEqualTo(OTHER_TRACK);

        DisconnectorDTO laDeSuPoste = disconnectorService.getById(DISCONNECTOR_A);
        laDeSuPoste.setConnectedTrackId(TRACK);
        assertThatThrownBy(() -> disconnectorService.update(laDeSuPoste))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getErrors())
                        .singleElement()
                        .satisfies(alert -> {
                            assertThat(alert.getMessage()).isEqualTo(ErrorCodes.BUSINESS_RULE_VIOLATION);
                            assertThat(alert.getFields()).containsExactly("connectedTrackId");
                        }));
        assertThat(conectada(DISCONNECTOR_A)).isEqualTo(OTHER_TRACK);

        DisconnectorFunctionDTO function = new DisconnectorFunctionDTO();
        function.setCode(FUNCTION_CODE);
        DisconnectorDTO sinPoste = new DisconnectorDTO();
        sinPoste.setName("IT-LINK-PARALELO");
        sinPoste.setOnLoad(false);
        sinPoste.setStationId(STATION);
        sinPoste.setTrackId(TRACK);
        sinPoste.setConnectedTrackId(TRACK);
        sinPoste.setDisconnectorFunction(function);
        assertThatThrownBy(() -> disconnectorService.create(sinPoste)).isInstanceOf(ValidationException.class);

        sinPoste.setConnectedTrackId(OTHER_TRACK);
        DisconnectorDTO creado = disconnectorService.create(sinPoste);
        assertThat(conectada(creado.getId())).isEqualTo(OTHER_TRACK);
    }

    /**
     * El alta en lote no pasa por el {@code Business}, y una estación escribe sus seccionadores con
     * su propio mapper: los dos pasan por el de seccionador, que es donde se mira la vía del poste.
     */
    @Test
    @DisplayName("la vía de su poste tampoco entra en un lote ni escrita desde su estación (V27)")
    void viaConectadaPorTodosLosCaminos() {
        DisconnectorFunctionDTO function = new DisconnectorFunctionDTO();
        function.setCode(FUNCTION_CODE);
        DisconnectorDTO nuevo = new DisconnectorDTO();
        nuevo.setName("IT-LINK-LOTE");
        nuevo.setOnLoad(false);
        nuevo.setStationId(STATION);
        nuevo.setProfileId(PROFILE_FREE);
        nuevo.setConnectedTrackId(TRACK);
        nuevo.setDisconnectorFunction(function);
        assertThatThrownBy(() -> disconnectorService.bulkCreate(List.of(nuevo)))
                .isInstanceOf(ValidationException.class);
        assertThat(jdbcTemplate.queryForObject("select count(*) from disconnector where name = 'IT-LINK-LOTE'",
                Integer.class)).isZero();

        DisconnectorDTO enLote = disconnectorService.getById(DISCONNECTOR_A);
        enLote.setConnectedTrackId(TRACK);
        assertThatThrownBy(() -> disconnectorService.bulkUpdate(List.of(enLote)))
                .isInstanceOf(ValidationException.class);

        StationDTO estacion = stationService.getById(STATION);
        estacion.setTracks(null);
        estacion.setSectionInsulators(null);
        estacion.getDisconnectors().stream()
                .filter(disconnector -> disconnector.getId() == DISCONNECTOR_A)
                .findFirst().orElseThrow()
                .setConnectedTrackId(TRACK);
        assertThatThrownBy(() -> stationService.update(estacion)).isInstanceOf(ValidationException.class);
        assertThat(conectada(DISCONNECTOR_A)).isNull();

        nuevo.setConnectedTrackId(OTHER_TRACK);
        DisconnectorDTO creado = disconnectorService.bulkCreate(List.of(nuevo)).getFirst();
        assertThat(conectada(creado.getId())).isEqualTo(OTHER_TRACK);
    }

    /**
     * Un seccionador en plena vía, en una zona neutra o en una subestación no es de ninguna estación.
     * Se da de alta sin ella si está en algún sitio (en un poste o con su vía propia), y sale en la
     * lista como los demás: buscado por su nombre, filtrado por {@code onLoad} y con la lista ordenada
     * por estación o por poste, donde va al final. La búsqueda y el orden pasan por la estación y el
     * poste, y con QueryDSL eran joins internos que lo dejaban fuera.
     */
    @Test
    @DisplayName("un seccionador sin estación se da de alta si está en algún sitio, y sale en la lista")
    void sinEstacion() {
        DisconnectorDTO enNingunSitio = sinEstacion("IT-LINK-SIN-ESTACION");
        assertThatThrownBy(() -> disconnectorService.create(enNingunSitio))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getErrors())
                        .singleElement()
                        .satisfies(alert -> {
                            assertThat(alert.getMessage()).isEqualTo(ErrorCodes.BUSINESS_RULE_VIOLATION);
                            assertThat(alert.getFields()).containsExactly("stationId");
                        }));

        DisconnectorDTO conViaPropia = sinEstacion("IT-LINK-SIN-ESTACION");
        conViaPropia.setKp("98375.5");
        conViaPropia.setTrackId(TRACK);
        long id = disconnectorService.create(conViaPropia).getId();
        assertThat(jdbcTemplate.queryForMap("select station_id, track_id from disconnector where id = ?", id))
                .containsEntry("station_id", null)
                .containsEntry("track_id", TRACK);
        assertThat(disconnectorService.getById(id).getStationId()).isNull();

        assertThat(lista(new DisconnectorFilter(null, null, null, "IT-LINK-SIN", null), Sort.unsorted()))
                .containsExactly(id);
        // A y B comparten estación: su orden entre ellos no está fijado, el del que no tiene sí.
        assertThat(lista(new DisconnectorFilter("IT-LINK", null, null, null, null), Sort.by("station.name")))
                .containsExactlyInAnyOrder(DISCONNECTOR_A, DISCONNECTOR_B, id)
                .last().isEqualTo(id);
        assertThat(lista(new DisconnectorFilter("IT-LINK", null, null, null, null), Sort.by("profile.profileId")))
                .containsExactly(DISCONNECTOR_A, DISCONNECTOR_B, id);
        assertThat(lista(new DisconnectorFilter("IT-LINK", null, null, null, false), Sort.unsorted()))
                .containsExactly(id);
        assertThat(lista(new DisconnectorFilter("IT-LINK", null, null, null, true), Sort.by("name")))
                .containsExactly(DISCONNECTOR_A, DISCONNECTOR_B);
    }

    private List<Long> lista(DisconnectorFilter filter, Sort sort) {
        return disconnectorService.getDisconnectors(PageRequest.of(0, 50, sort), filter).getContent().stream()
                .map(DisconnectorDTO::getId)
                .toList();
    }

    /** Un seccionador nuevo sin estación ni poste: dónde está lo pone cada caso. */
    private static DisconnectorDTO sinEstacion(String name) {
        DisconnectorFunctionDTO function = new DisconnectorFunctionDTO();
        function.setCode(FUNCTION_CODE);
        DisconnectorDTO dto = new DisconnectorDTO();
        dto.setName(name);
        dto.setOnLoad(false);
        dto.setDisconnectorFunction(function);
        return dto;
    }

    private Long conectada(long disconnectorId) {
        return jdbcTemplate.queryForObject("select connected_track_id from disconnector where id = ?", Long.class,
                disconnectorId);
    }

    @Test
    @DisplayName("dos seccionadores vivos en el mismo perfil siguen siendo un conflicto (409 BUS-002)")
    void dosVivosEnElMismoPerfil() {
        DisconnectorDTO seccionador = disconnectorService.getById(DISCONNECTOR_A);
        seccionador.setProfileId(PROFILE_B);

        assertThatThrownBy(() -> disconnectorService.update(seccionador))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(fila(DISCONNECTOR_A)).containsEntry("profile_id", PROFILE_A);
    }

    @Test
    @DisplayName("un seccionador borrado ya no ocupa su perfil: se puede colgar otro, y el perfil enseña el vivo")
    void elBorradoNoOcupaElPerfil() {
        disconnectorService.delete(disconnectorService.getById(DISCONNECTOR_B));
        DisconnectorDTO seccionador = disconnectorService.getById(DISCONNECTOR_A);
        seccionador.setProfileId(PROFILE_B);

        disconnectorService.update(seccionador);

        assertThat(fila(DISCONNECTOR_B)).containsEntry("deleted", true).containsEntry("profile_id", PROFILE_B);
        assertThat(profileService.getById(PROFILE_B).getDisconnector().getId()).isEqualTo(DISCONNECTOR_A);
    }

    @Test
    @DisplayName("con uno borrado y otro vivo en el mismo perfil, el esquema y el evento del perfil enseñan el vivo")
    @SuppressWarnings("unchecked")
    void esquemaYEventoConElVivo() {
        // El esquema y el evento cargan el seccionador con un join, no con la carga perezosa: son los
        // caminos que veria primero una fila borrada que se colase.
        disconnectorService.delete(disconnectorService.getById(DISCONNECTOR_B));
        DisconnectorDTO seccionador = disconnectorService.getById(DISCONNECTOR_A);
        seccionador.setProfileId(PROFILE_B);
        disconnectorService.update(seccionador);

        TrackSchematicDTO esquema = trackSchematicService.getSchematic(TRACK);

        assertThat(esquema.profiles()).extracting(ProfileNode::id)
                .containsExactlyInAnyOrder(PROFILE_A, PROFILE_B, PROFILE_FREE);
        ProfileNode nodo = esquema.profiles().stream().filter(each -> each.id() == PROFILE_B).findFirst().orElseThrow();
        assertThat(nodo.disconnector().id()).isEqualTo(DISCONNECTOR_A);

        Map<String, Object> payload = new TransactionTemplate(transactionManager).execute(status ->
                profilePayloadMapper.toPayload(profileRepository.findByIdForMessaging(PROFILE_B).orElseThrow()));
        assertThat((Map<String, Object>) payload.get("disconnector")).containsEntry("id", DISCONNECTOR_A);
    }
}
