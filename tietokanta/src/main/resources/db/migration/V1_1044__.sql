-- laskutuskuukausi puuttuu 95%:sesti bonuksilta. Lisätään se pvm kentän perusteella
-- jotta voidaan tehdä muutokset laskutusyhteenvetoon
UPDATE erilliskustannus
   SET laskutuskuukausi = make_date(EXTRACT(YEAR FROM pvm)::INT, EXTRACT(MONTH FROM pvm)::INT, 15)
 WHERE laskutuskuukausi IS NULL
   AND pvm IS NOT NULL;
