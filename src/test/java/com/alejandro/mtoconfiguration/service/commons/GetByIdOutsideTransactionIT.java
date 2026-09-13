package com.alejandro.mtoconfiguration.service.commons;

import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ExecutionPackageDTO;
import com.alejandro.mtoconfiguration.service.infraestructure.ExecutionPackageService;
import com.alejandro.mtoconfiguration.support.PostgresTestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code BaseService.getById} llamado desde fuera de una transaccion.
 *
 * <p>Es el caso de TODOS sus llamantes reales: {@code ReadController} no abre transaccion,
 * {@code open-in-view} esta a false, y {@code getById} lee la entidad con
 * {@code getReferenceById}, que devuelve un <b>proxy</b>. El mapeo posterior lo inicializa, y
 * sin sesion eso es {@code LazyInitializationException}.
 *
 * <p>No es un caso teorico: asi fallaron, una por una, las 11.714 modificaciones de la segunda
 * pasada del importador del maestro. El test se hace con un {@code insert} directo a proposito
 * —dar de alta por el servicio abriria una transaccion y taparia justo lo que se quiere
 * probar— y comprueba que el DTO trae un campo <b>que solo esta en la fila</b>: leer el id de
 * un proxy no lo inicializa, asi que afirmar sobre el id no probaria nada.
 */
@SpringBootTest
@DisplayName("Leer por id sin transaccion del llamante")
class GetByIdOutsideTransactionIT {

    private static final long ID = -7L;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.registerProperties(registry);
        registry.add("app.lov.seed-on-startup", () -> "false");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ExecutionPackageService service;

    @AfterEach
    void limpia() {
        jdbcTemplate.update("delete from execution_package where id = ?", ID);
    }

    @Test
    @DisplayName("el proxy se inicializa dentro del servicio, no en quien lo llama")
    void leePorIdSinTransaccionDelLlamante() {
        jdbcTemplate.update("""
                insert into execution_package (id, name, enabled, deleted, initial_package,
                        length, start_date, end_date, create_date, create_user,
                        version_date, version_user, version_number)
                values (?, 'IT-GETBYID', true, false, false, 0, date '2020-01-01',
                        date '2020-12-31', now(), 'test', now(), 'test', 1)
                """, ID);

        ExecutionPackageDTO dto = service.getById(ID);

        assertThat(dto.getName()).isEqualTo("IT-GETBYID");
    }
}
