ALTER TABLE vv_materiaali ADD COLUMN yksikko TEXT;

-- Tuotannossa on jo materiaaleja, joilla ei ole yksikköä.
UPDATE vv_materiaali SET yksikko='kpl' WHERE yksikko IS NULL;