DROP TABLE varustetoteuma_ulkoiset_kohdevirhe;
CREATE TABLE varustetoteuma_ulkoiset_virhe
(
    id           serial PRIMARY KEY NOT NULL,
    ulkoinen_oid varchar(128),
    alkupvm      timestamp,
    aikaleima    timestamp NOT NULL,
    virhekuvaus  text NOT NULL,
    vastaus      VARCHAR(8192)
);
