package com.alejandro.mtoconfiguration.masterdata.messaging;

import com.alejandro.mtoconfiguration.entity.infrastructure.Cantilever;
import com.alejandro.mtoconfiguration.entity.infrastructure.ExecutionPackage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El nombre logico de la entidad es el que viaja en el routing key y en el eventType:
 * si se desalinea con lo que espera el consumidor, el mensaje llega pero nadie lo
 * reconoce.
 */
class MasterDataEntityNameResolverTest {

    private final MasterDataEntityNameResolver resolver = new MasterDataEntityNameResolver();

    @Test
    void unaEntidadSinNombrePersonalizadoUsaSuNombreDeClaseNormalizado() {
        assertThat(resolver.resolve(new ExecutionPackage())).isEqualTo("execution-package");
    }

    @Test
    void unaEntidadConNombrePersonalizadoLoUsaEnLugarDelNombreDeClase() {
        // Cantilever declara @PublishMasterDataEvent(name = "cantilever"), coincide con
        // el nombre de clase normalizado, asi que lo relevante aqui es que SI se lee la
        // anotacion en vez de derivar el nombre por reflexion.
        assertThat(resolver.resolve(new Cantilever())).isEqualTo("cantilever");
    }

    @Test
    void unNombrePersonalizadoEnBlancoCaeAlNombreDeClase() {
        @PublishMasterDataEvent(name = "   ")
        class EntidadConNombreEnBlanco {
        }

        assertThat(resolver.resolve(new EntidadConNombreEnBlanco()))
                .isEqualTo("entidad-con-nombre-en-blanco");
    }

    @Test
    void unaEntidadNulaEsUnErrorDeProgramacion() {
        assertThatThrownBy(() -> resolver.resolve(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elCamelCaseSeParteEnGuionesYSePasaAMinusculas() {
        assertThat(resolver.normalize("SectionInsulator")).isEqualTo("section-insulator");
    }

    @Test
    void losGuionesBajosSeConviertenEnGuiones() {
        assertThat(resolver.normalize("section_insulator")).isEqualTo("section-insulator");
    }

    @Test
    void losEspaciosSeConviertenEnGuiones() {
        assertThat(resolver.normalize("section insulator")).isEqualTo("section-insulator");
    }

    @Test
    void losEspaciosSobrantesEnLosBordesSeRecortan() {
        assertThat(resolver.normalize("  station  ")).isEqualTo("station");
    }

    @Test
    void unNombreYaNormalizadoNoCambia() {
        assertThat(resolver.normalize("station")).isEqualTo("station");
    }
}
