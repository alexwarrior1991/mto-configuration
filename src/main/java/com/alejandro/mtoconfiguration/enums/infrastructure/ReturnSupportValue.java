package com.alejandro.mtoconfiguration.enums.infrastructure;

import com.alejandro.mtoconfiguration.enums.IStatusEnum;
import lombok.Getter;

import java.io.Serializable;

@Getter
public enum ReturnSupportValue implements IStatusEnum, Serializable {

    RETURN_WIRE_SUPPORT_POLE_RW1("RW1", "Return Wire Support in pole"),
    RETURN_WIRE_SUPPORT_POLE_RW2("RW2", "2 Return Wire Support in pole"),

    RETURN_WIRE_SUPPORT_IN_SUPPORT_RW1T_S("RW1T-S", "Return Wire Support in support"),
    RETURN_WIRE_SUPPORT_IN_CEILING_RW1T_C("RW1T-C", "Return Wire Support in ceiling"),
    RETURN_WIRE_SUPPORT_IN_SUPPORT_AND_CEILING_RW1T_SC("RW1T-SC", "Return Wire Support in support and ceiling"),

    RETURN_WIRE_SUPPORT_2_IN_SUPPORT_RW2T_S("RW2T-S", "2 Return Wire Support in support"),
    RETURN_WIRE_SUPPORT_2_IN_CEILING_RW2T_C("RW2T-C", "2 Return Wire Support in ceiling"),
    RETURN_WIRE_SUPPORT_2_IN_SUPPORT_AND_CEILING_RW2T_SC("RW2T-SC", "2 Return Wire Support in support and ceiling");

    private final String code;
    private final String description;

    ReturnSupportValue(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static ReturnSupportValue fromCode(String code) {
        for (ReturnSupportValue value : values()) {
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
