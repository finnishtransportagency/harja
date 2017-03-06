-- Jos muutoshintaista työtä muokataan, poistetaan laskutusyhteenvedot urakan ajalta, koska muutoshinnan voimassa urakan ajan
CREATE OR REPLACE FUNCTION poista_muistetut_laskutusyht_muutoshintainen_tyo() RETURNS trigger AS $$
BEGIN
  DELETE
    FROM laskutusyhteenveto_cache
   WHERE urakka = NEW.urakka;
  RETURN NULL;
END;
$$ LANGUAGE plpgsql;


CREATE TRIGGER tg_poista_muistetut_laskutusyht_muutoshintainen_tyo
AFTER INSERT OR UPDATE
  ON muutoshintainen_tyo
FOR EACH ROW
EXECUTE PROCEDURE poista_muistetut_laskutusyht_muutoshintainen_tyo();


-- Jos urakan lämpötila muuttuu, poistetaan laskutusyhteenvedot ko. hoitokauden ajalta, voi vaikuttaa suolasakon lasketaan
CREATE OR REPLACE FUNCTION poista_muistetut_laskutusyht_lampotila() RETURNS trigger AS $$
BEGIN
  PERFORM poista_hoitokauden_muistetut_laskutusyht(NEW.urakka, NEW.alkupvm::DATE);
  RETURN NULL;
END;
$$ LANGUAGE plpgsql;


CREATE TRIGGER tg_poista_muistetut_laskutusyht_lampotila
AFTER INSERT OR UPDATE
  ON lampotilat
FOR EACH ROW
EXECUTE PROCEDURE poista_muistetut_laskutusyht_lampotila();