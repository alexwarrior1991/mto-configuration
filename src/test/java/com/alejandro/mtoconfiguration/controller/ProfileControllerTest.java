package com.alejandro.mtoconfiguration.controller;

import com.alejandro.mtoconfiguration.configuration.security.KeycloakJwtAuthenticationConverter;
import com.alejandro.mtoconfiguration.controller.commons.ConfigurationApiPaths;
import com.alejandro.mtoconfiguration.controller.synchronous.infraestructure.ProfileController;
import com.alejandro.mtoconfiguration.core.exception.NotFoundException;
import com.alejandro.mtoconfiguration.core.exception.RestExceptionHandler;
import com.alejandro.mtoconfiguration.core.exception.ValidationException;
import com.alejandro.mtoconfiguration.core.exception.web.ApiErrorConfiguration;
import com.alejandro.mtoconfiguration.core.exception.web.ErrorCatalog;
import com.alejandro.mtoconfiguration.core.exception.web.ProblemDetailFactory;
import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ProfileDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.ProfileFilter;
import com.alejandro.mtoconfiguration.service.infraestructure.ProfileExportService;
import com.alejandro.mtoconfiguration.service.infraestructure.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP del controlador sincrono de perfiles, que es el mas completo del proyecto y sirve
 * de plantilla para el resto de controladores de infraestructura.
 *
 * <p>Lo que se fija aqui es lo que un cliente puede observar y el servicio no decide: el codigo de
 * respuesta de cada verbo, que el id de la ruta mande sobre el del cuerpo en un PUT, la paginacion
 * por defecto y la traduccion de las excepciones de servicio a un cuerpo de error. Nada de esto se
 * ve en un test del servicio.</p>
 *
 * <p>La seguridad se apaga en el slice a proposito: los permisos de estas rutas se prueban aparte,
 * contra la cadena de filtros real, en {@code ApiAuthorizationRulesTest}.</p>
 */
@WebMvcTest(controllers = ProfileController.class,
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
class ProfileControllerTest {

    private static final String PROFILES = ConfigurationApiPaths.BASE_PATH + "/profiles";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfileService profileService;
    @MockitoBean
    private ProfileExportService profileExportService;

    private static ProfileDTO profile(Long id) {
        ProfileDTO dto = new ProfileDTO();
        dto.setId(id);
        dto.setProfileId("P-" + id);
        return dto;
    }

    @BeforeEach
    void setUp() {
        when(profileService.create(any())).thenAnswer(i -> i.getArgument(0));
        when(profileService.update(any())).thenAnswer(i -> i.getArgument(0));
        when(profileService.bulkCreate(anyList())).thenAnswer(i -> i.getArgument(0));
        when(profileService.bulkUpdate(anyList())).thenAnswer(i -> i.getArgument(0));
    }

    @Nested
    @DisplayName("Codigos de respuesta del CRUD")
    class CodigosDeRespuesta {

        @Test
        @DisplayName("consultar por id responde 200 con el perfil")
        void getById() throws Exception {
            when(profileService.getById(5L)).thenReturn(profile(5L));

            mockMvc.perform(get(PROFILES + "/5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(5))
                    .andExpect(jsonPath("$.profileId").value("P-5"));
        }

        @Test
        @DisplayName("crear responde 201, no 200")
        void create() throws Exception {
            mockMvc.perform(post(PROFILES)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"profileId\":\"P-1\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.profileId").value("P-1"));
        }

        @Test
        @DisplayName("crear en lote responde 201 con el array completo")
        void bulkCreate() throws Exception {
            mockMvc.perform(post(PROFILES + "/bulk")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("[{\"profileId\":\"P-1\"},{\"profileId\":\"P-2\"}]"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        @DisplayName("modificar responde 200")
        void update() throws Exception {
            mockMvc.perform(put(PROFILES + "/5")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"profileId\":\"P-5\"}"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("modificar en lote responde 200")
        void bulkUpdate() throws Exception {
            mockMvc.perform(put(PROFILES + "/bulk")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("[{\"id\":1},{\"id\":2}]"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        @DisplayName("borrar responde 204 sin cuerpo")
        void deleteById() throws Exception {
            mockMvc.perform(delete(PROFILES + "/5"))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));
        }
    }

    @Nested
    @DisplayName("Contrato de la ruta")
    class ContratoDeLaRuta {

        @Test
        @DisplayName("en un PUT manda el id de la ruta, no el del cuerpo")
        void idDeRutaManda() throws Exception {
            // El controlador hace dto.setId(id) antes de delegar: si no lo hiciera, un cliente
            // podria modificar el perfil 999 llamando a la ruta del 5.
            mockMvc.perform(put(PROFILES + "/5")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":999,\"profileId\":\"P-5\"}"))
                    .andExpect(status().isOk());

            org.mockito.ArgumentCaptor<ProfileDTO> captor =
                    org.mockito.ArgumentCaptor.forClass(ProfileDTO.class);
            verify(profileService).update(captor.capture());
            assertThat(captor.getValue().getId()).isEqualTo(5L);
        }

        @Test
        @DisplayName("el borrado delega con un DTO que solo lleva el id de la ruta")
        void borradoPorId() throws Exception {
            mockMvc.perform(delete(PROFILES + "/7")).andExpect(status().isNoContent());

            org.mockito.ArgumentCaptor<ProfileDTO> captor =
                    org.mockito.ArgumentCaptor.forClass(ProfileDTO.class);
            verify(profileService).delete(captor.capture());
            assertThat(captor.getValue().getId()).isEqualTo(7L);
        }
    }

    @Nested
    @DisplayName("Paginacion y filtros")
    class PaginacionYFiltros {

        @Test
        @DisplayName("sin parametros la pagina por defecto es de 20 elementos")
        void paginaPorDefecto() throws Exception {
            when(profileService.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(profile(1L))));

            mockMvc.perform(get(PROFILES + "/paged")).andExpect(status().isOk());

            org.mockito.ArgumentCaptor<Pageable> captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
            verify(profileService).findAll(captor.capture());
            assertThat(captor.getValue().getPageSize()).isEqualTo(20);
            assertThat(captor.getValue().getPageNumber()).isZero();
        }

        @Test
        @DisplayName("los parametros de paginacion y orden llegan al servicio")
        void paginaExplicita() throws Exception {
            when(profileService.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get(PROFILES + "/paged")
                            .param("page", "2").param("size", "5").param("sort", "kp,desc"))
                    .andExpect(status().isOk());

            verify(profileService).findAll(PageRequest.of(2, 5, Sort.by(Sort.Direction.DESC, "kp")));
        }

        @Test
        @DisplayName("el filtro llega al servicio con los campos ausentes normalizados a cadena vacia")
        void filtro() throws Exception {
            when(profileService.getProfiles(any(Pageable.class), any(ProfileFilter.class)))
                    .thenReturn(new PageImpl<>(List.of(profile(1L))));

            mockMvc.perform(post(PROFILES + "/filter")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"trackId\":3,\"profileId\":\"  P-1  \"}"))
                    .andExpect(status().isOk());

            org.mockito.ArgumentCaptor<ProfileFilter> captor =
                    org.mockito.ArgumentCaptor.forClass(ProfileFilter.class);
            verify(profileService).getProfiles(any(Pageable.class), captor.capture());
            assertThat(captor.getValue().trackId()).isEqualTo(3L);
            assertThat(captor.getValue().profileId()).isEqualTo("P-1");
            assertThat(captor.getValue().searchText()).isEmpty();
        }

        @Test
        @DisplayName("la busqueda por criteria devuelve la pagina del servicio")
        void search() throws Exception {
            when(profileService.search(any())).thenReturn(new PageImpl<>(List.of(profile(1L))));

            mockMvc.perform(post(PROFILES + "/search")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"filters\":{},\"pageable\":{\"page\":0,\"size\":10}}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1));
        }
    }

    @Nested
    @DisplayName("Ventanas por keyset y rango de KP")
    class Keyset {

        @Test
        @DisplayName("sin pageSize la ventana por defecto es de 50 perfiles")
        void keysetPorDefecto() throws Exception {
            when(profileService.getProfilesByKeyset(eq(3L), any(), any(), eq(50)))
                    .thenReturn(List.of(profile(1L)));

            mockMvc.perform(get(PROFILES + "/track/3/keyset"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));

            verify(profileService).getProfilesByKeyset(3L, null, null, 50);
        }

        @Test
        @DisplayName("el cursor de keyset viaja como parametros de consulta")
        void keysetConCursor() throws Exception {
            when(profileService.getProfilesByKeyset(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt()))
                    .thenReturn(List.of());

            mockMvc.perform(get(PROFILES + "/track/3/keyset")
                            .param("lastKp", "12.5").param("lastId", "99").param("pageSize", "10"))
                    .andExpect(status().isOk());

            verify(profileService).getProfilesByKeyset(3L, new BigDecimal("12.5"), 99L, 10);
        }

        @Test
        @DisplayName("el rango de KP exige los dos extremos")
        void rangoSinExtremos() throws Exception {
            mockMvc.perform(get(PROFILES + "/track/3/range").param("startKp", "1"))
                    .andExpect(status().isBadRequest());

            verify(profileService, never()).getProfilesByKpRange(any(), any(), any());
        }

        @Test
        @DisplayName("el rango de KP delega los dos extremos en el servicio")
        void rangoCompleto() throws Exception {
            when(profileService.getProfilesByKpRange(any(), any(), any())).thenReturn(List.of(profile(1L)));

            mockMvc.perform(get(PROFILES + "/track/3/range")
                            .param("startKp", "1.5").param("endKp", "9.75"))
                    .andExpect(status().isOk());

            verify(profileService).getProfilesByKpRange(3L, new BigDecimal("1.5"), new BigDecimal("9.75"));
        }
    }

    @Nested
    @DisplayName("Exportacion sincrona")
    class Exportacion {

        @Test
        @DisplayName("la exportacion responde 202 sin esperar al fichero")
        void exportacion() throws Exception {
            mockMvc.perform(get(PROFILES + "/track/42/export"))
                    .andExpect(status().isAccepted())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("profiles-track-42.csv")));

            verify(profileExportService).getBasicMapper();
            verify(profileExportService).exportToCsvAsync(eq(42L), any(), any());
        }

        @Test
        @DisplayName("mapperType elige el formateador y un valor desconocido cae en el basico")
        void seleccionDeMapper() throws Exception {
            mockMvc.perform(get(PROFILES + "/track/1/export").param("mapperType", "TECHNICAL"))
                    .andExpect(status().isAccepted());
            verify(profileExportService).getTechnicalMapper();

            mockMvc.perform(get(PROFILES + "/track/1/export").param("mapperType", "default"))
                    .andExpect(status().isAccepted());
            verify(profileExportService).getDefaultMapper();

            mockMvc.perform(get(PROFILES + "/track/1/export").param("mapperType", "loQueSea"))
                    .andExpect(status().isAccepted());
            verify(profileExportService).getBasicMapper();
        }
    }

    @Nested
    @DisplayName("Errores")
    class Errores {

        @Test
        @DisplayName("un error de validacion del servicio sale como 400 en formato problem+json")
        void validacion() throws Exception {
            when(profileService.create(any())).thenThrow(new ValidationException(
                    List.of(Alert.ofDanger("validation.required.field", "profileId"))));

            mockMvc.perform(post(PROFILES)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @Test
        @DisplayName("un recurso inexistente sale como 404")
        void noEncontrado() throws Exception {
            when(profileService.getById(404L)).thenThrow(new NotFoundException("No existe el perfil 404"));

            mockMvc.perform(get(PROFILES + "/404")).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("un cuerpo mal formado sale como 400 sin llegar al servicio")
        void cuerpoMalFormado() throws Exception {
            mockMvc.perform(post(PROFILES)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"profileId\":"))
                    .andExpect(status().isBadRequest());

            verify(profileService, never()).create(any());
        }
    }
}
