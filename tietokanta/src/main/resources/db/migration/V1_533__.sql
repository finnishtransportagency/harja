ALTER TABLE urakka ADD COLUMN luotu timestamp;
ALTER TABLE urakka ADD COLUMN muokattu timestamp;
ALTER TABLE urakka ADD COLUMN luoja integer REFERENCES kayttaja (id);
ALTER TABLE urakka ADD COLUMN muokkaaja integer REFERENCES kayttaja (id);
ALTER TABLE urakka ADD COLUMN poistettu boolean default false;

ALTER TABLE sopimus ADD COLUMN luotu timestamp;
ALTER TABLE sopimus ADD COLUMN muokattu timestamp;
ALTER TABLE sopimus ADD COLUMN luoja integer REFERENCES kayttaja (id);
ALTER TABLE sopimus ADD COLUMN muokkaaja integer REFERENCES kayttaja (id);
ALTER TABLE sopimus ADD COLUMN poistettu boolean default false;

ALTER TABLE hanke ADD COLUMN luotu timestamp;
ALTER TABLE hanke ADD COLUMN muokattu timestamp;
ALTER TABLE hanke ADD COLUMN luoja integer REFERENCES kayttaja (id);
ALTER TABLE hanke ADD COLUMN muokkaaja integer REFERENCES kayttaja (id);
ALTER TABLE hanke ADD COLUMN poistettu boolean default false;

ALTER TABLE organisaatio ADD COLUMN luotu timestamp;
ALTER TABLE organisaatio ADD COLUMN muokattu timestamp;
-- ALTER TABLE organisaatio ADD COLUMN luoja integer REFERENCES kayttaja (id);
ALTER TABLE organisaatio ADD COLUMN muokkaaja integer REFERENCES kayttaja (id);
ALTER TABLE organisaatio ADD COLUMN poistettu boolean default false;