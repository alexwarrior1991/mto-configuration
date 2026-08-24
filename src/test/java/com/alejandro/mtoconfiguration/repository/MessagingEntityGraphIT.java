package com.alejandro.mtoconfiguration.repository;

import com.alejandro.mtoconfiguration.entity.commons.IEntity;
import com.alejandro.mtoconfiguration.repository.jpa.commons.MessagingEntityGraphRepository;
import com.alejandro.mtoconfiguration.support.PostgresTestDatabase;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Ejecuta el {@code findByIdForMessaging} de TODOS los repositorios de mensajeria.
 * <p>
 * Es la consulta que dispara cada evento de datos maestros
 * ({@code MasterDataEntityChangedEventListener}), y su {@code @EntityGraph} se declara
 * con rutas en texto: un nombre de atributo mal escrito o una relacion renombrada no
 * lo detecta el compilador. Sin este test, el fallo aparece al guardar la entidad en
 * produccion, dentro de la transaccion de negocio.
 * <p>
 * Las tablas estan vacias a proposito: lo que se valida es que la ruta del grafo
 * resuelve y que el SQL se ejecuta contra PostgreSQL, no el contenido.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MessagingEntityGraphIT {

    @Autowired
    private ApplicationContext applicationContext;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.registerProperties(registry);
    }

    @TestFactory
    Stream<DynamicTest> todosLosGrafosDeMensajeriaResuelvenYSeEjecutan() {
        Map<String, MessagingEntityGraphRepository> repositories =
                applicationContext.getBeansOfType(MessagingEntityGraphRepository.class);

        assertThat(repositories)
                .as("no se ha encontrado ningun repositorio de mensajeria: el test no estaria probando nada")
                .isNotEmpty();

        return repositories.entrySet().stream()
                .map(entry -> DynamicTest.dynamicTest(entry.getKey(), () ->
                        assertThatCode(() -> {
                            @SuppressWarnings("unchecked")
                            MessagingEntityGraphRepository<IEntity> repository = entry.getValue();
                            repository.findByIdForMessaging(1L);
                        }).doesNotThrowAnyException()));
    }

    @TestFactory
    Stream<DynamicTest> ningunRepositorioDeMensajeriaSeQuedaSinRegistrar() {
        Collection<String> encontrados =
                applicationContext.getBeansOfType(MessagingEntityGraphRepository.class).keySet();

        return Stream.of(
                        "cantileverRepository", "disconnectorRepository", "executionPackageRepository",
                        "profileRepository", "sectionInsulatorRepository", "stationRepository",
                        "steadyArmRepository", "trackRepository")
                .map(nombre -> DynamicTest.dynamicTest(nombre, () ->
                        assertThat(encontrados).contains(nombre)));
    }
}
