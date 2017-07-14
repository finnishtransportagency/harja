CREATE OR REPLACE FUNCTION aseta_ilmoituksen_tila()
  RETURNS TRIGGER AS $$
DECLARE
  uusi_tila ILMOITUKSEN_TILA;
BEGIN
  uusi_tila := 'kuittaamaton' :: ILMOITUKSEN_TILA;
  IF NEW.kuittaustyyppi = 'lopetus'
  THEN
    uusi_tila := 'lopetettu' :: ILMOITUKSEN_TILA;
  ELSEIF NEW.kuittaustyyppi = 'aloitus'
    THEN
      uusi_tila := 'aloitettu' :: ILMOITUKSEN_TILA;
  ELSEIF NEW.kuittaustyyppi = 'vastaanotto'
    THEN
      uusi_tila := 'vastaanotettu' :: ILMOITUKSEN_TILA;
  ELSEIF NEW.kuittaustyyppi = 'vaara-urakka'
    THEN
      uusi_tila := 'lopetettu' :: ILMOITUKSEN_TILA;
  END IF;

  UPDATE ilmoitus
  SET tila = uusi_tila
  WHERE id = NEW.ilmoitus AND NOT tila = 'lopetettu' :: ILMOITUKSEN_TILA;

  RETURN NEW;

END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tg_aseta_ilmoituksen_tila
ON ilmoitustoimenpide;
CREATE TRIGGER tg_aseta_ilmoituksen_tila
AFTER INSERT ON ilmoitustoimenpide
FOR EACH ROW
WHEN (NEW.kuittaustyyppi != 'vastaus' AND
      NEW.kuittaustyyppi != 'muutos' AND
      NEW.kuittaustyyppi != 'valitys' AND
      NEW.kuittaustyyppi IS NOT NULL)
EXECUTE PROCEDURE aseta_ilmoituksen_tila();