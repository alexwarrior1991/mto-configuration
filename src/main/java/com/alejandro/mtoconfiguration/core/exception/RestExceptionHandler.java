package com.alejandro.mtoconfiguration.core.exception;

import com.alejandro.mtoconfiguration.core.model.exception.DefaultErrorResponse;
import feign.FeignException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.nio.file.AccessDeniedException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice
//ToDo: write in the configuration
@ConditionalOnProperty(value = "configuration.modules.rest.exception-handler.enabled", havingValue = "true", matchIfMissing = true)
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Handles exceptions of type {@link FeignException.Unauthorized} triggered by Feign client calls.
     * Logs the exception's error message and returns a ResponseEntity with an HTTP 401 Unauthorized status.
     *
     * @param e        the FeignException.Unauthorized instance containing details of the unauthorized exception
     * @param response the HttpServletResponse associated with the current request
     * @return a ResponseEntity with an HTTP status of 401 Unauthorized
     */
    @ExceptionHandler(FeignException.Unauthorized.class)
    public ResponseEntity<Object> handleFeignStatusException(FeignException.Unauthorized e, HttpServletResponse response) {
        log.error(e.getMessage(), e);
        return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
    }

    /**
     * Handles exceptions of type {@link InvalidBearerTokenException}.
     * Logs the exception's error message and returns a ResponseEntity
     * with an HTTP 401 Unauthorized status.
     *
     * @param e        the InvalidBearerTokenException instance providing details of the exception
     * @param response the HttpServletResponse associated with the current request
     * @return a ResponseEntity with an HTTP status of 401 Unauthorized
     */
    @ExceptionHandler(InvalidBearerTokenException.class)
    public ResponseEntity<Object> handleTokenNotActiveException(InvalidBearerTokenException e, HttpServletResponse response) {
        log.error(e.getMessage(), e);
        return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
    }

    /**
     * Handles exceptions of type {@link HttpClientErrorException.Unauthorized}.
     * Logs the exception's error message and returns a ResponseEntity containing
     * a {@link DefaultErrorResponse} with an HTTP 401 Unauthorized status.
     *
     * @param e        the {@link HttpClientErrorException.Unauthorized} instance containing details of the unauthorized exception
     * @param response the {@link HttpServletResponse} associated with the current request
     * @return a {@link ResponseEntity} containing a {@link DefaultErrorResponse} with an HTTP status of 401 Unauthorized
     */
    @ExceptionHandler(HttpClientErrorException.Unauthorized.class)
    public ResponseEntity<DefaultErrorResponse> handleUnauthorizedException(HttpClientErrorException.Unauthorized e, HttpServletResponse response) {
        log.error(e.getMessage(), e);
        return new ResponseEntity<>(getErrorResponse("unauthorized", HttpStatus.UNAUTHORIZED), HttpStatus.UNAUTHORIZED);
    }

    /**
     * Handles exceptions of type {@link AccessDeniedException}.
     * Logs the exception's error message and returns a {@link ResponseEntity} containing
     * a {@link DefaultErrorResponse} with an HTTP 401 Unauthorized status.
     *
     * @param e        the {@link AccessDeniedException} instance containing details of the access denial exception
     * @param response the {@link HttpServletResponse} associated with the current request
     * @return a {@link ResponseEntity} containing a {@link DefaultErrorResponse} with an HTTP status of 401 Unauthorized
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<DefaultErrorResponse> handleAccessDeniedException(AccessDeniedException e, HttpServletResponse response) {
        log.error(e.getMessage(), e);
        return new ResponseEntity<>(getErrorResponse("unauthorized", HttpStatus.UNAUTHORIZED), HttpStatus.UNAUTHORIZED);
    }


    /**
     * Handles exceptions of type {@link GenericException}.
     * Logs the exception's error message and constructs a {@link ResponseEntity}
     * containing a {@link DefaultErrorResponse} with an HTTP 400 Bad Request status.
     *
     * @param e       the {@link GenericException} instance containing details of the exception
     * @param request the {@link HttpServletRequest} associated with the current request
     * @return a {@link ResponseEntity} containing a {@link DefaultErrorResponse} with an HTTP status of 400 Bad Request
     */
    @ExceptionHandler(GenericException.class)
    public ResponseEntity<Notification> handleGenericException(GenericException e, HttpServletRequest request) {
        log.error(e.getMessage(), e);

        Notification notification = getNotification(
                e.getAction(),
                e.getCode(),
                e.getMessage(),
                e.getSeverity(),
                request.getRequestURI(),   // mejor que getPathInfo()
                e.getCategory(),
                request
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(notification);
    }

    @ExceptionHandler(TransactionSystemException.class)
    public ResponseEntity<Object> handleJPAViolations(TransactionSystemException e) {
        log.error(e.getMessage(), e);

        var maybeConstraintViolation = Stream
                .iterate(e.getCause(), Objects::nonNull, Throwable::getCause)
                .filter(ConstraintViolationException.class::isInstance)
                .map(ConstraintViolationException.class::cast)
                .findFirst();

        if (maybeConstraintViolation.isPresent()) {
            ConstraintViolationException ve = maybeConstraintViolation.get();

            // Lista de mapas {campo -> mensaje}
            var errors = ve.getConstraintViolations().stream()
                    .map(constraintViolation -> {
                        Map<String, String> errMap = new HashMap<>();
                        errMap.put(
                                constraintViolation.getPropertyPath().toString(),
                                constraintViolation.getMessage()
                        );
                        return errMap;
                    })
                    .distinct()
                    .toList();

            return ResponseEntity.badRequest().body(errors);
        }

        return ResponseEntity.badRequest().build();
    }

    /**
     * Handles exceptions of type {@link MethodArgumentNotValidException} triggered when method argument validation fails.
     * Logs the exception's error message and returns a {@link ResponseEntity} containing a {@link DefaultErrorResponse}
     * with an HTTP 400 Bad Request status.
     *
     * @param e       the {@link MethodArgumentNotValidException} containing validation error details
     * @param headers the HTTP headers to be used in the response
     * @param status  the HTTP status code corresponding to the error
     * @param request the {@link WebRequest} associated with the current request
     * @return a {@link ResponseEntity} containing a {@link DefaultErrorResponse} with a list of validation error messages
     * and an HTTP status of 400 Bad Request, or {@code null} if no response is to be returned
     */
    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.error(e.getMessage(), e);
        var errors = e.getBindingResult().getAllErrors().stream()
                .map(ObjectError::getDefaultMessage)
                .distinct()
                .toList();
        return new ResponseEntity<>(getErrorResponse(errors, HttpStatus.BAD_REQUEST), HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles exceptions of type {@link ServletException}.
     * Logs the exception's error message and constructs a {@link ResponseEntity}
     * containing a {@link DefaultErrorResponse} with an HTTP 400 Bad Request status.
     *
     * @param e        the {@link ServletException} instance containing details of the exception
     * @param response the {@link HttpServletResponse} associated with the current request
     * @return a {@link ResponseEntity} containing a {@link DefaultErrorResponse}
     * with an HTTP status of 400 Bad Request
     */
    @ExceptionHandler(ServletException.class)
    public ResponseEntity<DefaultErrorResponse> handleServletException(ServletException e, HttpServletResponse response) {
        log.error(e.getMessage(), e);
        return new ResponseEntity<>(getErrorResponse(e.getMessage(), HttpStatus.BAD_REQUEST), HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles runtime exceptions and constructs a {@link ResponseEntity} containing a
     * {@link DefaultErrorResponse} with an HTTP 400 Bad Request status.
     * Logs the exception details for debugging purposes.
     *
     * @param e        the {@link Exception} instance that was thrown
     * @param response the {@link HttpServletResponse} associated with the current request
     * @return a {@link ResponseEntity} containing a {@link DefaultErrorResponse} with an
     * HTTP status of 400 Bad Request
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<DefaultErrorResponse> handleRuntimeException(Exception e, HttpServletResponse response) {
        log.error(e.getMessage(), e);
        return new ResponseEntity<>(getErrorResponse(e.getMessage(), HttpStatus.BAD_REQUEST), HttpStatus.BAD_REQUEST);
    }

    /**
     * Constructs a {@link DefaultErrorResponse} instance with an error message and HTTP status.
     *
     * @param message the error message to include in the response
     * @param status  the HTTP status to include in the response
     * @return a {@link DefaultErrorResponse} instance containing the provided message and status
     */
    private DefaultErrorResponse getErrorResponse(String message, HttpStatus status) {
        List<String> messages = new ArrayList<>();
        messages.add(message);
        return getErrorResponse(messages, status);
    }

    /**
     * Constructs a {@link DefaultErrorResponse} object containing the provided error details.
     *
     * @param message a list of error messages to include in the response
     * @param status  the HTTP status to be set in the response
     * @return an instance of {@link DefaultErrorResponse} containing the error details
     */
    private DefaultErrorResponse getErrorResponse(List<String> message, HttpStatus status) {
        DefaultErrorResponse error = new DefaultErrorResponse();
        error.setTimestamp(LocalDateTime.now());
        error.setErrors(message);
        error.setStatus(status.value());
        return error;
    }


    private Notification getNotification(String action,
                                         String code,
                                         String description,
                                         String severity,
                                         String path,
                                         String category, HttpServletRequest s) {
        return Optional.of(new Notification())
                .map(notification -> {
                    notification.setTimestamp(DateTimeFormatter
                            .ofPattern("yyyy-MM-dd hh:mm:ss")
                            .format(java.time.LocalDateTime.now()));
                    notification.setAction(action);
                    notification.setCode(code);
                    notification.setDescription(description);
                    notification.setSeverity(severity);
                    notification.setPath(s.getRequestURI());
                    notification.setCategory(category);
                    return notification;
                })
                .orElseThrow();
    }
}
