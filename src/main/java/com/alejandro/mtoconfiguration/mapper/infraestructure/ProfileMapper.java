package com.alejandro.mtoconfiguration.mapper.infraestructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.Cantilever;
import com.alejandro.mtoconfiguration.entity.infrastructure.Disconnector;
import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.mapper.commons.BaseMapper;
import com.alejandro.mtoconfiguration.mapper.commons.CentralConfigMapper;
import com.alejandro.mtoconfiguration.mapper.commons.ReferenceMapper;
import com.alejandro.mtoconfiguration.mapper.commons.ToEntityIgnoreAudit;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ProfileDTO;
import com.alejandro.mtoconfiguration.service.commons.MasterDataService;
import com.alejandro.mtoconfiguration.entity.lov.Sectioning;
import com.alejandro.mtoconfiguration.model.synchronous.lov.SectioningDTO;
import com.alejandro.mtoconfiguration.entity.lov.Anchorage;
import com.alejandro.mtoconfiguration.model.synchronous.lov.AnchorageDTO;
import com.alejandro.mtoconfiguration.entity.lov.DisconnectorFunction;
import com.alejandro.mtoconfiguration.model.synchronous.lov.DisconnectorFunctionDTO;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

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

    /**
     * Hace falta aqui, y no solo en el {@code uses} del @Mapper, porque la reconciliacion de
     * mensulas vive en el {@code @AfterMapping} de esta clase y necesita volcar cada DTO sobre la
     * mensula que ya existe.
     *
     * <p>El nombre lleva el sufijo {@code Child} a proposito: el impl generado declara su propio
     * campo {@code cantileverMapper}, y un campo del mismo nombre en la subclase <b>sombrea</b> a
     * este. Spring inyecta los dos, pero cualquier cableado por reflexion —un test que instancia
     * el impl a mano— alcanzaria solo el de la subclase y dejaria este a null.
     */
    @Autowired
    protected CantileverMapper cantileverChildMapper;

    @Override
    @Mapping(target = "trackId", source = "track.id")
    @Mapping(target = "anchorages", ignore = true)
    @Mapping(target = "anchorageFoundation", ignore = true)
    @Mapping(target = "foundation", ignore = true)
    @Mapping(target = "poleType", ignore = true)
    @Mapping(target = "portal", ignore = true)
    @Mapping(target = "profileStatus", ignore = true)
    @Mapping(target = "returnSupport", ignore = true)
    @Mapping(target = "supportType", ignore = true)
    @Mapping(target = "sectionings", ignore = true)
    @Mapping(target = "sectioningFeedings", ignore = true)
    public abstract ProfileDTO toDTO(Profile entity);

    @Override
    @Mapping(target = "track", source = "trackId")
    @Mapping(target = "cantilevers", ignore = true) // se reconcilia en mapDtoToEntity
    @Mapping(target = "anchorages", ignore = true)
    @Mapping(target = "anchorageFoundation", ignore = true)
    @Mapping(target = "foundation", ignore = true)
    @Mapping(target = "poleType", ignore = true)
    @Mapping(target = "portal", ignore = true)
    @Mapping(target = "profileStatus", ignore = true)
    @Mapping(target = "returnSupport", ignore = true)
    @Mapping(target = "supportType", ignore = true)
    @Mapping(target = "sectionings", ignore = true)
    @Mapping(target = "sectioningFeedings", ignore = true)
    @ToEntityIgnoreAudit
    public abstract Profile toEntity(ProfileDTO dto);

    @Override
    @Mapping(target = "track", source = "trackId")
    @Mapping(target = "cantilevers", ignore = true) // se reconcilia en mapDtoToEntity
    @Mapping(target = "anchorages", ignore = true)
    @Mapping(target = "anchorageFoundation", ignore = true)
    @Mapping(target = "foundation", ignore = true)
    @Mapping(target = "poleType", ignore = true)
    @Mapping(target = "portal", ignore = true)
    @Mapping(target = "profileStatus", ignore = true)
    @Mapping(target = "returnSupport", ignore = true)
    @Mapping(target = "supportType", ignore = true)
    @Mapping(target = "sectionings", ignore = true)
    @Mapping(target = "sectioningFeedings", ignore = true)
    @ToEntityIgnoreAudit
    public abstract void updateEntityFromDTO(ProfileDTO dto, @MappingTarget Profile entity);

    @AfterMapping
    protected void mapDtoToEntity(ProfileDTO dto, @MappingTarget Profile entity) {


        // 1. Resolución de múltiples LOVs usando MasterDataService
        if (dto.getAnchorages() != null) {
            Set<Anchorage> resolved = new LinkedHashSet<>();
            for (AnchorageDTO each : dto.getAnchorages()) {
                if (each == null || each.getCode() == null) {
                    continue;
                }
                Anchorage anchorage = masterDataService.getAnchorageByCode(each.getCode());
                if (anchorage != null) {
                    resolved.add(anchorage);
                }
            }
            entity.setAnchorages(resolved);
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
        if (dto.getSupportType() != null) {
            entity.setSupportType(masterDataService.getSupportTypeByCode(dto.getSupportType().getCode()));
        }
        if (dto.getReturnSupport() != null) {
            entity.setReturnSupport(masterDataService.getReturnSupportByCode(dto.getReturnSupport().getCode()));
        }
        if (dto.getSectionings() != null) {
            // Se reemplaza el conjunto entero, no se anade: mandar la lista es declarar cuales
            // son TODOS los seccionamientos del perfil, igual que en el resto de la API.
            Set<Sectioning> resolved = new LinkedHashSet<>();
            for (SectioningDTO each : dto.getSectionings()) {
                if (each == null || each.getCode() == null) {
                    continue;
                }
                Sectioning sectioning = masterDataService.getSectioningByCode(each.getCode());
                if (sectioning != null) {
                    resolved.add(sectioning);
                }
            }
            entity.setSectionings(resolved);
        }
        if (dto.getSectioningFeedings() != null) {
            Set<DisconnectorFunction> resolved = new LinkedHashSet<>();
            for (DisconnectorFunctionDTO each : dto.getSectioningFeedings()) {
                if (each == null || each.getCode() == null) {
                    continue;
                }
                DisconnectorFunction function =
                        masterDataService.getDisconnectorFunctionByCode(each.getCode());
                if (function != null) {
                    resolved.add(function);
                }
            }
            entity.setSectioningFeedings(resolved);
        }

        // 2. Reconciliación de la colección de Cantilevers.
        //
        // mergeCollection y no linkCollection: la mensula que el cliente devuelve con su id tiene
        // que actualizar ESA fila. Añadirla, que es lo que hacia el codigo generado, insertaba una
        // copia sin id junto a la original y dejaba la original sin los cambios.
        mergeCollection(
                dto.getCantilevers(),
                entity.getCantilevers(),
                entity,
                cantileverChildMapper::toEntity,
                (childDto, child) -> cantileverChildMapper.updateEntityFromDTO(childDto, child),
                Cantilever::setProfile
        );

        // 3. Sincronización de relación 1:1 con Disconnector (Bidireccional)
        if (entity.getDisconnector() != null) {
            linkEntity(entity.getDisconnector(), entity, Disconnector::setProfile);
        }

    }


    @AfterMapping
    protected void mapEntityToDto(Profile entity, @MappingTarget ProfileDTO dto) {

        // 4. Enriquecimiento del DTO con LOVs usando la caché del MasterDataService
        if (entity.getAnchorages() != null) {
            dto.setAnchorages(entity.getAnchorages().stream()
                    .map(each -> masterDataService.getAnchorageByIdAndMapToDTO(each.getId()))
                    .filter(Objects::nonNull)
                    .toList());
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
        if (entity.getSupportType() != null) {
            dto.setSupportType(masterDataService.getSupportTypeByIdAndMapToDTO(entity.getSupportType().getId()));
        }
        if (entity.getReturnSupport() != null) {
            dto.setReturnSupport(masterDataService.getReturnSupportByIdAndMapToDTO(entity.getReturnSupport().getId()));
        }
        if (entity.getSectionings() != null) {
            dto.setSectionings(entity.getSectionings().stream()
                    .map(each -> masterDataService.getSectioningByIdAndMapToDTO(each.getId()))
                    .filter(Objects::nonNull)
                    .toList());
        }
        if (entity.getSectioningFeedings() != null) {
            dto.setSectioningFeedings(entity.getSectioningFeedings().stream()
                    .map(each -> masterDataService.getDisconnectorFunctionByIdAndMapToDTO(each.getId()))
                    .filter(Objects::nonNull)
                    .toList());
        }
    }
}
