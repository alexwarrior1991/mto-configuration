package com.alejandro.mtoconfiguration.mapper.lov;

import com.alejandro.mtoconfiguration.entity.lov.AnchorageFoundation;
import com.alejandro.mtoconfiguration.mapper.commons.CentralConfigMapper;
import com.alejandro.mtoconfiguration.mapper.lov.commons.LovMapper;
import com.alejandro.mtoconfiguration.model.synchronous.lov.AnchorageFoundationDTO;
import org.mapstruct.Mapper;

@Mapper(config = CentralConfigMapper.class, uses = {AnchorageFoundationTypeMapper.class})
public interface AnchorageFoundationMapper extends LovMapper<AnchorageFoundationDTO, AnchorageFoundation> {
}
