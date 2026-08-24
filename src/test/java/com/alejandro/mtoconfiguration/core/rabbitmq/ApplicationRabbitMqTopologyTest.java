package com.alejandro.mtoconfiguration.core.rabbitmq;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Valida la topologia REAL de application.yaml, no una de laboratorio.
 * <p>
 * Es la que se manda al broker en cada arranque. Un fallo aqui no es un test roto:
 * es un arranque que se cae en el entorno con PRECONDITION_FAILED, arrastrando la
 * declaracion de todo lo demas.
 */
class ApplicationRabbitMqTopologyTest {

    private RabbitMqProperties applicationProperties() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application.yaml", new ClassPathResource("application.yaml"));

        StandardEnvironment environment = new StandardEnvironment();
        sources.forEach(source -> environment.getPropertySources().addLast(source));

        return Binder.get(environment)
                .bind("app.rabbitmq", RabbitMqProperties.class)
                .orElseThrow(() -> new IllegalStateException("app.rabbitmq no esta en application.yaml"));
    }

    private Declarables declarables(RabbitMqProperties properties) {
        return new RabbitMqConfiguration(properties)
                .rabbitDeclarables(new RabbitMqTopologyValidator(properties));
    }

    @Test
    void laTopologiaDeLaAplicacionEsCoherente() throws IOException {
        RabbitMqProperties properties = applicationProperties();

        assertThatCode(() -> new RabbitMqTopologyValidator(properties).validate())
                .doesNotThrowAnyException();
    }

    @Test
    void seDeclaraElExchangeDeDatosMaestrosComoTopicDurable() throws IOException {
        List<Exchange> exchanges = declarables(applicationProperties()).getDeclarables().stream()
                .filter(Exchange.class::isInstance)
                .map(Exchange.class::cast)
                .filter(exchange -> !exchange.getName().endsWith(".dlx"))
                .toList();

        assertThat(exchanges).hasSize(1);
        assertThat(exchanges.getFirst().getName()).isEqualTo("mto.master-data.exchange");
        assertThat(exchanges.getFirst().getType()).isEqualTo("topic");
        assertThat(exchanges.getFirst().isDurable())
                .as("sin durable, la configuracion del exchange no sobrevive a un reinicio del broker")
                .isTrue();
    }

    @Test
    void todaColaDeclaradaTieneSuDeadLetter() throws IOException {
        Declarables declarables = declarables(applicationProperties());

        List<String> colas = declarables.getDeclarables().stream()
                .filter(Queue.class::isInstance)
                .map(Queue.class::cast)
                .map(Queue::getName)
                .toList();

        colas.stream()
                .filter(nombre -> !nombre.endsWith(".dlq"))
                .forEach(nombre -> assertThat(colas)
                        .as("la cola %s se queda sin dead letter: un mensaje que no se puede"
                                + " procesar bloquearia el resto", nombre)
                        .contains(nombre + ".dlq"));
    }

    @Test
    void todaColaDeclaradaEsDurableYNoSeAutoborra() throws IOException {
        List<Queue> colas = declarables(applicationProperties()).getDeclarables().stream()
                .filter(Queue.class::isInstance)
                .map(Queue.class::cast)
                .toList();

        assertThat(colas).isNotEmpty();
        assertThat(colas).allSatisfy(cola -> {
            assertThat(cola.isDurable()).as("%s no es durable", cola.getName()).isTrue();
            assertThat(cola.isAutoDelete()).as("%s se autoborra", cola.getName()).isFalse();
            assertThat(cola.isExclusive()).as("%s es exclusive", cola.getName()).isFalse();
        });
    }

    @Test
    void todoBindingApuntaAUnExchangeYAUnaColaQueSeDeclaranAqui() throws IOException {
        Declarables declarables = declarables(applicationProperties());

        List<String> colas = declarables.getDeclarables().stream()
                .filter(Queue.class::isInstance).map(Queue.class::cast).map(Queue::getName).toList();
        List<String> exchanges = declarables.getDeclarables().stream()
                .filter(Exchange.class::isInstance).map(Exchange.class::cast).map(Exchange::getName).toList();
        List<Binding> bindings = declarables.getDeclarables().stream()
                .filter(Binding.class::isInstance).map(Binding.class::cast).toList();

        assertThat(bindings).isNotEmpty();
        assertThat(bindings).allSatisfy(binding -> {
            assertThat(colas).contains(binding.getDestination());
            assertThat(exchanges).contains(binding.getExchange());
        });
    }

    @Test
    void ningunaColaDeclaradaSeQuedaSinBinding() throws IOException {
        Declarables declarables = declarables(applicationProperties());

        List<String> conBinding = declarables.getDeclarables().stream()
                .filter(Binding.class::isInstance)
                .map(Binding.class::cast)
                .map(Binding::getDestination)
                .toList();

        List<String> colas = declarables.getDeclarables().stream()
                .filter(Queue.class::isInstance)
                .map(Queue.class::cast)
                .map(Queue::getName)
                .toList();

        // Perder un binding no rompe nada de forma visible: la cola sigue existiendo,
        // el exchange sigue aceptando mensajes y simplemente nadie recibe ese evento.
        assertThat(colas).allSatisfy(cola -> assertThat(conBinding)
                .as("la cola %s se declara pero no esta bindeada a ningun exchange:"
                        + " no recibira ni un mensaje", cola)
                .contains(cola));
    }

    @Test
    void ningunaColaDeclaraArgumentosQueNoLeCorrespondenASuTipo() throws IOException {
        // Las colas de application.yaml son clasicas: mandarles x-queue-type o
        // x-delivery-limit las haria irredeclarables contra el broker que ya las tiene.
        List<Queue> colas = declarables(applicationProperties()).getDeclarables().stream()
                .filter(Queue.class::isInstance)
                .map(Queue.class::cast)
                .toList();

        assertThat(colas).allSatisfy(cola -> {
            Map<String, Object> argumentos = cola.getArguments();
            assertThat(argumentos).doesNotContainKey(RabbitMqConstants.ARG_QUEUE_TYPE);
            assertThat(argumentos).doesNotContainKey(RabbitMqConstants.ARG_DELIVERY_LIMIT);
        });
    }

    @Test
    void lasColasDeDatosMaestrosSiguenSiendoLasEsperadas() throws IOException {
        List<String> colas = declarables(applicationProperties()).getDeclarables().stream()
                .filter(Queue.class::isInstance)
                .map(Queue.class::cast)
                .map(Queue::getName)
                .filter(nombre -> !nombre.endsWith(".dlq"))
                .toList();

        // Cambiar esta lista significa cambiar el contrato con los servicios que las
        // consumen, asi que conviene que no se pueda hacer sin darse cuenta.
        assertThat(colas).containsExactlyInAnyOrder(
                "mto.master-data.events.queue",
                "mto.master-data.cache.queue",
                "mto.master-data.audit.queue",
                "mto.master-data.deleted.queue");
    }
}
