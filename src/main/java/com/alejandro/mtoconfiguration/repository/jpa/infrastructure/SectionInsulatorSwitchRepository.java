package com.alejandro.mtoconfiguration.repository.jpa.infrastructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.SectionInsulatorSwitch;
import com.alejandro.mtoconfiguration.repository.jpa.commons.CRUDRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface SectionInsulatorSwitchRepository extends CRUDRepository<SectionInsulatorSwitch> {

    /**
     * Las agujas que ya tiene el aislador, como escalares.
     *
     * <p>El importador las usa para dos cosas: emparejar cada aguja del maestro con la que ya
     * existe —por el código del plano, que es su clave natural dentro del aislador, y así
     * reimportar conserva el id y el histórico de auditoría de cada una— y decidir si el maestro
     * trae algún cambio.
     *
     * <p>Proyección de escalares y no entidades, por la misma razón que
     * {@code CantileverRepository.findIdsByProfileIdOrderByIdAsc}: el importador no abre
     * transacción y un proxy fuera de sesión no se puede inicializar.
     */
    @Query("""
            select sw.id as switchId,
                   sw.code as code,
                   sw.kp as kp,
                   sw.turnoutDenominator as turnoutDenominator,
                   sw.track.id as trackId,
                   sw.enabled as enabled
            from SectionInsulatorSwitch sw
            where sw.sectionInsulator.id = :sectionInsulatorId
            order by sw.id asc
            """)
    List<SwitchSnapshot> findSnapshotsBySectionInsulatorId(@Param("sectionInsulatorId") Long sectionInsulatorId);

    /** Lo que el importador necesita de una aguja que ya existe. */
    interface SwitchSnapshot {
        Long getSwitchId();

        String getCode();

        BigDecimal getKp();

        Integer getTurnoutDenominator();

        Long getTrackId();

        Boolean getEnabled();
    }
}
