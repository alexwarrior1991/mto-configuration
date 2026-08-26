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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Autorización de extremo a extremo contra un Keycloak real: los tokens los emite el servidor, con
 * su firma y sus claims, y la aplicación los valida y los traduce a permisos sin ningún doble por
 * medio.
 *
 * <p>Se complementa con {@code JwtValidationChainTest}, que cubre la misma cadena con tokens
 * firmados a mano y sin contenedores. Lo que añade este test es lo que aquel no puede comprobar: que
 * el <b>realm</b> esté bien montado. Concretamente, que el <i>audience mapper</i> exista —sin él
 * Keycloak no incluye esta API en el {@code aud} y ningún token validaría—, que los permisos vivan
 * como roles de cliente y que un rol compuesto de realm llegue expandido en {@code resource_access}.
 * Esas tres cosas son configuración del servidor, no código, y por eso solo se ven aquí.</p>
 *
 * <p>El realm de {@code src/test/resources/keycloak/mto-test-realm.json} tiene la misma forma que la
 * propuesta para los entornos reales, así que sirve además de ejemplo ejecutable de cómo hay que
 * configurarlo.</p>
 *
 * <p>Se monta un slice de MVC y no la aplicación entera a propósito: lo que se prueba es la cadena
 * de filtros y el mapeo de roles, y la capa de datos solo añadiría contenedores y tiempo.</p>
 */
@Testcontainers
@WebMvcTest(controllers = ApiAuthorizationRulesTest.ProbeController.class)
@AutoConfigureMockMvc
@Import({SecurityConfiguration.class, KeycloakJwtAuthenticationConverter.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        ProblemDetailFactory.class, ErrorCatalog.class, ApiErrorConfiguration.class,
        ApiAuthorizationRulesTest.ProbeController.class,
        ApiAuthorizationRulesTest.ProbeLovController.class})
class KeycloakAuthorizationIT {

    private static final String PROBES = ConfigurationApiPaths.BASE_PATH + "/probes";
    private static final String LOVS = ConfigurationApiPaths.BASE_PATH + "/probe-lovs";

    private static final String REALM = "mto";
    private static final String API_CLIENT_ID = "mto-configuration-api";
    private static final String CLIENTE_CON_AUDIENCIA = "mto-test-frontend";
    private static final String CLIENTE_SIN_AUDIENCIA = "mto-test-ajeno";

    private static final int PUERTO_HTTP = 8080;

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * {@code --import-realm} deja el realm listo al arrancar, así que no hace falta programar la
     * consola de administración desde el test. La sonda espera al documento de descubrimiento y no
     * a que el puerto acepte conexiones: Keycloak escucha bastante antes de servir el realm.
     */
    @Container
    static final GenericContainer<?> KEYCLOAK =
            new GenericContainer<>(DockerImageName.parse("quay.io/keycloak/keycloak:26.1"))
                    .withExposedPorts(PUERTO_HTTP)
                    .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
                    .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
                    .withCopyFileToContainer(
                            MountableFile.forClasspathResource("keycloak/mto-test-realm.json"),
                            "/opt/keycloak/data/import/mto-test-realm.json")
                    .withCommand("start-dev", "--import-realm")
                    .waitingFor(Wait.forHttp("/realms/" + REALM + "/.well-known/openid-configuration")
                            .forPort(PUERTO_HTTP)
                            .forStatusCode(200)
                            .withStartupTimeout(Duration.ofMinutes(5)));

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        // El emisor tiene que coincidir con el 'iss' que Keycloak escribe en el token, y Keycloak lo
        // deriva de la URL por la que se le pide: la misma que se usa aquí para pedirlo.
        registro.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                KeycloakAuthorizationIT::urlDelRealm);

        registro.add("app.security.client-id", () -> API_CLIENT_ID);
        registro.add("app.security.principal-claim", () -> "preferred_username");
        registro.add("app.security.audience-validation-enabled", () -> "true");
        registro.add("app.security.required-audience", () -> API_CLIENT_ID);
        registro.add("app.security.expose-api-docs", () -> "false");
        registro.add("app.security.cors.allowed-origins", () -> "http://localhost:4200");
        registro.add("app.security.cors.allowed-methods", () -> "GET,POST,DELETE");
        registro.add("app.security.cors.allowed-headers", () -> "Authorization,Content-Type");
        registro.add("app.security.cors.allow-credentials", () -> "false");
        registro.add("app.security.cors.max-age", () -> "3600");
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("un permiso de cliente abre su verbo y solo el suyo")
    void unPermisoDeClienteAbreSuVerbo() throws Exception {
        String token = tokenDe("lector", "lector", CLIENTE_CON_AUDIENCIA);

        mockMvc.perform(get(PROBES + "/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post(PROBES)
                        .contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    /**
     * Keycloak expande los roles compuestos al emitir el token. Es lo que sostiene el modelo: los
     * perfiles se diseñan en el realm y el código solo comprueba permisos.
     */
    @Test
    @DisplayName("un perfil compuesto de realm llega con sus permisos de cliente expandidos")
    void unPerfilCompuestoLlegaExpandido() throws Exception {
        String token = tokenDe("editor", "editor", CLIENTE_CON_AUDIENCIA);

        mockMvc.perform(get(PROBES + "/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post(PROBES)
                        .contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        // El perfil no incluye config-delete ni lov-manage.
        mockMvc.perform(delete(PROBES + "/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(LOVS)
                        .contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    /**
     * S-12 contra un servidor real. El usuario tiene un rol <em>de realm</em> llamado
     * {@code config-delete}, igual que el permiso de cliente. Quien administra el realm no es
     * necesariamente quien escribe el código, así que crear ese rol no puede equivaler a conceder el
     * permiso.
     */
    @Test
    @DisplayName("un rol de realm homónimo de un permiso no concede ese permiso")
    void unRolDeRealmHomonimoNoConcedeElPermiso() throws Exception {
        String token = tokenDe("impostor", "impostor", CLIENTE_CON_AUDIENCIA);

        mockMvc.perform(delete(PROBES + "/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    /**
     * S-04 contra un servidor real, y la razón por la que el realm necesita un <i>audience
     * mapper</i>: el cliente {@code mto-test-ajeno} no lo tiene, así que sus tokens —bien firmados y
     * del mismo emisor— no nombran a esta API.
     */
    @Test
    @DisplayName("un token de otro cliente del mismo realm se rechaza por audiencia")
    void unTokenDeOtroClienteSeRechaza() throws Exception {
        String token = tokenDe("lector", "lector", CLIENTE_SIN_AUDIENCIA);

        mockMvc.perform(get(PROBES + "/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("sin token la respuesta sigue siendo 401 con su desafío Bearer")
    void sinTokenSigueSiendo401() throws Exception {
        mockMvc.perform(get(PROBES + "/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(result -> assertThat(
                        result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE))
                        .startsWith("Bearer"));
    }

    // --- obtención de tokens reales ---

    private static String urlDelRealm() {
        return "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(PUERTO_HTTP)
                + "/realms/" + REALM;
    }

    /**
     * Se usa el <i>grant</i> de acceso directo por comodidad del test: es la forma más corta de
     * conseguir un token de un usuario concreto sin simular un navegador. No es la que debe usar la
     * aplicación.
     */
    private String tokenDe(String usuario, String contrasena, String clientId) throws Exception {
        String cuerpo = "grant_type=password"
                + "&client_id=" + codificar(clientId)
                + "&username=" + codificar(usuario)
                + "&password=" + codificar(contrasena)
                + "&scope=openid";

        HttpRequest peticion = HttpRequest.newBuilder()
                .uri(URI.create(urlDelRealm() + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                .build();

        HttpResponse<String> respuesta = HttpClient.newHttpClient()
                .send(peticion, HttpResponse.BodyHandlers.ofString());

        assertThat(respuesta.statusCode())
                .withFailMessage("Keycloak no emitió token para '%s' con el cliente '%s': %s",
                        usuario, clientId, respuesta.body())
                .isEqualTo(200);

        return JSON.readValue(respuesta.body(), Map.class).get("access_token").toString();
    }

    private static String codificar(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }
}
