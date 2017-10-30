-- vv_vaylan poistettu-kenttä ei voi olla NULL
UPDATE vv_vayla SET poistettu = FALSE WHERE poistettu IS NULL;
ALTER TABLE vv_vayla ALTER COLUMN poistettu SET NOT NULL;