ALTER TABLE sanktio_profiili_rivi_lukittu_summa
    RENAME TO sanktio_profiili_rivi_summamaaritys;

ALTER INDEX sanktio_profiili_rivi_lukittu_summa_haku_idx
    RENAME TO sanktio_profiili_rivi_summamaaritys_haku_idx;

ALTER TABLE sanktio_profiili_rivi_summamaaritys
    ADD COLUMN maaritystapa TEXT,
    ADD COLUMN ohjeteksti  TEXT;

-- automaattinen = jarjestelma tayttaa summan profiilista (voidaan laajentaa laskenta-automaatioksi)
-- manuaalinen   = kayttaja kirjaa summan kasin; ohjeteksti vapaaehtoinen
UPDATE sanktio_profiili_rivi_summamaaritys
   SET maaritystapa = 'automaattinen'
 WHERE maaritystapa IS NULL;

ALTER TABLE sanktio_profiili_rivi_summamaaritys
    ALTER COLUMN maaritystapa SET DEFAULT 'automaattinen',
    ALTER COLUMN maaritystapa SET NOT NULL,
    ALTER COLUMN summa_euroina DROP NOT NULL;

ALTER TABLE sanktio_profiili_rivi_summamaaritys
    ADD CONSTRAINT sanktio_profiili_rivi_summamaaritys_maaritystapa_check
        CHECK (maaritystapa IN ('automaattinen', 'manuaalinen')),
    ADD CONSTRAINT sanktio_profiili_rivi_summamaaritys_ohjeteksti_check
        CHECK (ohjeteksti IS NULL OR btrim(ohjeteksti) <> ''),
    ADD CONSTRAINT sanktio_profiili_rivi_summamaaritys_sisalto_check
        CHECK (
            (maaritystapa = 'automaattinen' AND summa_euroina IS NOT NULL)
            OR
            (maaritystapa = 'manuaalinen' AND (summa_euroina IS NOT NULL OR ohjeteksti IS NOT NULL))
        );
