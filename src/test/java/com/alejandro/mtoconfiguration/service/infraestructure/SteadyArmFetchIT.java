package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.CantileverDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ProfileDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.TrackDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.CantileverFilter;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.ProfileFilter;
import com.alejandro.mtoconfiguration.support.PostgresTestDatabase;
import com.alejandro.mtoconfiguration.support.SqlStatementRecorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El brazo de una mensula no cuesta una sentencia por mensula.
 *
 * <p>{@code Cantilever.steadyArm} es el lado inverso de un {@code @OneToOne}: la clave ajena esta en
 * {@code steady_arm}, asi que Hibernate no puede saber si hay brazo sin ir a mirarlo, y no puede
 * dejarlo perezoso. Declarado {@code LAZY}, lo cargaba igual, pero aparte: un
 * {@code select … from steady_arm where cantilever_id=?} por cada mensula leida. Una pagina de
 * perfiles, el detalle de un perfil o de una via y la lista de mensulas pagaban una ida y vuelta por
 * mensula.
 *
 * <p>Aqui se cuenta esa sentencia, no el total: cuarenta perfiles con dos mensulas cada uno, y tres de
 * cada cuatro con brazo. Lo que sale tiene que ser lo de siempre.
 */
@SpringBootTest
@DisplayName("El brazo de una mensula se carga con ella")
class SteadyArmFetchIT {

    private static final long PACKAGE = -9201L;
    private static final long TRACK = -9202L;
    private static final long STEADY_ARM_TYPE = -9203L;
    private static final int PROFILES = 40;

    /** La carga suelta del brazo por la clave ajena de su mensula: la que no debe repetirse. */
    private static final Pattern ARM_BY_CANTILEVER = Pattern.compile(
            "from\\s+\\S*steady_arm\\s+\\w+\\s+where\\s+\\w+\\.cantilever_id\\s*=\\s*\\?",
            Pattern.CASE_INSENSITIVE);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.registerProperties(registry);
        registry.add("app.lov.seed-on-startup", () -> "false");
        registry.add("spring.jpa.properties.hibernate.session_factory.statement_inspector",
                SqlStatementRecorder.class::getName);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ProfileService profileService;
    @Autowired
    private TrackService trackService;
    @Autowired
    private CantileverService cantileverService;

    @BeforeEach
    void inserta() {
        limpia();
        jdbcTemplate.update("""
                insert into execution_package (id, name, enabled, deleted, initial_package,
                        length, start_date, end_date, create_date, create_user,
                        version_date, version_user, version_number)
                values (?, 'IT-ARM-FETCH', true, false, false, 0, date '2020-01-01',
                        date '2020-12-31', now(), 'test', now(), 'test', 1)
                """, PACKAGE);
        jdbcTemplate.update("""
                insert into track (id, name, status, deleted, execution_package_id, create_date,
                        create_user, version_date, version_user, version_number)
                values (?, 'IT-ARM-FETCH-TRACK', true, false, ?, now(), 'test', now(), 'test', 1)
                """, TRACK, PACKAGE);
        jdbcTemplate.update("""
                insert into steady_arm_type (id, code, description, enabled, create_date, create_user,
                        version_date, version_user, version_number)
                values (?, 'IT-ARM-FETCH-SAT', 'brazo', true, now(), 'test', now(), 'test', 1)
                """, STEADY_ARM_TYPE);
        jdbcTemplate.update("""
                insert into profile (id, profile_id, kilometric_point, track_id, deleted, create_date,
                        create_user, version_date, version_user, version_number)
                select -9210000 - g, 'ARM-' || g, g * 0.050, ?, false, now(), 'test', now(), 'test', 1
                from generate_series(1, ?) g
                """, TRACK, PROFILES);
        jdbcTemplate.update("""
                insert into cantilever (id, profile_id, deleted, create_date, create_user,
                        version_date, version_user, version_number)
                select -9220000 - g * 2 - s, -9210000 - g, false, now(), 'test', now(), 'test', 1
                from generate_series(1, ?) g, generate_series(0, 1) s
                """, PROFILES);
        // Tres de cada cuatro mensulas con brazo; y un brazo borrado en una que no tiene otro: no sale.
        jdbcTemplate.update("""
                insert into steady_arm (id, cantilever_id, length, steady_arm_type_id, deleted, create_date,
                        create_user, version_date, version_user, version_number)
                select c.id - 100000, c.id, 1200, ?, false, now(), 'test', now(), 'test', 1
                from cantilever c where c.profile_id between ? and ? and (-c.id) % 4 <> 0
                """, STEADY_ARM_TYPE, -9210000 - PROFILES, -9210001);
        jdbcTemplate.update("""
                insert into steady_arm (id, cantilever_id, length, steady_arm_type_id, deleted, create_date,
                        create_user, version_date, version_user, version_number)
                select c.id - 200000, c.id, 900, ?, true, now(), 'test', now(), 'test', 1
                from cantilever c where c.profile_id between ? and ? and (-c.id) % 4 = 0
                """, STEADY_ARM_TYPE, -9210000 - PROFILES, -9210001);
    }

    @AfterEach
    void limpia() {
        jdbcTemplate.update("delete from steady_arm where steady_arm_type_id = ?", STEADY_ARM_TYPE);
        jdbcTemplate.update("delete from cantilever where profile_id in (select id from profile where track_id = ?)",
                TRACK);
        jdbcTemplate.update("delete from profile where track_id = ?", TRACK);
        jdbcTemplate.update("delete from track where id = ?", TRACK);
        jdbcTemplate.update("delete from steady_arm_type where id = ?", STEADY_ARM_TYPE);
        jdbcTemplate.update("delete from execution_package where id = ?", PACKAGE);
    }

    @Test
    @DisplayName("una pagina de /profiles/filter trae los brazos sin una sentencia por mensula")
    void unaPaginaDePerfiles() {
        SqlStatementRecorder.clear();

        List<ProfileDTO> page = profileService.getProfiles(
                PageRequest.of(0, PROFILES, Sort.by("kp")),
                new ProfileFilter(null, null, TRACK, null, null, null, null, null, null, null, null, null,
                        null, null)).getContent();

        assertThat(page).hasSize(PROFILES);
        assertArms(page.stream().flatMap(profile -> profile.getCantilevers().stream()).toList(), 2 * PROFILES);
        assertNoSelectPerCantilever();
    }

    @Test
    @DisplayName("el detalle de un perfil y el de su via, tampoco")
    void elDetalleDeUnPerfilYElDeSuVia() {
        SqlStatementRecorder.clear();

        ProfileDTO profile = profileService.getById(-9210001L);
        TrackDTO track = trackService.getById(TRACK);

        assertArms(profile.getCantilevers(), 2);
        assertThat(track.getProfiles()).hasSize(PROFILES);
        assertArms(track.getProfiles().stream().flatMap(each -> each.getCantilevers().stream()).toList(),
                2 * PROFILES);
        assertNoSelectPerCantilever();
    }

    @Test
    @DisplayName("la lista de mensulas, la de un perfil y el detalle de una mensula, tampoco")
    void lasListasDeMensulas() {
        SqlStatementRecorder.clear();

        List<CantileverDTO> page = cantileverService.getCantilevers(PageRequest.of(0, 50, Sort.by("id")),
                new CantileverFilter(-9210002L, null, null, null, null, null, null, null)).getContent();
        List<CantileverDTO> deUnPerfil = cantileverService.getByProfileId(-9210001L);
        CantileverDTO una = cantileverService.getById(deUnPerfil.getFirst().getId());

        assertArms(page, 2);
        assertArms(deUnPerfil, 2);
        assertArms(List.of(una), 1);
        assertNoSelectPerCantilever();
    }

    /** Cada mensula sale con su brazo vivo, o sin brazo si no lo tiene o esta borrado. */
    private void assertArms(List<CantileverDTO> cantilevers, int expected) {
        assertThat(cantilevers).hasSize(expected);
        for (CantileverDTO cantilever : cantilevers) {
            Long arm = cantilever.getSteadyArm() == null ? null : cantilever.getSteadyArm().getId();
            Long esperado = (-cantilever.getId()) % 4 != 0 ? cantilever.getId() - 100000 : null;
            assertThat(arm).as("brazo de la mensula %s", cantilever.getId()).isEqualTo(esperado);
            if (arm != null) {
                assertThat(cantilever.getSteadyArm().getSteadyArmType().getCode()).isEqualTo("IT-ARM-FETCH-SAT");
            }
        }
    }

    private void assertNoSelectPerCantilever() {
        assertThat(SqlStatementRecorder.statements())
                .as("hay sentencias apuntadas: el inspector esta activo").isNotEmpty();
        assertThat(SqlStatementRecorder.count(sql -> ARM_BY_CANTILEVER.matcher(sql).find()))
                .as("select de steady_arm por la clave de su mensula").isZero();
    }
}
