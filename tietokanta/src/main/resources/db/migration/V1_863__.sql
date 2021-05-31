UPDATE materiaalikoodi SET nimi = 'Natriumformiaattiliuos' WHERE nimi = 'Natriumformiaatti';
INSERT INTO materiaalikoodi (nimi, yksikko, urakkatyyppi, kohdistettava, materiaalityyppi)
VALUES ('Natriumformiaatti', 't', 'hoito'::urakkatyyppi, false, 'muu'::materiaalityyppi);

-- Lisätään tauluun järjestysnumero, jotta lista saadaan haettua käyttöliittymään halutussa järjestyksessä.
ALTER TABLE materiaalikoodi
ADD COLUMN jarjestys INTEGER;

UPDATE materiaalikoodi SET jarjestys = 1 WHERE nimi = 'Talvisuola';
UPDATE materiaalikoodi SET jarjestys = 2 WHERE nimi = 'Talvisuolaliuos CaCl2';
UPDATE materiaalikoodi SET jarjestys = 3 WHERE nimi = 'Talvisuolaliuos NaCl';
UPDATE materiaalikoodi SET jarjestys = 4 WHERE nimi = 'Talvisuola NaCl, rakeinen'; -- järjestysnumerot on materiaalien-jarjestys-funktion mukaiset
UPDATE materiaalikoodi SET jarjestys = 5 WHERE nimi = 'Erityisalueet CaCl2-liuos';
UPDATE materiaalikoodi SET jarjestys = 6 WHERE nimi = 'Erityisalueet NaCl';
UPDATE materiaalikoodi SET jarjestys = 7 WHERE nimi = 'Erityisalueet NaCl-liuos';
UPDATE materiaalikoodi SET jarjestys = 8 WHERE nimi = 'Hiekoitushiekan suola';
UPDATE materiaalikoodi SET jarjestys = 9 WHERE nimi = 'Kaliumformiaatti';
UPDATE materiaalikoodi SET jarjestys = 10 WHERE nimi = 'Natriumformiaatti';
UPDATE materiaalikoodi SET jarjestys = 11 WHERE nimi = 'Natriumformiaattiliuos';
UPDATE materiaalikoodi SET jarjestys = 12 WHERE nimi = 'Kesäsuola (pölynsidonta)';
UPDATE materiaalikoodi SET jarjestys = 13 WHERE nimi = 'Kesäsuola (sorateiden kevätkunnostus)';
UPDATE materiaalikoodi SET jarjestys = 14 WHERE nimi = 'Hiekoitushiekka';
UPDATE materiaalikoodi SET jarjestys = 15 WHERE nimi = 'Jätteet kaatopaikalle';
UPDATE materiaalikoodi SET jarjestys = 16 WHERE nimi = 'Murskeet';
UPDATE materiaalikoodi SET jarjestys = 17 WHERE nimi = 'Rikkaruohojen torjunta-aineet';
