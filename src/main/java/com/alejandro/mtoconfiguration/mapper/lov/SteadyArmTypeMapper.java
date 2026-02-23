package com.alejandro.mtoconfiguration.mapper.lov;

import com.alejandro.mtoconfiguration.entity.lov.SteadyArmType;
import com.alejandro.mtoconfiguration.mapper.commons.CentralConfigMapper;
import com.alejandro.mtoconfiguration.mapper.lov.commons.LovMapper;
import com.alejandro.mtoconfiguration.model.synchronous.lov.SteadyArmTypeDTO;
import org.mapstruct.Mapper;

@Mapper(config = CentralConfigMapper.class)
public interface SteadyArmTypeMapper extends LovMapper<SteadyArmTypeDTO, SteadyArmType> {
}
