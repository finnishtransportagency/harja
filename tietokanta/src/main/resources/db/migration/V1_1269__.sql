-- Luodaan suunnittelu_kalustoresurssi-taulu kalustoresurssien suunnittelua varten
CREATE TABLE suunnittelu_kalustoresurssi
(
    id               SERIAL PRIMARY KEY,
    urakka_id        INTEGER     NOT NULL REFERENCES urakka (id),
    hoitoluokkaryhma VARCHAR(50) NOT NULL,
    maara            INTEGER,
    poistettu        BOOLEAN,
    luoja            INTEGER REFERENCES kayttaja (id),
    luotu            TIMESTAMP,
    muokkaaja        INTEGER REFERENCES kayttaja (id),
    muokattu         TIMESTAMP,
    UNIQUE (urakka_id, hoitoluokkaryhma)
);
