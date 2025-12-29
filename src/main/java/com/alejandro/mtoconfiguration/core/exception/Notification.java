package com.alejandro.mtoconfiguration.core.exception;

import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.io.Serial;
import java.io.Serializable;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Notification implements Serializable {

    @Serial
    private static final long serialVersionUID = -399897039489289326L;

    @NotNull
    String action;
    @NotNull
    String category;
    @NotNull
    String code;
    @NotNull
    String severity;
    @NotNull
    String timestamp;
    @NotNull
    String description;
    @NotNull
    String path;
}
