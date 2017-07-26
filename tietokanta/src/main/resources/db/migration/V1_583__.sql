-- Lisää vesiväyläurakoiden toimenpideinstansseilla sopivat toimenpidekoodit (Kaukon "MERIVÄYLIEN URAKKARAKENNE HARJASSA - EHDOTUS - 24.4.2017" Excelin pohjalta)

INSERT INTO toimenpidekoodi (koodi, taso, emo, nimi) VALUES ('VV112', 3, (SELECT id
                                                          FROM toimenpidekoodi
                                                          WHERE nimi = 'Väylänhoito'), 'Kauppamerenkulun kustannukset');
INSERT INTO toimenpidekoodi (koodi, taso, emo, nimi) VALUES ('VV113', 3, (SELECT id
                                                          FROM toimenpidekoodi
                                                          WHERE nimi = 'Väylänhoito'),
                                                      'Muun vesiliikenteen kustannukset');

UPDATE toimenpidekoodi SET nimi = 'Urakan yhteiset kustannukset' WHERE koodi='VV111';

ALTER TABLE toimenpideinstanssi ALTER COLUMN urakka SET NOT NULL;

-- Lisää olemassa oleville VV-urakoille toimenpideinstanssit

CREATE OR REPLACE FUNCTION lisaa_vv_urakoille_toimenpiteet()
  RETURNS VOID AS $$
DECLARE
  urakka_rivi RECORD;
BEGIN
  FOR urakka_rivi IN SELECT
                id,
                alkupvm,
                loppupvm
              FROM urakka
              WHERE tyyppi = 'vesivayla-hoito'
  LOOP

    INSERT INTO toimenpideinstanssi (urakka, nimi, toimenpide, alkupvm, loppupvm)
    VALUES (urakka_rivi.id,
            'Kauppamerenkulun kustannukset TP',
            (SELECT id
             FROM toimenpidekoodi
             WHERE nimi = 'Kauppamerenkulun kustannukset'),
            urakka_rivi.alkupvm,
            urakka_rivi.loppupvm);

    INSERT INTO toimenpideinstanssi_vesivaylat ("toimenpideinstanssi-id", vaylatyyppi)
    VALUES ((SELECT id FROM toimenpideinstanssi WHERE urakka = urakka_rivi.id
            AND nimi = 'Kauppamerenkulun kustannukset TP'), 'kauppamerenkulku');

    INSERT INTO toimenpideinstanssi (urakka, nimi, toimenpide, alkupvm, loppupvm)
    VALUES (urakka_rivi.id,
            'Muun vesiliikenteen kustannukset TP',
            (SELECT id
             FROM toimenpidekoodi
             WHERE nimi = 'Muun vesiliikenteen kustannukset'),
            urakka_rivi.alkupvm,
            urakka_rivi.loppupvm);

    INSERT INTO toimenpideinstanssi_vesivaylat ("toimenpideinstanssi-id", vaylatyyppi)
    VALUES ((SELECT id FROM toimenpideinstanssi WHERE urakka = urakka_rivi.id
                                                      AND nimi = 'Muun vesiliikenteen kustannukset TP'),
    'muu');

    INSERT INTO toimenpideinstanssi (urakka, nimi, toimenpide, alkupvm, loppupvm)
    VALUES (urakka_rivi.id,
            'Urakan yhteiset kustannukset TP',
            (SELECT id
             FROM toimenpidekoodi
             WHERE nimi = 'Urakan yhteiset kustannukset'),
            urakka_rivi.alkupvm,
            urakka_rivi.loppupvm) ON CONFLICT DO NOTHING;

  END LOOP;
  RETURN;
END;
$$ LANGUAGE plpgsql;

SELECT * FROM lisaa_vv_urakoille_toimenpiteet();

DROP FUNCTION lisaa_vv_urakoille_toimenpiteet();
