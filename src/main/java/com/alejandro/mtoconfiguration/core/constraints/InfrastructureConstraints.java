package com.alejandro.mtoconfiguration.core.constraints;

/**
 * Restricciones de los campos de infraestructura, en un único sitio.
 *
 * <p>Las mismas constantes se usan en las anotaciones de las entidades (que definen la precisión de
 * la columna y las validaciones de Hibernate) y en los validadores de DTO. Antes cada capa llevaba
 * sus propios literales y habían divergido: los validadores aceptaban valores que la columna no
 * podía almacenar, de modo que el error salía como un 500 del driver en vez de como un 400 con el
 * campo señalado.</p>
 */
public final class InfrastructureConstraints {

    private InfrastructureConstraints() {
        // Evitar instanciación
    }

    // --- Nombres ---
    public static final int NAME_MIN_LENGTH = 1;
    public static final int NAME_MAX_LENGTH = 200;

    // --- Profile ---
    public static final int PROFILE_ID_MIN_LENGTH = 1;
    public static final int PROFILE_ID_MAX_LENGTH = 50;
    public static final int KP_INTEGER_DIGITS = 9;
    public static final int KP_FRACTION_DIGITS = 3;
    /** Máximo de vanos por perfil; réplica del {@code @Size} de la entidad. */
    public static final int PROFILE_MAX_CANTILEVERS = 3;

    /**
     * Vano hasta el perfil siguiente, en metros.
     *
     * <p>Es el sentido que le da el origen: en los workbooks el valor no está en la fila del
     * perfil sino en la intermedia, entre ese perfil y el siguiente.
     */
    public static final int SPAN_INTEGER_DIGITS = 3;
    public static final int SPAN_FRACTION_DIGITS = 3;

    /**
     * Altura del soporte de ménsula, separación del poste al gálibo y distancia carril-poste,
     * las tres en milímetros y sin decimales.
     *
     * <p>Seis dígitos son holgados para los rangos reales (25..7.400, 843..5.343 y
     * -6.290..9.125): se prefiere margen a tener que migrar la columna más adelante.
     *
     * <p>{@code RAIL_POLE_DISTANCE} es la única con signo, porque el signo indica a qué lado de
     * la vía queda el poste.
     */
    public static final int HEIGHT_CANTILEVER_SUPPORT_INTEGER_DIGITS = 6;
    public static final int HEIGHT_CANTILEVER_SUPPORT_FRACTION_DIGITS = 0;

    public static final int POLE_GAUGE_LOCATION_INTEGER_DIGITS = 6;
    public static final int POLE_GAUGE_LOCATION_FRACTION_DIGITS = 0;

    public static final int RAIL_POLE_DISTANCE_INTEGER_DIGITS = 6;
    public static final int RAIL_POLE_DISTANCE_FRACTION_DIGITS = 0;

    // --- SteadyArm ---
    /**
     * El mínimo del validador (1) es más estricto que el de la entidad ({@code @Min(0)}) a
     * propósito: una ménsula de longitud cero no es un dato válido de negocio, y rechazarla en el
     * DTO da un 400 con el campo señalado en vez de dejarla llegar a base de datos.
     */
    public static final long STEADY_ARM_LENGTH_MIN = 1L;
    public static final long STEADY_ARM_LENGTH_MAX = 2_000L;

    // --- Cantilever ---
    public static final int CW_HEIGHT_INTEGER_DIGITS = 1;
    public static final int CW_HEIGHT_FRACTION_DIGITS = 3;
    public static final String CW_HEIGHT_MIN = "0.000";

    public static final int STAGGER_INTEGER_DIGITS = 3;
    public static final int STAGGER_FRACTION_DIGITS = 0;

    public static final int CATENARY_HEIGHT_INTEGER_DIGITS = 1;
    public static final int CATENARY_HEIGHT_FRACTION_DIGITS = 3;

    public static final int CW_ELEVATION_INTEGER_DIGITS = 1;
    public static final int CW_ELEVATION_FRACTION_DIGITS = 3;

    public static final int WIND_DEFLECTION_INTEGER_DIGITS = 1;
    public static final int WIND_DEFLECTION_FRACTION_DIGITS = 3;

    public static final int ARM_ANGLE_INTEGER_DIGITS = 2;
    public static final int ARM_ANGLE_FRACTION_DIGITS = 3;
    public static final String ARM_ANGLE_MIN = "-90.000";
    public static final String ARM_ANGLE_MAX = "90.000";

    // --- SectionInsulatorSwitch ---
    /**
     * Código de la aguja tal y como lo escribe el plano: {@code W} y un número ({@code W31},
     * {@code W110}).
     *
     * <p>El patrón vive aquí y se aplica en el validador, no como {@code @Pattern} en la entidad:
     * así un código mal escrito sale como un {@code Alert} con su campo señalado y no como un 500
     * de Hibernate Validator en medio del flush.
     */
    public static final int SWITCH_CODE_MIN_LENGTH = 2;
    /**
     * Cuarenta, como todas las columnas {@code code} del esquema desde {@code V9}, y no los cinco
     * que necesita un {@code W1234}: {@code FlywayMigrationIT} lo comprueba para el conjunto del
     * esquema, y la forma real del código la fija {@link #SWITCH_CODE_PATTERN}, no el ancho de la
     * columna.
     */
    public static final int SWITCH_CODE_MAX_LENGTH = 40;
    public static final String SWITCH_CODE_PATTERN = "^W\\d{1,4}$";

    /**
     * Denominador de la tangente del desvío: el {@code 9} de {@code 1:9}.
     *
     * <p>Se guarda el entero y no el literal porque es lo único que varía —el numerador siempre es
     * 1— y así se puede ordenar y comparar: un {@code 1:12} es más tendido que un {@code 1:9}, cosa
     * que con el texto no se ve. La representación {@code 1:N} se compone al salir.
     *
     * <p>El máximo es holgado a propósito: el plano trae 1:8, 1:9 y 1:12, pero hay desvíos de alta
     * velocidad muy por encima y no compensa tener que migrar la validación por eso.
     */
    public static final int TURNOUT_DENOMINATOR_MIN = 1;
    public static final int TURNOUT_DENOMINATOR_MAX = 100;
}
