package com.alejandro.mtoconfiguration.repository.jpa.lov;

import com.alejandro.mtoconfiguration.entity.lov.ComercialEntityType;
import com.alejandro.mtoconfiguration.repository.jpa.lov.commons.LovRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ComercialEntityTypeRepository extends LovRepository<ComercialEntityType> {
}
