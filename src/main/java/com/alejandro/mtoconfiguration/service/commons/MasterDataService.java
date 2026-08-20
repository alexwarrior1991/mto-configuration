package com.alejandro.mtoconfiguration.service.commons;

import com.alejandro.mtoconfiguration.configuration.cache.CacheNames;
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
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class MasterDataService {


    private static final String CACHE_ITEM = CacheNames.LOV_ITEM;
    private static final String CACHE_LIST = CacheNames.LOV_LIST;

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
        return Optional.ofNullable(code)
                .filter(StringUtils::isNotBlank)
                .map(repository::findByCode)
                .map(this::initialize)
                .orElse(null);
    }

    private <E extends Lov, D extends LovDTO> E getEntityRefByCode(
            String code,
            LovRepository<E> repository,
            Function<String, D> cachedDtoLookup
    ) {
        return Optional.ofNullable(code)
                .filter(StringUtils::isNotBlank)
                .map(cachedDtoLookup)
                .map(LovDTO::getId)
                .map(repository::getReferenceById)
                .orElse(null);
    }

    private <E extends Lov> E getEntityById(Long id, LovRepository<E> repository) {
        return Optional.ofNullable(id)
                .flatMap(repository::findById)
                .map(this::initialize)
                .orElse(null);
    }

    private <E extends Lov, D extends LovDTO> D getEntityByIdAndMap(Long id, LovRepository<E> repository, LovMapper<D, E> mapper) {
        if (id == null) {
            return null;
        }
        return Optional.of(id)
                .flatMap(repository::findById)
                .map(this::initialize)
                .map(mapper::toDTO)
                .orElse(null);
    }

    private <E extends Lov, D extends LovDTO> D getEntityByCodeAndMap(
            String code,
            LovRepository<E> repository,
            LovMapper<D, E> mapper
    ) {
        return Optional.ofNullable(code)
                .filter(StringUtils::isNotBlank)
                .map(repository::findByCode)
                .map(this::initialize)
                .map(mapper::toDTO)
                .orElse(null);
    }

    private <E extends Lov, D extends LovDTO> List<D> getListAndMap(
            LovRepository<E> repository,
            LovMapper<D, E> mapper
    ) {
        return repository.findAll()
                .stream()
                .map(this::initialize)
                .map(mapper::toDTO)
                .toList();
    }


    private <E extends Lov> E initialize(E entity) {
        Hibernate.initialize(entity);
        return entity;
    }

    // --- Anchorage Foundation ---


    public AnchorageFoundation getAnchorageFoundationByCode(String code) {
        return getEntityRefByCode(code, anchorageFoundationRepository, this::getAnchorageFoundationByCodeAndMapToDTO);
    }

    @Cacheable(
            cacheNames = CACHE_ITEM,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null"
    )
    public AnchorageFoundationDTO getAnchorageFoundationByIdAndMapToDTO(Long id) {
        return getEntityByIdAndMap(id, anchorageFoundationRepository, anchorageFoundationMapper);
    }

    @Cacheable(
            cacheNames = CACHE_ITEM,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null"
    )
    public AnchorageFoundationDTO getAnchorageFoundationByCodeAndMapToDTO(String code) {
        return getEntityByCodeAndMap(code, anchorageFoundationRepository, anchorageFoundationMapper);
    }


    @Cacheable(
            cacheNames = CACHE_LIST,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null || #result.isEmpty()"
    )
    public List<AnchorageFoundationDTO> getAnchorageFoundationList() {
        return getListAndMap(anchorageFoundationRepository, anchorageFoundationMapper);
    }

    // --- Anchorage Foundation Type ---

    public AnchorageFoundationType getAnchorageFoundationTypeByCode(String code) {
        return getEntityRefByCode(code, anchorageFoundationTypeRepository, this::getAnchorageFoundationTypeByCodeAndMapToDTO);
    }

    @Cacheable(
            cacheNames = CACHE_ITEM,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null"
    )
    public AnchorageFoundationTypeDTO getAnchorageFoundationTypeByIdAndMapToDTO(Long id) {
        return getEntityByIdAndMap(id, anchorageFoundationTypeRepository, anchorageFoundationTypeMapper);
    }

    @Cacheable(
            cacheNames = CACHE_ITEM,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null"
    )
    public AnchorageFoundationTypeDTO getAnchorageFoundationTypeByCodeAndMapToDTO(String code) {
        return getEntityByCodeAndMap(code, anchorageFoundationTypeRepository, anchorageFoundationTypeMapper);
    }

    @Cacheable(
            cacheNames = CACHE_LIST,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null || #result.isEmpty()"
    )
    public List<AnchorageFoundationTypeDTO> getAnchorageFoundationTypeList() {
        return getListAndMap(anchorageFoundationTypeRepository, anchorageFoundationTypeMapper);
    }

    // --- Anchorage ---

    public Anchorage getAnchorageByCode(String code) {
        return getEntityRefByCode(code, anchorageRepository, this::getAnchorageByCodeAndMapToDTO);
    }

    @Cacheable(
            cacheNames = CACHE_ITEM,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null"
    )
    public AnchorageDTO getAnchorageByIdAndMapToDTO(Long id) {
        return getEntityByIdAndMap(id, anchorageRepository, anchorageMapper);
    }

    @Cacheable(
            cacheNames = CACHE_ITEM,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null"
    )
    public AnchorageDTO getAnchorageByCodeAndMapToDTO(String code) {
        return getEntityByCodeAndMap(code, anchorageRepository, anchorageMapper);
    }

    @Cacheable(
            cacheNames = CACHE_LIST,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null || #result.isEmpty()"
    )
    public List<AnchorageDTO> getAnchorageList() {
        return getListAndMap(anchorageRepository, anchorageMapper);
    }

    // --- Cantilever Type ---

    public CantileverType getCantileverTypeByCode(String code) {
        return getEntityRefByCode(code, cantileverTypeRepository, this::getCantileverTypeByCodeAndMapToDTO);
    }

    @Cacheable(
            cacheNames = CACHE_ITEM,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null"
    )
    public CantileverTypeDTO getCantileverTypeByIdAndMapToDTO(Long id) {
        return getEntityByIdAndMap(id, cantileverTypeRepository, cantileverTypeMapper);
    }

    @Cacheable(
            cacheNames = CACHE_ITEM,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null"
    )
    public CantileverTypeDTO getCantileverTypeByCodeAndMapToDTO(String code) {
        return getEntityByCodeAndMap(code, cantileverTypeRepository, cantileverTypeMapper);
    }

    @Cacheable(
            cacheNames = CACHE_LIST,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null || #result.isEmpty()"
    )
    public List<CantileverTypeDTO> getCantileverTypeList() {
        return getListAndMap(cantileverTypeRepository, cantileverTypeMapper);
    }

    // --- Comercial Entity Type ---

    public ComercialEntityType getComercialEntityTypeByCode(String code) {
        return getEntityRefByCode(code, comercialEntityTypeRepository, this::getComercialEntityTypeByCodeAndMapToDTO);
    }

    @Cacheable(
            cacheNames = CACHE_ITEM,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null"
    )
    public ComercialEntityTypeDTO getComercialEntityTypeByIdAndMapToDTO(Long id) {
        return getEntityByIdAndMap(id, comercialEntityTypeRepository, comercialEntityTypeMapper);
    }

    @Cacheable(
            cacheNames = CACHE_ITEM,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null"
    )
    public ComercialEntityTypeDTO getComercialEntityTypeByCodeAndMapToDTO(String code) {
        return getEntityByCodeAndMap(code, comercialEntityTypeRepository, comercialEntityTypeMapper);
    }

    @Cacheable(
            cacheNames = CACHE_LIST,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null || #result.isEmpty()"
    )
    public List<ComercialEntityTypeDTO> getComercialEntityTypeList() {
        return getListAndMap(comercialEntityTypeRepository, comercialEntityTypeMapper);
    }


    // --- Disconnector Function ---

    public DisconnectorFunction getDisconnectorFunctionByCode(String code) {
        return getEntityRefByCode(code, disconnectorFunctionRepository, this::getDisconnectorFunctionByCodeAndMapToDTO);
    }

    @Cacheable(
            cacheNames = CACHE_ITEM,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null"
    )
    public DisconnectorFunctionDTO getDisconnectorFunctionByIdAndMapToDTO(Long id) {
        return getEntityByIdAndMap(id, disconnectorFunctionRepository, disconnectorFunctionMapper);
    }

    @Cacheable(
            cacheNames = CACHE_ITEM,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null"
    )
    public DisconnectorFunctionDTO getDisconnectorFunctionByCodeAndMapToDTO(String code) {
        return getEntityByCodeAndMap(code, disconnectorFunctionRepository, disconnectorFunctionMapper);
    }

    @Cacheable(
            cacheNames = CACHE_LIST,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null || #result.isEmpty()"
    )
    public List<DisconnectorFunctionDTO> getDisconnectorFunctionList() {
        return getListAndMap(disconnectorFunctionRepository, disconnectorFunctionMapper);
    }

    // --- Foundation ---

    public Foundation getFoundationByCode(String code) {
        return getEntityRefByCode(code, foundationRepository, this::getFoundationByCodeAndMapToDTO);
    }

    @Cacheable(
            cacheNames = CACHE_ITEM,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null"
    )
    public FoundationDTO getFoundationByIdAndMapToDTO(Long id) {
        return getEntityByIdAndMap(id, foundationRepository, foundationMapper);
    }

    @Cacheable(
            cacheNames = CACHE_ITEM,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null"
    )
    public FoundationDTO getFoundationByCodeAndMapToDTO(String code) {
        return getEntityByCodeAndMap(code, foundationRepository, foundationMapper);
    }

    @Cacheable(
            cacheNames = CACHE_LIST,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null || #result.isEmpty()"
    )
    public List<FoundationDTO> getFoundationList() {
        return getListAndMap(foundationRepository, foundationMapper);
    }

    // --- Foundation Type ---

    public FoundationType getFoundationTypeByCode(String code) {
        return getEntityRefByCode(code, foundationTypeRepository, this::getFoundationTypeByCodeAndMapToDTO);
    }

    @Cacheable(
            cacheNames = CACHE_ITEM,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null"
    )
    public FoundationTypeDTO getFoundationTypeByIdAndMapToDTO(Long id) {
        return getEntityByIdAndMap(id, foundationTypeRepository, foundationTypeMapper);
    }

    @Cacheable(
            cacheNames = CACHE_ITEM,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null"
    )
    public FoundationTypeDTO getFoundationTypeByCodeAndMapToDTO(String code) {
        return getEntityByCodeAndMap(code, foundationTypeRepository, foundationTypeMapper);
    }

    @Cacheable(
            cacheNames = CACHE_LIST,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null || #result.isEmpty()"
    )
    public List<FoundationTypeDTO> getFoundationTypeList() {
        return getListAndMap(foundationTypeRepository, foundationTypeMapper);
    }

    // --- Pole Type ---

    public PoleType getPoleTypeByCode(String code) {
        return getEntityRefByCode(code, poleTypeRepository, this::getPoleTypeByCodeAndMapToDTO);
    }

    @Cacheable(
            cacheNames = CACHE_ITEM,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null"
    )
    public PoleTypeDTO getPoleTypeByIdAndMapToDTO(Long id) {
        return getEntityByIdAndMap(id, poleTypeRepository, poleTypeMapper);
    }

    @Cacheable(
            cacheNames = CACHE_ITEM,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null"
    )
    public PoleTypeDTO getPoleTypeByCodeAndMapToDTO(String code) {
        return getEntityByCodeAndMap(code, poleTypeRepository, poleTypeMapper);
    }

    @Cacheable(
            cacheNames = CACHE_LIST,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null || #result.isEmpty()"
    )
    public List<PoleTypeDTO> getPoleTypeList() {
        return getListAndMap(poleTypeRepository, poleTypeMapper);
    }

    // --- Portal ---

    public Portal getPortalByCode(String code) {
        return getEntityRefByCode(code, portalRepository, this::getPortalByCodeAndMapToDTO);
    }

    @Cacheable(
            cacheNames = CACHE_ITEM,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null"
    )
    public PortalDTO getPortalByIdAndMapToDTO(Long id) {
        return getEntityByIdAndMap(id, portalRepository, portalMapper);
    }

    @Cacheable(
            cacheNames = CACHE_ITEM,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null"
    )
    public PortalDTO getPortalByCodeAndMapToDTO(String code) {
        return getEntityByCodeAndMap(code, portalRepository, portalMapper);
    }

    @Cacheable(
            cacheNames = CACHE_LIST,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null || #result.isEmpty()"
    )
    public List<PortalDTO> getPortalList() {
        return getListAndMap(portalRepository, portalMapper);
    }

    // --- Portal Type ---

    public PortalType getPortalTypeByCode(String code) {
        return getEntityRefByCode(code, portalTypeRepository, this::getPortalTypeByCodeAndMapToDTO);
    }

    @Cacheable(
            cacheNames = CACHE_ITEM,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null"
    )
    public PortalTypeDTO getPortalTypeByIdAndMapToDTO(Long id) {
        return getEntityByIdAndMap(id, portalTypeRepository, portalTypeMapper);
    }

    @Cacheable(
            cacheNames = CACHE_ITEM,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null"
    )
    public PortalTypeDTO getPortalTypeByCodeAndMapToDTO(String code) {
        return getEntityByCodeAndMap(code, portalTypeRepository, portalTypeMapper);
    }

    @Cacheable(
            cacheNames = CACHE_LIST,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null || #result.isEmpty()"
    )
    public List<PortalTypeDTO> getPortalTypeList() {
        return getListAndMap(portalTypeRepository, portalTypeMapper);
    }

    // --- Profile Status ---

    public ProfileStatus getProfileStatusByCode(String code) {
        return getEntityRefByCode(code, profileStatusRepository, this::getProfileStatusByCodeAndMapToDTO);
    }

    @Cacheable(
            cacheNames = CACHE_ITEM,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null"
    )
    public ProfileStatusDTO getProfileStatusByIdAndMapToDTO(Long id) {
        return getEntityByIdAndMap(id, profileStatusRepository, profileStatusMapper);
    }

    @Cacheable(
            cacheNames = CACHE_ITEM,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null"
    )
    public ProfileStatusDTO getProfileStatusByCodeAndMapToDTO(String code) {
        return getEntityByCodeAndMap(code, profileStatusRepository, profileStatusMapper);
    }

    @Cacheable(
            cacheNames = CACHE_LIST,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null || #result.isEmpty()"
    )
    public List<ProfileStatusDTO> getProfileStatusList() {
        return getListAndMap(profileStatusRepository, profileStatusMapper);
    }

    // --- Return Support ---

    public ReturnSupport getReturnSupportByCode(String code) {
        return getEntityRefByCode(code, returnSupportRepository, this::getReturnSupportByCodeAndMapToDTO);
    }

    @Cacheable(
            cacheNames = CACHE_ITEM,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null"
    )
    public ReturnSupportDTO getReturnSupportByIdAndMapToDTO(Long id) {
        return getEntityByIdAndMap(id, returnSupportRepository, returnSupportMapper);
    }

    @Cacheable(
            cacheNames = CACHE_ITEM,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null"
    )
    public ReturnSupportDTO getReturnSupportByCodeAndMapToDTO(String code) {
        return getEntityByCodeAndMap(code, returnSupportRepository, returnSupportMapper);
    }

    @Cacheable(
            cacheNames = CACHE_LIST,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null || #result.isEmpty()"
    )
    public List<ReturnSupportDTO> getReturnSupportList() {
        return getListAndMap(returnSupportRepository, returnSupportMapper);
    }


    // --- Sectioning ---

    public Sectioning getSectioningByCode(String code) {
        return getEntityRefByCode(code, sectioningRepository, this::getSectioningByCodeAndMapToDTO);
    }

    @Cacheable(
            cacheNames = CACHE_ITEM,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null"
    )
    public SectioningDTO getSectioningByIdAndMapToDTO(Long id) {
        return getEntityByIdAndMap(id, sectioningRepository, sectioningMapper);
    }

    @Cacheable(
            cacheNames = CACHE_ITEM,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null"
    )
    public SectioningDTO getSectioningByCodeAndMapToDTO(String code) {
        return getEntityByCodeAndMap(code, sectioningRepository, sectioningMapper);
    }

    @Cacheable(
            cacheNames = CACHE_LIST,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null || #result.isEmpty()"
    )
    public List<SectioningDTO> getSectioningList() {
        return getListAndMap(sectioningRepository, sectioningMapper);
    }


    // --- Steady Arm Type ---

    public SteadyArmType getSteadyArmTypeByCode(String code) {
        return getEntityRefByCode(code, steadyArmTypeRepository, this::getSteadyArmTypeByCodeAndMapToDTO);
    }

    @Cacheable(
            cacheNames = CACHE_ITEM,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null"
    )
    public SteadyArmTypeDTO getSteadyArmTypeByIdAndMapToDTO(Long id) {
        return getEntityByIdAndMap(id, steadyArmTypeRepository, steadyArmTypeMapper);
    }

    @Cacheable(
            cacheNames = CACHE_ITEM,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null"
    )
    public SteadyArmTypeDTO getSteadyArmTypeByCodeAndMapToDTO(String code) {
        return getEntityByCodeAndMap(code, steadyArmTypeRepository, steadyArmTypeMapper);
    }

    @Cacheable(
            cacheNames = CACHE_LIST,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null || #result.isEmpty()"
    )
    public List<SteadyArmTypeDTO> getSteadyArmTypeList() {
        return getListAndMap(steadyArmTypeRepository, steadyArmTypeMapper);
    }

    // --- Support Type ---

    public SupportType getSupportTypeByCode(String code) {
        return getEntityRefByCode(code, supportTypeRepository, this::getSupportTypeByCodeAndMapToDTO);
    }

    @Cacheable(
            cacheNames = CACHE_ITEM,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null"
    )
    public SupportTypeDTO getSupportTypeByIdAndMapToDTO(Long id) {
        return getEntityByIdAndMap(id, supportTypeRepository, supportTypeMapper);
    }

    @Cacheable(
            cacheNames = CACHE_ITEM,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null"
    )
    public SupportTypeDTO getSupportTypeByCodeAndMapToDTO(String code) {
        return getEntityByCodeAndMap(code, supportTypeRepository, supportTypeMapper);
    }

    @Cacheable(
            cacheNames = CACHE_LIST,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null || #result.isEmpty()"
    )
    public List<SupportTypeDTO> getSupportTypeList() {
        return getListAndMap(supportTypeRepository, supportTypeMapper);
    }

    public <E extends BaseEntity, T extends LovDTO> E getByIdOrByCode(
            T dto,
            SerializableLongFunction<E> byId,
            SerializableFunction<String, E> byCode
    ) {
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
