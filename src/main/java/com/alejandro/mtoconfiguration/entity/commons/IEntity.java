package com.alejandro.mtoconfiguration.entity.commons;

import com.alejandro.mtoconfiguration.core.exception.ConcurrencyException;

import java.time.LocalDateTime;

public interface IEntity {

    Long getId();

    void setId();

    LocalDateTime getCreateDate();

    void setCreateDate(LocalDateTime createDate);

    LocalDateTime getVersionDate();

    void setVersionDate(LocalDateTime versionDate);

    String getCreateUser();

    void setCreateUser(String createUser);

    String getVersionUser();

    void setVersionUser(String versionUser);

    Integer getVersionNumber();

    void validateVersion(Integer versionNumber) throws ConcurrencyException;

    void setVersionIncremented(boolean versionIncremented);

    boolean isVersionIncremented();

    default void setDirty(boolean dirty) {

    }

    default boolean isDirty() {
        return false;
    }
}
