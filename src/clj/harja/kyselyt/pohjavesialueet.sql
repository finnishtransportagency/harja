-- name: hae-pohjavesialueet
-- Hakee pohjavesialueet annetulle hallintayksikölle
SELECT id, nimi, alue, tunnus
  FROM pohjavesialueet_hallintayksikoittain
 WHERE hallintayksikko = :hallintayksikko;

-- name: hae-urakan-pohjavesialueet
-- Hakee hoidon alueurakan alueella olevat pohjavesialueet
SELECT id, nimi, alue, tunnus
  FROM pohjavesialueet_hallintayksikoittain p
 WHERE ST_CONTAINS((SELECT au.alue 
                      FROM urakka u
		           JOIN hanke h ON u.hanke = h.id
			   JOIN alueurakka au ON h.alueurakkanro = au.alueurakkanro
		     WHERE u.id = :urakka),
	            p.alue);
		    

-- name: tuhoa-pohjavesialuedata!
-- Poistaa kaikki pohjavesialueet
DELETE FROM pohjavesialue;

-- name: vie-pohjavesialuetauluun!
INSERT INTO pohjavesialue (nimi, tunnus, alue) VALUES
       (:nimi, :tunnus, ST_GeomFromText(:geometria)::geometry);

-- name: paivita-hallintayksikoiden-pohjavesialueet
SELECT paivita_hallintayksikoiden_pohjavesialueet();
