package com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports;

/**
 * Los diez codigos de lista de valores de un perfil, tal como vienen en el maestro.
 *
 * <p>Van agrupados y no sueltos en {@link ProfileMasterRow} porque son diez campos con
 * el mismo tratamiento —texto que se resuelve contra el catalogo— y desplegarlos alli
 * dejaba un record de veinte componentes en el que nadie acierta el orden.
 *
 * <p>Un codigo en blanco significa "el origen no lo trae", no "ponlo a null a la
 * fuerza": la relacion se deja sin tocar.
 */
public record ProfileLovCodes(
        String sectioning,
        String anchorage,
        String anchorageFoundation,
        String foundation,
        String poleType,
        String portal,
        String returnSupport,
        String sectioningFeeding,
        String supportType,
        String assemblyConfiguration
) {
    public static ProfileLovCodes empty() {
        return new ProfileLovCodes("", "", "", "", "", "", "", "", "", "");
    }
}
