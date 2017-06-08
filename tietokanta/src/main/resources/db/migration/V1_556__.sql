-- Oletettavasti hinnan otsikon pitää olla uniikki per hinnoittelu
CREATE UNIQUE INDEX uniikki_hinta on vv_hinta ("hinnoittelu-id", otsikko) WHERE poistettu IS NOT TRUE;