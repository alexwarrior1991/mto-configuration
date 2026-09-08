package com.alejandro.mtoconfiguration.mapper.infraestructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.Disconnector;
import com.alejandro.mtoconfiguration.entity.infrastructure.SectionInsulator;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.TrackDTO;
import com.alejandro.mtoconfiguration.mapper.commons.BaseMapper;
import com.alejandro.mtoconfiguration.mapper.commons.CentralConfigMapper;
import com.alejandro.mtoconfiguration.mapper.commons.ReferenceMapper;
import com.alejandro.mtoconfiguration.mapper.commons.ToEntityIgnoreAudit;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.StationDTO;
import com.alejandro.mtoconfiguration.service.commons.MasterDataService;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(config = CentralConfigMapper.class, uses = {
        ReferenceMapper.class,
        TrackMapper.class,
        DisconnectorMapper.class,
        SectionInsulatorMapper.class
})
public abstract class StationMapper implements BaseMapper<StationDTO, Station> {

    @Autowired
    protected MasterDataService masterDataService;

    /**
     * Hacen falta aqui, y no solo en el {@code uses} del @Mapper, porque la reconciliacion de las
     * tres colecciones vive en el {@code @AfterMapping} de esta clase y necesita volcar cada DTO
     * sobre el hijo que ya existe.
     *
     * <p>Los nombres llevan el sufijo {@code Child} a proposito: el impl generado declara sus
     * propios campos con el nombre corto, y un campo del mismo nombre en la subclase <b>sombrea</b>
     * a este. Spring inyecta los dos, pero cualquier cableado por reflexion —un test que instancia
     * el impl a mano— alcanzaria solo el de la subclase y dejaria estos a null.
     */
    @Autowired
    protected TrackMapper trackChildMapper;
    @Autowired
    protected DisconnectorMapper disconnectorChildMapper;
    @Autowired
    protected SectionInsulatorMapper sectionInsulatorChildMapper;

    @Override
    @Mapping(target = "executionPackageId", source = "executionPackage.id")
    public abstract StationDTO toDTO(Station entity);

    @Override
    @Mapping(target = "executionPackage", source = "executionPackageId")
    @Mapping(target = "tracks", ignore = true)            // se reconcilian en mapDtoToEntity
    @Mapping(target = "disconnectors", ignore = true)
    @Mapping(target = "sectionInsulators", ignore = true)
    @ToEntityIgnoreAudit
    public abstract Station toEntity(StationDTO dto);

    @Override
    @Mapping(target = "executionPackage", source = "executionPackageId")
    @Mapping(target = "tracks", ignore = true)            // se reconcilian en mapDtoToEntity
    @Mapping(target = "disconnectors", ignore = true)
    @Mapping(target = "sectionInsulators", ignore = true)
    @ToEntityIgnoreAudit
    public abstract void updateEntityFromDTO(StationDTO dto, @MappingTarget Station entity);

    @AfterMapping
    protected void mapDtoToEntity(StationDTO dto, @MappingTarget Station entity) {
        // Las tres colecciones se reconcilian FUSIONANDO POR ID, no añadiendo: un hijo que el
        // cliente devuelve con su id tiene que actualizar esa fila, no insertar una copia.

        // 1. Vias. NO se reconcilian con mergeCollection, a diferencia de las dos colecciones
        //    de abajo, y la diferencia no es de estilo: desde V17 la relacion es N:M, asi que
        //    una via puede estar en tres estaciones a la vez. mergeCollection BORRA el hijo que
        //    el cliente no manda —es lo correcto cuando la estacion es la duena del hijo—, y
        //    aqui eso significaba que un PUT sobre ZIC sin mencionar 'TRACK 1' borraba la via
        //    entera, con sus perfiles, tambien para BIN y para HAD. Lo que hay que hacer al
        //    quitarla de la lista es DESLIGARLA de esta estacion y nada mas.
        linkTracks(dto, entity);

        // 2. Seccionadores
        mergeCollection(
                dto.getDisconnectors(),
                entity.getDisconnectors(),
                entity,
                disconnectorChildMapper::toEntity,
                (childDto, child) -> disconnectorChildMapper.updateEntityFromDTO(childDto, child),
                Disconnector::setStation
        );

        // 3. Aisladores de seccion
        mergeCollection(
                dto.getSectionInsulators(),
                entity.getSectionInsulators(),
                entity,
                sectionInsulatorChildMapper::toEntity,
                (childDto, child) -> sectionInsulatorChildMapper.updateEntityFromDTO(childDto, child),
                SectionInsulator::setStation
        );

        // 4. Resolución de LOVs (Si Station tuviera alguno en el futuro)
    }

    /**
     * Reconcilia las vias de la estacion ligando y desligando, nunca borrando.
     *
     * <p>El lado dueño de la N:M es {@code Track.stations}, asi que tocar solo la coleccion de
     * la estacion no persistiria nada: cada alta y cada baja tiene que ir tambien a la via.
     */
    private void linkTracks(StationDTO dto, Station entity) {
        if (dto.getTracks() == null) {
            return;
        }

        Map<Long, Track> ligadas = entity.getTracks().stream()
                .filter(track -> track.getId() != null)
                .collect(Collectors.toMap(track -> track.getId(), track -> track, (a, b) -> a));

        Set<Long> entrantes = dto.getTracks().stream()
                .filter(Objects::nonNull)
                .map(TrackDTO::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Las que el cliente ha quitado: fuera el vinculo, la via se queda.
        for (Track track : List.copyOf(entity.getTracks())) {
            if (track.getId() != null && !entrantes.contains(track.getId())) {
                entity.removeTrack(track);
            }
        }

        for (TrackDTO childDto : dto.getTracks()) {
            if (childDto == null) {
                continue;
            }
            Track ligada = childDto.getId() == null ? null : ligadas.get(childDto.getId());
            if (ligada != null) {
                trackChildMapper.updateEntityFromDTO(childDto, ligada);
                // El lado dueño es Track.stations, asi que el vinculo se afirma ahi tambien. Es
                // idempotente (es un Set) y hace que el mapper no dependa de que quien construyo
                // la via ya lo hubiera puesto: updateEntityFromDTO no lo toca, porque un TrackDTO
                // anidado en una estacion no trae stationIds.
                ligada.addStation(entity);
            } else {
                // Via nueva, o una que ya existia y todavia no estaba ligada a esta estacion.
                entity.addTrack(trackChildMapper.toEntity(childDto));
            }
        }
    }

    @AfterMapping
    protected void mapEntityToDto(Station entity, @MappingTarget StationDTO dto) {
        // Reservado para resolución de LOVs si se añaden en el futuro
    }
}
