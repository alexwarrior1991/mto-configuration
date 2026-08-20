package com.alejandro.mtoconfiguration.configuration.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "cache.redis")
public class RedisCacheProperties {


    /**
     * Ventana durante la que se cortocircuita Redis tras un fallo de conexión,
     * para no pagar el timeout en cada petición. Pasada la ventana se reintenta.
     */
    private Duration degradedRetryWindow = Duration.ofSeconds(30);

    private Duration defaultTtl = Duration.ofMinutes(30);
    private Duration normalItemTtl = Duration.ofHours(6);
    private Duration normalListTtl = Duration.ofMinutes(30);
    private Duration normalPageTtl = Duration.ofMinutes(10);
    private Duration normalSearchTtl = Duration.ofMinutes(10);
    private Duration lovItemTtl = Duration.ofHours(24);
    private Duration lovListTtl = Duration.ofHours(24);

    private List<String> allowedSubtypes = List.of(
            "java.util",
            "java.time",
            "org.springframework.data.domain"
    );


    public Duration getDegradedRetryWindow() {
        return degradedRetryWindow;
    }

    public void setDegradedRetryWindow(Duration degradedRetryWindow) {
        this.degradedRetryWindow = degradedRetryWindow;
    }

    public Duration getDefaultTtl() {
        return defaultTtl;
    }

    public void setDefaultTtl(Duration defaultTtl) {
        this.defaultTtl = defaultTtl;
    }

    public Duration getNormalItemTtl() {
        return normalItemTtl;
    }

    public void setNormalItemTtl(Duration normalItemTtl) {
        this.normalItemTtl = normalItemTtl;
    }

    public Duration getNormalListTtl() {
        return normalListTtl;
    }

    public void setNormalListTtl(Duration normalListTtl) {
        this.normalListTtl = normalListTtl;
    }

    public Duration getNormalPageTtl() {
        return normalPageTtl;
    }

    public void setNormalPageTtl(Duration normalPageTtl) {
        this.normalPageTtl = normalPageTtl;
    }

    public Duration getNormalSearchTtl() {
        return normalSearchTtl;
    }

    public void setNormalSearchTtl(Duration normalSearchTtl) {
        this.normalSearchTtl = normalSearchTtl;
    }

    public Duration getLovItemTtl() {
        return lovItemTtl;
    }

    public void setLovItemTtl(Duration lovItemTtl) {
        this.lovItemTtl = lovItemTtl;
    }

    public Duration getLovListTtl() {
        return lovListTtl;
    }

    public void setLovListTtl(Duration lovListTtl) {
        this.lovListTtl = lovListTtl;
    }

    public List<String> getAllowedSubtypes() {
        return allowedSubtypes;
    }

    public void setAllowedSubtypes(List<String> allowedSubtypes) {
        this.allowedSubtypes = allowedSubtypes;
    }
}
