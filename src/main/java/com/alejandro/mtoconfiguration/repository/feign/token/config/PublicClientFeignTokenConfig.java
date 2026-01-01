package com.alejandro.mtoconfiguration.repository.feign.token.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "configuration.feign.repositories.token-client.config.token-public")
//TODO: write the configuration data en module-config.yaml
public class PublicClientFeignTokenConfig {
    private String grant_type;
    private String scope;
    private String username;
    private String password;
}
