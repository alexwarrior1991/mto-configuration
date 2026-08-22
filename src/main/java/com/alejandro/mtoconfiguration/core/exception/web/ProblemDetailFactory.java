package com.alejandro.mtoconfiguration.core.exception.web;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Construye la respuesta de error en formato <i>Problem Details</i> (RFC 9457).
 *
 * <p>Se usa el estándar en vez de un formato propio porque ya lo entienden los clientes HTTP, las
 * pasarelas y los generadores de OpenAPI, y porque Spring lo trae de serie ({@link ProblemDetail}).
 * A los campos estándar se añaden cuatro extensiones:</p>
 *
 * <ul>
 *   <li>{@code code} — código estable del catálogo, para reaccionar sin parsear el texto.</li>
 *   <li>{@code traceId} — el mismo identificador que aparece en el log del servidor.</li>
 *   <li>{@code retryable} — si reintentar la misma petición puede funcionar.</li>
 *   <li>{@code errors} — el detalle campo a campo.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ProblemDetailFactory {

    public static final String PROPERTY_CODE = "code";
    public static final String PROPERTY_TRACE_ID = "traceId";
    public static final String PROPERTY_TIMESTAMP = "timestamp";
    public static final String PROPERTY_RETRYABLE = "retryable";
    public static final String PROPERTY_ERRORS = "errors";

    private static final String MDC_TRACE_ID = "traceId";

    private final ErrorCatalog catalog;
    private final ApiErrorProperties properties;

    /**
     * Fallo de validación de la petición: siempre 400, con el detalle campo a campo.
     *
     * <p>El estado lo fija la operación, no los códigos concretos: una petición puede acumular
     * campos obligatorios y duplicados a la vez, y sigue siendo una sola petición inválida. Cada
     * alerta conserva su propio código dentro de {@code errors}.</p>
     */
    public ProblemDetail validationFailure(List<Alert> alerts, String instance) {
        List<ApiErrorDetail> details = CollectionUtils.emptyIfNull(alerts).stream()
                .filter(Objects::nonNull)
                .map(catalog::toDetail)
                .toList();

        ProblemDetail problem = base(
                ErrorCodes.VALIDATION_FAILED,
                HttpStatus.BAD_REQUEST,
                catalog.resolveMessage(ErrorCodes.VALIDATION_FAILED, List.of(String.valueOf(details.size()))),
                instance);

        problem.setProperty(PROPERTY_ERRORS, details);

        return problem;
    }

    /**
     * Error identificado por su código: el estado y el mensaje salen del catálogo.
     */
    public ProblemDetail fromCode(String code, String instance) {
        return fromCode(code, catalog.resolveMessage(code, List.of()), instance);
    }

    public ProblemDetail fromCode(String code, String detail, String instance) {
        return base(code, catalog.statusOf(code), detail, instance);
    }

    /**
     * Error con detalle por campo procedente de otra fuente (Bean Validation, restricciones JPA).
     */
    public ProblemDetail withDetails(String code, String detail, List<ApiErrorDetail> details, String instance) {
        ProblemDetail problem = fromCode(code, detail, instance);
        problem.setProperty(PROPERTY_ERRORS, details);

        return problem;
    }

    /** Identificador de correlación de la respuesta, para casarla con el log del servidor. */
    public static String traceIdOf(ProblemDetail problem) {
        return problem.getProperties() == null
                ? null
                : String.valueOf(problem.getProperties().get(PROPERTY_TRACE_ID));
    }

    private ProblemDetail base(String code, HttpStatus status, String detail, String instance) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);

        problem.setType(URI.create(properties.typeBaseUri() + "/" + code.toLowerCase(Locale.ROOT)));
        problem.setTitle(catalog.titleOf(code));

        if (instance != null) {
            problem.setInstance(URI.create(instance));
        }

        problem.setProperty(PROPERTY_CODE, code);
        problem.setProperty(PROPERTY_TIMESTAMP, Instant.now());
        problem.setProperty(PROPERTY_TRACE_ID, currentTraceId());
        problem.setProperty(PROPERTY_RETRYABLE, catalog.isRetryable(code));

        return problem;
    }

    /**
     * Reutiliza el identificador de traza si ya hay uno en el contexto de log; si no, genera uno.
     * Así, cuando se añada trazabilidad distribuida, el identificador de la respuesta será el mismo
     * que el de la traza sin tocar esta clase.
     */
    private String currentTraceId() {
        String existing = MDC.get(MDC_TRACE_ID);

        return (existing != null && !existing.isBlank())
                ? existing
                : UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
