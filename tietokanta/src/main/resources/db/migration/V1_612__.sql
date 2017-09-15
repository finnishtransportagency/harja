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
ALTER TABLE vv_hinta ADD COLUMN yksikkohinta NUMERIC(8, 2);

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

ALTER TABLE vv_hinta ADD COLUMN "komponentti-id" TEXT REFERENCES reimari_turvalaitekomponentti (id);
ALTER TABLE vv_hinta ADD COLUMN "komponentti-tilamuutos" TEXT;
ALTER TABLE vv_hinta
  ADD CONSTRAINT komponentin_hinnalla_id_ja_tila CHECK (
  (ryhma = 'komponentti' AND "komponentti-id" IS NOT NULL AND "komponentti-tilamuutos" IS NOT NULL)
  OR
  (ryhma != 'komponentti' AND "komponentti-id" IS NULL AND "komponentti-tilamuutos" IS NULL)
);

-- Samassa hinnoittelussa voi yhdelle komponentille, ja sen tilamuutokselle, olla vain yksi hinta.
CREATE UNIQUE INDEX uniikki_hinta_komponentille on vv_hinta ("hinnoittelu-id", "komponentti-tilamuutos", "komponentti-id")
  WHERE poistettu IS NOT TRUE;

-- Toimenpidetaulun reimari-komponentit tyypissä komponentti-id on virheellisesti
-- INTEGER, vaikka tietomallissa se on TEXT. Tauluun sen muuttaminen olisi hyvin työlästä,
-- joten muutetaan vain suoraan tähän viewiin.
DROP VIEW reimari_toimenpiteen_komponenttien_tilamuutokset;

CREATE OR REPLACE VIEW reimari_toimenpiteen_komponenttien_tilamuutokset AS
  SELECT id AS "toimenpide-id",
         "reimari-muokattu" AS muokattu,
         "reimari-luotu" AS luotu,
         (unnest("reimari-komponentit")).tila AS tilakoodi,
         (unnest("reimari-komponentit")).id::TEXT AS "komponentti-id"
  FROM reimari_toimenpide;