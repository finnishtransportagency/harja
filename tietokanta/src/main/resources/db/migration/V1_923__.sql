CREATE TABLE sopimus_tehtavamaara
(
    id        SERIAL PRIMARY KEY,
    urakka    INTEGER NOT NULL REFERENCES urakka (id),
    tehtava   INTEGER NOT NULL REFERENCES toimenpidekoodi (id),
    maara     NUMERIC,
    muokattu  TIMESTAMP,
    muokkaaja INTEGER REFERENCES harja.public.kayttaja (id),
    UNIQUE (urakka, tehtava)
);

COMMENT ON TABLE sopimus_tehtavamaara IS
    'Tehtävä- ja määräluettelon sopimuksen mukaisten tietojen tallentamiseen. '
        'Sopimuksen mukaiset tehtäväkohtaiset määrät syötetään urakan alussa, ennen kuin'
        'hoitokausikohtaisia määriä voidaan syöttää.'
