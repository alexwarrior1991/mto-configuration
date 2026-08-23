package com.alejandro.mtoconfiguration.core.exception;

import com.alejandro.mtoconfiguration.core.exception.web.ApiErrorConfiguration;
import com.alejandro.mtoconfiguration.core.exception.web.ErrorCatalog;
import com.alejandro.mtoconfiguration.core.exception.web.ProblemDetailFactory;
import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import com.alejandro.mtoconfiguration.configuration.security.KeycloakJwtAuthenticationConverter;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifica el JSON que sale de verdad por HTTP, no solo las piezas por separado: el contrato de
 * error es lo que ve el cliente, y es lo que hay que fijar.
 */
// El slice solo debe montar la traducción de errores. Se excluye el convertidor de JWT porque
// arrastra la configuración de seguridad, que no interviene en el contrato de error.
@WebMvcTest(controllers = RestExceptionHandlerTest.ProbeController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                ServletWebSecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = KeycloakJwtAuthenticationConverter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({RestExceptionHandlerTest.ProbeController.class, RestExceptionHandler.class,
        ProblemDetailFactory.class, ErrorCatalog.class, ApiErrorConfiguration.class})
class RestExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("una validación fallida devuelve 400 con el detalle campo a campo")
    void validacionDevuelve400ConDetallePorCampo() throws Exception {
        mockMvc.perform(get("/probe/validation"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_FAILED))
                .andExpect(jsonPath("$.title").value("Petición inválida"))
                .andExpect(jsonPath("$.instance").value("/probe/validation"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[0].field").value("tracks[0].name"))
                .andExpect(jsonPath("$.errors[0].code").value(ErrorCodes.VALIDATION_REQUIRED_FIELD))
                .andExpect(jsonPath("$.errors[0].message").value(containsString("tracks[0].name")))
                .andExpect(jsonPath("$.errors[1].field").value("kp"))
                .andExpect(jsonPath("$.errors[1].code").value(ErrorCodes.VALIDATION_OUT_OF_RANGE));
    }

    @Test
    @DisplayName("el mensaje de rango se formatea con el mínimo y el máximo")
    void elMensajeDeRangoLlevaSusArgumentos() throws Exception {
        mockMvc.perform(get("/probe/validation"))
                .andExpect(jsonPath("$.errors[1].message").value(allOf(
                        containsString("kp"), containsString("1"), containsString("200"))));
    }

    @Test
    @DisplayName("dos campos que incumplen la misma regla son dos errores, no uno")
    void noColapsaErroresDelMismoCodigo() throws Exception {
        mockMvc.perform(get("/probe/same-code"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("name", "enabled")));
    }

    @Test
    void recursoNoEncontradoDevuelve404() throws Exception {
        mockMvc.perform(get("/probe/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCodes.RESOURCE_NOT_FOUND))
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"));
    }

    @Test
    @DisplayName("un conflicto de concurrencia es 409 y se marca como reintentable")
    void concurrenciaDevuelve409Reintentable() throws Exception {
        mockMvc.perform(get("/probe/concurrency"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCodes.CONCURRENCY_CONFLICT))
                .andExpect(jsonPath("$.retryable").value(true));
    }

    @Test
    @DisplayName("un error inesperado no filtra el mensaje interno, solo el traceId")
    void errorInesperadoNoFiltraDetalleInterno() throws Exception {
        mockMvc.perform(get("/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNEXPECTED_ERROR))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.detail").value(not(containsString("SELECT"))))
                .andExpect(jsonPath("$.detail").value(not(containsString("PASSWORD"))))
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    @Test
    @DisplayName("todas las respuestas de error comparten la misma forma")
    void todasLasRespuestasCompartenForma() throws Exception {
        for (String path : List.of("/probe/validation", "/probe/not-found", "/probe/concurrency", "/probe/boom")) {
            mockMvc.perform(get(path))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.type").isNotEmpty())
                    .andExpect(jsonPath("$.title").isNotEmpty())
                    .andExpect(jsonPath("$.status").isNotEmpty())
                    .andExpect(jsonPath("$.code").isNotEmpty())
                    .andExpect(jsonPath("$.traceId").isNotEmpty())
                    .andExpect(jsonPath("$.timestamp").isNotEmpty())
                    .andExpect(jsonPath("$.instance").value(path));
        }
    }

    @Test
    @DisplayName("una excepción con código del catálogo usa su estado y rellena la plantilla")
    void excepcionConCodigoDelCatalogo() throws Exception {
        mockMvc.perform(get("/probe/business"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(ErrorCodes.BUSINESS_RULE_VIOLATION))
                .andExpect(jsonPath("$.detail").value("Regla de negocio violada: El paquete ya está cerrado"));
    }

    @Test
    @DisplayName("ningún marcador de plantilla llega al cliente")
    void ningunMarcadorLlegaAlCliente() throws Exception {
        for (String path : List.of("/probe/validation", "/probe/business", "/probe/not-found",
                "/probe/concurrency", "/probe/boom")) {
            mockMvc.perform(get(path))
                    .andExpect(jsonPath("$.detail").value(not(containsString("%s"))))
                    .andExpect(jsonPath("$.detail").value(not(containsString("%d"))));
        }
    }

    @Test
    @DisplayName("una excepción de servicio sin código del catálogo es un fallo interno")
    void excepcionDeServicioSinCodigoEsInterna() throws Exception {
        mockMvc.perform(get("/probe/plain-base"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNEXPECTED_ERROR))
                .andExpect(jsonPath("$.detail").value(not(containsString("CriteriaSearchRepository"))));
    }

    @Test
    @DisplayName("los mensajes en castellano viajan en UTF-8, no mutilados")
    void losMensajesViajanEnUtf8() throws Exception {
        var response = mockMvc.perform(get("/probe/validation")).andReturn().getResponse();

        assertThat(response.getCharacterEncoding()).isEqualToIgnoringCase("UTF-8");
        assertThat(new String(response.getContentAsByteArray(), StandardCharsets.UTF_8))
                .contains("Petición inválida")
                .contains("está fuera del rango");
    }

    @Test
    @DisplayName("el traceId de la respuesta es el que permite localizar el fallo en el log")
    void cadaRespuestaLlevaSuPropioTraceId() throws Exception {
        String first = traceIdOf("/probe/boom");
        String second = traceIdOf("/probe/boom");

        assertThat(first).isNotBlank().isNotEqualTo(second);
    }

    private String traceIdOf(String path) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(
                mockMvc.perform(get(path)).andReturn().getResponse().getContentAsString(), "$.traceId");
    }

    @RestController
    static class ProbeController {

        @GetMapping("/probe/validation")
        String validation() {
            throw new ValidationException(List.of(
                    Alert.ofDanger(ErrorCodes.VALIDATION_REQUIRED_FIELD, "tracks[0].name"),
                    Alert.ofDanger(ErrorCodes.VALIDATION_OUT_OF_RANGE, "kp", "1", "200")));
        }

        @GetMapping("/probe/same-code")
        String sameCode() {
            throw new ValidationException(List.of(
                    Alert.ofDanger(ErrorCodes.VALIDATION_REQUIRED_FIELD, "name"),
                    Alert.ofDanger(ErrorCodes.VALIDATION_REQUIRED_FIELD, "enabled")));
        }

        @GetMapping("/probe/business")
        String business() {
            throw new BaseException(Alert.ofDanger(
                    ErrorCodes.BUSINESS_RULE_VIOLATION, "El paquete ya está cerrado"));
        }

        @GetMapping("/probe/plain-base")
        String plainBase() {
            throw new BaseException("Search method not implemented (CriteriaSearchRepository is null)");
        }

        @GetMapping("/probe/not-found")
        String notFound() {
            throw new NotFoundException("No existe la vía 42");
        }

        @GetMapping("/probe/concurrency")
        String concurrency() {
            throw new ConcurrencyException("validation.lock.notLastVersion");
        }

        @GetMapping("/probe/boom")
        String boom() {
            throw new IllegalStateException("SELECT * FROM USERS WHERE PASSWORD='hunter2'");
        }
    }
}
