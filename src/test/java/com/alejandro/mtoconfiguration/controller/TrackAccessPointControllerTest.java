package com.alejandro.mtoconfiguration.controller;

import com.alejandro.mtoconfiguration.configuration.security.KeycloakJwtAuthenticationConverter;
import com.alejandro.mtoconfiguration.controller.commons.ConfigurationApiPaths;
import com.alejandro.mtoconfiguration.controller.synchronous.infraestructure.TrackAccessPointController;
import com.alejandro.mtoconfiguration.core.exception.RestExceptionHandler;
import com.alejandro.mtoconfiguration.core.exception.ValidationException;
import com.alejandro.mtoconfiguration.core.exception.web.ApiErrorConfiguration;
import com.alejandro.mtoconfiguration.core.exception.web.ErrorCatalog;
import com.alejandro.mtoconfiguration.core.exception.web.ProblemDetailFactory;
import com.alejandro.mtoconfiguration.enums.infrastructure.TrackAccessPointKind;
import com.alejandro.mtoconfiguration.enums.infrastructure.TrackAccessPointSide;
import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.TrackAccessPointDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.TrackAccessPointFilter;
import com.alejandro.mtoconfiguration.service.infraestructure.TrackAccessPointService;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
 * Contrato HTTP de {@code /track-access-points} (V28): las rutas y los códigos de los demás maestros
 * ({@code ProfileControllerTest} es la plantilla), la forma del JSON que leen el backoffice y
 * mto-frontend, y lo propio: el filtro y los de una vía.
 *
 * <p>La seguridad se apaga en el slice a propósito: las reglas son por verbo y las prueba
 * {@code ApiAuthorizationRulesTest}.</p>
 */
@WebMvcTest(controllers = TrackAccessPointController.class,
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
class TrackAccessPointControllerTest {

    private static final String ACCESS_POINTS = ConfigurationApiPaths.BASE_PATH + "/track-access-points";

    private static final String BODY = """
            {"name":"Acceso km 110+176","trackId":3,"stationId":40,"kp":110176.000,
             "kind":"PREPARED_ACCESS","side":"LEFT","roadRailAccess":true,
             "maxAxleLoadT":12.5,"maxGrossWeightT":40,"latitude":31.669342,"longitude":34.574119,
             "enabled":true}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TrackAccessPointService trackAccessPointService;

    private static TrackAccessPointDTO accessPoint(Long id) {
        TrackAccessPointDTO dto = new TrackAccessPointDTO();
        dto.setId(id);
        dto.setName("Acceso " + id);
        dto.setTrackId(3L);
        dto.setKp(new BigDecimal("110176.000"));
        dto.setKind(TrackAccessPointKind.LEVEL_CROSSING);
        dto.setSide(TrackAccessPointSide.BOTH);
        dto.setLatitude(new BigDecimal("31.669342"));
        dto.setLongitude(new BigDecimal("34.574119"));
        return dto;
    }

    @BeforeEach
    void setUp() {
        when(trackAccessPointService.create(any())).thenAnswer(i -> i.getArgument(0));
        when(trackAccessPointService.update(any())).thenAnswer(i -> i.getArgument(0));
        when(trackAccessPointService.bulkCreate(anyList())).thenAnswer(i -> i.getArgument(0));
        when(trackAccessPointService.bulkUpdate(anyList())).thenAnswer(i -> i.getArgument(0));
    }

    @Nested
    @DisplayName("Códigos de respuesta del CRUD")
    class CodigosDeRespuesta {

        @Test
        @DisplayName("consultar por id responde 200 con los enumerados por su nombre y los decimales como número")
        void getById() throws Exception {
            when(trackAccessPointService.getById(5L)).thenReturn(accessPoint(5L));

            mockMvc.perform(get(ACCESS_POINTS + "/5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(5))
                    .andExpect(jsonPath("$.trackId").value(3))
                    .andExpect(jsonPath("$.kind").value("LEVEL_CROSSING"))
                    .andExpect(jsonPath("$.side").value("BOTH"))
                    .andExpect(jsonPath("$.kp").value(110176.000))
                    .andExpect(jsonPath("$.latitude").value(31.669342))
                    .andExpect(jsonPath("$.roadRailAccess").value(true))
                    .andExpect(jsonPath("$.enabled").value(true));
        }

        @Test
        @DisplayName("crear responde 201 y el cuerpo llega entero al servicio")
        void create() throws Exception {
            mockMvc.perform(post(ACCESS_POINTS).contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("Acceso km 110+176"));

            ArgumentCaptor<TrackAccessPointDTO> captor = ArgumentCaptor.forClass(TrackAccessPointDTO.class);
            verify(trackAccessPointService).create(captor.capture());
            TrackAccessPointDTO dto = captor.getValue();
            assertThat(dto.getTrackId()).isEqualTo(3L);
            assertThat(dto.getStationId()).isEqualTo(40L);
            assertThat(dto.getKind()).isEqualTo(TrackAccessPointKind.PREPARED_ACCESS);
            assertThat(dto.getSide()).isEqualTo(TrackAccessPointSide.LEFT);
            assertThat(dto.getMaxAxleLoadT()).isEqualByComparingTo("12.5");
            assertThat(dto.getLongitude()).isEqualByComparingTo("34.574119");
        }

        @Test
        @DisplayName("un cuerpo sin roadRailAccess ni enabled los deja a true")
        void porDefecto() throws Exception {
            mockMvc.perform(post(ACCESS_POINTS).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"A\",\"trackId\":3,\"kind\":\"DEPOT\",\"side\":\"RIGHT\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.roadRailAccess").value(true))
                    .andExpect(jsonPath("$.enabled").value(true));
        }

        @Test
        @DisplayName("una clase que no existe es un 400, no un 500")
        void claseDesconocida() throws Exception {
            mockMvc.perform(post(ACCESS_POINTS).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"A\",\"trackId\":3,\"kind\":\"HELIPUERTO\",\"side\":\"RIGHT\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("crear en lote responde 201 con el array completo")
        void bulkCreate() throws Exception {
            mockMvc.perform(post(ACCESS_POINTS + "/bulk").contentType(MediaType.APPLICATION_JSON)
                            .content("[" + BODY + "," + BODY + "]"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        @DisplayName("modificar responde 200 y manda el id de la ruta, no el del cuerpo")
        void update() throws Exception {
            mockMvc.perform(put(ACCESS_POINTS + "/5").contentType(MediaType.APPLICATION_JSON)
                            .content(BODY.replace("{\"name\"", "{\"id\":999,\"name\"")))
                    .andExpect(status().isOk());

            ArgumentCaptor<TrackAccessPointDTO> captor = ArgumentCaptor.forClass(TrackAccessPointDTO.class);
            verify(trackAccessPointService).update(captor.capture());
            assertThat(captor.getValue().getId()).isEqualTo(5L);
        }

        @Test
        @DisplayName("modificar en lote responde 200")
        void bulkUpdate() throws Exception {
            mockMvc.perform(put(ACCESS_POINTS + "/bulk").contentType(MediaType.APPLICATION_JSON)
                            .content("[{\"id\":1},{\"id\":2}]"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        @DisplayName("borrar responde 204 sin cuerpo, con un DTO que solo lleva el id de la ruta")
        void deleteById() throws Exception {
            mockMvc.perform(delete(ACCESS_POINTS + "/7"))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));

            ArgumentCaptor<TrackAccessPointDTO> captor = ArgumentCaptor.forClass(TrackAccessPointDTO.class);
            verify(trackAccessPointService).delete(captor.capture());
            assertThat(captor.getValue().getId()).isEqualTo(7L);
        }
    }

    @Nested
    @DisplayName("Consultas")
    class Consultas {

        @Test
        @DisplayName("el filtro llega al servicio con sus enumerados y los textos ausentes como cadena vacía")
        void filtro() throws Exception {
            when(trackAccessPointService.getTrackAccessPoints(any(Pageable.class), any(TrackAccessPointFilter.class)))
                    .thenReturn(new PageImpl<>(List.of(accessPoint(1L))));

            mockMvc.perform(post(ACCESS_POINTS + "/filter").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"trackId\":3,\"kind\":\"PREPARED_ACCESS\",\"roadRailAccess\":true}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(1))
                    .andExpect(jsonPath("$.page.totalElements").value(1));

            ArgumentCaptor<TrackAccessPointFilter> captor = ArgumentCaptor.forClass(TrackAccessPointFilter.class);
            verify(trackAccessPointService).getTrackAccessPoints(any(Pageable.class), captor.capture());
            assertThat(captor.getValue().trackId()).isEqualTo(3L);
            assertThat(captor.getValue().kind()).isEqualTo(TrackAccessPointKind.PREPARED_ACCESS);
            assertThat(captor.getValue().roadRailAccess()).isTrue();
            assertThat(captor.getValue().enabled()).isNull();
            assertThat(captor.getValue().searchText()).isEmpty();
        }

        @Test
        @DisplayName("los de una vía responden 200 con la lista")
        void deUnaVia() throws Exception {
            when(trackAccessPointService.getTrackAccessPointsByTrackId(3L))
                    .thenReturn(List.of(accessPoint(1L), accessPoint(2L)));

            mockMvc.perform(get(ACCESS_POINTS + "/track/3"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }
    }

    @Test
    @DisplayName("un error de validación del servicio sale como 400 con su campo")
    void validacion() throws Exception {
        when(trackAccessPointService.create(any())).thenThrow(new ValidationException(
                List.of(Alert.ofDanger(ErrorCodes.VALIDATION_REQUIRED_FIELD, "longitude"))));

        mockMvc.perform(post(ACCESS_POINTS).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("longitude"));
    }
}
