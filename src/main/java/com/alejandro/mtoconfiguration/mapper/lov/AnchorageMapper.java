package com.alejandro.mtoconfiguration.mapper.lov;

import com.alejandro.mtoconfiguration.entity.lov.Anchorage;
import com.alejandro.mtoconfiguration.mapper.commons.CentralConfigMapper;
import com.alejandro.mtoconfiguration.mapper.lov.commons.LovMapper;
import com.alejandro.mtoconfiguration.model.synchronous.lov.AnchorageDTO;
import org.mapstruct.Mapper;

@Mapper(config = CentralConfigMapper.class)
public interface AnchorageMapper extends LovMapper<AnchorageDTO, Anchorage> {
}
