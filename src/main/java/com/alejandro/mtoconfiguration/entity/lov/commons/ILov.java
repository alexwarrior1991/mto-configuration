package com.alejandro.mtoconfiguration.entity.lov.commons;

import com.alejandro.mtoconfiguration.entity.commons.IEntity;

public interface ILov extends IEntity {

    String getCode();

    void setCode(String code);

    String getDescription();

    void setDescription(String description);

    boolean isEnabled();

    void setEnabled(boolean enabled);
}
