package com.alejandro.mtoconfiguration.configuration.security;

import com.alejandro.mtoconfiguration.controller.commons.ConfigurationApiPaths;
import com.alejandro.mtoconfiguration.core.exception.web.ApiErrorConfiguration;
import com.alejandro.mtoconfiguration.core.exception.web.ErrorCatalog;
import com.alejandro.mtoconfiguration.core.exception.web.ProblemDetailFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Permisos de los endpoints de trabajos.
 *
 * <p>Las rutas nuevas cuelgan de {@code /profiles/jobs/...}, dos segmentos por debajo de donde
 * miran los patrones de siempre, asi que sin entradas propias habrian caido en la regla general del
 * verbo: <b>bastaria {@code config-write} para lanzar una carga masiva</b>, que es justo la
 * distincion que el permiso de carga existe para mantener.</p>
 *
 * <p>Se prueba contra controladores sonda montados en rutas con la misma forma que las reales, como
 * el resto de pruebas de la cadena de filtros: lo que se verifica son los patrones, y arrastrar la
 * capa de servicio solo anadiria ruido.</p>
 */
@WebMvcTest(controllers = ProfileJobAuthorizationTest.ProbeJobController.class)
@AutoConfigureMockMvc
@Import({SecurityConfiguration.class, KeycloakJwtAuthenticationConverter.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        ProblemDetailFactory.class, ErrorCatalog.class, ApiErrorConfiguration.class,
        ProfileJobAuthorizationTest.ProbeJobController.class})
@TestPropertySource(properties = {
        "app.security.client-id=mto-configuration-api",
        "app.security.principal-claim=preferred_username",
        "app.security.audience-validation-enabled=false",
        "app.security.expose-api-docs=false",
        "app.security.cors.allowed-origins=http://localhost:4200",
        "app.security.cors.allowed-methods=GET,POST,PUT,DELETE",
        "app.security.cors.allowed-headers=Authorization,Content-Type",
        "app.security.cors.allow-credentials=false",
        "app.security.cors.max-age=3600",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8082/realms/mto"
})
class ProfileJobAuthorizationTest {

    private static final String JOBS = ConfigurationApiPaths.BASE_PATH + "/probes/jobs";

    @Autowired
    private MockMvc mockMvc;

    private RequestPostProcessor withRoles(String... roles) {
        return jwt().authorities(AuthorityUtils.createAuthorityList(
                java.util.Arrays.stream(roles).map(role -> "ROLE_" + role).toArray(String[]::new)));
    }

    @Test
    @DisplayName("lanzar una exportacion pide lectura, como el export de siempre")
    void exportacionPideLectura() throws Exception {
        mockMvc.perform(post(JOBS + "/export").with(withRoles(SecurityRoles.CONFIG_READ)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("sin ningun permiso, la exportacion se deniega")
    void exportacionSinPermiso() throws Exception {
        mockMvc.perform(post(JOBS + "/export").with(withRoles("OTRO_ROL")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("las cargas masivas como trabajo piden el permiso de carga, no el de escritura")
    void cargasPidenPermisoDeCarga() throws Exception {
        mockMvc.perform(post(JOBS + "/bulk-create")
                        .with(withRoles(SecurityRoles.CONFIG_IMPORT))
                        .contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isOk());

        mockMvc.perform(post(JOBS + "/bulk-update")
                        .with(withRoles(SecurityRoles.CONFIG_IMPORT))
                        .contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isOk());

        // Poder escribir registro a registro no debe bastar para cargar en masa: un error aqui
        // afecta a miles de filas de una vez.
        mockMvc.perform(post(JOBS + "/bulk-create")
                        .with(withRoles(SecurityRoles.CONFIG_WRITE))
                        .contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(JOBS + "/bulk-update")
                        .with(withRoles(SecurityRoles.CONFIG_WRITE))
                        .contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("consultar el estado y descargar el fichero piden lectura")
    void seguimientoPideLectura() throws Exception {
        mockMvc.perform(get(JOBS + "/11111111-2222-3333-4444-555555555555")
                        .with(withRoles(SecurityRoles.CONFIG_READ)))
                .andExpect(status().isOk());

        mockMvc.perform(get(JOBS + "/11111111-2222-3333-4444-555555555555/file")
                        .with(withRoles(SecurityRoles.CONFIG_READ)))
                .andExpect(status().isOk());

        mockMvc.perform(get(JOBS + "/11111111-2222-3333-4444-555555555555")
                        .with(withRoles(SecurityRoles.CONFIG_WRITE)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("sin token no se llega a ningun endpoint de trabajos")
    void sinTokenNoHayNada() throws Exception {
        mockMvc.perform(post(JOBS + "/export")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(JOBS + "/11111111-2222-3333-4444-555555555555"))
                .andExpect(status().isUnauthorized());
    }

    @RestController
    @RequestMapping(ConfigurationApiPaths.BASE_PATH + "/probes/jobs")
    static class ProbeJobController {

        @PostMapping("/export")
        String startExport() {
            return "ok";
        }

        @PostMapping("/bulk-create")
        String startBulkCreate() {
            return "ok";
        }

        @PostMapping("/bulk-update")
        String startBulkUpdate() {
            return "ok";
        }

        @GetMapping("/{jobId}")
        String getJob(@PathVariable String jobId) {
            return jobId;
        }

        @GetMapping("/{jobId}/file")
        String downloadFile(@PathVariable String jobId) {
            return jobId;
        }
    }
}
