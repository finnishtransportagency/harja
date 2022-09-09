-- Rajoitusalue
CREATE TABLE rajoitusalue (
                              id                 SERIAL PRIMARY KEY NOT NULL,
                              tierekisteriosoite TR_OSOITE,
-- TODO: Tallennetaanko pituudet suoraan tähän tauluun, vai tehdäänkö esim. VIEW jossa joinataan mukaan
--       tavaraa tauluista tr_osan_ajorata tr_ajoratojen_pituudet tr_osien_pituudet ja lasketaan pituudet näkymässä.
-- Lasketaan tierekisteriosoitteelle
                              pituus             INTEGER,
-- Lasketaan tierekisteriosoitteelle
                              ajoratojen_pituus  INTEGER,
-- lasketaan sijainti valmiiksi, niin nopeutetaan tietokantahakuja koordinaattien perusteella
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

-- TODO: Tarkastetaan leikkaako tierekisteriosoite rajoitusalue-taulussa (Hahmotelma. Ei vielä testattu toimiiko.)
CREATE OR REPLACE FUNCTION rajoitusalue_tr_osoite_ei_leikkaa(id_tunniste INTEGER, osoite TR_OSOITE)
    RETURNS BOOLEAN AS
$$
BEGIN

    RAISE NOTICE 'saatiin id : % ja tr-osoite: % ', id_tunniste, osoite;
    -- Leikkaus ohitettu nyt, kun se ei oikein voi toimia id vertailun kanssa, kun sitä id:tä ei vielä ole, kun insert on kesken.
    -- Se ei tapahdu siinä transaktiossa samassa järjestyksessä, kuin miten se on tässä ajateltu.
    RETURN FALSE;
    -- RETURN (SELECT NOT EXISTS(
    --        SELECT 1
    --          FROM rajoitusalue
    --         WHERE rajoitusalue.id != id_tunniste
    -- FIXME: Varustepuolella on tällainen apufunktio. Tästä voisi tehdä yleisen funktion eri nimellä.
    --        Ilmeisesti on tapana syöttää tr-osoitteita niin, että edellisen osoitteen loppu on seuraavan alku,
    --        eli esim. 1. 25 2/200 - 3/2837 2. 25 3/2837 - 5/1153.
    --        varuste_leikkaus funktio tässä tapauksessa tekee päätelmän, että kyseiset osotteet leikkaavat, mikä
    --        ei toimi rajoitusalueiden käyttötarkoitukseen.
    --        Meidän käyttötarkoituksiamme varten täytynee tehdä eri variantti varuste_leikkaus funktiosta.
    --          AND varuste_leikkaus(
    --                osoite.tie, osoite.aosa, osoite.aet, osoite.losa, osoite.let,
    --                (rajoitusalue.tierekisteriosoite).tie, (rajoitusalue.tierekisteriosoite).aosa,
    --                (rajoitusalue.tierekisteriosoite).aet, (rajoitusalue.tierekisteriosoite).losa,
    --                (rajoitusalue.tierekisteriosoite).let)));
END;
$$ LANGUAGE plpgsql;

-- TODO: Estetään rajoitusalueen tallentaminen, mikäli tierekisteriosoite leikkaa aiemman rajoitusalueen osoitteen kanssa.
--       HOX: Hahmotelma. Toimintaa ei vielä varmistettu!
--ALTER TABLE rajoitusalue
--    ADD CONSTRAINT tierekisteriosoite_ei_leikkaa CHECK (rajoitusalue_tr_osoite_ei_leikkaa(id, tierekisteriosoite));

CREATE INDEX rajoitusalue_tr_osoite_geom_idx ON rajoitusalue USING gist (((tierekisteriosoite).geometria));


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
   poistettu          BOOLEAN default false
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
