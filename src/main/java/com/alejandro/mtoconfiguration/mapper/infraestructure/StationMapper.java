package com.alejandro.mtoconfiguration.mapper.infraestructure;


import com.alejandro.mtoconfiguration.entity.infrastructure.Disconnector;
import com.alejandro.mtoconfiguration.entity.infrastructure.SectionInsulator;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.mapper.commons.BaseMapper;
import com.alejandro.mtoconfiguration.mapper.commons.CentralConfigMapper;
import com.alejandro.mtoconfiguration.mapper.commons.ReferenceMapper;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.StationDTO;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = CentralConfigMapper.class, uses = {
        ReferenceMapper.class,
        TrackMapper.class,
        DisconnectorMapper.class,
        SectionInsulatorMapper.class
})
public interface StationMapper extends BaseMapper<StationDTO, Station> {

    @Override
    @Mapping(target = "executionPackageId", source = "executionPackage.id")
    StationDTO toDTO(Station entity);

    @Override
    @Mapping(target = "executionPackage", source = "executionPackageId")
    Station toEntity(StationDTO dto);

    @Override
    @Mapping(target = "executionPackage", source = "executionPackageId")
    void updateEntityFromDTO(StationDTO dto, Station entity);

    @AfterMapping
    default void linkRelations(StationDTO dto, @MappingTarget Station entity) {
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
    }
}
