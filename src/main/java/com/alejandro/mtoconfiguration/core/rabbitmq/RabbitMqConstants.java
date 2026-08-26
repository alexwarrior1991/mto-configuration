package com.alejandro.mtoconfiguration.core.rabbitmq;

public final class RabbitMqConstants {

    public static final String RABBIT_LISTENER_CONTAINER_FACTORY = "rabbitListenerContainerFactory";

    public static final String HEADER_RETRY_COUNT = "x-retry-count";
    public static final String HEADER_ERROR_MESSAGE = "x-error-message";
    public static final String HEADER_ORIGINAL_EXCHANGE = "x-original-exchange";
    public static final String HEADER_ORIGINAL_ROUTING_KEY = "x-original-routing-key";

    /** Argumentos de declaracion de colas. */
    public static final String ARG_DEAD_LETTER_EXCHANGE = "x-dead-letter-exchange";
    public static final String ARG_DEAD_LETTER_ROUTING_KEY = "x-dead-letter-routing-key";
    public static final String ARG_MESSAGE_TTL = "x-message-ttl";
    public static final String ARG_MAX_LENGTH = "x-max-length";
    public static final String ARG_MAX_LENGTH_BYTES = "x-max-length-bytes";
    public static final String ARG_OVERFLOW = "x-overflow";
    public static final String ARG_QUEUE_TYPE = "x-queue-type";
    public static final String ARG_QUEUE_MODE = "x-queue-mode";
    public static final String ARG_DELIVERY_LIMIT = "x-delivery-limit";

    private RabbitMqConstants(){

    }
}
