package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.enums.infrastructure.SectionInsulatorInstallationType;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ProfileDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SectionInsulatorDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.ProfileFilter;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.SectionInsulatorFilter;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las listas filtradas ({@code POST /{recurso}/filter}) no pierden la fila cuya referencia opcional
 * está vacía, ni al buscar por texto ni al ordenar por esa referencia.
 *
 * <p>La búsqueda por texto de los aisladores llegaba con QueryDSL a la vía con un join implícito, que
 * es interno: un aislador sin vía desaparecía aunque casara por su nombre. El orden ya iba con un join
 * externo, porque QueryDSL crea uno para el {@code ORDER BY}, y se comprueba igual: en los aisladores
 * por la vía, y en los perfiles por el tipo de poste, que es opcional, y por el estado, que el
 * validador exige pero la columna admite vacío. El seccionador sin estación o sin poste lo prueba
 * {@code DisconnectorLinkIT}.
 *
 * <p>Los datos se insertan con SQL, como en {@code FilterOutsideTransactionIT}: así puede haber un
 * perfil sin estado.
 */
@SpringBootTest
@DisplayName("Listas filtradas con una referencia opcional vacía")
class FilterOptionalReferencesIT {

    private static final long PACKAGE = -61L;
    private static final long STATION = -62L;
    private static final long TRACK = -63L;
    private static final long POLE_TYPE = -64L;
    private static final long PROFILE_WITH_POLE_TYPE = -65L;
    private static final long PROFILE_WITHOUT_POLE_TYPE = -66L;
    private static final long INSULATOR_ON_TRACK = -67L;
    private static final long INSULATOR_WITHOUT_TRACK = -68L;
    private static final long SWITCH_W31 = -69L;
    private static final long SWITCH_W32 = -70L;

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
    private SectionInsulatorService sectionInsulatorService;

    @BeforeEach
    void inserta() {
        limpia();
        jdbcTemplate.update("""
                insert into execution_package (id, name, enabled, deleted, initial_package,
                        length, start_date, end_date, create_date, create_user,
                        version_date, version_user, version_number)
                values (?, 'IT-OPT', true, false, false, 0, date '2020-01-01',
                        date '2020-12-31', now(), 'test', now(), 'test', 1)
                """, PACKAGE);
        jdbcTemplate.update("""
                insert into station (id, name, execution_package_id, deleted, create_date, create_user,
                        version_date, version_user, version_number)
                values (?, 'IT-OPT-STATION', ?, false, now(), 'test', now(), 'test', 1)
                """, STATION, PACKAGE);
        jdbcTemplate.update("""
                insert into track (id, name, status, deleted, execution_package_id, create_date,
                        create_user, version_date, version_user, version_number)
                values (?, 'IT-OPT-TRACK', true, false, ?, now(), 'test', now(), 'test', 1)
                """, TRACK, PACKAGE);
        jdbcTemplate.update("""
                insert into pole_type (id, code, description, enabled, create_date, create_user,
                        version_date, version_user, version_number)
                values (?, 'ITOPT', 'Tipo de poste de IT-OPT', true, now(), 'test', now(), 'test', 1)
                """, POLE_TYPE);
        perfil(PROFILE_WITH_POLE_TYPE, "IT-OPT-A", "1.000", POLE_TYPE);
        perfil(PROFILE_WITHOUT_POLE_TYPE, "IT-OPT-B", "2.000", null);
        aislador(INSULATOR_ON_TRACK, "IT-OPT-INS-A", TRACK, "IN_TRACK");
        aislador(INSULATOR_WITHOUT_TRACK, "IT-OPT-INS-B", null, null);
        aguja(SWITCH_W31, "W31");
        aguja(SWITCH_W32, "W32");
    }

    /** Sin estado, que la columna admite. */
    private void perfil(long id, String code, String kp, Long poleType) {
        jdbcTemplate.update("""
                insert into profile (id, profile_id, kilometric_point, track_id, pole_type_id, deleted,
                        create_date, create_user, version_date, version_user, version_number)
                values (?, ?, ?::numeric, ?, ?, false, now(), 'test', now(), 'test', 1)
                """, id, code, kp, TRACK, poleType);
    }

    /** Sin tipo de instalación la vía es opcional, como en los que ya estaban antes de V23. */
    private void aislador(long id, String name, Long track, String installationType) {
        jdbcTemplate.update("""
                insert into section_insulator (id, name, status, station_id, track_id, kilometric_point,
                        installation_type, deleted, create_date, create_user, version_date, version_user,
                        version_number)
                values (?, ?, true, ?, ?, 1.500, ?, false, now(), 'test', now(), 'test', 1)
                """, id, name, STATION, track, installationType);
    }

    /** Las dos agujas son del aislador con vía, y las dos casan con «W3». */
    private void aguja(long id, String code) {
        jdbcTemplate.update("""
                insert into section_insulator_switch (id, code, section_insulator_id, track_id,
                        kilometric_point, turnout_denominator, status, deleted, create_date, create_user,
                        version_date, version_user, version_number)
                values (?, ?, ?, ?, 1.500, 9, true, false, now(), 'test', now(), 'test', 1)
                """, id, code, INSULATOR_ON_TRACK, TRACK);
    }

    @AfterEach
    void limpia() {
        jdbcTemplate.update("delete from section_insulator_switch where id in (?, ?)", SWITCH_W31, SWITCH_W32);
        jdbcTemplate.update("delete from section_insulator where id in (?, ?)", INSULATOR_ON_TRACK,
                INSULATOR_WITHOUT_TRACK);
        jdbcTemplate.update("delete from profile where id in (?, ?)", PROFILE_WITH_POLE_TYPE,
                PROFILE_WITHOUT_POLE_TYPE);
        jdbcTemplate.update("delete from pole_type where id = ?", POLE_TYPE);
        jdbcTemplate.update("delete from track where id = ?", TRACK);
        jdbcTemplate.update("delete from station where id = ?", STATION);
        jdbcTemplate.update("delete from execution_package where id = ?", PACKAGE);
    }

    @Test
    @DisplayName("un aislador sin vía sale en la búsqueda por texto y en la lista ordenada por vía")
    void aisladorSinVia() {
        assertThat(aisladores(buscando("IT-OPT-INS"), Sort.unsorted()))
                .containsExactlyInAnyOrder(INSULATOR_ON_TRACK, INSULATOR_WITHOUT_TRACK);
        // En orden ascendente, el que no tiene vía va después de los que la tienen; en descendente, antes.
        assertThat(aisladores(buscando("IT-OPT-INS"), Sort.by("track.name")))
                .containsExactly(INSULATOR_ON_TRACK, INSULATOR_WITHOUT_TRACK);
        assertThat(aisladores(llamados("IT-OPT-INS"), Sort.by("track.name")))
                .containsExactly(INSULATOR_ON_TRACK, INSULATOR_WITHOUT_TRACK);
        assertThat(aisladores(llamados("IT-OPT-INS"), Sort.by(Sort.Direction.DESC, "track.name")))
                .containsExactly(INSULATOR_WITHOUT_TRACK, INSULATOR_ON_TRACK);
        assertThat(aisladores(llamados("IT-OPT-INS"), Sort.by("station.name", "name")))
                .containsExactly(INSULATOR_ON_TRACK, INSULATOR_WITHOUT_TRACK);
    }

    @Test
    @DisplayName("un aislador con dos agujas que casan sale una sola vez, también en el total")
    void unaVezPorAislador() {
        for (SectionInsulatorFilter filter : List.of(
                new SectionInsulatorFilter("IT-OPT-INS", null, null, "W3", null, null, null),
                new SectionInsulatorFilter("IT-OPT-INS", null, null, null, null, "W3", null))) {
            Page<SectionInsulatorDTO> page = sectionInsulatorService.getSectionInsulators(PageRequest.of(0, 50),
                    filter);

            assertThat(page.getContent()).extracting(SectionInsulatorDTO::getId).containsExactly(INSULATOR_ON_TRACK);
            assertThat(page.getTotalElements()).isOne();
        }
    }

    @Test
    @DisplayName("los filtros del aislador siguen filtrando, y el tipo y el estado solo si vienen")
    void filtrosDelAislador() {
        assertThat(aisladores(new SectionInsulatorFilter("IT-OPT-INS", null, "IT-OPT-TRACK", null, null, null, null),
                Sort.unsorted()))
                .containsExactly(INSULATOR_ON_TRACK);
        assertThat(aisladores(new SectionInsulatorFilter("IT-OPT-INS", null, null, null,
                SectionInsulatorInstallationType.IN_TRACK, null, null), Sort.unsorted()))
                .containsExactly(INSULATOR_ON_TRACK);
        assertThat(aisladores(new SectionInsulatorFilter("IT-OPT-INS", "IT-OPT-STAT", null, null, null, null, true),
                Sort.by("name")))
                .containsExactly(INSULATOR_ON_TRACK, INSULATOR_WITHOUT_TRACK);
        assertThat(aisladores(new SectionInsulatorFilter("IT-OPT-INS", null, null, null, null, null, false),
                Sort.unsorted()))
                .isEmpty();
    }

    @Test
    @DisplayName("un perfil sin tipo de poste o sin estado sale en la lista ordenada por esa columna")
    void perfilSinTipoDePoste() {
        ProfileFilter deLaVia = new ProfileFilter("IT-OPT-", null, TRACK, null, null, null, null, null, null,
                null, null, null, null, null);

        assertThat(perfiles(deLaVia, Sort.by("poleType.code")))
                .containsExactly(PROFILE_WITH_POLE_TYPE, PROFILE_WITHOUT_POLE_TYPE);
        assertThat(perfiles(deLaVia, Sort.by("profileStatus.code", "profileId")))
                .containsExactly(PROFILE_WITH_POLE_TYPE, PROFILE_WITHOUT_POLE_TYPE);
        // Filtrar por el tipo de poste sí deja fuera al que no lo tiene.
        assertThat(perfiles(new ProfileFilter(null, null, TRACK, null, null, null, null, null, "ITOPT", null, null,
                null, null, null), Sort.unsorted()))
                .containsExactly(PROFILE_WITH_POLE_TYPE);
    }

    private static SectionInsulatorFilter buscando(String text) {
        return new SectionInsulatorFilter(null, null, null, null, null, text, null);
    }

    private static SectionInsulatorFilter llamados(String name) {
        return new SectionInsulatorFilter(name, null, null, null, null, null, null);
    }

    private List<Long> aisladores(SectionInsulatorFilter filter, Sort sort) {
        return sectionInsulatorService.getSectionInsulators(PageRequest.of(0, 50, sort), filter).getContent()
                .stream()
                .map(SectionInsulatorDTO::getId)
                .toList();
    }

    private List<Long> perfiles(ProfileFilter filter, Sort sort) {
        return profileService.getProfiles(PageRequest.of(0, 50, sort), filter).getContent().stream()
                .map(ProfileDTO::getId)
                .toList();
    }
}
