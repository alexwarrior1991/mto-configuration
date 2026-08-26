package com.alejandro.mtoconfiguration.core.rabbitmq;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.DirectFieldAccessor;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * La factory de listeners tiene que aplicar {@code spring.rabbitmq.listener.simple.*}.
 * <p>
 * Declarar este bean a mano SUSTITUYE al que crea Spring Boot. Antes se construia sin
 * pasar por el configurer, de modo que el bloque de reintentos y el acknowledge-mode
 * estaban escritos en application.yaml sin que nadie los leyera: un consumidor que
 * fallara no habria reintentado nunca. Es un fallo que no se ve, porque la
 * configuracion esta ahi puesta y parece activa.
 */
class RabbitListenerFactoryTest {

    private SimpleRabbitListenerContainerFactory factory(RabbitProperties rabbitProperties) {
        RabbitMqConfiguration configuration = new RabbitMqConfiguration(new RabbitMqProperties());
        ConnectionFactory connectionFactory = mock(CachingConnectionFactory.class);
        MessageConverter converter = new JacksonJsonMessageConverter();

        return configuration.rabbitListenerContainerFactory(
                new SimpleRabbitListenerContainerFactoryConfigurer(rabbitProperties),
                connectionFactory,
                converter);
    }

    private SimpleMessageListenerContainer container(RabbitProperties rabbitProperties) {
        return factory(rabbitProperties).createListenerContainer();
    }

    @Test
    void laConcurrenciaYElPrefetchDelYamlLleganAlContenedor() {
        RabbitProperties properties = new RabbitProperties();
        properties.getListener().getSimple().setConcurrency(3);
        properties.getListener().getSimple().setMaxConcurrency(9);
        properties.getListener().getSimple().setPrefetch(25);

        DirectFieldAccessor contenedor = new DirectFieldAccessor(container(properties));

        assertThat(contenedor.getPropertyValue("concurrentConsumers")).isEqualTo(3);
        assertThat(contenedor.getPropertyValue("maxConcurrentConsumers")).isEqualTo(9);
        assertThat(contenedor.getPropertyValue("prefetchCount")).isEqualTo(25);
    }

    @Test
    void unMensajeRechazadoNoSeReencolaSiElYamlLoDice() {
        RabbitProperties properties = new RabbitProperties();
        properties.getListener().getSimple().setDefaultRequeueRejected(false);

        // Reencolar sin fin un mensaje que no se puede procesar es como se bloquea una
        // cola entera: tiene que irse a su DLQ.
        assertThat(new DirectFieldAccessor(container(properties)).getPropertyValue("defaultRequeueRejected"))
                .isEqualTo(false);
    }

    @Test
    void elBloqueDeReintentosDelYamlNoSeIgnora() {
        RabbitProperties properties = new RabbitProperties();
        properties.getListener().getSimple().getRetry().setEnabled(true);
        properties.getListener().getSimple().getRetry().setMaxRetries(3);
        properties.getListener().getSimple().getRetry().setInitialInterval(Duration.ofMillis(1000));

        // Con retry activo el configurer instala una cadena de advices en el
        // contenedor. Sin pasar por el configurer no habria ninguna, y los reintentos
        // configurados en el YAML no existirian.
        Object advices = new DirectFieldAccessor(container(properties)).getPropertyValue("adviceChain");

        assertThat((Object[]) advices)
                .as("sin el configurer, el retry del YAML se pierde en silencio")
                .isNotEmpty();
    }

    @Test
    void elConversorPropioDelServicioSeSigueAplicando() {
        // El configurer va primero, pero lo que es de este servicio manda despues.
        assertThat(new DirectFieldAccessor(factory(new RabbitProperties()))
                .getPropertyValue("messageConverter"))
                .isInstanceOf(JacksonJsonMessageConverter.class);
    }
}
