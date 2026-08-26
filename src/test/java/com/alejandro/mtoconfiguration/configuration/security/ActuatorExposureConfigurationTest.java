package com.alejandro.mtoconfiguration.configuration.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.endpoint.Show;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.health.autoconfigure.actuate.endpoint.HealthEndpointProperties;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los endpoints de Actuator se publican a propósito —hacen falta para explotar el servicio— y la
 * cadena de filtros los protege con el rol de operación. Lo que no puede publicarse es el desglose
 * del <i>health</i>: enumera la base de datos, Redis y RabbitMQ con sus mensajes de error, mientras
 * que el estado escueto sí tiene que seguir abierto porque lo consultan el balanceador y las sondas
 * del orquestador.
 *
 * <p>Se enlazan las propiedades del {@code application.yaml} real en vez de comprobar el texto,
 * porque el fallo típico de esta configuración es una property mal escrita: no ata nada y no avisa
 * de nada. Aquí el defecto de Boot es {@code NEVER}, así que un nombre equivocado no abriría el
 * desglose —lo cerraría también para explotación, que se quedaría sin diagnóstico justo cuando lo
 * necesita—. En cualquiera de los dos sentidos, el valor efectivo dejaría de ser el que se cree
 * haber configurado, y eso es lo que este test impide.</p>
 */
class ActuatorExposureConfigurationTest {

    private final Binder binder = configuracionDesplegada();

    @Test
    @DisplayName("el desglose del health solo se muestra a quien está autorizado")
    void elDesgloseDelHealthExigeAutorizacion() {
        HealthEndpointProperties health = health();

        assertThat(health.getShowDetails()).isEqualTo(Show.WHEN_AUTHORIZED);
        assertThat(health.getShowComponents()).isEqualTo(Show.WHEN_AUTHORIZED);
    }

    @Test
    @DisplayName("el rol que abre el desglose es el mismo que protege el resto de Actuator")
    void elRolEsElMismoQueProtegeActuator() {
        assertThat(health().getRoles()).containsExactly(SecurityRoles.OPS_METRICS);
    }

    @Test
    @DisplayName("solo se exponen los endpoints previstos")
    void soloSeExponenLosEndpointsPrevistos() {
        List<String> expuestos = binder
                .bind("management.endpoints.web.exposure.include", String[].class)
                .map(List::of)
                .orElse(List.of());

        assertThat(expuestos).containsExactlyInAnyOrder("health", "info", "outbox", "prometheus");
    }

    private HealthEndpointProperties health() {
        return binder.bind("management.endpoint.health", HealthEndpointProperties.class)
                .orElseThrow(() -> new AssertionError(
                        "No hay configuración bajo management.endpoint.health en application.yaml"));
    }

    private static Binder configuracionDesplegada() {
        StandardEnvironment environment = new StandardEnvironment();

        try {
            List<PropertySource<?>> fuentes = new YamlPropertySourceLoader()
                    .load("application.yaml", new ClassPathResource("application.yaml"));

            fuentes.forEach(environment.getPropertySources()::addFirst);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer application.yaml", e);
        }

        return Binder.get(environment);
    }
}
