package com.alejandro.mtoconfiguration.model.synchronous.infrastructure;

import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.ComercialEntityTypeDTO;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BusinessEntityDTO extends BaseDTO {

    private String identificationNumber;
    private String name;
    private String code;

    private ComercialEntityTypeDTO comercialEntityType;
}
