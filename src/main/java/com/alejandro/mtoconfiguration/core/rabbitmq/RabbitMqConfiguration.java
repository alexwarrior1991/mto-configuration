package com.alejandro.mtoconfiguration.core.rabbitmq;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
@EnableRabbit
@RequiredArgsConstructor
@EnableConfigurationProperties(RabbitMqProperties.class)
@ConditionalOnProperty(prefix = "app.rabbitmq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RabbitMqConfiguration {

    private final RabbitMqProperties properties;

    @Bean
    public MessageConverter rabbitMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory,
            MessageConverter rabbitMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(rabbitMessageConverter);
        template.setMandatory(properties.getPublisher().isMandatory());
        template.setObservationEnabled(true);
        return template;
    }

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        RabbitAdmin rabbitAdmin = new RabbitAdmin(connectionFactory);
        rabbitAdmin.setAutoStartup(true);
        return rabbitAdmin;
    }

    /**
     * Factory de listeners, configurada a partir de {@code spring.rabbitmq.listener.simple.*}.
     * <p>
     * El configurer no es un adorno: declarar este bean a mano SUSTITUYE al que crea
     * Spring Boot, y sin pasar por el configurer se pierde en silencio toda la
     * configuracion del YAML. Antes esta factory solo aplicaba unas pocas propiedades
     * propias, de modo que el bloque de reintentos, el backoff y el acknowledge-mode
     * estaban escritos en application.yaml sin que nadie los leyera: un consumidor que
     * fallara no habria reintentado nunca las 3 veces configuradas.
     * <p>
     * Por eso tampoco existe ya un {@code app.rabbitmq.listener}: duplicaba una a una
     * las propiedades de Boot, y esa duplicacion era justo la que hacia creer que algo
     * estaba configurado cuando no lo estaba.
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            MessageConverter rabbitMessageConverter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();

        // Primero el YAML de Boot...
        configurer.configure(factory, connectionFactory);

        // ...y despues lo que es de este servicio.
        factory.setMessageConverter(rabbitMessageConverter);

        return factory;
    }

    @Bean
    public RabbitMqTopologyValidator rabbitMqTopologyValidator() {
        return new RabbitMqTopologyValidator(properties);
    }

    @Bean
    public Declarables rabbitDeclarables(RabbitMqTopologyValidator topologyValidator) {
        topologyValidator.validate();

        List<Declarable> declarables = new ArrayList<>();

        declarables.addAll(buildExchanges());
        declarables.addAll(buildQueues());
        declarables.addAll(buildDeadLetterExchanges());
        declarables.addAll(buildDeadLetterQueues());
        declarables.addAll(buildBindings());
        declarables.addAll(buildDeadLetterBindings());

        return new Declarables(declarables);
    }

    /** Colas de este servicio. Las que pertenecen a otro no se tocan. */
    private List<RabbitMqProperties.Queue> declaredQueues() {
        return properties.getQueues()
                .stream()
                .filter(this::isDeclared)
                .toList();
    }

    private List<Exchange> buildExchanges() {
        return properties.getExchanges()
                .stream()
                .map(this::buildExchange)
                .toList();
    }

    private Exchange buildExchange(RabbitMqProperties.Exchange exchange) {
        return switch (exchange.getType()) {
            case DIRECT -> new DirectExchange(
                    exchange.getName(),
                    exchange.isDurable(),
                    exchange.isAutoDelete(),
                    exchange.getArguments()
            );
            case TOPIC -> new TopicExchange(
                    exchange.getName(),
                    exchange.isDurable(),
                    exchange.isAutoDelete(),
                    exchange.getArguments()
            );
            case FANOUT -> new FanoutExchange(
                    exchange.getName(),
                    exchange.isDurable(),
                    exchange.isAutoDelete(),
                    exchange.getArguments()
            );
            case HEADERS -> new HeadersExchange(
                    exchange.getName(),
                    exchange.isDurable(),
                    exchange.isAutoDelete(),
                    exchange.getArguments()
            );
        };
    }

    private List<Queue> buildQueues() {
        return declaredQueues()
                .stream()
                .map(this::buildQueue)
                .toList();
    }

    private Queue buildQueue(RabbitMqProperties.Queue queue) {
        Map<String, Object> arguments = new HashMap<>(queue.getArguments());

        if (isDeadLetterEnabled(queue)) {
            arguments.put(RabbitMqConstants.ARG_DEAD_LETTER_EXCHANGE, resolveDeadLetterExchange(queue));
            arguments.put(RabbitMqConstants.ARG_DEAD_LETTER_ROUTING_KEY, resolveDeadLetterRoutingKey(queue));
        }

        if (queue.getMessageTtl() != null) {
            arguments.put(RabbitMqConstants.ARG_MESSAGE_TTL, queue.getMessageTtl());
        }

        if (queue.getMaxLength() != null) {
            arguments.put(RabbitMqConstants.ARG_MAX_LENGTH, queue.getMaxLength());
        }

        if (queue.getMaxLengthBytes() != null) {
            arguments.put(RabbitMqConstants.ARG_MAX_LENGTH_BYTES, queue.getMaxLengthBytes());
        }

        if (queue.getOverflow() != null) {
            arguments.put(RabbitMqConstants.ARG_OVERFLOW, queue.getOverflow().argumentValue());
        }

        if (resolveQueueType(queue) == RabbitMqProperties.QueueType.QUORUM) {
            // Solo se manda x-queue-type cuando es quorum: una cola clasica creada sin
            // el argumento y redeclarada CON el es una redeclaracion distinta para el
            // broker, y responde PRECONDITION_FAILED.
            arguments.put(RabbitMqConstants.ARG_QUEUE_TYPE, RabbitMqProperties.QueueType.QUORUM.argumentValue());

            if (queue.getDeliveryLimit() != null) {
                arguments.put(RabbitMqConstants.ARG_DELIVERY_LIMIT, queue.getDeliveryLimit());
            }
        } else if (queue.isLazy()) {
            // Ignorado por RabbitMQ 3.12+, pero se sigue mandando para no cambiar los
            // argumentos de colas que ya existen: eso si romperia la declaracion.
            arguments.put(RabbitMqConstants.ARG_QUEUE_MODE, "lazy");
        }

        return new Queue(
                queue.getName(),
                resolveDurable(queue),
                resolveExclusive(queue),
                resolveAutoDelete(queue),
                arguments
        );
    }

    private List<Exchange> buildDeadLetterExchanges() {
        return declaredQueues()
                .stream()
                .filter(this::isDeadLetterEnabled)
                .map(queue -> new DirectExchange(resolveDeadLetterExchange(queue), true, false))
                .map(exchange -> (Exchange) exchange)
                .distinct()
                .toList();
    }

    private List<Queue> buildDeadLetterQueues() {
        return declaredQueues()
                .stream()
                .filter(this::isDeadLetterEnabled)
                .map(this::buildDeadLetterQueue)
                .distinct()
                .toList();
    }

    /**
     * La DLQ hereda el tipo de su cola principal: de nada sirve replicar la cola de
     * trabajo si los mensajes que fallan acaban en una cola sin replica que se pierde
     * con su nodo, que ademas son justo los que hay que conservar para investigar.
     */
    private Queue buildDeadLetterQueue(RabbitMqProperties.Queue queue) {
        Map<String, Object> arguments = new HashMap<>();

        if (resolveQueueType(queue) == RabbitMqProperties.QueueType.QUORUM) {
            arguments.put(RabbitMqConstants.ARG_QUEUE_TYPE, RabbitMqProperties.QueueType.QUORUM.argumentValue());
        }

        return new Queue(resolveDeadLetterQueue(queue), true, false, false, arguments);
    }

    private List<Binding> buildBindings() {
        Set<String> colasPropias = declaredQueues()
                .stream()
                .map(RabbitMqProperties.Queue::getName)
                .collect(Collectors.toSet());

        return properties.getBindings()
                .stream()
                .filter(binding -> colasPropias.contains(binding.getQueue()))
                .map(binding -> new Binding(
                        binding.getQueue(),
                        Binding.DestinationType.QUEUE,
                        binding.getExchange(),
                        binding.getRoutingKey(),
                        binding.getArguments()
                ))
                .toList();
    }

    private List<Binding> buildDeadLetterBindings() {
        return declaredQueues()
                .stream()
                .filter(this::isDeadLetterEnabled)
                .map(queue -> new Binding(
                        resolveDeadLetterQueue(queue),
                        Binding.DestinationType.QUEUE,
                        resolveDeadLetterExchange(queue),
                        resolveDeadLetterRoutingKey(queue),
                        Map.of()
                ))
                .toList();
    }

    private boolean resolveDurable(RabbitMqProperties.Queue queue) {
        return queue.getDurable() != null
                ? queue.getDurable()
                : properties.getDefaults().isDurable();
    }

    private boolean resolveExclusive(RabbitMqProperties.Queue queue) {
        return queue.getExclusive() != null
                ? queue.getExclusive()
                : properties.getDefaults().isExclusive();
    }

    private boolean resolveAutoDelete(RabbitMqProperties.Queue queue) {
        return queue.getAutoDelete() != null
                ? queue.getAutoDelete()
                : properties.getDefaults().isAutoDelete();
    }

    private boolean isDeclared(RabbitMqProperties.Queue queue) {
        return queue.getDeclare() != null
                ? queue.getDeclare()
                : properties.getDefaults().isDeclareQueues();
    }

    private RabbitMqProperties.QueueType resolveQueueType(RabbitMqProperties.Queue queue) {
        return queue.getType() != null
                ? queue.getType()
                : properties.getDefaults().getQueueType();
    }

    private boolean isDeadLetterEnabled(RabbitMqProperties.Queue queue) {
        return queue.getDeadLetterEnabled() != null
                ? queue.getDeadLetterEnabled()
                : properties.getDefaults().isDeadLetterEnabled();
    }

    private String resolveDeadLetterExchange(RabbitMqProperties.Queue queue) {
        if (queue.getDeadLetterExchange() != null && !queue.getDeadLetterExchange().isBlank()) {
            return queue.getDeadLetterExchange();
        }
        return queue.getName() + properties.getDefaults().getDeadLetterExchangeSuffix();
    }

    private String resolveDeadLetterQueue(RabbitMqProperties.Queue queue) {
        if (queue.getDeadLetterQueue() != null && !queue.getDeadLetterQueue().isBlank()) {
            return queue.getDeadLetterQueue();
        }
        return queue.getName() + properties.getDefaults().getDeadLetterQueueSuffix();
    }

    private String resolveDeadLetterRoutingKey(RabbitMqProperties.Queue queue) {
        if (queue.getDeadLetterRoutingKey() != null && !queue.getDeadLetterRoutingKey().isBlank()) {
            return queue.getDeadLetterRoutingKey();
        }
        return queue.getName() + properties.getDefaults().getDeadLetterRoutingKeySuffix();
    }

}