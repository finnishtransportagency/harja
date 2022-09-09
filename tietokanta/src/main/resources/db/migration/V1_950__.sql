-- Lisätään ehdollinen uniikki tarkistus paikkauskohteelle.
-- Jotta sellainen voidaan lisätä, pitää vanha uniikki tarkistus poistaa.
ALTER TABLE paikkauskohde DROP CONSTRAINT paikkauskohteen_uniikki_ulkoinen_id_urakka;
-- Lisää uusi, joka sallii useamman samanlaisen paikkauskohteen, mikäli muut on poistettu.
CREATE UNIQUE INDEX paikkauskohteen_uniikki_ulkoinen_id_urakka ON paikkauskohde ("ulkoinen-id", "urakka-id") WHERE poistettu IS FALSE;
