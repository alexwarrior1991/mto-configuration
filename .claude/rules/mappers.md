---
paths:
  - "src/main/java/com/alejandro/mtoconfiguration/mapper/**/*.java"
  - "src/test/java/com/alejandro/mtoconfiguration/mapper/**/*.java"
---
# Mappers (MapStruct)

- Un mapper de infraestructura es `public abstract class XMapper implements BaseMapper<XDTO, X>` con
  `@Mapper(config = CentralConfigMapper.class, uses = …)`; uno de catálogo, una interfaz vacía que
  extiende `LovMapper<XDTO, X>` con el mismo `config`. `CentralConfigMapper` fija
  `unmappedTargetPolicy = IGNORE`: un campo que no casa por nombre no da aviso, se queda sin mapear.
- `toEntity` y `updateEntityFromDTO` llevan `@ToEntityIgnoreAudit`: el id, la auditoría
  (`createDate`, `createUser`, `versionDate`, `versionUser`), `versionNumber` y `dirty` nunca se copian
  del DTO a la entidad. Un mapper que sobrescribe uno de los dos (los de catálogo con tipo padre, para
  ignorarlo) repite la anotación, porque MapStruct no hereda los `@Mapping`: sin ella, el alta podía
  pisar la fila del id del cuerpo y la modificación acababa en un 500. Lo fija `LovMapperTest`
  («Todos los catálogos»), que recorre los mappers generados de los diecisiete catálogos.
- `BaseMapper.updateDTOFromEntity` no lleva `@Mapping`, y MapStruct no comparte los `@Mapping` entre
  métodos. Un mapper cuyo `toDTO` pone ids de padre (`@Mapping(target = "trackId", source =
  "track.id")`) la sobrescribe con `@Override @InheritConfiguration(name = "toDTO")`; si no, el
  detalle (`GET /{recurso}/{id}`) y las respuestas de alta y modificación, que terminan en
  `updateDTOFromEntity`, devuelven esos ids a `null`. Lo hacen los nueve mappers de infraestructura con
  padre, y lo fijan los casos de detalle de sus `*MapperTest`, que llaman a `updateDTOFromEntity`.
- Las referencias a catálogo se ignoran en el `@Mapping` y se resuelven en los `@AfterMapping`:
  `mapDtoToEntity` las busca por código en `MasterDataService`, y `mapEntityToDto` las rellena por id
  desde su caché. Un `@AfterMapping` se aplica a todo método con esos tipos, también a
  `updateDTOFromEntity`. Al modificar, una referencia a `null` no toca nada y una sin código la
  quita, porque el catálogo no resuelve un código vacío (README_API.md §3); lo fijan
  `ProfileMapperTest` y `LovReferenceResolverTest`.
- Las colecciones de hijos también se ignoran en el `@Mapping` y se reconcilian en `mapDtoToEntity`
  con `BaseMapper.mergeCollection`: con id se actualiza, sin id se crea, lo que no llega se borra y
  `null` no toca nada (README_API.md §4). Los 1:1 se enlazan con `linkEntity`. Nunca con la
  estrategia de colecciones de MapStruct: reemplazar borra e inserta, y los hijos cambian de id y
  pierden su historial. Lo fijan `BaseMapperTest` («Fusion por id») y los `mapper/merge/*ChildMergeIT`.
- El seccionador del perfil no es un hijo: el perfil es el lado inverso de un vínculo que escribe el
  seccionador (`disconnector.profile_id`, con sus datos), así que `ProfileMapper` lo ignora al
  escribir (`@Mapping(target = "disconnector", ignore = true)`) y `ProfileValidator` no lo valida.
  Desde el perfil es solo de salida (README_API.md §4). Cuando el código generado lo volcaba, otro
  seccionador se copiaba sobre el actual, el alta creaba una copia y `null` lo quitaba de la
  respuesta pero no de la base. Lo fijan `ProfileMapperTest` y `DisconnectorLinkIT`.
- Antes de volcar un hijo que llega con su id, se compara su `versionNumber` con el guardado
  (`BaseMapper.checkVersion`): `mergeCollection` lo hace con cada hijo de la colección, y un 1:1 nuevo
  tiene que llamarlo en su `@AfterMapping`, como el brazo en `CantileverMapper`. Lo fijan
  `BaseMapperTest` («Version de los hijos»), `ProfileMapperTest` y `CantileverMapperTest`.
- Tests: `MapperGraph` monta los mappers generados reales con sus dependencias
  (`new MapperGraph(masterDataService, referenceMapper).profile`); un `XMapperTest` por mapper, con un
  `@Nested` por aspecto.
