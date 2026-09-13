package com.alejandro.mtoconfiguration.mapper.infraestructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
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

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

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
     * Se llama {@code referenceResolver} y no {@code referenceMapper} por lo mismo que los
     * mappers hijo llevan sufijo {@code Child}: el impl que genera MapStruct declara su propio
     * campo {@code referenceMapper}, que viene del {@code uses} de arriba. Spring inyecta los
     * dos y no se entera, pero cualquier inyeccion por reflexion que busque el campo POR NOMBRE
     * —la que usan los tests— encuentra primero el del impl y deja este a null, con el
     * NullPointer saliendo en el {@code @AfterMapping} y no donde se hizo el cableado.
     */
    @Autowired
    protected ReferenceMapper referenceResolver;

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
    @Mapping(target = "stationIds", ignore = true)   // se rellenan en mapEntityToDto
    public abstract TrackDTO toDTO(Track entity);

    @Override
    @Mapping(target = "executionPackage", source = "executionPackageId")
    @Mapping(target = "stations", ignore = true) // se resuelven en mapDtoToEntity
    @Mapping(target = "profiles", ignore = true) // se reconcilian en mapDtoToEntity
    @ToEntityIgnoreAudit
    public abstract Track toEntity(TrackDTO dto);

    @Override
    @Mapping(target = "executionPackage", source = "executionPackageId")
    @Mapping(target = "stations", ignore = true) // se resuelven en mapDtoToEntity
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

        // Estaciones: mandar la lista es declarar cuales son TODAS las de la via, igual que en
        // el resto de la API. Null es "no se ha mandado el campo" y no toca nada; una lista
        // vacia si desliga, que es como se dice "esta via ya no esta dentro de ninguna".
        if (dto.getStationIds() != null) {
            Set<Station> resolved = new LinkedHashSet<>();
            for (Long id : dto.getStationIds()) {
                if (id != null) {
                    resolved.add(referenceResolver.resolve(id, Station.class));
                }
            }
            entity.setStations(resolved);
        }
    }

    @AfterMapping
    protected void mapEntityToDto(Track entity, @MappingTarget TrackDTO dto) {
        if (entity.getStations() != null) {
            dto.setStationIds(entity.getStations().stream()
                    .map(each -> each.getId())   // no Station::getId: ambiguo con BaseEntity.getId(BaseEntity)
                    .filter(Objects::nonNull)
                    .sorted()
                    .toList());
        }
    }
}
