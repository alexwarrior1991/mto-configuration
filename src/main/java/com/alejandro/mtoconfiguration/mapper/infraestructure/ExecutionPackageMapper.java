package com.alejandro.mtoconfiguration.mapper.infraestructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.ExecutionPackage;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.mapper.commons.BaseMapper;
import com.alejandro.mtoconfiguration.mapper.commons.CentralConfigMapper;
import com.alejandro.mtoconfiguration.mapper.commons.ReferenceMapper;
import com.alejandro.mtoconfiguration.mapper.commons.ToEntityIgnoreAudit;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ExecutionPackageDTO;
import com.alejandro.mtoconfiguration.service.commons.MasterDataService;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(
        config = CentralConfigMapper.class,
        uses = {ReferenceMapper.class, TrackMapper.class, StationMapper.class}
)
public abstract class ExecutionPackageMapper implements BaseMapper<ExecutionPackageDTO, ExecutionPackage> {

    @Autowired
    protected MasterDataService masterDataService;

    /**
     * Hacen falta aqui, y no solo en el {@code uses} del @Mapper, porque la reconciliacion de vias
     * y estaciones vive en el {@code @AfterMapping} de esta clase y necesita volcar cada DTO sobre
     * el hijo que ya existe.
     *
     * <p>El sufijo {@code Child} es deliberado: el impl generado declara sus propios campos con el
     * nombre corto y unos homonimos en la subclase sombrearian a estos.
     */
    @Autowired
    protected TrackMapper trackChildMapper;
    @Autowired
    protected StationMapper stationChildMapper;

    @Override
    @Mapping(target = "companyId", source = "company.id")
    public abstract ExecutionPackageDTO toDTO(ExecutionPackage entity);

    @Override
    @Mapping(target = "company", source = "companyId")
    @Mapping(target = "tracks", ignore = true)   // se reconcilian en mapDtoToEntity
    @Mapping(target = "stations", ignore = true)
    @ToEntityIgnoreAudit
    public abstract ExecutionPackage toEntity(ExecutionPackageDTO dto);

    @Override
    @Mapping(target = "company", source = "companyId")
    @Mapping(target = "tracks", ignore = true)   // se reconcilian en mapDtoToEntity
    @Mapping(target = "stations", ignore = true)
    @ToEntityIgnoreAudit
    public abstract void updateEntityFromDTO(ExecutionPackageDTO dto, @MappingTarget ExecutionPackage entity);

    @AfterMapping
    protected void mapDtoToEntity(ExecutionPackageDTO dto, @MappingTarget ExecutionPackage entity) {
        // Las dos colecciones se reconcilian FUSIONANDO POR ID, no añadiendo: un hijo que el
        // cliente devuelve con su id tiene que actualizar esa fila, no insertar una copia.

        // 1. Vias
        mergeCollection(
                dto.getTracks(),
                entity.getTracks(),
                entity,
                trackChildMapper::toEntity,
                (childDto, child) -> trackChildMapper.updateEntityFromDTO(childDto, child),
                Track::setExecutionPackage
        );

        // 2. Estaciones
        mergeCollection(
                dto.getStations(),
                entity.getStations(),
                entity,
                stationChildMapper::toEntity,
                (childDto, child) -> stationChildMapper.updateEntityFromDTO(childDto, child),
                Station::setExecutionPackage
        );
    }

    @AfterMapping
    protected void mapEntityToDto(ExecutionPackage entity, @MappingTarget ExecutionPackageDTO dto) {
        // Reservado para resolución de LOVs si se añaden en el futuro
    }
}
