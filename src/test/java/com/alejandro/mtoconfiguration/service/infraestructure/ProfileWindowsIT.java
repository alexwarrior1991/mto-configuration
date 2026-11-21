package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.CantileverDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.DisconnectorDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ProfileDTO;
import com.alejandro.mtoconfiguration.service.infraestructure.asynchronous.ProfileAsyncService;
import com.alejandro.mtoconfiguration.support.PostgresTestDatabase;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las ventanas de perfiles de una via, {@code GET /profiles/track/{id}/keyset} y {@code /range} (y
 * sus gemelas {@code /async}), llamadas como las llama su controlador: sin transaccion alrededor.
 *
 * <p>{@code ProfileService.getProfilesByKeyset} y {@code getProfilesByKpRange} no eran
 * transaccionales, y mapear un perfil recorre lo perezoso (sus seccionadores desde V29, sus
 * anclajes, sus seccionamientos): con {@code open-in-view} a false eso es
 * {@code LazyInitializationException}, y las dos rutas respondian 500 {@code TEC-999} en cuanto la
 * via tenia un perfil. {@code ProfileControllerTest} simula el servicio y no lo veia; es el mismo
 * fallo que {@code FilterOutsideTransactionIT} fija para las listas.
 *
 * <p>Ademas, el brazo de una mensula es el lado inverso de un uno a uno, que Hibernate no puede
 * dejar perezoso: sin traerlo en el {@code fetch} de la ventana, cada mensula costaba su propio
 * {@code select} de {@code steady_arm}.
 *
 * <p>Aqui la via tiene 1.203 perfiles, tres ventanas de 500, con KP repetidos para que el desempate
 * por id cuente. Los datos van por SQL, como en {@code FilterOutsideTransactionIT}, y lo esperado se
 * calcula tambien por SQL, sin pasar por JPA.
 */
@SpringBootTest
@DisplayName("Ventanas de perfiles de una via, sin transaccion del llamante")
class ProfileWindowsIT {

    private static final long PACKAGE = -9101L;
    private static final long TRACK = -9102L;
    private static final long OTHER_TRACK = -9103L;
    private static final long STEADY_ARM_TYPE = -9104L;
    private static final long SECTIONING = -9105L;
    private static final long STATION = -9106L;
    private static final int PROFILES = 1203;
    private static final int WINDOW = 500;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.registerProperties(registry);
        registry.add("app.lov.seed-on-startup", () -> "false");
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ProfileService profileService;
    @Autowired
    private ProfileAsyncService profileAsyncService;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void inserta() {
        limpia();
        jdbcTemplate.update("""
                insert into execution_package (id, name, enabled, deleted, initial_package,
                        length, start_date, end_date, create_date, create_user,
                        version_date, version_user, version_number)
                values (?, 'IT-WINDOWS', true, false, false, 0, date '2020-01-01',
                        date '2020-12-31', now(), 'test', now(), 'test', 1)
                """, PACKAGE);
        for (long track : List.of(TRACK, OTHER_TRACK)) {
            jdbcTemplate.update("""
                    insert into track (id, name, status, deleted, execution_package_id, create_date,
                            create_user, version_date, version_user, version_number)
                    values (?, 'IT-WINDOWS-' || ?, true, false, ?, now(), 'test', now(), 'test', 1)
                    """, track, track, PACKAGE);
        }
        jdbcTemplate.update("""
                insert into steady_arm_type (id, code, description, enabled, create_date, create_user,
                        version_date, version_user, version_number)
                values (?, 'IT-WINDOWS-SAT', 'brazo', true, now(), 'test', now(), 'test', 1)
                """, STEADY_ARM_TYPE);
        jdbcTemplate.update("""
                insert into sectioning (id, code, description, enabled, create_date, create_user,
                        version_date, version_user, version_number)
                values (?, 'IT-WINDOWS-SEC', 'seccionamiento', true, now(), 'test', now(), 'test', 1)
                """, SECTIONING);
        // Tres perfiles por KP: sin el id de desempate el orden entre ventanas no seria estable.
        jdbcTemplate.update("""
                insert into profile (id, profile_id, kilometric_point, track_id, deleted, create_date,
                        create_user, version_date, version_user, version_number)
                select -1000000 - g, 'W-' || g, (g % 401) * 0.5, ?, false, now(), 'test', now(), 'test', 1
                from generate_series(1, ?) g
                """, TRACK, PROFILES);
        // Otra via, y un perfil borrado en la nuestra: ninguno de los dos sale.
        jdbcTemplate.update("""
                insert into profile (id, profile_id, kilometric_point, track_id, deleted, create_date,
                        create_user, version_date, version_user, version_number)
                values (-1900001, 'W-OTRA', 1.000, ?, false, now(), 'test', now(), 'test', 1),
                       (-1900002, 'W-BORRADO', 1.000, ?, true, now(), 'test', now(), 'test', 1)
                """, OTHER_TRACK, TRACK);
        // Una mensula en los pares y otra en los multiplos de cinco; una de cada cuarenta, borrada.
        jdbcTemplate.update("""
                insert into cantilever (id, profile_id, deleted, create_date, create_user,
                        version_date, version_user, version_number)
                select -2000000 - g, -1000000 - g, g % 40 = 0, now(), 'test', now(), 'test', 1
                from generate_series(1, ?) g where g % 2 = 0
                union all
                select -3000000 - g, -1000000 - g, false, now(), 'test', now(), 'test', 1
                from generate_series(1, ?) g where g % 5 = 0
                """, PROFILES, PROFILES);
        jdbcTemplate.update("""
                insert into steady_arm (id, cantilever_id, length, steady_arm_type_id, deleted, create_date,
                        create_user, version_date, version_user, version_number)
                select c.id - 5000000, c.id, 1200, ?, false, now(), 'test', now(), 'test', 1
                from cantilever c where c.id <= -2000000 and c.id > -4000000 and (-c.id) % 3 = 0
                """, STEADY_ARM_TYPE);
        jdbcTemplate.update("""
                insert into station (id, name, deleted, create_date, create_user,
                        version_date, version_user, version_number)
                values (?, 'IT-WINDOWS-STATION', false, now(), 'test', now(), 'test', 1)
                """, STATION);
        // Un seccionador en un poste de cada once; en uno de cada treinta y tres, dos (V29), con el
        // segundo delante por nombre; y uno borrado, que no sale.
        jdbcTemplate.update("""
                insert into disconnector (id, name, onload, profile_id, station_id, deleted, create_date,
                        create_user, version_date, version_user, version_number)
                select -4000000 - g, 'W-D-' || g, true, -1000000 - g, ?, g % 110 = 0, now(), 'test', now(), 'test', 1
                from generate_series(1, ?) g where g % 11 = 0
                union all
                select -6000000 - g, 'W-A-' || g, true, -1000000 - g, ?, false, now(), 'test', now(), 'test', 1
                from generate_series(1, ?) g where g % 33 = 0
                """, STATION, PROFILES, STATION, PROFILES);
        jdbcTemplate.update("""
                insert into profile_sectioning (profile_id, sectioning_id)
                select -1000000 - g, ? from generate_series(1, ?) g where g % 7 = 0
                """, SECTIONING, PROFILES);
    }

    @AfterEach
    void limpia() {
        jdbcTemplate.update("delete from profile_sectioning where sectioning_id = ?", SECTIONING);
        jdbcTemplate.update("delete from steady_arm where steady_arm_type_id = ?", STEADY_ARM_TYPE);
        jdbcTemplate.update("delete from disconnector where station_id = ?", STATION);
        jdbcTemplate.update("delete from station where id = ?", STATION);
        jdbcTemplate.update("delete from cantilever where profile_id in "
                + "(select id from profile where track_id in (?, ?))", TRACK, OTHER_TRACK);
        jdbcTemplate.update("delete from profile where track_id in (?, ?)", TRACK, OTHER_TRACK);
        jdbcTemplate.update("delete from track where id in (?, ?)", TRACK, OTHER_TRACK);
        jdbcTemplate.update("delete from sectioning where id = ?", SECTIONING);
        jdbcTemplate.update("delete from steady_arm_type where id = ?", STEADY_ARM_TYPE);
        jdbcTemplate.update("delete from execution_package where id = ?", PACKAGE);
    }

    @Test
    @DisplayName("las ventanas traen la via entera en orden, cada cursor lleva a la siguiente, con mensulas, brazos y seccionadores")
    void lasPaginasPorKeysetTraenLaViaEnteraConSusMensulasYSusBrazos() {
        List<List<ProfileDTO>> pages = keysetPages();

        assertThat(pages).extracting(List::size).containsExactly(WINDOW, WINDOW, PROFILES - 2 * WINDOW);

        List<ProfileDTO> todos = pages.stream().flatMap(List::stream).toList();
        assertThat(todos).extracting(ProfileDTO::getId).containsExactlyElementsOf(expectedProfileIds());

        assertChildren(todos);
        assertThat(todos).filteredOn(profile -> !profile.getSectionings().isEmpty())
                .hasSize(PROFILES / 7);
    }

    @Test
    @DisplayName("la ventana por la fachada /async es la misma")
    void laVentanaAsincronaEsLaMisma() {
        List<ProfileDTO> page = profileAsyncService.getProfilesByKeysetAsync(TRACK, null, null, 40).join();

        assertThat(page).extracting(ProfileDTO::getId).containsExactlyElementsOf(expectedProfileIds().subList(0, 40));
        assertThat(page).usingRecursiveComparison()
                .isEqualTo(profileService.getProfilesByKeyset(TRACK, null, null, 40));
    }

    @Test
    @DisplayName("el tramo por KP trae sus perfiles en orden, con sus mensulas, sus brazos y sus seccionadores")
    void elTramoPorKpTraeSusPerfilesConSusHijos() {
        BigDecimal desde = new BigDecimal("10.000");
        BigDecimal hasta = new BigDecimal("30.000");
        List<Long> esperados = jdbcTemplate.queryForList("select id from profile where track_id = ?"
                + " and deleted = false and kilometric_point between ? and ? order by kilometric_point, id",
                Long.class, TRACK, desde, hasta);

        List<ProfileDTO> tramo = profileService.getProfilesByKpRange(TRACK, desde, hasta);

        assertThat(esperados).hasSizeGreaterThan(100);
        assertThat(tramo).extracting(ProfileDTO::getId).containsExactlyElementsOf(esperados);
        assertChildren(tramo);
        assertThat(profileAsyncService.getProfilesByKpRangeAsync(TRACK, desde, hasta).join())
                .usingRecursiveComparison().isEqualTo(tramo);
    }

    @Test
    @DisplayName("cada perfil de una ventana sale con la forma de su detalle")
    void cadaPerfilDeUnaVentanaSaleComoSuDetalle() {
        List<ProfileDTO> page = profileService.getProfilesByKeyset(TRACK, null, null, 70);

        assertThat(page).filteredOn(profile -> !profile.getDisconnectors().isEmpty()).isNotEmpty();
        assertThat(page).filteredOn(profile -> profile.getCantilevers().stream()
                .anyMatch(cantilever -> cantilever.getSteadyArm() != null)).isNotEmpty();
        for (ProfileDTO profile : page) {
            assertThat(profile).as("perfil %s", profile.getProfileId())
                    .usingRecursiveComparison().isEqualTo(profileService.getById(profile.getId()));
        }
    }

    @Test
    @DisplayName("una ventana no pide un brazo por mensula: sus sentencias no crecen con sus mensulas")
    void unaVentanaNoPideUnBrazoPorMensula() {
        Statistics statistics = statistics();
        statistics.clear();

        List<ProfileDTO> page = profileService.getProfilesByKeyset(TRACK, null, null, 200);

        long mensulas = page.stream().mapToLong(profile -> profile.getCantilevers().size()).sum();
        long brazos = page.stream().flatMap(profile -> profile.getCantilevers().stream())
                .filter(cantilever -> cantilever.getSteadyArm() != null).count();
        assertThat(mensulas).isGreaterThan(100);
        assertThat(brazos).isGreaterThan(30);
        assertThat(statistics.getEntityStatistics(Profile.class.getName()).getLoadCount())
                .as("perfiles leidos: los de la ventana, no la via").isEqualTo(200);
        // La ventana (con sus mensulas y sus brazos), y las colecciones perezosas por lotes de 20:
        // medio centenar para 200 perfiles. Con un select de brazo por mensula pasaban de 150.
        assertThat(statistics.getPrepareStatementCount())
                .as("sentencias de una ventana de 200 perfiles con %s mensulas", mensulas)
                .isLessThan(mensulas / 2 + 10);
    }

    /** Las mensulas (por id), el brazo de cada una y los seccionadores (por nombre) de cada perfil. */
    private void assertChildren(List<ProfileDTO> todos) {
        Map<Long, List<Long>> mensulas = expectedCantilevers();
        Map<Long, Long> brazos = expectedSteadyArms();
        Map<Long, List<Long>> seccionadores = expectedDisconnectors();
        for (ProfileDTO profile : todos) {
            List<Long> esperadas = mensulas.getOrDefault(profile.getId(), List.of());
            assertThat(profile.getCantilevers()).as("mensulas de %s", profile.getProfileId())
                    .extracting(CantileverDTO::getId).containsExactlyElementsOf(esperadas);
            for (CantileverDTO cantilever : profile.getCantilevers()) {
                Long brazo = cantilever.getSteadyArm() == null ? null : cantilever.getSteadyArm().getId();
                assertThat(brazo).as("brazo de la mensula %s", cantilever.getId())
                        .isEqualTo(brazos.get(cantilever.getId()));
                if (brazo != null) {
                    assertThat(cantilever.getSteadyArm().getSteadyArmType().getCode())
                            .isEqualTo("IT-WINDOWS-SAT");
                }
            }
            assertThat(profile.getTrackId()).isEqualTo(TRACK);
            assertThat(profile.getDisconnectors()).as("seccionadores de %s", profile.getProfileId())
                    .extracting(DisconnectorDTO::getId)
                    .containsExactlyElementsOf(seccionadores.getOrDefault(profile.getId(), List.of()));
        }
    }

    private List<List<ProfileDTO>> keysetPages() {
        List<List<ProfileDTO>> pages = new ArrayList<>();
        List<ProfileDTO> page = profileService.getProfilesByKeyset(TRACK, null, null, WINDOW);
        while (!page.isEmpty()) {
            pages.add(page);
            ProfileDTO last = page.getLast();
            page = profileService.getProfilesByKeyset(TRACK, new BigDecimal(last.getKp()), last.getId(), WINDOW);
        }
        return pages;
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    private List<Long> expectedProfileIds() {
        return jdbcTemplate.queryForList("select id from profile where track_id = ? and deleted = false"
                + " order by kilometric_point, id", Long.class, TRACK);
    }

    private Map<Long, List<Long>> expectedCantilevers() {
        Map<Long, List<Long>> result = new LinkedHashMap<>();
        jdbcTemplate.query("select c.profile_id, c.id from cantilever c join profile p on p.id = c.profile_id"
                + " where p.track_id = ? and c.deleted = false order by c.id", rs -> {
            result.computeIfAbsent(rs.getLong(1), key -> new ArrayList<>()).add(rs.getLong(2));
        }, TRACK);
        return result;
    }

    private Map<Long, Long> expectedSteadyArms() {
        Map<Long, Long> result = new LinkedHashMap<>();
        jdbcTemplate.query("select cantilever_id, id from steady_arm where steady_arm_type_id = ?"
                + " and deleted = false", rs -> {
            result.put(rs.getLong(1), rs.getLong(2));
        }, STEADY_ARM_TYPE);
        return result;
    }

    private Map<Long, List<Long>> expectedDisconnectors() {
        Map<Long, List<Long>> result = new LinkedHashMap<>();
        jdbcTemplate.query("select profile_id, id from disconnector where station_id = ? and deleted = false"
                + " order by upper(name), id", rs -> {
            result.computeIfAbsent(rs.getLong(1), key -> new ArrayList<>()).add(rs.getLong(2));
        }, STATION);
        return result;
    }
}
