package com.alejandro.mtoconfiguration.mapper.lov;

import com.alejandro.mtoconfiguration.entity.lov.Portal;
import com.alejandro.mtoconfiguration.mapper.commons.CentralConfigMapper;
import com.alejandro.mtoconfiguration.mapper.lov.commons.LovMapper;
import com.alejandro.mtoconfiguration.model.synchronous.lov.PortalDTO;
import org.mapstruct.Mapper;

@Mapper(config = CentralConfigMapper.class, uses = {PortalTypeMapper.class})
public interface PortalMapper extends LovMapper<PortalDTO, Portal> {
}
