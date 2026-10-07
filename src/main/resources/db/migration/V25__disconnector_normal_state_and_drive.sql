-- El seccionador, con su estado normal y su accionamiento; y el poste deja de ser obligatorio.
--
-- El plano de seccionamiento dice de cada seccionador dos cosas que la tabla no guardaba:
--
--   - su ESTADO NORMAL de explotacion, que es la cuchilla dibujada abierta o cerrada. Se guarda
--     como booleano 'normally_open': true es normalmente abierto, false normalmente cerrado.
--   - su ACCIONAMIENTO, el circulo del motor junto a la cuchilla. Se guarda como texto
--     ('MOTOR' o 'MANUAL'), igual que installation_type del aislador en V23: la columna se lee
--     sola en una consulta, y anadir un valor no depende del orden de declaracion del enum.
--
-- Los dos anulables: los seccionadores que ya estan en base no traen ninguno, y exigirlos aqui
-- convertiria el despliegue en una migracion de datos que nadie puede rellenar todavia.
--
-- El poste (profile_id) ya era anulable desde V1: lo exigia el validador, no la base. Deja de
-- exigirlo porque hay seccionadores que no estan en un poste de la linea. El indice unico parcial
-- de V24 no cambia: PostgreSQL no compara nulos en un indice unico, asi que los seccionadores sin
-- poste no se estorban entre si.
--
-- La gemela _aud recibe el mismo cambio en ESTA misma migracion: Hibernate construye el mapeo de
-- Envers desde la entidad, asi que una columna anadida sin su gemela hace fallar a
-- ddl-auto: validate y la aplicacion no arranca.
--
-- Todo idempotente (IF NOT EXISTS), como el resto de migraciones, por baseline-on-migrate: la
-- migracion tiene que poder pasar sobre una base que ya la tenga.

alter table disconnector     add column if not exists normally_open boolean;
alter table disconnector_aud add column if not exists normally_open boolean;

alter table disconnector     add column if not exists drive_type varchar(30);
alter table disconnector_aud add column if not exists drive_type varchar(30);
