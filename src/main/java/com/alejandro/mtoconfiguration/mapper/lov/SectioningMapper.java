package com.alejandro.mtoconfiguration.mapper.lov;

import com.alejandro.mtoconfiguration.entity.lov.Sectioning;
import com.alejandro.mtoconfiguration.mapper.commons.CentralConfigMapper;
import com.alejandro.mtoconfiguration.mapper.lov.commons.LovMapper;
import com.alejandro.mtoconfiguration.model.synchronous.lov.SectioningDTO;
import org.mapstruct.Mapper;

@Mapper(config = CentralConfigMapper.class)
public interface SectioningMapper extends LovMapper<SectioningDTO, Sectioning> {
}
