-- El perfil guarda por fin su tipo de soporte.
--
-- La columna 'Supports' de las hojas de trazado dice que pieza sujeta la catenaria en el
-- poste: S1, S2, S1/B7, OCR SUPPORT... Su catalogo, support_type, existe desde V1 y se
-- rellena desde los workbooks igual que los demas, pero NADIE apuntaba a el: la columna
-- se recogia en la hoja NO_MAPEADO del maestro y se quedaba ahi. 2.038 valores medidos.
--
-- Lo incoherente no era que faltara el campo, sino que el dato YA decidia lo que se
-- carga sin quedar guardado: el generador lee 'Supports = OCR SUPPORT' para deducir que
-- la mensula de ese hueco es de catenaria rigida. Se usaba la pista y se tiraba la fuente.
--
-- Es @ManyToOne y no N:M como sectioning o anchorage, y no por comodidad: en las 2.038
-- celdas medidas no hay ni una con dos codigos. Donde el origen escribe varios valores en
-- una celda, el modelo lleva una tabla de union; aqui no los escribe.
--
-- Opcional, como los demas campos tecnicos del perfil: la columna viene informada en
-- ~1.970 de los 11.714 perfiles cargables. Exigirla dejaria fuera al 83 %.
--
-- La columna gemela de profile_aud va sin NOT NULL y sin restricciones, como el resto de
-- la tabla de auditoria: Envers guarda una fila por revision y una revision de borrado no
-- trae valores.

ALTER TABLE profile     ADD COLUMN IF NOT EXISTS support_type_id bigint;
ALTER TABLE profile_aud ADD COLUMN IF NOT EXISTS support_type_id bigint;

-- La clave ajena solo en la tabla base. profile_aud no lleva ninguna, igual que las
-- otras nueve LOV del perfil: apuntaria a filas que pueden haber cambiado desde la
-- revision que se esta guardando.
--
-- DROP IF EXISTS + ADD, y no un DO con un SELECT sobre pg_constraint: ese SELECT hay que
-- acotarlo a un esquema, y en cuanto la tabla se resuelve por search_path desde OTRO
-- —que es lo que pasa al adoptar Flyway sobre una base que ya existia— la comprobacion
-- mira donde no es, no encuentra la restriccion y el ALTER revienta por duplicada.
ALTER TABLE profile DROP CONSTRAINT IF EXISTS fk_profile_support_type;
ALTER TABLE profile ADD CONSTRAINT fk_profile_support_type
    FOREIGN KEY (support_type_id) REFERENCES support_type;
