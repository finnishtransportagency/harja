CREATE TYPE paatoksen_tyyppi AS ENUM (
    'tavoitehinnan-ylitys',
    'kattohinnan-ylitys',
    'tavoitehinnan-alitus'
    );

CREATE TABLE urakka_paatos
(
    id                      SERIAL PRIMARY KEY,
    "hoitokauden-alkuvuosi" INT NOT NULL,
    "urakka-id"             INTEGER NOT NULL REFERENCES urakka (id),
    -- Tavoite- tai kattohinnan ylityksen tai alituksen määrä. Alitus negatiivisenä
    "hinnan-erotus"         NUMERIC,
    -- Paljonko maksetaan rahana, urakoitsijan ja tilaajan osuudet erikseen.
    "urakoitsijan-maksu"    NUMERIC,
    "tilaajan-maksu"        NUMERIC,
    -- Paljonko siirretään ensi hoitokaudelle. Miinusmerkkinen, jos vähennetään.
    siirto                  NUMERIC,
    tyyppi                  paatoksen_tyyppi,
    muokattu                TIMESTAMP,
    "muokkaaja-id"          INTEGER REFERENCES kayttaja (id),
    "luoja-id"              INTEGER REFERENCES kayttaja (id) NOT NULL,
    luotu                   TIMESTAMP DEFAULT NOW(),
    poistettu               BOOLEAN  DEFAULT false
);

COMMENT ON TABLE urakka_paatos IS
    E'Kuvaa vuoden päättämisessä tehtäviä päätöksiä tavoite- ja kattohintaan liittyen.

    Kattohinnan ylittyessä urakoitsija voi maksaa yli menevästä osasta tilaajalle, tai summa voidaan
    siirtää seuraavalle hoitovuodelle kustannuksiksi kokonaan tai osittain.

    Tavoitehinnan ylittyessä urakoitsija maksaa tilaajalle.

    Lähtökohtaisesti urakoitsija maksaa summasta 30% tilaajalle tavoitehinnan ylityksissä ja 100% kattohinnan ylityksessä.

    Mikäli tavoitehinta alittuu, tilaaja maksaa puolestaan urakoitsijalle 30%, kuitenkin maksimissaan 3% urakan tavoitehinnasta.
    Tavoitehinnan alittuessa voidaan myös siirtää seuraavan vuoden alennukseksi, tässä myös mahdollisuus tehdä osittain siirto ja maksu.';

ALTER TABLE tavoitehinnan_oikaisu RENAME COLUMN hoitokausi TO "hoitokauden-alkuvuosi";
ALTER TABLE tavoitehinnan_oikaisu ALTER COLUMN poistettu SET DEFAULT false;
