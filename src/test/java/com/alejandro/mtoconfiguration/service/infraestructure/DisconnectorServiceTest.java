package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.business.infrastructure.DisconnectorBusiness;
import com.alejandro.mtoconfiguration.mapper.infraestructure.DisconnectorMapper;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.DisconnectorFilter;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.DisconnectorCriteriaSearchRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.DisconnectorRepository;
import com.alejandro.mtoconfiguration.service.commons.MasterDataService;
import com.alejandro.mtoconfiguration.validator.infrastructure.DisconnectorValidator;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El filtro {@code onLoad} de los seccionadores: era primitivo, asi que {@code false} y "no viene"
 * eran lo mismo y README_API.md llego a documentarlo como que {@code false} no filtraba. Ahora
 * {@code true} y {@code false} filtran por ese estado y ausente no filtra, como el {@code enabled}
 * de los demas recursos.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DisconnectorServiceTest {

    @Mock
    private DisconnectorRepository repository;
    @Mock
    private DisconnectorMapper mapper;
    @Mock
    private DisconnectorValidator validator;
    @Mock
    private DisconnectorBusiness business;
    @Mock
    private DisconnectorCriteriaSearchRepository criteriaSearchRepository;
    @Mock
    private MasterDataService masterDataService;

    private DisconnectorService service;

    @BeforeEach
    void setUp() {
        service = new DisconnectorService(repository, mapper, validator, business, criteriaSearchRepository, masterDataService);
        when(repository.findAll(any(Predicate.class), any(Pageable.class))).thenReturn(Page.empty());
    }

    private BooleanBuilder predicateFor(Boolean onLoad) {
        service.getDisconnectors(PageRequest.of(0, 20), new DisconnectorFilter(null, null, null, null, onLoad));
        ArgumentCaptor<Predicate> captor = ArgumentCaptor.forClass(Predicate.class);
        verify(repository).findAll(captor.capture(), any(Pageable.class));
        return (BooleanBuilder) captor.getValue();
    }

    @Test
    @DisplayName("sin onLoad no filtra")
    void ausenteNoFiltra() {
        assertThat(predicateFor(null).hasValue()).isFalse();
    }

    @Test
    @DisplayName("false devuelve solo los que no estan en carga, ya no 'todo'")
    void falseFiltra() {
        assertThat(predicateFor(false).toString()).contains("disconnector.onLoad = false");
    }

    @Test
    @DisplayName("true devuelve solo los que estan en carga")
    void trueFiltra() {
        assertThat(predicateFor(true).toString()).contains("disconnector.onLoad = true");
    }
}
