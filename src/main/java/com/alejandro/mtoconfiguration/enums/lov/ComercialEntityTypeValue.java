package com.alejandro.mtoconfiguration.enums.lov;

import com.alejandro.mtoconfiguration.enums.IStatusEnum;
import lombok.Getter;

import java.io.Serializable;

@Getter
public enum ComercialEntityTypeValue implements IStatusEnum, Serializable {

    CONSIGNEE("CONSIGNEE", "COMMERCIAL_ENTITY_TYPE_CONSIGNEE"),
    CUSTOMS("CUSTOMS", "COMMERCIAL_ENTITY_TYPE_CUSTOMS"),
    RAILWAY_COMPANY("RAILWAY_COMPANY", "COMMERCIAL_ENTITY_TYPE_RAILWAY_COMPANY")
    ;

    private final String code;
    private final String description;

    ComercialEntityTypeValue(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * Retrieves a PoleTypeValue instance corresponding to the specified code.
     *
     * @param code the code representing a specific PoleTypeValue.
     * @return the matching PoleTypeValue instance if found, or null if no match is found.
     */
    public static ComercialEntityTypeValue fromCode(String code) {
        for (ComercialEntityTypeValue value : values()) {
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
