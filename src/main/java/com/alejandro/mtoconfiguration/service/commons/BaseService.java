package com.alejandro.mtoconfiguration.service.commons;

import com.alejandro.mtoconfiguration.business.commons.Business;
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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public abstract class BaseService<T extends BaseDTO, E extends IEntity> {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @PersistenceContext
    protected EntityManager em;

    @Autowired
    protected ApplicationContext applicationContext;

    @Autowired
    protected HttpServletRequest request;

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
            value = "${cache.application}" + ".list",
            unless = "#result.size() < 1",
            key = "#root.targetClass.getSimpleName()"
    )
    public List<T> findAll() throws BaseException {
        return Optional.of(getRepository().findAll())
                .filter(list -> !list.isEmpty())
                .map(getMapper()::toListDTO)
                .orElseGet(ArrayList::new);
    }

    @Cacheable(
            value = "${cache.application}" + ".page",
            unless = "#result.getSize() < 1",
            key = "#root.targetClass.getSimpleName() + '::' + #pageable"
    )
    public Page<T> findAll(Pageable pageable) throws BaseException {
        return Optional.of(getRepository().findAll(pageable))
                .filter(page -> !page.stream().isParallel())
                .map(getMapper()::mapToDTOs)
                .orElseGet(Page::empty);
    }

    @Cacheable(
            value = "${cache.application}.page",
            key = "#root.targetClass.getSimpleName() + '::search::' + #searchRequestDTO"
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
        // 1. Validaciones previas (Lanza ValidationException si falla)
        doValidationsBeforeSave(dto);

        // 2. Proceso de creación funcional
        return Optional.of(getEntity())
                .map(entity -> {
                    Optional.ofNullable(getBusiness()).ifPresent(b -> b.preMapperDTOToEntity(dto, entity));

                    getMapper().updateEntityFromDTO(dto, entity);

                    Optional.ofNullable(getBusiness()).ifPresent(b -> b.postValidationDTOToEntity(dto, entity));

                    return entity;
                })
                .map(getRepository()::saveAndFlush)
                .map(e -> {
                    getMapper().updateDTOFromEntity(e, dto);

                    applicationContext.getBean(this.getClass()).cacheClean(true);

                    return dto;
                })
                .orElseThrow(() -> new BaseException("Error inesperado al crear la entidad"));

    }

    @Transactional(
            rollbackOn = {ValidationException.class, Exception.class}
    )
    public List<T> bulkCreate(List<T> dtoList) throws BaseException {
        return Optional.ofNullable(dtoList)
                .filter(CollectionUtils::isNotEmpty)
                .map(list -> {
                    // 1. Validaciones masivas
                    doValidationsBeforeBulkSave(list);
                    doValidationsBeforeSave(list);

                    return list;
                })
                .map(getMapper()::toListEntity) // 2. Mapeo masivo DTO -> Entity
                .map(getRepository()::saveAll)  // 3. Persistencia en bloque
                .map(savedEntities -> {
                    getRepository().flush();

                    // 4. Limpieza de caché (usando el bean del contexto para respetar AOP)
                    applicationContext.getBean(this.getClass()).cacheClean(true);

                    // 5. Mapeo de vuelta para retornar DTOs con IDs y auditoría
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
                .map(T::getId)
                .map(getRepository()::getReferenceById)
                .map(entity -> {

                    Optional.ofNullable(getValidator())
                            .map(v -> v.validateBeforeUpdate(dto))
                            .filter(Utils::hasErrors)
                            .ifPresent(alerts -> {
                                throw new ValidationException(alerts);
                            });

                    Optional.ofNullable(getBusiness()).ifPresent(b -> b.preMapperDTOToEntity(dto, entity));
                    getMapper().updateEntityFromDTO(dto, entity);
                    Optional.ofNullable(getBusiness()).ifPresent(b -> b.postValidationDTOToEntity(dto, entity));

                    return entity;
                })
                .map(getRepository()::saveAndFlush)
                .map(savedEntity -> {
                    // Refresco de DTO y limpieza de caché
                    getMapper().updateDTOFromEntity(savedEntity, dto);
                    applicationContext.getBean(this.getClass()).cacheClean(true);
                    return dto;
                })
                .orElseThrow(() -> new BaseException("No se puede actualizar un objeto nulo o sin ID"));

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
                    applicationContext.getBean(this.getClass()).cacheClean(true);
                    return dto;
                })
                .orElseThrow(() -> new BaseException("No se puede cancelar un objeto nulo o sin ID"));
    }

    @Cacheable(
            value = "${cache.application}" + ".item",
            key = "#root.targetClass.getSimpleName() + '::' + #id"
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

    private void doValidationsBeforeSave(T dto) {
        Optional.ofNullable(getValidator())
                .map(validator -> validator.validateBeforeSave(dto))
                .filter(Utils::hasErrors)
                .ifPresent(alerts -> {
                    throw new ValidationException(alerts);
                });
    }

    private void doValidationsBeforeSave(List<T> dtos) throws ValidationException {
        Optional.ofNullable(getValidator())
                .map(validator -> dtos.stream()
                        .map(validator::validateBeforeSave)
                        .filter(CollectionUtils::isNotEmpty)
                        .flatMap(Collection::stream)
                        .toList())
                .filter(Utils::hasErrors)
                .ifPresent(alerts -> {
                    throw new ValidationException(alerts);
                });
    }

    private void doValidationsBeforeBulkSave(List<T> dto) throws ValidationException {
        Optional.ofNullable(getValidator())
                .map(validator -> validator.validateBeforeBulkSave(dto))
                .filter(Utils::hasErrors)
                .ifPresent(alerts -> {
                    throw new ValidationException(alerts);
                });
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
