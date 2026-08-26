package com.alejandro.mtoconfiguration.repository.feign;

import com.alejandro.mtoconfiguration.repository.feign.config.ServiceAccountTokenException;
import com.alejandro.mtoconfiguration.repository.feign.config.ServiceAccountTokenInterceptor;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import feign.Feign;
import feign.Param;
import feign.RequestLine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ejercita la cadena entera de una llamada saliente —cliente Feign, interceptor, gestor de clientes
 * autorizados y endpoint de token— contra un servidor simulado, sin depender de que el servicio
 * destino exista todavía.
 *
 * <p>Es lo que sustituye al mecanismo anterior, que pedía un token por invocación mediante el
 * <i>grant</i> {@code password} con usuario y contraseña de servicio guardados en configuración.</p>
 */
class ServiceAccountTokenInterceptorTest {

    private static final String REGISTRATION_ID = "mto-services";

    private HttpServer servidor;
    private String urlBase;

    /** Cuántas veces se ha pedido un token: es lo que demuestra que hay caché. */
    private final AtomicInteger tokensEmitidos = new AtomicInteger();

    /** Cabecera Authorization que ha recibido el servicio destino. */
    private volatile String autorizacionRecibida;

    interface ServicioRemoto {
        @RequestLine("GET /recursos/{id}")
        String obtener(@Param("id") long id);
    }

    @BeforeEach
    void levantarServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        servidor.createContext("/token", intercambio -> {
            tokensEmitidos.incrementAndGet();
            responder(intercambio, 200, """
                    {"access_token":"token-de-servicio","token_type":"Bearer","expires_in":300}""");
        });

        servidor.createContext("/recursos/", intercambio -> {
            autorizacionRecibida = intercambio.getRequestHeaders().getFirst("Authorization");
            responder(intercambio, 200, "ok");
        });

        servidor.start();
        urlBase = "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    @AfterEach
    void pararServidor() {
        servidor.stop(0);
    }

    @Test
    @DisplayName("la llamada saliente viaja con el token de la cuenta de servicio")
    void laLlamadaVaConElTokenDeLaCuentaDeServicio() {
        ServicioRemoto cliente = clienteCon(gestorDeTokens());

        assertThat(cliente.obtener(1)).isEqualTo("ok");
        assertThat(autorizacionRecibida).isEqualTo("Bearer token-de-servicio");
    }

    /**
     * La regresión que motivaba el cambio: el interceptor anterior pedía un token nuevo al endpoint
     * de Keycloak en cada invocación, sin caché ni control de expiración.
     */
    @Test
    @DisplayName("varias llamadas reutilizan el mismo token: no se pide uno por invocación")
    void variasLlamadasReutilizanElMismoToken() {
        ServicioRemoto cliente = clienteCon(gestorDeTokens());

        cliente.obtener(1);
        cliente.obtener(2);
        cliente.obtener(3);

        assertThat(tokensEmitidos).hasValue(1);
    }

    @Test
    @DisplayName("una cabecera Authorization ya puesta se respeta")
    void unaCabeceraYaPuestaSeRespeta() {
        ServicioRemoto cliente = Feign.builder()
                .requestInterceptor(template -> template.header("Authorization", "Bearer token-propagado"))
                .requestInterceptor(new ServiceAccountTokenInterceptor(gestorDeTokens(), REGISTRATION_ID))
                .target(ServicioRemoto.class, urlBase);

        cliente.obtener(1);

        assertThat(autorizacionRecibida).isEqualTo("Bearer token-propagado");
        assertThat(tokensEmitidos).hasValue(0);
    }

    /**
     * Antes, un fallo al obtener el token dejaba la cabecera literal {@code "null null"} y el error
     * se manifestaba como un 401 del servicio remoto, lejos de su causa.
     */
    @Test
    @DisplayName("si no hay token la llamada falla aquí, no como un 401 del servicio remoto")
    void siNoHayTokenLaLlamadaFallaAqui() {
        // El endpoint de token apunta a un puerto donde no escucha nadie.
        ServicioRemoto cliente = clienteCon(gestorDeTokensCon("http://127.0.0.1:1/token"));

        assertThatThrownBy(() -> cliente.obtener(1))
                .isInstanceOfAny(ServiceAccountTokenException.class, RuntimeException.class)
                .hasMessageNotContainingAny("null null");

        assertThat(autorizacionRecibida).isNull();
    }

    private ServicioRemoto clienteCon(OAuth2AuthorizedClientManager gestor) {
        return Feign.builder()
                .requestInterceptor(new ServiceAccountTokenInterceptor(gestor, REGISTRATION_ID))
                .target(ServicioRemoto.class, urlBase);
    }

    private OAuth2AuthorizedClientManager gestorDeTokens() {
        return gestorDeTokensCon(urlBase + "/token");
    }

    private OAuth2AuthorizedClientManager gestorDeTokensCon(String tokenUri) {
        ClientRegistration registro = ClientRegistration.withRegistrationId(REGISTRATION_ID)
                .tokenUri(tokenUri)
                .clientId("mto-configuration-svc")
                .clientSecret("secreto-de-prueba")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .build();

        ClientRegistrationRepository registros = new InMemoryClientRegistrationRepository(registro);

        AuthorizedClientServiceOAuth2AuthorizedClientManager gestor =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                        registros, new InMemoryOAuth2AuthorizedClientService(registros));

        gestor.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .build());

        return gestor;
    }

    private static void responder(HttpExchange intercambio, int estado, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().add("Content-Type", "application/json");
        intercambio.sendResponseHeaders(estado, bytes.length);

        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(bytes);
        }
    }
}
