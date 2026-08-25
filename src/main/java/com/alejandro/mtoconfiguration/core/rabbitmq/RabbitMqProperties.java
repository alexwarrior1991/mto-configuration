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

        /**
         * Si esta aplicacion declara las colas de la lista.
         * <p>
         * Una cola pertenece a quien la consume, no a quien publica: es el consumidor
         * el que sabe que TTL, que limite y que tipo necesita.
         * <p>
         * Declarar en los dos lados es idempotente SOLO si los argumentos coinciden
         * exactamente. Si no, el broker responde PRECONDITION_FAILED y cierra el canal:
         * RabbitAdmin declara exchanges, luego colas y luego bindings sobre el mismo
         * canal, de modo que los exchanges ya declarados sobreviven, pero se quedan sin
         * declarar el resto de colas del lote y TODOS los bindings. Sin bindings no se
         * enruta nada. Y con dos repositorios y dos ciclos de release, los argumentos
         * acaban divergiendo: el consumidor querra un TTL y no podra ponerlo sin un
         * despliegue coordinado de un servicio al que eso no le afecta.
         */
        private boolean declareQueues = true;

        /** Tipo por defecto de las colas declaradas. */
        private QueueType queueType = QueueType.CLASSIC;
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
         * Modo lazy.
         *
         * @deprecated RabbitMQ 3.12 y posteriores IGNORAN {@code x-queue-mode}: las
         * colas clasicas v2 ya escriben a disco por defecto. Se mantiene para no
         * romper configuraciones existentes, pero declararlo no cambia nada.
         */
        @Deprecated(since = "RabbitMQ 3.12")
        private boolean lazy = false;

        /**
         * Si este servicio declara la cola. Si no se informa, manda
         * {@code defaults.declare-queues}.
         * <p>
         * Ponlo a false para las colas que pertenecen a otro servicio: entonces no se
         * declaran ni ellas, ni su dead letter, ni sus bindings.
         */
        private Boolean declare;

        /**
         * Tipo de cola. Si no se informa, manda {@code defaults.queue-type}.
         * <p>
         * Las quorum se replican entre nodos, de modo que sobreviven a la caida del
         * nodo que las alojaba; una clasica sin replica se pierde con el, por muy
         * durable que sea. OJO: el tipo de una cola NO se puede cambiar en caliente,
         * hay que borrarla y volver a crearla.
         */
        private QueueType type;

        /**
         * Entregas antes de mandar un mensaje a la dead letter (solo colas quorum).
         * <p>
         * Es la proteccion contra el mensaje envenenado a nivel de broker, sin
         * depender de que el consumidor cuente reintentos.
         */
        private Integer deliveryLimit;

        /**
         * Que hacer cuando la cola alcanza su limite.
         * <p>
         * El valor por defecto de RabbitMQ es descartar los mensajes MAS ANTIGUOS en
         * silencio, que para eventos de datos maestros es perdida de datos.
         * {@code REJECT_PUBLISH} devuelve un nack al publicador: con el outbox, eso
         * es un reintento con backoff en lugar de un evento perdido.
         */
        private Overflow overflow;

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
    public static class Publisher {
        private boolean mandatory = true;
    }

    public enum ExchangeType {
        DIRECT,
        TOPIC,
        FANOUT,
        HEADERS
    }

    public enum QueueType {

        CLASSIC("classic"),
        QUORUM("quorum");

        private final String argumentValue;

        QueueType(String argumentValue) {
            this.argumentValue = argumentValue;
        }

        public String argumentValue() {
            return argumentValue;
        }
    }

    public enum Overflow {

        /** Comportamiento por defecto de RabbitMQ: descarta los mensajes mas antiguos. */
        DROP_HEAD("drop-head"),

        /** Rechaza la publicacion con un nack. El publicador se entera. */
        REJECT_PUBLISH("reject-publish"),

        /** Rechaza la publicacion y ademas manda el mensaje a la dead letter. */
        REJECT_PUBLISH_DLX("reject-publish-dlx");

        private final String argumentValue;

        Overflow(String argumentValue) {
            this.argumentValue = argumentValue;
        }

        public String argumentValue() {
            return argumentValue;
        }
    }
}
