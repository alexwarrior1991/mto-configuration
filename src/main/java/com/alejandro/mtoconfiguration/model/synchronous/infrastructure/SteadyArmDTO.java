package com.alejandro.mtoconfiguration.model.synchronous.infrastructure;

import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.SteadyArmTypeDTO;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SteadyArmDTO extends BaseDTO {

    private Long length;
    private SteadyArmTypeDTO steadyArmType;
    private Long cantileverId;
}
