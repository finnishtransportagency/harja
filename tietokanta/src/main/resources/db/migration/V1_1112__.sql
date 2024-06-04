ALTER TABLE organisaatio
    DROP CONSTRAINT organisaatio_ytunnus_key;

CREATE UNIQUE INDEX organisaatio_ytunnus_uniikki_idx
    ON organisaatio (ytunnus)
    WHERE harjassa_luotu = false;
