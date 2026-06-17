-- Arvonvähennys-lomakkeen tarvitsemat lisäsarakkeet sanktio-tauluun

ALTER TABLE sanktio
    ADD COLUMN maaraystapa TEXT,
    ADD COLUMN vaikuttaa_tavoitehintaan BOOLEAN,
    ADD COLUMN tavoitehinnanalennus NUMERIC,
    ADD COLUMN tehtavaryhma INTEGER REFERENCES tehtavaryhma (id),
    ADD COLUMN tehtava INTEGER REFERENCES tehtava (id);

COMMENT ON COLUMN sanktio.maaraystapa IS 'Arvonvähennyksen määräystapa: tyomaakokous tai valikatselmus';
COMMENT ON COLUMN sanktio.vaikuttaa_tavoitehintaan IS 'Vaikuttaako arvonvähennys tavoitehintaan';
COMMENT ON COLUMN sanktio.tavoitehinnanalennus IS 'Tavoitehinnan alennus euroissa';
COMMENT ON COLUMN sanktio.tehtavaryhma IS 'Arvonvähennyksen tehtäväryhmä';
COMMENT ON COLUMN sanktio.tehtava IS 'Arvonvähennyksen tehtävä (toimenpidekoodi taso 4)';

-- Mahdollistetaan järjestelmäasetuksien kautta validoinnin poisto arvonvähennyslomakkeelle.
ALTER TABLE jarjestelman_asetukset
    ADD COLUMN arvonvahennys_validoinnit_kaytossa BOOLEAN   DEFAULT TRUE; -- Tämä asetetaan hallinnasta

INSERT INTO jarjestelman_asetukset (arvonvahennys_validoinnit_kaytossa)
VALUES (TRUE);
