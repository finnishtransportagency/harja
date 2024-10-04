-- Talvihoitoreitityksille tietokantataulut
CREATE TABLE IF NOT EXISTS talvihoitoreitti
(
    id          SERIAL PRIMARY KEY,
    nimi        VARCHAR(255) NOT NULL,
    urakka_id   INTEGER      NOT NULL,
    ulkoinen_id TEXT         NOT NULL, -- Lähettävän järjestelmän oma tunniste
    varikoodi   VARCHAR(255) NOT NULL,
    muokattu    TIMESTAMP,
    muokkaaja   INTEGER,
    luotu       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    luoja       INTEGER
);

-- Vain uniikit ulkoiset id:t urakoittain
CREATE UNIQUE INDEX talvihoitoreitti_ulkoinen_id_urakka_id_uindex
    ON public.talvihoitoreitti (ulkoinen_id, urakka_id);

CREATE TABLE IF NOT EXISTS talvihoitoreitti_sijainti
(
    id                  SERIAL PRIMARY KEY,
    talvihoitoreitti_id INTEGER      NOT NULL REFERENCES talvihoitoreitti (id) ON DELETE CASCADE,
    tie                 INTEGER      NOT NULL,
    alkuosa             INTEGER      NOT NULL,
    alkuetaisyys        INTEGER      NOT NULL,
    loppuosa            INTEGER,
    loppuetaisyys       INTEGER,
    hoitoluokka         VARCHAR(255) NOT NULL,
    pituus_m            DECIMAL(10, 2), -- metreinä
    reitti              geometry
);

CREATE TABLE IF NOT EXISTS talvihoitoreitti_sijainti_kalusto
(
    id                           SERIAL PRIMARY KEY,
    talvihoitoreitti_sijainti_id INTEGER      NOT NULL REFERENCES talvihoitoreitti_sijainti (id) ON DELETE CASCADE,
    kalustotyyppi                VARCHAR(255) NOT NULL,
    maara                        INTEGER      NOT NULL
);

-- Uusi apiavain
INSERT INTO integraatio (jarjestelma, nimi)
VALUES ('api', 'lisaa-talvihoitoreitti');
INSERT INTO integraatio (jarjestelma, nimi)
VALUES ('api', 'poista-talvihoitoreitti');
