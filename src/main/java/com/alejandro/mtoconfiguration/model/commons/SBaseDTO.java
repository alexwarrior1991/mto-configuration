package com.alejandro.mtoconfiguration.model.commons;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.util.Date;

public class SBaseDTO extends BaseDTO{

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

    @Override
    public String getCreateUser() {
        return createUser;
    }

    @Override
    public void setCreateUser( String createUser ) {
        this.createUser = createUser;
    }

    @Override
    public String getVersionUser() {
        return versionUser;
    }

    @Override
    public void setVersionUser( String versionUser ) {
        this.versionUser = versionUser;
    }

    @Override
    public LocalDateTime getCreateDate() {
        return createDate;
    }

    @Override
    public void setCreateDate( LocalDateTime createDate ) {
        this.createDate = createDate;
    }

    @Override
    public LocalDateTime getVersionDate() {
        return versionDate;
    }

    @Override
    public void setVersionDate( LocalDateTime versionDate ) {
        this.versionDate = versionDate;
    }

    @Override
    public Integer getVersionNumber() {
        return versionNumber;
    }

    @Override
    public void setVersionNumber( Integer versionNumber ) {
        this.versionNumber = versionNumber;
    }

}
