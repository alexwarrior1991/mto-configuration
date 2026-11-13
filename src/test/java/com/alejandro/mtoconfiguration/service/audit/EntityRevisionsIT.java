package com.alejandro.mtoconfiguration.service.audit;

import com.alejandro.mtoconfiguration.core.exception.NotFoundException;
import com.alejandro.mtoconfiguration.enums.infrastructure.TrackAccessPointKind;
import com.alejandro.mtoconfiguration.enums.infrastructure.TrackAccessPointSide;
import com.alejandro.mtoconfiguration.model.audit.EntityRevisionDTO;
import com.alejandro.mtoconfiguration.model.audit.RevisionMetadataDTO;
import com.alejandro.mtoconfiguration.model.audit.RevisionOperation;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.DisconnectorDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SectionInsulatorDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SectionInsulatorSwitchDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.StationDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.TrackAccessPointDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.DisconnectorFunctionDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.SectioningDTO;
import com.alejandro.mtoconfiguration.service.infraestructure.DisconnectorService;
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

    @BeforeEach
    void inserta() {
        limpia();
        jdbcTemplate.update("""
                insert into disconnector_function (id, code, description, enabled, create_date,
                        create_user, version_date, version_user, version_number)
                values (?, ?, 'Historial', true, now(), 'test', now(), 'test', 1)
                """, FUNCTION, FUNCTION_CODE);
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
        jdbcTemplate.update("delete from sectioning_aud where code like 'IT-REV%' or id in "
                + "(select id from sectioning_aud where code like 'IT-REV%')");
        jdbcTemplate.update("delete from sectioning where code like 'IT-REV%'");
        jdbcTemplate.update("delete from profile_aud where id = ?", PROFILE);
        jdbcTemplate.update("delete from profile where id = ?", PROFILE);
        jdbcTemplate.update("delete from track_aud where id in (?, ?)", TRACK, OTHER_TRACK);
        jdbcTemplate.update("delete from track where id in (?, ?)", TRACK, OTHER_TRACK);
        jdbcTemplate.update("delete from station_aud where id = ?", STATION);
        jdbcTemplate.update("delete from station where id = ?", STATION);
        jdbcTemplate.update("delete from execution_package_aud where id = ?", PACKAGE);
        jdbcTemplate.update("delete from execution_package where id = ?", PACKAGE);
        jdbcTemplate.update("delete from disconnector_function where id = ?", FUNCTION);
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
    @DisplayName("un seccionador en un poste sale con su poste y su función, que no están auditados como relación: se leen como están hoy")
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
                .as("el poste es una relación sin auditar: se lee como está hoy, también borrado")
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
}
