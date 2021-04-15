ALTER TABLE pohjavesialue_talvisuola
    ADD COLUMN luotu     timestamp,
    ADD COLUMN luoja     integer REFERENCES kayttaja (id),
    ADD COLUMN muokattu  timestamp,
    ADD COLUMN muokkaaja integer REFERENCES kayttaja (id);

