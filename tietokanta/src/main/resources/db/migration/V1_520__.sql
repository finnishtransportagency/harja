-- YHA-sidonnan lukitukselle eksplisiittinen boolean-arvo
UPDATE yhatiedot SET sidonta_lukittu = FALSE WHERE sidonta_lukittu IS NULL;
ALTER TABLE yhatiedot ALTER COLUMN sidonta_lukittu SET DEFAULT FALSE;
ALTER TABLE yhatiedot ALTER COLUMN sidonta_lukittu SET NOT NULL;