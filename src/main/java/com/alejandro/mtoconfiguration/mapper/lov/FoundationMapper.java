package com.alejandro.mtoconfiguration.mapper.lov;

import com.alejandro.mtoconfiguration.entity.lov.Foundation;
import com.alejandro.mtoconfiguration.mapper.commons.CentralConfigMapper;
import com.alejandro.mtoconfiguration.mapper.lov.commons.LovMapper;
import com.alejandro.mtoconfiguration.model.synchronous.lov.FoundationDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = CentralConfigMapper.class, uses = {FoundationTypeMapper.class})
public interface FoundationMapper extends LovMapper<FoundationDTO, Foundation> {

    @Override
    FoundationDTO toDTO(Foundation entity);

    @Override
    @Mapping(target = "foundationType", ignore = true)
    Foundation toEntity(FoundationDTO dto);

    @Override
    @Mapping(target = "foundationType", ignore = true)
    void updateEntityFromDTO(FoundationDTO dto, @MappingTarget Foundation entity);
}
