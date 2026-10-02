package com.alejandro.mtoconfiguration.service.commons;

import com.alejandro.mtoconfiguration.entity.lov.PoleType;
import com.alejandro.mtoconfiguration.model.commons.LovReferenceDTO;
import com.alejandro.mtoconfiguration.repository.jpa.lov.commons.LovRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * La resolucion de una LOV por su codigo, que es como los maestros leen sus referencias a catalogo
 * ({@code MasterDataService.get*ByCode}).
 *
 * <p>Que un codigo vacio no resuelva nada es contrato: una LOV opcional de un perfil se vacia
 * mandando la referencia sin codigo ({@code {}}), porque {@code null} es «no la toques»
 * (README_API.md §3). Lo usa mto-frontend.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Resolucion de una LOV por su codigo")
class LovReferenceResolverTest {

    @Mock
    private LovRepository<PoleType> repository;

    private final LovReferenceResolver resolver = new LovReferenceResolver();

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "  ")
    @DisplayName("un codigo vacio no resuelve nada ni consulta el catalogo")
    void codigoVacio(String code) {
        assertThat(resolver.resolveIdByCode("PoleType", code, repository)).isNull();
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("un codigo del catalogo da su id")
    void codigoConocido() {
        PoleType poleType = new PoleType();
        poleType.setId(7L);
        when(repository.findByCode("PT1")).thenReturn(poleType);

        assertThat(resolver.resolveIdByCode("PoleType", "PT1", repository)).isEqualTo(new LovReferenceDTO(7L));
    }

    @Test
    @DisplayName("un codigo que el catalogo no tiene tampoco resuelve nada")
    void codigoDesconocido() {
        when(repository.findByCode("NOPE")).thenReturn(null);

        assertThat(resolver.resolveIdByCode("PoleType", "NOPE", repository)).isNull();
    }
}
