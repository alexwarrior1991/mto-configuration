package com.alejandro.mtoconfiguration.core.outbox;

import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cableado real de Spring para la parte del outbox: aqui se ve lo que un test con
 * mocks sueltos no ve, como un {@code @PostConstruct} que impide arrancar o un
 * {@code @ConditionalOnProperty} mal puesto.
 */
class OutboxWiringTest {

    private ApplicationContextRunner runner(boolean publisherConfirms) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of())
                .withUserConfiguration(RabbitStubConfiguration.class, OutboxConfiguration.class)
                // El acceso a base de datos no entra aqui: lo que se comprueba es el cableado.
                .withBean(OutboxRelayService.class, () -> mock(OutboxRelayService.class))
                // En produccion lo aporta actuator; aqui basta un registro en memoria.
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .withBean(com.alejandro.mtoconfiguration.core.messaging.MessagePayloadSignature.class,
                        () -> new com.alejandro.mtoconfiguration.core.messaging.MessagePayloadSignature(
                                new com.alejandro.mtoconfiguration.core.messaging.MessageSignatureProperties()))
                .withBean(OutboxRabbitPublisher.class)
                .withBean(OutboxPublisherScheduler.class)
                .withPropertyValues("mto.test.publisher-confirms=" + publisherConfirms);
    }

    @Test
    void elRelayArrancaConPublisherConfirmsActivados() {
        runner(true).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(OutboxRabbitPublisher.class);
            assertThat(context).hasSingleBean(OutboxPublisherScheduler.class);
            assertThat(context).hasSingleBean(OutboxRetryPolicy.class);
            assertThat(context).hasSingleBean(OutboxMetrics.class);
            assertThat(context).hasSingleBean(OutboxTracing.class);
        });
    }

    @Test
    void elRelayNoArrancaSiFaltanLosPublisherConfirms() {
        // Degradar en silencio aqui significa volver a marcar PUBLISHED mensajes que
        // el broker no ha aceptado, que es justo el fallo que se estaba corrigiendo.
        runner(false).run(context -> assertThat(context)
                .getFailure()
                .hasMessageContaining("outboxRabbitPublisher")
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("publisher-confirm-type=correlated"));
    }

    @Test
    void sinTrazabilidadConfiguradaElOutboxUsaLaImplementacionVacia() {
        // Publicar eventos es el trabajo del outbox; trazarlos es un extra que no puede
        // condicionar su arranque.
        runner(true).run(context -> assertThat(context)
                .getBean(OutboxTracing.class)
                .isInstanceOf(NoOpOutboxTracing.class));
    }

    @Test
    void conTracerYPropagatorElOutboxPropagaLaTraza() {
        runner(true)
                .withBean(Tracer.class, () -> mock(Tracer.class))
                .withBean(Propagator.class, () -> mock(Propagator.class))
                .run(context -> assertThat(context)
                        .getBean(OutboxTracing.class)
                        .isInstanceOf(MicrometerOutboxTracing.class));
    }

    @Test
    void lasPropiedadesDelOutboxTraenValoresPorDefectoRazonables() {
        runner(true).run(context -> {
            OutboxProperties properties = context.getBean(OutboxProperties.class);

            assertThat(properties.getMaxAttempts()).isEqualTo(20);
            assertThat(properties.getInitialRetryDelay()).hasSeconds(5);
            assertThat(properties.getMaxRetryDelay()).hasMinutes(5);
            assertThat(properties.getBatchSize()).isEqualTo(50);
            assertThat(properties.getConfirmTimeout()).hasSeconds(10);
            assertThat(properties.getClaimVisibilityTimeout())
                    .as("la visibilidad debe superar con holgura la espera de confirmacion de todo un lote")
                    .isGreaterThan(properties.getConfirmTimeout());
        });
    }

    @Test
    void lasPropiedadesPorDefectoCumplenLoQueSeExigeAlArrancar() {
        runner(true).run(context -> {
            OutboxProperties properties = context.getBean(OutboxProperties.class);

            assertThat(properties.getPassTimeBudget()).hasSeconds(30);
            assertThat(properties.getClaimVisibilityTimeout())
                    .isGreaterThanOrEqualTo(properties.getConfirmTimeout().multipliedBy(2))
                    .isGreaterThanOrEqualTo(properties.getPassTimeBudget().plus(properties.getConfirmTimeout()));
        });
    }

    @Test
    void noArrancaSiUnaPublicacionNoCabeConHolguraEnLaVisibilidadDeSuReclamo() {
        // Con menos del doble, otra replica reclama el mensaje mientras el primero
        // sigue esperando su confirmacion, y sale duplicado.
        runner(true)
                .withPropertyValues(
                        "app.outbox.confirm-timeout=10s",
                        "app.outbox.claim-visibility-timeout=15s",
                        "app.outbox.pass-time-budget=1s")
                .run(context -> assertThat(context)
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("app.outbox.claim-visibility-timeout (PT15S)")
                        .hasMessageContaining("app.outbox.confirm-timeout (PT10S)"));
    }

    @Test
    void noArrancaSiLaPasadaPuedeDurarMasQueLaVisibilidad() {
        runner(true)
                .withPropertyValues(
                        "app.outbox.confirm-timeout=10s",
                        "app.outbox.claim-visibility-timeout=5m",
                        "app.outbox.pass-time-budget=295s")
                .run(context -> assertThat(context)
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("app.outbox.pass-time-budget (PT4M55S)")
                        .hasMessageContaining("app.outbox.claim-visibility-timeout (PT5M)"));
    }

    @Test
    void noArrancaConUnPlazoOUnTamanoQueNoSonPositivos() {
        runner(true)
                .withPropertyValues("app.outbox.max-attempts=0")
                .run(context -> assertThat(context).getFailure().rootCause()
                        .hasMessageContaining("app.outbox.max-attempts"));
        runner(true)
                .withPropertyValues("app.outbox.pass-time-budget=0s")
                .run(context -> assertThat(context).getFailure().rootCause()
                        .hasMessageContaining("app.outbox.pass-time-budget"));
    }

    @Configuration(proxyBeanMethods = false)
    static class RabbitStubConfiguration {

        @Bean
        @ConditionalOnMissingBean
        RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
            RabbitTemplate template = mock(RabbitTemplate.class);
            when(template.getConnectionFactory()).thenReturn(connectionFactory);
            return template;
        }

        @Bean
        ConnectionFactory connectionFactory(
                @org.springframework.beans.factory.annotation.Value("${mto.test.publisher-confirms}")
                boolean publisherConfirms) {
            CachingConnectionFactory connectionFactory = mock(CachingConnectionFactory.class);
            when(connectionFactory.isPublisherConfirms()).thenReturn(publisherConfirms);
            return connectionFactory;
        }
    }
}
