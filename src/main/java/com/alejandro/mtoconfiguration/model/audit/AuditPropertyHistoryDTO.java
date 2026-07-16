package com.alejandro.mtoconfiguration.model.audit;

import java.time.LocalDateTime;

/*
* Este DTO sirve para consultar cómo ha cambiado una propiedad.
*
* [
  {
    "revision": 1,
    "revisionDateTime": "2026-07-16T09:30:00",
    "value": "Pendiente"
  },
  {
    "revision": 4,
    "revisionDateTime": "2026-07-16T09:45:00",
    "value": "Validado"
  }
]
* */
public record AuditPropertyHistoryDTO(
        Number revision,
        LocalDateTime revisionDateTime,
        Object value
) {
}
