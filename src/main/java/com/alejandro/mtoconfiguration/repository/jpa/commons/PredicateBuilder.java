package com.alejandro.mtoconfiguration.repository.jpa.commons;

import com.alejandro.mtoconfiguration.entity.commons.BaseEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Predicate;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

public class PredicateBuilder<E1 extends BaseEntity, E2 extends BaseEntity> {

    private static final String SEARCH = "search";
    private static final String SEARCH_TEXT = "searchText";

    private final CriteriaBuilder criteriaBuilder;
    private final From<E1, E2> from;
    private final Map<String, Object> filters;

    public PredicateBuilder(CriteriaBuilder criteriaBuilder, From<E1, E2> from, Map<String, Object> filters) {
        this.criteriaBuilder = criteriaBuilder;
        this.from = from;
        this.filters = filters;
    }

    public Predicate emptyConjunction() {
        return criteriaBuilder.conjunction();
    }

    public Predicate emptyDisjunction() {
        return criteriaBuilder.disjunction();
    }

    /**
     * Combines predicates; returns null if empty after filtering
     */
    public Predicate and(Predicate... predicates) {
        var nonNullPredicates = Arrays.stream(predicates)
                .filter(Objects::nonNull)
                .toList();

        return CollectionUtils.isEmpty(nonNullPredicates)
                ? null
                : criteriaBuilder.and(nonNullPredicates.toArray(Predicate[]::new));
    }

    /**
     * Combines predicates with OR, filtering nulls; returns null if empty
     */
    public Predicate or(Predicate... predicates) {
        var nonNullPredicates = Arrays.stream(predicates)
                .filter(Objects::nonNull)
                .toList();

        return CollectionUtils.isEmpty(nonNullPredicates)
                ? null
                : criteriaBuilder.or(nonNullPredicates.toArray(Predicate[]::new));
    }

    public Predicate like(String columnName) {
        return like(columnName, null);
    }

    /**
     * Builds case‑insensitive like predicate if value exists
     */
    public Predicate like(String columnName, String filterName) {
        String value = MapUtils.getString(filters, StringUtils.defaultIfBlank(filterName, columnName));

        return StringUtils.isNotBlank(value)
                ? criteriaBuilder.like(criteriaBuilder.upper(from.get(columnName)), "%" + value.toUpperCase() + "%")
                : null;
    }

    public Predicate notLike(String column) {
        return notLike(column, null);
    }

    /**
     * Builds case‑insensitive not‑like predicate if value exists
     */
    public Predicate notLike(String columnName, String filterName) {
        String value = MapUtils.getString(filters, StringUtils.defaultIfBlank(filterName, columnName));

        return StringUtils.isNotBlank(value)
                ? criteriaBuilder.notLike(criteriaBuilder.upper(from.get(columnName)), "%" + value.toUpperCase() + "%")
                : null;
    }

    public Predicate isBlank(String column) {
        return StringUtils.isNotBlank(column)
                ? criteriaBuilder.or(criteriaBuilder.isNull(from.get(column)), criteriaBuilder.equal(from.get(column), ""))
                : null;
    }

    public Predicate isNotBlank(String column) {
        return StringUtils.isNotBlank(column)
                ? criteriaBuilder.isNotNull(from.get("column"))
                : null;
    }

    public Predicate isTrue(String columnName) {
        return criteriaBuilder.isTrue(from.get(columnName));
    }

    /**
     * Returns predicate if filter value is true
     */
    public Predicate isTrue(String columnName, String filterName) {
        String column = StringUtils.defaultIfBlank(filterName, columnName);
        Object value = filters.get(column);

        return (value instanceof Boolean && BooleanUtils.isTrue((Boolean) value))
                ? criteriaBuilder.isTrue(from.get(columnName))
                : null;
    }

    public Predicate isNotTrue(String columnName) {
        return criteriaBuilder.or(
                criteriaBuilder.isNull(from.get(columnName)),
                criteriaBuilder.isFalse(from.get(columnName))
        );
    }

    /**
     * Returns predicate if filter is absent or false
     */
    public Predicate isNotTrue(String columnName, String filterName) {
        String column = StringUtils.defaultIfBlank(filterName, columnName);
        Object value = filters.get(column);

        return (value == null || value instanceof Boolean && BooleanUtils.isFalse((Boolean) value))
                ? criteriaBuilder.or(
                criteriaBuilder.isNull(from.get(columnName)),
                criteriaBuilder.isFalse(from.get(columnName))
        )
                : null;
    }

    public Predicate isFalse(String columnName) {
        return criteriaBuilder.isFalse(from.get(columnName));
    }

    /**
     * Returns predicate if filter value is false
     */
    public Predicate isFalse(String columnName, String filterName) {
        String column = StringUtils.defaultIfBlank(filterName, columnName);
        Object value = filters.get(column);

        return (value instanceof Boolean && BooleanUtils.isFalse((Boolean) value))
                ? criteriaBuilder.isFalse(from.get(columnName))
                : null;
    }

    public Predicate isNotFalse(String columnName) {
        return criteriaBuilder.or(
                criteriaBuilder.isNull(from.get(columnName)),
                criteriaBuilder.isTrue(from.get(columnName))
        );
    }

    /**
     * Returns predicate if filter is absent or true; else null
     */
    public Predicate isNotFalse(String columnName, String filterName) {
        String column = StringUtils.defaultIfBlank(filterName, columnName);
        Object value = filters.get(column);

        return (value == null || value instanceof Boolean && BooleanUtils.isTrue((Boolean) value))
                ? criteriaBuilder.or(
                criteriaBuilder.isNull(from.get(columnName)),
                criteriaBuilder.isTrue(from.get(columnName))
        )
                : null;
    }

    public Predicate startWith(String columnName) {
        return like(columnName, null);
    }

    /**
     * Returns case‑insensitive prefix predicate if value is present
     */
    public Predicate startWith(String columnName, String filterName) {
        String value = MapUtils.getString(filters, StringUtils.defaultIfBlank(filterName, columnName));

        return StringUtils.isNotBlank(value)
                ? criteriaBuilder.like(criteriaBuilder.upper(from.get(columnName)), value.toUpperCase() + "%")
                : null;
    }

    public Predicate endWith(String columnName) {
        return like(columnName, null);
    }

    /**
     * Builds case‑insensitive predicate matching trailing input
     */
    public Predicate endWith(String columnName, String filterName) {
        String value = MapUtils.getString(filters, StringUtils.defaultIfBlank(filterName, columnName));

        return StringUtils.isNotBlank(value)
                ? criteriaBuilder.like(criteriaBuilder.upper(from.get(columnName)), "%" + value.toUpperCase())
                : null;
    }

    public Predicate eq(String columnName) {
        return eq(columnName, null);
    }

    public Predicate eq(String columnName, String filterName) {
        String column = StringUtils.defaultIfBlank(filterName, columnName);
        Object value = filters.get(column);

        return value != null ? criteriaBuilderEqual(columnName, value) : null;
    }

    private Predicate criteriaBuilderEqual(String columnName, Object value) {
        return criteriaBuilder.equal(from.get(columnName), value);
    }

    public Predicate ne(String columnName) {
        return ne(columnName, null);
    }

    public Predicate ne(String columnName, String filterName) {
        String column = StringUtils.defaultIfBlank(filterName, columnName);
        Object value = filters.get(column);

        return value != null ? criteriaBuilder.notEqual(from.get(columnName), value) : null;
    }

    public Predicate in(String columnName) {
        return in(columnName, null);
    }

    /**
     * Builds `in` predicate based on filter values and types
     */
    @SuppressWarnings("unchecked")
    public Predicate in(String columnName, String filterName) {
        Object values = MapUtils.getObject(filters, StringUtils.defaultIfBlank(filterName, columnName));

        if (values == null) {
            return null;
        }

        if (values instanceof List<?> list) {
            if (list.isEmpty()) {
                return null;
            }

            Class<?> columnType = from.get(columnName).getJavaType();

            // Matches list element type to column type
            return switch (getListElementType(list)) {
                case STRING -> String.class.equals(columnType) ? from.get(columnName).in((List<String>) values) : null;
                case INTEGER ->
                        Integer.class.equals(columnType) ? from.get(columnName).in((List<Integer>) values) : null;
                case LONG -> Long.class.equals(columnType) ? from.get(columnName).in((List<Long>) values) : null;
                case UNKNOWN -> null;
            };
        }

        if (values instanceof String || values instanceof Integer || values instanceof Long) {
            return from.get(columnName).in(values);
        }

        return null;
    }

    private ListElementType getListElementType(List<?> list) {
        if (list.stream().allMatch(String.class::isInstance)) {
            return ListElementType.STRING;
        }
        if (list.stream().allMatch(Integer.class::isInstance)) {
            return ListElementType.INTEGER;
        }
        if (list.stream().allMatch(Long.class::isInstance)) {
            return ListElementType.LONG;
        }
        return ListElementType.UNKNOWN;
    }

    private enum ListElementType {
        STRING, INTEGER, LONG, UNKNOWN
    }

    public Predicate notIn(String columnName) {
        return notIn(columnName, null);
    }

    public Predicate notIn(String columnName, String filterName) {
        Predicate predicate = in(columnName, filterName);

        return predicate != null
                ? criteriaBuilder.not(predicate)
                : null;
    }

    public Predicate localDateTimeFrom(String columnName) {
        return localDateTimeFrom(columnName, null);
    }

    public Predicate localDateTimeFrom(String columnName, String filterName) {
        String value = MapUtils.getString(filters, StringUtils.defaultIfBlank(filterName, columnName + "From"));

        return StringUtils.isNotBlank(value)
                ? criteriaBuilder.greaterThanOrEqualTo(from.get(columnName), LocalDateTime.parse(value))
                : null;
    }

    public Predicate localDateTimeTo(String columnName) {
        return localDateTimeTo(columnName, null);
    }

    public Predicate localDateTimeTo(String columnName, String filterName) {
        String value = MapUtils.getString(filters, StringUtils.defaultIfBlank(filterName, columnName + "To"));

        return StringUtils.isNotBlank(value)
                ? criteriaBuilder.lessThanOrEqualTo(from.get(columnName), LocalDateTime.parse(value))
                : null;
    }

    public Predicate localDateTimeBetween(String columnName) {
        return localDateTimeBetween(columnName, null);
    }

    public Predicate localDateTimeBetween(String columnName, String filterName) {
        return and(localDateTimeFrom(columnName, filterName), localDateTimeTo(columnName, filterName));
    }

    public Predicate dateFrom(String columnName) {
        return dateFrom(columnName, null);
    }

    public Predicate dateFrom(String columnName, String filterName) {
        String value = MapUtils.getString(filters, StringUtils.defaultIfBlank(filterName, columnName + "From"));

        if (StringUtils.isNotBlank(value)) {
            return criteriaBuilder.greaterThanOrEqualTo(from.get(columnName), Date.from(OffsetDateTime.parse(value).toInstant()));
        }

        return null;
    }

    public Predicate dateTo(String columnName) {
        return dateTo(columnName, null);
    }

    public Predicate dateTo(String columnName, String filterName) {
        String value = MapUtils.getString(filters, StringUtils.defaultIfBlank(filterName, columnName + "To"));

        if (StringUtils.isNotBlank(value)) {
            return criteriaBuilder.lessThanOrEqualTo(from.get(columnName), Date.from(OffsetDateTime.parse(value).toInstant()));
        }

        return null;
    }

    public Predicate dateBetween(String columnName) {
        return dateBetween(columnName, null);
    }

    public Predicate dateBetween(String columnName, String filterName) {
        return and(dateFrom(columnName, filterName), dateTo(columnName, filterName));
    }

    public Predicate localDateFrom(String columnName) {
        return localDateFrom(columnName, null);
    }

    public Predicate localDateFrom(String columnName, String filterName) {
        String value = MapUtils.getString(filters, StringUtils.defaultIfBlank(filterName, columnName + "From"));
        return StringUtils.isNotBlank(value)
                ? criteriaBuilder.greaterThanOrEqualTo(from.get(columnName), LocalDate.parse(value))
                : null;
    }

    public Predicate localDateTo(String columnName) {
        return localDateTo(columnName, null);
    }

    public Predicate localDateTo(String columnName, String filterName) {
        String value = MapUtils.getString(filters, StringUtils.defaultIfBlank(filterName, columnName + "To"));
        return StringUtils.isNotBlank(value)
                ? criteriaBuilder.lessThanOrEqualTo(from.get(columnName), LocalDate.parse(value))
                : null;
    }

    public Predicate localDateBetween(String columnName) {
        return localDateBetween(columnName, null);
    }

    public Predicate localDateBetween(String columnName, String filterName) {
        return and(localDateFrom(columnName, filterName), localDateTo(columnName, filterName));
    }

    public Predicate numberFrom(String columnName) {
        return numberFrom(columnName, null);
    }

    public Predicate numberFrom(String columnName, String filterName) {
        Number value = MapUtils.getNumber(filters, StringUtils.defaultIfBlank(filterName, columnName) + "From");

        return value != null
                ? criteriaBuilder.ge(from.get(columnName), value)
                : null;
    }

    public Predicate numberTo(String columnName) {
        return numberTo(columnName, null);
    }

    public Predicate numberTo(String columnName, String filterName) {
        Number value = MapUtils.getNumber(filters, StringUtils.defaultIfBlank(filterName, columnName) + "To");
        return value != null ? criteriaBuilder.le(from.get(columnName), value) : null;
    }

    public Predicate search(String... columns) {
        return Optional.ofNullable(columns)
                .filter(cols -> cols.length > 0)
                .map(cols -> extractSearchValue())
                .filter(StringUtils::isNotBlank)
                .map(searchValue -> buildSearchPredicates(searchValue, columns))
                .orElse(null);
    }


    private Predicate buildSearchPredicates(String searchValue, String[] columns) {
        String upperSearchValue = "%" + searchValue.toUpperCase() + "%";

        Predicate[] predicates = Arrays.stream(columns)
                .map(column -> criteriaBuilder.like(
                        criteriaBuilder.upper(from.get(column)),
                        upperSearchValue
                ))
                .toArray(Predicate[]::new);

        return criteriaBuilder.or(predicates);
    }

    public Predicate searchNumeric(String... columns) {
        return Optional.ofNullable(columns)
                .filter(cols -> cols.length > 0)
                .map(cols -> extractSearchValue())
                .filter(this::isValidNumericSearch)
                .map(searchValue -> buildNumericSearchPredicates(searchValue, columns))
                .orElse(null);
    }

    private boolean isValidNumericSearch(String searchValue) {
        return StringUtils.isNotBlank(searchValue)
                && StringUtils.isNumeric(searchValue)
                && !StringUtils.startsWith(searchValue, "0");
    }

    private Predicate buildNumericSearchPredicates(String searchValue, String[] columns) {
        String likePattern = "%" + searchValue + "%";

        Predicate[] predicates = Arrays.stream(columns)
                .map(column -> criteriaBuilder.like(
                        from.get(column).as(String.class),
                        likePattern
                ))
                .toArray(Predicate[]::new);

        return criteriaBuilder.or(predicates);
    }

    public Predicate searchBoolean(String... columns) {
        return Optional.ofNullable(columns)
                .filter(cols -> cols.length > 0)
                .map(cols -> extractSearchValue())
                .filter(this::isValidBooleanSearch)
                .map(searchValue -> buildBooleanSearchPredicates(searchValue, columns))
                .orElse(null);
    }

    private boolean isValidBooleanSearch(String searchValue) {
        return StringUtils.isNotBlank(searchValue)
                && ("true".equalsIgnoreCase(searchValue) || "false".equalsIgnoreCase(searchValue));
    }

    private Predicate buildBooleanSearchPredicates(String searchValue, String[] columns) {
        String booleanStr = Boolean.parseBoolean(searchValue) ? "1" : "0";
        String likePattern = "%" + booleanStr + "%";

        Predicate[] predicates = Arrays.stream(columns)
                .map(column -> criteriaBuilder.like(
                        from.get(column).as(String.class),
                        likePattern
                ))
                .toArray(Predicate[]::new);

        return criteriaBuilder.or(predicates);
    }


    private String extractSearchValue() {
        return Optional.ofNullable(MapUtils.getString(filters, SEARCH))
                .filter(StringUtils::isNotBlank)
                .orElseGet(() -> MapUtils.getString(filters, SEARCH_TEXT));
    }


    public Predicate searchInDateColumns(String... columns) {
        if (columns == null || columns.length == 0) {
            return null;
        }

        String searchValue = MapUtils.getString(filters, SEARCH);

        if (StringUtils.isBlank(searchValue)) {
            searchValue = MapUtils.getString(filters, SEARCH_TEXT);
        }

        if (StringUtils.isNotBlank(searchValue)) {
            List<Predicate> predicates = new ArrayList<>();

            for (String column : columns) {
                predicates.add(buildDatePredicate(column, searchValue));
            }

            return criteriaBuilder.or(predicates.toArray(new Predicate[0]));
        }

        return null;
    }

    private Predicate buildDatePredicate(String column, String searchValue) {
        String functionToChar = "TO_CHAR";
        String functionTrunc = "TRUNC";

        if (isLocalDateTime(searchValue)) {
            LocalDateTime parsedDateTime = LocalDateTime.parse(searchValue, DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

            Predicate exactDateTimeMatch = criteriaBuilder.equal(
                    criteriaBuilder.function(functionTrunc, LocalDateTime.class, from.get(column)),
                    parsedDateTime.toLocalDate()
            );

            Predicate exactTimeMatch = criteriaBuilder.equal(
                    criteriaBuilder.function(functionToChar, String.class, from.get(column), criteriaBuilder.literal("HH24")),
                    parsedDateTime.format(DateTimeFormatter.ofPattern("HH"))
            );

            Predicate exactMinuteMatch = criteriaBuilder.equal(
                    criteriaBuilder.function(functionToChar, String.class, from.get(column), criteriaBuilder.literal("MI")),
                    parsedDateTime.format(DateTimeFormatter.ofPattern("mm"))
            );

            return criteriaBuilder.and(exactDateTimeMatch, exactTimeMatch, exactMinuteMatch);

        } else if (isDate(searchValue)) {
            LocalDate parsedDate = LocalDate.parse(searchValue, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            LocalDateTime startOfDay = parsedDate.atStartOfDay();
            LocalDateTime endOfDay = parsedDate.atTime(23, 59, 59);

            return criteriaBuilder.between(from.get(column), startOfDay, endOfDay);

        } else {
            Predicate yearLike = buildDateTimePredicate(column, "YYYY", searchValue);
            Predicate monthLike = buildDateTimePredicate(column, "MM", searchValue);
            Predicate dayLike = buildDateTimePredicate(column, "DD", searchValue);
            Predicate hourLike = buildDateTimePredicate(column, "HH24", searchValue);
            Predicate minuteLike = buildDateTimePredicate(column, "MI", searchValue);

            return criteriaBuilder.or(yearLike, monthLike, dayLike, hourLike, minuteLike);
        }
    }

    private Predicate buildDateTimePredicate(String column, String dateField, String searchValue) {
        String functionToChar = "TO_CHAR";

        return criteriaBuilder.like(
                criteriaBuilder.function(functionToChar, String.class, from.get(column), criteriaBuilder.literal(dateField)),
                "%" + searchValue + "%"
        );
    }


    private boolean isDate(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        try {
            LocalDate.parse(text, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private boolean isLocalDateTime(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        try {
            LocalDateTime.parse(text, DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

}
