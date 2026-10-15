ALTER TABLE liite
    ADD COLUMN ulkoinen_id TEXT,
    ADD COLUMN poistettu BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN poistaja INTEGER REFERENCES kayttaja (id),
    ADD COLUMN muokkaaja INTEGER REFERENCES kayttaja (id),
    ADD COLUMN muokattu TIMESTAMP;

CREATE INDEX liite_urakka_ulkoinen_id_idx
    ON liite (urakka, lahde, ulkoinen_id)
    WHERE poistettu IS NOT TRUE;
