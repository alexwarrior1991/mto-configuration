package com.alejandro.mtoconfiguration.mapper.infraestructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.Disconnector;
import com.alejandro.mtoconfiguration.entity.infrastructure.SectionInsulator;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
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

        // 1. Vias
        mergeCollection(
                dto.getTracks(),
                entity.getTracks(),
                entity,
                trackChildMapper::toEntity,
                (childDto, child) -> trackChildMapper.updateEntityFromDTO(childDto, child),
                Track::setStation
        );

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

    @AfterMapping
    protected void mapEntityToDto(Station entity, @MappingTarget StationDTO dto) {
        // Reservado para resolución de LOVs si se añaden en el futuro
    }
}
