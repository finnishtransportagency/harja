
-- Lisätään materiaalikoodille urakkatyyppi enum sekä boolean onko se "kohdistettava" alueelle

ALTER TABLE materiaalikoodi ADD COLUMN urakkatyyppi urakkatyyppi;
ALTER TABLE materiaalikoodi ADD COLUMN kohdistettava boolean;

UPDATE materiaalikoodi SET urakkatyyppi='hoito'::urakkatyyppi, kohdistettava=false;

ALTER TABLE materiaalikoodi ALTER urakkatyyppi SET NOT NULL;
ALTER TABLE materiaalikoodi ALTER kohdistettava SET NOT NULL;
