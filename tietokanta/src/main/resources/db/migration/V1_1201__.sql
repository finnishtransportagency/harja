-- Lisää kustannusennuste tyyppi
ALTER TYPE lupaustyyppi ADD VALUE 'kustannusennuste';

-- Lisää taulu pisterajojen tallentamiselle urakan-alkuvuosi kohtaisena
CREATE TABLE lupaus_kustannusennuste_kuukausi_pisteet (
    id SERIAL PRIMARY KEY,
    "urakan-alkuvuosi" INTEGER NOT NULL,
    kuukausi INTEGER NOT NULL CHECK (kuukausi BETWEEN 1 AND 12),
    paiva INTEGER NOT NULL,
    kuvaus VARCHAR(100) NOT NULL,
    pisterajat JSONB NOT NULL,
    luotu TIMESTAMP DEFAULT NOW(),
    luoja INTEGER REFERENCES kayttaja(id),
    UNIQUE ("urakan-alkuvuosi", kuukausi, paiva)
);

-- Indeksit nopeuttamaan hakuja
CREATE INDEX idx_kustannusennuste_kuukausi_pisteet_alkuvuosi 
ON lupaus_kustannusennuste_kuukausi_pisteet ("urakan-alkuvuosi");

CREATE INDEX idx_kustannusennuste_kuukausi_pisteet_kuukausi_paiva 
ON lupaus_kustannusennuste_kuukausi_pisteet (kuukausi, paiva);

CREATE INDEX idx_kustannusennuste_kuukausi_pisteet_jsonb 
ON lupaus_kustannusennuste_kuukausi_pisteet USING gin (pisterajat);

-- Lisää lupaus_kustannusennuste taulu
CREATE TABLE lupaus_kustannusennuste (
    id SERIAL PRIMARY KEY,
    "lupaus-id" INTEGER NOT NULL REFERENCES lupaus (id),
    "urakka-id" INTEGER NOT NULL REFERENCES urakka (id),
    hoitovuosi_alkuvuosi INTEGER NOT NULL,
    maarapaiva DATE NOT NULL,
    ennustettu_tavoitehinta DECIMAL(15,2),
    ennustetut_kustannukset DECIMAL(15,2),
    syotetty_pvm TIMESTAMP,
    lasketut_pisteet INTEGER, -- Tämän ennusteen saamat pisteet
    tarkkuus_prosentti DECIMAL(5,2),
    laskentakaava_versio VARCHAR(10),
    laskentakaava_teksti TEXT,
    laskentakaava_parametrit JSONB,
    laskentakaava_vaiheet JSONB,
    luoja INTEGER NOT NULL REFERENCES kayttaja (id),
    luotu TIMESTAMP NOT NULL DEFAULT NOW(),
    muokkaaja INTEGER REFERENCES kayttaja (id),
    muokattu TIMESTAMP,
    CONSTRAINT lupaus_kustannusennuste_unique UNIQUE ("lupaus-id", "urakka-id", maarapaiva)
);

COMMENT ON COLUMN lupaus_kustannusennuste.laskentakaava_versio 
IS 'Käytetyn laskentakaavan versionumero, esim. v1.0';

COMMENT ON COLUMN lupaus_kustannusennuste.laskentakaava_teksti 
IS 'Laskentakaava ihmisluettavassa muodossa';

COMMENT ON COLUMN lupaus_kustannusennuste.laskentakaava_parametrit 
IS 'Laskennassa käytetyt syöttöarvot JSON-muodossa';

COMMENT ON COLUMN lupaus_kustannusennuste.laskentakaava_vaiheet 
IS 'Laskentavaiheet ja väliarvot JSON-muodossa auditointia varten';

CREATE TABLE lupaus_hoitovuosi_lopputilanne (
    id SERIAL PRIMARY KEY,
    "urakka-id" INTEGER NOT NULL REFERENCES urakka (id),
    hoitovuosi_alkuvuosi INTEGER NOT NULL,
    lopullinen_tavoitehinta DECIMAL(15,2),
    lopulliset_kustannukset DECIMAL(15,2),
    valikatselmus_pvm DATE,
    vahvistaja INTEGER REFERENCES kayttaja (id),
    vahvistettu TIMESTAMP,
    CONSTRAINT hoitovuosi_lopputilanne_unique UNIQUE ("urakka-id", hoitovuosi_alkuvuosi)
);

-- Mahdollistaa hoitovuosikohtaiset erikoisarvot lupauksen kirjauskuukausille
-- sekä (vaihtoehtoisesti) päätöskuukaudelle ja joustovaran kuukausille.
--
-- Varasuunnitelmalogiikka: jos tälle (lupaus_id, hoitovuosi_nro) ei löydy riviä,
-- käytetään lupaus-taulun alkuperäisiä sarakkeita (kirjaus-kkt, paatos-kk, joustovara-kkta).

CREATE TABLE lupaus_hoitovuoden_kirjauskuukaudet (
    id BIGSERIAL PRIMARY KEY,
    "lupaus-id" BIGINT NOT NULL REFERENCES lupaus(id) ON DELETE CASCADE,
    "hoitovuosi-nro" INTEGER NOT NULL CHECK ("hoitovuosi-nro" >= 1 AND "hoitovuosi-nro" <= 15),
    "kirjaus-kkt" INTEGER[] NOT NULL,
    "paatos-kk" INTEGER CHECK ("paatos-kk" BETWEEN 0 AND 12),
    "joustovara-kkta" INTEGER CHECK ("joustovara-kkta" BETWEEN 0 AND 12),
    luotu TIMESTAMP WITHOUT TIME ZONE DEFAULT now(),
    luoja INTEGER REFERENCES kayttaja(id),
    CONSTRAINT lupaus_hoitovuoden_kirjauskuukaudet_unique UNIQUE ("lupaus-id", "hoitovuosi-nro")
);

CREATE INDEX idx_lupaus_hoitovuoden_kirjauskuukaudet_lupaus_id ON lupaus_hoitovuoden_kirjauskuukaudet ("lupaus-id");

COMMENT ON TABLE lupaus_hoitovuoden_kirjauskuukaudet IS 'Hoitovuosikohtaiset erikoisarvot lupauksen kirjauskuukausille ja tarvittaessa päätös- sekä joustovaratiedoille.';
COMMENT ON COLUMN lupaus_hoitovuoden_kirjauskuukaudet."hoitovuosi-nro" IS 'Hoitovuoden järjestysnumero (1 = ensimmäinen hoitovuosi urakan alusta).';
COMMENT ON COLUMN lupaus_hoitovuoden_kirjauskuukaudet."kirjaus-kkt" IS 'Hoitovuoden kirjauskuukaudet, korvaa lupaus.kirjaus-kkt';
COMMENT ON COLUMN lupaus_hoitovuoden_kirjauskuukaudet."paatos-kk" IS 'Hoitovuoden päätöskuukausi, korvaa lupaus.paatos-kk jos ei NULL';
COMMENT ON COLUMN lupaus_hoitovuoden_kirjauskuukaudet."joustovara-kkta" IS 'Hoitovuoden joustovara kuukausissa, korvaa lupaus.joustovara-kkta jos ei NULL';
