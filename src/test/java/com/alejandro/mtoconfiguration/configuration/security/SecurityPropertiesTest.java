package com.alejandro.mtoconfiguration.configuration.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La configuración de seguridad tiene dos valores cuyo error no se nota funcionando: una audiencia
 * vacía deja pasar cualquier token del realm, y un comodín en los orígenes de CORS revienta cada
 * preflight en tiempo de ejecución. Los dos deben impedir el arranque, que es lo que se fija aquí.
 */
class SecurityPropertiesTest {

    private static final String[] CONFIGURACION_MINIMA = {
            "app.security.client-id=mto-configuration-api",
            "app.security.principal-claim=preferred_username",
            "app.security.cors.allowed-origins=http://localhost:4200",
            "app.security.cors.allowed-methods=GET",
            "app.security.cors.allowed-headers=Authorization"
    };

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(PropiedadesDeSeguridad.class)
            .withPropertyValues(CONFIGURACION_MINIMA);

    @Test
    @DisplayName("con audiencia configurada la aplicación arranca")
    void conAudienciaConfiguradaArranca() {
        contextRunner
                .withPropertyValues(
                        "app.security.audience-validation-enabled=true",
                        "app.security.required-audience=mto-configuration-api")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(SecurityProperties.class).requiredAudience())
                            .isEqualTo("mto-configuration-api");
                });
    }

    @Test
    @DisplayName("la validación de audiencia activa sin audiencia impide el arranque")
    void validacionDeAudienciaSinAudienciaImpideElArranque() {
        contextRunner
                .withPropertyValues(
                        "app.security.audience-validation-enabled=true",
                        "app.security.required-audience=")
                // El mensaje viaja en la causa raíz: Boot envuelve el fallo de binding en una
                // ConfigurationPropertiesBindException cuyo texto solo nombra el prefijo.
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("app.security.required-audience es obligatorio"));
    }

    @Test
    @DisplayName("sin validación de audiencia la audiencia vacía es admisible")
    void sinValidacionDeAudienciaLaAudienciaVaciaEsAdmisible() {
        contextRunner
                .withPropertyValues("app.security.audience-validation-enabled=false")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("el comodín en los orígenes de CORS impide el arranque")
    void elComodinEnLosOrigenesDeCorsImpideElArranque() {
        contextRunner
                .withPropertyValues(
                        "app.security.audience-validation-enabled=false",
                        "app.security.cors.allowed-origins=*")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("app.security.cors.allowed-origins no admite el comodín"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SecurityProperties.class)
    static class PropiedadesDeSeguridad {
    }
}
