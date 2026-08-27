package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Cableado de la capa de trabajos en segundo plano.
 *
 * <p>No declara ningun executor: los trabajos corren en el {@code applicationTaskExecutor} de
 * {@code AsyncConfiguration}, que ya usa hilos virtuales y propaga el {@code SecurityContext}. Un
 * executor propio aqui habria creado un segundo pool invisible para la configuracion y, sobre todo,
 * habria perdido la identidad del usuario en el salto de hilo.</p>
 */
@Configuration
@EnableConfigurationProperties(AsyncJobProperties.class)
public class AsyncJobConfiguration {
}
