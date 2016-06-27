ALTER TABLE toimenpidekoodi ADD COLUMN api_seuranta BOOLEAN;

UPDATE toimenpidekoodi SET api_seuranta = TRUE WHERE nimi = 'Auraus ja sohjonpoisto';
UPDATE toimenpidekoodi SET api_seuranta = TRUE WHERE nimi = 'Liikennemerkkien puhdistus';
UPDATE toimenpidekoodi SET api_seuranta = TRUE WHERE nimi = 'Linjahiekoitus';
UPDATE toimenpidekoodi SET api_seuranta = TRUE WHERE nimi = 'Lumivallien madaltaminen';
UPDATE toimenpidekoodi SET api_seuranta = TRUE WHERE nimi = 'Pinnan tasaus';
UPDATE toimenpidekoodi SET api_seuranta = TRUE WHERE nimi = 'Pistehiekoitus';
UPDATE toimenpidekoodi SET api_seuranta = TRUE WHERE nimi = 'Sulamisveden haittojen torjunta';
UPDATE toimenpidekoodi SET api_seuranta = TRUE WHERE nimi = 'Suolaus';
UPDATE toimenpidekoodi SET api_seuranta = TRUE WHERE nimi = 'Suolaus';

-- todo: tee loppuun, kun saadaan valmis lista