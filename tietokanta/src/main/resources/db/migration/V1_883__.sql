CREATE TABLE urakka_tavoite_oikaisu
(
    id     SERIAL PRIMARY KEY,
    urakka INTEGER NOT NULL REFERENCES urakka (id),
    luoja_id INTEGER REFERENCES kayttaja(id),
    luotu TIMESTAMP,
    muokkaaja_id INTEGER NOT NULL REFERENCES kayttaja(id),
    muokattu TIMESTAMP,
    selite TEXT,
    summa NUMERIC,
    poistettu BOOLEAN
)
