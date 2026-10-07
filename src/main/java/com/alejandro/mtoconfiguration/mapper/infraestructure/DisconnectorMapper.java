package com.alejandro.mtoconfiguration.mapper.infraestructure;

import com.alejandro.mtoconfiguration.core.exception.ValidationException;
import com.alejandro.mtoconfiguration.entity.infrastructure.Disconnector;
import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.mapper.commons.BaseMapper;
import com.alejandro.mtoconfiguration.mapper.commons.CentralConfigMapper;
import com.alejandro.mtoconfiguration.mapper.commons.ReferenceMapper;
import com.alejandro.mtoconfiguration.mapper.commons.ToEntityIgnoreAudit;
import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.DisconnectorDTO;
import com.alejandro.mtoconfiguration.service.commons.MasterDataService;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import org.mapstruct.AfterMapping;
import org.mapstruct.InheritConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Objects;

@Mapper(
        config = CentralConfigMapper.class,
        uses = {
                ReferenceMapper.class
        }
)
public abstract class DisconnectorMapper implements BaseMapper<DisconnectorDTO, Disconnector> {

    private static final String FIELD_CONNECTED_TRACK_ID = "connectedTrackId";

    @Autowired
    protected MasterDataService masterDataService;

    @Override
    @Mapping(target = "stationId", source = "station.id")
    @Mapping(target = "profileId", source = "profile.id")
    @Mapping(target = "profileCode", source = "profile.profileId")
    @Mapping(target = "profileKp", source = "profile.kp")
    @Mapping(target = "trackId", source = "track.id")
    @Mapping(target = "connectedTrackId", source = "connectedTrack.id")
    @Mapping(target = "disconnectorFunction", ignore = true)
    public abstract DisconnectorDTO toDTO(Disconnector entity);

    /**
     * Hereda las reglas de {@code toDTO}: el detalle y el final de un alta o una modificacion
     * vuelcan la entidad con este metodo, no con {@code toDTO} (ver {@code BaseMapper}).
     */
    @Override
    @InheritConfiguration(name = "toDTO")
    public abstract void updateDTOFromEntity(Disconnector entity, @MappingTarget DisconnectorDTO dto);

    @Override
    @Mapping(target = "station", source = "stationId")
    @Mapping(target = "profile", source = "profileId")
    @Mapping(target = "track", source = "trackId")
    @Mapping(target = "connectedTrack", source = "connectedTrackId")
    @Mapping(target = "disconnectorFunction", ignore = true)
    @ToEntityIgnoreAudit
    public abstract Disconnector toEntity(DisconnectorDTO dto);

    @Override
    @Mapping(target = "station", source = "stationId")
    @Mapping(target = "profile", source = "profileId")
    @Mapping(target = "track", source = "trackId")
    @Mapping(target = "connectedTrack", source = "connectedTrackId")
    @Mapping(target = "disconnectorFunction", ignore = true)
    @ToEntityIgnoreAudit
    public abstract void updateEntityFromDTO(DisconnectorDTO dto, @MappingTarget Disconnector entity);

    @AfterMapping
    protected void mapDtoToEntity(DisconnectorDTO dto, @MappingTarget Disconnector entity) {

        if (dto.getDisconnectorFunction() != null && dto.getDisconnectorFunction().getCode() != null) {
            entity.setDisconnectorFunction(
                    masterDataService.getDisconnectorFunctionByCode(dto.getDisconnectorFunction().getCode())
            );
        }

        // 3. Sincronización de otras relaciones si existieran

        requireConnectedTrackIsNotItsOwn(entity);
    }

    /**
     * La via conectada (V27) es la otra de las dos que el seccionador pone en paralelo, asi que con
     * poste no puede ser la del perfil; sin el, la compara {@code DisconnectorValidator} con su
     * {@code trackId}.
     *
     * <p>Va aqui, y no en el validador ni en {@code DisconnectorBusiness}, porque la via del poste no
     * viaja en el DTO y este es el unico paso por el que pasan todas las escrituras de un seccionador:
     * el alta y la modificacion, sueltas o en lote (el alta en lote no llama al {@code Business}), y
     * los seccionadores que su estacion escribe anidados. Sale con el mismo 400 que el validador,
     * sobre el mismo campo, como el 409 de {@code checkVersion} sale de {@code mergeCollection}. Leer
     * la via carga el perfil, una vez y solo cuando el seccionador trae via conectada.</p>
     */
    private static void requireConnectedTrackIsNotItsOwn(Disconnector entity) {
        Track connected = entity.getConnectedTrack();
        Profile profile = entity.getProfile();
        if (connected == null || profile == null || profile.getTrack() == null) {
            return;
        }

        if (Objects.equals(connected.getId(), profile.getTrack().getId())) {
            throw new ValidationException(List.of(
                    Alert.ofDanger(ErrorCodes.BUSINESS_RULE_VIOLATION, FIELD_CONNECTED_TRACK_ID)));
        }
    }

    @AfterMapping
    protected void mapEntityToDto(Disconnector entity, @MappingTarget DisconnectorDTO dto) {
        if (entity.getDisconnectorFunction() != null && entity.getDisconnectorFunction().getId() != null) {
            dto.setDisconnectorFunction(
                    masterDataService.getDisconnectorFunctionByIdAndMapToDTO(entity.getDisconnectorFunction().getId())
            );
        }
    }
}
