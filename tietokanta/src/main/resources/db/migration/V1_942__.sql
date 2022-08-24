ALTER TABLE paivystys
    ADD COLUMN luotu  timestamp,
    ADD COLUMN muokattu  timestamp,
    ADD COLUMN muokkaaja integer REFERENCES kayttaja (id);