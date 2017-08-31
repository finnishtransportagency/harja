<<<<<<< HEAD
-- Toimenpiteen hinnoittelu V3 vaatimat muutokset.

-- Lisää ryhmä. Tämä on tarkoitettu pääasiassa frontille, jotta hinnat voidaan näyttää oikeiden otsikoiden alla
-- Frontin tulee jatkossa vv_hintaa lähetettäessään kertoa ryhmä
CREATE TYPE vv_hinta_ryhma AS ENUM ('tyo', 'komponentti', 'muu');
ALTER TABLE vv_hinta ADD COLUMN ryhma vv_hinta_ryhma; -- Voi olla null esim. jos kyseessä Ryhmähinta.

-- VV-hinta tauluun voidaan syöttää jatkossa JOKO euromääräinen hinta TAI yksikkö, yksikköhinta ja määrä
ALTER TABLE vv_hinta RENAME COLUMN maara TO summa;
ALTER TABLE vv_hinta ALTER COLUMN summa DROP NOT NULL; -- Ks. constraint alta
ALTER TABLE vv_hinta ADD COLUMN maara NUMERIC(6, 2);
ALTER TABLE vv_hinta ADD COLUMN yksikko VARCHAR(64);
ALTER TABLE vv_hinta ADD COLUMN yksikkohinta NUMERIC(6, 2);

ALTER TABLE vv_hinta
  ADD CONSTRAINT validi_hinta CHECK (
  -- Annetaan joko yksittäinen rahasumma TAI yksikkö, yksikköhinta ja määrä
  (summa IS NOT NULL OR (maara IS NOT NULL
                         AND yksikko IS NOT NULL
                         AND yksikkohinta IS NOT NULL))
  AND
  -- Joka tapauksessa on pakko antaa summa tai määrä, ei molempia
  ((summa IS NOT NULL AND maara IS NULL) OR (maara IS NOT NULL AND summa IS NULL)));

-- Otsikkoa ei tarvi jos kyseessä komponentin hinta joka linkittyy komponenttiin.
ALTER TABLE vv_hinta ALTER COLUMN otsikko DROP NOT NULL;

-- Lisää olemassa olevat hinnat ryhmiin, mutta vain jos kyseessä toimenpiteen oma hinnoittelu.
UPDATE vv_hinta
SET ryhma = 'muu'
WHERE "hinnoittelu-id" IN (SELECT id
                           FROM vv_hinnoittelu
                           WHERE hintaryhma IS NOT TRUE)
      AND otsikko != 'Päivän hinta' AND otsikko != 'Omakustannushinta';

-- Päivän hinta ja Omakustannushinta oli aiemmit työt-ryhmän alla. Jos näitä on annettu,
-- migratoidaan ne työ-ryhmän alle
UPDATE vv_hinta
SET ryhma = 'tyo'
WHERE "hinnoittelu-id" IN (SELECT id
                           FROM vv_hinnoittelu
                           WHERE hintaryhma IS NOT TRUE)
      AND otsikko = 'Päivän hinta' OR otsikko = 'Omakustannushinta';

-- TODO Tietomallimuutos: vv_hinta linkkaus reimari-toimenpiteessä tehtyyn komponentin toimenpiteeseen
=======
-- Vesiväylien indeksit
INSERT INTO urakkatyypin_indeksi(urakkatyyppi, indeksinimi, koodi, raakaaine)
VALUES
  ('vesivayla-hoito'::urakkatyyppi, 'MAKU 2005 kunnossapidon osaindeksi', NULL, NULL),
  ('vesivayla-hoito'::urakkatyyppi, 'MAKU 2010 ylläpidon kokonaisindeksi', NULL, NULL);

UPDATE urakka
   SET indeksi = CASE
              WHEN alkupvm < '2017-8-1' THEN 'MAKU 2005 kunnossapidon osaindeksi'
              ELSE 'MAKU 2010 ylläpidon kokonaisindeksi' END
 WHERE tyyppi = 'vesivayla-hoito' and indeksi IS NULL;


-- indeksilaskennan sprocista tehdään useaa urakkatyyppiä tukeva, uudelleennimetään funktio
-- tiedostossa R__Indeksilaskenta.sql ja pudotetaan vanha tässä migraatiossa
DROP FUNCTION hoitourakan_indeksilaskennan_perusluku(urakka_id INTEGER, indeksinimi VARCHAR);
DROP FUNCTION hoitourakan_indeksilaskennan_perusluku(urakka_id INTEGER);
>>>>>>> develop
