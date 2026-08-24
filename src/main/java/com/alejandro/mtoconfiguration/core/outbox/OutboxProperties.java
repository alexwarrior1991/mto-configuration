package com.alejandro.mtoconfiguration.core.outbox;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.outbox")
public class OutboxProperties {

    private boolean enabled = true;

    /**
     * Intentos antes de dar un mensaje por perdido (FAILED).
     * <p>
     * Con backoff exponencial acotado a {@link #maxRetryDelay}, 20 intentos cubren
     * mas de una hora de broker caido. Con el backoff lineal anterior y 10 intentos
     * la ventana era de menos de cuatro minutos: un reinicio de RabbitMQ bastaba
     * para mandar los eventos a FAILED.
     */
    private int maxAttempts = 20;

    /** Retardo del primer reintento. Se duplica en cada intento hasta el tope. */
    private Duration initialRetryDelay = Duration.ofSeconds(5);

    /** Tope del backoff exponencial. */
    private Duration maxRetryDelay = Duration.ofMinutes(5);

    /**
     * Desviacion aleatoria aplicada al retardo, entre 0 y 1.
     * <p>
     * Con 0.2 el retardo real cae en [0.8*d, 1.2*d]. Evita que todas las replicas
     * reintenten a la vez contra un broker que acaba de levantarse.
     */
    private double retryJitter = 0.2;

    private Duration publisherFixedDelay = Duration.ofSeconds(5);

    /** Mensajes que reclama cada pasada del relay. */
    private int batchSize = 50;

    /**
     * Tiempo que un mensaje reclamado (IN_PROGRESS) permanece invisible para el
     * resto de replicas.
     * <p>
     * Es la red de seguridad ante una caida del proceso entre reclamar y publicar:
     * pasado este plazo otra replica vuelve a cogerlo. Debe ser holgadamente mayor
     * que {@link #confirmTimeout} por el tamano del lote, o dos replicas publicarian
     * el mismo mensaje.
     */
    private Duration claimVisibilityTimeout = Duration.ofMinutes(5);

    /** Espera maxima por el publisher confirm del broker antes de dar el envio por fallido. */
    private Duration confirmTimeout = Duration.ofSeconds(10);
}
