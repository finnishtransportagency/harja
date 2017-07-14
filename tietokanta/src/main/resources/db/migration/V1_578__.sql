<<<<<<< HEAD
-- Lisää vesiväyläurakoiden toimenpideinstansseilla sopivat toimenpidekoodit (Kaukon "MERIVÄYLIEN URAKKARAKENNE HARJASSA - EHDOTUS - 24.4.2017" Excelin pohjalta)

INSERT INTO toimenpidekoodi (taso, emo, nimi) VALUES (3, (SELECT id
                                                          FROM toimenpidekoodi
                                                          WHERE nimi = 'Väylänhoito'), 'Kauppamerenkulun kustannukset');
INSERT INTO toimenpidekoodi (taso, emo, nimi) VALUES (3, (SELECT id
                                                          FROM toimenpidekoodi
                                                          WHERE nimi = 'Väylänhoito'),
                                                      'Muun vesiliikenteen kustannukset');
INSERT INTO toimenpidekoodi (taso, emo, nimi) VALUES (3, (SELECT id
                                                          FROM toimenpidekoodi
                                                          WHERE nimi = 'Väylänhoito'), 'Urakan yhteiset kustannukset');

-- Lisää olemassa oleville VV-urakoille toimenpideinstanssit

CREATE OR REPLACE FUNCTION lisaa_vv_urakoille_toimenpiteet()
  RETURNS VOID AS $$
DECLARE
  rivi RECORD;
BEGIN
  FOR rivi IN SELECT
                id,
                alkupvm,
                loppupvm
              FROM urakka
              WHERE tyyppi = 'vesivayla-hoito'
  LOOP

    INSERT INTO toimenpideinstanssi (urakka, nimi, toimenpide, alkupvm, loppupvm)
    VALUES (rivi.id,
            'Kauppamerenkulun kustannukset TP',
            (SELECT id
             FROM toimenpidekoodi
             WHERE nimi = 'Kauppamerenkulun kustannukset'),
            rivi.alkupvm,
            rivi.loppupvm);

    INSERT INTO toimenpideinstanssi (urakka, nimi, toimenpide, alkupvm, loppupvm)
    VALUES (rivi.id,
            'Muun vesiliikenteen kustannukset TP',
            (SELECT id
             FROM toimenpidekoodi
             WHERE nimi = 'Muun vesiliikenteen kustannukset'),
            rivi.alkupvm,
            rivi.loppupvm);

    INSERT INTO toimenpideinstanssi (urakka, nimi, toimenpide, alkupvm, loppupvm)
    VALUES (rivi.id,
            'Urakan yhteiset kustannukset TP',
            (SELECT id
             FROM toimenpidekoodi
             WHERE nimi = 'Urakan yhteiset kustannukset'),
            rivi.alkupvm,
            rivi.loppupvm);

  END LOOP;
  RETURN;
END;
$$ LANGUAGE plpgsql;

SELECT * FROM lisaa_vv_urakoille_toimenpiteet();

DROP FUNCTION lisaa_vv_urakoille_toimenpiteet();
=======
CREATE TABLE reimari_sopimuslinkki (
"harja-sopimus-id"     INTEGER NOT NULL
                       UNIQUE
                       REFERENCES sopimus,
"reimari-sopimus-id"   INTEGER NOT NULL
                       UNIQUE);

DROP TRIGGER IF EXISTS toimenpiteen_sopimus_id_trigger ON reimari_toimenpide;
DROP FUNCTION IF EXISTS toimenpiteen_sopimus_id_trigger_proc();

CREATE OR REPLACE FUNCTION toimenpiteen_linkit_trigger_proc()
  RETURNS TRIGGER AS
$$
DECLARE id_temp INTEGER;
BEGIN
  id_temp := (SELECT id FROM sopimus hs, reimari_sopimuslinkki sl
    WHERE
      sl."harja-sopimus-id" = hs.id AND
      sl."reimari-sopimus-id" = (NEW."reimari-sopimus").nro LIMIT 1);

  NEW."sopimus-id" = id_temp;
                -- id:ksi tulee NULL jos ei löydy, joka on ok
  RAISE NOTICE 'reimari_toimenpide linkit trigger: sopimus-id arvoksi %', NEW."sopimus-id";

  id_temp := (SELECT id FROM vv_turvalaite
               WHERE tunniste IS NOT NULL AND tunniste = (NEW."reimari-turvalaite").nro LIMIT 1);

  NEW."turvalaite-id" = id_temp;
                -- id:ksi tulee NULL jos ei löydy, joka on ok
  RAISE NOTICE 'reimari_toimenpide linkit trigger: turvalaite-id arvoksi %', NEW."turvalaite-id";

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS toimenpiteen_linkit_trigger ON reimari_toimenpide;
CREATE TRIGGER toimenpiteen_linkit_trigger
  BEFORE INSERT OR UPDATE ON reimari_toimenpide
  FOR EACH ROW
  EXECUTE PROCEDURE toimenpiteen_linkit_trigger_proc();
>>>>>>> develop
