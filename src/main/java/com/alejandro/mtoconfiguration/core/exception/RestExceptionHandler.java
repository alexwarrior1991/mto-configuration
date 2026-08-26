package com.alejandro.mtoconfiguration.core.exception;

import com.alejandro.mtoconfiguration.core.exception.web.ApiErrorDetail;
import com.alejandro.mtoconfiguration.core.exception.web.ApiErrorProperties;
import com.alejandro.mtoconfiguration.core.exception.web.ErrorCatalog;
import com.alejandro.mtoconfiguration.core.exception.web.ProblemDetailFactory;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import feign.FeignException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Traduce cualquier excepción que escape de un controlador a una respuesta
 * <i>Problem Details</i> (RFC 9457), con el mismo cuerpo para todos los casos.
 *
 * <p>Dos reglas gobiernan la clase:</p>
 *
 * <ol>
 *   <li><b>Un solo formato.</b> Todos los handlers devuelven {@link ProblemDetail}. Antes convivían
 *       tres formatos distintos según la excepción, y el cliente tenía que saber cuál esperaba.</li>
 *   <li><b>Los errores internos no se cuentan.</b> Un 5xx devuelve un mensaje genérico y un
 *       {@code traceId}; el detalle real va al log. El mensaje de una excepción no controlada puede
 *       contener SQL, rutas o nombres de tabla, y eso no sale por la API.</li>
 * </ol>
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
@RequiredArgsConstructor
@ConditionalOnProperty(value = "configuration.modules.rest.exception-handler.enabled", havingValue = "true", matchIfMissing = true)
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

    private final ProblemDetailFactory problems;
    private final ErrorCatalog catalog;
    private final ApiErrorProperties properties;

    // --- Validación de negocio ---

    /**
     * Alertas producidas por los validadores de DTO. Es la ruta por la que sale el detalle campo a
     * campo que construye la capa de validación.
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ProblemDetail> handleValidation(ValidationException e, HttpServletRequest request) {
        ProblemDetail problem = problems.validationFailure(e.getErrors(), uriOf(request));

        log.info("Validación rechazada [{}]: {}", ProblemDetailFactory.traceIdOf(problem), e.getErrors().size());

        return respond(problem);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(NotFoundException e, HttpServletRequest request) {
        return respond(problems.fromCode(ErrorCodes.RESOURCE_NOT_FOUND, e.getMessage(), uriOf(request)));
    }

    @ExceptionHandler(ConcurrencyException.class)
    public ResponseEntity<ProblemDetail> handleConcurrency(ConcurrencyException e, HttpServletRequest request) {
        return respond(problems.fromCode(ErrorCodes.CONCURRENCY_CONFLICT, uriOf(request)));
    }

    /**
     * Resto de errores de servicio. Se tratan como fallo interno salvo que la excepción traiga
     * alertas con códigos del catálogo, en cuyo caso mandan esos.
     */
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ProblemDetail> handleBase(BaseException e, HttpServletRequest request) {
        return e.getErrors().stream()
                .filter(alert -> catalog.find(alert.getMessage()).isPresent())
                .findFirst()
                // Los campos de la alerta son los argumentos de la plantilla: sin ellos, un mensaje
                // parametrizado saldría con el marcador sin sustituir.
                .map(alert -> respond(problems.fromCode(
                        alert.getMessage(),
                        catalog.resolveMessage(alert.getMessage(), alert.getFields()),
                        uriOf(request))))
                .orElseGet(() -> internalError(e, request));
    }

    // --- Validación declarativa y de persistencia ---

    /** Bean Validation sobre el cuerpo de la petición ({@code @Valid}). */
    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
                                                                            HttpHeaders headers,
                                                                            HttpStatusCode status,
                                                                            WebRequest request) {
        List<ApiErrorDetail> details = e.getBindingResult().getAllErrors().stream()
                .map(RestExceptionHandler::toDetail)
                .distinct()
                .toList();

        ProblemDetail problem = problems.withDetails(
                ErrorCodes.VALIDATION_FAILED,
                catalog.resolveMessage(ErrorCodes.VALIDATION_FAILED, List.of(String.valueOf(details.size()))),
                details,
                uriOf(request));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    /**
     * Restricciones de la entidad que salta Hibernate al hacer flush. Llegar aquí significa que la
     * validación de DTO dejó pasar algo que la columna no admite: se registra como aviso porque
     * indica una divergencia entre validador y esquema.
     */
    @ExceptionHandler(TransactionSystemException.class)
    public ResponseEntity<ProblemDetail> handleJpaViolations(TransactionSystemException e, HttpServletRequest request) {
        return Stream.iterate(e.getCause(), Objects::nonNull, Throwable::getCause)
                .filter(ConstraintViolationException.class::isInstance)
                .map(ConstraintViolationException.class::cast)
                .findFirst()
                .map(violation -> {
                    List<ApiErrorDetail> details = violation.getConstraintViolations().stream()
                            .map(v -> new ApiErrorDetail(
                                    v.getPropertyPath().toString(),
                                    ErrorCodes.VALIDATION_INVALID_FORMAT,
                                    v.getMessage()))
                            .distinct()
                            .toList();

                    log.warn("Restricción de entidad incumplida tras pasar la validación de DTO: {}", details);

                    return respond(problems.withDetails(
                            ErrorCodes.VALIDATION_FAILED,
                            catalog.resolveMessage(ErrorCodes.VALIDATION_FAILED, List.of(String.valueOf(details.size()))),
                            details,
                            uriOf(request)));
                })
                .orElseGet(() -> internalError(e, request));
    }

    // --- Seguridad ---

    /**
     * Cubre {@link AuthenticationException} entera, no solo el token inválido: por aquí entran
     * también los 401 que {@code RestAuthenticationEntryPoint} delega desde la cadena de filtros,
     * donde el fallo puede ser la ausencia de token. Sin la superclase, esos caían en el manejador
     * general y salían como 500.
     */
    @ExceptionHandler({FeignException.Unauthorized.class,
            AuthenticationException.class,
            HttpClientErrorException.Unauthorized.class})
    public ResponseEntity<ProblemDetail> handleUnauthorized(Exception e, HttpServletRequest request) {
        log.warn("Petición no autenticada a {}: {}", uriOf(request), e.getMessage());

        return respond(problems.fromCode(ErrorCodes.UNAUTHORIZED, uriOf(request)));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
        log.warn("Acceso denegado a {}: {}", uriOf(request), e.getMessage());

        return respond(problems.fromCode(ErrorCodes.FORBIDDEN, uriOf(request)));
    }

    // --- Errores de otros módulos ---

    @ExceptionHandler({GenericException.class, RailwayInfrastructureException.class})
    public ResponseEntity<ProblemDetail> handleModuleException(RuntimeException e, HttpServletRequest request) {
        String code = codeOf(e);

        return catalog.find(code).isPresent()
                ? respond(problems.fromCode(code, e.getMessage(), uriOf(request)))
                : respond(problems.fromCode(ErrorCodes.BUSINESS_RULE_VIOLATION, e.getMessage(), uriOf(request)));
    }

    // --- Cajón de sastre ---

    @ExceptionHandler({ServletException.class, Exception.class})
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception e, HttpServletRequest request) {
        return internalError(e, request);
    }

    /**
     * Respuesta de fallo interno: mensaje genérico del catálogo y {@code traceId}. El detalle real
     * queda en el log, asociado a ese mismo identificador.
     */
    private ResponseEntity<ProblemDetail> internalError(Exception e, HttpServletRequest request) {
        ProblemDetail problem = problems.fromCode(ErrorCodes.UNEXPECTED_ERROR, uriOf(request));
        String traceId = ProblemDetailFactory.traceIdOf(problem);

        log.error("Error inesperado [{}] en {}", traceId, uriOf(request), e);

        if (properties.includeStackTrace()) {
            problem.setProperty("exception", e.toString());
        }

        return respond(problem);
    }

    private ResponseEntity<ProblemDetail> respond(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private static ApiErrorDetail toDetail(ObjectError error) {
        String field = (error instanceof FieldError fieldError) ? fieldError.getField() : error.getObjectName();

        return new ApiErrorDetail(field, ErrorCodes.VALIDATION_INVALID_FORMAT, error.getDefaultMessage());
    }

    private static String codeOf(RuntimeException e) {
        return switch (e) {
            case GenericException generic -> generic.getCode();
            case RailwayInfrastructureException railway -> railway.getCode();
            default -> null;
        };
    }

    private static String uriOf(HttpServletRequest request) {
        return request != null ? request.getRequestURI() : null;
    }

    private static String uriOf(WebRequest request) {
        return request != null ? request.getDescription(false).replaceFirst("^uri=", "") : null;
    }
}
