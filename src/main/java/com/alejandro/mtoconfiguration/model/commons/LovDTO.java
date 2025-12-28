package com.alejandro.mtoconfiguration.model.commons;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.Optional;

@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@Getter
@Setter
public class LovDTO extends BaseDTO {
    String code;
    String description;
    String type;
    boolean enabled;

    public LovDTO(Long id, String code, String description) {
        super.setId(id);
        this.code = code;
        this.description = description;
    }

    public LovDTO(Long id, String code, String description, Class<? extends LovDTO> type, boolean enabled) {
        super.setId(id);
        this.code = code;
        this.description = description;
        this.type = type.getSimpleName();
        this.enabled = enabled;
    }

    public static Long getLovId(LovDTO lovDTO) {
        return Optional.ofNullable(lovDTO).map(LovDTO::getId).orElse(null);
    }

    public static String getLovCode(LovDTO lovDTO) {
        return Optional.ofNullable(lovDTO).map(LovDTO::getCode).orElse(null);
    }

    public static String getLovCodeOrEmptyString(LovDTO lovDTO) {
        return Optional.ofNullable(lovDTO).map(LovDTO::getCode).orElse("");
    }

    public static String getLovDescription(LovDTO lovDTO) {
        return Optional.ofNullable(lovDTO).map(LovDTO::getDescription).orElse("");
    }
}
