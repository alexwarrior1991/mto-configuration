package com.alejandro.mtoconfiguration.mapper.infraestructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.Cantilever;
import com.alejandro.mtoconfiguration.entity.infrastructure.Disconnector;
import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.mapper.commons.BaseMapper;
import com.alejandro.mtoconfiguration.mapper.commons.CentralConfigMapper;
import com.alejandro.mtoconfiguration.mapper.commons.ReferenceMapper;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ProfileDTO;
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
                CantileverMapper.class,
                DisconnectorMapper.class
        }
)
public abstract class ProfileMapper implements BaseMapper<ProfileDTO, Profile> {

    @Autowired
    protected MasterDataService masterDataService;

    @Override
    @Mapping(target = "trackId", source = "track.id")
    @Mapping(target = "anchorage", ignore = true)
    @Mapping(target = "anchorageFoundation", ignore = true)
    @Mapping(target = "foundation", ignore = true)
    @Mapping(target = "poleType", ignore = true)
    @Mapping(target = "portal", ignore = true)
    @Mapping(target = "profileStatus", ignore = true)
    @Mapping(target = "returnSupport", ignore = true)
    @Mapping(target = "sectioning", ignore = true)
    public abstract ProfileDTO toDTO(Profile entity);

    @Override
    @Mapping(target = "track", source = "trackId")
    @Mapping(target = "anchorage", ignore = true)
    @Mapping(target = "anchorageFoundation", ignore = true)
    @Mapping(target = "foundation", ignore = true)
    @Mapping(target = "poleType", ignore = true)
    @Mapping(target = "portal", ignore = true)
    @Mapping(target = "profileStatus", ignore = true)
    @Mapping(target = "returnSupport", ignore = true)
    @Mapping(target = "sectioning", ignore = true)
    public abstract Profile toEntity(ProfileDTO dto);

    @Override
    @Mapping(target = "track", source = "trackId")
    @Mapping(target = "anchorage", ignore = true)
    @Mapping(target = "anchorageFoundation", ignore = true)
    @Mapping(target = "foundation", ignore = true)
    @Mapping(target = "poleType", ignore = true)
    @Mapping(target = "portal", ignore = true)
    @Mapping(target = "profileStatus", ignore = true)
    @Mapping(target = "returnSupport", ignore = true)
    @Mapping(target = "sectioning", ignore = true)
    public abstract void updateEntityFromDTO(ProfileDTO dto, @MappingTarget Profile entity);

    @AfterMapping
    protected void mapDtoToEntity(ProfileDTO dto, @MappingTarget Profile entity) {


        // 1. Resolución de múltiples LOVs usando MasterDataService
        if (dto.getAnchorage() != null) {
            entity.setAnchorage(masterDataService.getAnchorageByCode(dto.getAnchorage().getCode()));
        }
        if (dto.getAnchorageFoundation() != null) {
            entity.setAnchorageFoundation(masterDataService.getAnchorageFoundationByCode(dto.getAnchorageFoundation().getCode()));
        }
        if (dto.getFoundation() != null) {
            entity.setFoundation(masterDataService.getFoundationByCode(dto.getFoundation().getCode()));
        }
        if (dto.getPoleType() != null) {
            entity.setPoleType(masterDataService.getPoleTypeByCode(dto.getPoleType().getCode()));
        }
        if (dto.getPortal() != null) {
            entity.setPortal(masterDataService.getPortalByCode(dto.getPortal().getCode()));
        }
        if (dto.getProfileStatus() != null) {
            entity.setProfileStatus(masterDataService.getProfileStatusByCode(dto.getProfileStatus().getCode()));
        }
        if (dto.getReturnSupport() != null) {
            entity.setReturnSupport(masterDataService.getReturnSupportByCode(dto.getReturnSupport().getCode()));
        }
        if (dto.getSectioning() != null) {
            entity.setSectioning(masterDataService.getSectioningByCode(dto.getSectioning().getCode()));
        }

        // 2. Sincronización de la colección de Cantilevers
        linkCollection(
                dto.getCantilevers(),
                entity.getCantilevers(),
                entity,
                Cantilever::setProfile,
                true
        );

        // 3. Sincronización de relación 1:1 con Disconnector (Bidireccional)
        if (entity.getDisconnector() != null) {
            linkEntity(entity.getDisconnector(), entity, Disconnector::setProfile);
        }

    }


    @AfterMapping
    protected void mapEntityToDto(Profile entity, @MappingTarget ProfileDTO dto) {

        // 4. Enriquecimiento del DTO con LOVs usando la caché del MasterDataService
        if (entity.getAnchorage() != null) {
            dto.setAnchorage(masterDataService.getAnchorageByIdAndMapToDTO(entity.getAnchorage().getId()));
        }
        if (entity.getAnchorageFoundation() != null) {
            dto.setAnchorageFoundation(masterDataService.getAnchorageFoundationByIdAndMapToDTO(entity.getAnchorageFoundation().getId()));
        }
        if (entity.getFoundation() != null) {
            dto.setFoundation(masterDataService.getFoundationByIdAndMapToDTO(entity.getFoundation().getId()));
        }
        if (entity.getPoleType() != null) {
            dto.setPoleType(masterDataService.getPoleTypeByIdAndMapToDTO(entity.getPoleType().getId()));
        }
        if (entity.getPortal() != null) {
            dto.setPortal(masterDataService.getPortalByIdAndMapToDTO(entity.getPortal().getId()));
        }
        if (entity.getProfileStatus() != null) {
            dto.setProfileStatus(masterDataService.getProfileStatusByIdAndMapToDTO(entity.getProfileStatus().getId()));
        }
        if (entity.getReturnSupport() != null) {
            dto.setReturnSupport(masterDataService.getReturnSupportByIdAndMapToDTO(entity.getReturnSupport().getId()));
        }
        if (entity.getSectioning() != null) {
            dto.setSectioning(masterDataService.getSectioningByIdAndMapToDTO(entity.getSectioning().getId()));
        }
    }
}
