ALTER TABLE kayttaja ADD COLUMN luotu timestamp;
ALTER TABLE kayttaja ADD COLUMN muokattu timestamp;
ALTER TABLE kayttaja ADD COLUMN luoja integer REFERENCES kayttaja (id);
ALTER TABLE kayttaja ADD COLUMN muokkaaja integer REFERENCES kayttaja (id);
ALTER TABLE kayttaja ADD COLUMN poistettu boolean default false;
