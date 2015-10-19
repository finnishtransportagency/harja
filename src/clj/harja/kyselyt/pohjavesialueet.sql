-- name: hae-pohjavesialueet
-- Hakee pohjavesialueet annetulle hallintayksikölle
SELECT id, nimi, alue, tunnus
  FROM pohjavesialueet_hallintayksikoittain
 WHERE hallintayksikko = :hallintayksikko

-- name: tuhoa-pohjavesialuedata!
-- Poistaa kaikki pohjavesialueet
DELETE FROM pohjavesialue;

-- name: vie-pohjavesialuetauluun!
INSERT INTO pohjavesialue (nimi, tunnus, alue) VALUES
       (:nimi, :tunnus, ST_GeomFromText(:geometria)::geometry)