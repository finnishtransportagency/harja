-- Kuvaus: älä salli arvoa null toimenpidekoodien kokonaishintainen-sarakkeeseen
UPDATE toimenpidekoodi SET kokonaishintainen = false WHERE kokonaishintainen IS NULL;
ALTER TABLE toimenpidekoodi ALTER COLUMN kokonaishintainen SET NOT NULL;

--Korjaa toimenpidekoodin constraint niin että samalla nimellä voidaan luoda tehtävä jos edellinen poistettu
ALTER TABLE toimenpidekoodi DROP CONSTRAINT uniikki_nimi_emo;

CREATE UNIQUE INDEX uniikki_nimi_emo_jos_ei_poistettu
ON toimenpidekoodi (nimi, emo)
  WHERE poistettu = false;
