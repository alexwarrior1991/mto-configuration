package com.alejandro.mtoconfiguration.service.lov.imports;

import com.alejandro.mtoconfiguration.entity.lov.Foundation;
import com.alejandro.mtoconfiguration.entity.lov.Sectioning;
import com.alejandro.mtoconfiguration.model.synchronous.lov.FoundationDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.SectioningDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.imports.LovImportReport;
import com.alejandro.mtoconfiguration.model.synchronous.lov.imports.LovMasterRow;
import com.alejandro.mtoconfiguration.repository.jpa.lov.commons.LovRepository;
import com.alejandro.mtoconfiguration.service.lov.commons.LovCrudService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reglas de la carga del catalogo maestro.
 *
 * <p>Se prueban aqui las tres decisiones que hacen que una reimportacion sea segura:
 * solo se carga lo marcado como ENABLED, un codigo que ya existe se actualiza en vez de
 * duplicarse, y una fila que falla no arrastra a las demas.
 */
@DisplayName("LovUpsertService")
class LovMasterImporterTest {

    private LovCrudService<SectioningDTO> sectioningService;
    private LovRepository<Sectioning> sectioningRepository;
    private LovImportTarget<SectioningDTO, Sectioning> sectioningTarget;
    private LovUpsertService upsertService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        sectioningService = mock(LovCrudService.class);
        sectioningRepository = mock(LovRepository.class);
        sectioningTarget = new LovImportTarget<>("Sectioning", sectioningService,
                sectioningRepository, SectioningDTO::new, null, null);
        upsertService = new LovUpsertService();
    }

    @Test
    @DisplayName("da de alta lo que no existe")
    void creaLoQueNoExiste() {
        when(sectioningRepository.findByCodeIn(anyList())).thenReturn(List.of());
        LovImportReport report = new LovImportReport(false);

        upsertService.upsertAll(sectioningTarget, List.of(rowOf("S/A", "Overlap Semi-Axis")),
                false, report, ok -> { });

        verify(sectioningService).create(any(SectioningDTO.class));
        assertThat(report.getCreated()).isEqualTo(1);
        assertThat(report.getUpdated()).isZero();
    }

    @Test
    @DisplayName("actualiza lo que existe con otra descripcion, sin duplicar el codigo")
    void actualizaLoQueCambio() {
        when(sectioningRepository.findByCodeIn(anyList()))
                .thenReturn(List.of(sectioning(7L, "S/A", "descripcion vieja")));
        LovImportReport report = new LovImportReport(false);

        upsertService.upsertAll(sectioningTarget, List.of(rowOf("S/A", "Overlap Semi-Axis")),
                false, report, ok -> { });

        verify(sectioningService).update(anyLong(), any(SectioningDTO.class));
        verify(sectioningService, never()).create(any());
        assertThat(report.getUpdated()).isEqualTo(1);
    }

    /**
     * Es lo que hace barata una reimportacion: sin esto, volver a cargar el mismo fichero
     * generaria una revision de Envers y un evento de invalidacion de cache por cada fila
     * del catalogo aunque no hubiese cambiado absolutamente nada.
     */
    @Test
    @DisplayName("no reescribe lo que ya esta igual")
    void noTocaLoQueNoCambio() {
        when(sectioningRepository.findByCodeIn(anyList()))
                .thenReturn(List.of(sectioning(7L, "S/A", "Overlap Semi-Axis")));
        LovImportReport report = new LovImportReport(false);

        upsertService.upsertAll(sectioningTarget, List.of(rowOf("S/A", "Overlap Semi-Axis")),
                false, report, ok -> { });

        verify(sectioningService, never()).create(any());
        verify(sectioningService, never()).update(anyLong(), any());
        assertThat(report.getUnchanged()).isEqualTo(1);
    }

    @Test
    @DisplayName("en simulacion cuenta lo mismo pero no escribe nada")
    void enSimulacionNoEscribe() {
        when(sectioningRepository.findByCodeIn(anyList())).thenReturn(List.of());
        LovImportReport report = new LovImportReport(true);

        upsertService.upsertAll(sectioningTarget, List.of(rowOf("S/A", "Overlap Semi-Axis")),
                true, report, ok -> { });

        verify(sectioningService, never()).create(any());
        assertThat(report.getCreated())
                .as("la simulacion tiene que decir exactamente lo que haria la carga real")
                .isEqualTo(1);
    }

    /**
     * Al contrario que {@code AbstractLovCrudService.bulkCreate}, que mete el lote entero
     * en una transaccion y pierde las mil filas buenas por una mala.
     */
    @Test
    @DisplayName("una fila que falla no arrastra al resto")
    void unaFilaMalaNoTumbaLaCarga() {
        when(sectioningRepository.findByCodeIn(anyList())).thenReturn(List.of());
        when(sectioningService.create(any(SectioningDTO.class)))
                .thenThrow(new IllegalStateException("codigo duplicado"))
                .thenReturn(new SectioningDTO());
        LovImportReport report = new LovImportReport(false);
        List<Boolean> progress = new ArrayList<>();

        upsertService.upsertAll(sectioningTarget,
                List.of(rowOf("MALA", "x"), rowOf("BUENA", "y")), false, report, progress::add);

        assertThat(report.getFailed()).isEqualTo(1);
        assertThat(report.getCreated()).isEqualTo(1);
        assertThat(progress).containsExactly(false, true);
        assertThat(report.getErrors().getFirst().code()).isEqualTo("MALA");
    }

    /**
     * Foundation, Portal y AnchorageFoundation tienen una relacion OBLIGATORIA hacia su
     * catalogo de tipos. Sin TIPO la fila no puede crearse, y es mejor decirlo con un
     * mensaje claro que dejar que reviente dentro de LovRelationResolver.
     */
    @Test
    @DisplayName("rechaza con mensaje claro una entidad con tipo obligatorio y sin tipo")
    @SuppressWarnings("unchecked")
    void exigeElTipoCuandoLaEntidadLoNecesita() {
        LovCrudService<FoundationDTO> foundationService = mock(LovCrudService.class);
        LovRepository<Foundation> foundationRepository = mock(LovRepository.class);
        when(foundationRepository.findByCodeIn(anyList())).thenReturn(List.of());

        LovImportTarget<FoundationDTO, Foundation> target = new LovImportTarget<>(
                "Foundation", foundationService, foundationRepository, FoundationDTO::new,
                FoundationDTO::setDrawingNumber,
                (dto, code) -> dto.setFoundationType(null));

        LovImportReport report = new LovImportReport(false);
        upsertService.upsertAll(target,
                List.of(new LovMasterRow("Foundation", "C3R", "Foundation", "", "", null, true, 2)),
                false, report, ok -> { });

        verify(foundationService, never()).create(any());
        assertThat(report.getErrors()).singleElement()
                .satisfies(error -> assertThat(error.message()).contains("TIPO"));
    }

    // --- utilidades ---------------------------------------------------------

    private LovMasterRow rowOf(String code, String description) {
        return new LovMasterRow("Sectioning", code, description, "", "", null, true, 2);
    }

    private Sectioning sectioning(Long id, String code, String description) {
        Sectioning entity = new Sectioning();
        entity.setId(id);
        entity.setCode(code);
        entity.setDescription(description);
        entity.setEnabled(true);
        return entity;
    }
}
