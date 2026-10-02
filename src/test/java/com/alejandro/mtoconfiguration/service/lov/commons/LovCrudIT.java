package com.alejandro.mtoconfiguration.service.lov.commons;

import com.alejandro.mtoconfiguration.core.exception.ConcurrencyException;
import com.alejandro.mtoconfiguration.model.synchronous.lov.FoundationDTO;
import com.alejandro.mtoconfiguration.service.lov.FoundationService;
import com.alejandro.mtoconfiguration.service.lov.FoundationTypeService;
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
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Un catálogo como lo usa un cliente de la API, contra PostgreSQL real.
 *
 * <p>Un editor lee la fila, cambia lo que toca y la manda entera. Se prueba pasando el DTO por el
 * JSON, y no con el DTO de Java, porque ahí estaban los dos fallos. El {@code versionNumber} no salía
 * en el JSON, así que ningún cliente podía devolverlo y el bloqueo optimista no comprobaba nada. Y
 * los mappers de los tres catálogos con tipo padre copiaban del cuerpo el id, la versión y la
 * creación, que el JSON no lleva: la modificación acababa en un 500 y el alta tampoco entraba.</p>
 *
 * <p>El borrado es físico, y lo que lo impide es la clave ajena: eso solo se ve con la base de
 * verdad. El controlador lo dice como 409 {@code BUS-002} ({@code LovControllerContractTest}).</p>
 */
@SpringBootTest
@DisplayName("Un catalogo por la API, contra PostgreSQL")
class LovCrudIT {

    private static final long TYPE = -41L;
    private static final long FOUNDATION = -42L;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.registerProperties(registry);
        registry.add("app.lov.seed-on-startup", () -> "false");
    }

    /** Las anotaciones de Jackson del DTO son las de la API: con esto se ve lo que ve el cliente. */
    private final JsonMapper json = JsonMapper.builder().build();

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private FoundationService foundationService;
    @Autowired
    private FoundationTypeService foundationTypeService;

    @BeforeEach
    void inserta() {
        limpia();
        jdbcTemplate.update("""
                insert into foundation_type (id, code, description, enabled, create_date, create_user,
                        version_date, version_user, version_number)
                values (?, 'IT-RT-TIPO', 'Tipo', true, now(), 'creador', now(), 'creador', 1)
                """, TYPE);
        jdbcTemplate.update("""
                insert into foundation (id, code, description, enabled, drawing_number, foundation_type_id,
                        create_date, create_user, version_date, version_user, version_number)
                values (?, 'IT-RT', 'Zapata', true, 1234, ?, now(), 'creador', now(), 'creador', 1)
                """, FOUNDATION, TYPE);
    }

    @AfterEach
    void limpia() {
        jdbcTemplate.update("delete from foundation_aud where code like 'IT-RT%'");
        jdbcTemplate.update("delete from foundation where code like 'IT-RT%'");
        jdbcTemplate.update("delete from foundation_type_aud where id = ?", TYPE);
        jdbcTemplate.update("delete from foundation_type where id = ?", TYPE);
    }

    private FoundationDTO porLaApi(FoundationDTO dto) {
        return json.readValue(json.writeValueAsString(dto), FoundationDTO.class);
    }

    private Map<String, Object> fila(String code) {
        return jdbcTemplate.queryForMap("""
                select id, description, drawing_number, foundation_type_id, create_user, version_number
                from foundation where code = ?
                """, code);
    }

    @Test
    @DisplayName("una cimentacion vuelve con su version, se modifica sin perder su creacion, su plano ni su tipo, y la version vieja da conflicto")
    void modificacion() {
        FoundationDTO leida = porLaApi(foundationService.findById(FOUNDATION));
        assertThat(leida.getVersionNumber()).isEqualTo(1);
        assertThat(leida.getCreateUser()).as("la creacion no viaja").isNull();

        leida.setDescription("Modificada");
        FoundationDTO respuesta = porLaApi(foundationService.update(FOUNDATION, leida));

        assertThat(respuesta.getVersionNumber()).isEqualTo(2);
        assertThat(fila("IT-RT"))
                .containsEntry("description", "Modificada")
                .containsEntry("drawing_number", 1234L)
                .containsEntry("foundation_type_id", TYPE)
                .containsEntry("create_user", "creador")
                .containsEntry("version_number", 2);

        leida.setDescription("Tarde");
        assertThatThrownBy(() -> foundationService.update(FOUNDATION, leida))
                .isInstanceOf(ConcurrencyException.class);
        assertThat(fila("IT-RT")).containsEntry("description", "Modificada");
    }

    @Test
    @DisplayName("el alta de una cimentacion con su tipo entra con su id y la version 1, aunque el cuerpo traiga otros")
    void alta() {
        FoundationDTO nueva = json.readValue("""
                {"id": %d, "code": "IT-RT-NUEVA", "description": "Pilote", "enabled": true,
                 "versionNumber": 9, "drawingNumber": 55, "foundationType": {"id": %d}}
                """.formatted(FOUNDATION, TYPE), FoundationDTO.class);

        FoundationDTO creada = foundationService.create(nueva);

        assertThat(creada.getId()).isNotEqualTo(FOUNDATION);
        assertThat(creada.getVersionNumber()).isEqualTo(1);
        assertThat(fila("IT-RT-NUEVA"))
                .containsEntry("id", creada.getId())
                .containsEntry("drawing_number", 55L)
                .containsEntry("foundation_type_id", TYPE)
                .containsEntry("version_number", 1);
        assertThat(fila("IT-RT"))
                .as("el id del cuerpo no ha pisado la fila que ya lo tenia")
                .containsEntry("description", "Zapata");
    }

    @Test
    @DisplayName("borrar una entrada que nadie usa borra la fila: no es un borrado logico")
    void borrado() {
        foundationService.delete(FOUNDATION);

        assertThat(jdbcTemplate.queryForObject("select count(*) from foundation where id = ?", Integer.class,
                FOUNDATION)).isZero();
    }

    @Test
    @DisplayName("un tipo que usa una cimentacion no se borra: la clave ajena lo impide y llega como integridad")
    void borradoEnUso() {
        assertThatThrownBy(() -> foundationTypeService.delete(TYPE))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbcTemplate.queryForObject("select count(*) from foundation_type where id = ?", Integer.class,
                TYPE)).isEqualTo(1);
    }
}
