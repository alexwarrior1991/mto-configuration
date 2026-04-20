package com.alejandro.mtoconfiguration.mapper.infraestructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.SectionInsulator;
import com.alejandro.mtoconfiguration.mapper.commons.BaseMapper;
import com.alejandro.mtoconfiguration.mapper.commons.CentralConfigMapper;
import com.alejandro.mtoconfiguration.mapper.commons.ReferenceMapper;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SectionInsulatorDTO;
import com.alejandro.mtoconfiguration.service.commons.MasterDataService;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(
        config = CentralConfigMapper.class,
        uses = {
                ReferenceMapper.class
        }
)
public abstract class SectionInsulatorMapper implements BaseMapper<SectionInsulatorDTO, SectionInsulator> {

    @Autowired
    protected MasterDataService masterDataService;

    @Override
    @Mapping(target = "stationId", source = "station.id")
    public abstract SectionInsulatorDTO toDTO(SectionInsulator entity);

    @Override
    @Mapping(target = "station", source = "stationId")
    public abstract SectionInsulator toEntity(SectionInsulatorDTO dto);

    @Override
    @Mapping(target = "station", source = "stationId")
    public abstract void updateEntityFromDTO(SectionInsulatorDTO dto, @MappingTarget SectionInsulator entity);

    @AfterMapping
    protected void mapDtoToEntity(SectionInsulatorDTO dto, @MappingTarget SectionInsulator entity) {

        // Aquí podrías añadir resolución de LOVs si SectionInsulator tuviera alguno

        // Si tuviera colecciones hijas (como Cantilevers asociados), usaríamos linkCollection aquí.
    }

    @AfterMapping
    protected void mapEntityToDto(SectionInsulator entity, @MappingTarget SectionInsulatorDTO dto) {
        // Reservado para resolución de LOVs si se añaden en el futuro
    }
}
