package com.alejandro.mtoconfiguration.model.commons;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.StringUtils;

import java.io.Serial;
import java.time.LocalDateTime;
import java.util.Date;

public class SLovDTO extends LovDTO {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonIgnore
    private String createUser;
    @JsonIgnore
    private String versionUser;
    @JsonIgnore
    private LocalDateTime createDate;
    @JsonIgnore
    private LocalDateTime versionDate;
    @JsonIgnore
    private Integer versionNumber;

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
    public String getVersionUser() {
        return versionUser;
    }

    @Override
    public void setVersionUser(String versionUser) {
        this.versionUser = versionUser;
    }

    @Override
    public LocalDateTime getCreateDate() {
        return createDate;
    }

    @Override
    public void setCreateDate(LocalDateTime createDate) {
        this.createDate = createDate;
    }

    @Override
    public LocalDateTime getVersionDate() {
        return versionDate;
    }

    @Override
    public void setVersionDate(LocalDateTime versionDate) {
        this.versionDate = versionDate;
    }

    @Override
    public Integer getVersionNumber() {
        return versionNumber;
    }

    @Override
    public void setVersionNumber(Integer versionNumber) {
        this.versionNumber = versionNumber;
    }

    public static boolean validLovDTO(LovDTO dto) {
        return dto != null && (dto.getId() != null || !StringUtils.isBlank(dto.getCode()));
    }

}
