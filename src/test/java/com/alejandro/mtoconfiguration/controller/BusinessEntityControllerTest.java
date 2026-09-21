package com.alejandro.mtoconfiguration.controller;

import com.alejandro.mtoconfiguration.configuration.security.KeycloakJwtAuthenticationConverter;
import com.alejandro.mtoconfiguration.controller.commons.ConfigurationApiPaths;
import com.alejandro.mtoconfiguration.controller.synchronous.infraestructure.BusinessEntityController;
import com.alejandro.mtoconfiguration.core.exception.RestExceptionHandler;
import com.alejandro.mtoconfiguration.core.exception.web.ApiErrorConfiguration;
import com.alejandro.mtoconfiguration.core.exception.web.ErrorCatalog;
import com.alejandro.mtoconfiguration.core.exception.web.ProblemDetailFactory;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.BusinessEntityDTO;
import com.alejandro.mtoconfiguration.service.infraestructure.BusinessEntityService;
import java.util.List;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP del controlador de entidades de negocio: dos lecturas y nada mas. Lo que se fija
 * es la ruta y la forma de la respuesta, que es lo que consume quien tiene que elegir la empresa de
 * un paquete de ejecucion.
 */
@WebMvcTest(controllers = BusinessEntityController.class,
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
class BusinessEntityControllerTest {

    private static final String BUSINESS_ENTITIES = ConfigurationApiPaths.BASE_PATH + "/business-entities";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BusinessEntityService service;

    private static BusinessEntityDTO empresa(Long id, String nif, String name) {
        BusinessEntityDTO dto = new BusinessEntityDTO();
        dto.setId(id);
        dto.setIdentificationNumber(nif);
        dto.setName(name);
        dto.setCode("C-" + id);
        return dto;
    }

    @Test
    @DisplayName("GET lista todas las empresas con id, NIF y nombre")
    void lista() throws Exception {
        when(service.findAll()).thenReturn(List.of(empresa(1L, "A12345678", "Constructora Norte"), empresa(2L, "B87654321", "Mantenedora Sur")));

        mockMvc.perform(get(BUSINESS_ENTITIES))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].identificationNumber").value("A12345678"))
                .andExpect(jsonPath("$[0].name").value("Constructora Norte"))
                .andExpect(jsonPath("$[1].name").value("Mantenedora Sur"));
    }

    @Test
    @DisplayName("GET /{id} devuelve la empresa")
    void porId() throws Exception {
        when(service.getById(7L)).thenReturn(empresa(7L, "A12345678", "Constructora Norte"));

        mockMvc.perform(get(BUSINESS_ENTITIES + "/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.name").value("Constructora Norte"));
    }
}
