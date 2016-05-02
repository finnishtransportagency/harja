UPDATE tarkastus
SET
tyyppi='laatu'::tarkastustyyppi
WHERE tyyppi IS NULL;

ALTER TABLE tarkastus
    ALTER tyyppi SET NOT NULL;