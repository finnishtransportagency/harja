
-- Korjaus: jos indeksi poistetaan, ei laskutusyhteenvedon cachea invalidoitu
DROP TRIGGER tg_poista_muistetut_laskutusyht_ind ON indeksi;

CREATE TRIGGER tg_poista_muistetut_laskutusyht_ind
AFTER INSERT OR UPDATE OR DELETE
  ON indeksi
FOR EACH ROW
EXECUTE PROCEDURE poista_muistetut_laskutusyht_ind();