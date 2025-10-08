CREATE TABLE tarjous_tehtavamaara
(
    id        SERIAL PRIMARY KEY,
    urakka_id    INTEGER   NOT NULL REFERENCES urakka (id),
    tehtava_id   INTEGER   NOT NULL REFERENCES tehtava (id),
    maara     NUMERIC,
    muokattu  TIMESTAMP NOT NULL DEFAULT NOW(),
    muokkaaja INTEGER REFERENCES kayttaja (id),
    luotu  TIMESTAMP NOT NULL DEFAULT NOW(),
    luoja INTEGER REFERENCES kayttaja (id),
    UNIQUE (urakka_id, tehtava_id)
);

COMMENT ON TABLE tarjous_tehtavamaara IS
    'Tarjouksen tehtävä- ja määräluettelon mukaisten tietojen tallentamiseen. '
        'Tarjouksen mukaiset tehtäväkohtaiset määrät syötetään urakan alussa, ennen kuin'
        'hoitokausikohtaisia määriä voidaan syöttää.';
