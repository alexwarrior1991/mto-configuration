package com.alejandro.mtoconfiguration.core.rabbitmq;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.rabbitmq")
public class RabbitMqProperties {


    /**
     * Activa o desactiva la declaración automática de exchanges, queues y bindings.
     */
    private boolean enabled = true;

    /**
     * Configuración global por defecto para las colas.
     */
    @Valid
    private Defaults defaults = new Defaults();

    /**
     * Exchanges declarados por la aplicación.
     */
    @Valid
    private List<Exchange> exchanges = new ArrayList<>();

    /**
     * Colas declaradas por la aplicación.
     */
    @Valid
    private List<Queue> queues = new ArrayList<>();

    /**
     * Bindings entre exchanges y colas.
     */
    @Valid
    private List<Binding> bindings = new ArrayList<>();

    /**
     * Configuración del listener container.
     */
    @Valid
    private Listener listener = new Listener();

    /**
     * Configuración del publisher.
     */
    @Valid
    private Publisher publisher = new Publisher();


    @Getter
    @Setter
    public static class Defaults {
        private boolean durable = true;
        private boolean exclusive = false;
        private boolean autoDelete = false;
        private boolean deadLetterEnabled = true;
        private String deadLetterExchangeSuffix = ".dlx";
        private String deadLetterQueueSuffix = ".dlq";
        private String deadLetterRoutingKeySuffix = ".dlq";
    }

    @Getter
    @Setter
    public static class Exchange {

        @NotBlank
        private String name;

        /**
         * Valores soportados: direct, topic, fanout, headers.
         */
        @NotNull
        private ExchangeType type = ExchangeType.TOPIC;

        private boolean durable = true;
        private boolean autoDelete = false;
        private boolean internal = false;
        private Map<String, Object> arguments = new HashMap<>();
    }

    @Getter
    @Setter
    public static class Queue {

        @NotBlank
        private String name;

        private Boolean durable;
        private Boolean exclusive;
        private Boolean autoDelete;

        /**
         * Si está activo, se creará automáticamente DLX + DLQ para esta cola.
         */
        private Boolean deadLetterEnabled;

        /**
         * Permite sobrescribir el DLX concreto de esta cola.
         */
        private String deadLetterExchange;

        /**
         * Permite sobrescribir la DLQ concreta de esta cola.
         */
        private String deadLetterQueue;

        /**
         * Permite sobrescribir la routing key usada para mandar mensajes a la DLQ.
         */
        private String deadLetterRoutingKey;

        /**
         * TTL de mensajes en milisegundos.
         */
        private Long messageTtl;

        /**
         * Tamaño máximo de cola.
         */
        private Long maxLength;

        /**
         * Tamaño máximo en bytes.
         */
        private Long maxLengthBytes;

        /**
         * Modo lazy: útil para colas grandes.
         */
        private boolean lazy = false;

        private Map<String, Object> arguments = new HashMap<>();
    }

    @Getter
    @Setter
    public static class Binding {

        @NotBlank
        private String queue;

        @NotBlank
        private String exchange;

        /**
         * No obligatorio para fanout.
         */
        private String routingKey = "";

        private Map<String, Object> arguments = new HashMap<>();
    }

    @Getter
    @Setter
    public static class Listener {
        private int concurrentConsumers = 1;
        private int maxConcurrentConsumers = 5;
        private int prefetchCount = 10;
        private boolean defaultRequeueRejected = false;
        private long receiveTimeout = 1000L;
    }

    @Getter
    @Setter
    public static class Publisher {
        private boolean mandatory = true;
    }

    public enum ExchangeType {
        DIRECT,
        TOPIC,
        FANOUT,
        HEADERS
    }
}
