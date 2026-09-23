---
paths:
  - "src/main/java/com/alejandro/mtoconfiguration/core/messaging/**/*.java"
  - "src/main/java/com/alejandro/mtoconfiguration/core/outbox/**/*.java"
  - "src/main/java/com/alejandro/mtoconfiguration/core/rabbitmq/**/*.java"
  - "src/main/java/com/alejandro/mtoconfiguration/masterdata/**/*.java"
  - "src/test/java/com/alejandro/mtoconfiguration/core/messaging/**/*.java"
  - "src/test/java/com/alejandro/mtoconfiguration/core/outbox/**/*.java"
  - "src/test/java/com/alejandro/mtoconfiguration/core/rabbitmq/**/*.java"
  - "src/test/java/com/alejandro/mtoconfiguration/masterdata/**/*.java"
  - "README_MESSAGING.md"
---
# Mensajería: eventos de datos maestros

- El contrato de los eventos de `mto.master-data.exchange` es de este repositorio, y lo consumen
  `mto-stock` y `mto-maintenance`. Solo se **añaden** claves: renombrar o quitar una clave, un nombre
  de entidad o una clave de enrutado rompe a los consumidores. Cada cambio se documenta en
  README_MESSAGING.md (§2.4, con su aviso al principio del fichero) y se avisa a los dos.
- Publican las ocho entidades anotadas con `@PublishMasterDataEvent(name = "…")`:
  `execution-package`, `station`, `track`, `profile`, `cantilever`, `steady-arm`, `disconnector` y
  `section-insulator`. El nombre del evento sale de esa anotación, así que cambiarla es cambiar el
  contrato. `SectionInsulatorSwitch` no publica: viaja dentro de su aislador.
- El evento se escribe en el outbox dentro de la transacción de negocio
  (`MasterDataEntityChangedEventListener` es un `@EventListener` síncrono) y el relay lo publica
  después. Solo el relay habla con el broker (`OutboxRabbitPublisher`), y no arranca sin publisher
  confirms (`OutboxWiringTest`).
- Cada entidad publicada tiene su mapper de payload explícito. Sin él cae en la serialización genérica
  de la entidad JPA, que entra en recursión con las relaciones bidireccionales
  (`MasterDataPayloadMapperCoverageTest`). Su repositorio tiene `findByIdForMessaging`, cuyo
  `@EntityGraph` carga en una sola sentencia todo lo que lee ese mapper (`MasterDataPayloadContractIT`,
  `MessagingEntityGraphIT`).
- Este servicio publica y no consume: no hay ningún `@RabbitListener`. Los argumentos de una cola que ya
  existe no se pueden cambiar (README_MESSAGING.md §9).
