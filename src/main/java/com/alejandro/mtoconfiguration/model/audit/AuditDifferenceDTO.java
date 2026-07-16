package com.alejandro.mtoconfiguration.model.audit;

/*
* Este DTO representa una diferencia entre dos revisiones.
* {
  "property": "description",
  "oldValue": "Valor anterior",
  "newValue": "Valor nuevo"
}
* */
public record AuditDifferenceDTO(
        String property,
        Object oldValue,
        Object newValue
) {
}
