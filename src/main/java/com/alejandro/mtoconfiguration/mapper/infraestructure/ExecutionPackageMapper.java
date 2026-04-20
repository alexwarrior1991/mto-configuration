package com.alejandro.mtoconfiguration.mapper.infraestructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.ExecutionPackage;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.mapper.commons.BaseMapper;
import com.alejandro.mtoconfiguration.mapper.commons.CentralConfigMapper;
import com.alejandro.mtoconfiguration.mapper.commons.ReferenceMapper;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ExecutionPackageDTO;
import com.alejandro.mtoconfiguration.service.commons.MasterDataService;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(
        config = CentralConfigMapper.class,
        uses = {ReferenceMapper.class, TrackMapper.class, StationMapper.class}
)
public abstract class ExecutionPackageMapper implements BaseMapper<ExecutionPackageDTO, ExecutionPackage> {

    @Autowired
    protected MasterDataService masterDataService;

    @Override
    @Mapping(target = "companyId", source = "company.id")
    public abstract ExecutionPackageDTO toDTO(ExecutionPackage entity);

    @Override
    @Mapping(target = "company", source = "companyId")
    public abstract ExecutionPackage toEntity(ExecutionPackageDTO dto);

    @Override
    @Mapping(target = "company", source = "companyId")
    public abstract void updateEntityFromDTO(ExecutionPackageDTO dto, @MappingTarget ExecutionPackage entity);

    @AfterMapping
    protected void mapDtoToEntity(ExecutionPackageDTO dto, @MappingTarget ExecutionPackage entity) {
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

    @AfterMapping
    protected void mapEntityToDto(ExecutionPackage entity, @MappingTarget ExecutionPackageDTO dto) {
        // Reservado para resolución de LOVs si se añaden en el futuro
    }
}
