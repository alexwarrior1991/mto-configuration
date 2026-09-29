# Mensajería: eventos de datos maestros y eventos propios

- El contrato de los eventos de `mto.master-data.exchange` es de este repositorio, y lo consumen
  `mto-stock`, `mto-maintenance` y `mto-notification`. Solo se **añaden** claves: renombrar o quitar
  una clave, un nombre de entidad o una clave de enrutado rompe a los consumidores. Cada cambio se
  documenta en README_MESSAGING.md (§2.4, con su aviso al principio del fichero) y se avisa a los
  tres.
- El sobre (`AsynchronousMessage`) lleva, además de las siete claves originales, `actor`
  (`{id, username, kind}`, con `kind` `PERSON`/`SERVICE`/`SYSTEM`) y `correlationId`. Los pone
  `AsynchronousMessageFactory` leyendo `MessageContextResolver` (el `SecurityContext` y el MDC) en
  el momento de crear el mensaje, que es el único en el que existe ese contexto; `messageHash` sigue
  siendo la huella de las siete claves originales. Un evento nunca lleva secretos en `values`. Lo
  fijan `AsynchronousMessageFactoryTest` y `MessageContextResolverTest`.
- Publican las ocho entidades anotadas con `@PublishMasterDataEvent(name = "…")`:
  `execution-package`, `station`, `track`, `profile`, `cantilever`, `steady-arm`, `disconnector` y
  `section-insulator`. El nombre del evento sale de esa anotación, así que cambiarla es cambiar el
  contrato. `SectionInsulatorSwitch` no publica: viaja dentro de su aislador.
- Lo que este servicio cuenta de sí mismo sale por su propio exchange, `mto.configuration.exchange`
  (`ConfigurationRabbitMqNames`), con `data` en la forma `DomainEvent`
  (`{entityName, entityId, eventName, values}`), nunca por el de datos maestros: quien escucha
  `mto.master-data.#` no recibe nada que no sea un dato maestro. Hoy hay un evento,
  `mto.configuration.job.finished` (`JobFinishedEventPublisher`), escrito en la misma transacción
  `REQUIRES_NEW` que el estado terminal del trabajo. Lo fijan `JobFinishedEventPublisherTest` y
  `ConfigurationRabbitMqNamesTest`.
- Un trabajo en segundo plano corre con su `jobId` como `correlationId` (`JobCorrelation.wrap`, al
  encolar), así que todos los eventos que escribe —los de datos maestros de una importación y su
  propio `job.finished`— viajan agrupados bajo él. El MDC de la petición cruza al hilo de fondo por
  `MdcTaskDecorator` en el executor de la aplicación. Lo fijan `JobCorrelationTest`,
  `ProfileJobServiceTest` y `MdcTaskDecoratorTest`.
- El evento se escribe en el outbox dentro de la transacción de negocio
  (`MasterDataEntityChangedEventListener` es un `@EventListener` síncrono) y el relay lo publica
  después. Solo el relay habla con el broker (`OutboxRabbitPublisher`), y no arranca sin publisher
  confirms (`OutboxWiringTest`).
- Cada entidad publicada tiene su mapper de payload explícito. Sin él cae en la serialización genérica
  de la entidad JPA, que entra en recursión con las relaciones bidireccionales
  (`MasterDataPayloadMapperCoverageTest`). Su repositorio tiene `findByIdForMessaging`, cuyo
  `@EntityGraph` carga en una sola sentencia todo lo que lee ese mapper (`MasterDataPayloadContractIT`,
  `MessagingEntityGraphIT`).
- Hay un ejemplo JSON por evento en `docs/messaging/examples/`, y `MessagingContractExamplesTest` lo
  construye con la factoría real y lo compara con el fichero: un evento nuevo o una clave nueva
  cambian el ejemplo en el mismo commit. Los consumidores los copian como fixtures.
- Este servicio publica y no consume: no hay ningún `@RabbitListener` y **no declara ninguna cola**,
  solo sus dos exchanges. Una cola pertenece a quien la consume, que la declara con sus bindings
  (`ApplicationRabbitMqTopologyTest`). Los argumentos de una cola que ya existe no se pueden cambiar
  (README_MESSAGING.md §9).
