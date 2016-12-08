-- Lisää luontipvm laskutusyhteenvedon cacheen

DELETE FROM laskutusyhteenveto_cache;

ALTER TABLE laskutusyhteenveto_cache
  ADD COLUMN tallennettu timestamp DEFAULT NOW();
