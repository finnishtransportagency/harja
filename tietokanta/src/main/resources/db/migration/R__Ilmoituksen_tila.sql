CREATE FUNCTION aseta_ilmoituksen_tila()
  RETURNS TRIGGER AS $$
DECLARE
  uusi_tila ilmoituksen_tila;
BEGIN
  uusi_tila := 'kuittaamaton' :: ilmoituksen_tila;
  IF NEW.kuittaustyyppi = 'lopetus' THEN
    uusi_tila := 'lopetettu' :: ilmoituksen_tila;
  ELSEIF NEW.kuittaustyyppi = 'aloitus' THEN
    uusi_tila := 'aloitettu' :: ilmoituksen_tila;
  ELSEIF NEW.kuittaustyyppi = 'vastaanotto' THEN
    uusi_tila := 'vastaanotettu' :: ilmoituksen_tila;
  END IF;

  UPDATE ilmoitus
  SET tila = uusi_tila
  WHERE id = NEW.ilmoitus;

  RETURN NEW;

END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_aseta_ilmoituksen_tila
AFTER INSERT ON ilmoitustoimenpide
FOR EACH ROW
WHEN (NEW.kuittaustyyppi != 'vastaus' :: kuittaustyyppi AND NEW.kuittaustyyppi != 'muutos' :: kuittaustyyppi AND
      NEW.kuittaustyyppi != 'valitys'::kuittaustyyppi AND NEW.kuittaustyyppi IS NOT NULL)
EXECUTE PROCEDURE aseta_ilmoituksen_tila();