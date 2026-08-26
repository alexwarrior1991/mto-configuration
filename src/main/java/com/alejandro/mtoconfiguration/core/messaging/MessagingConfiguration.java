package com.alejandro.mtoconfiguration.core.messaging;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MessageSignatureProperties.class)
public class MessagingConfiguration {

    @Bean
    public MessagePayloadSignature messagePayloadSignature(MessageSignatureProperties properties) {
        return new MessagePayloadSignature(properties);
    }
}
