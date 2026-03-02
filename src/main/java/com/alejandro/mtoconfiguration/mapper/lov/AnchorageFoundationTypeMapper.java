package com.alejandro.mtoconfiguration.mapper.lov;

import com.alejandro.mtoconfiguration.entity.lov.AnchorageFoundationType;
import com.alejandro.mtoconfiguration.mapper.commons.CentralConfigMapper;
import com.alejandro.mtoconfiguration.mapper.lov.commons.LovMapper;
import com.alejandro.mtoconfiguration.model.synchronous.lov.AnchorageFoundationTypeDTO;
import org.mapstruct.Mapper;

@Mapper(config = CentralConfigMapper.class)
public interface AnchorageFoundationTypeMapper extends LovMapper<AnchorageFoundationTypeDTO, AnchorageFoundationType> {
}
