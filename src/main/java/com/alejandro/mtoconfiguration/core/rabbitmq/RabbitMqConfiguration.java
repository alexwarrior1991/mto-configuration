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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter rabbitMessageConverter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(rabbitMessageConverter);
        factory.setConcurrentConsumers(properties.getListener().getConcurrentConsumers());
        factory.setMaxConcurrentConsumers(properties.getListener().getMaxConcurrentConsumers());
        factory.setPrefetchCount(properties.getListener().getPrefetchCount());
        factory.setDefaultRequeueRejected(properties.getListener().isDefaultRequeueRejected());
        factory.setReceiveTimeout(properties.getListener().getReceiveTimeout());
        return factory;
    }

    @Bean
    public Declarables rabbitDeclarables() {
        List<Declarable> declarables = new ArrayList<>();

        declarables.addAll(buildExchanges());
        declarables.addAll(buildQueues());
        declarables.addAll(buildDeadLetterExchanges());
        declarables.addAll(buildDeadLetterQueues());
        declarables.addAll(buildBindings());
        declarables.addAll(buildDeadLetterBindings());

        return new Declarables(declarables);
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
        return properties.getQueues()
                .stream()
                .map(this::buildQueue)
                .toList();
    }

    private Queue buildQueue(RabbitMqProperties.Queue queue) {
        Map<String, Object> arguments = new HashMap<>(queue.getArguments());

        if (isDeadLetterEnabled(queue)) {
            arguments.put("x-dead-letter-exchange", resolveDeadLetterExchange(queue));
            arguments.put("x-dead-letter-routing-key", resolveDeadLetterRoutingKey(queue));
        }

        if (queue.getMessageTtl() != null) {
            arguments.put("x-message-ttl", queue.getMessageTtl());
        }

        if (queue.getMaxLength() != null) {
            arguments.put("x-max-length", queue.getMaxLength());
        }

        if (queue.getMaxLengthBytes() != null) {
            arguments.put("x-max-length-bytes", queue.getMaxLengthBytes());
        }

        if (queue.isLazy()) {
            arguments.put("x-queue-mode", "lazy");
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
        return properties.getQueues()
                .stream()
                .filter(this::isDeadLetterEnabled)
                .map(queue -> new DirectExchange(resolveDeadLetterExchange(queue), true, false))
                .map(exchange -> (Exchange) exchange)
                .distinct()
                .toList();
    }

    private List<Queue> buildDeadLetterQueues() {
        return properties.getQueues()
                .stream()
                .filter(this::isDeadLetterEnabled)
                .map(queue -> new Queue(resolveDeadLetterQueue(queue), true, false, false))
                .distinct()
                .toList();
    }

    private List<Binding> buildBindings() {
        return properties.getBindings()
                .stream()
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
        return properties.getQueues()
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