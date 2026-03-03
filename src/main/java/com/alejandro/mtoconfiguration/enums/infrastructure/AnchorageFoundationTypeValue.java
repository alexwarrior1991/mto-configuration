package com.alejandro.mtoconfiguration.enums.infrastructure;

import com.alejandro.mtoconfiguration.enums.IStatusEnum;
import lombok.Getter;

import java.io.Serializable;

@Getter
public enum AnchorageFoundationTypeValue implements IStatusEnum, Serializable {

    MESSENGER_CW_ANCHORAGE_FOUNDATION_ANMC("AnMC", "Messenger and C.W., anchorage foundation"),
    MESSENGER_CW_ANCHORAGE_FOUNDATION_ANMCR("AnMCR", "Messenger and C.W., anchorage foundation"),
    MESSENGER_CW_ANCHORAGE_FOUNDATION_ANMC_RT("AnMC-RT", "Messenger and C.W., anchorage foundation"),

    RETURN_FEEDER_MIDPOINT_ANCHORAGE_FOUNDATION_ANM("AnM", "Return, Feeder and Midpoint anchorage foundation"),
    RETURN_FEEDER_MIDPOINT_ANCHORAGE_FOUNDATION_ANMR("AnMR", "Return, Feeder and Midpoint anchorage foundation"),
    RETURN_FEEDER_MIDPOINT_ANCHORAGE_FOUNDATION_ANM_RT("AnM-RT", "Return, Feeder and Midpoint anchorage foundation"),

    MESSENGER_CW_ANCHORAGE_BASE_VIADUCT("AnMC/PL", "Messenger and C.W., anchorage base in viaduct"),
    RETURN_FEEDER_MIDPOINT_ANCHORAGE_BASE_VIADUCT("AnM/PL", "Return, Feeder and Midpoint anchorage base in viaduct"),

    MESSENGER_CW_ANCHORAGE_TUNNEL("AnMC/Tunnel", "Messenger and C.W., anchorage in tunnel"),
    RETURN_FEEDER_MIDPOINT_ANCHORAGE_TUNNEL("AnM/Tunnel", "Return, Feeder and Midpoint anchorage in tunnel"),

    MESSENGER_CW_ANCHORAGE_BASE_VIADUCT_W("AnMC/W", "Messenger and C.W., anchorage base in viaduct"),
    RETURN_FEEDER_MIDPOINT_ANCHORAGE_BASE_VIADUCT_W("AnM/W", "Return, Feeder and Midpoint base in viaduct"),

    MESSENGER_CW_TENSOREX_ANCHORAGE("AnM/Tx", "Messenger and C.W., Tensorex anchorage");

    private final String code;
    private final String description;

    AnchorageFoundationTypeValue(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static AnchorageFoundationTypeValue fromCode(String code) {
        for (AnchorageFoundationTypeValue value : values()) {
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
