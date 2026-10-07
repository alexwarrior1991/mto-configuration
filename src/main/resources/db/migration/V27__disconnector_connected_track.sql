-- La via con la que un seccionador pone en paralelo la suya.
--
-- Hay seccionadores que unen la catenaria de dos vias: los de puesta en paralelo (Disc/PP,
-- LoadB/PP, y -pr en portico), que el plano rotula casi siempre con la B del by-pass (HER-B02,
-- THS-BF01). Van montados en el poste de una via, que es la suya (la del perfil, o la propia sin
-- poste desde V26), pero aislar la OTRA via para trabajar obliga a abrirlos tambien, y con una sola
-- via guardada no se ven desde ella. Los guarda el propio seccionador, como el aislador de seccion
-- guarda la suya desde V23:
--
--   - connected_track_id, la otra via. Anulable: solo la tienen los que unen dos vias.
--
-- Tiene que ser otra via que la del seccionador: lo exigen DisconnectorValidator (sin poste, con su
-- via propia) y DisconnectorMapper (con poste, con la de su perfil), no la base, que no puede mirar
-- la via del perfil desde una restriccion de esta tabla.
--
-- La columna lleva su gemela _aud en ESTA misma migracion (ver V25). La clave ajena, solo en la
-- tabla base; la _aud lleva la columna sin restriccion, como las de V23 y V26. El indice sirve a
-- quien busque lo que conecta con una via.
--
-- Todo idempotente (IF NOT EXISTS / DROP IF EXISTS), por baseline-on-migrate.

alter table disconnector     add column if not exists connected_track_id bigint;
alter table disconnector_aud add column if not exists connected_track_id bigint;

alter table disconnector drop constraint if exists fk_disconnector_connected_track;
alter table disconnector add constraint fk_disconnector_connected_track
    foreign key (connected_track_id) references track;

create index if not exists idx_disconnector_connected_track on disconnector (connected_track_id);
