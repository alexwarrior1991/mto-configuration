package com.alejandro.mtoconfiguration.utils;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.commons.AlertLevel;
import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import com.alejandro.mtoconfiguration.model.commons.LovDTO;
import org.apache.commons.collections4.CollectionUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

public class Utils {

    private Utils() {
        // Private constructor to hide the implicit public one
    }

    public static Boolean hasErrors(List<Alert> alerts) {
        return CollectionUtils.emptyIfNull(alerts).stream()
                .anyMatch(alert -> alert.getLevel() == AlertLevel.DANGER);
    }

    public static boolean isNew(BaseDTO dto) {
        return dto != null && dto.getId() == null;
    }

    public static boolean isNotNew(BaseDTO dto) {
        return !isNew(dto);
    }

    public static boolean exists(BaseDTO dto) {
        return dto != null && dto.getId() != null;
    }

    public static boolean notExists(BaseDTO dto) {
        return !exists(dto);
    }

    public static <T> Predicate<T> distinctByKey(Function<? super T, ?> key) {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> seen.add(key.apply(t));
    }

    public static Date truncateDateToDay(Date date) {
        return date != null ? Date.from(date.toInstant().truncatedTo(ChronoUnit.DAYS)) : null;
    }

    public static Date truncateDateToMinutes(Date date) {
        return date != null ? Date.from(date.toInstant().truncatedTo(ChronoUnit.MINUTES)) : null;
    }

    public static LocalDateTime getAsLocalDateTimeTruncatedToMinutes(Date date) {
        return date != null ? date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().truncatedTo(ChronoUnit.MINUTES) : null;
    }


    public static <S extends BaseDTO, T extends BaseDTO> void mapAuditProperties(S source, T target) {
        if (source == null || target == null) {
            return;
        }

        target.setCreateUser(source.getCreateUser());
        target.setVersionUser(source.getVersionUser());
        target.setCreateDate(source.getCreateDate());
        target.setVersionDate(source.getVersionDate());
        target.setVersionNumber(source.getVersionNumber());
    }

    public static <S extends LovDTO, T extends LovDTO> void mapLovProperties(S source, T target) {
        if (source == null || target == null) {
            return;
        }

        target.setCode(source.getCode());
        target.setDescription(source.getDescription());
        target.setType(source.getType());
        target.setEnabled(source.isEnabled());
        target.setVersionNumber(source.getVersionNumber());
    }
}
