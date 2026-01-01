package com.alejandro.mtoconfiguration.repository.feign.config;

import com.alejandro.mtoconfiguration.repository.feign.token.InternalClientFeignToken;
import com.alejandro.mtoconfiguration.repository.feign.token.config.InternalClientFeignTokenConfig;
import com.alejandro.mtoconfiguration.repository.feign.token.model.KeycloakTokenResponse;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.http.HttpHeaders;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Configuration class for Keycloak authentication using Feign interceptors. This class
 * implements the {@link RequestInterceptor} interface to inject an Authorization header
 * into outgoing HTTP requests based on the token response from the Keycloak server.
 *
 * The {@code KeycloakAuthConfiguration} class interacts with the following components:
 *
 * - {@link InternalClientFeignToken}: Feign client interface responsible for making
 *   requests to Keycloak's token endpoint to retrieve access tokens.
 * - {@link InternalClientFeignTokenConfig}: Configuration class that holds the client
 *   credentials, grant type, and other properties needed to obtain tokens from Keycloak.
 *
 * The `apply` method retrieves an access token by using the configured client details,
 * and adds the token as a Bearer token in the Authorization header of outgoing requests
 * if such a header is not already present.
 *
 * Responsibilities:
 * 1. Prepare the request form data needed to authenticate with Keycloak, using properties
 *    retrieved from {@link InternalClientFeignTokenConfig}.
 * 2. Retrieve the {@link KeycloakTokenResponse} from Keycloak via the
 *    {@link InternalClientFeignToken#getToken(Map)} call.
 * 3. Add the Authorization header (in the form of Bearer tokens) to the request template
 *    if it is missing.
 *
 * This class is marked with Lombok's {@link RequiredArgsConstructor} annotation to
 * automatically generate a constructor for its final fields.
 *
 * Key Points:
 * - The `apply` method relies on Keycloak configuration properties to construct the
 *   authentication request.
 * - The method ensures idempotency by checking whether an Authorization header already exists
 *   before adding one.
 */
@RequiredArgsConstructor
public class KeycloakAuthConfiguration implements RequestInterceptor {

    private final InternalClientFeignToken internalClientFeignToken;

    private final InternalClientFeignTokenConfig internalClientFeignTokenConfig;

    @Override
    public void apply(RequestTemplate requestTemplate) {
        Map<String, Object> form = new HashMap<>();
        form.put("grant_type", internalClientFeignTokenConfig.getGrant_type());
        form.put("client_id", internalClientFeignTokenConfig.getClient_id());
        form.put("client_secret", internalClientFeignTokenConfig.getClient_secret());
        form.put("scope", internalClientFeignTokenConfig.getScope());
        form.put("username", internalClientFeignTokenConfig.getUsername());
        form.put("password", internalClientFeignTokenConfig.getPassword());

        KeycloakTokenResponse token = Optional.ofNullable(internalClientFeignToken.getToken(form).getBody())
                .orElse(new KeycloakTokenResponse());

        if (CollectionUtils.isEmpty(requestTemplate.headers().get(HttpHeaders.AUTHORIZATION))) {
            requestTemplate.header(HttpHeaders.AUTHORIZATION, token.getTokenType() + " " + token.getAccessToken());
        }
    }
}
