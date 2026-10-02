package com.alejandro.mtoconfiguration.model.commons;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.StringUtils;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * Una entrada de catálogo tal como sale por la API: la de {@code GET /pole-types} y la que viaja
 * dentro de un maestro ({@code profile.poleType}).
 *
 * <p>Lleva de {@link BaseDTO} el {@code versionNumber}, que es el bloqueo optimista de
 * {@code PUT /{id}} y {@code PUT /bulk} (README_API.md §9), y {@code versionDate} y
 * {@code versionUser}, quién la tocó por última vez. Hasta que se publicaron estaban ocultos, así que
 * ningún cliente podía devolver la versión que leyó: el servicio no comprobaba nada y ganaba el
 * último en guardar. Solo se ocultan los de creación, que nadie usa.</p>
 */
public class SLovDTO extends LovDTO {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonIgnore
    private String createUser;
    @JsonIgnore
    private LocalDateTime createDate;

    public SLovDTO() {
        super();
    }

    public SLovDTO(Long id, String code, String description) {
        super(id, code, description);
    }

    public SLovDTO(Long id, String code, String description, String type, boolean enabled) {
        super(id, code, description);
        this.setType(type);
        this.setEnabled(enabled);
    }

    @Override
    public String getCreateUser() {
        return createUser;
    }

    @Override
    public void setCreateUser(String createUser) {
        this.createUser = createUser;
    }

    @Override
    public LocalDateTime getCreateDate() {
        return createDate;
    }

    @Override
    public void setCreateDate(LocalDateTime createDate) {
        this.createDate = createDate;
    }

    public static boolean validLovDTO(LovDTO dto) {
        return dto != null && (dto.getId() != null || !StringUtils.isBlank(dto.getCode()));
    }

}
