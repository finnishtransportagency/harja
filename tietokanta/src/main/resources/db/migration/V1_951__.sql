-- Rajoitusalue
CREATE TABLE rajoitusalue (
                              id                 SERIAL PRIMARY KEY NOT NULL,
                              tierekisteriosoite TR_OSOITE,
-- Lasketaan tierekisteriosoitteelle pituus ja ajoratojen pituus
                              pituus             INTEGER,
                              ajoratojen_pituus  INTEGER,
-- Lasketaan sijainti valmiiksi, niin nopeutetaan tietokantahakuja koordinaattien perusteella
                              sijainti geometry,

-- Urakan id, johon rajoitusalue liittyy (vrt. pohjavesialueet_urakoittain ja pohjavesialueet_hallintayksikoittain)
-- NOTE: pohjavesialueet_hallintayksikoittain viewiä on käytetty vain kartalle hakuun
--       Hallintayksikön saa tarvittaessa haettua urakan id:n perusteella.
                              urakka_id          INTEGER,

                              luotu              TIMESTAMP,
                              luoja              INTEGER REFERENCES kayttaja (id),
                              muokattu           TIMESTAMP,
                              muokkaaja          INTEGER REFERENCES kayttaja (id),
                              poistettu          BOOLEAN default false
);

-- Rajoitusalueen rajoitus yksittäiselle hoitovuodelle
CREATE TABLE rajoitusalue_rajoitus (
   id                    SERIAL PRIMARY KEY NOT NULL,
   rajoitusalue_id       INTEGER REFERENCES rajoitusalue (id),
   suolarajoitus         NUMERIC,
   formiaatti            BOOLEAN,
   hoitokauden_alkuvuosi SMALLINT,

   luotu                 TIMESTAMP DEFAULT NOW(),
   luoja                 INTEGER REFERENCES kayttaja (id),
   muokattu              TIMESTAMP,
   muokkaaja             INTEGER REFERENCES kayttaja (id),
   poistettu             BOOLEAN default false
);

-- Leikkaavat pohjavesialueet proseduuri tarvitsee tyypin, jonka se voi palauttaa
CREATE TYPE POHJAVESIALUE_RIVI
AS
(
    nimi          VARCHAR,
    tunnus        VARCHAR
);

CREATE OR REPLACE FUNCTION leikkaavat_pohjavesialueet(tie INTEGER, aosa INTEGER, aet INTEGER,
                                                      losa INTEGER, let INTEGER) RETURNS SETOF POHJAVESIALUE_RIVI AS
$$

DECLARE
    p    RECORD;
    rivi POHJAVESIALUE_RIVI;
BEGIN
    FOR p IN SELECT distinct on (pa.nimi) nimi, pa.tunnus, pa.alue
             FROM pohjavesialue pa
             WHERE ST_INTERSECTS(pa.alue,
                                 (SELECT * FROM
                                     tierekisteriosoitteelle_viiva(tie, aosa, aet, losa, let)))
        LOOP
            rivi := (p.nimi, p.tunnus);
            RETURN NEXT rivi;
        END LOOP;
END;
$$ LANGUAGE plpgsql;

-- Lisätään suolasakolle tyyppi, jotta voidaan yksilöidä sakko, joko talvisuolan kokonaismäärälle tai yksittäiselle pohjavesialueelle
CREATE TYPE  suolasakko_tyyppi AS ENUM ('kokonaismaara', 'rajoitusalue');

ALTER TABLE suolasakko ADD COLUMN tyyppi suolasakko_tyyppi DEFAULT 'kokonaismaara';

-- Päivitä vielä uniikki-constraint
ALTER TABLE suolasakko DROP CONSTRAINT uniikki_suolasakko;
ALTER TABLE suolasakko
    ADD CONSTRAINT uniikki_suolasakko
        UNIQUE (urakka, hoitokauden_alkuvuosi, tyyppi);

