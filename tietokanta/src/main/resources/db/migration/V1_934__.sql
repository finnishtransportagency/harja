DROP TABLE varustetoteuma_ulkoiset_kohdevirhe;
CREATE TABLE varustetoteuma_ulkoiset_virhe
(
    aikaleima    timestamp NOT NULL,
    virhekuvaus  text NOT NULL,
    virhekohteen_oid TEXT,
    virhekohteen_alkupvm      timestamp,
    virhekohteen_vastaus      TEXT
);

CREATE INDEX aikaleima_idx ON varustetoteuma_ulkoiset_virhe(aikaleima);
