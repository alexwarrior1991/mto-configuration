package com.alejandro.mtoconfiguration.core.exception.web;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCode;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import com.alejandro.mtoconfiguration.validator.commons.ErrorType;
import com.alejandro.mtoconfiguration.validator.commons.StandardErrorCodes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Traduce un código del catálogo a lo que necesita la capa HTTP: estado, mensaje y reintentabilidad.
 *
 * <p>Esta clase es el <b>único</b> punto donde el catálogo de errores se encuentra con HTTP.
 * {@code validator.commons} no conoce Spring Web: describe el error (tipo, severidad, plantilla,
 * reintentable) y es aquí donde se decide qué estado le corresponde. Así el mismo catálogo sirve si
 * mañana los errores viajan por mensajería en vez de por REST.</p>
 */
@Slf4j
@Component
public class ErrorCatalog {

    /**
     * Estado por defecto de cada familia de errores.
     *
     * <p>400 para una petición mal formada y 422 para una que se entiende pero infringe una regla de
     * negocio: el cliente necesita distinguir «te has equivocado escribiendo» de «esto no se puede
     * hacer».</p>
     */
    private static final Map<ErrorType, HttpStatus> STATUS_BY_TYPE = Map.of(
            ErrorType.VALIDATION, HttpStatus.BAD_REQUEST,
            ErrorType.BUSINESS, HttpStatus.UNPROCESSABLE_ENTITY,
            ErrorType.NOT_FOUND, HttpStatus.NOT_FOUND,
            ErrorType.SECURITY, HttpStatus.FORBIDDEN,
            ErrorType.CONCURRENCY, HttpStatus.CONFLICT,
            ErrorType.INTEGRATION, HttpStatus.BAD_GATEWAY,
            ErrorType.TECHNICAL, HttpStatus.INTERNAL_SERVER_ERROR);

    /** Códigos cuyo estado concreto no se deduce de la familia. */
    private static final Map<String, HttpStatus> STATUS_BY_CODE = Map.of(
            ErrorCodes.UNAUTHORIZED, HttpStatus.UNAUTHORIZED,
            ErrorCodes.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED,
            ErrorCodes.DUPLICATED_RESOURCE, HttpStatus.CONFLICT,
            ErrorCodes.INTEGRATION_TIMEOUT, HttpStatus.GATEWAY_TIMEOUT);

    public Optional<ErrorCode> find(String code) {
        return code == null ? Optional.empty() : StandardErrorCodes.findByCode(code);
    }

    public HttpStatus statusOf(String code) {
        if (code == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }

        return Optional.ofNullable(STATUS_BY_CODE.get(code))
                .or(() -> find(code).map(ErrorCode::type).map(STATUS_BY_TYPE::get))
                .orElse(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public boolean isRetryable(String code) {
        return find(code).map(ErrorCode::retryable).orElse(false);
    }

    /** Título corto de la familia del error, para el campo {@code title} de RFC 9457. */
    public String titleOf(String code) {
        return find(code)
                .map(ErrorCode::type)
                .map(ErrorCatalog::titleOf)
                .orElse("Error");
    }

    /**
     * Convierte una alerta en su detalle listo para publicar.
     *
     * <p>Por convención el primer elemento de {@code Alert.fields} es la ruta del campo y el resto
     * son argumentos de la plantilla.</p>
     */
    public ApiErrorDetail toDetail(Alert alert) {
        List<String> fields = alert.getFields();
        String field = fields.isEmpty() ? null : fields.get(0);

        return new ApiErrorDetail(field, alert.getMessage(), resolveMessage(alert.getMessage(), fields));
    }

    /**
     * Formatea el mensaje de un código con sus argumentos.
     *
     * <p>Nunca propaga una excepción: una plantilla que no case con sus argumentos degrada al propio
     * código. Que el catálogo esté mal configurado no puede tumbar la respuesta de error, que es
     * justo lo que el cliente necesita para entender qué ha pasado.</p>
     */
    public String resolveMessage(String code, List<String> args) {
        return find(code)
                .map(errorCode -> format(errorCode, args))
                .orElse(code);
    }

    private String format(ErrorCode errorCode, List<String> args) {
        try {
            return errorCode.format(args.toArray());
        } catch (RuntimeException e) {
            log.warn("La plantilla de {} no admite los argumentos {}: {}",
                    errorCode.code(), args, e.getMessage());

            // Se devuelve la plantilla sin sus marcadores: un "%s" suelto en la respuesta no le
            // dice nada a quien la lee y delata que el catálogo está mal montado.
            return stripPlaceholders(errorCode.messageTemplate());
        }
    }

    private static String stripPlaceholders(String template) {
        return template.replaceAll("\\s*'?%[sd]'?", "").trim();
    }

    private static String titleOf(ErrorType type) {
        return switch (type) {
            case VALIDATION -> "Petición inválida";
            case BUSINESS -> "Regla de negocio incumplida";
            case NOT_FOUND -> "Recurso no encontrado";
            case SECURITY -> "Acceso denegado";
            case CONCURRENCY -> "Conflicto de concurrencia";
            case INTEGRATION -> "Error de integración";
            case TECHNICAL -> "Error interno";
        };
    }
}
