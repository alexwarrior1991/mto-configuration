package com.alejandro.mtoconfiguration.controller.commons;

import com.alejandro.mtoconfiguration.entity.commons.IEntity;
import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import com.alejandro.mtoconfiguration.service.commons.BaseService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@ApiResponsesStandard
public interface BaseController<T extends BaseDTO, E extends IEntity> {

    BaseService<T, E> getService();

    default String getEntityName() {
        return getService().getEntity().getClass().getSimpleName().toLowerCase();
    }

    default <R extends BaseDTO> ResponseEntity<Object> processRequestWithValidation(ThrowingFunction<T, R> function, T argument) {
        return ResponseEntity.ok(function.apply(argument));
    }

    default <R extends BaseDTO> ResponseEntity<Object> processBulkRequestWithValidation(ThrowingListFunction<T, R> function, List<T> argument) {
        return ResponseEntity.ok(function.apply(argument));
    }

    default <R extends BaseDTO> ResponseEntity<Object> processRequest(ThrowingFunction<T, R> function, T argument) {
        return ResponseEntity.ok(function.apply(argument));
    }

    default <A, R extends BaseDTO> ResponseEntity<Object> processGenericRequest(ThrowingFunction<A, R> function, A argument) {
        return ResponseEntity.ok(function.apply(argument));
    }

    default <A, R extends BaseDTO> ResponseEntity<Object> processGenericListRequest(ThrowingFunction<A, List<R>> function, A argument) {
        return ResponseEntity.ok(function.apply(argument));
    }

    default <A, R extends BaseDTO> ResponseEntity<Object> processGenericListRequestWithValidation(ThrowingFunction<A, List<R>> function, A argument) {
        return ResponseEntity.ok(function.apply(argument));
    }

    default <A, R> ResponseEntity<Object> processGenericListRequestGeneric(ThrowingFunction<A, List<R>> function, A argument) {
        return ResponseEntity.ok(function.apply(argument));
    }

    default <A, R extends BaseDTO> ResponseEntity<Object> processGenericRequestWithValidation(ThrowingFunction<A, R> function, A argument) {
        return ResponseEntity.ok(function.apply(argument));
    }

    default <A, R extends BaseDTO> ResponseEntity<Object> processGenericPageRequest(
            ThrowingFunction<A, Page<R>> function,
            A argument
    ) {
        return ResponseEntity.ok(function.apply(argument));
    }

}
