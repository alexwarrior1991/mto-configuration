package com.alejandro.mtoconfiguration.mapper.lov;

import com.alejandro.mtoconfiguration.entity.lov.SupportType;
import com.alejandro.mtoconfiguration.mapper.commons.CentralConfigMapper;
import com.alejandro.mtoconfiguration.mapper.lov.commons.LovMapper;
import com.alejandro.mtoconfiguration.model.synchronous.lov.SupportTypeDTO;
import org.mapstruct.Mapper;

@Mapper(config = CentralConfigMapper.class)
public interface SupportTypeMapper extends LovMapper<SupportTypeDTO, SupportType> {
}
