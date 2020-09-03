CREATE TABLE paallystysmassa
(
    id                SERIAL PRIMARY KEY,
    urakka            INTEGER REFERENCES urakka (id),

    massatyyppitunnus TEXT,
    raekoko           INTEGER,
    nimi              TEXT,
    rc                NUMERIC,

    --Kivi- ja sideaines
    esiintyma         TEXT,
    km_arvo           TEXT,
    muotoarvo         TEXT,
    sideainetyyppi    TEXT,
    pitoisuus         NUMERIC,
    lisaaineet        TEXT,

    -- muokkausmetatiedot
    poistettu         BOOLEAN   DEFAULT FALSE,
    muokkaaja         INTEGER REFERENCES kayttaja (id),
    muokattu          TIMESTAMP,
    luoja             INTEGER REFERENCES kayttaja (id),
    luotu             TIMESTAMP DEFAULT NOW()
);

CREATE INDEX paallystysmassa_urakka_idx ON paallystysmassa (urakka);