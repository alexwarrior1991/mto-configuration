package com.alejandro.mtoconfiguration.core.rabbitmq;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Un error de topologia contra el broker sale tarde y sin pistas: PRECONDITION_FAILED
 * no dice que cola lo provoco, y arrastra la declaracion de todo lo demas. Estos
 * tests fijan lo que se puede cazar antes de conectar.
 */
class RabbitMqTopologyValidatorTest {

    private RabbitMqProperties.Queue queue(String name) {
        RabbitMqProperties.Queue queue = new RabbitMqProperties.Queue();
        queue.setName(name);
        return queue;
    }

    private void validate(RabbitMqProperties properties) {
        new RabbitMqTopologyValidator(properties).validate();
    }

    @Test
    void unaColaQuorumExclusivaSeRechazaConSuMotivo() {
        RabbitMqProperties properties = new RabbitMqProperties();
        RabbitMqProperties.Queue q = queue("mto.quorum.queue");
        q.setType(RabbitMqProperties.QueueType.QUORUM);
        q.setExclusive(true);
        properties.setQueues(List.of(q));

        assertThatThrownBy(() -> validate(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no puede ser exclusive");
    }

    @Test
    void unaColaQuorumAutoDeleteONoDurableSeRechaza() {
        RabbitMqProperties autoDelete = new RabbitMqProperties();
        RabbitMqProperties.Queue a = queue("a");
        a.setType(RabbitMqProperties.QueueType.QUORUM);
        a.setAutoDelete(true);
        autoDelete.setQueues(List.of(a));
        assertThatThrownBy(() -> validate(autoDelete)).hasMessageContaining("auto-delete");

        RabbitMqProperties noDurable = new RabbitMqProperties();
        RabbitMqProperties.Queue b = queue("b");
        b.setType(RabbitMqProperties.QueueType.QUORUM);
        b.setDurable(false);
        noDurable.setQueues(List.of(b));
        assertThatThrownBy(() -> validate(noDurable)).hasMessageContaining("durable");
    }

    @Test
    void elLimiteDeEntregasNoExisteEnColasClasicas() {
        RabbitMqProperties properties = new RabbitMqProperties();
        RabbitMqProperties.Queue q = queue("mto.clasica.queue");
        q.setDeliveryLimit(3);
        properties.setQueues(List.of(q));

        assertThatThrownBy(() -> validate(properties))
                .hasMessageContaining("x-delivery-limit solo existe en colas quorum");
    }

    @Test
    void unOverflowSinLimiteNoSirveDeNada() {
        RabbitMqProperties properties = new RabbitMqProperties();
        RabbitMqProperties.Queue q = queue("mto.acotada.queue");
        q.setOverflow(RabbitMqProperties.Overflow.REJECT_PUBLISH);
        properties.setQueues(List.of(q));

        // Es el error silencioso tipico: se cree tener proteccion contra desbordamiento
        // y el argumento no llega a aplicarse nunca porque no hay limite que alcanzar.
        assertThatThrownBy(() -> validate(properties))
                .hasMessageContaining("no tiene ningun limite");
    }

    @Test
    void unBindingAUnaColaDesconocidaSeRechaza() {
        RabbitMqProperties properties = new RabbitMqProperties();
        RabbitMqProperties.Binding binding = new RabbitMqProperties.Binding();
        binding.setQueue("mto.fantasma.queue");
        binding.setExchange("mto.exchange");
        properties.setBindings(List.of(binding));

        assertThatThrownBy(() -> validate(properties))
                .hasMessageContaining("apunta a una cola que no esta");
    }

    @Test
    void losNombresDuplicadosSeRechazan() {
        RabbitMqProperties colas = new RabbitMqProperties();
        colas.setQueues(List.of(queue("repetida"), queue("repetida")));
        assertThatThrownBy(() -> validate(colas)).hasMessageContaining("esta declarada mas de una vez");

        RabbitMqProperties exchanges = new RabbitMqProperties();
        RabbitMqProperties.Exchange e1 = new RabbitMqProperties.Exchange();
        e1.setName("dup");
        RabbitMqProperties.Exchange e2 = new RabbitMqProperties.Exchange();
        e2.setName("dup");
        exchanges.setExchanges(List.of(e1, e2));
        assertThatThrownBy(() -> validate(exchanges)).hasMessageContaining("mas de una vez");
    }

    @Test
    void losValoresNegativosOACeroSeRechazan() {
        RabbitMqProperties properties = new RabbitMqProperties();
        RabbitMqProperties.Queue q = queue("mto.mala.queue");
        q.setMaxLength(0L);
        q.setMessageTtl(-1L);
        properties.setQueues(List.of(q));

        assertThatThrownBy(() -> validate(properties))
                .hasMessageContaining("max-length")
                .hasMessageContaining("message-ttl");
    }

    @Test
    void elMensajeDeErrorJuntaTodosLosProblemasDeUnaVez() {
        RabbitMqProperties properties = new RabbitMqProperties();
        RabbitMqProperties.Queue q = queue("mto.mala.queue");
        q.setType(RabbitMqProperties.QueueType.QUORUM);
        q.setExclusive(true);
        q.setAutoDelete(true);
        properties.setQueues(List.of(q));

        // Corregir de uno en uno reiniciando la aplicacion cada vez es tiempo perdido.
        assertThatThrownBy(() -> validate(properties))
                .hasMessageContaining("exclusive")
                .hasMessageContaining("auto-delete");
    }

    @Test
    void unaTopologiaCoherenteNoProtesta() {
        RabbitMqProperties properties = new RabbitMqProperties();
        RabbitMqProperties.Exchange exchange = new RabbitMqProperties.Exchange();
        exchange.setName("mto.master-data.exchange");
        properties.setExchanges(List.of(exchange));

        RabbitMqProperties.Queue q = queue("mto.eventos.queue");
        q.setType(RabbitMqProperties.QueueType.QUORUM);
        q.setDeliveryLimit(5);
        q.setMaxLength(10_000L);
        q.setOverflow(RabbitMqProperties.Overflow.REJECT_PUBLISH);
        properties.setQueues(List.of(q));

        RabbitMqProperties.Binding binding = new RabbitMqProperties.Binding();
        binding.setQueue("mto.eventos.queue");
        binding.setExchange("mto.master-data.exchange");
        binding.setRoutingKey("mto.#");
        properties.setBindings(List.of(binding));

        assertThatCode(() -> validate(properties)).doesNotThrowAnyException();
    }
}
