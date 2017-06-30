DROP TRIGGER tg_poista_muistetut_laskutusyht_sanktio ON sanktio;

CREATE TRIGGER tg_poista_muistetut_laskutusyht_sanktio
AFTER INSERT OR UPDATE
  ON sanktio
FOR EACH ROW
WHEN (NEW.sakkoryhma NOT IN ('yllapidon_sakko', 'yllapidon_bonus', 'yllapidon_muistutus'))
EXECUTE PROCEDURE poista_muistetut_laskutusyht_sanktio();