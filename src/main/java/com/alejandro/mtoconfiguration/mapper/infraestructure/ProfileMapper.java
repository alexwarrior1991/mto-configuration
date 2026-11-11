package com.alejandro.mtoconfiguration.mapper.infraestructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.Cantilever;
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
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.DisconnectorDTO;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import org.mapstruct.AfterMapping;
import org.mapstruct.InheritConfiguration;
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

    /** Los seccionadores del perfil, en {@code mapEntityToDto}; el sufijo, por lo mismo que arriba. */
    @Autowired
    protected DisconnectorMapper disconnectorChildMapper;

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
    @Mapping(target = "assemblyConfiguration", ignore = true)
    @Mapping(target = "sectionings", ignore = true)
    @Mapping(target = "sectioningFeedings", ignore = true)
    @Mapping(target = "disconnectors", ignore = true) // en orden, en mapEntityToDto
    @Mapping(target = "disconnector", ignore = true) // el primero de disconnectors
    public abstract ProfileDTO toDTO(Profile entity);

    /**
     * Hereda las reglas de {@code toDTO}: el detalle y el final de un alta o una modificacion
     * vuelcan la entidad con este metodo, no con {@code toDTO} (ver {@code BaseMapper}).
     */
    @Override
    @InheritConfiguration(name = "toDTO")
    public abstract void updateDTOFromEntity(Profile entity, @MappingTarget ProfileDTO dto);

    /**
     * El seccionador no se escribe desde el perfil: el vinculo es suyo ({@code disconnector.profile_id},
     * del que el perfil es el lado inverso) y sus datos tambien, asi que se cambia con su propio
     * recurso. Aqui es solo de salida, como {@code profileCode} y {@code profileKp} en el seccionador
     * (README_API.md §4). Antes lo escribia el codigo generado, y mal: otro seccionador copiaba sus
     * datos sobre el que ya colgaba, o creaba una copia si no colgaba ninguno, y {@code null} lo
     * quitaba de la respuesta pero no de la base.
     */
    @Override
    @Mapping(target = "track", source = "trackId")
    @Mapping(target = "cantilevers", ignore = true) // se reconcilia en mapDtoToEntity
    @Mapping(target = "disconnectors", ignore = true) // solo de salida (ver arriba)
    @Mapping(target = "anchorages", ignore = true)
    @Mapping(target = "anchorageFoundation", ignore = true)
    @Mapping(target = "foundation", ignore = true)
    @Mapping(target = "poleType", ignore = true)
    @Mapping(target = "portal", ignore = true)
    @Mapping(target = "profileStatus", ignore = true)
    @Mapping(target = "returnSupport", ignore = true)
    @Mapping(target = "supportType", ignore = true)
    @Mapping(target = "assemblyConfiguration", ignore = true)
    @Mapping(target = "sectionings", ignore = true)
    @Mapping(target = "sectioningFeedings", ignore = true)
    @ToEntityIgnoreAudit
    public abstract Profile toEntity(ProfileDTO dto);

    @Override
    @Mapping(target = "track", source = "trackId")
    @Mapping(target = "cantilevers", ignore = true) // se reconcilia en mapDtoToEntity
    @Mapping(target = "disconnectors", ignore = true) // solo de salida (ver toEntity)
    @Mapping(target = "anchorages", ignore = true)
    @Mapping(target = "anchorageFoundation", ignore = true)
    @Mapping(target = "foundation", ignore = true)
    @Mapping(target = "poleType", ignore = true)
    @Mapping(target = "portal", ignore = true)
    @Mapping(target = "profileStatus", ignore = true)
    @Mapping(target = "returnSupport", ignore = true)
    @Mapping(target = "supportType", ignore = true)
    @Mapping(target = "assemblyConfiguration", ignore = true)
    @Mapping(target = "sectionings", ignore = true)
    @Mapping(target = "sectioningFeedings", ignore = true)
    @ToEntityIgnoreAudit
    public abstract void updateEntityFromDTO(ProfileDTO dto, @MappingTarget Profile entity);

    @AfterMapping
    protected void mapDtoToEntity(ProfileDTO dto, @MappingTarget Profile entity) {


        // 1. Resolución de múltiples LOVs usando MasterDataService
        if (dto.getAnchorages() != null) {
            Set<Anchorage> resolved = new LinkedHashSet<>();
            int index = 0;
            for (AnchorageDTO each : dto.getAnchorages()) {
                String field = "anchorages[" + index++ + "]";
                // Un hueco, o una referencia vacia, no cuenta; una informada tiene que existir.
                Anchorage anchorage = each == null ? null : resolveLov(each, field,
                        masterDataService::getAnchorageByCode, masterDataService::getAnchorageByIdAndMapToDTO);
                if (anchorage != null) {
                    resolved.add(anchorage);
                }
            }
            if (!sameEntities(resolved, entity.getAnchorages())) { // ver BaseMapper.sameEntities
                entity.setAnchorages(resolved);
            }
        }
        if (dto.getAnchorageFoundation() != null) {
            entity.setAnchorageFoundation(resolveLov(dto.getAnchorageFoundation(), "anchorageFoundation",
                    masterDataService::getAnchorageFoundationByCode, masterDataService::getAnchorageFoundationByIdAndMapToDTO));
        }
        if (dto.getFoundation() != null) {
            entity.setFoundation(resolveLov(dto.getFoundation(), "foundation",
                    masterDataService::getFoundationByCode, masterDataService::getFoundationByIdAndMapToDTO));
        }
        if (dto.getPoleType() != null) {
            entity.setPoleType(resolveLov(dto.getPoleType(), "poleType",
                    masterDataService::getPoleTypeByCode, masterDataService::getPoleTypeByIdAndMapToDTO));
        }
        if (dto.getPortal() != null) {
            entity.setPortal(resolveLov(dto.getPortal(), "portal",
                    masterDataService::getPortalByCode, masterDataService::getPortalByIdAndMapToDTO));
        }
        if (dto.getProfileStatus() != null) {
            entity.setProfileStatus(resolveLov(dto.getProfileStatus(), "profileStatus",
                    masterDataService::getProfileStatusByCode, masterDataService::getProfileStatusByIdAndMapToDTO));
        }
        if (dto.getSupportType() != null) {
            entity.setSupportType(resolveLov(dto.getSupportType(), "supportType",
                    masterDataService::getSupportTypeByCode, masterDataService::getSupportTypeByIdAndMapToDTO));
        }
        if (dto.getAssemblyConfiguration() != null) {
            entity.setAssemblyConfiguration(resolveLov(dto.getAssemblyConfiguration(), "assemblyConfiguration",
                    masterDataService::getAssemblyConfigurationByCode, masterDataService::getAssemblyConfigurationByIdAndMapToDTO));
        }
        if (dto.getReturnSupport() != null) {
            entity.setReturnSupport(resolveLov(dto.getReturnSupport(), "returnSupport",
                    masterDataService::getReturnSupportByCode, masterDataService::getReturnSupportByIdAndMapToDTO));
        }
        if (dto.getSectionings() != null) {
            // Se reemplaza el conjunto entero, no se anade: mandar la lista es declarar cuales
            // son TODOS los seccionamientos del perfil, igual que en el resto de la API.
            Set<Sectioning> resolved = new LinkedHashSet<>();
            int index = 0;
            for (SectioningDTO each : dto.getSectionings()) {
                String field = "sectionings[" + index++ + "]";
                // Un hueco, o una referencia vacia, no cuenta; una informada tiene que existir.
                Sectioning sectioning = each == null ? null : resolveLov(each, field,
                        masterDataService::getSectioningByCode, masterDataService::getSectioningByIdAndMapToDTO);
                if (sectioning != null) {
                    resolved.add(sectioning);
                }
            }
            if (!sameEntities(resolved, entity.getSectionings())) { // ver BaseMapper.sameEntities
                entity.setSectionings(resolved);
            }
        }
        if (dto.getSectioningFeedings() != null) {
            Set<DisconnectorFunction> resolved = new LinkedHashSet<>();
            int index = 0;
            for (DisconnectorFunctionDTO each : dto.getSectioningFeedings()) {
                String field = "sectioningFeedings[" + index++ + "]";
                // Un hueco, o una referencia vacia, no cuenta; una informada tiene que existir.
                DisconnectorFunction function = each == null ? null : resolveLov(each, field,
                        masterDataService::getDisconnectorFunctionByCode, masterDataService::getDisconnectorFunctionByIdAndMapToDTO);
                if (function != null) {
                    resolved.add(function);
                }
            }
            if (!sameEntities(resolved, entity.getSectioningFeedings())) { // ver BaseMapper.sameEntities
                entity.setSectioningFeedings(resolved);
            }
        }

        // 2. Reconciliación de la colección de Cantilevers.
        //
        // mergeCollection y no linkCollection: la mensula que el cliente devuelve con su id tiene
        // que actualizar ESA fila. Añadirla, que es lo que hacia el codigo generado, insertaba una
        // copia sin id junto a la original y dejaba la original sin los cambios.
        mergeCollection(
                "cantilevers",
                dto.getCantilevers(),
                entity.getCantilevers(),
                entity,
                cantileverChildMapper::toEntity,
                (childDto, child) -> cantileverChildMapper.updateEntityFromDTO(childDto, child),
                Cantilever::setProfile
        );
    }


    @AfterMapping
    protected void mapEntityToDto(Profile entity, @MappingTarget ProfileDTO dto) {

        // Los seccionadores, por nombre (V29), y el primero en «disconnector».
        List<DisconnectorDTO> disconnectors = entity.disconnectorsByName().stream()
                .map(disconnectorChildMapper::toDTO)
                .collect(Collectors.toCollection(ArrayList::new));
        dto.setDisconnectors(disconnectors);
        dto.setDisconnector(disconnectors.isEmpty() ? null : disconnectors.getFirst());

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
        if (entity.getAssemblyConfiguration() != null) {
            dto.setAssemblyConfiguration(masterDataService.getAssemblyConfigurationByIdAndMapToDTO(
                    entity.getAssemblyConfiguration().getId()));
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
