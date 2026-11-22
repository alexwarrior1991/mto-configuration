package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.core.exception.ValidationException;
import com.alejandro.mtoconfiguration.entity.infrastructure.Disconnector;
import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.service.commons.event.EntityChangeOperation;
import com.alejandro.mtoconfiguration.service.commons.event.EntityChangeTracker;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Que mira {@link LiveParentGuard} de lo que el flush ha escrito, y que no. La carrera de verdad,
 * contra PostgreSQL y con dos transacciones, esta en {@code DeleteWithDependentsIT}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("El padre de lo que se escribe sigue vivo")
class LiveParentGuardTest {

    @Mock
    private EntityManager entityManager;
    @Mock
    private Query query;

    private LiveParentGuard guard;

    @BeforeEach
    void setUp() {
        guard = new LiveParentGuard();
        ReflectionTestUtils.setField(guard, "entityManager", entityManager);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
    }

    private static Track via(long id) {
        Track track = new Track();
        track.setId(id);
        return track;
    }

    private static Profile perfil(long id, Track track) {
        Profile profile = new Profile();
        profile.setId(id);
        profile.setTrack(track);
        return profile;
    }

    private static EntityChangeTracker.Change cambio(Object entity, EntityChangeOperation operation) {
        return new EntityChangeTracker.Change((com.alejandro.mtoconfiguration.entity.commons.IEntity) entity, operation);
    }

    @Test
    @DisplayName("un alta cuyo padre borro otra peticion mientras tanto se rechaza con BUS-001 en su campo")
    void altaConElPadreBorrado() {
        when(query.getResultList()).thenReturn(List.of(7L));

        assertThatThrownBy(() -> guard.afterFlush(List.of(cambio(perfil(1L, via(7L)), EntityChangeOperation.CREATED))))
                .isInstanceOfSatisfying(ValidationException.class, rejected -> {
                    assertThat(rejected.getErrors()).singleElement().satisfies(alert -> {
                        assertThat(alert.getMessage())
                                .isEqualTo(ErrorCodes.BUSINESS_RULE_VIOLATION);
                        assertThat(alert.getFields()).containsExactly("trackId");
                    });
                });

        verify(entityManager).createNativeQuery(contains("from track where id in (:ids) and deleted = true"));
        verify(query).setParameter(eq("ids"), eq(Set.of(7L)));
    }

    @Test
    @DisplayName("con el padre vivo, el alta pasa")
    void altaConElPadreVivo() {
        assertThatCode(() -> guard.afterFlush(List.of(cambio(perfil(1L, via(7L)), EntityChangeOperation.CREATED))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("el padre que borra esta misma escritura no cuenta: ni se pregunta por el")
    void elPadreBorradoEnEstaEscritura() {
        Track borrada = via(7L);
        Disconnector seccionador = new Disconnector();
        seccionador.setId(3L);
        seccionador.setTrack(borrada);

        guard.afterFlush(List.of(
                cambio(borrada, EntityChangeOperation.DELETED),
                cambio(seccionador, EntityChangeOperation.CREATED)));

        verifyNoInteractions(query);
    }

    @Test
    @DisplayName("una modificacion que no cambia ninguna referencia no se mira; la que cambia una, si, y solo esa")
    void modificacion() {
        Profile perfil = perfil(1L, via(7L));

        guard.afterFlush(List.of(cambio(perfil, EntityChangeOperation.UPDATED)));
        verifyNoInteractions(entityManager);

        try (EntityChangeTracker.Scope ignored = EntityChangeTracker.open()) {
            EntityChangeTracker.recordChangedProperties(perfil, List.of("kp", "track"));

            guard.afterFlush(List.of(cambio(perfil, EntityChangeOperation.UPDATED)));
        }
        verify(entityManager).createNativeQuery(contains("from track where"));
    }

    @Test
    @DisplayName("lo que se borra no se mira: un huerfano de antes se puede borrar")
    void loBorradoNoSeMira() {
        guard.afterFlush(List.of(cambio(perfil(1L, via(7L)), EntityChangeOperation.DELETED)));

        verifyNoInteractions(query);
    }
}
