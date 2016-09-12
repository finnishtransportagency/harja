CREATE TYPE ilmoituksen_tila AS ENUM ('kuittaamaton', 'vastaanotettu', 'aloitettu', 'lopetettu');

ALTER TABLE ilmoitus
  ADD COLUMN tr_lopputienumero INTEGER,
  ADD COLUMN ulkoinen_id VARCHAR(25),
  ADD COLUMN luoja INTEGER,
  ADD CONSTRAINT ilmoitus_luoja_fkey FOREIGN KEY (luoja) REFERENCES kayttaja (id),
  ADD COLUMN tila ilmoituksen_tila DEFAULT 'kuittaamaton' :: ilmoituksen_tila,
  ALTER COLUMN ilmoitusid DROP NOT NULL;

CREATE INDEX ilmoitus_ilmoitettu_idx ON ilmoitus (ilmoitettu);

CREATE INDEX ilmoitustoimenpide_ilmoitus_idx ON ilmoitustoimenpide (ilmoitus);

UPDATE ilmoitus i
SET tila = coalesce((SELECT CASE
                            WHEN kuittaustyyppi = 'lopetus'
                              THEN 'lopetettu' :: ilmoituksen_tila
                            WHEN kuittaustyyppi = 'aloitus'
                              THEN 'aloitettu' :: ilmoituksen_tila
                            WHEN kuittaustyyppi = 'vastaanotto'
                              THEN 'vastaanotettu' :: ilmoituksen_tila
                            ELSE 'kuittaamaton' :: ilmoituksen_tila
                            END
                     FROM ilmoitustoimenpide
                     WHERE ilmoitus = i.id
                     ORDER BY kuitattu DESC
                     LIMIT 1),
                    'kuittaamaton' :: ilmoituksen_tila);

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
      NEW.kuittaustyyppi IS NOT NULL)
EXECUTE PROCEDURE aseta_ilmoituksen_tila();




