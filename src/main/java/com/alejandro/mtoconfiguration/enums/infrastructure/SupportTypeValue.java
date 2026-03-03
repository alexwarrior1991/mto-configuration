package com.alejandro.mtoconfiguration.enums.infrastructure;

import com.alejandro.mtoconfiguration.enums.IStatusEnum;
import lombok.Getter;

import java.io.Serializable;

@Getter
public enum SupportTypeValue implements IStatusEnum, Serializable {

    SUPPORT_1_CANTILEVER_PORTAL("S1", "Support for 1 cantilever in portal"),
    SUPPORT_2_CANTILEVER_PORTAL("S2", "Support for 2 cantilever in portal"),
    SUPPORT_1_CANTILEVER_TUNNEL("S1T", "Support for 1 cantilever in tunnel"),
    SUPPORT_2_CANTILEVER_TUNNEL_2X("S2T", "Support for 2 cantilever in tunnel (2x)"),
    SUPPORT_1_CANTILEVER_WALL("S-1T-W", "Support for 1 cantilever in wall"),
    SUPPORT_2_CANTILEVER_WALL_2X("S-2T-W", "Support for 2 cantilever in wall (2x)"),
    SUPPORT_1_CANTILEVER_SEPARATED_WALL("S-1T-R", "Support for 1 cantilever separated in wall"),
    SUPPORT_2_CANTILEVER_SEPARATED_WALL_2X("S-2T-R", "Support for 2 cantilever separated in wall (2x)"),
    SUPPORT_1_CANTILEVER_SEPARATED_WALL_H("S-1T-H", "Support for 1 cantilever separated in wall"),
    SUPPORT_2_CANTILEVER_SEPARATED_WALL_H_2X("S-2T-H", "Support for 2 cantilever separated in wall (2x)"),
    EXTENSION_1_CONSOLE_PLATFORM("SUPL-5", "Extension for 1 console in platform");

    private final String code;
    private final String description;

    SupportTypeValue(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static SupportTypeValue fromCode(String code) {
        for (SupportTypeValue value : values()) {
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
