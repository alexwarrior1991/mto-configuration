package com.alejandro.mtoconfiguration.mapper.infraestructure;

import com.alejandro.mtoconfiguration.core.exception.ValidationException;
import com.alejandro.mtoconfiguration.entity.commons.CRUDEntity;
import com.alejandro.mtoconfiguration.entity.infrastructure.TrackAccessPoint;
import com.alejandro.mtoconfiguration.mapper.commons.BaseMapper;
import com.alejandro.mtoconfiguration.mapper.commons.CentralConfigMapper;
import com.alejandro.mtoconfiguration.mapper.commons.ReferenceMapper;
import com.alejandro.mtoconfiguration.mapper.commons.ToEntityIgnoreAudit;
import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.TrackAccessPointDTO;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import jakarta.persistence.EntityNotFoundException;
import org.mapstruct.AfterMapping;
import org.mapstruct.InheritConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

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

    private static final String FIELD_TRACK_ID = "trackId";
    private static final String FIELD_STATION_ID = "stationId";

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

    /**
     * La vía y la estación tienen que existir y estar vivas. {@code ReferenceMapper} devuelve un proxy
     * sin mirar la base, así que un id que no existe acababa en la clave ajena al confirmar (409
     * {@code BUS-002}, «valor repetido o en uso», que no dice nada), y uno borrado se aceptaba. Sale con
     * el 400 {@code BUS-001} sobre su campo, como el validador. Va aquí y no en el validador, que no
     * mira la base, y es el único paso por el que pasan el alta y la modificación, sueltas o en lote.
     */
    @AfterMapping
    protected void requireLiveReferences(TrackAccessPointDTO dto, @MappingTarget TrackAccessPoint entity) {
        requireLive(entity.getTrack(), FIELD_TRACK_ID);
        requireLive(entity.getStation(), FIELD_STATION_ID);
    }

    private static void requireLive(CRUDEntity reference, String field) {
        if (reference == null) {
            return;
        }
        boolean live;
        try {
            live = !reference.isDeleted(); // inicializa el proxy
        } catch (EntityNotFoundException missing) {
            live = false;
        }
        if (!live) {
            throw new ValidationException(List.of(Alert.ofDanger(ErrorCodes.BUSINESS_RULE_VIOLATION, field)));
        }
    }
}
