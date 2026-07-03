package com.alejandro.mtoconfiguration.service.commons;

import com.alejandro.mtoconfiguration.business.commons.Business;
import com.alejandro.mtoconfiguration.configuration.cache.CacheEvictionEvent;
import com.alejandro.mtoconfiguration.configuration.cache.CacheNames;
import com.alejandro.mtoconfiguration.core.exception.BaseException;
import com.alejandro.mtoconfiguration.core.exception.ConcurrencyException;
import com.alejandro.mtoconfiguration.core.exception.ValidationException;
import com.alejandro.mtoconfiguration.entity.commons.IEntity;
import com.alejandro.mtoconfiguration.mapper.commons.BaseMapper;
import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import com.alejandro.mtoconfiguration.model.commons.SearchRequestDTO;
import com.alejandro.mtoconfiguration.repository.jpa.commons.CriteriaSearchRepository;
import com.alejandro.mtoconfiguration.utils.Utils;
import com.alejandro.mtoconfiguration.validator.commons.Validator;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;
import java.util.function.Function;

public abstract class BaseService<T extends BaseDTO, E extends IEntity> {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @PersistenceContext
    protected EntityManager em;

    @Autowired
    protected HttpServletRequest request;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    // Abstract methods to be implemented in concrete subclasses
    protected abstract BaseMapper<T, E> getMapper();

    protected abstract Validator<T> getValidator();

    public abstract E getEntity();

    public abstract T getDTO();

    protected abstract JpaRepository<E, Long> getRepository();

    protected abstract CriteriaSearchRepository<E> getCriteriaSearchRepository();

    protected abstract Map<String, Object> searchParams();

    protected abstract Business<T, E> getBusiness();

    @Cacheable(
            cacheNames = CacheNames.NORMAL_LIST,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null || #result.isEmpty()"
    )
    public List<T> findAll() throws BaseException {
        return Optional.of(getRepository().findAll())
                .filter(list -> !list.isEmpty())
                .map(getMapper()::toListDTO)
                .orElseGet(ArrayList::new);
    }

    @Cacheable(
            cacheNames = CacheNames.NORMAL_PAGE,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null || #result.isEmpty()"
    )
    public Page<T> findAll(Pageable pageable) throws BaseException {
        return Optional.of(getRepository().findAll(pageable))
                .filter(page -> !page.stream().isParallel())
                .map(getMapper()::mapToDTOs)
                .orElseGet(Page::empty);
    }

    @Cacheable(
            cacheNames = CacheNames.NORMAL_SEARCH,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null || #result.isEmpty()"
    )
    public Page<T> search(SearchRequestDTO searchRequestDTO) throws BaseException {

        Optional.ofNullable(getValidator())
                .map(v -> v.validateBeforeSearch(searchRequestDTO))
                .filter(Utils::hasErrors)
                .ifPresent(alerts -> {
                    throw new ValidationException(alerts);
                });

        return Optional.ofNullable(getCriteriaSearchRepository())
                .map(repo -> repo.criteriaSearchWithChildren((Class<E>) getEntity().getClass(), searchRequestDTO, em, searchParams()))
                .map(getMapper()::mapToDTOs)
                .orElseThrow(() -> new BaseException("Search method not implemented (CriteriaSearchRepository is null)"));

    }

    @Transactional(
            rollbackOn = {ValidationException.class, Exception.class}
    )
    public T create(T dto) throws BaseException {
        return Optional.ofNullable(dto)
                .map(d -> {
                    validateBeforeCreate(d);
                    return d;
                })
                .map(getMapper()::toEntity)
                .map(entity -> {
                    Optional.ofNullable(getBusiness())
                            .ifPresent(b -> b.preMapperDTOToEntity(dto, entity));

                    Optional.ofNullable(getBusiness())
                            .ifPresent(b -> b.postValidationDTOToEntity(dto, entity));

                    return entity;
                })
                .map(getRepository()::saveAndFlush)
                .map(savedEntity -> {
                    getMapper().updateDTOFromEntity(savedEntity, dto);
                    publishCacheEvictionEvent();
                    return dto;
                })
                .orElseThrow(() -> new BaseException("No se puede crear un objeto nulo"));

    }

    @Transactional(
            rollbackOn = {ValidationException.class, Exception.class}
    )
    public List<T> bulkCreate(List<T> dtoList) throws BaseException {
        return Optional.ofNullable(dtoList)
                .filter(CollectionUtils::isNotEmpty)
                .map(list -> {
                    validateBeforeBulkCreate(list);
                    return list;
                })
                .map(getMapper()::toListEntity)
                .map(getRepository()::saveAll)
                .map(savedEntities -> {
                    getRepository().flush();
                    publishCacheEvictionEvent();
                    return getMapper().toListDTO(savedEntities);
                })
                .orElseGet(Collections::emptyList);
    }

    @Transactional(
            rollbackOn = {ValidationException.class, ConcurrencyException.class, Exception.class}
    )
    public T update(T dto) throws BaseException {
        return Optional.ofNullable(dto)
                .filter(Utils::exists)
                .map(d -> {
                    validateBeforeUpdate(d);
                    return d;
                })
                .map(T::getId)
                .map(id -> getRepository().findById(id)
                        .orElseThrow(() -> new BaseException(
                                getEntity().getClass().getSimpleName() + " Object not found with id " + id
                        ))
                )
                .map(entity -> {
                    Optional.ofNullable(getBusiness())
                            .ifPresent(b -> b.preMapperDTOToEntity(dto, entity));

                    getMapper().updateEntityFromDTO(dto, entity);

                    Optional.ofNullable(getBusiness())
                            .ifPresent(b -> b.postValidationDTOToEntity(dto, entity));

                    return entity;
                })
                .map(getRepository()::saveAndFlush)
                .map(savedEntity -> {
                    getMapper().updateDTOFromEntity(savedEntity, dto);
                    publishCacheEvictionEvent();
                    return dto;
                })
                .orElseThrow(() -> new BaseException("No se puede actualizar un objeto nulo o sin ID"));
    }

    @Transactional(
            rollbackOn = {ValidationException.class, ConcurrencyException.class, Exception.class}
    )
    public List<T> bulkUpdate(List<T> dtoList) throws BaseException {
        return Optional.ofNullable(dtoList)
                .filter(CollectionUtils::isNotEmpty)
                .map(list -> {
                    validateBeforeBulkUpdate(list);
                    return list;
                })
                .map(list -> list.stream()
                        .map(dto -> Optional.ofNullable(dto)
                                .filter(Utils::exists)
                                .map(T::getId)
                                .map(id -> getRepository().findById(id)
                                        .orElseThrow(() -> new BaseException(
                                                getEntity().getClass().getSimpleName() + " Object not found with id " + id
                                        ))
                                )
                                .map(entity -> {
                                    Optional.ofNullable(getBusiness())
                                            .ifPresent(b -> b.preMapperDTOToEntity(dto, entity));

                                    getMapper().updateEntityFromDTO(dto, entity);

                                    Optional.ofNullable(getBusiness())
                                            .ifPresent(b -> b.postValidationDTOToEntity(dto, entity));

                                    return entity;
                                })
                                .orElseThrow(() -> new BaseException("No se puede actualizar un objeto nulo o sin ID"))
                        )
                        .toList()
                )
                .map(getRepository()::saveAll)
                .map(savedEntities -> {
                    getRepository().flush();
                    publishCacheEvictionEvent();
                    return getMapper().toListDTO(savedEntities);
                })
                .orElseGet(Collections::emptyList);
    }


    @Transactional(
            rollbackOn = {ValidationException.class, ConcurrencyException.class, Exception.class}
    )
    public T cancel(T dto) throws BaseException {
        return Optional.ofNullable(dto)
                .filter(Utils::exists)
                .map(T::getId)
                .map(getRepository()::getReferenceById)
                .map(entity -> {

                    // 3. Validaciones de negocio específicas para la cancelación
                    Optional.ofNullable(getValidator())
                            .map(v -> v.validateBeforeCancel(dto))
                            .filter(Utils::hasErrors)
                            .ifPresent(alerts -> {
                                throw new ValidationException(alerts);
                            });
                    Optional.ofNullable(getBusiness()).ifPresent(b -> b.preCancelDTOToEntity(dto, entity));
                    getMapper().updateEntityFromDTO(dto, entity);

                    return entity;
                })
                .map(getRepository()::saveAndFlush)
                .map(savedEntity -> {
                    getMapper().updateDTOFromEntity(savedEntity, dto);
                    publishCacheEvictionEvent();
                    return dto;
                })
                .orElseThrow(() -> new BaseException("No se puede cancelar un objeto nulo o sin ID"));
    }

    @Cacheable(
            cacheNames = CacheNames.NORMAL_ITEM,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null"
    )
    public T getById(Long id) throws BaseException {
        return Optional.ofNullable(id)
                .map(getRepository()::getReferenceById)
                .map(entity -> {
                    T dto = getDTO();
                    Optional.ofNullable(getBusiness()).ifPresent(b -> b.preMapperEntityToDTO(entity, dto));
                    getMapper().updateDTOFromEntity(entity, dto);
                    Optional.ofNullable(getBusiness()).ifPresent(b -> b.postMapperEntityToDTO(entity, dto));
                    return dto;
                })
                .orElseThrow(() -> new BaseException("El ID proporcionado no puede ser nulo"));
    }

    private void validateBeforeCreate(T dto) {
        Optional.ofNullable(getValidator())
                .map(validator -> validator.validateBeforeSave(dto))
                .filter(Utils::hasErrors)
                .ifPresent(alerts -> {
                    throw new ValidationException(alerts);
                });
    }

    private void validateBeforeBulkCreate(List<T> dtoList) {
        Optional.ofNullable(getValidator())
                .map(validator -> validator.validateBeforeBulkSave(dtoList))
                .filter(Utils::hasErrors)
                .ifPresent(alerts -> {
                    throw new ValidationException(alerts);
                });
    }

    private void validateBeforeUpdate(T dto) {
        Optional.ofNullable(getValidator())
                .map(validator -> validator.validateBeforeUpdate(dto))
                .filter(Utils::hasErrors)
                .ifPresent(alerts -> {
                    throw new ValidationException(alerts);
                });
    }

    private void validateBeforeBulkUpdate(List<T> dtoList) {
        Optional.ofNullable(getValidator())
                .map(validator -> validator.validateBeforeBulkUpdate(dtoList))
                .filter(Utils::hasErrors)
                .ifPresent(alerts -> {
                    throw new ValidationException(alerts);
                });
    }

    protected <V> void applyCondition(BooleanBuilder builder, V value, Function<V, BooleanExpression> function) {
        Optional.ofNullable(value)
                .filter(v -> !(v instanceof String s) || !s.isBlank())
                .map(function)
                .ifPresent(builder::and);
    }

    protected void publishCacheEvictionEvent() {
        String serviceName = getCurrentServiceName();
        applicationEventPublisher.publishEvent(new CacheEvictionEvent(serviceName));
        log.info("Cache eviction event published for service: {}", serviceName);
    }

    private String getCurrentServiceName() {
        return AopUtils.getTargetClass(this).getSimpleName();
    }

    @Caching(evict = {
            @CacheEvict(value = "${cache.application}.item", key = "#root.targetClass.getSimpleName()", allEntries = true),
            @CacheEvict(value = "${cache.application}.list", key = "#root.targetClass.getSimpleName()"),
            @CacheEvict(value = "${cache.application}.page", key = "#root.targetClass.getSimpleName()", allEntries = true)
    })
    public void cacheClean(Boolean redirect) {
        log.info("Cleaning cache for service: {}", getClass().getSimpleName());
    }

}
