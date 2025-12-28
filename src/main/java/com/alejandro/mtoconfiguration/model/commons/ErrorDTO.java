package com.alejandro.mtoconfiguration.model.commons;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ErrorDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    String field;
    String error;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        ErrorDTO errorDTO = (ErrorDTO) o;
        return Objects.equals(getField(), errorDTO.getField()) && Objects.equals(getError(), errorDTO.getError());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getField(), getError());
    }

}
