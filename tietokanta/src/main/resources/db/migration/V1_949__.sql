--

-- Rajoitusalue
CREATE TABLE rajoitusalue (
    id                 SERIAL PRIMARY KEY,
    tierekisteriosoite TR_OSOITE,
    -- TODO: Tallennetaanko pituudet suoraan tähän tauluun, vai tehdäänkö esim. VIEW jossa joinataan mukaan
    --       tavaraa tauluista tr_osan_ajorata tr_ajoratojen_pituudet tr_osien_pituudet ja lasketaan pituudet näkymässä.
    -- Lasketaan tierekisteriosoitteelle
    pituus             INTEGER,
    -- Lasketaan tierekisteriosoitteelle
    ajoratojen_pituus  INTEGER,

    -- Urakan id, johon rajoitusalue liittyy (vrt. pohjavesialueet_urakoittain)
    urakka             INTEGER,

    -- TODO: Tarvitaanko, mikä käyttötarkoitus? (vrt. pohjavesialueet_hallintayksikoittain)
    --hallintayksikko    INTEGER,

    luotu              TIMESTAMP,
    luoja              INTEGER REFERENCES kayttaja (id),
    muokattu           TIMESTAMP,
    muokkaaja          INTEGER REFERENCES kayttaja (id),

    CONSTRAINT uniikki_rajoitusalue UNIQUE (tierekisteriosoite, urakka)
);

-- TODO: Tarkastetaan leikkaako tierekisteriosoite rajoitusalue-taulussa (Hahmotelma. Ei vielä testattu toimiiko.)
CREATE OR REPLACE FUNCTION rajoitusalue_tr_osoite_ei_leikkaa(id_tunniste INTEGER, osoite TR_OSOITE)
    RETURNS BOOLEAN AS
$$
BEGIN
    RETURN (SELECT NOT EXISTS(
            SELECT 1
              FROM rajoitusalue
             WHERE rajoitusalue.id != id_tunniste
               -- FIXME: Varustepuolella on tällainen apufunktio. Tästä voisi tehdä yleisen funktion eri nimellä.
               AND varuste_leikkaus(
                     osoite.tie, osoite.aosa, osoite.aet, osoite.losa, osoite.let,
                     rajoitusalue.tierekisteriosoite.tie, rajoitusalue.tierekisteriosoite.aosa,
                     rajoitusalue.tierekisteriosoite.aet, rajoitusalue.tierekisteriosoite.losa,
                     rajoitusalue.tierekisteriosoite.let)));
END;
$$ LANGUAGE plpgsql;

-- TODO: Estetään rajoitusalueen tallentaminen, mikäli tierekisteriosoite leikkaa aiemman rajoitusalueen osoitteen kanssa.
--       HOX: Hahmotelma. Toimintaa ei vielä varmistettu!
ALTER TABLE rajoitusalue
    ADD CONSTRAINT tierekisteriosoite_ei_leikkaa CHECK (rajoitusalue_tr_osoite_ei_leikkaa(id, tierekisteriosoite));

CREATE INDEX rajoitusalue_tr_osoite_geom_idx ON rajoitusalue USING gist (((tierekisteriosoite).geometria));


-- Rajoitusalueen rajoitus yksittäiselle hoitovuodelle
CREATE TABLE rajoitusalue_rajoitus (
    id                    SERIAL PRIMARY KEY,
    rajoitusalue_id       INTEGER REFERENCES rajoitusalue (id),
    suolarajoitus         NUMERIC,
    formiaatti            BOOLEAN,
    hoitokauden_alkuvuosi SMALLINT,

    luotu                 TIMESTAMP DEFAULT NOW(),
    luoja                 INTEGER REFERENCES kayttaja (id),
    muokattu              TIMESTAMP,
    muokkaaja             INTEGER REFERENCES kayttaja (id),

    CONSTRAINT uniikki_rajoitusalue_rajoitus UNIQUE (rajoitusalue_id, hoitokauden_alkuvuosi)
);



