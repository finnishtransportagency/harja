-- name: onko-olemassa?
-- single?: true
SELECT exists(SELECT *
              FROM liikenneohjausaidat
              WHERE aita_id = :id AND jarjestelma = :jarjestelma);

-- name: luo-liikenteenohjausaita<!
-- Luo uuden päällystysilmoituksen
INSERT INTO liikenneohjausaidat
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

