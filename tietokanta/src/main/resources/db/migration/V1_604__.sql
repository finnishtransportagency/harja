-- Lisää vv_hinnoittelu taululle ryhmittelytieto.

-- Tämä on tarkoitettu pääasiassa frontille, jotta hinnat voidaan näyttää oikeiden otsikoiden alla
-- Frontin tulee jatkossa vv_hintaa lähetettäessään kertoa ryhmä
CREATE TYPE vv_hinta_ryhma AS ENUM ('tyo', 'komponentti', 'muu');
ALTER TABLE vv_hinta ADD COLUMN ryhma vv_hinta_ryhma;

-- Lisää olemassa olevat hinnat ryhmiin, mutta vain jos kyseessä toimenpiteen oma hinnoittelu.
UPDATE vv_hinta SET ryhma = 'muu'
WHERE "hinnoittelu-id" IN (SELECT id FROM vv_hinnoittelu WHERE hintaryhma IS NOT TRUE)
AND otsikko != 'Päivän hinta' AND otsikko != 'Omakustannushinta';

-- Päivän hinta ja Omakustannushinta oli aiemmit työt-ryhmän alla. Jos näitä on annettu,
-- migratoidaan ne työ-ryhmän alle
UPDATE vv_hinta SET ryhma = 'tyo'
WHERE "hinnoittelu-id" IN (SELECT id FROM vv_hinnoittelu WHERE hintaryhma IS NOT TRUE)
AND otsikko = 'Päivän hinta' OR otsikko = 'Omakustannushinta';

SELECT * FROM reimari_toimenpide;

SELECT * FROM reimari_turvalaitekomponentti;
SELECT * FROM reimari_komponenttityyppi;

SELECT * FROM vv_hinta;
SELECT * FROM vv_tyo;

-- TODO Tietomallimuutos: vv_hinta linkkaus reimari-toimenpiteessä tehtyyn komponentin toimenpiteeseen