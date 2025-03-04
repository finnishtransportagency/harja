CREATE TABLE tiemerkinta_korjauskustannus (
    id serial PRIMARY KEY,
    urakka INTEGER REFERENCES urakka (id),
    luoja INTEGER REFERENCES kayttaja (id),
    luotu TIMESTAMP NOT NULL DEFAULT NOW(),
    muokattu TIMESTAMP,
    muokkaaja INTEGER REFERENCES kayttaja (id),

    kustannusvuosi INTEGER,
    kustannus NUMERIC,
    pk1 NUMERIC,
    pk2 NUMERIC,
    pk3 NUMERIC);

CREATE UNIQUE INDEX kustannusvuosi_urakka_idx
ON tiemerkinta_korjauskustannus(urakka, kustannusvuosi);
