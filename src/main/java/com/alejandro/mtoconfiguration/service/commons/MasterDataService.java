package com.alejandro.mtoconfiguration.service.commons;

import com.alejandro.mtoconfiguration.core.exception.BaseException;
import com.alejandro.mtoconfiguration.entity.commons.BaseEntity;
import com.alejandro.mtoconfiguration.entity.lov.*;
import com.alejandro.mtoconfiguration.entity.lov.commons.Lov;
import com.alejandro.mtoconfiguration.mapper.lov.*;
import com.alejandro.mtoconfiguration.mapper.lov.commons.LovMapper;
import com.alejandro.mtoconfiguration.model.commons.LovDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.*;
import com.alejandro.mtoconfiguration.repository.jpa.lov.*;
import com.alejandro.mtoconfiguration.repository.jpa.lov.commons.LovRepository;
import com.alejandro.mtoconfiguration.utils.Utils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MasterDataService {

    private static final String CACHE_ITEM = "mto.semi.configuration.item";

    // Repositories
    private final AnchorageFoundationRepository anchorageFoundationRepository;
    private final AnchorageFoundationTypeRepository anchorageFoundationTypeRepository;
    private final AnchorageRepository anchorageRepository;
    private final CantileverTypeRepository cantileverTypeRepository;
    private final ComercialEntityTypeRepository comercialEntityTypeRepository;
    private final DisconnectorFunctionRepository disconnectorFunctionRepository;
    private final FoundationRepository foundationRepository;
    private final FoundationTypeRepository foundationTypeRepository;
    private final PoleTypeRepository poleTypeRepository;
    private final PortalRepository portalRepository;
    private final PortalTypeRepository portalTypeRepository;
    private final ProfileStatusRepository profileStatusRepository;
    private final ReturnSupportRepository returnSupportRepository;
    private final SectioningRepository sectioningRepository;
    private final SteadyArmTypeRepository steadyArmTypeRepository;
    private final SupportTypeRepository supportTypeRepository;

    // Mappers
    private final AnchorageFoundationMapper anchorageFoundationMapper;
    private final AnchorageFoundationTypeMapper anchorageFoundationTypeMapper;
    private final AnchorageMapper anchorageMapper;
    private final CantileverTypeMapper cantileverTypeMapper;
    private final ComercialEntityTypeMapper comercialEntityTypeMapper;
    private final DisconnectorFunctionMapper disconnectorFunctionMapper;
    private final FoundationMapper foundationMapper;
    private final FoundationTypeMapper foundationTypeMapper;
    private final PoleTypeMapper poleTypeMapper;
    private final PortalMapper portalMapper;
    private final PortalTypeMapper portalTypeMapper;
    private final ProfileStatusMapper profileStatusMapper;
    private final ReturnSupportMapper returnSupportMapper;
    private final SectioningMapper sectioningMapper;
    private final SteadyArmTypeMapper steadyArmTypeMapper;
    private final SupportTypeMapper supportTypeMapper;

    private <E extends Lov> E getEntityByCode(String code, LovRepository<E> repository) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        E entity = repository.findByCode(code);
        if (entity != null) {
            Hibernate.initialize(entity);
        }
        return entity;
    }

    private <E extends Lov> E getEntityById(Long id, LovRepository<E> repository) {
        if (id == null) {
            return null;
        }
        return repository.findById(id).orElse(null);
    }

    private <E extends Lov, D extends LovDTO> D getEntityByIdAndMap(Long id, LovRepository<E> repository, LovMapper<D, E> mapper) {
        if (id == null) {
            return null;
        }
        return repository.findById(id)
                .map(mapper::toDTO)
                .orElse(null);
    }


    @Cacheable(value = CACHE_ITEM, keyGenerator = "cacheCustomKeyGenerator", unless = "#result == null ")
    public AnchorageFoundation getAnchorageFoundationByCode(String code) {
        return getEntityByCode(code, anchorageFoundationRepository);
    }

    @Cacheable(value = CACHE_ITEM, keyGenerator = "cacheCustomKeyGenerator", unless = "#result == null ")
    public AnchorageFoundationDTO getAnchorageFoundationByIdAndMapToDTO(Long id) {
        return getEntityByIdAndMap(id, anchorageFoundationRepository, anchorageFoundationMapper);
    }

    @Cacheable(value = CACHE_ITEM, keyGenerator = "cacheCustomKeyGenerator", unless = "#result == null ")
    public AnchorageFoundationType getAnchorageFoundationTypeByCode(String code) {
        return getEntityByCode(code, anchorageFoundationTypeRepository);
    }

    @Cacheable(value = CACHE_ITEM, keyGenerator = "cacheCustomKeyGenerator", unless = "#result == null ")
    public AnchorageFoundationTypeDTO getAnchorageFoundationTypeByIdAndMapToDTO(Long id) {
        return getEntityByIdAndMap(id, anchorageFoundationTypeRepository, anchorageFoundationTypeMapper);
    }

    @Cacheable(value = CACHE_ITEM, keyGenerator = "cacheCustomKeyGenerator", unless = "#result == null ")
    public Anchorage getAnchorageByCode(String code) {
        return getEntityByCode(code, anchorageRepository);
    }

    @Cacheable(value = CACHE_ITEM, keyGenerator = "cacheCustomKeyGenerator", unless = "#result == null ")
    public AnchorageDTO getAnchorageByIdAndMapToDTO(Long id) {
        return getEntityByIdAndMap(id, anchorageRepository, anchorageMapper);
    }

    // --- Cantilever Type ---

    @Cacheable(value = CACHE_ITEM, keyGenerator = "cacheCustomKeyGenerator", unless = "#result == null ")
    public CantileverType getCantileverTypeByCode(String code) {
        return getEntityByCode(code, cantileverTypeRepository);
    }

    @Cacheable(value = CACHE_ITEM, keyGenerator = "cacheCustomKeyGenerator", unless = "#result == null ")
    public CantileverTypeDTO getCantileverTypeByIdAndMapToDTO(Long id) {
        return getEntityByIdAndMap(id, cantileverTypeRepository, cantileverTypeMapper);
    }

    // --- Comercial Entity Type ---

    @Cacheable(value = CACHE_ITEM, keyGenerator = "cacheCustomKeyGenerator", unless = "#result == null ")
    public ComercialEntityType getComercialEntityTypeByCode(String code) {
        return getEntityByCode(code, comercialEntityTypeRepository);
    }

    @Cacheable(value = CACHE_ITEM, keyGenerator = "cacheCustomKeyGenerator", unless = "#result == null ")
    public ComercialEntityTypeDTO getComercialEntityTypeByIdAndMapToDTO(Long id) {
        return getEntityByIdAndMap(id, comercialEntityTypeRepository, comercialEntityTypeMapper);
    }

    // --- Disconnector Function ---

    @Cacheable(value = CACHE_ITEM, keyGenerator = "cacheCustomKeyGenerator", unless = "#result == null ")
    public DisconnectorFunction getDisconnectorFunctionByCode(String code) {
        return getEntityByCode(code, disconnectorFunctionRepository);
    }

    @Cacheable(value = CACHE_ITEM, keyGenerator = "cacheCustomKeyGenerator", unless = "#result == null ")
    public DisconnectorFunctionDTO getDisconnectorFunctionByIdAndMapToDTO(Long id) {
        return getEntityByIdAndMap(id, disconnectorFunctionRepository, disconnectorFunctionMapper);
    }

    // --- Foundation ---

    @Cacheable(value = CACHE_ITEM, keyGenerator = "cacheCustomKeyGenerator", unless = "#result == null ")
    public Foundation getFoundationByCode(String code) {
        return getEntityByCode(code, foundationRepository);
    }

    @Cacheable(value = CACHE_ITEM, keyGenerator = "cacheCustomKeyGenerator", unless = "#result == null ")
    public FoundationDTO getFoundationByIdAndMapToDTO(Long id) {
        return getEntityByIdAndMap(id, foundationRepository, foundationMapper);
    }

    // --- Foundation Type ---

    @Cacheable(value = CACHE_ITEM, keyGenerator = "cacheCustomKeyGenerator", unless = "#result == null ")
    public FoundationType getFoundationTypeByCode(String code) {
        return getEntityByCode(code, foundationTypeRepository);
    }

    @Cacheable(value = CACHE_ITEM, keyGenerator = "cacheCustomKeyGenerator", unless = "#result == null ")
    public FoundationTypeDTO getFoundationTypeByIdAndMapToDTO(Long id) {
        return getEntityByIdAndMap(id, foundationTypeRepository, foundationTypeMapper);
    }

    // --- Pole Type ---

    @Cacheable(value = CACHE_ITEM, keyGenerator = "cacheCustomKeyGenerator", unless = "#result == null ")
    public PoleType getPoleTypeByCode(String code) {
        return getEntityByCode(code, poleTypeRepository);
    }

    @Cacheable(value = CACHE_ITEM, keyGenerator = "cacheCustomKeyGenerator", unless = "#result == null ")
    public PoleTypeDTO getPoleTypeByIdAndMapToDTO(Long id) {
        return getEntityByIdAndMap(id, poleTypeRepository, poleTypeMapper);
    }

    // --- Portal ---

    @Cacheable(value = CACHE_ITEM, keyGenerator = "cacheCustomKeyGenerator", unless = "#result == null ")
    public Portal getPortalByCode(String code) {
        return getEntityByCode(code, portalRepository);
    }

    @Cacheable(value = CACHE_ITEM, keyGenerator = "cacheCustomKeyGenerator", unless = "#result == null ")
    public PortalDTO getPortalByIdAndMapToDTO(Long id) {
        return getEntityByIdAndMap(id, portalRepository, portalMapper);
    }

    // --- Portal Type ---

    @Cacheable(value = CACHE_ITEM, keyGenerator = "cacheCustomKeyGenerator", unless = "#result == null ")
    public PortalType getPortalTypeByCode(String code) {
        return getEntityByCode(code, portalTypeRepository);
    }

    @Cacheable(value = CACHE_ITEM, keyGenerator = "cacheCustomKeyGenerator", unless = "#result == null ")
    public PortalTypeDTO getPortalTypeByIdAndMapToDTO(Long id) {
        return getEntityByIdAndMap(id, portalTypeRepository, portalTypeMapper);
    }

    // --- Profile Status ---

    @Cacheable(value = CACHE_ITEM, keyGenerator = "cacheCustomKeyGenerator", unless = "#result == null ")
    public ProfileStatus getProfileStatusByCode(String code) {
        return getEntityByCode(code, profileStatusRepository);
    }

    @Cacheable(value = CACHE_ITEM, keyGenerator = "cacheCustomKeyGenerator", unless = "#result == null ")
    public ProfileStatusDTO getProfileStatusByIdAndMapToDTO(Long id) {
        return getEntityByIdAndMap(id, profileStatusRepository, profileStatusMapper);
    }

    // --- Return Support ---

    @Cacheable(value = CACHE_ITEM, keyGenerator = "cacheCustomKeyGenerator", unless = "#result == null ")
    public ReturnSupport getReturnSupportByCode(String code) {
        return getEntityByCode(code, returnSupportRepository);
    }

    @Cacheable(value = CACHE_ITEM, keyGenerator = "cacheCustomKeyGenerator", unless = "#result == null ")
    public ReturnSupportDTO getReturnSupportByIdAndMapToDTO(Long id) {
        return getEntityByIdAndMap(id, returnSupportRepository, returnSupportMapper);
    }

    // --- Sectioning ---

    @Cacheable(value = CACHE_ITEM, keyGenerator = "cacheCustomKeyGenerator", unless = "#result == null ")
    public Sectioning getSectioningByCode(String code) {
        return getEntityByCode(code, sectioningRepository);
    }

    @Cacheable(value = CACHE_ITEM, keyGenerator = "cacheCustomKeyGenerator", unless = "#result == null ")
    public SectioningDTO getSectioningByIdAndMapToDTO(Long id) {
        return getEntityByIdAndMap(id, sectioningRepository, sectioningMapper);
    }

    // --- Steady Arm Type ---

    @Cacheable(value = CACHE_ITEM, keyGenerator = "cacheCustomKeyGenerator", unless = "#result == null ")
    public SteadyArmType getSteadyArmTypeByCode(String code) {
        return getEntityByCode(code, steadyArmTypeRepository);
    }

    @Cacheable(value = CACHE_ITEM, keyGenerator = "cacheCustomKeyGenerator", unless = "#result == null ")
    public SteadyArmTypeDTO getSteadyArmTypeByIdAndMapToDTO(Long id) {
        return getEntityByIdAndMap(id, steadyArmTypeRepository, steadyArmTypeMapper);
    }

    // --- Support Type ---

    @Cacheable(value = CACHE_ITEM, keyGenerator = "cacheCustomKeyGenerator", unless = "#result == null ")
    public SupportType getSupportTypeByCode(String code) {
        return getEntityByCode(code, supportTypeRepository);
    }

    @Cacheable(value = CACHE_ITEM, keyGenerator = "cacheCustomKeyGenerator", unless = "#result == null ")
    public SupportTypeDTO getSupportTypeByIdAndMapToDTO(Long id) {
        return getEntityByIdAndMap(id, supportTypeRepository, supportTypeMapper);
    }


    @Cacheable(value = CACHE_ITEM, keyGenerator = "cacheCustomKeyDTOGenerator", unless = "#result == null ")
    public <E extends BaseEntity, T extends LovDTO> E getByIdOrByCode(T dto, SerializableLongFunction<E> byId, SerializableFunction<String, E> byCode) {

        if (dto == null) {
            return null;
        }

        if (Utils.exists(dto)) {
            return byId.apply(dto.getId());
        }

        if (StringUtils.isBlank(dto.getCode())) {
            return null;
        }

        return byCode.apply(dto.getCode());
    }
}
