package com.alejandro.mtoconfiguration.core.rabbitmq;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Revisa la topologia declarada antes de mandarsela al broker.
 * <p>
 * Los errores de topologia en RabbitMQ salen tarde y mal: un argumento incompatible
 * se traduce en un PRECONDITION_FAILED del broker que tumba la declaracion de TODO
 * el bloque, incluidos los exchanges que si eran correctos, y el mensaje de error no
 * dice cual de las colas lo provoco. Aqui se detecta lo que se puede detectar sin
 * broker, con un mensaje que dice que arreglar.
 */
@Slf4j
public class RabbitMqTopologyValidator {

    private final RabbitMqProperties properties;

    public RabbitMqTopologyValidator(RabbitMqProperties properties) {
        this.properties = properties;
    }

    public void validate() {
        List<String> errores = new ArrayList<>();

        validateUniqueNames(errores);
        properties.getQueues().forEach(queue -> validateQueue(queue, errores));
        validateBindings(errores);

        if (!errores.isEmpty()) {
            throw new IllegalStateException(
                    "Topologia de RabbitMQ incoherente en app.rabbitmq:\n  - "
                            + String.join("\n  - ", errores));
        }

        warnAboutDeprecations();
        warnAboutUnboundedQueues();
        warnAboutUnboundQueues();
    }

    private void validateUniqueNames(List<String> errores) {
        Set<String> vistos = new HashSet<>();
        properties.getQueues().stream()
                .map(RabbitMqProperties.Queue::getName)
                .filter(name -> !vistos.add(name))
                .forEach(name -> errores.add("la cola '" + name + "' esta declarada mas de una vez"));

        Set<String> exchangesVistos = new HashSet<>();
        properties.getExchanges().stream()
                .map(RabbitMqProperties.Exchange::getName)
                .filter(name -> !exchangesVistos.add(name))
                .forEach(name -> errores.add("el exchange '" + name + "' esta declarado mas de una vez"));
    }

    private void validateQueue(RabbitMqProperties.Queue queue, List<String> errores) {
        boolean quorum = resolveType(queue) == RabbitMqProperties.QueueType.QUORUM;

        if (quorum) {
            // El broker rechaza estas combinaciones; mejor decirlo aqui que descubrirlo
            // en el arranque contra un PRECONDITION_FAILED sin pistas.
            if (Boolean.TRUE.equals(queue.getExclusive())) {
                errores.add("la cola quorum '" + queue.getName() + "' no puede ser exclusive");
            }
            if (Boolean.TRUE.equals(queue.getAutoDelete())) {
                errores.add("la cola quorum '" + queue.getName() + "' no puede ser auto-delete");
            }
            if (Boolean.FALSE.equals(queue.getDurable())) {
                errores.add("la cola quorum '" + queue.getName() + "' tiene que ser durable");
            }
            if (queue.isLazy()) {
                errores.add("la cola quorum '" + queue.getName() + "' no admite el modo lazy");
            }
        } else if (queue.getDeliveryLimit() != null) {
            errores.add("x-delivery-limit solo existe en colas quorum, y '"
                    + queue.getName() + "' es classic");
        }

        if (queue.getDeliveryLimit() != null && queue.getDeliveryLimit() < 1) {
            errores.add("delivery-limit de '" + queue.getName() + "' tiene que ser mayor que cero");
        }
        if (queue.getMessageTtl() != null && queue.getMessageTtl() < 0) {
            errores.add("message-ttl de '" + queue.getName() + "' no puede ser negativo");
        }
        if (queue.getMaxLength() != null && queue.getMaxLength() < 1) {
            errores.add("max-length de '" + queue.getName() + "' tiene que ser mayor que cero");
        }
        if (queue.getMaxLengthBytes() != null && queue.getMaxLengthBytes() < 1) {
            errores.add("max-length-bytes de '" + queue.getName() + "' tiene que ser mayor que cero");
        }
        if (queue.getOverflow() != null && queue.getMaxLength() == null && queue.getMaxLengthBytes() == null) {
            errores.add("la cola '" + queue.getName() + "' fija overflow pero no tiene ningun limite:"
                    + " sin max-length ni max-length-bytes ese argumento no llega a aplicarse nunca");
        }
    }

    private void validateBindings(List<String> errores) {
        Set<String> colasDeclaradas = properties.getQueues().stream()
                .filter(this::isDeclared)
                .map(RabbitMqProperties.Queue::getName)
                .collect(java.util.stream.Collectors.toSet());

        Set<String> colasConocidas = properties.getQueues().stream()
                .map(RabbitMqProperties.Queue::getName)
                .collect(java.util.stream.Collectors.toSet());

        properties.getBindings().forEach(binding -> {
            if (!colasConocidas.contains(binding.getQueue())) {
                errores.add("el binding a '" + binding.getQueue() + "' apunta a una cola que no esta"
                        + " en app.rabbitmq.queues: declara la cola aqui o quita el binding");
                return;
            }
            if (!colasDeclaradas.contains(binding.getQueue())) {
                errores.add("el binding a '" + binding.getQueue() + "' no se puede mantener aqui:"
                        + " la cola tiene declare=false, asi que pertenece a otro servicio y es"
                        + " ese servicio el que debe crear su binding");
            }
        });
    }

    private void warnAboutUnboundQueues() {
        Set<String> conBinding = properties.getBindings().stream()
                .map(RabbitMqProperties.Binding::getQueue)
                .collect(java.util.stream.Collectors.toSet());

        properties.getQueues().stream()
                .filter(this::isDeclared)
                .filter(queue -> !conBinding.contains(queue.getName()))
                .forEach(queue -> log.warn(
                        "La cola '{}' se declara pero no tiene ningun binding en esta"
                                + " configuracion: no recibira ni un mensaje salvo que la bindee"
                                + " otro servicio. Revisa app.rabbitmq.bindings.",
                        queue.getName()));
    }

    private void warnAboutUnboundedQueues() {
        properties.getQueues().stream()
                .filter(this::isDeclared)
                .filter(queue -> queue.getMaxLength() == null
                        && queue.getMaxLengthBytes() == null
                        && queue.getMessageTtl() == null)
                .forEach(queue -> log.warn(
                        "La cola '{}' se declara sin ningun limite (max-length, max-length-bytes"
                                + " ni message-ttl). Si su consumidor se para, crece hasta llenar el"
                                + " disco del broker, y un broker con el disco lleno bloquea las"
                                + " publicaciones de TODOS los servicios. Los limites de una cola que"
                                + " ya existe se aplican con una policy, no redeclarandola.",
                        queue.getName()));
    }

    private void warnAboutDeprecations() {
        properties.getQueues().stream()
                .filter(RabbitMqProperties.Queue::isLazy)
                .forEach(queue -> log.warn(
                        "La cola '{}' declara lazy, pero RabbitMQ 3.12 y posteriores ignoran"
                                + " x-queue-mode: las colas clasicas v2 ya escriben a disco por defecto."
                                + " Quita la propiedad o pasa la cola a quorum.",
                        queue.getName()));
    }

    private boolean isDeclared(RabbitMqProperties.Queue queue) {
        return queue.getDeclare() != null
                ? queue.getDeclare()
                : properties.getDefaults().isDeclareQueues();
    }

    private RabbitMqProperties.QueueType resolveType(RabbitMqProperties.Queue queue) {
        return queue.getType() != null ? queue.getType() : properties.getDefaults().getQueueType();
    }
}
