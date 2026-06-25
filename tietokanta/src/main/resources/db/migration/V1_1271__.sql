-- Muutoksia reikäpaikkauksiin liittyvissä tietovaatimuksissa
-- Yksittäiselle paikkaukselle ei koskaan vaadita kustannustietoa tai materiaalimäärää
-- Yksikkö on pakollinen vain silloin, kun reikäpaikkauksen materiaalimäärä on yli nolla
ALTER TABLE paikkaus
    DROP CONSTRAINT kustannus_sallitaanko_null,
    DROP CONSTRAINT paikkaus_maara_sallitaanko_null,
    DROP CONSTRAINT yksikko_sallitaanko_null,
    ADD CONSTRAINT yksikko_sallitaanko_null
        CHECK ("paikkaus-tyyppi" = 'paikkaus' OR
               (("maara" IS NULL OR "maara" = 0) OR
               ("maara" IS NOT NULL AND "reikapaikkaus-yksikko" IS NOT NULL AND "reikapaikkaus-yksikko" != '')));

-- Lisätään tauluun kenttä, johon voidaan tallentaa reikäpaikkauksiin liittyviä luokitteluja
ALTER TABLE paikkaus
    ADD COLUMN luokittelu TEXT[];

