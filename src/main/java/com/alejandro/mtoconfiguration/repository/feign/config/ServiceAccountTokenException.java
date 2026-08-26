package com.alejandro.mtoconfiguration.repository.feign.config;

/**
 * No se pudo obtener el token de la cuenta de servicio para una llamada saliente. Se distingue de un
 * 401 del servicio remoto porque el problema es local: credenciales mal configuradas, Keycloak
 * inaccesible o el cliente sin permiso para el <i>grant</i>.
 */
public class ServiceAccountTokenException extends RuntimeException {

    public ServiceAccountTokenException(String registrationId) {
        super("No se pudo obtener el token de la cuenta de servicio '" + registrationId
                + "'. Revísense las credenciales del cliente y el acceso a Keycloak.");
    }
}
