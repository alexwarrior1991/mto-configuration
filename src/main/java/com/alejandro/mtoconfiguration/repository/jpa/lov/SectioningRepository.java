package com.alejandro.mtoconfiguration.repository.jpa.lov;

import com.alejandro.mtoconfiguration.entity.lov.Sectioning;
import com.alejandro.mtoconfiguration.repository.jpa.lov.commons.LovRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SectioningRepository extends LovRepository<Sectioning> {
}
