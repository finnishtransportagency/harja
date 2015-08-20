ALTER TABLE kayttaja_rooli ADD COLUMN luotu timestamp;
ALTER TABLE kayttaja_rooli ADD COLUMN muokattu timestamp;
ALTER TABLE kayttaja_rooli ADD COLUMN luoja integer REFERENCES kayttaja (id);
ALTER TABLE kayttaja_rooli ADD COLUMN muokkaaja integer REFERENCES kayttaja (id);
ALTER TABLE kayttaja_rooli ADD COLUMN poistettu boolean default false;
