package com.alejandro.mtoconfiguration.validator.commons;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import com.alejandro.mtoconfiguration.utils.Utils;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public abstract class CRUDValidator<T extends BaseDTO> extends GenericValidator<T> {

    public abstract List<Alert> validateBeforeDelete(T dto);

    protected <D extends BaseDTO> List<Alert> validateChildrenDtos(Collection<D> dtos, CRUDValidator<D> validator, boolean isNew) {
        if (CollectionUtils.isEmpty(dtos)) {
            return Collections.emptyList();
        }

        return dtos.stream()
                .filter(isNew ? Utils::isNew : Utils::isNotNew)
                .map(dto -> isNew ? validator.validateBeforeSave(dto) : validator.validateBeforeUpdate(dto))
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }
}
