package com.alejandro.mtoconfiguration.repository.jpa.infrastructure;

import com.alejandro.mtoconfiguration.entity.configuration.BusinessEntity;
import com.alejandro.mtoconfiguration.repository.jpa.commons.CRUDRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BusinessEntityRepository extends CRUDRepository<BusinessEntity> {
}
