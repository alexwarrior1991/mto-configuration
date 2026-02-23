package com.alejandro.mtoconfiguration.mapper.lov;

import com.alejandro.mtoconfiguration.entity.lov.Foundation;
import com.alejandro.mtoconfiguration.mapper.commons.CentralConfigMapper;
import com.alejandro.mtoconfiguration.mapper.lov.commons.LovMapper;
import com.alejandro.mtoconfiguration.model.synchronous.lov.FoundationDTO;
import org.mapstruct.Mapper;

@Mapper(config = CentralConfigMapper.class, uses = {FoundationTypeMapper.class})
public interface FoundationMapper extends LovMapper<FoundationDTO, Foundation> {
}
