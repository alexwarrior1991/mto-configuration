package com.alejandro.mtoconfiguration.repository.jpa.commons;

import com.alejandro.mtoconfiguration.entity.commons.BaseEntity;
import com.alejandro.mtoconfiguration.entity.commons.BaseEntity_;
import com.alejandro.mtoconfiguration.entity.commons.IEntity;
import com.alejandro.mtoconfiguration.model.commons.PageableDTO;
import com.alejandro.mtoconfiguration.model.commons.SearchRequestDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.*;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

public interface CriteriaSearchRepository<E extends IEntity> {

    Predicate buildPredicate(CriteriaBuilder criteriaBuilder, Root<E> root, Map<String, Object> filters, Map<String, Object> params);

    default Map<String, String> getEnhancedProperties() {
        return new HashMap<>();
    }

    /**
     * Enhances sort properties for entity fields if non‑empty
     */
    default Sort enhanceSortForEntityFields(Sort sort) {
        if (sort.isEmpty()) {
            return sort;
        }

        return Sort.by(sort.stream().map(order -> {
            if (StringUtils.isNotBlank(order.getProperty()) && getEnhancedProperties().containsKey(order.getProperty())) {
                return new Sort.Order(order.getDirection(), getEnhancedProperties().get(order.getProperty()));
            } else {
                return order;
            }
        }).collect(Collectors.toList()));
    }

    /**
     * Configures paginated criteria query for entity search
     */
    default Page<E> criteriaSearch(Class<E> entityClass, SearchRequestDTO searchRequestDTO,
                                   EntityManager entityManager, Map<String, Object> params) {

        PageableDTO pageable = searchRequestDTO.getPageable();

        // Converts sort parameters to sort order list
        List<Sort.Order> orders = IntStream.range(0, pageable.getSortBy().size())
                .mapToObj(i -> new Sort.Order(
                        Sort.Direction.fromString(pageable.getSortDirection().get(i)),
                        pageable.getSortBy().get(i)))
                .toList();


        PageRequest pageRequest = PageRequest.of(pageable.getPage(), pageable.getSize(), Sort.by(orders));
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<E> criteriaQuery = criteriaBuilder.createQuery(entityClass);
        Root<E> entityRoot = criteriaQuery.from(entityClass);
        Predicate predicate = this.buildPredicate(criteriaBuilder, entityRoot, searchRequestDTO.getFilters(), params);
        if (predicate != null) {
            criteriaQuery.where(predicate);
        }

        if (pageRequest.getSort().isSorted()) {
            Sort sort = this.enhanceSortForEntityFields(pageRequest.getSort());
            sort.forEach(order -> {
                Path<E> path = this.getPath(entityRoot, order.getProperty());
                Path<E> idPath = this.getPath(entityRoot, "id");
                if (order.isAscending()) {
                    criteriaQuery.orderBy(criteriaBuilder.asc(path), criteriaBuilder.asc(idPath));
                } else {
                    criteriaQuery.orderBy(criteriaBuilder.desc(path), criteriaBuilder.desc(idPath));
                }
            });

        }

        CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
        Root<E> countRoot = countQuery.from(entityClass);
        Predicate countPredicate = this.buildPredicate(criteriaBuilder, countRoot, searchRequestDTO.getFilters(), params);
        if (countPredicate != null) {
            countQuery.where(countPredicate);
        }

        Long count = entityManager.createQuery(countQuery).getSingleResult();
        List<E> results = entityManager.createQuery(criteriaQuery).setFirstResult((int) pageRequest.getOffset())
                .setMaxResults(pageRequest.getPageSize()).getResultList();

        return new PageImpl<>(results, pageRequest, count);
    }

    default Page<E> criteriaSearchWithChildren(Class<E> entityClass, SearchRequestDTO searchRequestDTO,
                                               EntityManager entityManager, Map<String, Object> params) {

        PageableDTO pageable = searchRequestDTO.getPageable();

        String sortBy = CollectionUtils.isNotEmpty(pageable.getSortBy()) ? pageable.getSortBy().getFirst() : getDefaultSortBy();
        String sortDirection = CollectionUtils.isNotEmpty(pageable.getSortDirection()) ? pageable.getSortDirection().getFirst() : getDefaultSortDirection();

        PageRequest pageRequest = PageRequest.of(pageable.getPage(), pageable.getSize());

        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();

        CriteriaQuery<Long> idQuery = criteriaBuilder.createQuery(Long.class);
        Root<E> entityIdRoot = idQuery.from(entityClass);

        Predicate predicate = this.buildPredicate(criteriaBuilder, entityIdRoot, searchRequestDTO.getFilters(), params);

        Optional.ofNullable(sortBy)
                .filter(sb -> sortDirection != null && StringUtils.isNoneBlank(sb, sortDirection))
                .map(sb -> getSortPath(entityManager, entityIdRoot, sb))
                .ifPresent(sortPath -> idQuery.orderBy(
                        getOrder(criteriaBuilder, sortDirection, sortPath), // sort by request param
                        getOrder(criteriaBuilder, sortDirection, entityIdRoot.get(BaseEntity_.CREATE_DATE)) // in case of tie, sort by Parent create date
                ));

        // Applies predicate to ID query or selects all IDs
        Optional.ofNullable(predicate)
                .ifPresentOrElse(
                        p -> idQuery.select(entityIdRoot.get(BaseEntity_.ID)).distinct(true).where(p),
                        () -> idQuery.select(entityIdRoot.get(BaseEntity_.ID)).distinct(true)
                );

        // Executes ID query; applies pagination; returns distinct IDs
        var entityIds = entityManager.createQuery(idQuery)
                .setFirstResult(0)
                .setMaxResults((int) ((pageRequest.getOffset() + pageRequest.getPageSize()) * getMaxNumberOfChildPerParent()))
                .getResultList().stream().distinct()
                .skip(pageRequest.getOffset())
                .limit(pageRequest.getPageSize())
                .toList();


        idQuery.select(criteriaBuilder.countDistinct(entityIdRoot));
        Long count = entityManager.createQuery(idQuery).getSingleResult();

        CriteriaQuery<E> entityQuery = criteriaBuilder.createQuery(entityClass);
        Root<E> entityRoot = entityQuery.from(entityClass);

        entityQuery.select(entityRoot).where(entityRoot.in(entityIds)).distinct(true);
        TypedQuery<E> typedQuery = entityManager.createQuery(entityQuery);

        List<E> results = typedQuery.getResultList();

        Map<Long, Integer> idIndexMap = IntStream.range(0, entityIds.size())
                .boxed()
                .collect(Collectors.toMap(entityIds::get, i -> i));

        results.sort(Comparator.comparing(entity -> idIndexMap.getOrDefault(entity.getId(), Integer.MAX_VALUE)));

        return new PageImpl<>(results, pageRequest, count);

    }

    default Path<E> getPath(Root<E> root, String property) {
        // Builds nested path from property via stream reduction
        return Optional.of(property)
                .filter(p -> p.contains("."))
                .map(p -> p.split("\\."))
                .map(parts -> Arrays.stream(parts)
                        .reduce(
                                (Path<E>) root,
                                (path, part) -> path.get(part),
                                (p1, p2) -> p2
                        ))
                .orElseGet(() -> root.get(property));
    }

    default String getDefaultSortBy() {
        return "createDate";
    }

    default String getDefaultSortDirection() {
        return "desc";
    }

    default Order getOrder(CriteriaBuilder criteriaBuilder, String sortDirection, Path<BaseEntity> sortPath) {
        return sortDirection.equalsIgnoreCase("asc") ?
                criteriaBuilder.asc(sortPath) :
                criteriaBuilder.desc(sortPath);
    }

    <B extends BaseEntity> Path<B> getSortPath(EntityManager entityManager, Root<E> entityRoot, String sortBy);

    //This must return the max number of children per parent entity. This is used to calculate the max number of results
    // to be retrieved from the database when searching for a parent entity with children. This is used to avoid retrieving
    // all the children from the database when the parent has a lot of children.
    // If there is a search on child of a child, then the value must be the max number of child multiplied by the max number of child of the child.
    //Override if it's needed more than one
    default int getMaxNumberOfChildPerParent() {
        return 1;//example: 2 is the max of equipments per movement (the GateMovement child)
    }

    default <B extends BaseEntity> boolean isFieldFrom(EntityType<B> entity, String fieldName) {
        return entity.getAttributes().stream()
                .map(Attribute::getName)
                .anyMatch(name -> name.equals(fieldName));
    }
}
