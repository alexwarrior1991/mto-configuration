package com.alejandro.mtoconfiguration.controller;

import com.alejandro.mtoconfiguration.configuration.security.KeycloakJwtAuthenticationConverter;
import com.alejandro.mtoconfiguration.controller.synchronous.infraestructure.ProfileJobController;
import com.alejandro.mtoconfiguration.core.exception.NotFoundException;
import com.alejandro.mtoconfiguration.core.exception.ValidationException;
import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.core.exception.RestExceptionHandler;
import com.alejandro.mtoconfiguration.core.exception.web.ApiErrorConfiguration;
import com.alejandro.mtoconfiguration.core.exception.web.ErrorCatalog;
import com.alejandro.mtoconfiguration.core.exception.web.ProblemDetailFactory;
import com.alejandro.mtoconfiguration.entity.jobs.AsyncJob;
import com.alejandro.mtoconfiguration.enums.jobs.JobStatus;
import com.alejandro.mtoconfiguration.enums.jobs.JobType;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.jobs.ProfileJobResponse;
import com.alejandro.mtoconfiguration.service.infraestructure.jobs.ProfileJobFiles;
import com.alejandro.mtoconfiguration.service.infraestructure.jobs.ProfileJobService;
import com.alejandro.mtoconfiguration.service.infraestructure.jobs.ProfileJobResponseMapper;
import com.alejandro.mtoconfiguration.service.infraestructure.jobs.ProfileJobSubmission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP de los trabajos de perfiles.
 *
 * <p>Lo importante de estas pruebas no es que devuelvan JSON, es <b>cuando</b> devuelven: el 202
 * sale sin esperar a que el trabajo haga nada, que es la diferencia entera con los endpoints
 * {@code /async} que devuelven {@code CompletableFuture} y mantienen la peticion abierta.</p>
 *
 * <p>La seguridad se apaga en el slice a proposito; los permisos de estas rutas se prueban aparte,
 * contra la cadena de filtros real, en {@code ProfileJobAuthorizationTest}.</p>
 */
@WebMvcTest(controllers = ProfileJobController.class,
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
class ProfileJobControllerTest {

    private static final String JOBS = ProfileJobResponseMapper.JOBS_PATH;
    private static final UUID JOB_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfileJobService profileJobService;
    @MockitoBean
    private ProfileJobResponseMapper responseMapper;
    @MockitoBean
    private ProfileJobFiles profileJobFiles;

    @TempDir
    Path tempDir;

    private AsyncJob job(JobType type, JobStatus status) {
        AsyncJob job = new AsyncJob();
        job.setId(JOB_ID);
        job.setType(type);
        job.setStatus(status);
        job.setCreatedAt(Instant.parse("2026-01-01T10:00:00Z"));
        job.setFileName("profiles-track-42-" + JOB_ID + ".csv");
        return job;
    }

    private ProfileJobResponse response(AsyncJob job) {
        return new ProfileJobResponse(job.getId(), job.getType(), job.getStatus(), job.getCreatedAt(),
                null, null, 42L, "basic", null, 0, 0, 0, null, null, null);
    }

    @BeforeEach
    void setUp() {
        when(responseMapper.toResponse(any())).thenAnswer(i -> response(i.getArgument(0)));
    }

    @Test
    @DisplayName("lanzar una exportacion responde 202 con Location y jobId")
    void exportacionAceptada() throws Exception {
        AsyncJob job = job(JobType.PROFILE_EXPORT, JobStatus.PENDING);
        when(profileJobService.submitExport(anyLong(), anyString()))
                .thenReturn(new ProfileJobSubmission(job, true));

        mockMvc.perform(post(JOBS + "/export").param("trackId", "42").param("mapperType", "basic"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "http://localhost" + JOBS + "/" + JOB_ID))
                .andExpect(jsonPath("$.id").value(JOB_ID.toString()))
                .andExpect(jsonPath("$.type").value("PROFILE_EXPORT"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("sin capacidad responde 429 con Retry-After y el trabajo REJECTED")
    void rechazoPorCapacidad() throws Exception {
        AsyncJob job = job(JobType.PROFILE_EXPORT, JobStatus.REJECTED);
        when(profileJobService.submitExport(anyLong(), anyString()))
                .thenReturn(new ProfileJobSubmission(job, false));

        // 429 y no 202: un 202 con un trabajo que nunca va a correr seria mentirle al cliente. El
        // Location sigue viajando porque el rechazo tambien tiene fila que consultar.
        mockMvc.perform(post(JOBS + "/export").param("trackId", "42"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("Location", "http://localhost" + JOBS + "/" + JOB_ID))
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    @DisplayName("una carga masiva vacia sale como 400 con el cuerpo de error de siempre")
    void cargaVacia() throws Exception {
        when(profileJobService.submitBulkCreate(any())).thenThrow(new ValidationException(
                List.of(Alert.ofDanger("La lista de perfiles no puede estar vacia", "dtoList"))));

        // Un lote vacio no se convierte en un trabajo: seria una fila que nace y muere COMPLETED
        // sin haber hecho nada, y un 202 por una peticion que casi siempre es un error del cliente.
        mockMvc.perform(post(JOBS + "/bulk-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("consultar un trabajo devuelve su estado")
    void consultaDeEstado() throws Exception {
        when(profileJobService.getJob(JOB_ID)).thenReturn(job(JobType.PROFILE_EXPORT, JobStatus.RUNNING));

        mockMvc.perform(get(JOBS + "/" + JOB_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    @DisplayName("descargar un trabajo inexistente da 404")
    void descargaDeTrabajoInexistente() throws Exception {
        when(profileJobService.getJob(JOB_ID)).thenThrow(new NotFoundException("No existe el trabajo " + JOB_ID));

        mockMvc.perform(get(JOBS + "/" + JOB_ID + "/file"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("descargar un trabajo que aun no ha terminado da 409")
    void descargaAntesDeTiempo() throws Exception {
        when(profileJobService.getJob(JOB_ID)).thenReturn(job(JobType.PROFILE_EXPORT, JobStatus.RUNNING));

        // 409 y no 404: el recurso existe y la peticion es legitima; lo que falta es esperar. Un
        // 404 mandaria al cliente a buscar un error en su identificador.
        mockMvc.perform(get(JOBS + "/" + JOB_ID + "/file"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("descargar una carga masiva da 404: no produce fichero")
    void descargaDeTrabajoSinFichero() throws Exception {
        when(profileJobService.getJob(JOB_ID)).thenReturn(job(JobType.PROFILE_BULK_CREATE, JobStatus.COMPLETED));

        mockMvc.perform(get(JOBS + "/" + JOB_ID + "/file"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("si el fichero ya no esta, 410 en vez de 404")
    void ficheroDesaparecido() throws Exception {
        when(profileJobService.getJob(JOB_ID)).thenReturn(job(JobType.PROFILE_EXPORT, JobStatus.COMPLETED));
        when(profileJobFiles.find(any())).thenReturn(Optional.empty());

        // La diferencia le importa al cliente: un 404 sugiere reintentar y un 410 dice que hay que
        // volver a lanzar la exportacion.
        mockMvc.perform(get(JOBS + "/" + JOB_ID + "/file"))
                .andExpect(status().isGone());
    }

    @Test
    @DisplayName("un trabajo COMPLETED devuelve el CSV como adjunto")
    void descargaDelCsv() throws Exception {
        AsyncJob job = job(JobType.PROFILE_EXPORT, JobStatus.COMPLETED);
        Path csv = tempDir.resolve(job.getFileName());
        Files.write(csv, List.of("profileId;kp;track", "P-1;10.5;VIA-1"));

        when(profileJobService.getJob(JOB_ID)).thenReturn(job);
        when(profileJobFiles.find(any())).thenReturn(Optional.of(new FileSystemResource(csv)));

        mockMvc.perform(get(JOBS + "/" + JOB_ID + "/file"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"" + job.getFileName() + "\""))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("P-1;10.5;VIA-1")));
    }
}
