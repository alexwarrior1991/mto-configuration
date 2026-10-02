package com.alejandro.mtoconfiguration.controller;

import com.alejandro.mtoconfiguration.configuration.security.KeycloakJwtAuthenticationConverter;
import com.alejandro.mtoconfiguration.controller.commons.ConfigurationApiPaths;
import com.alejandro.mtoconfiguration.controller.synchronous.lov.ProfileStatusController;
import com.alejandro.mtoconfiguration.core.exception.ConcurrencyException;
import com.alejandro.mtoconfiguration.core.exception.RestExceptionHandler;
import com.alejandro.mtoconfiguration.core.exception.web.ApiErrorConfiguration;
import com.alejandro.mtoconfiguration.core.exception.web.ErrorCatalog;
import com.alejandro.mtoconfiguration.core.exception.web.ProblemDetailFactory;
import com.alejandro.mtoconfiguration.model.synchronous.lov.ProfileStatusDTO;
import com.alejandro.mtoconfiguration.service.lov.ProfileStatusService;
import jakarta.persistence.EntityNotFoundException;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
 * Contrato HTTP heredado por las dieciseis listas de valores.
 *
 * <p>Se prueba contra un controlador concreto ({@code ProfileStatusController}) y no contra una
 * subclase inventada a proposito: lo que interesa comprobar es justamente que una LOV que no
 * escribe ni un endpoint hereda las rutas, los verbos y los codigos de
 * {@link com.alejandro.mtoconfiguration.controller.synchronous.lov.commons.AbstractLovController}.
 * Lo que valga aqui vale para las quince restantes.</p>
 *
 * <p>Los codigos son la parte fragil: el alta responde <b>201</b> y el borrado <b>204</b>, no 200
 * como el resto de controladores del proyecto. Es una diferencia facil de romper al tocar la clase
 * base y que ningun test del servicio veria.</p>
 */
@WebMvcTest(controllers = ProfileStatusController.class,
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
class LovControllerContractTest {

    private static final String LOV = ConfigurationApiPaths.BASE_PATH + "/profile-statuses";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfileStatusService service;

    private static ProfileStatusDTO lov(Long id, String code) {
        ProfileStatusDTO dto = new ProfileStatusDTO();
        dto.setId(id);
        dto.setCode(code);
        dto.setDescription("Descripcion " + code);
        return dto;
    }

    @Test
    @DisplayName("listar responde 200 con el catalogo completo")
    void findAll() throws Exception {
        when(service.findAll()).thenReturn(List.of(lov(1L, "A"), lov(2L, "B")));

        mockMvc.perform(get(LOV))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("A"));
    }

    @Test
    @DisplayName("consultar por id responde 200")
    void findById() throws Exception {
        when(service.findById(1L)).thenReturn(lov(1L, "A"));

        mockMvc.perform(get(LOV + "/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("A"));
    }

    @Test
    @DisplayName("consultar por codigo tiene su propia ruta")
    void findByCode() throws Exception {
        when(service.findByCode("A")).thenReturn(lov(1L, "A"));

        mockMvc.perform(get(LOV + "/code/A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("crear responde 201, no 200")
    void create() throws Exception {
        when(service.create(any())).thenReturn(lov(1L, "A"));

        mockMvc.perform(post(LOV)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"A\",\"description\":\"Descripcion A\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("crear en lote responde 201")
    void bulkCreate() throws Exception {
        when(service.bulkCreate(anyList())).thenReturn(List.of(lov(1L, "A"), lov(2L, "B")));

        mockMvc.perform(post(LOV + "/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"code\":\"A\",\"description\":\"d\"},{\"code\":\"B\",\"description\":\"d\"}]"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("modificar responde 200 y manda el id de la ruta")
    void update() throws Exception {
        when(service.update(eq(5L), any())).thenReturn(lov(5L, "A"));

        mockMvc.perform(put(LOV + "/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":999,\"code\":\"A\",\"description\":\"d\"}"))
                .andExpect(status().isOk());

        // El id de la ruta es la fuente de verdad: el del cuerpo ni se consulta.
        verify(service).update(eq(5L), any());
    }

    @Test
    @DisplayName("modificar en lote responde 200")
    void bulkUpdate() throws Exception {
        when(service.bulkUpdate(anyList())).thenReturn(List.of(lov(1L, "A")));

        mockMvc.perform(put(LOV + "/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"id\":1,\"code\":\"A\",\"description\":\"d\"}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("borrar responde 204 sin cuerpo")
    void delete204() throws Exception {
        mockMvc.perform(delete(LOV + "/1"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(service).delete(1L);
    }

    @Test
    @DisplayName("una entrada sale con su version y con quien la toco por ultima vez, pero sin su creacion")
    void versionYAuditoria() throws Exception {
        ProfileStatusDTO dto = lov(1L, "A");
        dto.setVersionNumber(3);
        dto.setVersionDate(LocalDateTime.of(2026, 9, 30, 8, 15));
        dto.setVersionUser("ana");
        dto.setCreateDate(LocalDateTime.of(2026, 1, 2, 9, 0));
        dto.setCreateUser("luis");
        when(service.findAll()).thenReturn(List.of(dto));

        mockMvc.perform(get(LOV))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].versionNumber").value(3))
                .andExpect(jsonPath("$[0].versionDate").value("2026-09-30T08:15:00"))
                .andExpect(jsonPath("$[0].versionUser").value("ana"))
                .andExpect(jsonPath("$[0].createDate").doesNotExist())
                .andExpect(jsonPath("$[0].createUser").doesNotExist());
    }

    @Test
    @DisplayName("la version leida llega al servicio en la modificacion y en el lote")
    void versionLeida() throws Exception {
        // Es el bloqueo optimista: si el JSON no la leyera, el servicio no tendria nada que comparar
        // y ganaria el ultimo en guardar.
        when(service.update(eq(5L), any())).thenReturn(lov(5L, "A"));
        when(service.bulkUpdate(anyList())).thenReturn(List.of(lov(1L, "A")));

        mockMvc.perform(put(LOV + "/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":5,\"code\":\"A\",\"description\":\"d\",\"versionNumber\":3}"))
                .andExpect(status().isOk());
        mockMvc.perform(put(LOV + "/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"id\":1,\"code\":\"A\",\"description\":\"d\",\"versionNumber\":2}]"))
                .andExpect(status().isOk());

        ArgumentCaptor<ProfileStatusDTO> modificada = ArgumentCaptor.forClass(ProfileStatusDTO.class);
        verify(service).update(eq(5L), modificada.capture());
        assertThat(modificada.getValue().getVersionNumber()).isEqualTo(3);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProfileStatusDTO>> lote = ArgumentCaptor.forClass(List.class);
        verify(service).bulkUpdate(lote.capture());
        assertThat(lote.getValue()).singleElement()
                .satisfies(entrada -> assertThat(entrada.getVersionNumber()).isEqualTo(2));
    }

    @Test
    @DisplayName("una version vieja sale como 409 CON-001")
    void versionVieja() throws Exception {
        when(service.update(eq(5L), any()))
                .thenThrow(new ConcurrencyException("ProfileStatus 5 ha cambiado desde que se leyo"));

        mockMvc.perform(put(LOV + "/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":5,\"code\":\"A\",\"description\":\"d\",\"versionNumber\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CON-001"));
    }

    @Test
    @DisplayName("borrar una entrada que otro registro usa sale como 409 BUS-002: el borrado es fisico")
    void borradoEnUso() throws Exception {
        doThrow(new DataIntegrityViolationException("violates foreign key constraint"))
                .when(service).delete(1L);

        mockMvc.perform(delete(LOV + "/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUS-002"));
    }

    @Test
    @DisplayName("un id inexistente sale como 404, no como 500")
    void noEncontrado() throws Exception {
        when(service.findById(anyLong())).thenThrow(new EntityNotFoundException("ProfileStatus not found with id 99"));

        mockMvc.perform(get(LOV + "/99")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("borrar un id inexistente sale como 404")
    void borradoNoEncontrado() throws Exception {
        doThrow(new EntityNotFoundException("ProfileStatus not found with id 99"))
                .when(service).delete(99L);

        mockMvc.perform(delete(LOV + "/99")).andExpect(status().isNotFound());
    }
}
