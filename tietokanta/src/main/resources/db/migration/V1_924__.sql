CREATE TABLE sopimus_tehtavamaara
(
    id        SERIAL PRIMARY KEY,
    urakka    INTEGER   NOT NULL REFERENCES urakka (id),
    tehtava   INTEGER   NOT NULL REFERENCES toimenpidekoodi (id),
    maara     NUMERIC,
    muokattu  TIMESTAMP NOT NULL DEFAULT NOW(),
    muokkaaja INTEGER REFERENCES kayttaja (id),
    UNIQUE (urakka, tehtava)
);

COMMENT ON TABLE sopimus_tehtavamaara IS
    'Tehtävä- ja määräluettelon sopimuksen mukaisten tietojen tallentamiseen. '
        'Sopimuksen mukaiset tehtäväkohtaiset määrät syötetään urakan alussa, ennen kuin'
        'hoitokausikohtaisia määriä voidaan syöttää.';

CREATE TABLE sopimuksen_tehtavamaarat_tallennettu
(
    id          SERIAL PRIMARY KEY,
    urakka      INTEGER NOT NULL REFERENCES urakka (id),
    tallennettu BOOLEAN DEFAULT FALSE
);

COMMENT ON TABLE sopimuksen_tehtavamaarat_tallennettu IS
    'Tauluun tallennetaan tieto, että urakan sopimuksen tehtävämäärät on tallennettu.'