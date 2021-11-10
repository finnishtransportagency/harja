CREATE TABLE kattohinnan_oikaisu
(
    id                SERIAL PRIMARY KEY,
    "urakka-id"       INTEGER NOT NULL REFERENCES urakka (id),
    "luoja-id"        INTEGER REFERENCES kayttaja (id),
    luotu             TIMESTAMP,
    "muokkaaja-id"    INTEGER NOT NULL REFERENCES kayttaja (id),
    muokattu          TIMESTAMP,
    "uusi-kattohinta" NUMERIC NOT NULL,
    hoitokausi        INT     NOT NULL,
    poistettu         BOOLEAN
);
