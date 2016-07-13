-- name: onko-olemassa?
-- single?: true
SELECT exists(SELECT *
              FROM suljettu_tieosuus
              WHERE osuus_id = :id AND jarjestelma = :jarjestelma);

-- name: luo-suljettu-tieosuus<!
INSERT INTO suljettu_tieosuus
(jarjestelma,
 osuus_id,
 alkuaidan_sijainti,
 loppaidan_sijainti,
 asetettu,
 kaistat,
 ajoradat,
 yllapitokohde,
 kirjaaja)
VALUES
  (:jarjestelma,
   :osuusid,
   ST_MakePoint(:alkux, :alkuy) :: POINT,
   ST_MakePoint(:loppux, :loppuy) :: POINT,
   :asetettu,
   :kaistat :: INTEGER [],
   :ajoradat :: INTEGER [],
   :yllapitokohde,
   :kirjaaja);

-- name: paivita-suljettu-tieosuus!
UPDATE suljettu_tieosuus
SET
  alkuaidan_sijainti = ST_MakePoint(:alkux, :alkuy) :: POINT,
  loppaidan_sijainti = ST_MakePoint(:loppux, :loppuy) :: POINT,
  kaistat            = :kaistat :: INTEGER [],
  ajoradat           = :ajoradat :: INTEGER [],
  muokattu           = now(),
  asetettu           = :asetettu
WHERE osuus_id = :osuusid AND jarjestelma = :jarjestelma;

-- name: merkitse-suljettu-tieosuus-poistetuksi!
UPDATE suljettu_tieosuus
SET asetettu = :poistettu, poistaja = :poistaja
WHERE osuus_id = :osuusid AND jarjestelma = :jarjestelma;