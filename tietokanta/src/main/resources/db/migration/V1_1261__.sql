ALTER TABLE sanktio_profiili_rivi_lukittu_summa
    RENAME TO sanktio_profiili_rivi_summamaaritys;

ALTER INDEX sanktio_profiili_rivi_lukittu_summa_haku_idx
    RENAME TO sanktio_profiili_rivi_summamaaritys_haku_idx;

ALTER TABLE sanktio_profiili_rivi_summamaaritys
    ADD COLUMN maaritystapa TEXT,
    ADD COLUMN ohjeteksti  TEXT;

UPDATE sanktio_profiili_rivi_summamaaritys
   SET maaritystapa = 'kiintea_euromaara'
 WHERE maaritystapa IS NULL;

ALTER TABLE sanktio_profiili_rivi_summamaaritys
    ALTER COLUMN maaritystapa SET DEFAULT 'kiintea_euromaara',
    ALTER COLUMN maaritystapa SET NOT NULL,
    ALTER COLUMN summa_euroina DROP NOT NULL;

ALTER TABLE sanktio_profiili_rivi_summamaaritys
    ADD CONSTRAINT sanktio_profiili_rivi_summamaaritys_maaritystapa_check
        CHECK (maaritystapa IN ('kiintea_euromaara', 'vapaa_ohjeteksti')),
    ADD CONSTRAINT sanktio_profiili_rivi_summamaaritys_ohjeteksti_check
        CHECK (ohjeteksti IS NULL OR btrim(ohjeteksti) <> ''),
    ADD CONSTRAINT sanktio_profiili_rivi_summamaaritys_sisalto_check
        CHECK (
            (maaritystapa = 'kiintea_euromaara' AND summa_euroina IS NOT NULL)
            OR
            (maaritystapa = 'vapaa_ohjeteksti' AND summa_euroina IS NULL AND ohjeteksti IS NOT NULL)
        );

COMMENT ON TABLE sanktio_profiili_rivi_summamaaritys
                IS 'Sanktio-profiiliriviin sidotut summan maaritykset, joissa voi olla euromaara ja ohjeteksti samalla rivilla.';
