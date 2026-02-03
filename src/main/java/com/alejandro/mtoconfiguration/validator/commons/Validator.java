package com.alejandro.mtoconfiguration.validator.commons;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import com.alejandro.mtoconfiguration.model.commons.SearchRequestDTO;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public interface Validator<T extends BaseDTO> extends Serializable {

    List<Alert> validateBeforeSave(T dto);

    default List<Alert> validateBeforeUpdate(T dto) {
        return validateBeforeSave(dto);
    }

    default List<Alert> validateBeforeCancel(T dto) {
        return new ArrayList<>();
    }

    default List<Alert> validateBeforeSearch(SearchRequestDTO dto) {
        return new ArrayList<>();
    }

    default List<Alert> validateBeforeBulkSave(List<T> dto) {
        return new ArrayList<>();
    }
}
