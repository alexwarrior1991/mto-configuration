package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.support.PostgresTestDatabase;
import com.alejandro.mtoconfiguration.support.SqlStatementRecorder;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Los casos de {@link AbstractDeleteWithDependentsIT}, con las tablas en {@code public}. */
@SpringBootTest
@DisplayName("Borrar con cosas colgando, y borrar una via entera")
class DeleteWithDependentsIT extends AbstractDeleteWithDependentsIT {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.registerProperties(registry);
        registry.add("app.lov.seed-on-startup", () -> "false");
        registry.add("spring.jpa.properties.hibernate.session_factory.statement_inspector",
                SqlStatementRecorder.class::getName);
    }
}
