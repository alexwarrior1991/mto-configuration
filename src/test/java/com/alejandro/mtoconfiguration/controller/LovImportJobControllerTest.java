package com.alejandro.mtoconfiguration.controller;

import com.alejandro.mtoconfiguration.configuration.security.KeycloakJwtAuthenticationConverter;
import com.alejandro.mtoconfiguration.controller.synchronous.lov.LovImportJobController;
import com.alejandro.mtoconfiguration.core.exception.RestExceptionHandler;
import com.alejandro.mtoconfiguration.core.exception.web.ApiErrorConfiguration;
import com.alejandro.mtoconfiguration.core.exception.web.ErrorCatalog;
import com.alejandro.mtoconfiguration.core.exception.web.ProblemDetailFactory;
import com.alejandro.mtoconfiguration.entity.jobs.AsyncJob;
import com.alejandro.mtoconfiguration.enums.jobs.JobStatus;
import com.alejandro.mtoconfiguration.enums.jobs.JobType;
import com.alejandro.mtoconfiguration.model.synchronous.lov.imports.LovImportJobResponse;
import com.alejandro.mtoconfiguration.service.infraestructure.jobs.LovImportJobResponseMapper;
import com.alejandro.mtoconfiguration.service.infraestructure.jobs.LovImportJobService;
import com.alejandro.mtoconfiguration.service.infraestructure.jobs.LovImportJobSubmission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP de la importacion del catalogo.
 *
 * <p>Lo que se comprueba no es que devuelva JSON, sino <b>cuando</b>: el 202 sale sin
 * esperar a que la importacion haga nada, y el 429 se distingue del 202 porque la falta
 * de cupo no es un error del cliente sino una invitacion a reintentar. Tambien que
 * {@code dryRun} llega al servicio: si se perdiera por el camino, una simulacion
 * escribiria en base de datos, que es el peor fallo posible de este endpoint.
 *
 * <p>La seguridad se apaga en el slice a proposito; los permisos de la ruta se cubren
 * contra la cadena de filtros real en {@code ApiAuthorizationRulesTest}.
 */
@WebMvcTest(controllers = LovImportJobController.class,
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
@DisplayName("LovImportJobController")
class LovImportJobControllerTest {

    private static final String JOBS = LovImportJobResponseMapper.JOBS_PATH;
    private static final UUID JOB_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LovImportJobService lovImportJobService;
    @MockitoBean
    private LovImportJobResponseMapper responseMapper;

    @BeforeEach
    void setUp() {
        when(responseMapper.toResponse(any())).thenAnswer(invocation -> {
            AsyncJob job = invocation.getArgument(0);
            return new LovImportJobResponse(job.getId(), job.getType(), job.getStatus(),
                    job.getCreatedAt(), null, null, null, 0, 0, 0, null, null, null);
        });
    }

    @Test
    @DisplayName("acepta el fichero con 202 y una cabecera Location al estado del trabajo")
    void devuelve202ConLocation() throws Exception {
        when(lovImportJobService.submit(any(), anyBoolean()))
                .thenReturn(LovImportJobSubmission.accepted(job(JobStatus.PENDING)));

        mockMvc.perform(multipart(JOBS + "/import").file(master()))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.endsWith(JOBS + "/" + JOB_ID)))
                .andExpect(jsonPath("$.id").value(JOB_ID.toString()))
                .andExpect(jsonPath("$.type").value("LOV_IMPORT"));
    }

    /**
     * 429 y no 500: no hay nada roto, solo no hay hueco. El {@code Retry-After} le dice al
     * cliente que esto se arregla esperando, no cambiando la peticion.
     */
    @Test
    @DisplayName("responde 429 con Retry-After cuando no hay cupo")
    void devuelve429SinCupo() throws Exception {
        when(lovImportJobService.submit(any(), anyBoolean()))
                .thenReturn(LovImportJobSubmission.rejected(job(JobStatus.REJECTED)));

        mockMvc.perform(multipart(JOBS + "/import").file(master()))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(header().exists("Location"));
    }

    @Test
    @DisplayName("propaga dryRun al servicio en vez de perderlo por el camino")
    void propagaDryRun() throws Exception {
        when(lovImportJobService.submit(any(), anyBoolean()))
                .thenReturn(LovImportJobSubmission.accepted(job(JobStatus.PENDING)));

        mockMvc.perform(multipart(JOBS + "/import").file(master()).param("dryRun", "true"))
                .andExpect(status().isAccepted());

        ArgumentCaptor<Boolean> dryRun = ArgumentCaptor.forClass(Boolean.class);
        org.mockito.Mockito.verify(lovImportJobService).submit(any(), dryRun.capture());
        assertThat(dryRun.getValue()).isTrue();
    }

    @Test
    @DisplayName("por defecto la importacion NO es una simulacion")
    void porDefectoNoEsSimulacion() throws Exception {
        when(lovImportJobService.submit(any(), anyBoolean()))
                .thenReturn(LovImportJobSubmission.accepted(job(JobStatus.PENDING)));

        mockMvc.perform(multipart(JOBS + "/import").file(master()))
                .andExpect(status().isAccepted());

        ArgumentCaptor<Boolean> dryRun = ArgumentCaptor.forClass(Boolean.class);
        org.mockito.Mockito.verify(lovImportJobService).submit(any(), dryRun.capture());
        assertThat(dryRun.getValue()).isFalse();
    }

    @Test
    @DisplayName("un fichero vacio es un 400, no un trabajo que fallara luego")
    void rechazaFicheroVacio() throws Exception {
        mockMvc.perform(multipart(JOBS + "/import")
                        .file(new MockMultipartFile("file", "lov-master.xlsx",
                                "application/vnd.ms-excel.sheet.macroEnabled.12", new byte[0])))
                .andExpect(status().isBadRequest());
    }

    private MockMultipartFile master() {
        return new MockMultipartFile("file", "lov-master.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "contenido".getBytes());
    }

    private AsyncJob job(JobStatus status) {
        AsyncJob job = new AsyncJob();
        job.setId(JOB_ID);
        job.setType(JobType.LOV_IMPORT);
        job.setStatus(status);
        job.setCreatedAt(Instant.parse("2026-01-01T10:00:00Z"));
        return job;
    }
}
