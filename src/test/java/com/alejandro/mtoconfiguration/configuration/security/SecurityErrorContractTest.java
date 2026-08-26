package com.alejandro.mtoconfiguration.configuration.security;

import com.alejandro.mtoconfiguration.controller.commons.ConfigurationApiPaths;
import com.alejandro.mtoconfiguration.core.exception.web.ApiErrorConfiguration;
import com.alejandro.mtoconfiguration.core.exception.web.ErrorCatalog;
import com.alejandro.mtoconfiguration.core.exception.web.ProblemDetailFactory;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Contrato de las respuestas 401 y 403.
 *
 * <p>Antes, los handlers de seguridad serializaban un mapa propio
 * ({@code timestamp}/{@code status}/{@code error}/{@code message}/{@code path}) mientras el resto de
 * la API respondía en RFC 9457. Un 403 lanzado por la cadena de filtros y otro lanzado por un
 * {@code @PreAuthorize} salían con cuerpos incompatibles, y el cliente necesitaba dos parsers para
 * el mismo rechazo. Aquí se fija que hay un solo formato y que la cabecera
 * {@code WWW-Authenticate} permite distinguir «no hay token» de «el token no vale».</p>
 */
@WebMvcTest(controllers = ApiAuthorizationRulesTest.ProbeController.class)
@AutoConfigureMockMvc
@Import({SecurityConfiguration.class, KeycloakJwtAuthenticationConverter.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        ProblemDetailFactory.class, ErrorCatalog.class, ApiErrorConfiguration.class,
        ApiAuthorizationRulesTest.ProbeController.class,
        ApiAuthorizationRulesTest.ProbeLovController.class})
@TestPropertySource(properties = {
        "app.security.client-id=mto-configuration-api",
        "app.security.principal-claim=preferred_username",
        "app.security.audience-validation-enabled=false",
        "app.security.expose-api-docs=false",
        "app.security.cors.allowed-origins=http://localhost:4200",
        "app.security.cors.allowed-methods=GET,POST",
        "app.security.cors.allowed-headers=Authorization,Content-Type",
        "app.security.cors.allow-credentials=false",
        "app.security.cors.max-age=3600",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8082/realms/mto"
})
class SecurityErrorContractTest {

    private static final String PROBES = ConfigurationApiPaths.BASE_PATH + "/probes";
    private static final String LOVS = ConfigurationApiPaths.BASE_PATH + "/probe-lovs";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("el 401 sale en Problem Details, no en el formato antiguo")
    void el401SaleEnProblemDetails() throws Exception {
        mockMvc.perform(get(PROBES + "/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED))
                .andExpect(jsonPath("$.title").value("Acceso denegado"))
                .andExpect(jsonPath("$.instance").value(PROBES + "/1"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.type").isNotEmpty())
                // Campos del formato anterior: si reaparecen, es que alguien ha vuelto atrás.
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.path").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    @DisplayName("el 403 sale en Problem Details con su propio código")
    void el403SaleEnProblemDetails() throws Exception {
        mockMvc.perform(get(PROBES + "/1").with(con(SecurityRoles.CONFIG_WRITE)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value(ErrorCodes.FORBIDDEN))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.message").doesNotExist());
    }

    /**
     * El caso que motivaba S-09: son dos caminos distintos —uno muere en la cadena de filtros y el
     * otro dentro del controlador— para el mismo rechazo.
     */
    @Test
    @DisplayName("el 403 del filtro y el de @PreAuthorize tienen la misma forma")
    void losDos403TienenLaMismaForma() throws Exception {
        MvcResult delFiltro = mockMvc.perform(get(PROBES + "/1").with(con(SecurityRoles.CONFIG_WRITE)))
                .andExpect(status().isForbidden())
                .andReturn();

        // Con config-write pasa la regla del verbo y cae en el @PreAuthorize de la clase base.
        MvcResult deMetodo = mockMvc.perform(post(LOVS)
                        .contentType(MediaType.APPLICATION_JSON).content("{}")
                        .with(con(SecurityRoles.CONFIG_WRITE)))
                .andExpect(status().isForbidden())
                .andReturn();

        assertThat(deMetodo.getResponse().getContentType())
                .isEqualTo(delFiltro.getResponse().getContentType());
        assertThat(camposDe(deMetodo)).isEqualTo(camposDe(delFiltro));
    }

    /**
     * Lo que se fija es la <em>ausencia</em> de {@code error}, que es lo que separa «no has traído
     * token» de «tu token no vale». Spring Security 7 añade además un parámetro
     * {@code resource_metadata} (RFC 9728) que apunta a
     * {@code /.well-known/oauth-protected-resource}; la aplicación no publica ese documento, así que
     * no se afirma nada sobre él aquí.
     */
    @Test
    @DisplayName("sin token el desafío no lleva error: no hay nada que refrescar")
    void sinTokenElDesafioNoLlevaError() throws Exception {
        MvcResult resultado = mockMvc.perform(get(PROBES + "/1"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        String desafio = resultado.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE);

        assertThat(desafio)
                .startsWith("Bearer")
                .doesNotContain("error=");
    }

    /**
     * Es la mitad útil de S-10: sin este {@code error} el frontal no puede distinguir un token que
     * hay que refrescar de unas credenciales que hay que volver a pedir, y acaba tratando todos los
     * 401 igual.
     */
    @Test
    @DisplayName("con un token ilegible la cabecera lo dice: invalid_token")
    void conTokenIlegibleLaCabeceraLoDice() throws Exception {
        MvcResult resultado = mockMvc.perform(get(PROBES + "/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer esto-no-es-un-jwt"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        String desafio = resultado.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE);

        assertThat(desafio)
                .startsWith("Bearer ")
                .contains("error=\"invalid_token\"")
                .contains("error_description=");
    }

    @Test
    @DisplayName("el 403 anuncia insufficient_scope, no invalid_token")
    void el403AnunciaInsufficientScope() throws Exception {
        MvcResult resultado = mockMvc.perform(get(PROBES + "/1").with(con(SecurityRoles.CONFIG_WRITE)))
                .andExpect(status().isForbidden())
                .andReturn();

        assertThat(resultado.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE))
                .contains("error=\"insufficient_scope\"");
    }

    /** Nombres de campo del cuerpo, que es lo que tiene que coincidir entre ambos caminos. */
    private static java.util.Set<String> camposDe(MvcResult resultado) throws Exception {
        var arbol = new tools.jackson.databind.ObjectMapper()
                .readTree(resultado.getResponse().getContentAsString());

        return new java.util.TreeSet<>(arbol.propertyNames());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor con(String... roles) {
        String[] autoridades = java.util.Arrays.stream(roles).map(rol -> "ROLE_" + rol).toArray(String[]::new);
        return jwt().authorities(AuthorityUtils.createAuthorityList(autoridades));
    }
}
