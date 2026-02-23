package com.alejandro.mtoconfiguration.mapper.lov;

import com.alejandro.mtoconfiguration.entity.lov.CantileverType;
import com.alejandro.mtoconfiguration.mapper.commons.CentralConfigMapper;
import com.alejandro.mtoconfiguration.mapper.lov.commons.LovMapper;
import com.alejandro.mtoconfiguration.model.synchronous.lov.CantileverTypeDTO;
import org.mapstruct.Mapper;

@Mapper(config = CentralConfigMapper.class)
public interface CantileverTypeMapper extends LovMapper<CantileverTypeDTO, CantileverType> {
}
