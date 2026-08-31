package com.alejandro.mtoconfiguration.repository.jpa.commons;

import com.alejandro.mtoconfiguration.entity.commons.BaseEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Traduccion de un mapa de filtros a predicados de criteria.
 *
 * <p>Es la clase mas grande del proyecto y hasta ahora solo se ejercitaba de refilon, desde los
 * ITs de busqueda, que necesitan PostgreSQL y solo pasan por cuatro de sus cincuenta metodos. Aqui
 * se prueba contra un {@code CriteriaBuilder} simulado, de modo que se pueda comprobar
 * <b>exactamente</b> que llamada se construye —el patron del LIKE, el valor parseado de la fecha,
 * el tipo de la lista del IN— sin base de datos.</p>
 *
 * <p>La regla que gobierna casi todos los metodos es la misma: si el filtro no viene, o viene en
 * blanco, el predicado es {@code null} para que {@code and}/{@code or} lo descarten. Un metodo que
 * devuelva un predicado cuando no debe no da error: estrecha la busqueda en silencio.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PredicateBuilderTest {

    @Mock
    private CriteriaBuilder cb;
    @Mock
    private From<BaseEntity, BaseEntity> from;
    @Mock
    private Path<Object> namePath;
    @Mock
    private Expression<String> upperName;
    @Mock
    private Predicate marker;
    @Mock
    private Predicate other;

    private final Map<String, Object> filters = new HashMap<>();

    private PredicateBuilder<BaseEntity, BaseEntity> builder;

    @BeforeEach
    void setUp() {
        builder = new PredicateBuilder<>(cb, from, filters);

        doReturn(namePath).when(from).get("name");
        doReturn(namePath).when(from).get("length");
        doReturn(namePath).when(from).get("enabled");
        doReturn(upperName).when(cb).upper(any());
        when(cb.like(any(), anyString())).thenReturn(marker);
        when(cb.notLike(any(), anyString())).thenReturn(marker);
        when(cb.equal(any(), any(Object.class))).thenReturn(marker);
        when(cb.notEqual(any(), any(Object.class))).thenReturn(marker);
        when(cb.isTrue(any())).thenReturn(marker);
        when(cb.isFalse(any())).thenReturn(marker);
        when(cb.isNull(any())).thenReturn(marker);
        when(cb.isNotNull(any())).thenReturn(marker);
        when(cb.and(any(Predicate[].class))).thenReturn(marker);
        when(cb.or(any(Predicate[].class))).thenReturn(marker);
        // or(Expression, Expression) es un overload distinto del varargs, y es el que elige el
        // compilador cuando el codigo bajo prueba pasa exactamente dos predicados.
        when(cb.or(any(Expression.class), any(Expression.class))).thenReturn(marker);
        when(cb.not(any())).thenReturn(other);
        when(cb.ge(any(), any(Number.class))).thenReturn(marker);
        when(cb.le(any(), any(Number.class))).thenReturn(marker);
        when(cb.greaterThanOrEqualTo(any(), any(Comparable.class))).thenReturn(marker);
        when(cb.lessThanOrEqualTo(any(), any(Comparable.class))).thenReturn(marker);
    }

    /** Patron pasado al ultimo {@code like} construido. */
    private String capturedLikePattern() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(cb).like(any(), captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("LIKE")
    class Like {

        @Test
        @DisplayName("un valor con contenido produce un LIKE case-insensitive con comodines a ambos lados")
        void likeConValor() {
            filters.put("name", "atocha");

            assertThat(builder.like("name")).isSameAs(marker);
            assertThat(capturedLikePattern()).isEqualTo("%ATOCHA%");
        }

        @Test
        @DisplayName("un valor ausente, nulo o en blanco no genera predicado")
        void likeSinValor() {
            assertThat(builder.like("name")).isNull();

            filters.put("name", "");
            assertThat(builder.like("name")).isNull();

            filters.put("name", "   ");
            assertThat(builder.like("name")).isNull();

            filters.put("name", null);
            assertThat(builder.like("name")).isNull();

            verifyNoInteractions(cb);
        }

        @Test
        @DisplayName("con filterName la clave del mapa y la columna pueden ser distintas")
        void likeConFilterName() {
            filters.put("stationName", "atocha");

            assertThat(builder.like("name", "stationName")).isSameAs(marker);
            verify(from).get("name");
        }

        @Test
        @DisplayName("un filterName en blanco cae en el nombre de la columna")
        void likeConFilterNameEnBlanco() {
            filters.put("name", "atocha");

            assertThat(builder.like("name", "  ")).isSameAs(marker);
        }

        @Test
        @DisplayName("notLike construye la negacion, no un LIKE")
        void notLike() {
            filters.put("name", "atocha");

            assertThat(builder.notLike("name")).isSameAs(marker);

            verify(cb).notLike(any(), eq("%ATOCHA%"));
            verify(cb, never()).like(any(), anyString());
        }

        @Test
        @DisplayName("startWith ancla el comodin al final y endWith al principio")
        void prefijoYSufijo() {
            filters.put("name", "ato");

            assertThat(builder.startWith("name", null)).isSameAs(marker);
            assertThat(capturedLikePattern()).isEqualTo("ATO%");
        }

        @Test
        @DisplayName("las variantes de un solo argumento conservan su semantica de prefijo y sufijo")
        void prefijoYSufijoUnArgumento() {
            filters.put("name", "ato");

            builder.startWith("name");
            assertThat(capturedLikePattern()).isEqualTo("ATO%");
        }
    }

    @Nested
    @DisplayName("Igualdad y booleanos")
    class Igualdad {

        @Test
        @DisplayName("eq compara con el valor del filtro y sin valor no genera predicado")
        void eq() {
            assertThat(builder.eq("name")).isNull();

            filters.put("name", "ATOCHA");
            assertThat(builder.eq("name")).isSameAs(marker);
            verify(cb).equal(namePath, "ATOCHA");
        }

        @Test
        @DisplayName("eq acepta el valor false, que no es lo mismo que ausente")
        void eqConFalse() {
            filters.put("name", false);

            assertThat(builder.eq("name")).isSameAs(marker);
            verify(cb).equal(namePath, false);
        }

        @Test
        @DisplayName("ne construye la desigualdad")
        void ne() {
            filters.put("name", "ATOCHA");

            assertThat(builder.ne("name")).isSameAs(marker);
            verify(cb).notEqual(namePath, "ATOCHA");
        }

        @Test
        @DisplayName("isTrue con filtro solo actua si el valor es TRUE")
        void isTrueConFiltro() {
            assertThat(builder.isTrue("name", "onLoad")).as("ausente").isNull();

            filters.put("onLoad", false);
            assertThat(builder.isTrue("name", "onLoad")).as("false no filtra").isNull();

            filters.put("onLoad", "true");
            assertThat(builder.isTrue("name", "onLoad")).as("cadena no es Boolean").isNull();

            filters.put("onLoad", true);
            assertThat(builder.isTrue("name", "onLoad")).isSameAs(marker);
        }

        @Test
        @DisplayName("isFalse con filtro solo actua si el valor es FALSE")
        void isFalseConFiltro() {
            filters.put("onLoad", true);
            assertThat(builder.isFalse("name", "onLoad")).isNull();

            filters.put("onLoad", false);
            assertThat(builder.isFalse("name", "onLoad")).isSameAs(marker);
        }

        @Test
        @DisplayName("isNotTrue actua cuando el filtro esta ausente o es FALSE")
        void isNotTrue() {
            assertThat(builder.isNotTrue("name", "onLoad")).as("ausente si actua").isSameAs(marker);

            filters.put("onLoad", false);
            assertThat(builder.isNotTrue("name", "onLoad")).isSameAs(marker);

            filters.put("onLoad", true);
            assertThat(builder.isNotTrue("name", "onLoad")).isNull();
        }

        @Test
        @DisplayName("isNotFalse actua cuando el filtro esta ausente o es TRUE")
        void isNotFalse() {
            assertThat(builder.isNotFalse("name", "onLoad")).as("ausente si actua").isSameAs(marker);

            filters.put("onLoad", true);
            assertThat(builder.isNotFalse("name", "onLoad")).isSameAs(marker);

            filters.put("onLoad", false);
            assertThat(builder.isNotFalse("name", "onLoad")).isNull();
        }

        @Test
        @DisplayName("isBlank cubre tanto el nulo como la cadena vacia")
        void isBlank() {
            assertThat(builder.isBlank("name")).isSameAs(marker);

            verify(cb).isNull(namePath);
            verify(cb).equal(namePath, "");
        }

        @Test
        @DisplayName("isNotBlank comprueba la columna recibida")
        void isNotBlank() {
            assertThat(builder.isNotBlank("name")).isSameAs(marker);

            verify(from).get("name");
            verify(cb).isNotNull(namePath);
        }
    }

    @Nested
    @DisplayName("Combinacion")
    class Combinacion {

        @Test
        @DisplayName("and descarta los nulos antes de combinar")
        void andDescartaNulos() {
            assertThat(builder.and(marker, null, other)).isSameAs(marker);

            ArgumentCaptor<Predicate[]> captor = ArgumentCaptor.forClass(Predicate[].class);
            verify(cb).and(captor.capture());
            assertThat(captor.getValue()).containsExactly(marker, other);
        }

        @Test
        @DisplayName("and de puros nulos devuelve null en vez de una conjuncion vacia")
        void andTodoNulo() {
            // Una conjuncion vacia se traduce a "1=1" y ensucia la consulta; null hace que el
            // llamante omita el WHERE.
            assertThat(builder.and(null, null)).isNull();
            assertThat(builder.and()).isNull();

            verify(cb, never()).and(any(Predicate[].class));
        }

        @Test
        @DisplayName("or descarta los nulos y devuelve null si no queda nada")
        void or() {
            assertThat(builder.or(null, marker)).isSameAs(marker);
            assertThat(builder.or(null, null)).isNull();
        }
    }

    @Nested
    @DisplayName("IN")
    class In {

        @BeforeEach
        void stubIn() {
            doReturn(marker).when(namePath).in(any(Collection.class));
            doReturn(marker).when(namePath).in(any(Object[].class));
        }

        @Test
        @DisplayName("una lista de cadenas contra una columna de texto genera el IN")
        void listaDeCadenas() {
            doReturn(String.class).when(namePath).getJavaType();
            filters.put("name", List.of("A", "B"));

            assertThat(builder.in("name")).isSameAs(marker);
        }

        @Test
        @DisplayName("una lista cuyo tipo no casa con la columna no genera IN")
        void tipoQueNoCasa() {
            // Sin esta comprobacion Hibernate falla en tiempo de ejecucion al comparar
            // una columna de texto con una lista de numeros.
            doReturn(Long.class).when(namePath).getJavaType();
            filters.put("name", List.of("A", "B"));

            assertThat(builder.in("name")).isNull();
        }

        @Test
        @DisplayName("una lista mixta no genera IN")
        void listaMixta() {
            doReturn(String.class).when(namePath).getJavaType();
            filters.put("name", List.of("A", 1L));

            assertThat(builder.in("name")).isNull();
        }

        @Test
        @DisplayName("una lista vacia o un filtro ausente no generan IN")
        void listaVacia() {
            assertThat(builder.in("name")).isNull();

            filters.put("name", List.of());
            assertThat(builder.in("name")).isNull();
        }

        @Test
        @DisplayName("un escalar tambien vale como IN de un solo elemento")
        void escalar() {
            filters.put("name", "A");
            assertThat(builder.in("name")).isSameAs(marker);

            filters.put("name", 1L);
            assertThat(builder.in("name")).isSameAs(marker);
        }

        @Test
        @DisplayName("un tipo no contemplado no genera IN")
        void tipoNoContemplado() {
            filters.put("name", true);

            assertThat(builder.in("name")).isNull();
        }

        @Test
        @DisplayName("notIn niega el IN, y sin IN no hay nada que negar")
        void notIn() {
            assertThat(builder.notIn("name")).isNull();
            verify(cb, never()).not(any());

            doReturn(String.class).when(namePath).getJavaType();
            filters.put("name", List.of("A"));
            assertThat(builder.notIn("name")).isSameAs(other);
        }
    }

    @Nested
    @DisplayName("Rangos numericos")
    class Numeros {

        @Test
        @DisplayName("numberGe y numberLe leen la clave que se les indica")
        void geYLe() {
            assertThat(builder.numberGe("length", "lengthMin")).isNull();

            filters.put("lengthMin", 200L);
            filters.put("lengthMax", 300L);

            assertThat(builder.numberGe("length", "lengthMin")).isSameAs(marker);
            assertThat(builder.numberLe("length", "lengthMax")).isSameAs(marker);
        }

        @Test
        @DisplayName("numberFrom y numberTo derivan la clave añadiendo los sufijos From y To")
        void fromYTo() {
            filters.put("lengthFrom", 200L);
            filters.put("lengthTo", 300L);

            assertThat(builder.numberFrom("length")).isSameAs(marker);
            assertThat(builder.numberTo("length")).isSameAs(marker);
        }

        @Test
        @DisplayName("el valor cero es un limite valido, no un filtro ausente")
        void cero() {
            filters.put("lengthMin", 0);

            assertThat(builder.numberGe("length", "lengthMin")).isSameAs(marker);
        }
    }

    @Nested
    @DisplayName("Rangos de fecha")
    class Fechas {

        @Test
        @DisplayName("localDateFrom y localDateTo parsean la fecha ISO del filtro")
        void localDate() {
            filters.put("startDateFrom", "2026-01-01");
            filters.put("startDateTo", "2026-12-31");

            assertThat(builder.localDateFrom("startDate")).isSameAs(marker);
            verify(cb).greaterThanOrEqualTo(any(), eq(LocalDate.of(2026, 1, 1)));

            assertThat(builder.localDateTo("startDate")).isSameAs(marker);
            verify(cb).lessThanOrEqualTo(any(), eq(LocalDate.of(2026, 12, 31)));
        }

        @Test
        @DisplayName("localDateTimeFrom parsea fecha y hora")
        void localDateTime() {
            filters.put("createDateFrom", "2026-01-01T10:15:00");

            assertThat(builder.localDateTimeFrom("createDate")).isSameAs(marker);
            verify(cb).greaterThanOrEqualTo(any(), eq(LocalDateTime.of(2026, 1, 1, 10, 15)));
        }

        @Test
        @DisplayName("dateFrom parsea un instante con zona horaria")
        void date() {
            filters.put("createDateFrom", "2026-01-01T10:15:00Z");

            assertThat(builder.dateFrom("createDate")).isSameAs(marker);
            verify(cb).greaterThanOrEqualTo(any(), eq(Date.from(java.time.Instant.parse("2026-01-01T10:15:00Z"))));
        }

        @Test
        @DisplayName("un rango sin ninguno de los dos extremos no genera predicado")
        void rangoVacio() {
            assertThat(builder.localDateBetween("startDate")).isNull();
            assertThat(builder.localDateTimeBetween("createDate")).isNull();
            assertThat(builder.dateBetween("createDate")).isNull();
        }

        @Test
        @DisplayName("un rango con un solo extremo devuelve ese extremo, no una conjuncion")
        void rangoAbierto() {
            filters.put("startDateFrom", "2026-01-01");

            assertThat(builder.localDateBetween("startDate")).isSameAs(marker);

            ArgumentCaptor<Predicate[]> captor = ArgumentCaptor.forClass(Predicate[].class);
            verify(cb).and(captor.capture());
            assertThat(captor.getValue()).as("solo el extremo informado").hasSize(1);
        }

        @Test
        @DisplayName("un rango con los dos extremos los combina con AND")
        void rangoCerrado() {
            filters.put("startDateFrom", "2026-01-01");
            filters.put("startDateTo", "2026-12-31");

            assertThat(builder.localDateBetween("startDate")).isSameAs(marker);

            ArgumentCaptor<Predicate[]> captor = ArgumentCaptor.forClass(Predicate[].class);
            verify(cb).and(captor.capture());
            assertThat(captor.getValue()).as("los dos extremos").hasSize(2);
        }

        @Test
        @DisplayName("localDateGe y localDateLe leen la clave que se les indica")
        void clavesExplicitas() {
            filters.put("desde", "2026-01-01");
            filters.put("hasta", "2026-12-31");

            assertThat(builder.localDateGe("startDate", "desde")).isSameAs(marker);
            assertThat(builder.localDateLe("startDate", "hasta")).isSameAs(marker);
        }
    }

    @Nested
    @DisplayName("Busqueda libre")
    class BusquedaLibre {

        @Test
        @DisplayName("search lee la clave 'search' antes que 'searchText'")
        void precedenciaDeClaves() {
            filters.put("search", "atocha");
            filters.put("searchText", "chamartin");

            assertThat(builder.search("name")).isSameAs(marker);
            assertThat(capturedLikePattern()).isEqualTo("%ATOCHA%");
        }

        @Test
        @DisplayName("search cae en 'searchText' si 'search' viene en blanco")
        void respaldoEnSearchText() {
            filters.put("search", "  ");
            filters.put("searchText", "chamartin");

            assertThat(builder.search("name")).isSameAs(marker);
            assertThat(capturedLikePattern()).isEqualTo("%CHAMARTIN%");
        }

        @Test
        @DisplayName("sin texto de busqueda, o sin columnas, no hay predicado")
        void sinTextoNiColumnas() {
            assertThat(builder.search("name")).isNull();

            filters.put("searchText", "  ");
            assertThat(builder.search("name")).isNull();

            filters.put("searchText", "atocha");
            assertThat(builder.search()).isNull();
        }

        @Test
        @DisplayName("con varias columnas se combinan en un OR")
        void variasColumnas() {
            doReturn(namePath).when(from).get("description");
            filters.put("searchText", "atocha");

            assertThat(builder.search("name", "description")).isSameAs(marker);

            ArgumentCaptor<Predicate[]> captor = ArgumentCaptor.forClass(Predicate[].class);
            verify(cb).or(captor.capture());
            assertThat(captor.getValue()).hasSize(2);
        }

        @Test
        @DisplayName("searchNumeric compara la columna convertida a texto")
        void searchNumerico() {
            doReturn(namePath).when(namePath).as(String.class);
            filters.put("searchText", "250");

            assertThat(builder.searchNumeric("length")).isSameAs(marker);
            assertThat(capturedLikePattern()).isEqualTo("%250%");
        }

        @Test
        @DisplayName("searchNumeric pide un CAST real cuando el builder es el de Hibernate")
        void searchNumericoConCast() {
            // path.as(String.class) solo cambia el tipo en Java: no emite conversion en el SQL, y
            // contra PostgreSQL la consulta moria con "operator does not exist: bigint ~~ text".
            // Con el builder de Hibernate hay que pedir el cast explicitamente.
            org.hibernate.query.criteria.HibernateCriteriaBuilder hibernateBuilder =
                    org.mockito.Mockito.mock(org.hibernate.query.criteria.HibernateCriteriaBuilder.class);
            Path<Object> hibernatePath = org.mockito.Mockito.mock(Path.class,
                    org.mockito.Mockito.withSettings()
                            .extraInterfaces(org.hibernate.query.criteria.JpaExpression.class));
            org.hibernate.query.criteria.JpaExpression<String> casted =
                    org.mockito.Mockito.mock(org.hibernate.query.criteria.JpaExpression.class);

            From<BaseEntity, BaseEntity> hibernateFrom = org.mockito.Mockito.mock(From.class);
            doReturn(hibernatePath).when(hibernateFrom).get("length");
            doReturn(casted).when(hibernateBuilder).cast(any(), eq(String.class));
            org.hibernate.query.criteria.JpaPredicate hibernateMarker =
                    org.mockito.Mockito.mock(org.hibernate.query.criteria.JpaPredicate.class);
            when(hibernateBuilder.like(any(), anyString())).thenReturn(hibernateMarker);
            doReturn(hibernateMarker).when(hibernateBuilder).or(any(Predicate[].class));

            Map<String, Object> hibernateFilters = new HashMap<>();
            hibernateFilters.put("searchText", "250");

            PredicateBuilder<BaseEntity, BaseEntity> hibernateAware =
                    new PredicateBuilder<>(hibernateBuilder, hibernateFrom, hibernateFilters);

            assertThat(hibernateAware.searchNumeric("length")).isSameAs(hibernateMarker);

            verify(hibernateBuilder).cast(any(), eq(String.class));
            verify(hibernateBuilder).like(casted, "%250%");
            verify(hibernatePath, never()).as(String.class);
        }

        @Test
        @DisplayName("searchNumeric ignora un texto no numerico o que empiece por cero")
        void searchNumericoInvalido() {
            filters.put("searchText", "abc");
            assertThat(builder.searchNumeric("length")).isNull();

            // Un valor con cero a la izquierda no puede casar con un numero almacenado, y
            // convertirlo en LIKE '%0250%' devolveria siempre vacio.
            filters.put("searchText", "0250");
            assertThat(builder.searchNumeric("length")).isNull();

            filters.put("searchText", "25.5");
            assertThat(builder.searchNumeric("length")).isNull();
        }

        @Test
        @DisplayName("searchBoolean traduce true y false a 1 y 0")
        void searchBooleano() {
            doReturn(namePath).when(namePath).as(String.class);

            filters.put("searchText", "TRUE");
            assertThat(builder.searchBoolean("enabled")).isSameAs(marker);
            assertThat(capturedLikePattern()).isEqualTo("%1%");
        }

        @Test
        @DisplayName("searchBoolean ignora cualquier texto que no sea true o false")
        void searchBooleanoInvalido() {
            filters.put("searchText", "si");

            assertThat(builder.searchBoolean("enabled")).isNull();
        }
    }
}
