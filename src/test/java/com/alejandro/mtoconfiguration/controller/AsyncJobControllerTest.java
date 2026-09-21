package com.alejandro.mtoconfiguration.controller;

import com.alejandro.mtoconfiguration.configuration.security.KeycloakJwtAuthenticationConverter;
import com.alejandro.mtoconfiguration.controller.commons.ConfigurationApiPaths;
import com.alejandro.mtoconfiguration.controller.synchronous.infraestructure.AsyncJobController;
import com.alejandro.mtoconfiguration.core.exception.RestExceptionHandler;
import com.alejandro.mtoconfiguration.core.exception.web.ApiErrorConfiguration;
import com.alejandro.mtoconfiguration.core.exception.web.ErrorCatalog;
import com.alejandro.mtoconfiguration.core.exception.web.ProblemDetailFactory;
import com.alejandro.mtoconfiguration.enums.jobs.JobStatus;
import com.alejandro.mtoconfiguration.enums.jobs.JobType;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.jobs.AsyncJobListItem;
import com.alejandro.mtoconfiguration.service.infraestructure.jobs.AsyncJobQueryService;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP del listado de trabajos: la pagina tiene la forma fijada en {@code README_API.md}
 * §6, los filtros y la paginacion llegan al servicio tal cual, y sin parametros el orden es del
 * mas reciente al mas antiguo. La seguridad se apaga en el slice; el permiso de la ruta se prueba
 * contra la cadena real en {@code ProfileJobAuthorizationTest}.
 */
@WebMvcTest(controllers = AsyncJobController.class,
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
class AsyncJobControllerTest {

    private static final String JOBS = ConfigurationApiPaths.BASE_PATH + "/jobs";
    private static final UUID JOB_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AsyncJobQueryService jobs;

    private static AsyncJobListItem item() {
        return new AsyncJobListItem(JOB_ID, JobType.PROFILE_EXPORT, JobStatus.COMPLETED,
                Instant.parse("2026-01-01T10:00:00Z"), Instant.parse("2026-01-01T10:00:01Z"),
                Instant.parse("2026-01-01T10:00:05Z"), 42L, "basic", 10, 10, 10, 0, null);
    }

    @Test
    @DisplayName("la lista es una pagina con la forma DTO y filas sin errores por elemento")
    void laListaEsUnaPagina() throws Exception {
        when(jobs.list(isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(item()),
                        PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")), 1));

        mockMvc.perform(get(JOBS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(JOB_ID.toString()))
                .andExpect(jsonPath("$.content[0].type").value("PROFILE_EXPORT"))
                .andExpect(jsonPath("$.content[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.content[0].trackId").value(42))
                .andExpect(jsonPath("$.content[0].successfulItems").value(10))
                .andExpect(jsonPath("$.content[0].error").doesNotExist())
                .andExpect(jsonPath("$.content[0].itemErrors").doesNotExist())
                .andExpect(jsonPath("$.content[0].downloadUrl").doesNotExist())
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(1))
                .andExpect(jsonPath("$.pageable").doesNotExist());
    }

    @Test
    @DisplayName("tipo, estado, pagina, tamano y orden llegan al servicio tal cual")
    void losFiltrosLleganAlServicio() throws Exception {
        when(jobs.list(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get(JOBS)
                        .param("type", "LOV_IMPORT")
                        .param("status", "FAILED")
                        .param("page", "2")
                        .param("size", "5")
                        .param("sort", "status,asc"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(jobs).list(eq(JobType.LOV_IMPORT), eq(JobStatus.FAILED), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "status"));
    }

    @Test
    @DisplayName("sin parametros: veinte por pagina, del mas reciente al mas antiguo")
    void sinParametrosElOrdenEsElMasRecientePrimero() throws Exception {
        when(jobs.list(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get(JOBS)).andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(jobs).list(isNull(), isNull(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
