-- Urakkastats taulu

CREATE TABLE urakkastats (
  urakka INTEGER PRIMARY KEY REFERENCES urakka (id),
  tyokonehavainto TIMESTAMP, -- Viimeisin työkoneen lähetysaika
  toteuma TIMESTAMP, -- Viimeisin toteuman luonti-/muokkausaika
  ilmoitus TIMESTAMP, -- Viimeisin urakkaan saapunut ilmoitus
  ilmoitustoimenpide TIMESTAMP, -- Viimeisin urakan ilmoitukseen saapunut kuittaus
  tarkastus TIMESTAMP -- Viimeisin urakan tarkastuksen luonti/-muokkausaika
);


-- Päivitä työkonehavainnon aikaleima
CREATE OR REPLACE FUNCTION urakkastats_tyokonehavainto () RETURNS TRIGGER AS $$
BEGIN
  IF NEW.urakkaid IS NOT NULL THEN
   INSERT INTO urakkastats (urakka, tyokonehavainto) VALUES (NEW.urakkaid, current_timestamp)
          ON CONFLICT ON CONSTRAINT urakkastats_pkey
	  DO UPDATE SET tyokonehavainto=current_timestamp;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_urakkastats_tyokonehavainto
AFTER INSERT OR UPDATE ON tyokonehavainto
FOR EACH ROW EXECUTE PROCEDURE urakkastats_tyokonehavainto();

-- Päivitä toteuman aikaleima
CREATE OR REPLACE FUNCTION urakkastats_toteuma () RETURNS TRIGGER AS $$
BEGIN
  IF NEW.urakka IS NOT NULL THEN
   INSERT INTO urakkastats (urakka, toteuma) VALUES (NEW.urakka, current_timestamp)
          ON CONFLICT ON CONSTRAINT urakkastats_pkey
	  DO UPDATE SET toteuma=current_timestamp;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_urakkastats_toteuma
AFTER INSERT OR UPDATE ON toteuma
FOR EACH ROW EXECUTE PROCEDURE urakkastats_toteuma();

-- Päivitä ilmoituksen aikaleima
CREATE OR REPLACE FUNCTION urakkastats_ilmoitus () RETURNS TRIGGER AS $$
BEGIN
  IF NEW.urakka IS NOT NULL THEN
   INSERT INTO urakkastats (urakka, ilmoitus) VALUES (NEW.urakka, current_timestamp)
          ON CONFLICT ON CONSTRAINT urakkastats_pkey
	  DO UPDATE SET ilmoitus=current_timestamp;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_urakkastats_ilmoitus
AFTER INSERT OR UPDATE ON ilmoitus
FOR EACH ROW EXECUTE PROCEDURE urakkastats_ilmoitus();

-- Päivitä ilmoituksen kuittauksen aikaleima
CREATE OR REPLACE FUNCTION urakkastats_ilmoitustoimenpide () RETURNS TRIGGER AS $$
DECLARE
  ilmoituksen_urakka INTEGER;
BEGIN
  ilmoituksen_urakka := SELECT urakka FROM ilmoitus WHERE id = NEW.ilmoitus;
  IF ilmoituksen_urakka IS NOT NULL THEN
   INSERT INTO urakkastats (urakka, ilmoitustoimenpide) VALUES (ilmoituksen_urakka, current_timestamp)
          ON CONFLICT ON CONSTRAINT urakkastats_pkey
	  DO UPDATE SET ilmoitustoimenpide=current_timestamp;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_urakkastats_ilmoitustoimenpide
AFTER INSERT OR UPDATE ON ilmoitustoimenpide
FOR EACH ROW EXECUTE PROCEDURE urakkastats_ilmoitustoimenpide();

-- Päivitä tarkastuksen aikaleima
CREATE OR REPLACE FUNCTION urakkastats_tarkastus () RETURNS TRIGGER AS $$
BEGIN
  IF NEW.urakka IS NOT NULL THEN
   INSERT INTO urakkastats (urakka, tarkastus) VALUES (NEW.urakka, current_timestamp)
          ON CONFLICT ON CONSTRAINT urakkastats_pkey
	  DO UPDATE SET tarkastus=current_timestamp;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_urakkastats_tarkastus
AFTER INSERT OR UPDATE ON tarkastus
FOR EACH ROW EXECUTE PROCEDURE urakkastats_tarkastus();
