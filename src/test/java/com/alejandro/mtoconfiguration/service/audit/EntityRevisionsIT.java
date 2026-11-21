package com.alejandro.mtoconfiguration.service.audit;

import com.alejandro.mtoconfiguration.core.exception.NotFoundException;
import com.alejandro.mtoconfiguration.enums.infrastructure.TrackAccessPointKind;
import com.alejandro.mtoconfiguration.enums.infrastructure.TrackAccessPointSide;
import com.alejandro.mtoconfiguration.model.audit.EntityRevisionDTO;
import com.alejandro.mtoconfiguration.model.audit.RevisionMetadataDTO;
import com.alejandro.mtoconfiguration.model.audit.RevisionOperation;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.CantileverDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.DisconnectorDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ProfileDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SectionInsulatorDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SectionInsulatorSwitchDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.StationDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SteadyArmDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.TrackAccessPointDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.CantileverTypeDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.DisconnectorFunctionDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.ProfileStatusDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.SectioningDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.SteadyArmTypeDTO;
import com.alejandro.mtoconfiguration.service.infraestructure.DisconnectorService;
import com.alejandro.mtoconfiguration.service.infraestructure.ProfileService;
import com.alejandro.mtoconfiguration.service.infraestructure.SectionInsulatorService;
import com.alejandro.mtoconfiguration.service.infraestructure.StationService;
import com.alejandro.mtoconfiguration.service.infraestructure.TrackAccessPointService;
import com.alejandro.mtoconfiguration.service.lov.SectioningService;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El historial de una fila ({@code GET /{recurso}/{id}/revisions}) contra PostgreSQL: lo que Envers
 * guardó de verdad al escribir por los servicios, leído por donde lo lee el controlador
 * ({@code BaseService.findRevisions} y {@code AbstractLovCrudService.findRevisions}).
 *
 * <p>Solo aquí se ve que la consulta de revisiones pagina y ordena en la base, que el borrado lógico
 * de un maestro se lee como una baja aunque Envers lo apunte como modificación, que la fila de una
 * revisión se puede mapear a su DTO dentro de la transacción de la lectura (sus relaciones son
 * perezosas) y qué guarda Envers del borrado físico de un catálogo.
 */
@SpringBootTest
@DisplayName("El historial de una fila, contra PostgreSQL")
class EntityRevisionsIT {

    private static final long PACKAGE = -611L;
    private static final long STATION = -612L;
    private static final long TRACK = -613L;
    private static final long OTHER_TRACK = -614L;
    private static final long PROFILE = -615L;
    private static final long FUNCTION = -616L;
    private static final String FUNCTION_CODE = "IT-REV-FN";
    private static final long CANTILEVER_TYPE = -617L;
    private static final long ARM_TYPE = -618L;
    private static final String CHILD_LOV_CODE = "IT-REV-CH";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.registerProperties(registry);
        registry.add("app.lov.seed-on-startup", () -> "false");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TrackAccessPointService trackAccessPointService;
    @Autowired
    private SectionInsulatorService sectionInsulatorService;
    @Autowired
    private DisconnectorService disconnectorService;
    @Autowired
    private StationService stationService;
    @Autowired
    private SectioningService sectioningService;
    @Autowired
    private ProfileService profileService;

    @BeforeEach
    void inserta() {
        limpia();
        jdbcTemplate.update("""
                insert into disconnector_function (id, code, description, enabled, create_date,
                        create_user, version_date, version_user, version_number)
                values (?, ?, 'Historial', true, now(), 'test', now(), 'test', 1)
                """, FUNCTION, FUNCTION_CODE);
        for (var lov : java.util.Map.of("cantilever_type", CANTILEVER_TYPE, "steady_arm_type", ARM_TYPE).entrySet()) {
            jdbcTemplate.update("insert into " + lov.getKey() + " (id, code, description, enabled, create_date,"
                    + " create_user, version_date, version_user, version_number)"
                    + " values (?, ?, 'Historial', true, now(), 'test', now(), 'test', 1)",
                    lov.getValue(), CHILD_LOV_CODE);
        }
        jdbcTemplate.update("""
                insert into execution_package (id, name, enabled, deleted, initial_package,
                        length, start_date, end_date, create_date, create_user,
                        version_date, version_user, version_number)
                values (?, 'IT-REV', true, false, false, 0, date '2020-01-01',
                        date '2020-12-31', now(), 'test', now(), 'test', 1)
                """, PACKAGE);
        jdbcTemplate.update("""
                insert into station (id, name, execution_package_id, deleted, create_date, create_user,
                        version_date, version_user, version_number)
                values (?, 'IT-REV-STATION', ?, false, now(), 'test', now(), 'test', 1)
                """, STATION, PACKAGE);
        via(TRACK, "IT-REV-TRACK");
        via(OTHER_TRACK, "IT-REV-TRACK-2");
        jdbcTemplate.update("""
                insert into profile (id, profile_id, kilometric_point, track_id, profile_status_id,
                        deleted, create_date, create_user, version_date, version_user, version_number)
                values (?, 'IT-REV-P1', 1500.000, ?, (select id from profile_status where code = 'PROVISIONAL'),
                        false, now(), 'test', now(), 'test', 1)
                """, PROFILE, TRACK);
    }

    private void via(long id, String name) {
        jdbcTemplate.update("""
                insert into track (id, name, status, deleted, execution_package_id, create_date,
                        create_user, version_date, version_user, version_number)
                values (?, ?, true, false, ?, now(), 'test', now(), 'test', 1)
                """, id, name, PACKAGE);
    }

    @AfterEach
    void limpia() {
        RequestContextHolder.resetRequestAttributes();
        jdbcTemplate.update("delete from outbox_message where payload::text like '%IT-REV%'");
        for (String tabla : List.of("track_access_point", "disconnector")) {
            jdbcTemplate.update("delete from " + tabla + "_aud where name like 'IT-REV%'");
            jdbcTemplate.update("delete from " + tabla + " where name like 'IT-REV%'");
        }
        jdbcTemplate.update("delete from section_insulator_switch_aud where code in ('W9901', 'W9902')");
        jdbcTemplate.update("delete from section_insulator_switch where code in ('W9901', 'W9902')");
        jdbcTemplate.update("delete from section_insulator_aud where name like 'IT-REV%'");
        jdbcTemplate.update("delete from section_insulator where name like 'IT-REV%'");
        jdbcTemplate.update("delete from profile_sectioning where sectioning_id in"
                + " (select id from sectioning where code like 'IT-REV%')");
        jdbcTemplate.update("delete from profile_sectioning_aud where sectioning_id in"
                + " (select id from sectioning where code like 'IT-REV%')");
        jdbcTemplate.update("delete from sectioning_aud where code like 'IT-REV%' or id in "
                + "(select id from sectioning_aud where code like 'IT-REV%')");
        jdbcTemplate.update("delete from sectioning where code like 'IT-REV%'");
        String perfiles = "(select id from profile_aud where profile_id like 'IT-REV-H%'"
                + " union select id from profile where profile_id like 'IT-REV-H%')";
        String mensulas = "(select id from cantilever_aud where profile_id in " + perfiles + ")";
        jdbcTemplate.update("delete from steady_arm where cantilever_id in " + mensulas);
        jdbcTemplate.update("delete from steady_arm_aud where cantilever_id in " + mensulas);
        jdbcTemplate.update("delete from cantilever where profile_id in " + perfiles);
        jdbcTemplate.update("delete from cantilever_aud where profile_id in " + perfiles);
        jdbcTemplate.update("delete from profile where profile_id like 'IT-REV-H%'");
        jdbcTemplate.update("delete from profile_aud where profile_id like 'IT-REV-H%'");
        jdbcTemplate.update("delete from profile_aud where id = ?", PROFILE);
        jdbcTemplate.update("delete from profile where id = ?", PROFILE);
        String vias = "(select id from track_aud where name like 'IT-REV%' union select id from track where name like 'IT-REV%')";
        jdbcTemplate.update("delete from track_station where track_id in " + vias);
        jdbcTemplate.update("delete from track_station_aud where track_id in " + vias);
        jdbcTemplate.update("delete from track_aud where id in " + vias);
        jdbcTemplate.update("delete from track where name like 'IT-REV%'");
        jdbcTemplate.update("delete from station_aud where name like 'IT-REV%'");
        jdbcTemplate.update("delete from station where name like 'IT-REV%'");
        jdbcTemplate.update("delete from station_aud where id = ?", STATION);
        jdbcTemplate.update("delete from station where id = ?", STATION);
        jdbcTemplate.update("delete from execution_package_aud where id = ?", PACKAGE);
        jdbcTemplate.update("delete from execution_package where id = ?", PACKAGE);
        jdbcTemplate.update("delete from disconnector_function where id = ?", FUNCTION);
        jdbcTemplate.update("delete from cantilever_type where id = ?", CANTILEVER_TYPE);
        jdbcTemplate.update("delete from steady_arm_type where id = ?", ARM_TYPE);
    }

    private static TrackAccessPointDTO acceso(String name) {
        TrackAccessPointDTO dto = new TrackAccessPointDTO();
        dto.setName(name);
        dto.setTrackId(TRACK);
        dto.setStationId(STATION);
        dto.setKp(new BigDecimal("110176.000"));
        dto.setKind(TrackAccessPointKind.PREPARED_ACCESS);
        dto.setSide(TrackAccessPointSide.LEFT);
        return dto;
    }

    /** Una petición en curso, como la que ve {@code RevisionEntityListener} al crear la revisión. */
    private static void peticion(String method, String uri, String correlationId) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.addHeader("X-Correlation-Id", correlationId);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    @DisplayName("alta, modificación y borrado lógico de un maestro: tres revisiones, la más reciente primero, cada una con la fila como quedó")
    void historialDeUnMaestro() {
        Instant antes = Instant.now().minus(1, ChronoUnit.SECONDS);

        peticion("POST", "/api/v1/configuration/track-access-points", "corr-alta");
        TrackAccessPointDTO creado = trackAccessPointService.create(acceso("IT-REV-A"));

        peticion("PUT", "/api/v1/configuration/track-access-points/" + creado.getId(), "corr-cambio");
        creado.setName("IT-REV-A2");
        creado.setKp(new BigDecimal("110200.000"));
        TrackAccessPointDTO cambiado = trackAccessPointService.update(creado);

        RequestContextHolder.resetRequestAttributes();
        trackAccessPointService.delete(cambiado);

        Page<EntityRevisionDTO<TrackAccessPointDTO>> pagina =
                trackAccessPointService.findRevisions(creado.getId(), PageRequest.of(0, 20));

        assertThat(pagina.getTotalElements()).isEqualTo(3);
        assertThat(pagina.getContent()).extracting(each -> each.revision().operation())
                .containsExactly(RevisionOperation.DELETED, RevisionOperation.UPDATED, RevisionOperation.CREATED);
        assertThat(pagina.getContent()).extracting(each -> each.revision().revision())
                .isSortedAccordingTo((a, b) -> Long.compare(b, a));
        assertThat(pagina.getContent()).extracting(each -> each.entity().getName())
                .containsExactly("IT-REV-A2", "IT-REV-A2", "IT-REV-A");
        assertThat(pagina.getContent()).extracting(each -> each.entity().getKp())
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("110200"), new BigDecimal("110200"), new BigDecimal("110176"));
        assertThat(pagina.getContent()).extracting(each -> each.entity().getVersionNumber())
                .as("Envers no guarda la versión del bloqueo optimista: no se inventa")
                .containsOnlyNulls();
        assertThat(pagina.getContent()).allSatisfy(each -> {
            assertThat(each.entity().getId()).isEqualTo(creado.getId());
            assertThat(each.entity().getTrackId()).isEqualTo(TRACK);
            assertThat(each.entity().getStationId()).isEqualTo(STATION);
            assertThat(each.revision().revisionAt()).isAfter(antes).isBeforeOrEqualTo(Instant.now());
            assertThat(each.revision().author()).isEqualTo("system");
        });

        RevisionMetadataDTO alta = pagina.getContent().get(2).revision();
        assertThat(alta.source()).isEqualTo("HTTP");
        assertThat(alta.requestMethod()).isEqualTo("POST");
        assertThat(alta.requestUri()).isEqualTo("/api/v1/configuration/track-access-points");
        assertThat(alta.correlationId()).isEqualTo("corr-alta");

        RevisionMetadataDTO cambio = pagina.getContent().get(1).revision();
        assertThat(cambio.requestMethod()).isEqualTo("PUT");
        assertThat(cambio.correlationId()).isEqualTo("corr-cambio");

        RevisionMetadataDTO baja = pagina.getContent().get(0).revision();
        assertThat(baja.source()).as("sin petición en curso").isEqualTo("SYSTEM");
        assertThat(baja.requestMethod()).isNull();
        assertThat(baja.correlationId()).isNull();
    }

    @Test
    @DisplayName("el historial se pagina en la base: cada página trae las suyas y el total es el de todas")
    void paginado() {
        TrackAccessPointDTO dto = trackAccessPointService.create(acceso("IT-REV-PAG-0"));
        for (int i = 1; i <= 4; i++) {
            dto.setName("IT-REV-PAG-" + i);
            dto = trackAccessPointService.update(dto);
        }
        Long id = dto.getId();

        Page<EntityRevisionDTO<TrackAccessPointDTO>> primera = trackAccessPointService.findRevisions(id, PageRequest.of(0, 2));
        Page<EntityRevisionDTO<TrackAccessPointDTO>> tercera = trackAccessPointService.findRevisions(id, PageRequest.of(2, 2));
        Page<EntityRevisionDTO<TrackAccessPointDTO>> masAlla = trackAccessPointService.findRevisions(id, PageRequest.of(7, 2));

        assertThat(primera.getTotalElements()).isEqualTo(5);
        assertThat(primera.getTotalPages()).isEqualTo(3);
        assertThat(primera.getContent()).extracting(each -> each.entity().getName())
                .containsExactly("IT-REV-PAG-4", "IT-REV-PAG-3");
        assertThat(tercera.getContent()).extracting(each -> each.entity().getName())
                .containsExactly("IT-REV-PAG-0");
        assertThat(tercera.getContent().getFirst().revision().operation()).isEqualTo(RevisionOperation.CREATED);
        assertThat(masAlla.getContent()).as("una página más allá del final va vacía, no es un 404").isEmpty();
        assertThat(masAlla.getTotalElements()).isEqualTo(5);
    }

    @Test
    @DisplayName("un id sin ninguna revisión es un 404: el que no existe, y la fila que una migración sembró con SQL")
    void sinHistorial() {
        assertThatThrownBy(() -> trackAccessPointService.findRevisions(-999_999L, PageRequest.of(0, 20)))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> stationService.findRevisions(STATION, PageRequest.of(0, 20)))
                .as("la estación de este test se insertó con SQL: existe, pero Envers no la vio")
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("un aislador sale en cada revisión con sus vías y con las agujas que tenía entonces")
    void aisladorConSusAgujas() {
        SectionInsulatorDTO dto = new SectionInsulatorDTO();
        dto.setName("IT-REV-SI");
        dto.setEnabled(true);
        dto.setStationId(STATION);
        dto.setTrackId(TRACK);
        dto.setKp(new BigDecimal("1200.000"));
        dto.setSwitches(new java.util.ArrayList<>(List.of(aguja("W9901"))));
        SectionInsulatorDTO creado = sectionInsulatorService.create(dto);

        creado.setConnectedTrackId(OTHER_TRACK);
        creado.getSwitches().add(aguja("W9902"));
        sectionInsulatorService.update(creado);

        List<EntityRevisionDTO<SectionInsulatorDTO>> revisiones =
                sectionInsulatorService.findRevisions(creado.getId(), PageRequest.of(0, 20)).getContent();

        assertThat(revisiones).hasSize(2);
        assertThat(revisiones.get(0).entity().getConnectedTrackId()).isEqualTo(OTHER_TRACK);
        assertThat(revisiones.get(0).entity().getSwitches()).extracting(SectionInsulatorSwitchDTO::getCode)
                .containsExactlyInAnyOrder("W9901", "W9902");
        assertThat(revisiones.get(1).entity().getConnectedTrackId()).isNull();
        assertThat(revisiones.get(1).entity().getTrackId()).isEqualTo(TRACK);
        assertThat(revisiones.get(1).entity().getSwitches()).extracting(SectionInsulatorSwitchDTO::getCode)
                .containsExactly("W9901");
    }

    private static SectionInsulatorSwitchDTO aguja(String code) {
        SectionInsulatorSwitchDTO dto = new SectionInsulatorSwitchDTO();
        dto.setCode(code);
        dto.setEnabled(true);
        return dto;
    }

    @Test
    @DisplayName("un seccionador en un poste sale con su poste, leído de la auditoría del poste, y con su función, que no está auditada como relación: se lee como está hoy")
    void seccionadorConSuPoste() {
        DisconnectorFunctionDTO function = new DisconnectorFunctionDTO();
        function.setCode(FUNCTION_CODE);
        DisconnectorDTO dto = new DisconnectorDTO();
        dto.setName("IT-REV-D");
        dto.setOnLoad(false);
        dto.setStationId(STATION);
        dto.setProfileId(PROFILE);
        dto.setDisconnectorFunction(function);
        DisconnectorDTO creado = disconnectorService.create(dto);

        List<EntityRevisionDTO<DisconnectorDTO>> revisiones =
                disconnectorService.findRevisions(creado.getId(), PageRequest.of(0, 20)).getContent();

        assertThat(revisiones).hasSize(1);
        DisconnectorDTO entonces = revisiones.getFirst().entity();
        assertThat(entonces.getName()).isEqualTo("IT-REV-D");
        assertThat(entonces.getProfileId()).isEqualTo(PROFILE);
        assertThat(entonces.getProfileCode()).isEqualTo("IT-REV-P1");
        assertThat(entonces.getDisconnectorFunction().getCode()).isEqualTo(FUNCTION_CODE);
    }

    @Test
    @DisplayName("si el poste de entonces se borró después, el historial del seccionador lo sigue nombrando")
    void seccionadorCuyoPosteSeBorro() {
        DisconnectorFunctionDTO function = new DisconnectorFunctionDTO();
        function.setCode(FUNCTION_CODE);
        DisconnectorDTO dto = new DisconnectorDTO();
        dto.setName("IT-REV-D2");
        dto.setOnLoad(false);
        dto.setStationId(STATION);
        dto.setProfileId(PROFILE);
        dto.setDisconnectorFunction(function);
        DisconnectorDTO creado = disconnectorService.create(dto);

        jdbcTemplate.update("update profile set deleted = true where id = ?", PROFILE);

        List<EntityRevisionDTO<DisconnectorDTO>> revisiones =
                disconnectorService.findRevisions(creado.getId(), PageRequest.of(0, 20)).getContent();

        assertThat(revisiones).hasSize(1);
        assertThat(revisiones.getFirst().revision().operation()).isEqualTo(RevisionOperation.CREATED);
        assertThat(revisiones.getFirst().entity()).isNotNull();
        assertThat(revisiones.getFirst().entity().getProfileId()).isEqualTo(PROFILE);
        assertThat(revisiones.getFirst().entity().getProfileCode())
                .as("el poste se lee de su auditoría (profile_aud), que lo conserva también borrado")
                .isEqualTo("IT-REV-P1");
    }

    @Test
    @DisplayName("una estación sale con la forma de su fila en las listas: sin sus hijos")
    void estacionSinHijos() {
        StationDTO dto = new StationDTO();
        dto.setName("IT-REV-STATION-2");
        dto.setExecutionPackageId(PACKAGE);
        StationDTO creada = stationService.create(dto);
        try {
            List<EntityRevisionDTO<StationDTO>> revisiones =
                    stationService.findRevisions(creada.getId(), PageRequest.of(0, 20)).getContent();

            assertThat(revisiones).hasSize(1);
            assertThat(revisiones.getFirst().revision().operation()).isEqualTo(RevisionOperation.CREATED);
            assertThat(revisiones.getFirst().entity().getName()).isEqualTo("IT-REV-STATION-2");
            assertThat(revisiones.getFirst().entity().getExecutionPackageId()).isEqualTo(PACKAGE);
        } finally {
            jdbcTemplate.update("delete from station_aud where id = ?", creada.getId());
            jdbcTemplate.update("delete from station where id = ?", creada.getId());
        }
    }

    @Test
    @DisplayName("un catálogo: alta, modificación y borrado físico; la revisión del borrado solo guarda el id")
    void historialDeUnCatalogo() {
        SectioningDTO dto = new SectioningDTO();
        dto.setCode("IT-REV-LOV");
        dto.setDescription("Uno");
        dto.setEnabled(true);
        SectioningDTO creado = sectioningService.create(dto);

        creado.setDescription("Dos");
        sectioningService.update(creado.getId(), creado);
        sectioningService.delete(creado.getId());

        Page<EntityRevisionDTO<SectioningDTO>> pagina =
                sectioningService.findRevisions(creado.getId(), PageRequest.of(0, 20));

        assertThat(pagina.getContent()).extracting(each -> each.revision().operation())
                .containsExactly(RevisionOperation.DELETED, RevisionOperation.UPDATED, RevisionOperation.CREATED);
        assertThat(pagina.getContent().get(1).entity().getDescription()).isEqualTo("Dos");
        assertThat(pagina.getContent().get(2).entity().getDescription()).isEqualTo("Uno");
        assertThat(pagina.getContent().get(2).entity().getCode()).isEqualTo("IT-REV-LOV");

        SectioningDTO borrada = pagina.getContent().get(0).entity();
        assertThat(borrada.getId()).isEqualTo(creado.getId());
        assertThat(borrada.getCode()).as("Envers no guarda los datos en la revisión de un borrado físico").isNull();
        assertThat(pagina.getContent()).extracting(each -> each.entity().getVersionNumber()).containsOnlyNulls();

        assertThatThrownBy(() -> sectioningService.findRevisions(-999_999L, PageRequest.of(0, 20)))
                .isInstanceOf(NotFoundException.class);
    }

    /**
     * Envers lee los hijos de una revisión de la gemela {@code _aud} del hijo, donde no se aplica
     * ningún {@code @SQLRestriction("deleted = false")}: el hijo borrado en lógico (que para Envers
     * es una modificación) seguía saliendo en todas las revisiones posteriores a su borrado.
     */
    @Test
    @DisplayName("un perfil sale en cada revisión con las ménsulas y el brazo que tenía entonces: lo quitado deja de salir")
    void perfilConLasMensulasYElBrazoQueTeniaEntonces() {
        ProfileDTO creado = profileService.create(perfil("IT-REV-H1", mensula("100", true), mensula("200", false)));

        ProfileDTO sinLaSegunda = profileService.getById(creado.getId());
        sinLaSegunda.getCantilevers().removeIf(each -> each.getSteadyArm() == null);
        sinLaSegunda.setKp("11.000"); // el perfil cambia tambien: quitar un hijo no es una revision suya
        profileService.update(sinLaSegunda);

        ProfileDTO sinBrazo = profileService.getById(creado.getId());
        sinBrazo.getCantilevers().getFirst().setSteadyArm(null);
        sinBrazo.setKp("12.000");
        profileService.update(sinBrazo);

        List<EntityRevisionDTO<ProfileDTO>> revisiones =
                profileService.findRevisions(creado.getId(), PageRequest.of(0, 20)).getContent();

        assertThat(revisiones).hasSize(3);
        assertThat(mensulas(revisiones.get(2))).as("al crearlo").containsExactly("100+brazo", "200");
        assertThat(mensulas(revisiones.get(1))).as("quitada la segunda ménsula").containsExactly("100+brazo");
        assertThat(mensulas(revisiones.get(0))).as("quitado el brazo de la primera").containsExactly("100");
        assertThat(mensulas(revisiones.get(0)))
                .as("la última revisión es lo que devuelve hoy el detalle")
                .isEqualTo(profileService.getById(creado.getId()).getCantilevers().stream()
                        .map(EntityRevisionsIT::mensula).toList());
    }

    @Test
    @DisplayName("un perfil deja de llevar en su historial el seccionador que se borró, desde la revisión en que se borró")
    void perfilSinElSeccionadorBorrado() {
        ProfileDTO creado = profileService.create(perfil("IT-REV-H2"));
        DisconnectorFunctionDTO function = new DisconnectorFunctionDTO();
        function.setCode(FUNCTION_CODE);
        DisconnectorDTO seccionador = new DisconnectorDTO();
        seccionador.setName("IT-REV-D3");
        seccionador.setOnLoad(false);
        seccionador.setStationId(STATION);
        seccionador.setProfileId(creado.getId());
        seccionador.setDisconnectorFunction(function);
        seccionador = disconnectorService.create(seccionador);

        ProfileDTO conSeccionador = profileService.getById(creado.getId());
        conSeccionador.setKp("11.000");
        profileService.update(conSeccionador);

        disconnectorService.delete(seccionador);
        ProfileDTO sinSeccionador = profileService.getById(creado.getId());
        sinSeccionador.setKp("12.000");
        profileService.update(sinSeccionador);

        List<EntityRevisionDTO<ProfileDTO>> revisiones =
                profileService.findRevisions(creado.getId(), PageRequest.of(0, 20)).getContent();

        EntityRevisionDTO<ProfileDTO> conEl = revisiones.stream()
                .filter(each -> "11.000".equals(each.entity().getKp())).findFirst().orElseThrow();
        EntityRevisionDTO<ProfileDTO> sinEl = revisiones.stream()
                .filter(each -> "12.000".equals(each.entity().getKp())).findFirst().orElseThrow();
        assertThat(conEl.entity().getDisconnectors()).extracting(DisconnectorDTO::getName)
                .containsExactly("IT-REV-D3");
        assertThat(sinEl.entity().getDisconnectors()).isEmpty();
        assertThat(sinEl.entity().getDisconnector()).isNull();
    }

    @Test
    @DisplayName("un aislador deja de llevar en su historial la aguja que se le quitó")
    void aisladorSinLaAgujaQuitada() {
        SectionInsulatorDTO dto = new SectionInsulatorDTO();
        dto.setName("IT-REV-SI2");
        dto.setEnabled(true);
        dto.setStationId(STATION);
        dto.setTrackId(TRACK);
        dto.setKp(new BigDecimal("1300.000"));
        dto.setSwitches(new java.util.ArrayList<>(List.of(aguja("W9901"), aguja("W9902"))));
        SectionInsulatorDTO creado = sectionInsulatorService.create(dto);

        creado.getSwitches().removeIf(each -> "W9902".equals(each.getCode()));
        creado.setKp(new BigDecimal("1301.000")); // el aislador cambia tambien: ver el caso del perfil
        sectionInsulatorService.update(creado);

        List<EntityRevisionDTO<SectionInsulatorDTO>> revisiones =
                sectionInsulatorService.findRevisions(creado.getId(), PageRequest.of(0, 20)).getContent();

        assertThat(revisiones).hasSize(2);
        assertThat(revisiones.get(1).entity().getSwitches()).extracting(SectionInsulatorSwitchDTO::getCode)
                .containsExactlyInAnyOrder("W9901", "W9902");
        assertThat(revisiones.get(0).entity().getSwitches()).extracting(SectionInsulatorSwitchDTO::getCode)
                .containsExactly("W9901");
    }

    /**
     * El límite que documenta README_API.md §6. Los tres conjuntos de catálogo del perfil están
     * anotados {@code NOT_AUDITED}, pero Envers solo respeta ese modo en una relación a uno: en una
     * colección cruza la gemela de unión con la gemela {@code _aud} del catálogo. Una entrada sin
     * ninguna revisión (sembrada con SQL) no tiene con qué cruzarse y no sale. Toda entrada escrita
     * por la API, por el importador o por la siembra del arranque tiene la de su alta, y ninguna
     * migración siembra estos tres catálogos ({@code FlywayMigrationIT}).
     */
    @Test
    @DisplayName("los seccionamientos de un perfil salen en su historial si la entrada de catálogo tiene alguna revisión")
    void perfilConSusSeccionamientos() {
        SectioningDTO porLaApi = new SectioningDTO();
        porLaApi.setCode("IT-REV-SEC-API");
        porLaApi.setDescription("Por la API");
        porLaApi.setEnabled(true);
        sectioningService.create(porLaApi);
        jdbcTemplate.update("""
                insert into sectioning (id, code, description, enabled, create_date, create_user,
                        version_date, version_user, version_number)
                values (-619, 'IT-REV-SEC-SQL', 'Con SQL', true, now(), 'test', now(), 'test', 1)
                """);
        ProfileDTO dto = perfil("IT-REV-H3");
        SectioningDTO uno = new SectioningDTO();
        uno.setCode("IT-REV-SEC-API");
        SectioningDTO otro = new SectioningDTO();
        otro.setCode("IT-REV-SEC-SQL");
        dto.setSectionings(new java.util.ArrayList<>(List.of(uno, otro)));
        ProfileDTO creado = profileService.create(dto);

        assertThat(profileService.getById(creado.getId()).getSectionings()).extracting(SectioningDTO::getCode)
                .containsExactlyInAnyOrder("IT-REV-SEC-API", "IT-REV-SEC-SQL");

        List<EntityRevisionDTO<ProfileDTO>> revisiones =
                profileService.findRevisions(creado.getId(), PageRequest.of(0, 20)).getContent();

        assertThat(revisiones).hasSize(1);
        assertThat(revisiones.getFirst().entity().getSectionings()).extracting(SectioningDTO::getCode)
                .as("la entrada sembrada con SQL no tiene revisión con la que cruzarse")
                .containsExactly("IT-REV-SEC-API");
    }

    private static ProfileDTO perfil(String code, CantileverDTO... mensulas) {
        ProfileStatusDTO status = new ProfileStatusDTO();
        status.setCode("PROVISIONAL");
        ProfileDTO dto = new ProfileDTO();
        dto.setProfileId(code);
        dto.setKp("10.000");
        dto.setTrackId(TRACK);
        dto.setProfileStatus(status);
        dto.setCantilevers(new java.util.ArrayList<>(List.of(mensulas)));
        return dto;
    }

    private static CantileverDTO mensula(String stagger, boolean conBrazo) {
        CantileverTypeDTO type = new CantileverTypeDTO();
        type.setCode(CHILD_LOV_CODE);
        CantileverDTO dto = new CantileverDTO();
        dto.setStagger(new BigDecimal(stagger));
        dto.setCantileverType(type);
        if (conBrazo) {
            SteadyArmTypeDTO armType = new SteadyArmTypeDTO();
            armType.setCode(CHILD_LOV_CODE);
            SteadyArmDTO arm = new SteadyArmDTO();
            arm.setLength(1000L);
            arm.setSteadyArmType(armType);
            dto.setSteadyArm(arm);
        }
        return dto;
    }

    private static List<String> mensulas(EntityRevisionDTO<ProfileDTO> revision) {
        return revision.entity().getCantilevers().stream().map(EntityRevisionsIT::mensula).toList();
    }

    private static String mensula(CantileverDTO cantilever) {
        return cantilever.getStagger().toBigInteger() + (cantilever.getSteadyArm() == null ? "" : "+brazo");
    }
}
