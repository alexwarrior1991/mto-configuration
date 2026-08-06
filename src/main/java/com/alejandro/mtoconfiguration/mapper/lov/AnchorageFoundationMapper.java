package com.alejandro.mtoconfiguration.mapper.lov;

import com.alejandro.mtoconfiguration.entity.lov.AnchorageFoundation;
import com.alejandro.mtoconfiguration.mapper.commons.CentralConfigMapper;
import com.alejandro.mtoconfiguration.mapper.lov.commons.LovMapper;
import com.alejandro.mtoconfiguration.model.synchronous.lov.AnchorageFoundationDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = CentralConfigMapper.class, uses = {AnchorageFoundationTypeMapper.class})
public interface AnchorageFoundationMapper extends LovMapper<AnchorageFoundationDTO, AnchorageFoundation> {

    @Override
    @Mapping(target = "anchorageFoundationType", ignore = true)
    AnchorageFoundation toEntity(AnchorageFoundationDTO dto);

    @Override
    @Mapping(target = "anchorageFoundationType", ignore = true)
    void updateEntityFromDTO(AnchorageFoundationDTO dto, @MappingTarget AnchorageFoundation entity);
}
