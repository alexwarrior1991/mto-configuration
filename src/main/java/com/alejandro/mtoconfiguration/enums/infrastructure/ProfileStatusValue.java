package com.alejandro.mtoconfiguration.enums.infrastructure;

import com.alejandro.mtoconfiguration.enums.IStatusEnum;
import lombok.Getter;

import java.io.Serializable;

@Getter
public enum ProfileStatusValue implements IStatusEnum, Serializable {

    DRAFT("DRAFT", "PROFILE_STATUS_DRAFT"),
    PROVISIONAL("PROVISIONAL", "PROFILE_STATUS_PROVISIONAL"),
    DEFINITIVE("DEFINITIVE", "PROFILE_STATUS_DEFINITIVE")
    ;

    private final String code;
    private final String description;

    ProfileStatusValue(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static ProfileStatusValue fromCode(String code) {
        for (ProfileStatusValue value : values()) {
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
