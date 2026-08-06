package com.alejandro.mtoconfiguration.service.lov.commons;

import com.alejandro.mtoconfiguration.model.commons.LovDTO;

import java.util.List;

public interface LovCrudService <D extends LovDTO>{

    D findById(Long id);

    D findByCode(String code);

    List<D> findAll();

    D create(D dto);

    D update(Long id, D dto);

    void delete(Long id);

    List<D> bulkCreate(List<D> dtos);

    List<D> bulkUpdate(List<D> dtos);
}
