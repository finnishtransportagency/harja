ALTER TABLE liite
    ADD COLUMN ulkoinen_id TEXT,
    ADD COLUMN poistettu BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN muokkaaja INTEGER REFERENCES kayttaja (id),
    ADD COLUMN muokattu TIMESTAMP;

CREATE UNIQUE INDEX liite_urakka_luoja_ulkoinen_id_idx
    ON liite (urakka, luoja, ulkoinen_id)
    WHERE poistettu IS NOT TRUE;
