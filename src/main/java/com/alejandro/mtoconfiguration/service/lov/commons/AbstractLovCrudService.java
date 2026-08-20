package com.alejandro.mtoconfiguration.service.lov.commons;

import com.alejandro.mtoconfiguration.configuration.cache.CacheNames;
import com.alejandro.mtoconfiguration.configuration.cache.LovCacheEvictionEvent;
import com.alejandro.mtoconfiguration.entity.lov.commons.Lov;
import com.alejandro.mtoconfiguration.mapper.lov.commons.LovMapper;
import com.alejandro.mtoconfiguration.model.commons.LovDTO;
import com.alejandro.mtoconfiguration.repository.jpa.lov.commons.LovRepository;
import jakarta.persistence.EntityNotFoundException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Transactional(readOnly = true)
public class AbstractLovCrudService<D extends LovDTO, E extends Lov> implements LovCrudService<D> {

    protected final LovRepository<E> repository;
    protected final LovMapper<D, E> mapper;
    protected final ApplicationEventPublisher applicationEventPublisher;

    protected AbstractLovCrudService(
            LovRepository<E> repository,
            LovMapper<D, E> mapper,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        this.repository = repository;
        this.mapper = mapper;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    @Cacheable(
            cacheNames = CacheNames.LOV_ITEM,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null"
    )
    public D findById(Long id) {
        if (id == null) {
            return null;
        }

        E entity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(getEntityName() + " not found with id " + id));

        return mapper.toDTO(entity);
    }

    @Override
    @Cacheable(
            cacheNames = CacheNames.LOV_ITEM,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null"
    )
    public D findByCode(String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }

        E entity = repository.findByCode(code);

        if (entity == null) {
            throw new EntityNotFoundException(getEntityName() + " not found with code " + code);
        }

        return mapper.toDTO(entity);
    }

    @Override
    @Cacheable(
            cacheNames = CacheNames.LOV_LIST,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null || #result.isEmpty()"
    )
    public List<D> findAll() {
        return mapper.toListDTO(repository.findAll());
    }

    @Override
    @Transactional
    public D create(D dto) {
        validateForCreate(dto);

        E entity = mapper.toEntity(dto);

        beforeCreate(dto, entity);

        E saved = repository.save(entity);

        afterCreate(saved);

        publishLovCacheEvictionEvent();

        return mapper.toDTO(saved);
    }

    @Override
    @Transactional
    public D update(Long id, D dto) {
        validateForUpdate(id, dto);

        E entity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(getEntityName() + " not found with id " + id));

        mapper.updateEntityFromDTO(dto, entity);

        beforeUpdate(dto, entity);

        E saved = repository.save(entity);

        afterUpdate(saved);

        publishLovCacheEvictionEvent();

        return mapper.toDTO(saved);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Id is required");
        }

        E entity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(getEntityName() + " not found with id " + id));

        beforeDelete(entity);

        repository.delete(entity);
        publishLovCacheEvictionEvent();
    }

    @Override
    @Transactional
    public List<D> bulkCreate(List<D> dtos) {
        validateRequiredList(dtos);

        List<E> entities = new ArrayList<>();

        for (D dto : dtos) {
            validateForCreate(dto);

            E entity = mapper.toEntity(dto);

            beforeCreate(dto, entity);

            entities.add(entity);
        }

        List<E> savedEntities = repository.saveAll(entities);

        savedEntities.forEach(this::afterCreate);

        // Un solo evento por operación bulk: publicar uno por entidad dispararía
        // cientos de SCAN sobre el keyspace para invalidar exactamente lo mismo.
        publishLovCacheEvictionEvent();

        return mapper.toListDTO(savedEntities);
    }

    @Override
    @Transactional
    public List<D> bulkUpdate(List<D> dtos) {
        validateRequiredList(dtos);

        List<E> entities = new ArrayList<>();

        for (D dto : dtos) {
            validateRequiredDto(dto);

            Long id = dto.getId();

            validateForUpdate(id, dto);

            E entity = repository.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException(getEntityName() + " not found with id " + id));

            mapper.updateEntityFromDTO(dto, entity);

            beforeUpdate(dto, entity);

            entities.add(entity);
        }

        List<E> savedEntities = repository.saveAll(entities);

        savedEntities.forEach(this::afterUpdate);

        // Un solo evento por operación bulk: publicar uno por entidad dispararía
        // cientos de SCAN sobre el keyspace para invalidar exactamente lo mismo.
        publishLovCacheEvictionEvent();

        return mapper.toListDTO(savedEntities);
    }

    protected void validateForCreate(D dto) {
        validateRequiredDto(dto);
        validateRequiredCode(dto);
    }

    protected void validateForUpdate(Long id, D dto) {
        if (id == null) {
            throw new IllegalArgumentException("Id is required");
        }

        validateRequiredDto(dto);
        validateRequiredCode(dto);
    }

    protected void validateRequiredDto(D dto) {
        if (dto == null) {
            throw new IllegalArgumentException(getEntityName() + " data is required");
        }
    }

    protected void validateRequiredCode(D dto) {
        if (StringUtils.isBlank(dto.getCode())) {
            throw new IllegalArgumentException(getEntityName() + " code is required");
        }
    }

    protected void validateRequiredList(List<D> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            throw new IllegalArgumentException(getEntityName() + " list is required");
        }
    }

    protected void beforeCreate(D dto, E entity) {
        // Hook para servicios hijos
    }

    protected void beforeUpdate(D dto, E entity) {
        // Hook para servicios hijos
    }

    protected void afterCreate(E entity) {
        // Hook para servicios hijos
    }

    protected void afterUpdate(E entity) {
        // Hook para servicios hijos
    }

    protected void beforeDelete(E entity) {
        // Hook para servicios hijos
    }

    protected String getEntityName() {
        return "Lov";
    }

    private void publishLovCacheEvictionEvent() {
        applicationEventPublisher.publishEvent(new LovCacheEvictionEvent(getEntityName()));
    }
}
