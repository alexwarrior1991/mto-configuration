package com.alejandro.mtoconfiguration.repository.feign.token.config;

import lombok.AccessLevel;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration class for internal client token properties used in Feign repositories.
 * This class maps the properties prefixed with
 * "configuration.feign.repositories.token-client.config.token-internal" from the
 * application configuration file (such as application.yml or application.properties).
 *
 * The properties in this class define the credentials and related configurations necessary
 * for acquiring tokens when interacting with internal client APIs via Feign.
 *
 * Fields include:
 * - grant_type: Type of token grant to use, such as "password" or "client_credentials".
 * - client_id: Client ID for authentication.
 * - client_secret: Client secret associated with the client ID.
 * - scope: Permissions or scopes requested during the token acquisition.
 * - username: Username for authentication (used when applicable for the grant type).
 * - password: Password for authentication (used when applicable for the grant type).
 *
 * To use this configuration, ensure that the necessary properties are defined in your
 * external configuration file under the specified prefix.
 */
@Data
@Component
@ConfigurationProperties(prefix = "configuration.feign.repositories.token-client.config.token-internal")
//TODO: write the configuration data en module-config.yaml
public class InternalClientFeignTokenConfig {
    private String grant_type;
    private String client_id;
    private String client_secret;
    private String scope;
    private String username;
    private String password;
}
