package com.alejandro.mtoconfiguration.mapper.lov.commons;

import com.alejandro.mtoconfiguration.entity.lov.commons.Lov;
import com.alejandro.mtoconfiguration.mapper.commons.CentralConfigMapper;
import com.alejandro.mtoconfiguration.model.commons.LovDTO;
import com.alejandro.mtoconfiguration.model.commons.SLovDTO;
import org.mapstruct.Mapper;

@Mapper(config = CentralConfigMapper.class)
public interface GenericLovMapper {
    LovDTO toDTO(Lov entity);
    SLovDTO toSDTO(Lov entity);
}
