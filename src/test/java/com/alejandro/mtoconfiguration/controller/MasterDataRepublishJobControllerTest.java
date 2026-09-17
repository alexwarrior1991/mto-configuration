package com.alejandro.mtoconfiguration.controller;

import com.alejandro.mtoconfiguration.configuration.security.KeycloakJwtAuthenticationConverter;
import com.alejandro.mtoconfiguration.controller.synchronous.infraestructure.MasterDataRepublishJobController;
import com.alejandro.mtoconfiguration.core.exception.NotFoundException;
import com.alejandro.mtoconfiguration.core.exception.RestExceptionHandler;
import com.alejandro.mtoconfiguration.core.exception.ValidationException;
import com.alejandro.mtoconfiguration.core.exception.web.ApiErrorConfiguration;
import com.alejandro.mtoconfiguration.core.exception.web.ErrorCatalog;
import com.alejandro.mtoconfiguration.core.exception.web.ProblemDetailFactory;
import com.alejandro.mtoconfiguration.entity.jobs.AsyncJob;
import com.alejandro.mtoconfiguration.enums.jobs.JobStatus;
import com.alejandro.mtoconfiguration.enums.jobs.JobType;
import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.jobs.MasterDataRepublishJobResponse;
import com.alejandro.mtoconfiguration.service.infraestructure.jobs.MasterDataRepublishJobResponseMapper;
import com.alejandro.mtoconfiguration.service.infraestructure.jobs.MasterDataRepublishJobService;
import com.alejandro.mtoconfiguration.service.infraestructure.jobs.MasterDataRepublishJobSubmission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP del republicado de datos maestros.
 *
 * <p>Lo importante no es que devuelva JSON, es <b>cuando</b>: el 202 sale sin esperar a que se
 * republique nada, que es la razon de que esto sea un trabajo y no un endpoint sincrono —una via
 * con miles de perfiles se pasaria del timeout de 15s del gateway.</p>
 *
 * <p>La seguridad se apaga en el slice a proposito; que la ruta exija {@code CONFIG_IMPORT} se
 * prueba contra la cadena de filtros real en {@code ApiAuthorizationRulesTest}.</p>
 */
@WebMvcTest(controllers = MasterDataRepublishJobController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                ServletWebSecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class,
                OAuth2ClientAutoConfiguration.class,
                OAuth2ClientWebSecurityAutoConfiguration.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = KeycloakJwtAuthenticationConverter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({RestExceptionHandler.class, ProblemDetailFactory.class, ErrorCatalog.class,
        ApiErrorConfiguration.class})
class MasterDataRepublishJobControllerTest {

    private static final String REPUBLISH = MasterDataRepublishJobResponseMapper.JOBS_PATH;
    private static final UUID JOB_ID = UUID.fromString("11111111-2222-3333-4444-666666666666");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MasterDataRepublishJobService republishJobService;
    @MockitoBean
    private MasterDataRepublishJobResponseMapper responseMapper;

    private AsyncJob job(JobStatus status) {
        AsyncJob job = new AsyncJob();
        job.setId(JOB_ID);
        job.setType(JobType.MASTER_DATA_REPUBLISH);
        job.setStatus(status);
        job.setCreatedAt(Instant.now());
        job.setTotalItems(931);
        return job;
    }

    private MasterDataRepublishJobResponse response(JobStatus status) {
        return new MasterDataRepublishJobResponse(JOB_ID, JobType.MASTER_DATA_REPUBLISH, status,
                Instant.now(), null, null, 2L, 931, 0, 0, 0, null, null);
    }

    @Test
    @DisplayName("un republicado aceptado responde 202 con Location al estado y el jobId en el cuerpo")
    void aceptado() throws Exception {
        AsyncJob job = job(JobStatus.PENDING);
        when(republishJobService.submit(any(), any(), any()))
                .thenReturn(new MasterDataRepublishJobSubmission(job, true));
        when(responseMapper.toResponse(job)).thenReturn(response(JobStatus.PENDING));

        mockMvc.perform(post(REPUBLISH).param("entity", "profile").param("trackId", "2"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.endsWith(REPUBLISH + "/" + JOB_ID)))
                .andExpect(jsonPath("$.id").value(JOB_ID.toString()))
                .andExpect(jsonPath("$.type").value("MASTER_DATA_REPUBLISH"))
                .andExpect(jsonPath("$.totalItems").value(931));
    }

    @Test
    @DisplayName("sin cupo responde 429 con Retry-After, y el Location del trabajo rechazado")
    void sinCupo() throws Exception {
        AsyncJob job = job(JobStatus.REJECTED);
        when(republishJobService.submit(any(), any(), any()))
                .thenReturn(new MasterDataRepublishJobSubmission(job, false));
        when(responseMapper.toResponse(job)).thenReturn(response(JobStatus.REJECTED));

        // 429 y no 202: un 202 con un trabajo que nunca va a correr seria mentirle al cliente. El
        // Location viaja igual porque la fila REJECTED existe y se puede consultar.
        mockMvc.perform(post(REPUBLISH).param("entity", "all"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.endsWith(REPUBLISH + "/" + JOB_ID)))
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    @DisplayName("una seleccion invalida sale como 400 con el cuerpo de error de la API")
    void seleccionInvalida() throws Exception {
        when(republishJobService.submit(any(), any(), any()))
                .thenThrow(new ValidationException(List.of(
                        Alert.ofDanger("El filtro por via solo aplica a los perfiles", "trackId"))));

        mockMvc.perform(post(REPUBLISH).param("entity", "disconnector").param("trackId", "2"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("omitir la entidad no significa republicarlo todo: es un 400")
    void entidadObligatoria() throws Exception {
        when(republishJobService.submit(any(), any(), any()))
                .thenThrow(new ValidationException(List.of(
                        Alert.ofDanger("La entidad a republicar es obligatoria", "entity"))));

        mockMvc.perform(post(REPUBLISH))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("el estado de un trabajo se consulta por su identificador")
    void estado() throws Exception {
        AsyncJob job = job(JobStatus.COMPLETED);
        when(republishJobService.getJob(JOB_ID)).thenReturn(job);
        when(responseMapper.toResponse(job)).thenReturn(response(JobStatus.COMPLETED));

        mockMvc.perform(get(REPUBLISH + "/" + JOB_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("un trabajo que no existe es un 404")
    void inexistente() throws Exception {
        when(republishJobService.getJob(any())).thenThrow(new NotFoundException("No existe el trabajo"));

        mockMvc.perform(get(REPUBLISH + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
