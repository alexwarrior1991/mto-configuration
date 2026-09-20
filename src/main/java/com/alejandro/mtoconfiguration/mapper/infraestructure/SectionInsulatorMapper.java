package com.alejandro.mtoconfiguration.mapper.infraestructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.SectionInsulator;
import com.alejandro.mtoconfiguration.entity.infrastructure.SectionInsulatorSwitch;
import com.alejandro.mtoconfiguration.mapper.commons.BaseMapper;
import com.alejandro.mtoconfiguration.mapper.commons.CentralConfigMapper;
import com.alejandro.mtoconfiguration.mapper.commons.ReferenceMapper;
import com.alejandro.mtoconfiguration.mapper.commons.ToEntityIgnoreAudit;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SectionInsulatorDTO;
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
                SectionInsulatorSwitchMapper.class
        }
)
public abstract class SectionInsulatorMapper implements BaseMapper<SectionInsulatorDTO, SectionInsulator> {

    @Autowired
    protected MasterDataService masterDataService;

    /**
     * Hace falta aquí, y no sólo en el {@code uses} del @Mapper, porque la reconciliación de agujas
     * vive en el {@code @AfterMapping} de esta clase y necesita volcar cada DTO sobre la aguja que
     * ya existe.
     *
     * <p>El nombre lleva el sufijo {@code Child} a propósito, igual que en {@code ProfileMapper}: el
     * impl generado declara su propio campo {@code sectionInsulatorSwitchMapper}, y un campo del
     * mismo nombre en la subclase <b>sombrea</b> a este. Spring inyecta los dos; cualquier cableado
     * por reflexión —un test que instancia el impl a mano— alcanzaría sólo el de la subclase y
     * dejaría este a null.
     */
    @Autowired
    protected SectionInsulatorSwitchMapper sectionInsulatorSwitchChildMapper;

    @Override
    @Mapping(target = "stationId", source = "station.id")
    @Mapping(target = "trackId", source = "track.id")
    @Mapping(target = "connectedTrackId", source = "connectedTrack.id")
    public abstract SectionInsulatorDTO toDTO(SectionInsulator entity);

    @Override
    @Mapping(target = "station", source = "stationId")
    @Mapping(target = "track", source = "trackId")
    @Mapping(target = "connectedTrack", source = "connectedTrackId")
    @Mapping(target = "switches", ignore = true) // se reconcilia en mapDtoToEntity
    @ToEntityIgnoreAudit
    public abstract SectionInsulator toEntity(SectionInsulatorDTO dto);

    @Override
    @Mapping(target = "station", source = "stationId")
    @Mapping(target = "track", source = "trackId")
    @Mapping(target = "connectedTrack", source = "connectedTrackId")
    @Mapping(target = "switches", ignore = true) // se reconcilia en mapDtoToEntity
    @ToEntityIgnoreAudit
    public abstract void updateEntityFromDTO(SectionInsulatorDTO dto, @MappingTarget SectionInsulator entity);

    @AfterMapping
    protected void mapDtoToEntity(SectionInsulatorDTO dto, @MappingTarget SectionInsulator entity) {

        // Reconciliación de la colección de agujas.
        //
        // mergeCollection y no linkCollection, por lo mismo que las ménsulas del perfil: la aguja
        // que el cliente devuelve con su id tiene que actualizar ESA fila. Añadirla, que es lo que
        // hace el código generado, insertaría una copia sin id junto a la original y dejaría la
        // original sin los cambios.
        mergeCollection(
                dto.getSwitches(),
                entity.getSwitches(),
                entity,
                sectionInsulatorSwitchChildMapper::toEntity,
                (childDto, child) -> sectionInsulatorSwitchChildMapper.updateEntityFromDTO(childDto, child),
                SectionInsulatorSwitch::setSectionInsulator
        );
    }

    @AfterMapping
    protected void mapEntityToDto(SectionInsulator entity, @MappingTarget SectionInsulatorDTO dto) {
        // Reservado para resolución de LOVs si se añaden en el futuro
    }
}
