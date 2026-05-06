package com.alejandro.mtoconfiguration.repository.jpa.infrastructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.Disconnector;
import com.alejandro.mtoconfiguration.repository.jpa.commons.CRUDRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DisconnectorRepository extends CRUDRepository<Disconnector> {
    List<Disconnector> findByStationId(Long stationId);
    List<Disconnector> findByStationNameContainingIgnoreCase(String stationName);
}
