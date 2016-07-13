-- name: onko-olemassa?
-- single?: true
SELECT exists(SELECT *
              FROM suljettu_tieosuus
              WHERE aita_id = :id AND jarjestelma = :jarjestelma);

-- name: luo-suljettu-tieosuus<!
INSERT INTO suljettu_tieosuus
(jarjestelma,
 aita_id, sijainti,
 vastaanotettu,
 muokattu,
 kaistat,
 ajoradat,
 yllapitokohde)
VALUES
  (:jarjestelma,
   :aitaid,
   ST_MakePoint(:x, :y) :: POINT,
   :vastaanotettu,
   now(),
   :kaistat :: INTEGER [],
   :ajoradat :: INTEGER [],
   :yllapitokohde);

