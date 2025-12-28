package com.alejandro.mtoconfiguration.enums;

import java.io.Serializable;

public interface IStatusEnum extends Serializable {

    String getCode();

    String getDescription();

    String getName();
}
