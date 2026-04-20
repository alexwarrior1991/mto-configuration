package com.alejandro.mtoconfiguration.mapper.infraestructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.mapper.commons.BaseMapper;
import com.alejandro.mtoconfiguration.mapper.commons.CentralConfigMapper;
import com.alejandro.mtoconfiguration.mapper.commons.ReferenceMapper;
import com.alejandro.mtoconfiguration.mapper.commons.ToEntityIgnoreAudit;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.TrackDTO;
import com.alejandro.mtoconfiguration.service.commons.MasterDataService;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(
        config = CentralConfigMapper.class,
        uses = {
                ReferenceMapper.class,
                ProfileMapper.class
        }
)
public abstract class TrackMapper implements BaseMapper<TrackDTO, Track> {

    @Autowired
    protected MasterDataService masterDataService;

    @Override
    @Mapping(target = "executionPackageId", source = "executionPackage.id")
    @Mapping(target = "stationId", source = "station.id")
    public abstract TrackDTO toDTO(Track entity);

    @Override
    @Mapping(target = "executionPackage", source = "executionPackageId")
    @Mapping(target = "station", source = "stationId")
    @ToEntityIgnoreAudit
    public abstract Track toEntity(TrackDTO dto);

    @Override
    @Mapping(target = "executionPackage", source = "executionPackageId")
    @Mapping(target = "station", source = "stationId")
    @ToEntityIgnoreAudit
    public abstract void updateEntityFromDTO(TrackDTO dto, @MappingTarget Track entity);


    @AfterMapping
    protected void mapDtoToEntity(TrackDTO dto, @MappingTarget Track entity) {

        // Sincronización de Profiles (Bidireccional + Borrado de huérfanos)
        // Se asegura de que cada Profile apunte a la vía (Track) correspondiente.
        linkCollection(
                dto.getProfiles(),      // DTOs origen
                entity.getProfiles(),   // Colección actual en BD
                entity,                 // Padre (Track)
                Profile::setTrack,      // Linker
                true                    // Sincronización total
        );

        // Añadir aqui resolución de LOVs si Track tuviera alguno
    }

    @AfterMapping
    protected void mapEntityToDto(Track entity, @MappingTarget TrackDTO dto) {
        // Reservado para resolución de LOVs si se añaden en el futuro
    }
}
