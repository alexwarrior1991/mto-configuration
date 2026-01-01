package com.alejandro.mtoconfiguration.repository.feign.token;

import com.alejandro.mtoconfiguration.repository.feign.token.model.KeycloakTokenResponse;
import feign.codec.Encoder;
import feign.form.spring.SpringFormEncoder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.support.FeignHttpMessageConverters;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE;

/**
 * Feign client interface for interacting with Keycloak's token endpoint as a public client.
 * This interface is responsible for facilitating communication with Keycloak to obtain
 * authentication tokens, including access tokens and refresh tokens, by making POST requests
 * to the `/protocol/openid-connect/token` endpoint.
 *
 * The client is configured with a custom encoder to handle URL-encoded form data in request bodies.
 * The base URL for the endpoint is specified in the application property
 * `configuration.feign.repositories.token-client.uri`.
 *
 * Methods:
 * - `getToken`: Sends a POST request with URL-encoded form data to retrieve authentication
 *               tokens wrapped in a {@link ResponseEntity}.
 *
 * Nested Classes:
 * - `Configuration`: Provides custom encoding configuration for URL form-encoded requests
 *                     using `SpringFormEncoder` and `SpringEncoder`.
 */
//TODO: define url
@FeignClient(name = "feign-public-token-keycloak", url = "${configuration.feign.repositories.token-client.uri}", configuration  = PublicClientFeignToken.Configuration.class)
public interface PublicClientFeignToken {

    @PostMapping(value = "/protocol/openid-connect/token", consumes = APPLICATION_FORM_URLENCODED_VALUE)
    ResponseEntity<KeycloakTokenResponse> getToken(@RequestBody Map<String, ?> form);

    //URL Form encoder para los atributos del formulario
    class Configuration {
        @Bean
        Encoder feignFormEncoder(ObjectProvider<FeignHttpMessageConverters> converters) {
            return new SpringFormEncoder(new SpringEncoder(converters));
        }
    }
}
