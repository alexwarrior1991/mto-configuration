package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.business.infrastructure.ProfileBusiness;
import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.mapper.infraestructure.ProfileMapper;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ProfileDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.ProfileFilter;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.ProfileCriteriaSearchRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.ProfileRepository;
import com.alejandro.mtoconfiguration.validator.infrastructure.ProfileValidator;
import com.querydsl.core.types.Predicate;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Metodos propios de {@link ProfileService}: los que no hereda de la base y por tanto nadie mas
 * cubre.
 *
 * <p>Son tres cosas distintas y las tres se rompen en silencio: como se traducen los filtros
 * funcionales a un predicado de QueryDSL, que consulta de keyset se elige segun venga o no cursor,
 * y que el recorrido por ventanas desacople cada entidad del contexto de persistencia. Un fallo en
 * la primera devuelve resultados de mas; en la segunda, resultados repetidos; en la tercera, un
 * {@code OutOfMemoryError} al exportar una via larga.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfileServiceTest {

    @Mock
    private ProfileRepository repository;
    @Mock
    private ProfileMapper mapper;
    @Mock
    private ProfileValidator validator;
    @Mock
    private ProfileBusiness business;
    @Mock
    private ProfileCriteriaSearchRepository criteriaSearchRepository;
    @Mock
    private EntityManager entityManager;

    private ProfileService service;

    @BeforeEach
    void setUp() {
        service = new ProfileService(repository, mapper, validator, business, criteriaSearchRepository);
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
    }

    private static ProfileFilter filter(String profileId, Long trackId, String searchText) {
        return new ProfileFilter(profileId, null, trackId, null, null,
                null, null, null, null, null, null, null, null, searchText);
    }

    private static Profile profile(Long id, String kp) {
        Profile profile = new Profile();
        profile.setId(id);
        profile.setKp(new BigDecimal(kp));
        return profile;
    }

    private Predicate capturedPredicate() {
        ArgumentCaptor<Predicate> captor = ArgumentCaptor.forClass(Predicate.class);
        verify(repository).findAll(captor.capture(), any(Pageable.class));
        return captor.getValue();
    }

    @Nested
    @DisplayName("Filtros funcionales")
    class Filtros {

        @BeforeEach
        void stubRepository() {
            when(repository.findAll(any(Predicate.class), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));
        }

        @Test
        @DisplayName("un filtro con valor se traduce a una condicion sobre su columna")
        void filtroConValor() {
            service.getProfiles(PageRequest.of(0, 20), filter("P-1", 3L, null));

            assertThat(capturedPredicate().toString())
                    .contains("profileId")
                    .contains("track.id = 3");
        }

        @Test
        @DisplayName("los campos vacios del filtro no añaden condiciones")
        void filtroVacio() {
            // ProfileFilter normaliza los nulos a cadena vacia en su constructor, asi que sin este
            // descarte una peticion sin filtros generaria un LIKE '%%' por cada campo de texto.
            service.getProfiles(PageRequest.of(0, 20), filter(null, null, null));

            assertThat(((com.querydsl.core.BooleanBuilder) capturedPredicate()).hasValue())
                    .as("un filtro entero en blanco no debe generar ninguna condicion")
                    .isFalse();
        }

        @Test
        @DisplayName("el texto libre busca en el perfil, la via y la estacion a la vez")
        void busquedaLibre() {
            service.getProfiles(PageRequest.of(0, 20), filter(null, null, "torre"));

            String predicate = capturedPredicate().toString();
            assertThat(predicate).contains("profileId");
            assertThat(predicate).contains("track.name");
            assertThat(predicate).contains("track.station.name");
            assertThat(predicate).contains("||");
        }

        @Test
        @DisplayName("el texto libre se combina con el resto de filtros, no los sustituye")
        void busquedaLibreConFiltros() {
            service.getProfiles(PageRequest.of(0, 20), filter(null, 3L, "torre"));

            String predicate = capturedPredicate().toString();
            assertThat(predicate).contains("track.id = 3");
            assertThat(predicate).contains("&&");
        }

        @Test
        @DisplayName("el resultado se mapea a DTO manteniendo la paginacion")
        void resultadoMapeado() {
            when(repository.findAll(any(Predicate.class), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(profile(1L, "10"))));
            when(mapper.toDTO(any(Profile.class))).thenReturn(new ProfileDTO());

            assertThat(service.getProfiles(PageRequest.of(0, 20), filter(null, null, null)).getContent())
                    .hasSize(1);
        }
    }

    @Nested
    @DisplayName("Ventanas por keyset")
    class Keyset {

        @Test
        @DisplayName("sin cursor se lee desde el principio de la via")
        void primeraVentana() {
            when(repository.findByTrackIdOrderByKpAscIdAsc(any(), any())).thenReturn(List.of(profile(1L, "0")));

            service.getProfilesByKeyset(3L, null, null, 50);

            verify(repository).findByTrackIdOrderByKpAscIdAsc(3L, PageRequest.of(0, 50));
            verify(repository, never()).findNextPage(any(), any(), any(), any());
        }

        @Test
        @DisplayName("un cursor incompleto tambien se trata como primera ventana")
        void cursorIncompleto() {
            // Con solo uno de los dos valores el keyset no es estable: repetiria o saltaria filas
            // en cuanto dos perfiles compartan KP.
            when(repository.findByTrackIdOrderByKpAscIdAsc(any(), any())).thenReturn(List.of());

            service.getProfilesByKeyset(3L, new BigDecimal("10"), null, 50);
            service.getProfilesByKeyset(3L, null, 7L, 50);

            verify(repository, times(2)).findByTrackIdOrderByKpAscIdAsc(any(), any());
            verify(repository, never()).findNextPage(any(), any(), any(), any());
        }

        @Test
        @DisplayName("con cursor completo se salta directamente al punto pedido")
        void siguienteVentana() {
            when(repository.findNextPage(any(), any(), any(), any())).thenReturn(List.of());

            service.getProfilesByKeyset(3L, new BigDecimal("12.5"), 7L, 25);

            verify(repository).findNextPage(3L, new BigDecimal("12.5"), 7L, PageRequest.of(0, 25));
            verify(repository, never()).findByTrackIdOrderByKpAscIdAsc(any(), any());
        }

        @Test
        @DisplayName("el rango de KP delega en la consulta con indice compuesto")
        void rangoDeKp() {
            when(repository.findByKpRange(any(), any(), any())).thenReturn(List.of(profile(1L, "5")));
            when(mapper.toDTO(any(Profile.class))).thenReturn(new ProfileDTO());

            assertThat(service.getProfilesByKpRange(3L, new BigDecimal("1"), new BigDecimal("9"))).hasSize(1);

            verify(repository).findByKpRange(3L, new BigDecimal("1"), new BigDecimal("9"));
        }
    }

    @Nested
    @DisplayName("Recorrido por ventanas")
    class Recorrido {

        @Test
        @DisplayName("recorre todas las ventanas encadenando el cursor de la anterior")
        void recorridoCompleto() {
            Profile first = profile(1L, "1");
            Profile second = profile(2L, "2");
            Profile third = profile(3L, "3");

            when(repository.findByTrackIdOrderByKpAscIdAscGraph(any(), any())).thenReturn(List.of(first, second));
            when(repository.findNextPageGraph(3L, second.getKp(), 2L, PageRequest.of(0, 500)))
                    .thenReturn(List.of(third));
            when(repository.findNextPageGraph(3L, third.getKp(), 3L, PageRequest.of(0, 500)))
                    .thenReturn(List.of());

            List<Profile> visited = new ArrayList<>();
            service.processProfilesByTrack(3L, visited::add);

            assertThat(visited).containsExactly(first, second, third);
        }

        @Test
        @DisplayName("cada perfil se desacopla del contexto tras procesarlo")
        void detachPorPerfil() {
            // Sin el detach el contexto de persistencia se queda con la via entera: es justo lo que
            // este recorrido existe para evitar.
            Profile first = profile(1L, "1");
            when(repository.findByTrackIdOrderByKpAscIdAscGraph(any(), any())).thenReturn(List.of(first));
            when(repository.findNextPageGraph(any(), any(), any(), any())).thenReturn(List.of());

            service.processProfilesByTrack(3L, p -> {
            });

            verify(entityManager).detach(first);
        }

        @Test
        @DisplayName("una via sin perfiles no procesa nada")
        void viaVacia() {
            when(repository.findByTrackIdOrderByKpAscIdAscGraph(any(), any())).thenReturn(List.of());

            List<Profile> visited = new ArrayList<>();
            service.processProfilesByTrack(3L, visited::add);

            assertThat(visited).isEmpty();
            verify(entityManager, never()).detach(any());
        }
    }

    @Nested
    @DisplayName("Parametros de busqueda")
    class ParametrosDeBusqueda {

        @Test
        @DisplayName("los parametros de busqueda son inmutables")
        void parametrosInmutables() {
            // searchParams() viaja hasta el predicado de criteria; devolverlo mutable dejaria que
            // un repositorio se colase datos de permisos de otra peticion.
            Object params = ReflectionTestUtils.invokeMethod(service, "searchParams");

            assertThat(params).isInstanceOf(java.util.Map.class);
            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () -> ((java.util.Map<String, Object>) params).put("roles", List.of("ADMIN")))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
