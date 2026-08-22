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
}
