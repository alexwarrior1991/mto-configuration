package com.alejandro.mtoconfiguration.core.audit;

import com.alejandro.mtoconfiguration.configuration.security.CurrentUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Optional;

/**
 * Autor de las columnas de auditoría y de las revisiones de Envers.
 *
 * <p>Cuando no hay usuario autenticado hay dos situaciones muy distintas, y antes ambas se
 * registraban como {@code system}:</p>
 *
 * <ul>
 *   <li>Un proceso de fondo —el publicador del outbox, un listener de RabbitMQ, una tarea
 *       programada— escribe sin que haya nadie detrás. Es lo normal y se registra como
 *       {@code system}.</li>
 *   <li>Una petición HTTP llega hasta la escritura sin autenticación en el contexto. Eso no debería
 *       ocurrir: o la cadena de filtros ha dejado pasar algo, o el contexto se ha perdido por el
 *       camino. Se registra como {@code unknown} y se deja constancia en el log.</li>
 * </ul>
 *
 * <p>Distinguirlas importa porque el dato sobrevive a la incidencia: quien audite meses después
 * quién cambió un dato maestro necesita poder separar «lo hizo un proceso» de «no se sabe».</p>
 */
@Slf4j
@Component
public class SpringSecurityAuditorAware implements AuditorAware<String> {

    /** Escritura sin usuario que se espera: procesos internos de la propia aplicación. */
    public static final String SYSTEM = "system";

    /** Escritura sin usuario que no se espera: la identidad se ha perdido o nunca llegó. */
    public static final String UNKNOWN = "unknown";

    private final CurrentUserService currentUserService;

    public SpringSecurityAuditorAware(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @Override
    public Optional<String> getCurrentAuditor() {
        Optional<String> usuario = currentUserService.getUsername();

        if (usuario.isPresent()) {
            return usuario;
        }

        if (!hayPeticionEnCurso()) {
            return Optional.of(SYSTEM);
        }

        log.warn("Escritura sin usuario autenticado durante una petición HTTP: se audita como '{}'. "
                + "O la cadena de filtros ha dejado pasar la petición, o se ha perdido el contexto "
                + "de seguridad entre hilos.", UNKNOWN);

        return Optional.of(UNKNOWN);
    }

    /**
     * Los procesos de fondo corren fuera del ciclo de una petición, así que la ausencia de atributos
     * de petición es lo que los separa de un hilo que sí atiende a alguien.
     */
    private boolean hayPeticionEnCurso() {
        return RequestContextHolder.getRequestAttributes() != null;
    }
}
