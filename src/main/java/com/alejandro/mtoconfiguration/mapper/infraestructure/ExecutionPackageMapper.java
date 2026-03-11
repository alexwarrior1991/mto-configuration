package com.alejandro.mtoconfiguration.mapper.infraestructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.ExecutionPackage;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.mapper.commons.BaseMapper;
import com.alejandro.mtoconfiguration.mapper.commons.CentralConfigMapper;
import com.alejandro.mtoconfiguration.mapper.commons.ReferenceMapper;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ExecutionPackageDTO;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = CentralConfigMapper.class, uses = {ReferenceMapper.class, TrackMapper.class, StationMapper.class})
public interface ExecutionPackageMapper extends BaseMapper<ExecutionPackageDTO, ExecutionPackage> {

    @Override
    @Mapping(target = "company", source = "companyId")
    void updateEntityFromDTO(ExecutionPackageDTO dto, @MappingTarget ExecutionPackage entity);

    @Override
    @Mapping(target = "company", source = "companyId")
    ExecutionPackage toEntity(ExecutionPackageDTO dto);

    @Override
    @Mapping(target = "companyId", source = "company.id")
    ExecutionPackageDTO toDTO(ExecutionPackage entity);

    @AfterMapping
    default void linkRelations(ExecutionPackageDTO dto, @MappingTarget ExecutionPackage entity) {
        // 1. Sincronización de Tracks (Bidireccional + Borrado de huérfanos)
        linkCollection(
                dto.getTracks(),            // DTOs origen
                entity.getTracks(),         // Entidades destino
                entity,                     // Padre
                Track::setExecutionPackage, // Linker
                true                        // deleteEntitiesNotInDtoList = true
        );

        // 2. Sincronización de Stations (Bidireccional + Borrado de huérfanos)
        linkCollection(
                dto.getStations(),             // DTOs origen
                entity.getStations(),          // Entidades destino
                entity,                        // Padre
                Station::setExecutionPackage,  // Linker
                true                           // deleteEntitiesNotInDtoList = true
        );
    }
}
