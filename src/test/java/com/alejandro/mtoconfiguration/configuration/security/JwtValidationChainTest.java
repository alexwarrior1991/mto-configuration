package com.alejandro.mtoconfiguration.configuration.security;

import com.alejandro.mtoconfiguration.controller.commons.ConfigurationApiPaths;
import com.alejandro.mtoconfiguration.core.exception.web.ApiErrorConfiguration;
import com.alejandro.mtoconfiguration.core.exception.web.ErrorCatalog;
import com.alejandro.mtoconfiguration.core.exception.web.ProblemDetailFactory;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
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

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Valida la cadena de comprobación del token con tokens <b>de verdad</b>: firmados con una clave
 * real y verificados contra un JWK Set real servido por HTTP.
 *
 * <p>El resto de tests de autorización usan {@code jwt()}, que inyecta la autenticación ya resuelta
 * y por tanto <em>se salta</em> justo lo que aquí se prueba: la descarga del JWK Set, la
 * verificación de la firma, la validación del emisor y la de la audiencia. Sin esto, un fallo en
 * cualquiera de esas cuatro piezas —que la audiencia dejara de comprobarse, por ejemplo— no rompería
 * ningún test.</p>
 *
 * <p>No sustituye a un test contra Keycloak, que además comprobaría que el realm está bien montado;
 * cubre la parte que es responsabilidad de esta aplicación.</p>
 */
@WebMvcTest(controllers = ApiAuthorizationRulesTest.ProbeController.class)
@AutoConfigureMockMvc
@Import({SecurityConfiguration.class, KeycloakJwtAuthenticationConverter.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        ProblemDetailFactory.class, ErrorCatalog.class, ApiErrorConfiguration.class,
        ApiAuthorizationRulesTest.ProbeController.class})
class JwtValidationChainTest {

    private static final String PROBES = ConfigurationApiPaths.BASE_PATH + "/probes";
    private static final String EMISOR = "https://auth.mto.local/realms/mto";
    private static final String AUDIENCIA = "mto-configuration-api";
    private static final String CLIENTE = "mto-configuration-api";

    private static final RSAKey CLAVE = generarClave();
    private static final HttpServer SERVIDOR_JWKS = arrancarServidorDeClaves();

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> EMISOR);
        registro.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://127.0.0.1:" + SERVIDOR_JWKS.getAddress().getPort() + "/jwks");

        registro.add("app.security.client-id", () -> CLIENTE);
        registro.add("app.security.principal-claim", () -> "preferred_username");
        registro.add("app.security.audience-validation-enabled", () -> "true");
        registro.add("app.security.required-audience", () -> AUDIENCIA);
        registro.add("app.security.expose-api-docs", () -> "false");
        registro.add("app.security.cors.allowed-origins", () -> "http://localhost:4200");
        registro.add("app.security.cors.allowed-methods", () -> "GET,POST");
        registro.add("app.security.cors.allowed-headers", () -> "Authorization");
        registro.add("app.security.cors.allow-credentials", () -> "false");
        registro.add("app.security.cors.max-age", () -> "3600");
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("un token bien firmado con el permiso adecuado entra")
    void unTokenValidoEntra() throws Exception {
        String token = token(claims -> claims
                .audience(AUDIENCIA)
                .claim(JwtClaimNames.RESOURCE_ACCESS,
                        Map.of(CLIENTE, Map.of("roles", List.of("config-read")))));

        mockMvc.perform(get(PROBES + "/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    /**
     * La comprobación que protege S-04. Un token emitido por el mismo realm para otra aplicación
     * está igual de bien firmado y lleva el mismo emisor: lo único que lo separa es la audiencia.
     */
    @Test
    @DisplayName("un token del mismo realm pero para otra aplicación se rechaza")
    void unTokenParaOtraAplicacionSeRechaza() throws Exception {
        String token = token(claims -> claims
                .audience("mto-stock-api")
                .claim(JwtClaimNames.RESOURCE_ACCESS,
                        Map.of(CLIENTE, Map.of("roles", List.of("config-read")))));

        mockMvc.perform(get(PROBES + "/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("un token de otro emisor se rechaza aunque la firma sea válida")
    void unTokenDeOtroEmisorSeRechaza() throws Exception {
        String token = token(claims -> claims
                .issuer("https://auth.otro-dominio/realms/mto")
                .audience(AUDIENCIA)
                .claim(JwtClaimNames.RESOURCE_ACCESS,
                        Map.of(CLIENTE, Map.of("roles", List.of("config-read")))));

        mockMvc.perform(get(PROBES + "/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("un token firmado con otra clave se rechaza")
    void unTokenFirmadoConOtraClaveSeRechaza() throws Exception {
        RSAKey claveIntrusa = generarClave();

        String token = firmar(claveIntrusa, base()
                .audience(AUDIENCIA)
                .claim(JwtClaimNames.RESOURCE_ACCESS,
                        Map.of(CLIENTE, Map.of("roles", List.of("config-read"))))
                .build());

        mockMvc.perform(get(PROBES + "/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("un token caducado se rechaza")
    void unTokenCaducadoSeRechaza() throws Exception {
        String token = token(claims -> claims
                .audience(AUDIENCIA)
                .expirationTime(Date.from(Instant.now().minusSeconds(600)))
                .claim(JwtClaimNames.RESOURCE_ACCESS,
                        Map.of(CLIENTE, Map.of("roles", List.of("config-read")))));

        mockMvc.perform(get(PROBES + "/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    /**
     * S-12 de extremo a extremo: el rol viaja en {@code realm_access} y se llama igual que un
     * permiso. Con el mapeo anterior habría concedido {@code ROLE_CONFIG_DELETE}.
     */
    @Test
    @DisplayName("un rol de realm homónimo de un permiso no abre el borrado")
    void unRolDeRealmHomonimoNoAbreElBorrado() throws Exception {
        String token = token(claims -> claims
                .audience(AUDIENCIA)
                .claim(JwtClaimNames.REALM_ACCESS, Map.of("roles", List.of("config-delete"))));

        mockMvc.perform(delete(PROBES + "/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("los permisos de cliente sí se aplican por verbo")
    void losPermisosDeClienteSeAplicanPorVerbo() throws Exception {
        String token = token(claims -> claims
                .audience(AUDIENCIA)
                .claim(JwtClaimNames.RESOURCE_ACCESS,
                        Map.of(CLIENTE, Map.of("roles", List.of("config-write")))));

        mockMvc.perform(post(PROBES)
                        .contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get(PROBES + "/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // --- utilidades de firma ---

    private interface Ajuste {
        JWTClaimsSet.Builder aplicar(JWTClaimsSet.Builder builder);
    }

    private String token(Ajuste ajuste) throws Exception {
        return firmar(CLAVE, ajuste.aplicar(base()).build());
    }

    private static JWTClaimsSet.Builder base() {
        return new JWTClaimsSet.Builder()
                .issuer(EMISOR)
                .subject("6f1b1c8e-0000-4000-8000-000000000001")
                .issueTime(Date.from(Instant.now().minusSeconds(30)))
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .claim(JwtClaimNames.PREFERRED_USERNAME, "ana.perez")
                .claim(JwtClaimNames.SCOPE, "openid profile");
    }

    private static String firmar(RSAKey clave, JWTClaimsSet claims) throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(clave.getKeyID())
                        .type(JOSEObjectType.JWT)
                        .build(),
                claims);

        jwt.sign(new RSASSASigner(clave));

        return jwt.serialize();
    }

    private static RSAKey generarClave() {
        try {
            return new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar la clave de firma", e);
        }
    }

    /** Publica la parte pública de la clave donde el decodificador irá a buscarla. */
    private static HttpServer arrancarServidorDeClaves() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

            servidor.createContext("/jwks", intercambio -> {
                byte[] cuerpo = new JWKSet(CLAVE.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
                intercambio.getResponseHeaders().add("Content-Type", "application/json");
                intercambio.sendResponseHeaders(200, cuerpo.length);

                try (OutputStream salida = intercambio.getResponseBody()) {
                    salida.write(cuerpo);
                }
            });

            servidor.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> servidor.stop(0)));

            return servidor;
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo publicar el JWK Set de prueba", e);
        }
    }
}
