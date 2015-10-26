-- name: hae-pohjavesialueet
-- Hakee pohjavesialueet annetulle hallintayksikölle
SELECT id, nimi, alue, tunnus
  FROM pohjavesialueet_hallintayksikoittain
 WHERE hallintayksikko = :hallintayksikko;

-- name: hae-urakan-pohjavesialueet
-- Hakee hoidon alueurakan alueella olevat pohjavesialueet
SELECT p.nimi, p.tunnus, p.alue, t.hoitokauden_alkuvuosi, t.talvisuolaraja 
  FROM pohjavesialueet_urakoittain p
       JOIN pohjavesialue_talvisuola t ON (p.tunnus = t.pohjavesialue AND p.urakka = t.urakka)
 WHERE p.urakka = :urakka;
		    

-- name: tuhoa-pohjavesialuedata!
-- Poistaa kaikki pohjavesialueet
DELETE FROM pohjavesialue;

-- name: vie-pohjavesialuetauluun!
INSERT INTO pohjavesialue (nimi, tunnus, alue) VALUES
       (:nimi, :tunnus, ST_GeomFromText(:geometria)::geometry);

-- name: paivita-pohjavesialueet
SELECT paivita_pohjavesialueet();

