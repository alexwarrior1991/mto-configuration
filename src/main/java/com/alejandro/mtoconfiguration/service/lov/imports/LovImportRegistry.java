package com.alejandro.mtoconfiguration.service.lov.imports;

import com.alejandro.mtoconfiguration.model.synchronous.lov.AnchorageDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.AnchorageFoundationDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.AnchorageFoundationTypeDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.CantileverTypeDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.DisconnectorFunctionDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.FoundationDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.FoundationTypeDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.PoleTypeDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.PortalDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.PortalTypeDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.ReturnSupportDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.SectioningDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.SteadyArmTypeDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.SupportTypeDTO;
import com.alejandro.mtoconfiguration.repository.jpa.lov.AnchorageFoundationRepository;
import com.alejandro.mtoconfiguration.repository.jpa.lov.AnchorageFoundationTypeRepository;
import com.alejandro.mtoconfiguration.repository.jpa.lov.AnchorageRepository;
import com.alejandro.mtoconfiguration.repository.jpa.lov.CantileverTypeRepository;
import com.alejandro.mtoconfiguration.repository.jpa.lov.DisconnectorFunctionRepository;
import com.alejandro.mtoconfiguration.repository.jpa.lov.FoundationRepository;
import com.alejandro.mtoconfiguration.repository.jpa.lov.FoundationTypeRepository;
import com.alejandro.mtoconfiguration.repository.jpa.lov.PoleTypeRepository;
import com.alejandro.mtoconfiguration.repository.jpa.lov.PortalRepository;
import com.alejandro.mtoconfiguration.repository.jpa.lov.PortalTypeRepository;
import com.alejandro.mtoconfiguration.repository.jpa.lov.ReturnSupportRepository;
import com.alejandro.mtoconfiguration.repository.jpa.lov.SectioningRepository;
import com.alejandro.mtoconfiguration.repository.jpa.lov.SteadyArmTypeRepository;
import com.alejandro.mtoconfiguration.repository.jpa.lov.SupportTypeRepository;
import com.alejandro.mtoconfiguration.service.lov.AnchorageFoundationService;
import com.alejandro.mtoconfiguration.service.lov.AnchorageFoundationTypeService;
import com.alejandro.mtoconfiguration.service.lov.AnchorageService;
import com.alejandro.mtoconfiguration.service.lov.CantileverTypeService;
import com.alejandro.mtoconfiguration.service.lov.DisconnectorFunctionService;
import com.alejandro.mtoconfiguration.service.lov.FoundationService;
import com.alejandro.mtoconfiguration.service.lov.FoundationTypeService;
import com.alejandro.mtoconfiguration.service.lov.PoleTypeService;
import com.alejandro.mtoconfiguration.service.lov.PortalService;
import com.alejandro.mtoconfiguration.service.lov.PortalTypeService;
import com.alejandro.mtoconfiguration.service.lov.ReturnSupportService;
import com.alejandro.mtoconfiguration.service.lov.SectioningService;
import com.alejandro.mtoconfiguration.service.lov.SteadyArmTypeService;
import com.alejandro.mtoconfiguration.service.lov.SupportTypeService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Traduce el nombre de entidad del catalogo maestro al servicio, repositorio y DTO
 * correspondientes.
 *
 * <p>El orden del mapa importa: los catalogos {@code *Type} van primero porque
 * {@code Foundation}, {@code Portal} y {@code AnchorageFoundation} tienen una relacion
 * <b>obligatoria</b> hacia ellos, que {@code LovRelationResolver.resolveRequired}
 * resuelve por codigo en {@code beforeCreate}. Si se cargasen despues, todas esas
 * filas fallarian.
 *
 * <p>Anadir una entidad al alcance del importador es anadir una linea aqui.
 */
@Component
public class LovImportRegistry {

    private final Map<String, LovImportTarget<?, ?>> targets = new LinkedHashMap<>();

    @SuppressWarnings("java:S107") // Un parametro por entidad LOV: la alternativa es reflexion.
    public LovImportRegistry(
            FoundationTypeService foundationTypeService, FoundationTypeRepository foundationTypeRepository,
            PortalTypeService portalTypeService, PortalTypeRepository portalTypeRepository,
            AnchorageFoundationTypeService anchorageFoundationTypeService,
            AnchorageFoundationTypeRepository anchorageFoundationTypeRepository,
            SectioningService sectioningService, SectioningRepository sectioningRepository,
            AnchorageService anchorageService, AnchorageRepository anchorageRepository,
            AnchorageFoundationService anchorageFoundationService,
            AnchorageFoundationRepository anchorageFoundationRepository,
            FoundationService foundationService, FoundationRepository foundationRepository,
            PoleTypeService poleTypeService, PoleTypeRepository poleTypeRepository,
            PortalService portalService, PortalRepository portalRepository,
            SupportTypeService supportTypeService, SupportTypeRepository supportTypeRepository,
            CantileverTypeService cantileverTypeService, CantileverTypeRepository cantileverTypeRepository,
            SteadyArmTypeService steadyArmTypeService, SteadyArmTypeRepository steadyArmTypeRepository,
            ReturnSupportService returnSupportService, ReturnSupportRepository returnSupportRepository,
            DisconnectorFunctionService disconnectorFunctionService,
            DisconnectorFunctionRepository disconnectorFunctionRepository
    ) {
        // --- Catalogos de tipo. Primero, por la relacion obligatoria.
        register(new LovImportTarget<>("FoundationType", foundationTypeService,
                foundationTypeRepository, FoundationTypeDTO::new, null, null));
        register(new LovImportTarget<>("PortalType", portalTypeService,
                portalTypeRepository, PortalTypeDTO::new, null, null));
        register(new LovImportTarget<>("AnchorageFoundationType", anchorageFoundationTypeService,
                anchorageFoundationTypeRepository, AnchorageFoundationTypeDTO::new, null, null));

        // --- Entidades LOV del alcance.
        register(new LovImportTarget<>("Sectioning", sectioningService,
                sectioningRepository, SectioningDTO::new, null, null));
        register(new LovImportTarget<>("Anchorage", anchorageService,
                anchorageRepository, AnchorageDTO::new, AnchorageDTO::setDrawingNumber, null));
        register(new LovImportTarget<>("AnchorageFoundation", anchorageFoundationService,
                anchorageFoundationRepository, AnchorageFoundationDTO::new,
                AnchorageFoundationDTO::setDrawingNumber,
                (dto, code) -> dto.setAnchorageFoundationType(typeDto(code, new AnchorageFoundationTypeDTO()))));
        register(new LovImportTarget<>("Foundation", foundationService,
                foundationRepository, FoundationDTO::new, FoundationDTO::setDrawingNumber,
                (dto, code) -> dto.setFoundationType(typeDto(code, new FoundationTypeDTO()))));
        register(new LovImportTarget<>("PoleType", poleTypeService,
                poleTypeRepository, PoleTypeDTO::new, PoleTypeDTO::setDrawingNumber, null));
        register(new LovImportTarget<>("Portal", portalService,
                portalRepository, PortalDTO::new, PortalDTO::setDrawingNumber,
                (dto, code) -> dto.setPortalType(typeDto(code, new PortalTypeDTO()))));
        register(new LovImportTarget<>("SupportType", supportTypeService,
                supportTypeRepository, SupportTypeDTO::new, SupportTypeDTO::setDrawingNumber, null));
        register(new LovImportTarget<>("CantileverType", cantileverTypeService,
                cantileverTypeRepository, CantileverTypeDTO::new, CantileverTypeDTO::setDrawingNumber, null));
        register(new LovImportTarget<>("SteadyArmType", steadyArmTypeService,
                steadyArmTypeRepository, SteadyArmTypeDTO::new, null, null));
        register(new LovImportTarget<>("ReturnSupport", returnSupportService,
                returnSupportRepository, ReturnSupportDTO::new, ReturnSupportDTO::setDrawingNumber, null));
        register(new LovImportTarget<>("DisconnectorFunction", disconnectorFunctionService,
                disconnectorFunctionRepository, DisconnectorFunctionDTO::new, null, null));
    }

    /**
     * Referencia a un {@code *Type} por codigo. {@code LovRelationResolver} resuelve
     * primero por id y despues por codigo; aqui solo se dispone del codigo.
     */
    private static <T extends com.alejandro.mtoconfiguration.model.commons.LovDTO> T typeDto(String code, T dto) {
        dto.setCode(code);
        return dto;
    }

    private void register(LovImportTarget<?, ?> target) {
        targets.put(target.entityName(), target);
    }

    public Optional<LovImportTarget<?, ?>> find(String entityName) {
        return Optional.ofNullable(targets.get(entityName));
    }

    /** Entidades soportadas, en orden de carga. */
    public List<String> supportedEntities() {
        return List.copyOf(targets.keySet());
    }
}
