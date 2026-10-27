ALTER TABLE sanktio
    ADD COLUMN normaalimaara NUMERIC,
    ADD COLUMN omailmoitettu BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN sanktio.normaalimaara IS 'Sanktion profiilista ratkaistu normaalimäärä ennen mahdollista omailmoituspuolitusta.';
COMMENT ON COLUMN sanktio.omailmoitettu IS 'Tapahtumaan tallennettu käyttäjän omailmoitusvalinta.';


ALTER TABLE sanktio
    ADD COLUMN sanktio_profiili_rivi INTEGER REFERENCES sanktio_profiili_rivi (id),
    ADD COLUMN maaritystapa TEXT;

ALTER TABLE sanktio
    ADD CONSTRAINT sanktio_maaritystapa_check
        CHECK (maaritystapa IS NULL OR maaritystapa IN ('automaattinen', 'manuaalinen'));

UPDATE sanktio_profiili_rivi_summamaaritys sprsm
SET maaritystapa = 'manuaalinen',
    muokattu = CURRENT_TIMESTAMP
FROM sanktio_profiili_rivi spr,
     sanktio_profiili sp,
     sanktio_laji sl,
     sanktiotyyppi st
WHERE sprsm.sanktio_profiili_rivi_id = spr.id
  AND spr.sanktio_profiili_id = sp.id
  AND spr.sanktio_laji_id = sl.id
  AND spr.sanktiotyyppi_id = st.id
  AND sp.nimi = 'teiden-hoito-mhu2026'
  AND sl.koodi = 'C'
  AND st.koodi IN (17, 18, 19, 20, 21);
