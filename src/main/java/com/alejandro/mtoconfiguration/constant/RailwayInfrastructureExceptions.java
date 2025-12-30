package com.alejandro.mtoconfiguration.constant;

import lombok.Getter;

@Getter
public enum RailwayInfrastructureExceptions {
    RIE000("Excepción no controlada"),
    RIE001("Faltan parámetros requeridos"),
    ;
    private final String message;
    RailwayInfrastructureExceptions(String message) {
        this.message = message;
    }

    public String getCode() {
        return name();
    }

}
