-- Arvonvähennys-lomakkeen tarvitsemat lisäsarakkeet sanktio-tauluun

ALTER TABLE sanktio
    ADD COLUMN maaraystapa TEXT,
    ADD COLUMN IF NOT EXISTS "kasittelytapa" laatupoikkeaman_kasittelytapa,
    ADD COLUMN tehtavaryhma INTEGER REFERENCES tehtavaryhma (id),
    ADD COLUMN tehtava INTEGER REFERENCES tehtava (id);

COMMENT ON COLUMN sanktio.maaraystapa IS 'Arvonvähennyksen määräystapa: tyomaakokous tai valikatselmus';
COMMENT ON COLUMN sanktio.maaraystapa IS 'Arvonvähennyksen ja sanktion käsittelytapa. Yleensä tyomaakokous tai valikatselmus';
COMMENT ON COLUMN sanktio.tehtavaryhma IS 'Arvonvähennyksen tehtäväryhmä';
COMMENT ON COLUMN sanktio.tehtava IS 'Arvonvähennyksen tehtävä (toimenpidekoodi taso 4)';

-- Tallennetaan kaikille suorasanktioille käsittelytavaksi se, mikä on valittu laatupoikkeamalle kasittelytapa-kenttään
UPDATE sanktio s
SET kasittelytapa = lp.kasittelytapa
FROM laatupoikkeama lp
WHERE lp.id = s.laatupoikkeama
  AND s.kasittelytapa IS NULL
  AND s.suorasanktio IS TRUE;

-- Mahdollistetaan järjestelmäasetuksien kautta validoinnin poisto arvonvähennyslomakkeelle.
ALTER TABLE jarjestelman_asetukset
    ADD COLUMN arvonvahennys_validoinnit_kaytossa BOOLEAN   DEFAULT TRUE; -- Tämä asetetaan hallinnasta

INSERT INTO jarjestelman_asetukset (arvonvahennys_validoinnit_kaytossa)
VALUES (TRUE);
