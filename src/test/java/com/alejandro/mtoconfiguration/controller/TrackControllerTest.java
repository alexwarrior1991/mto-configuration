package com.alejandro.mtoconfiguration.controller;

import com.alejandro.mtoconfiguration.configuration.security.KeycloakJwtAuthenticationConverter;
import com.alejandro.mtoconfiguration.controller.commons.ConfigurationApiPaths;
import com.alejandro.mtoconfiguration.controller.synchronous.infraestructure.TrackController;
import com.alejandro.mtoconfiguration.core.exception.NotFoundException;
import com.alejandro.mtoconfiguration.core.exception.RestExceptionHandler;
import com.alejandro.mtoconfiguration.core.exception.web.ApiErrorConfiguration;
import com.alejandro.mtoconfiguration.core.exception.web.ErrorCatalog;
import com.alejandro.mtoconfiguration.core.exception.web.ProblemDetailFactory;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.TrackDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.schematic.TrackSchematicDTO;
import com.alejandro.mtoconfiguration.service.infraestructure.TrackSchematicService;
import com.alejandro.mtoconfiguration.service.infraestructure.TrackService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP del esquema de via ({@code GET /tracks/{id}/schematic}): la forma del JSON que lee
 * el backoffice, que lo que va a null no viaja y que una via inexistente es un 404 con codigo.
 * El CRUD de vias es el mismo de {@code ProfileControllerTest}, que sirve de plantilla; aqui solo
 * lo propio de este controlador.
 *
 * <p>La seguridad se apaga en el slice a proposito: el {@code GET} lo cubre la regla general de
 * {@code CONFIG_READ} que prueba {@code ApiAuthorizationRulesTest}.</p>
 */
@WebMvcTest(controllers = TrackController.class,
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
class TrackControllerTest {

    private static final String TRACKS = ConfigurationApiPaths.BASE_PATH + "/tracks";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TrackService trackService;
    @MockitoBean
    private TrackSchematicService trackSchematicService;

    private static TrackSchematicDTO esquema() {
        var brazo = new TrackSchematicDTO.CantileverArm(21L, "PT1", "-200", "5.300", "1.400", "SA1", 1200L);
        var seccionador = new TrackSchematicDTO.DisconnectorMark(40L, "SEC-40", true, "FEED", "ATOCHA");
        var p1 = new TrackSchematicDTO.ProfileNode(1L, "P-001", "10.000", 1, "55.000", "HEB", null, "OK",
                "-2500", new ArrayList<>(List.of("S1")), new ArrayList<>(List.of(brazo)), null);
        var p2 = new TrackSchematicDTO.ProfileNode(2L, "P-002", "20.000", 2, null, null, null, null,
                null, new ArrayList<>(), new ArrayList<>(), seccionador);
        var aguja = new TrackSchematicDTO.SwitchMark(60L, "W31", "15.500", 9, "VIA 1");
        var aislador = new TrackSchematicDTO.InsulatorMark(50L, "AIS-50", "15.000", "TRACK_CONNECTION", true,
                "ATOCHA", "VIA 1", "VIA 2", new ArrayList<>(List.of(aguja)));
        return new TrackSchematicDTO(3L, "VIA 1", true, "EP4", new ArrayList<>(List.of("ATOCHA", "CHAMARTIN")),
                new ArrayList<>(List.of(p1, p2)), new ArrayList<>(List.of(aislador)));
    }

    @Nested
    @DisplayName("El esquema de la via")
    class Esquema {

        @Test
        @DisplayName("responde 200 con la via, sus perfiles en el orden recibido y sus aisladores")
        void esquemaCompleto() throws Exception {
            when(trackSchematicService.getSchematic(3L)).thenReturn(esquema());

            mockMvc.perform(get(TRACKS + "/3/schematic"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.trackId").value(3))
                    .andExpect(jsonPath("$.trackName").value("VIA 1"))
                    .andExpect(jsonPath("$.executionPackageName").value("EP4"))
                    .andExpect(jsonPath("$.stations[1]").value("CHAMARTIN"))
                    .andExpect(jsonPath("$.profiles[0].code").value("P-001"))
                    .andExpect(jsonPath("$.profiles[0].kp").value("10.000"))
                    .andExpect(jsonPath("$.profiles[0].railPoleDistance").value("-2500"))
                    .andExpect(jsonPath("$.profiles[0].sectionings[0]").value("S1"))
                    .andExpect(jsonPath("$.profiles[0].cantilevers[0].type").value("PT1"))
                    .andExpect(jsonPath("$.profiles[0].cantilevers[0].steadyArmLength").value(1200))
                    .andExpect(jsonPath("$.profiles[1].code").value("P-002"))
                    .andExpect(jsonPath("$.profiles[1].disconnector.name").value("SEC-40"))
                    .andExpect(jsonPath("$.profiles[1].disconnector.station").value("ATOCHA"))
                    .andExpect(jsonPath("$.sectionInsulators[0].name").value("AIS-50"))
                    .andExpect(jsonPath("$.sectionInsulators[0].installationType").value("TRACK_CONNECTION"))
                    .andExpect(jsonPath("$.sectionInsulators[0].connectedTrack").value("VIA 2"))
                    .andExpect(jsonPath("$.sectionInsulators[0].switches[0].code").value("W31"));
        }

        @Test
        @DisplayName("lo que va a null no viaja; las listas vacias si")
        void nulosFuera() throws Exception {
            when(trackSchematicService.getSchematic(3L)).thenReturn(esquema());

            mockMvc.perform(get(TRACKS + "/3/schematic"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.profiles[0].disconnector").doesNotExist())
                    .andExpect(jsonPath("$.profiles[0].supportType").doesNotExist())
                    .andExpect(jsonPath("$.profiles[1].span").doesNotExist())
                    .andExpect(jsonPath("$.profiles[1].cantilevers").isArray())
                    .andExpect(jsonPath("$.profiles[1].cantilevers").isEmpty());
        }

        @Test
        @DisplayName("una via inexistente responde 404 NOT-001")
        void viaInexistente() throws Exception {
            when(trackSchematicService.getSchematic(404L)).thenThrow(new NotFoundException("Track not found with id 404"));

            mockMvc.perform(get(TRACKS + "/404/schematic"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT-001"));
        }
    }

    @Test
    @DisplayName("el detalle de la via sigue en su ruta: /{id}/schematic no la pisa")
    void elDetalleSigueAhi() throws Exception {
        TrackDTO via = new TrackDTO();
        via.setId(3L);
        via.setName("VIA 1");
        when(trackService.getById(3L)).thenReturn(via);

        mockMvc.perform(get(TRACKS + "/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("VIA 1"));
    }
}
