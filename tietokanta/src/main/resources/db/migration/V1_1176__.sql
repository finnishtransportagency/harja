CREATE TABLE mhu_muutos_rahavarausmuutoksen_syy
(
    urakka INTEGER REFERENCES urakka(id) NOT NULL,
    hoitokauden_alkuvuosi INTEGER NOT NULL,
    rahavaraus_id INTEGER REFERENCES rahavaraus(id) NOT NULL,
    syy TEXT,

    luotu     TIMESTAMP DEFAULT NOW(),
    luoja     INTEGER REFERENCES kayttaja (id),
    muokattu  TIMESTAMP DEFAULT NULL,
    muokkaaja INTEGER REFERENCES kayttaja (id) DEFAULT NULL,

    CONSTRAINT unique_urakka_hoitokausi_rahavaraus UNIQUE (urakka, hoitokauden_alkuvuosi, rahavaraus_id)
);
