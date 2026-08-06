-- Mahdollistetaan "Ei tiedossa" käsittelytavan tallennus
ALTER TYPE laatupoikkeaman_kasittelytapa ADD VALUE IF NOT EXISTS 'ei-tiedossa';

-- Lisätään sanktio-tauluun maaraystapa-kenttä tekstimuotoisena
ALTER TABLE sanktio
    ADD COLUMN IF NOT EXISTS "maaraystapa" text,
    ADD COLUMN IF NOT EXISTS "kasittelytapa" laatupoikkeaman_kasittelytapa,
    ADD COLUMN IF NOT EXISTS "laskutusrajan_ylitys" DECIMAL(10, 2);


-- Tallennetaan kaikille suorasanktioille käsittelytavaksi se, mikä on valittu laatupoikkeamalle kasittelytapa-kenttään
UPDATE sanktio s
SET kasittelytapa = lp.kasittelytapa
FROM laatupoikkeama lp
WHERE lp.id = s.laatupoikkeama
  AND s.kasittelytapa IS NULL
  AND s.suorasanktio IS TRUE;
