package com.alejandro.mtoconfiguration.core.rabbitmq;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Construccion de la topologia que se manda al broker.
 * <p>
 * Se prueba sin RabbitMQ porque lo que decide si una declaracion es aceptada o
 * rechazada son los ARGUMENTOS, y el broker no perdona: redeclarar una cola con un
 * argumento distinto del que tiene responde PRECONDITION_FAILED y tumba el bloque
 * entero de declaraciones, incluidos los exchanges correctos.
 */
class RabbitMqTopologyTest {

    private RabbitMqProperties.Queue queue(String name) {
        RabbitMqProperties.Queue queue = new RabbitMqProperties.Queue();
        queue.setName(name);
        return queue;
    }

    private RabbitMqProperties.Binding binding(String queue, String exchange, String routingKey) {
        RabbitMqProperties.Binding binding = new RabbitMqProperties.Binding();
        binding.setQueue(queue);
        binding.setExchange(exchange);
        binding.setRoutingKey(routingKey);
        return binding;
    }

    private Declarables build(RabbitMqProperties properties) {
        RabbitMqConfiguration configuration = new RabbitMqConfiguration(properties);
        return configuration.rabbitDeclarables(new RabbitMqTopologyValidator(properties));
    }

    private Optional<Queue> findQueue(Declarables declarables, String name) {
        return declarables.getDeclarables().stream()
                .filter(Queue.class::isInstance)
                .map(Queue.class::cast)
                .filter(q -> q.getName().equals(name))
                .findFirst();
    }

    private List<String> queueNames(Declarables declarables) {
        return declarables.getDeclarables().stream()
                .filter(Queue.class::isInstance)
                .map(Queue.class::cast)
                .map(Queue::getName)
                .toList();
    }

    private List<String> boundQueues(Declarables declarables) {
        return declarables.getDeclarables().stream()
                .filter(Binding.class::isInstance)
                .map(Binding.class::cast)
                .map(Binding::getDestination)
                .toList();
    }

    // ------------------------------------------------------------------ ownership

    @Test
    void unaColaDeOtroServicioNoSeDeclaraNiSeBindeaNiSeLeCreaDeadLetter() {
        RabbitMqProperties properties = new RabbitMqProperties();

        RabbitMqProperties.Queue propia = queue("mto.propia.queue");
        RabbitMqProperties.Queue ajena = queue("mto.ajena.queue");
        ajena.setDeclare(false);

        properties.setQueues(List.of(propia, ajena));
        properties.setBindings(List.of(binding("mto.propia.queue", "mto.exchange", "#")));

        Declarables declarables = build(properties);

        assertThat(queueNames(declarables))
                .as("de la cola ajena no se declara ni ella ni su dlq: es de quien la consume")
                .contains("mto.propia.queue", "mto.propia.queue.dlq")
                .doesNotContain("mto.ajena.queue", "mto.ajena.queue.dlq");
    }

    @Test
    void elBindingDeUnaColaAjenaTampocoSeMantieneAqui() {
        RabbitMqProperties properties = new RabbitMqProperties();

        RabbitMqProperties.Queue ajena = queue("mto.ajena.queue");
        ajena.setDeclare(false);
        ajena.setDeadLetterEnabled(false);

        properties.setQueues(List.of(ajena));
        // El binding se deja en la configuracion pero la cola es de otro servicio:
        // el validador lo rechaza antes de intentar declararlo contra el broker.
        properties.setBindings(List.of(binding("mto.ajena.queue", "mto.exchange", "#")));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> build(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("declare=false")
                .hasMessageContaining("ese servicio el que debe crear su binding");
    }

    @Test
    void conDeclareQueuesGlobalDesactivadoSoloSeDeclaranLosExchanges() {
        RabbitMqProperties properties = new RabbitMqProperties();
        properties.getDefaults().setDeclareQueues(false);

        RabbitMqProperties.Exchange exchange = new RabbitMqProperties.Exchange();
        exchange.setName("mto.master-data.exchange");
        properties.setExchanges(List.of(exchange));
        properties.setQueues(List.of(queue("mto.consumidor.queue")));

        Declarables declarables = build(properties);

        assertThat(queueNames(declarables)).isEmpty();
        assertThat(boundQueues(declarables)).isEmpty();
        assertThat(declarables.getDeclarables())
                .as("el exchange si es de este servicio y se sigue declarando")
                .hasSize(1);
    }

    // ------------------------------------------------------------------ quorum

    @Test
    void unaColaQuorumViajaConSuTipoYSuLimiteDeEntregas() {
        RabbitMqProperties properties = new RabbitMqProperties();

        RabbitMqProperties.Queue quorum = queue("mto.quorum.queue");
        quorum.setType(RabbitMqProperties.QueueType.QUORUM);
        quorum.setDeliveryLimit(5);
        properties.setQueues(List.of(quorum));

        Map<String, Object> argumentos = findQueue(build(properties), "mto.quorum.queue")
                .orElseThrow()
                .getArguments();

        assertThat(argumentos)
                .containsEntry(RabbitMqConstants.ARG_QUEUE_TYPE, "quorum")
                .containsEntry(RabbitMqConstants.ARG_DELIVERY_LIMIT, 5);
    }

    @Test
    void laDeadLetterHeredaElTipoDeSuColaPrincipal() {
        RabbitMqProperties properties = new RabbitMqProperties();

        RabbitMqProperties.Queue quorum = queue("mto.quorum.queue");
        quorum.setType(RabbitMqProperties.QueueType.QUORUM);
        properties.setQueues(List.of(quorum));

        Map<String, Object> argumentos = findQueue(build(properties), "mto.quorum.queue.dlq")
                .orElseThrow()
                .getArguments();

        assertThat(argumentos)
                .as("de nada sirve replicar la cola de trabajo si los mensajes que fallan"
                        + " acaban en una cola sin replica que se pierde con su nodo")
                .containsEntry(RabbitMqConstants.ARG_QUEUE_TYPE, "quorum");
    }

    @Test
    void unaColaClasicaNoDeclaraSuTipoDeFormaExplicita() {
        RabbitMqProperties properties = new RabbitMqProperties();
        properties.setQueues(List.of(queue("mto.clasica.queue")));

        Map<String, Object> argumentos = findQueue(build(properties), "mto.clasica.queue")
                .orElseThrow()
                .getArguments();

        // Una cola creada sin x-queue-type y redeclarada CON el es, para el broker, una
        // redeclaracion distinta: PRECONDITION_FAILED y adios topologia.
        assertThat(argumentos).doesNotContainKey(RabbitMqConstants.ARG_QUEUE_TYPE);
    }

    // ------------------------------------------------------------------ limites

    @Test
    void losLimitesYLaEstrategiaDeDesbordamientoLleganAlBroker() {
        RabbitMqProperties properties = new RabbitMqProperties();

        RabbitMqProperties.Queue acotada = queue("mto.acotada.queue");
        acotada.setMaxLength(10_000L);
        acotada.setMaxLengthBytes(50_000_000L);
        acotada.setMessageTtl(3_600_000L);
        acotada.setOverflow(RabbitMqProperties.Overflow.REJECT_PUBLISH);
        properties.setQueues(List.of(acotada));

        Map<String, Object> argumentos = findQueue(build(properties), "mto.acotada.queue")
                .orElseThrow()
                .getArguments();

        assertThat(argumentos)
                .containsEntry(RabbitMqConstants.ARG_MAX_LENGTH, 10_000L)
                .containsEntry(RabbitMqConstants.ARG_MAX_LENGTH_BYTES, 50_000_000L)
                .containsEntry(RabbitMqConstants.ARG_MESSAGE_TTL, 3_600_000L)
                // Por defecto RabbitMQ descarta los mensajes MAS ANTIGUOS en silencio,
                // que para eventos de datos maestros es perdida de datos. Con
                // reject-publish el publicador recibe un nack y el outbox reintenta.
                .containsEntry(RabbitMqConstants.ARG_OVERFLOW, "reject-publish");
    }

    // ------------------------------------------------------------------ dead letter

    @Test
    void cadaColaPropiaObtieneSuDlxSuDlqYSuBinding() {
        RabbitMqProperties properties = new RabbitMqProperties();
        properties.setQueues(List.of(queue("mto.eventos.queue")));

        Declarables declarables = build(properties);

        Map<String, Object> argumentos = findQueue(declarables, "mto.eventos.queue")
                .orElseThrow()
                .getArguments();

        assertThat(argumentos)
                .containsEntry(RabbitMqConstants.ARG_DEAD_LETTER_EXCHANGE, "mto.eventos.queue.dlx")
                .containsEntry(RabbitMqConstants.ARG_DEAD_LETTER_ROUTING_KEY, "mto.eventos.queue.dlq");

        assertThat(queueNames(declarables)).contains("mto.eventos.queue.dlq");
        assertThat(boundQueues(declarables)).contains("mto.eventos.queue.dlq");
    }

    @Test
    void sinDeadLetterNoSeCreaNadaDeMas() {
        RabbitMqProperties properties = new RabbitMqProperties();

        RabbitMqProperties.Queue sinDlq = queue("mto.simple.queue");
        sinDlq.setDeadLetterEnabled(false);
        properties.setQueues(List.of(sinDlq));

        Declarables declarables = build(properties);

        assertThat(queueNames(declarables)).containsExactly("mto.simple.queue");
        assertThat(findQueue(declarables, "mto.simple.queue").orElseThrow().getArguments())
                .doesNotContainKey(RabbitMqConstants.ARG_DEAD_LETTER_EXCHANGE);
    }

    @Test
    void losArgumentosSueltosDeLaConfiguracionSeRespetan() {
        RabbitMqProperties properties = new RabbitMqProperties();

        RabbitMqProperties.Queue conExtras = queue("mto.extras.queue");
        conExtras.setArguments(Map.of("x-single-active-consumer", true));
        properties.setQueues(List.of(conExtras));

        assertThat(findQueue(build(properties), "mto.extras.queue").orElseThrow().getArguments())
                .containsEntry("x-single-active-consumer", true);
    }

    @Test
    void todoLoDeclaradoEsDeclarableParaSpringAmqp() {
        RabbitMqProperties properties = new RabbitMqProperties();

        RabbitMqProperties.Exchange exchange = new RabbitMqProperties.Exchange();
        exchange.setName("mto.master-data.exchange");
        properties.setExchanges(List.of(exchange));
        properties.setQueues(List.of(queue("mto.eventos.queue")));
        properties.setBindings(List.of(binding("mto.eventos.queue", "mto.master-data.exchange", "mto.#")));

        assertThat(build(properties).getDeclarables())
                .isNotEmpty()
                .allSatisfy(declarable -> assertThat(declarable).isInstanceOf(Declarable.class));
    }
}
