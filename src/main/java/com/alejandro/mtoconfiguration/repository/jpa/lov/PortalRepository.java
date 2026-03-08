package com.alejandro.mtoconfiguration.repository.jpa.lov;

import com.alejandro.mtoconfiguration.entity.lov.Portal;
import com.alejandro.mtoconfiguration.repository.jpa.lov.commons.LovRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PortalRepository extends LovRepository<Portal> {
}
