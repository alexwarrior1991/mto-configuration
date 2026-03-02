package com.alejandro.mtoconfiguration.mapper.lov;

import com.alejandro.mtoconfiguration.entity.lov.ComercialEntityType;
import com.alejandro.mtoconfiguration.mapper.commons.CentralConfigMapper;
import com.alejandro.mtoconfiguration.mapper.lov.commons.LovMapper;
import com.alejandro.mtoconfiguration.model.synchronous.lov.ComercialEntityTypeDTO;
import org.mapstruct.Mapper;

@Mapper(config = CentralConfigMapper.class)
public interface ComercialEntityTypeMapper extends LovMapper<ComercialEntityTypeDTO, ComercialEntityType> {
}
