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

    /**
     * Hace falta aqui, y no solo en el {@code uses} del @Mapper, porque la reconciliacion de
     * perfiles vive en el {@code @AfterMapping} de esta clase y necesita volcar cada DTO sobre el
     * perfil que ya existe.
     *
     * <p>El sufijo {@code Child} es deliberado: el impl generado declara su propio campo
     * {@code profileMapper} y un homonimo en la subclase sombrearia a este.
     */
    @Autowired
    protected ProfileMapper profileChildMapper;

    @Override
    @Mapping(target = "executionPackageId", source = "executionPackage.id")
    @Mapping(target = "stationId", source = "station.id")
    public abstract TrackDTO toDTO(Track entity);

    @Override
    @Mapping(target = "executionPackage", source = "executionPackageId")
    @Mapping(target = "station", source = "stationId")
    @Mapping(target = "profiles", ignore = true) // se reconcilian en mapDtoToEntity
    @ToEntityIgnoreAudit
    public abstract Track toEntity(TrackDTO dto);

    @Override
    @Mapping(target = "executionPackage", source = "executionPackageId")
    @Mapping(target = "station", source = "stationId")
    @Mapping(target = "profiles", ignore = true) // se reconcilian en mapDtoToEntity
    @ToEntityIgnoreAudit
    public abstract void updateEntityFromDTO(TrackDTO dto, @MappingTarget Track entity);


    @AfterMapping
    protected void mapDtoToEntity(TrackDTO dto, @MappingTarget Track entity) {

        // Reconciliación de Profiles: fusiona por id, no añade.
        // Un perfil que el cliente devuelve con su id actualiza ESA fila; añadirlo insertaba una
        // copia sin id junto a la original y dejaba la original sin los cambios.
        mergeCollection(
                dto.getProfiles(),
                entity.getProfiles(),
                entity,
                profileChildMapper::toEntity,
                (childDto, child) -> profileChildMapper.updateEntityFromDTO(childDto, child),
                Profile::setTrack
        );

        // Añadir aqui resolución de LOVs si Track tuviera alguno
    }

    @AfterMapping
    protected void mapEntityToDto(Track entity, @MappingTarget TrackDTO dto) {
        // Reservado para resolución de LOVs si se añaden en el futuro
    }
}
