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
 * Feign client interface for interacting with Keycloak's token endpoint. This client facilitates
 * communication with Keycloak for getting access tokens, refresh tokens, and other authentication
 * details by making a POST request to the token endpoint.
 *
 * The client uses a custom configuration for encoding form data when making requests.
 * All requests made by this client are targeted to the URL specified in the application
 * property `configuration.feign.repositories.token-client.uri`.
 *
 * Methods:
 * - `getToken`: Sends a POST request to Keycloak's token endpoint with URL-encoded form data to
 *               retrieve authentication tokens and related details wrapped in a {@link ResponseEntity}.
 */
//TODO: define url
@FeignClient(
        name = "feign-internal-token-keycloak",
        url = "${configuration.feign.repositories.token-client.uri}",
        configuration = InternalClientFeignToken.Configuration.class
)
public interface InternalClientFeignToken {

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
