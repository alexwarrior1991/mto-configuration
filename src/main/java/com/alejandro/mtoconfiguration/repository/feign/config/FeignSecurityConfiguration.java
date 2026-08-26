package com.alejandro.mtoconfiguration.repository.feign.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/**
 * Autenticación de las llamadas salientes a otros servicios del dominio MTO, que se autentican
 * contra el mismo Keycloak.
 *
 * <p>Todo lo que hay aquí es condicional a que exista un {@link ClientRegistrationRepository}, es
 * decir, a que alguien haya configurado un cliente bajo
 * {@code spring.security.oauth2.client.registration}. Sin esa configuración no se crea nada y la
 * aplicación arranca igual: la integración con otros servicios no es obligatoria para funcionar.</p>
 *
 * <h2>Cómo se añade un cliente</h2>
 *
 * <p>Spring Cloud OpenFeign aplica <b>todos</b> los {@code RequestInterceptor} del contexto a todos
 * los clientes, así que declarar la interfaz basta: el token viaja solo.</p>
 *
 * <pre>{@code
 * @FeignClient(name = "mto-stock", url = "${mto.stock.url}")
 * public interface StockClient {
 *
 *     @GetMapping("/api/v1/stock/materials/{id}")
 *     MaterialDTO obtenerMaterial(@PathVariable Long id);
 * }
 * }</pre>
 *
 * <p>Ese automatismo tiene una contrapartida que conviene tener presente: si algún día se añade un
 * cliente Feign hacia un servicio <em>ajeno</em> a este realm, también recibiría el token de la
 * cuenta de servicio. Enviar un token a quien no debería verlo es una fuga de credencial, así que en
 * ese caso hay que darle al cliente su propia configuración sin este interceptor.</p>
 */
@Configuration
@EnableFeignClients(basePackages = "com.alejandro.mtoconfiguration.repository.feign")
public class FeignSecurityConfiguration {

    /** Identificador del cliente de Keycloak que representa a este servicio. */
    public static final String SERVICE_REGISTRATION_ID = "mto-services";

    /**
     * Se usa el manager basado en {@link OAuth2AuthorizedClientService} y no el ligado a la petición
     * HTTP: las llamadas salientes también salen de hilos de fondo —el publicador del outbox, un
     * listener de RabbitMQ— donde no hay ninguna petición en curso.
     */
    @Bean
    @ConditionalOnBean(ClientRegistrationRepository.class)
    @ConditionalOnMissingBean(OAuth2AuthorizedClientManager.class)
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrations,
            OAuth2AuthorizedClientService authorizedClients
    ) {
        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(clientRegistrations, authorizedClients);

        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .build());

        return manager;
    }

    @Bean
    @ConditionalOnBean(OAuth2AuthorizedClientManager.class)
    public ServiceAccountTokenInterceptor serviceAccountTokenInterceptor(
            OAuth2AuthorizedClientManager authorizedClientManager
    ) {
        return new ServiceAccountTokenInterceptor(authorizedClientManager, SERVICE_REGISTRATION_ID);
    }
}
