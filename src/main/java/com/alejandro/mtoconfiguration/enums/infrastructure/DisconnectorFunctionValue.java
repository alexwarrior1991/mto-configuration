package com.alejandro.mtoconfiguration.enums.infrastructure;

import com.alejandro.mtoconfiguration.enums.IStatusEnum;
import lombok.Getter;

import java.io.Serializable;

@Getter
public enum DisconnectorFunctionValue implements IStatusEnum, Serializable {

    INSULATED_OVERLAP("IO", "Insulated Overlap"),
    EARTHING_DISCONNECTOR("ED", "Earthing Disconnector"),
    BY_PASS_FEEDER("BPF", "By pass Feeder"),
    COUPLER("CPL", "Coupler"),
    FEEDING_DISCONNECTOR("FD", "Feeding disconnector");

    private final String code;
    private final String description;

    DisconnectorFunctionValue(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static DisconnectorFunctionValue fromCode(String code) {
        for (DisconnectorFunctionValue value : values()) {
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
