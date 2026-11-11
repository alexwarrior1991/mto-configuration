package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.masterdata.messaging.mapper.CantileverMasterDataPayloadMapper;
import com.alejandro.mtoconfiguration.masterdata.messaging.mapper.ProfileMasterDataPayloadMapper;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.CantileverDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SteadyArmDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.SteadyArmTypeDTO;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.CantileverRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.ProfileRepository;
import com.alejandro.mtoconfiguration.support.PostgresTestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El vinculo 1:1 entre una mensula y su brazo, de punta a punta: los servicios reales contra
 * PostgreSQL, con el esquema de las migraciones.
 *
 * <p>El vinculo es del brazo ({@code steady_arm.cantilever_id}) y el borrado es logico: un brazo
 * borrado conserva su mensula. V1 declaro esa columna {@code unique} sin mas, asi que la fila
 * borrada seguia ocupando la mensula y dar de alta otro brazo salia como 409 {@code BUS-002}. V30
 * hace parcial el indice ({@code WHERE deleted = false}), como V24 con el seccionador de un perfil.
 *
 * <p>Lo que queda entonces es una mensula con dos filas de brazo, una borrada y una viva. Leerla
 * tiene que dar la viva, nunca la borrada ni un fallo por encontrar dos: {@code Cantilever.steadyArm}
 * lleva {@code @SQLRestriction("deleted = false")}, y aqui se comprueba por los tres caminos que la
 * cargan (la carga perezosa del detalle y los dos {@code findByIdForMessaging} que la traen con un
 * join). El punto de partida se inserta con SQL, como en {@code DisconnectorLinkIT}.
 */
@SpringBootTest
@DisplayName("El vinculo entre una mensula y su brazo")
class SteadyArmLinkIT {

    private static final long ARM_TYPE = -71L;
    private static final long PACKAGE = -72L;
    private static final long TRACK = -73L;
    private static final long PROFILE = -74L;
    private static final long CANTILEVER = -75L;
    private static final long ARM = -76L;
    private static final long CANTILEVER_TYPE = -77L;
    private static final String ARM_TYPE_CODE = "IT-ARM";
    private static final String PROFILE_CODE = "IT-ARM-P1";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.registerProperties(registry);
        registry.add("app.lov.seed-on-startup", () -> "false");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private SteadyArmService steadyArmService;
    @Autowired
    private CantileverService cantileverService;
    @Autowired
    private CantileverRepository cantileverRepository;
    @Autowired
    private ProfileRepository profileRepository;
    @Autowired
    private CantileverMasterDataPayloadMapper cantileverPayloadMapper;
    @Autowired
    private ProfileMasterDataPayloadMapper profilePayloadMapper;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void inserta() {
        limpia();
        jdbcTemplate.update("""
                insert into steady_arm_type (id, code, description, enabled, create_date,
                        create_user, version_date, version_user, version_number)
                values (?, ?, 'Vinculo mensula-brazo', true, now(), 'test', now(), 'test', 1)
                """, ARM_TYPE, ARM_TYPE_CODE);
        jdbcTemplate.update("""
                insert into cantilever_type (id, code, description, enabled, create_date,
                        create_user, version_date, version_user, version_number)
                values (?, ?, 'Vinculo mensula-brazo', true, now(), 'test', now(), 'test', 1)
                """, CANTILEVER_TYPE, ARM_TYPE_CODE);
        jdbcTemplate.update("""
                insert into execution_package (id, name, enabled, deleted, initial_package,
                        length, start_date, end_date, create_date, create_user,
                        version_date, version_user, version_number)
                values (?, 'IT-ARM', true, false, false, 0, date '2020-01-01',
                        date '2020-12-31', now(), 'test', now(), 'test', 1)
                """, PACKAGE);
        jdbcTemplate.update("""
                insert into track (id, name, status, deleted, execution_package_id, create_date,
                        create_user, version_date, version_user, version_number)
                values (?, 'IT-ARM-TRACK', true, false, ?, now(), 'test', now(), 'test', 1)
                """, TRACK, PACKAGE);
        jdbcTemplate.update("""
                insert into profile (id, profile_id, kilometric_point, track_id, profile_status_id,
                        deleted, create_date, create_user, version_date, version_user, version_number)
                values (?, ?, 1.000, ?, (select id from profile_status where code = 'PROVISIONAL'),
                        false, now(), 'test', now(), 'test', 1)
                """, PROFILE, PROFILE_CODE, TRACK);
        jdbcTemplate.update("""
                insert into cantilever (id, cw_height, stagger, profile_id, cantilever_type_id, deleted,
                        create_date, create_user, version_date, version_user, version_number)
                values (?, 5.500, 200, ?, ?, false, now(), 'test', now(), 'test', 1)
                """, CANTILEVER, PROFILE, CANTILEVER_TYPE);
        jdbcTemplate.update("""
                insert into steady_arm (id, length, cantilever_id, steady_arm_type_id, deleted,
                        create_date, create_user, version_date, version_user, version_number)
                values (?, 1000, ?, ?, false, now(), 'test', now(), 'test', 1)
                """, ARM, CANTILEVER, ARM_TYPE);
    }

    @AfterEach
    void limpia() {
        jdbcTemplate.update("""
                delete from outbox_message
                 where (aggregate_type = 'steady-arm'
                        and aggregate_id in (select id::text from steady_arm where cantilever_id = ?))
                    or (aggregate_type = 'cantilever' and aggregate_id = ?)
                    or (aggregate_type = 'profile' and aggregate_id = ?)
                """, CANTILEVER, String.valueOf(CANTILEVER), String.valueOf(PROFILE));
        jdbcTemplate.update("delete from steady_arm_aud where cantilever_id = ?", CANTILEVER);
        jdbcTemplate.update("delete from steady_arm where cantilever_id = ?", CANTILEVER);
        jdbcTemplate.update("delete from cantilever_aud where id = ?", CANTILEVER);
        jdbcTemplate.update("delete from cantilever where id = ?", CANTILEVER);
        jdbcTemplate.update("delete from profile_aud where id = ?", PROFILE);
        jdbcTemplate.update("delete from profile where id = ?", PROFILE);
        jdbcTemplate.update("delete from track where id = ?", TRACK);
        jdbcTemplate.update("delete from execution_package where id = ?", PACKAGE);
        jdbcTemplate.update("delete from cantilever_type_aud where id = ?", CANTILEVER_TYPE);
        jdbcTemplate.update("delete from cantilever_type where id = ?", CANTILEVER_TYPE);
        jdbcTemplate.update("delete from steady_arm_type_aud where id = ?", ARM_TYPE);
        jdbcTemplate.update("delete from steady_arm_type where id = ?", ARM_TYPE);
    }

    private static SteadyArmDTO brazo(long length) {
        SteadyArmTypeDTO type = new SteadyArmTypeDTO();
        type.setCode(ARM_TYPE_CODE);
        SteadyArmDTO dto = new SteadyArmDTO();
        dto.setLength(length);
        dto.setSteadyArmType(type);
        dto.setCantileverId(CANTILEVER);
        return dto;
    }

    private List<Map<String, Object>> filas() {
        return jdbcTemplate.queryForList(
                "select id, length, deleted from steady_arm where cantilever_id = ? order by id", CANTILEVER);
    }

    @Test
    @DisplayName("un brazo borrado ya no ocupa su mensula: se da de alta otro, y la mensula enseña el vivo")
    void elBorradoNoOcupaLaMensula() {
        steadyArmService.delete(steadyArmService.getById(ARM));

        SteadyArmDTO nuevo = steadyArmService.create(brazo(1200L));

        assertThat(nuevo.getId()).isNotNull().isNotEqualTo(ARM);
        assertThat(jdbcTemplate.queryForMap("select deleted, cantilever_id from steady_arm where id = ?", ARM))
                .as("el borrado se queda en la tabla, con su mensula")
                .containsEntry("deleted", true).containsEntry("cantilever_id", CANTILEVER);
        assertThat(filas()).hasSize(2);
        assertThat(cantileverService.getById(CANTILEVER).getSteadyArm().getId()).isEqualTo(nuevo.getId());
    }

    @Test
    @DisplayName("tras borrar el brazo, modificar la mensula con otro dentro lo da de alta")
    void elBorradoNoImpideElAltaAnidada() {
        steadyArmService.delete(steadyArmService.getById(ARM));
        CantileverDTO mensula = cantileverService.getById(CANTILEVER);
        assertThat(mensula.getSteadyArm()).as("el borrado no se ve dentro de su mensula").isNull();
        SteadyArmDTO dentro = brazo(1300L);
        dentro.setCantileverId(null);
        mensula.setSteadyArm(dentro);

        CantileverDTO guardada = cantileverService.update(mensula);

        assertThat(guardada.getSteadyArm().getId()).isNotNull().isNotEqualTo(ARM);
        assertThat(filas()).extracting(fila -> fila.get("deleted")).containsExactlyInAnyOrder(true, false);
        assertThat(cantileverService.getById(CANTILEVER).getSteadyArm().getLength()).isEqualTo(1300L);
    }

    @Test
    @DisplayName("dos brazos vivos en la misma mensula siguen siendo un conflicto (409 BUS-002)")
    void dosVivosEnLaMismaMensula() {
        assertThatThrownBy(() -> steadyArmService.create(brazo(1200L)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(filas()).singleElement()
                .satisfies(fila -> assertThat(fila).containsEntry("id", ARM).containsEntry("deleted", false));
    }

    @Test
    @DisplayName("con uno borrado y otro vivo en la misma mensula, los eventos de la mensula y del perfil enseñan el vivo")
    @SuppressWarnings("unchecked")
    void losEventosEnsenanElVivo() {
        // Los dos findByIdForMessaging traen el brazo con un join, no con la carga perezosa del
        // detalle: son el camino que veria primero una fila borrada que se colase, o que fallaria
        // al encontrar dos filas para un 1:1.
        steadyArmService.delete(steadyArmService.getById(ARM));
        Long vivo = steadyArmService.create(brazo(1200L)).getId();

        Map<String, Object> mensula = new TransactionTemplate(transactionManager).execute(status ->
                cantileverPayloadMapper.toPayload(cantileverRepository.findByIdForMessaging(CANTILEVER).orElseThrow()));
        Map<String, Object> perfil = new TransactionTemplate(transactionManager).execute(status ->
                profilePayloadMapper.toPayload(profileRepository.findByIdForMessaging(PROFILE).orElseThrow()));

        assertThat((Map<String, Object>) mensula.get("steadyArm")).containsEntry("id", vivo);
        List<Map<String, Object>> mensulas = (List<Map<String, Object>>) perfil.get("cantilevers");
        assertThat(mensulas).singleElement()
                .satisfies(each -> assertThat(each).containsEntry("steadyArmId", vivo));
    }
}
