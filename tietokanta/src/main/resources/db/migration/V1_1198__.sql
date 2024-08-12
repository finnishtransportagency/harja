-- Poista duplikaatti rahavaraukset kannasta 
-- Äkilliset / Vahingot / Kannustin on duplikaattina kannassa, jätetään pelkästään: Rahavaraus B, C ja K


--------------------------------------------
------------ rahavaraus_tehtava ------------
--------------------------------------------
-- Päivitä tehtava taulusta 'Äkilliset hoitotyöt' -> Rahavaraus B
UPDATE rahavaraus_tehtava
   SET rahavaraus_id = (SELECT id FROM rahavaraus WHERE nimi LIKE '%Rahavaraus B%' LIMIT 1)
 WHERE rahavaraus_id IN (SELECT id FROM rahavaraus WHERE nimi = 'Äkilliset hoitotyöt');

-- Päivitä 'Vahinkojen korvaukset' -> Rahavaraus C
UPDATE rahavaraus_tehtava
   SET rahavaraus_id = (SELECT id FROM rahavaraus WHERE nimi LIKE '%Rahavaraus C%' LIMIT 1)
 WHERE rahavaraus_id IN (SELECT id FROM rahavaraus WHERE nimi = 'Vahinkojen korvaukset');

-- 'Kannustinjärjestelmä' -> Rahavaraus K
UPDATE rahavaraus_tehtava
   SET rahavaraus_id = (SELECT id FROM rahavaraus WHERE nimi LIKE '%Rahavaraus K%' LIMIT 1)
 WHERE rahavaraus_id IN (SELECT id FROM rahavaraus WHERE nimi = 'Kannustinjärjestelmä');


-------------------------------------------
------------ rahavaraus_urakka ------------
-------------------------------------------
UPDATE rahavaraus_urakka
   SET rahavaraus_id = (SELECT id FROM rahavaraus WHERE nimi LIKE '%Rahavaraus B%' LIMIT 1)
 WHERE rahavaraus_id IN (SELECT id FROM rahavaraus WHERE nimi = 'Äkilliset hoitotyöt');

UPDATE rahavaraus_urakka
   SET rahavaraus_id = (SELECT id FROM rahavaraus WHERE nimi LIKE '%Rahavaraus C%' LIMIT 1)
 WHERE rahavaraus_id IN (SELECT id FROM rahavaraus WHERE nimi = 'Vahinkojen korvaukset');

UPDATE rahavaraus_urakka
   SET rahavaraus_id = (SELECT id FROM rahavaraus WHERE nimi LIKE '%Rahavaraus K%' LIMIT 1)
 WHERE rahavaraus_id IN (SELECT id FROM rahavaraus WHERE nimi = 'Kannustinjärjestelmä');


-- Nyt duplikaatit voi poistaa
DELETE FROM rahavaraus
      WHERE nimi IN ('Äkilliset hoitotyöt', 'Vahinkojen korvaukset', 'Kannustinjärjestelmä');


CREATE OR REPLACE FUNCTION lisaa_urakan_oletus_rahavaraukset() RETURNS TRIGGER AS
$$
BEGIN
    INSERT INTO rahavaraus_urakka (urakka_id, rahavaraus_id, luoja)
    SELECT NEW.id,
           rv.id,
           (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
      FROM rahavaraus rv
     WHERE rv.nimi IN ('Rahavaraus B - Äkilliset hoitotyöt',
                       'Rahavaraus C - Vahinkojen korjaukset',
                       'Rahavaraus K - Kannustinjärjestelmä');

    RETURN NEW;
END
$$ LANGUAGE plpgsql;
