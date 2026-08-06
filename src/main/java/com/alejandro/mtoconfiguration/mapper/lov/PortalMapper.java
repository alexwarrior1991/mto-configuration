package com.alejandro.mtoconfiguration.mapper.lov;

import com.alejandro.mtoconfiguration.entity.lov.Portal;
import com.alejandro.mtoconfiguration.mapper.commons.CentralConfigMapper;
import com.alejandro.mtoconfiguration.mapper.lov.commons.LovMapper;
import com.alejandro.mtoconfiguration.model.synchronous.lov.PortalDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = CentralConfigMapper.class, uses = {PortalTypeMapper.class})
public interface PortalMapper extends LovMapper<PortalDTO, Portal> {

    @Override
    @Mapping(target = "portalType", ignore = true)
    Portal toEntity(PortalDTO dto);

    @Override
    @Mapping(target = "portalType", ignore = true)
    void updateEntityFromDTO(PortalDTO dto, @MappingTarget Portal entity);
}
