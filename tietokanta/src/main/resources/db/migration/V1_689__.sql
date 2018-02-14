ALTER TABLE vv_materiaali ADD COLUMN yksikko TEXT;
ALTER TYPE vv_materiaali_muutos ADD ATTRIBUTE luotu TIMESTAMP;

-- Nyt yksikkö löytyy myös materiaalille, joten muutetaan constraint takaisin
ALTER TABLE kan_hinta
DROP CONSTRAINT validi_hinta;

ALTER TABLE kan_hinta
ADD CONSTRAINT validi_hinta
CHECK ((((summa IS NOT NULL) OR ((maara IS NOT NULL) AND (yksikko IS NOT NULL) AND (yksikkohinta IS NOT NULL))) AND (((summa IS NOT NULL) AND (maara IS NULL)) OR ((maara IS NOT NULL) AND (summa IS NULL)))));

-- Tuotannossa on jo materiaaleja, joilla ei ole yksikköä.
UPDATE vv_materiaali SET yksikko='kpl' WHERE yksikko IS NULL;
