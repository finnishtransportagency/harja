-- Luodaan uusi taulut tarjouksen tiedoille
CREATE TABLE tarjous
(
    id                    serial PRIMARY KEY,
    hoitokauden_alkuvuosi INTEGER   NOT NULL,
    urakka_id             INTEGER   NOT NULL,
    tarjous_tavoitehinta  NUMERIC(10, 2) NOT NULL,
    tarjous_kattohinta    NUMERIC(10, 2) NOT NULL,
    luotu                 TIMESTAMP NOT NULL DEFAULT NOW(),
    luoja                 INTEGER NOT NULL REFERENCES kayttaja (id),
    muokattu              TIMESTAMP,
    muokkaaja             INTEGER REFERENCES kayttaja (id),
    FOREIGN KEY (urakka_id) REFERENCES urakka (id)
);

CREATE TABLE tarjous_kustannukset (
    id serial PRIMARY KEY,
    tarjous_id INTEGER NOT NULL REFERENCES tarjous(id), -- Pääasiallinen mäppäys tämän kautta. Urakka ja hoitokauden_alkuvuosi helpottaa hakemista
    urakka_id INTEGER NOT NULL REFERENCES urakka(id),
    hoitokauden_alkuvuosi INTEGER NOT NULL,
    summa NUMERIC(10, 2) NOT NULL,
    osio suunnittelu_osio NOT NULL,
    tehtava_id INTEGER REFERENCES tehtava(id), -- tehtava.id
    tehtavaryhma_id INTEGER REFERENCES tehtavaryhma(id), -- tehtavaryhma.id
    rahavaraus_id INTEGER REFERENCES rahavaraus(id), -- rahavaraus.id
    luoja INTEGER REFERENCES kayttaja (id),
    luotu TIMESTAMP NOT NULL DEFAULT NOW(),
    muokattu TIMESTAMP,
    muokkaaja INTEGER REFERENCES kayttaja (id)
);

CREATE TABLE tarjous_johto_ja_hallintokorvaus (
    id serial PRIMARY KEY,
    tarjous_id INTEGER NOT NULL REFERENCES tarjous(id), -- Pääasiallinen mäppäys tämän kautta. Urakka ja hoitokauden_alkuvuosi helpottaa hakemista
    urakka_id INTEGER NOT NULL REFERENCES urakka(id),
    hoitokauden_alkuvuosi INTEGER NOT NULL,
    summa NUMERIC(10, 2) NOT NULL,
    osio suunnittelu_osio NOT NULL,
    johto_ja_hallintokorvaus_toimenkuva_id INTEGER REFERENCES johto_ja_hallintokorvaus_toimenkuva(id),
    tehtava_id INTEGER REFERENCES tehtava(id),
    tehtavaryhma_id INTEGER REFERENCES tehtavaryhma(id),
    luoja INTEGER REFERENCES kayttaja (id),
    luotu TIMESTAMP NOT NULL DEFAULT NOW(),
    muokattu TIMESTAMP,
    muokkaaja INTEGER REFERENCES kayttaja (id)
);

