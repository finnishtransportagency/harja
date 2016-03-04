-- name: hae-pohjavesialueet
-- Hakee pohjavesialueet annetulle hallintayksikölle
SELECT
  id,
  nimi,
  alue,
  tunnus
FROM pohjavesialueet_hallintayksikoittain
WHERE hallintayksikko = :hallintayksikko AND suolarajoitus IS TRUE;

-- name: hae-urakan-pohjavesialueet
-- Hakee hoidon alueurakan alueella olevat pohjavesialueet
SELECT
  p.nimi,
  p.tunnus,
  p.alue
FROM pohjavesialueet_urakoittain p
WHERE p.urakka = :urakka AND suolarajoitus IS TRUE;

-- name: poista-pohjavesialueet!
-- Poistaa kaikki pohjavesialueet
DELETE FROM pohjavesialue;

-- name: luo-pohjavesialue!
INSERT INTO pohjavesialue (nimi, tunnus, alue, suolarajoitus) VALUES
  (:nimi, :tunnus, ST_GeomFromText(:geometria) :: GEOMETRY, :suolarajoitus);

-- name: paivita-pohjavesialueet
SELECT paivita_pohjavesialueet();

