package com.alejandro.mtoconfiguration.service.lov.commons;

import com.alejandro.mtoconfiguration.model.commons.LovDTO;
import com.alejandro.mtoconfiguration.repository.jpa.lov.commons.LovRepository;
import com.alejandro.mtoconfiguration.service.lov.commons.AbstractLovCrudServiceTest.TestLov;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Resolucion de una referencia a LOV que llega dentro de otro DTO.
 *
 * <p>El cliente puede mandar la LOV por id, por codigo o por las dos cosas, y de ahi sale la clave
 * ajena que se persiste. La regla que hay que fijar es el <b>orden</b>: manda el id y el codigo es
 * el plan B. Si se invirtiera, un DTO con id correcto y codigo obsoleto —lo normal en un cliente
 * que no refresca su catalogo— acabaria apuntando a otra fila sin que nadie se entere.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LovRelationResolverTest {

    private final LovRelationResolver resolver = new LovRelationResolver();

    @Mock
    private LovRepository<TestLov> repository;

    private static LovDTO dto(Long id, String code) {
        LovDTO dto = new LovDTO();
        dto.setId(id);
        dto.setCode(code);
        return dto;
    }

    @Test
    @DisplayName("con id y codigo manda el id")
    void elIdManda() {
        TestLov porId = new TestLov(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(porId));

        assertThat(resolver.resolveOptional(dto(1L, "OTRO"), repository)).isSameAs(porId);

        verify(repository, never()).findByCode("OTRO");
    }

    @Test
    @DisplayName("si el id no existe se intenta por codigo")
    void respaldoPorCodigo() {
        TestLov porCodigo = new TestLov(2L);
        when(repository.findById(1L)).thenReturn(Optional.empty());
        when(repository.findByCode("A")).thenReturn(porCodigo);

        assertThat(resolver.resolveOptional(dto(1L, "A"), repository)).isSameAs(porCodigo);
    }

    @Test
    @DisplayName("sin id se resuelve directamente por codigo")
    void soloCodigo() {
        TestLov porCodigo = new TestLov(2L);
        when(repository.findByCode("A")).thenReturn(porCodigo);

        assertThat(resolver.resolveOptional(dto(null, "A"), repository)).isSameAs(porCodigo);

        verify(repository, never()).findById(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("un codigo en blanco no llega a consultarse")
    void codigoEnBlanco() {
        assertThat(resolver.resolveOptional(dto(null, "   "), repository)).isNull();

        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("un DTO nulo se resuelve como ausencia, no como error")
    void dtoNuloOpcional() {
        assertThat(resolver.resolveOptional(null, repository)).isNull();

        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("en una relacion obligatoria, un DTO nulo nombra la relacion en el error")
    void dtoNuloObligatorio() {
        assertThatThrownBy(() -> resolver.resolveRequired(null, repository, "poleType"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("poleType is required");
    }

    @Test
    @DisplayName("en una relacion obligatoria, una referencia que no resuelve es un 'not found'")
    void referenciaObligatoriaInexistente() {
        when(repository.findById(9L)).thenReturn(Optional.empty());
        when(repository.findByCode("XX")).thenReturn(null);

        assertThatThrownBy(() -> resolver.resolveRequired(dto(9L, "XX"), repository, "poleType"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("poleType not found");
    }

    @Test
    @DisplayName("una relacion obligatoria que resuelve devuelve la entidad")
    void referenciaObligatoriaOk() {
        TestLov entity = new TestLov(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(entity));

        assertThat(resolver.resolveRequired(dto(1L, "A"), repository, "poleType")).isSameAs(entity);
    }
}
