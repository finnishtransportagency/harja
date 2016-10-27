-- Laskutusyhteenvedolle uniikkius

DELETE FROM laskutusyhteenveto_cache;

ALTER TABLE laskutusyhteenveto_cache
 ADD CONSTRAINT uniikki_urakka_aika UNIQUE (urakka, alkupvm, loppupvm);
