package com.alejandro.mtoconfiguration.controller;

import com.alejandro.mtoconfiguration.configuration.security.KeycloakJwtAuthenticationConverter;
import com.alejandro.mtoconfiguration.controller.asynchronous.infraestructure.ProfileAsyncController;
import com.alejandro.mtoconfiguration.controller.commons.ConfigurationApiPaths;
import com.alejandro.mtoconfiguration.core.exception.NotFoundException;
import com.alejandro.mtoconfiguration.core.exception.RestExceptionHandler;
import com.alejandro.mtoconfiguration.core.exception.ValidationException;
import com.alejandro.mtoconfiguration.core.exception.web.ApiErrorConfiguration;
import com.alejandro.mtoconfiguration.core.exception.web.ErrorCatalog;
import com.alejandro.mtoconfiguration.core.exception.web.ProblemDetailFactory;
import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ProfileDTO;
import com.alejandro.mtoconfiguration.service.infraestructure.asynchronous.ProfileAsyncService;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP de la fachada {@code /async} de perfiles.
 *
 * <p>Estos endpoints devuelven {@code CompletableFuture}, asi que la respuesta se completa en dos
 * tiempos: el servlet arranca el modo asincrono y solo despues se resuelve el cuerpo. De ahi el
 * {@code asyncDispatch}. Es exactamente la parte que un test del servicio no cubre y donde es facil
 * que algo funcione en la via sincrona y no aqui.</p>
 *
 * <p>Lo que mas importa comprobar es el camino de error: una excepcion que viaja dentro de un
 * future se envuelve en {@code CompletionException}, y si el manejo no la desenvuelve el cliente
 * recibe un 500 donde la via sincrona le daba un 400 o un 404.</p>
 */
@WebMvcTest(controllers = ProfileAsyncController.class,
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
class ProfileAsyncControllerTest {

    private static final String PROFILES = ConfigurationApiPaths.ASYNC_BASE_PATH + "/profiles";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfileAsyncService profileAsyncService;

    private static ProfileDTO profile(Long id) {
        ProfileDTO dto = new ProfileDTO();
        dto.setId(id);
        dto.setProfileId("P-" + id);
        return dto;
    }

    @Test
    @DisplayName("la consulta por id se resuelve tras el despacho asincrono")
    void getById() throws Exception {
        when(profileAsyncService.getByIdAsync(5L))
                .thenReturn(CompletableFuture.completedFuture(profile(5L)));

        MvcResult started = mockMvc.perform(get(PROFILES + "/5"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    @DisplayName("el alta asincrona responde 201, igual que la sincrona")
    void create() throws Exception {
        when(profileAsyncService.createAsync(any()))
                .thenReturn(CompletableFuture.completedFuture(profile(1L)));

        MvcResult started = mockMvc.perform(post(PROFILES)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileId\":\"P-1\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(started)).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("el alta en lote asincrona responde 201 con el array completo")
    void bulkCreate() throws Exception {
        when(profileAsyncService.bulkCreateAsync(anyList()))
                .thenReturn(CompletableFuture.completedFuture(List.of(profile(1L), profile(2L))));

        MvcResult started = mockMvc.perform(post(PROFILES + "/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"profileId\":\"P-1\"},{\"profileId\":\"P-2\"}]"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("un error de validacion dentro del future sale como 400, no como 500")
    void validacionDentroDelFuture() throws Exception {
        // El future llega ya fallado: es lo que ocurre cuando el servicio valida antes de delegar
        // en el pool. Sin este caso, la via asincrona podria devolver 500 donde la sincrona da 400.
        when(profileAsyncService.createAsync(any())).thenThrow(new ValidationException(
                List.of(Alert.ofDanger("validation.required.field", "profileId"))));

        mockMvc.perform(post(PROFILES)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("un recurso inexistente sale como 404 tambien en la via asincrona")
    void noEncontrado() throws Exception {
        when(profileAsyncService.getByIdAsync(404L))
                .thenThrow(new NotFoundException("No existe el perfil 404"));

        mockMvc.perform(get(PROFILES + "/404")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("la ventana por keyset mantiene el tamaño por defecto de 50")
    void keysetPorDefecto() throws Exception {
        when(profileAsyncService.getProfilesByKeysetAsync(3L, null, null, 50))
                .thenReturn(CompletableFuture.completedFuture(List.of(profile(1L))));

        MvcResult started = mockMvc.perform(get(PROFILES + "/track/3/keyset"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
