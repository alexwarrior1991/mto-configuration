-- El KP y la via de un seccionador que no esta en un poste.
--
-- Desde V25 el poste es opcional: los seccionadores de los porticos de subestacion y los de puesta a
-- tierra no estan en un poste de la linea. Pero el KP y la via de un seccionador los ponia su poste,
-- asi que uno sin poste se quedaba sin ellos aunque el plano de seccionamiento los diga. Los guarda
-- el propio seccionador, como el aislador de seccion en V23:
--
--   - kilometric_point, en METROS con tres decimales, igual que profile.kilometric_point;
--   - track_id, la via.
--
-- SOLO en un seccionador sin poste: el de uno en un poste son los de su perfil, y guardarlos dos
-- veces dejaria dos datos que pueden contradecirse. Lo exige DisconnectorValidator, no la base, por
-- lo mismo que la base tampoco exige el poste.
--
-- Los dos anulables y con su gemela _aud en ESTA misma migracion (ver V25). La clave ajena, solo en
-- la tabla base; la _aud lleva la columna sin restriccion, como las de V23.
--
-- Todo idempotente (IF NOT EXISTS / DROP IF EXISTS), por baseline-on-migrate.

alter table disconnector     add column if not exists kilometric_point numeric(12, 3);
alter table disconnector_aud add column if not exists kilometric_point numeric(12, 3);

alter table disconnector     add column if not exists track_id bigint;
alter table disconnector_aud add column if not exists track_id bigint;

alter table disconnector drop constraint if exists fk_disconnector_track;
alter table disconnector add constraint fk_disconnector_track
    foreign key (track_id) references track;

create index if not exists idx_disconnector_track on disconnector (track_id);
