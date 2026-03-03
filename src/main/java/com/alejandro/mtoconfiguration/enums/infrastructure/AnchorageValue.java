package com.alejandro.mtoconfiguration.enums.infrastructure;

import com.alejandro.mtoconfiguration.enums.IStatusEnum;
import lombok.Getter;

import java.io.Serializable;

@Getter
public enum AnchorageValue implements IStatusEnum, Serializable {

    OCS_COMPENSATED_ANCHORAGE("CP+AnMC", "OCS Compensated Anchorage"),
    OCS_FIXED_ANCHORAGE("FP+AnMC", "OCS Fixed Anchorage"),
    OCS_COMPENSATED_ANCHORAGE_TUNNEL("CP/Tunnel", "OCS Compensated Anchorage in tunnel"),
    OCS_FIXED_ANCHORAGE_TUNNEL("FP/Tunnel", "OCS Fixed Anchorage in tunnel"),
    OCS_TENSIONING_ANCHORAGE_POLE("CP/TX-P/1100", "OCS Tensioning Anchorage in pole"),
    OCS_TENSIONING_ANCHORAGE_TUNNEL("CP/TX-T/1100", "OCS Tensioning Anchorage in tunnel"),
    OCS_TENSIONING_ANCHORAGE_WALL("CP/TX-W/1100", "OCS Tensioning Anchorage in wall"),
    FEEDER_ANCHOR("AnFW", "Feeder Anchor"),
    RETURN_ANCHOR("AnRW", "Return Anchor"),
    FEEDER_ANCHOR_TUNNEL("AnFW/Tunnel", "Feeder anchor in tunnel"),
    RETURN_ANCHOR_TUNNEL("AnRW/Tunnel", "Return anchor in tunnel"),
    SEMI_TENSION_LENGTH("xxx m.", "SEMI TENSION LENGTH"),
    INSULATED_OVERLAP("IO", "Insulated Overlap");

    private final String code;
    private final String description;

    AnchorageValue(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static AnchorageValue fromCode(String code) {
        for (AnchorageValue value : values()) {
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
