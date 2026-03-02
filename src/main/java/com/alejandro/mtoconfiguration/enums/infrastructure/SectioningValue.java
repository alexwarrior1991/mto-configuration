package com.alejandro.mtoconfiguration.enums.infrastructure;

import com.alejandro.mtoconfiguration.enums.IStatusEnum;
import lombok.Getter;

import java.io.Serializable;

@Getter
public enum SectioningValue implements IStatusEnum, Serializable {

    OVERLAP_ANCHORAGE("A/S", "Overlap Anchorage"),
    DIAGONAL_ANCHORAGE("A/S-Diag", "Diagonal Anchorage"),
    OVERLAP_SEMI_AXIS("S/A", "Overlap Semi-Axis"),
    OVERLAP_AXIS("A", "Overlap Axis"),
    MID_POINT("MP", "Mid Point"),
    MID_POINT_TUNNEL("MP/Tunnel", "Mid Point in tunnel"),
    MID_POINT_ANCHORAGE("AnMP", "Mid Point Anchorage"),
    MID_POINT_ANCHORAGE_TUNNEL("AnMP/Tunnel", "Mid Point Anchorage in tunnel"),
    SWITCH_POINT_50("P50", "Switch Point 50"),
    SWITCH_POINT_90("P90", "Switch Point 90");

    private final String code;
    private final String description;

    SectioningValue(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static SectioningValue fromCode(String code) {
        for (SectioningValue value : values()) {
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
