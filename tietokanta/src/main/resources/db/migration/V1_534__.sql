-- Vesiväylien Kickoff-migraatio

-- harjassa_luotu
ALTER TABLE hanke ADD COLUMN harjassa_luotu BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE hanke SET harjassa_luotu = FALSE where harjassa_luotu IS NULL;

ALTER TABLE organisaatio ADD COLUMN harjassa_luotu BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE organisaatio SET harjassa_luotu = FALSE where harjassa_luotu IS NULL;

ALTER TABLE urakka ADD COLUMN harjassa_luotu BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE urakka SET harjassa_luotu = FALSE where harjassa_luotu IS NULL;

ALTER TABLE sopimus ADD COLUMN harjassa_luotu BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE sopimus SET harjassa_luotu = FALSE where harjassa_luotu IS NULL;

-- Urakalta ja sopimukselta pois pakollinen sampoid (ei ole pakollinen vesiväyläurakoissa)
ALTER TABLE urakka ALTER COLUMN sampoid DROP NOT NULL;
ALTER TABLE urakka ADD CONSTRAINT urakalla_sampoid_jos_ei_harjassa_luotu
CHECK (harjassa_luotu IS TRUE OR harjassa_luotu IS FALSE AND sampoid IS NOT NULL);
ALTER TABLE sopimus ALTER COLUMN sampoid DROP NOT NULL;
ALTER TABLE sopimus ADD CONSTRAINT sopimuksella_sampoid_jos_ei_harjassa_luotu
CHECK (harjassa_luotu IS TRUE OR harjassa_luotu IS FALSE AND sampoid IS NOT NULL);

-- Luotu, muokattu, muokkaaja jne. tiedot
ALTER TABLE urakka ADD COLUMN luotu timestamp DEFAULT NOW();
ALTER TABLE urakka ADD COLUMN muokattu timestamp;
ALTER TABLE urakka ADD COLUMN luoja integer REFERENCES kayttaja (id);
ALTER TABLE urakka ADD COLUMN muokkaaja integer REFERENCES kayttaja (id);
ALTER TABLE urakka ADD COLUMN poistettu boolean default false;

ALTER TABLE sopimus ADD COLUMN luotu timestamp DEFAULT NOW();
ALTER TABLE sopimus ADD COLUMN muokattu timestamp;
ALTER TABLE sopimus ADD COLUMN luoja integer REFERENCES kayttaja (id);
ALTER TABLE sopimus ADD COLUMN muokkaaja integer REFERENCES kayttaja (id);
ALTER TABLE sopimus ADD COLUMN poistettu boolean default false;

ALTER TABLE hanke ADD COLUMN luotu timestamp DEFAULT NOW();
ALTER TABLE hanke ADD COLUMN muokattu timestamp;
ALTER TABLE hanke ADD COLUMN luoja integer REFERENCES kayttaja (id);
ALTER TABLE hanke ADD COLUMN muokkaaja integer REFERENCES kayttaja (id);
ALTER TABLE hanke ADD COLUMN poistettu boolean default false;

ALTER TABLE organisaatio ADD COLUMN luotu timestamp DEFAULT NOW();
ALTER TABLE organisaatio ADD COLUMN muokattu timestamp;
ALTER TABLE organisaatio ADD COLUMN muokkaaja integer REFERENCES kayttaja (id);
ALTER TABLE organisaatio ADD COLUMN poistettu boolean default false;

-- Tiukkoja constraintteja lisää
ALTER TABLE hanke ALTER COLUMN nimi SET NOT NULL;
ALTER TABLE hanke ALTER COLUMN alkupvm SET NOT NULL;
ALTER TABLE hanke ALTER COLUMN loppupvm SET NOT NULL;
ALTER TABLE hanke ADD CONSTRAINT loppu_ennen_alkua CHECK (alkupvm <= loppupvm);
ALTER TABLE sopimus ADD CONSTRAINT loppu_ennen_alkua CHECK (alkupvm <= loppupvm);
ALTER TABLE sopimus ADD CONSTRAINT paasopimus_ei_ole_sama_sopimus CHECK
(paasopimus IS NULL OR (paasopimus IS NOT NULL AND paasopimus != id)); -- Pääsopimus ei voi koskaan viitata samaan sopimukseen. Jos sopimus on pääsopimus, sen paasopimus on NULL
ALTER TABLE sopimus ALTER alkupvm SET NOT NULL;
ALTER TABLE sopimus ALTER loppupvm SET NOT NULL;
ALTER TABLE urakka ADD CONSTRAINT loppu_ennen_alkua CHECK (alkupvm <= loppupvm);
ALTER TABLE urakka ALTER COLUMN alkupvm SET NOT NULL;
ALTER TABLE urakka ALTER COLUMN loppupvm SET NOT NULL;
ALTER TABLE urakka ALTER COLUMN tyyppi SET NOT NULL;
UPDATE urakka SET poistettu = FALSE WHERE poistettu IS NULL;
ALTER TABLE urakka ALTER COLUMN poistettu SET NOT NULL;
ALTER TABLE urakka ALTER COLUMN poistettu SET DEFAULT FALSE;
ALTER TABLE organisaatio ALTER COLUMN nimi SET NOT NULL;
ALTER TABLE organisaatio ALTER COLUMN tyyppi SET NOT NULL;
CREATE UNIQUE INDEX uniikki_hanke ON urakka (hanke) WHERE harjassa_luotu IS TRUE; -- Harjassa luodulle urakalle on hanke uniikki
