package com.alejandro.mtoconfiguration.enums.infrastructure;

import com.alejandro.mtoconfiguration.enums.IStatusEnum;
import lombok.Getter;

import java.io.Serializable;

@Getter
public enum CantileverTypeValue implements IStatusEnum, Serializable {

    CANTILEVER_PULL_ON("EMT-1", "Cantilever (Pull on)"),
    CANTILEVER_PUSH_OFF("EMT-2", "Cantilever (Push off)"),
    CANTILEVER_TERMINATION("EMT-T", "Cantilever (termination)"),
    TUNNEL_CANTILEVER_PULL_ON("EMT-1T", "Tunnel Cantilever (Pull on)"),
    TUNNEL_CANTILEVER_PUSH_OVER("EMT-2T", "Tunnel Cantilever (Push over)"),
    TUNNEL_CANTILEVER_TERMINATION("EMT-TT", "Tunnel Cantilever (termination)"),
    PORTAL_CANTILEVER_PULL_ON("EMT-1P", "Portal Cantilever (Pull on)"),
    PORTAL_CANTILEVER_PUSH_OVER("EMT-2P", "Portal Cantilever (Push over)"),
    PORTAL_CANTILEVER_TERMINATION("EMT-TP", "Portal Cantilever (termination)");

    private final String code;
    private final String description;

    CantileverTypeValue(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static CantileverTypeValue fromCode(String code) {
        for (CantileverTypeValue value : values()) {
            if (value.getCode().equalsIgnoreCase(code)) {
                return value;
            }
        }
        return null;
    }

    @Override
    public String getName() {
        return name();
    }
}
