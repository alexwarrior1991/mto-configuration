package com.alejandro.mtoconfiguration.model.synchronous.infrastructure;

import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.CantileverTypeDTO;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CantileverDTO extends BaseDTO {

    private BigDecimal cwHeight;
    private BigDecimal stagger;
    private BigDecimal catenaryHeight;
    private BigDecimal cwElevation;
    private BigDecimal windDeflection;
    private BigDecimal armAngle;

    private CantileverTypeDTO cantileverType;
    private Long profileId;
    private SteadyArmDTO steadyArm;
}
