-- Un poste admite varios seccionadores vivos.
--
-- V24 dejo en disconnector.profile_id un indice unico parcial: un perfil, un
-- seccionador vivo. En la linea no es asi: casi siempre hay uno, pero en algunos
-- postes cuelgan dos (los de una zona neutra, los de un haz de vias en talleres y
-- cocheras), y el segundo no se podia guardar.
--
-- Profile.disconnectors pasa a ser una coleccion (el lado inverso de un @ManyToOne)
-- con @SQLRestriction("deleted = false"), asi que lo borrado sigue sin contar. El
-- seccionador no cambia: su vinculo es su profile_id, como antes.
--
-- Fuera el indice unico y, en su lugar, uno normal sobre las filas vivas: el
-- esquema de la via y el evento del perfil cargan sus seccionadores por profile_id,
-- y la clave ajena no lleva indice propio. ddl-auto: validate no comprueba indices;
-- lo cubre FlywayMigrationIT.
DROP INDEX IF EXISTS ux_disconnector_profile_id;

CREATE INDEX IF NOT EXISTS idx_disconnector_profile_id
    ON disconnector (profile_id) WHERE deleted = false;
