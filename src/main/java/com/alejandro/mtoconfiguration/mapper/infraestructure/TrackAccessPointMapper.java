package com.alejandro.mtoconfiguration.mapper.infraestructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.TrackAccessPoint;
import com.alejandro.mtoconfiguration.mapper.commons.BaseMapper;
import com.alejandro.mtoconfiguration.mapper.commons.CentralConfigMapper;
import com.alejandro.mtoconfiguration.mapper.commons.ReferenceMapper;
import com.alejandro.mtoconfiguration.mapper.commons.ToEntityIgnoreAudit;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.TrackAccessPointDTO;
import org.mapstruct.InheritConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * El punto de acceso no tiene hijos ni catálogos: la vía y la estación viajan por su id y se
 * resuelven con {@link ReferenceMapper}, como las del aislador.
 */
@Mapper(
        config = CentralConfigMapper.class,
        uses = {
                ReferenceMapper.class
        }
)
public abstract class TrackAccessPointMapper implements BaseMapper<TrackAccessPointDTO, TrackAccessPoint> {

    @Override
    @Mapping(target = "trackId", source = "track.id")
    @Mapping(target = "stationId", source = "station.id")
    public abstract TrackAccessPointDTO toDTO(TrackAccessPoint entity);

    /**
     * Hereda las reglas de {@code toDTO}: el detalle y el final de un alta o una modificacion
     * vuelcan la entidad con este metodo, no con {@code toDTO} (ver {@code BaseMapper}).
     */
    @Override
    @InheritConfiguration(name = "toDTO")
    public abstract void updateDTOFromEntity(TrackAccessPoint entity, @MappingTarget TrackAccessPointDTO dto);

    @Override
    @Mapping(target = "track", source = "trackId")
    @Mapping(target = "station", source = "stationId")
    @ToEntityIgnoreAudit
    public abstract TrackAccessPoint toEntity(TrackAccessPointDTO dto);

    @Override
    @Mapping(target = "track", source = "trackId")
    @Mapping(target = "station", source = "stationId")
    @ToEntityIgnoreAudit
    public abstract void updateEntityFromDTO(TrackAccessPointDTO dto, @MappingTarget TrackAccessPoint entity);
}
