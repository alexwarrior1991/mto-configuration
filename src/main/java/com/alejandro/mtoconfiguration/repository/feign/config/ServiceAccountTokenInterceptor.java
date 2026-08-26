package com.alejandro.mtoconfiguration.repository.feign.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

/**
 * Pone el token de la cuenta de servicio en cada llamada saliente a otro servicio del mismo realm.
 *
 * <p>El token lo pide el {@link OAuth2AuthorizedClientManager} de Spring Security, que lo guarda y
 * lo renueva antes de que caduque. La versión anterior pedía uno nuevo al endpoint de Keycloak en
 * <em>cada</em> invocación: convertía a Keycloak en un punto de fallo síncrono de toda llamada
 * saliente y duplicaba su latencia.</p>
 *
 * <p>El principal es fijo y sintético a propósito. En {@code client_credentials} el token representa
 * al servicio, no a la persona que originó la petición, así que una sola entrada en caché sirve para
 * toda la aplicación —incluidos los hilos de fondo, donde no hay ningún usuario en el contexto—. Si
 * en algún caso hiciera falta actuar en nombre del usuario que originó la llamada, la vía es
 * <em>token exchange</em>, no reutilizar sus credenciales.</p>
 */
@Slf4j
public class ServiceAccountTokenInterceptor implements RequestInterceptor {

    private final OAuth2AuthorizedClientManager authorizedClientManager;

    private final String registrationId;

    private final Authentication principal;

    public ServiceAccountTokenInterceptor(
            OAuth2AuthorizedClientManager authorizedClientManager,
            String registrationId
    ) {
        this.authorizedClientManager = authorizedClientManager;
        this.registrationId = registrationId;
        this.principal = new UsernamePasswordAuthenticationToken(
                registrationId, null, AuthorityUtils.NO_AUTHORITIES);
    }

    @Override
    public void apply(RequestTemplate template) {
        // Respeta una cabecera puesta a mano: hay llamadas que necesitan propagar otro token.
        if (template.headers().containsKey(HttpHeaders.AUTHORIZATION)) {
            return;
        }

        OAuth2AuthorizedClient authorizedClient = authorizedClientManager.authorize(
                OAuth2AuthorizeRequest.withClientRegistrationId(registrationId)
                        .principal(principal)
                        .build());

        // Antes, un fallo al obtener el token producía la cabecera literal "null null" y el error
        // aparecía como un 401 del servicio remoto. Es un fallo de autenticación local y se dice.
        if (authorizedClient == null || authorizedClient.getAccessToken() == null) {
            throw new ServiceAccountTokenException(registrationId);
        }

        template.header(
                HttpHeaders.AUTHORIZATION,
                "Bearer " + authorizedClient.getAccessToken().getTokenValue());
    }
}
