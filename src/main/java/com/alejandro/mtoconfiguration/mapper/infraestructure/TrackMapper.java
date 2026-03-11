package com.alejandro.mtoconfiguration.mapper.infraestructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.mapper.commons.BaseMapper;
import com.alejandro.mtoconfiguration.mapper.commons.CentralConfigMapper;
import com.alejandro.mtoconfiguration.mapper.commons.ReferenceMapper;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.TrackDTO;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = CentralConfigMapper.class, uses = {ReferenceMapper.class, ProfileMapper.class})
public interface TrackMapper extends BaseMapper<TrackDTO, Track> {

    @Override
    @Mapping(target = "executionPackageId", source = "executionPackage.id")
    @Mapping(target = "stationId", source = "station.id")
    TrackDTO toDTO(Track entity);


    @Override
    @Mapping(target = "executionPackage", source = "executionPackageId")
    @Mapping(target = "station", source = "stationId")
    Track toEntity(TrackDTO dto);

    @Override
    @Mapping(target = "executionPackage", source = "executionPackageId")
    @Mapping(target = "station", source = "stationId")
    void updateEntityFromDTO(TrackDTO dto, @MappingTarget Track entity);

    @AfterMapping
    default void linkRelations(TrackDTO dto, @MappingTarget Track entity) {
        // Sincronización de Profiles (Bidireccional + Borrado de huérfanos)
        // Se asegura de que cada Profile apunte a la vía (Track) correspondiente.
        linkCollection(
                dto.getProfiles(),      // DTOs origen (fuente de verdad)
                entity.getProfiles(),   // Colección actual en la base de datos
                entity,                 // La entidad padre (Track)
                Profile::setTrack,      // Referencia al método para establecer el padre
                true                    // deleteEntitiesNotInDtoList = true (Sincronización total)
        );
    }
}
