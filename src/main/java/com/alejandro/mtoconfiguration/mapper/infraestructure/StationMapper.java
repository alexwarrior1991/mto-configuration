package com.alejandro.mtoconfiguration.mapper.infraestructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.Disconnector;
import com.alejandro.mtoconfiguration.entity.infrastructure.SectionInsulator;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.mapper.commons.BaseMapper;
import com.alejandro.mtoconfiguration.mapper.commons.CentralConfigMapper;
import com.alejandro.mtoconfiguration.mapper.commons.ReferenceMapper;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.StationDTO;
import com.alejandro.mtoconfiguration.service.commons.MasterDataService;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(config = CentralConfigMapper.class, uses = {
        ReferenceMapper.class,
        TrackMapper.class,
        DisconnectorMapper.class,
        SectionInsulatorMapper.class
})
public abstract class StationMapper implements BaseMapper<StationDTO, Station> {

    @Autowired
    protected MasterDataService masterDataService;

    @Override
    @Mapping(target = "executionPackageId", source = "executionPackage.id")
    public abstract StationDTO toDTO(Station entity);

    @Override
    @Mapping(target = "executionPackage", source = "executionPackageId")
    public abstract Station toEntity(StationDTO dto);

    @Override
    @Mapping(target = "executionPackage", source = "executionPackageId")
    public abstract void updateEntityFromDTO(StationDTO dto, @MappingTarget Station entity);

    @AfterMapping
    protected void mapDtoToEntity(StationDTO dto, @MappingTarget Station entity) {
        // 1. Sincronización de Tracks (Bidireccional + Borrado de huérfanos)
        linkCollection(
                dto.getTracks(),           // DTOs origen
                entity.getTracks(),        // Entidades destino
                entity,                    // Padre (Station)
                Track::setStation,         // Linker
                true                       // Sincronización total
        );

        // 2. Sincronización de Disconnectors (Bidireccional + Borrado de huérfanos)
        linkCollection(
                dto.getDisconnectors(),    // DTOs origen
                entity.getDisconnectors(), // Entidades destino
                entity,                    // Padre (Station)
                Disconnector::setStation,  // Linker
                true                       // Sincronización total
        );

        // 3. Sincronización de SectionInsulators (Bidireccional + Borrado de huérfanos)
        linkCollection(
                dto.getSectionInsulators(),    // DTOs origen
                entity.getSectionInsulators(), // Entidades destino
                entity,                        // Padre (Station)
                SectionInsulator::setStation,  // Linker
                true                           // Sincronización total
        );

        // 4. Resolución de LOVs (Si Station tuviera alguno en el futuro)
    }

    @AfterMapping
    protected void mapEntityToDto(Station entity, @MappingTarget StationDTO dto) {
        // Reservado para resolución de LOVs si se añaden en el futuro
    }
}
