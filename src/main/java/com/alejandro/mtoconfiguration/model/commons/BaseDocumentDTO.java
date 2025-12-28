package com.alejandro.mtoconfiguration.model.commons;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.io.Serial;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BaseDocumentDTO extends BaseDTO{

    @Serial
    private static final long serialVersionUID = 1L;
    String observations;
}
