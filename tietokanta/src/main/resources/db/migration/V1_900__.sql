ALTER TABLE kustannusarvioitu_tyo
    DROP CONSTRAINT "kustannusarvioitu_tyo_toimenpideinstanssi_tehtava_sopimus_v_key";

ALTER TABLE johto_ja_hallintokorvaus
    DROP CONSTRAINT "uniikki_johto_ja_hallintokorvaus";

ALTER TABLE kiinteahintainen_tyo
    DROP CONSTRAINT "kiinteahintainen_tyo_toimenpideinstanssi_tehtavaryhma_tehta_key";

ALTER TABLE urakka_tavoite
    DROP CONSTRAINT "urakka_tavoite_urakka_hoitokausi_key";


CREATE TYPE SUUNNITTELU_OSIO AS ENUM ('hankintakustannukset', 'erillishankinnat', 'johto-ja-hallintokorvaus',
    'hoidonjohtopalkkio', 'tavoite-ja-kattohinta', 'tilaajan-rahavaraukset');

-- Indeksikorjattu hinta tallennetaan riville jonka versio on 0. Indeksikorjattu summa lasketaan summa_indeksikorjattu-sarakkeeseen.
-- Versio on juokseva numero, joka lähtee nollasta.
-- Jos kustannussuunnitelmaa muokataan jälkeenpäin, nostetaan versionumeroa tietoja tallennettaessa.
-- Näin saadaan sitten muodostettua historiatiedot. Versioille nollasta ylöspäin ei tallenneta indeksikorjattua summaa.
ALTER TABLE kustannusarvioitu_tyo
    ADD COLUMN summa_indeksikorjattu      NUMERIC,
    ADD COLUMN indeksikorjaus_vahvistettu TIMESTAMP,
    ADD COLUMN vahvistaja                 INTEGER REFERENCES kayttaja (id),
    ADD COLUMN versio                     INTEGER NOT NULL DEFAULT 0,
    -- Mistä kustannussuunnitelman osiosta rivi on peräisin.
    ADD COLUMN osio                       SUUNNITTELU_OSIO,
    ADD CONSTRAINT uniikki_kustannusarvioitu_tyo
        UNIQUE (toimenpideinstanssi, tehtava, sopimus, vuosi, kuukausi, versio);

-- Johto_ja_hallintokorvaukset suhteen ei tarvitse seurata mistä osiosta data tulee, koska se tulee varmuudella vain
--   suunnitelman johto-ja hallintokorvaus osiosta. Näin ollen osio-saraketta ei lisätä tähän tauluun.
ALTER TABLE johto_ja_hallintokorvaus
    ADD COLUMN tuntipalkka_indeksikorjattu NUMERIC,
    ADD COLUMN indeksikorjaus_vahvistettu  TIMESTAMP,
    ADD COLUMN vahvistaja                  INTEGER REFERENCES kayttaja (id),
    ADD COLUMN versio                      INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT uniikki_johto_ja_hallintokorvaus
        EXCLUDE ("urakka-id" WITH =, "toimenkuva-id" WITH =, vuosi WITH =, kuukausi WITH =,
        versio WITH =, ei_ennen_urakka("ennen-urakkaa", id) WITH =);

-- kiinteahintainen_tyo taulussa ei tarvitse seurata mistä osiosta data tulee, koska sitä tulee tällä hetkellä vain
-- "Hankintakustannukset"-osiosta. Näin olle osio-saraketta ei tarvitse lisätä tähän tauluun.
ALTER TABLE kiinteahintainen_tyo
    ADD COLUMN summa_indeksikorjattu      NUMERIC,
    ADD COLUMN indeksikorjaus_vahvistettu TIMESTAMP,
    ADD COLUMN vahvistaja                 INTEGER REFERENCES kayttaja (id),
    ADD COLUMN versio                     INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT uniikki_kiinteahintainen_tyo
        UNIQUE (toimenpideinstanssi, tehtavaryhma, tehtava, sopimus, vuosi, kuukausi, versio);

ALTER TABLE urakka_tavoite
    ADD COLUMN tavoitehinta_indeksikorjattu           NUMERIC,
    ADD COLUMN tavoitehinta_siirretty_indeksikorjattu NUMERIC,
    ADD COLUMN kattohinta_indeksikorjattu             NUMERIC,
    ADD COLUMN indeksikorjaus_vahvistettu             TIMESTAMP,
    ADD COLUMN vahvistaja                             INTEGER REFERENCES kayttaja (id),
    ADD COLUMN versio                                 INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT uniikki_urakka_tavoite_key
        UNIQUE (urakka, hoitokausi, versio);

CREATE TABLE suunnittelu_kustannussuunnitelman_tila (
    id            SERIAL PRIMARY KEY,
    urakka        INTEGER REFERENCES urakka (id)   NOT NULL,
    osio          SUUNNITTELU_OSIO,
    hoitovuosi    SMALLINT                         NOT NULL,
    vahvistettu   BOOLEAN                          NOT NULL DEFAULT FALSE,
    luoja         INTEGER REFERENCES kayttaja (id) NOT NULL,
    luotu         TIMESTAMP                                 DEFAULT NOW(),
    muokattu      TIMESTAMP,
    muokkaaja     INTEGER REFERENCES kayttaja (id),
    vahvistaja    INTEGER REFERENCES kayttaja (id),
    vahvistus_pvm TIMESTAMP,
    CONSTRAINT urakka_osio_hoitovuosi
        UNIQUE (urakka, osio, hoitovuosi)
);

-- Jos vahvistamisen jälkeen suunnitelmaa muokataan, tallennetaan selite sille.
-- Muokkaus liittyy aina johonkin yksittäiseen riviin, joka voi olla jhk/kiint.tyo/kust.tyo
CREATE TABLE suunnittelu_kustannussuunnitelman_muutos (
    id                       SERIAL PRIMARY KEY,
    johto_ja_hallintokorvaus INT REFERENCES johto_ja_hallintokorvaus (id),
    kiinteahintainen_tyo     INT REFERENCES kiinteahintainen_tyo (id),
    kustannusarvioitu_tyo    INT REFERENCES kustannusarvioitu_tyo (id),
    kuvaus                   TEXT                             NOT NULL,
    selite                   TEXT                             NOT NULL,
    muutos                   NUMERIC                          NOT NULL,
    vuosi                    SMALLINT                         NOT NULL,
    luotu                    TIMESTAMP DEFAULT NOW(),
    luoja                    INTEGER REFERENCES kayttaja (id) NOT NULL,
    urakka                   INTEGER REFERENCES urakka (id)   NOT NULL
);