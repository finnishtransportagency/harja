-- name: hae-pohjavesialueet
-- Hakee pohjavesialueet annetulle hallintayksikölle
SELECT
  id,
  nimi,
  alue,
  tunnus
FROM pohjavesialueet_hallintayksikoittain
WHERE hallintayksikko = :hallintayksikko;

-- name: hae-urakan-pohjavesialueet
-- Hakee hoidon alueurakan alueella olevat pohjavesialueet
SELECT
  p.nimi,
  p.tunnus,
  p.alue
FROM pohjavesialueet_urakoittain p
WHERE p.urakka = :urakka;

-- name: tuhoa-pohjavesialuedata!
-- Poistaa kaikki pohjavesialueet
DELETE FROM pohjavesialue;

-- name: luo-pohjavesialue!
INSERT INTO pohjavesialue (nimi, tunnus, ulkoinen_id, alue, suolarajoitus) VALUES
  (:nimi, :tunnus, :ulkoinen_id, ST_GeomFromText(:geometria) :: GEOMETRY, :suolarajoitus);

-- name: paivita-pohjavesialue!
UPDATE pohjavesialue
SET
  nimi          = :nimi,
  tunnus        = :tunnus,
  alue          = ST_GeomFromText(:geometria) :: GEOMETRY,
  suolarajoitus = :suolarajoitus
WHERE ulkoinen_id = :ulkoinen_id;

-- name: onko-olemassa-ulkoisella-idlla
SELECT exists(SELECT 'olemassa'
              FROM pohjavesialue
              WHERE ulkoinen_id = :ulkoinen_id);

-- name: paivita-pohjavesialueet
SELECT paivita_pohjavesialueet();

