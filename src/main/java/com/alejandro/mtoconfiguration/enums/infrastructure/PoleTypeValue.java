package com.alejandro.mtoconfiguration.enums.infrastructure;

import com.alejandro.mtoconfiguration.enums.IStatusEnum;
import lombok.Getter;

import java.io.Serializable;

/**
 * The PoleTypeValue enum represents various types of poles or masts commonly used
 * in different infrastructure settings, ranging from Overhead Contact System (OCS)
 * masts to non-OCS structural elements.
 *
 * Each enum constant is associated with a unique code identifier and a descriptive
 * text that provides information about the specific type of pole or mast. These
 * entities are used for classification and identification purposes, often in the
 * context of infrastructure design and maintenance.
 *
 * Implements {@link IStatusEnum} interface to provide access to the code, description,
 * and name properties. Implements {@link Serializable} for enabling the serialization
 * of enum instances.
 *
 * Enum constants include:
 * - Standard single HEB OCS masts with varying sizes.
 * - Reinforced single and double HEB masts for increased structural stability.
 * - Variants for bridges and viaducts, including long types and compensated types.
 * - Special designs with base plates or wall-anchored configurations.
 * - Non-OCS structural elements like signaling masts, technical supports, and auxiliary track masts.
 *
 * Utility methods:
 * {@link #fromCode(String)} - Retrieves the enum constant corresponding to the given code.
 *                              Returns null if no match is found.
 */
@Getter
public enum PoleTypeValue implements IStatusEnum, Serializable {
    // HEB SIMPLE STANDARD
    HEB_240("HEB-240", "Standard single HEB OCS mast"),
    HEB_260("HEB-260", "Standard single HEB OCS mast"),
    HEB_280("HEB-280", "Standard single HEB OCS mast"),
    HEB_300("HEB-300", "Standard single HEB OCS mast"),

    // HEB SIMPLE REINFORCED (L SERIES)
    HEB_320L("HEB-320L", "Reinforced single HEB OCS mast (L series)"),
    HEB_360L("HEB-360L", "Reinforced single HEB OCS mast (L series)"),
    HEB_280L("HEB-280L", "Reinforced single HEB OCS mast (L series)"),
    HEB_300L("HEB-300L", "Reinforced single HEB OCS mast (L series)"),

    // HEB SIMPLE EXTRA LONG (LL)
    HEB_300LL("HEB-300LL", "Extra-long single HEB OCS mast (≈11.8 m)"),
    HEB_320LL("HEB-320LL", "Extra-long single HEB OCS mast (≈11.8 m)"),

    // HEB COMPENSATED (CP)
    HEB_240_CP("HEB-240 (CP)", "Compensated single HEB OCS mast"),
    HEB_260_CP("HEB-260 (CP)", "Compensated single HEB OCS mast"),
    HEB_280_CP("HEB-280 (CP)", "Compensated single HEB OCS mast"),
    HEB_300_CP("HEB-300 (CP)", "Compensated single HEB OCS mast"),

    // HEB FOR BRIDGE / VIADUCT (V, VL)
    HEB_240_V("HEB-240 V", "Single HEB OCS mast for bridge"),
    HEB_260_V("HEB-260 V", "Single HEB OCS mast for bridge"),
    HEB_280_V("HEB-280 V", "Single HEB OCS mast for bridge"),
    HEB_260_VL("HEB-260 VL", "Single HEB OCS mast for bridge (long variant)"),
    HEB_240_VL("HEB-240 VL", "Single HEB OCS mast for bridge (long variant)"),

    // DOUBLE HEB (2HEB) STANDARD
    TWO_HEB_240("2HEB-240", "Standard double HEB OCS mast"),
    TWO_HEB_260("2HEB-260", "Standard double HEB OCS mast"),
    TWO_HEB_280("2HEB-280", "Standard double HEB OCS mast"),
    TWO_HEB_300("2HEB-300", "Standard double HEB OCS mast"),

    // DOUBLE HEB (2HEB) REINFORCED (L SERIES)
    TWO_HEB_240L("2HEB-240L", "Reinforced double HEB OCS mast (L series)"),
    TWO_HEB_260L("2HEB-260L", "Reinforced double HEB OCS mast (L series)"),
    TWO_HEB_320L("2HEB-320L", "Reinforced double HEB OCS mast (L series)"),

    // DOUBLE HEB FOR BRIDGE (V / VL)
    TWO_HEB_240_V("2HEB-240 V", "Double HEB OCS mast for bridge"),
    TWO_HEB_260_V("2HEB-260 V", "Double HEB OCS mast for bridge"),
    TWO_HEB_280_V("2HEB-280 V", "Double HEB OCS mast for bridge"),
    TWO_HEB_280_VL("2HEB-280 VL", "Double HEB OCS mast for bridge (long variant)"),
    TWO_HEB_300_V_CP("2HEB-300 V (CP)", "Compensated double HEB OCS mast for bridge"),

    // HEB WITH BASE PLATE (PL)
    HEB_320PL("HEB-320PL", "HEB OCS mast with special base plate"),
    HEB_360PL("HEB-360PL", "HEB OCS mast with special base plate"),
    HEB_400PL("HEB-400PL", "HEB OCS mast with heavy base plate"),
    HEB_450PL("HEB-450PL", "HEB OCS mast with heavy base plate"),

    // SIGNALLING / NON-OCS ELEMENTS
    T_SIGN("T-SIGN", "Signalling mast (non-OCS)"),
    S1T("S1T", "Technical support type 1T (non-HEB mast)"),
    S2T("S2T", "Technical support type 2T (non-HEB mast)"),
    S_1T_W("S-1T-W", "Technical support 1T with wall anchoring"),
    S_2T_W("S-2T-W", "Technical support 2T with wall anchoring"),
    POLE_TRACK_4M("POLE TRACK 4M", "Auxiliary track mast (non-OCS)"),
    LOW_WALL("LOW WALL", "Structural element, not a mast"),
    HEB_240_WALL("HEB-240 WALL", "HEB mast anchored to wall");

    ;

    private final String code;
    private final String description;

    PoleTypeValue(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * Retrieves a PoleTypeValue instance corresponding to the specified code.
     *
     * @param code the code representing a specific PoleTypeValue.
     * @return the matching PoleTypeValue instance if found, or null if no match is found.
     */
    public static PoleTypeValue fromCode(String code) {
        for (PoleTypeValue value : values()) {
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
