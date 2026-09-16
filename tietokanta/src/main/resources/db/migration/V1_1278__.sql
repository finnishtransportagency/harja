-- Muutoksia reikäpaikkauksiin liittyvissä tietovaatimuksissa
-- Yksittäiselle paikkaukselle ei koskaan vaadita kustannustietoa tai materiaalimäärää
-- Yksikkö on pakollinen vain silloin, kun reikäpaikkauksen materiaalimäärä on yli nolla
-- Yksikön saa kirjata, vaikka määrää ei ole annettu
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

-- Mahdollistetaan paikkauksen tienkohdan tietojen merkitseminen manuaalisesti määritetyksi, jotta erotetaaan
-- järjestelmien päättelemät sijainnit työkoneen kuljettajan ilmoittamasta.
-- Reikäpaikkauksissa tienkohta on yleensä kuljettajan ilmoittama, urapaikkaukisssa tallentuu false.
-- Reikäpaikkauksissa sallitaan vain yksi tienkohta. Urapaikkauksissa voi olla useita.
-- Älä tallenna samalle paikkaus-id:lle sekä reikä- että urakapaikkauksia. Käyttöliittymät eivät käsittele hybridejä.
ALTER TABLE paikkauksen_tienkohta
    ADD COLUMN paikkaustyyppi  PAIKKAUSTYYPPI DEFAULT 'paikkaus'::paikkaustyyppi,
    ADD COLUMN "kasin-maaritelty" BOOLEAN DEFAULT FALSE;

CREATE UNIQUE INDEX uniikki_paikkaus_id_kun_reikapaikkaus
    ON paikkauksen_tienkohta ("paikkaus-id")
    WHERE paikkauksen_tienkohta.paikkaustyyppi = 'reikapaikkaus';

COMMENT ON COLUMN paikkauksen_tienkohta."kasin-maaritelty" IS 'FALSE kun paikkauksen tienkohta on järjestelmän automaattisesti päättelemä. TRUE kun tienkohdan tiedot on esimerkiksi työkoneen kuljettajan manuaalisesti kirjaamia.';

