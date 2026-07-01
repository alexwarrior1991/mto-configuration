package com.alejandro.mtoconfiguration.core.rabbitmq;

public final class RabbitMqConstants {

    public static final String RABBIT_LISTENER_CONTAINER_FACTORY = "rabbitListenerContainerFactory";

    public static final String HEADER_RETRY_COUNT = "x-retry-count";
    public static final String HEADER_ERROR_MESSAGE = "x-error-message";
    public static final String HEADER_ORIGINAL_EXCHANGE = "x-original-exchange";
    public static final String HEADER_ORIGINAL_ROUTING_KEY = "x-original-routing-key";

    private RabbitMqConstants(){

    }
}
