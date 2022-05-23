-- Luodaan materiaalikoodeille isäntätaulu, jonka avulla tehtävät voidaan yhdistää tarvittavalla tarkkuudella materiaaliin
CREATE TABLE materiaaliluokka (
  id serial primary key,   -- sisäinen tunniste
  nimi varchar(128),       -- materiaaliluokan nimi (esim. Talvisuola)
  yksikko varchar(16),
  materiaalityyppi materiaalityyppi
);

-- Luodaan tarvittavat materiaaliluokat
INSERT INTO materiaaliluokka (nimi, yksikko, materiaalityyppi) VALUES ('Talvisuola', 't', 'talvisuola');
INSERT INTO materiaaliluokka (nimi, yksikko, materiaalityyppi) VALUES ('Erityisalue', 't', 'erityisalue');
INSERT INTO materiaaliluokka (nimi, yksikko, materiaalityyppi) VALUES ('Formiaatti', 't', 'formiaatti');
INSERT INTO materiaaliluokka (nimi, yksikko, materiaalityyppi) VALUES ('Kesäsuola', 't', 'kesasuola');
INSERT INTO materiaaliluokka (nimi, yksikko, materiaalityyppi) VALUES ('Hiekoitushiekka', 't', 'hiekoitushiekka');
INSERT INTO materiaaliluokka (nimi, yksikko, materiaalityyppi) VALUES ('Murske', 't', 'murske');
INSERT INTO materiaaliluokka (nimi, yksikko, materiaalityyppi) VALUES ('Muut', 't', 'muu');

-- Linkitä materiaalikoodit materiaaliluokkaan
ALTER TABLE materiaalikoodi
    ADD COLUMN materiaaliluokka_id INTEGER;

-- Tarvitsemme uusia materiaalikoodeja vastaamaan uusiin vaatimuksiin, mitä tulee ympäristöraporttiin
INSERT INTO materiaalikoodi (nimi, yksikko, urakkatyyppi, kohdistettava, materiaalityyppi, materiaaliluokka_id)
VALUES ('Kelirikkomurske', 't', 'hoito'::urakkatyyppi, false, 'murske'::materiaalityyppi,  (SELECT id FROM materiaaliluokka WHERE nimi = 'Murske'));

INSERT INTO materiaalikoodi (nimi, yksikko, urakkatyyppi, kohdistettava, materiaalityyppi, materiaaliluokka_id)
VALUES ('Reunantäyttömurske', 't', 'hoito'::urakkatyyppi, false, 'murske'::materiaalityyppi, (SELECT id FROM materiaaliluokka WHERE nimi = 'Murske'));

-- Vanha materiaalikoodi Murske nimetään vain uusiksi Sorastusmurskeeksi
UPDATE materiaalikoodi SET nimi  = 'Sorastusmurske',
                           materiaaliluokka_id = (SELECT id FROM materiaaliluokka
                                                  WHERE nimi = 'Murske') WHERE nimi = 'Murskeet';

-- Nimetään materiaalikoodit uusiksi
UPDATE materiaalikoodi SET nimi  = 'Talvisuola, rakeinen NaCl' WHERE nimi = 'Talvisuola';
UPDATE materiaalikoodi SET nimi  = 'Kaliumformiaattiliuos' WHERE nimi = 'Kaliumformiaatti';
UPDATE materiaalikoodi SET nimi  = 'Kesäsuola sorateiden pölynsidonta' WHERE nimi = 'Kesäsuola (pölynsidonta)';
UPDATE materiaalikoodi SET nimi  = 'Kesäsuola sorateiden kevätkunnostus' WHERE nimi = 'Kesäsuola (sorateiden kevätkunnostus)';

INSERT INTO materiaalikoodi (nimi, yksikko, urakkatyyppi, kohdistettava, materiaalityyppi)
VALUES ('Kesäsuola päällystettyjen teiden pölynsidonta', 't', 'hoito'::urakkatyyppi, false, 'kesasuola'::materiaalityyppi);

UPDATE materiaalikoodi SET materiaaliluokka_id  = (SELECT id FROM materiaaliluokka WHERE nimi = 'Erityisalue') WHERE nimi = 'Erityisalueet CaCl2-liuos';
UPDATE materiaalikoodi SET materiaaliluokka_id  = (SELECT id FROM materiaaliluokka WHERE nimi = 'Erityisalue') WHERE nimi = 'Erityisalueet NaCl';
UPDATE materiaalikoodi SET materiaaliluokka_id  = (SELECT id FROM materiaaliluokka WHERE nimi = 'Erityisalue') WHERE nimi = 'Erityisalueet NaCl-liuos';
UPDATE materiaalikoodi SET materiaaliluokka_id  = (SELECT id FROM materiaaliluokka WHERE nimi = 'Talvisuola') WHERE nimi = 'Hiekoitushiekan suola';
UPDATE materiaalikoodi SET materiaaliluokka_id  = (SELECT id FROM materiaaliluokka WHERE nimi = 'Formiaatti') WHERE nimi = 'Kaliumformiaattiliuos';
UPDATE materiaalikoodi SET materiaaliluokka_id  = (SELECT id FROM materiaaliluokka WHERE nimi = 'Formiaatti') WHERE nimi = 'Natriumformiaatti';
UPDATE materiaalikoodi SET materiaaliluokka_id  = (SELECT id FROM materiaaliluokka WHERE nimi = 'Formiaatti') WHERE nimi = 'Natriumformiaattiliuos';
UPDATE materiaalikoodi SET materiaaliluokka_id  = (SELECT id FROM materiaaliluokka WHERE nimi = 'Kesasuola') WHERE nimi = 'Kesäsuola sorateiden pölynsidonta';
UPDATE materiaalikoodi SET materiaaliluokka_id  = (SELECT id FROM materiaaliluokka WHERE nimi = 'Kesasuola') WHERE nimi = 'Kesäsuola sorateiden kevätkunnostus';
UPDATE materiaalikoodi SET materiaaliluokka_id  = (SELECT id FROM materiaaliluokka WHERE nimi = 'Hiekoitushiekka') WHERE nimi = 'Hiekoitushiekka';
UPDATE materiaalikoodi SET materiaaliluokka_id  = (SELECT id FROM materiaaliluokka WHERE nimi = 'Muu') WHERE nimi = 'Jätteet kaatopaikalle';
UPDATE materiaalikoodi SET materiaaliluokka_id  = (SELECT id FROM materiaaliluokka WHERE nimi = 'Muu') WHERE nimi = 'Rikkaruohojen torjunta-aineet';

-- Päivitä muuttuneiden materiaalikoodien vuoksi myös järjestykset
UPDATE materiaalikoodi SET jarjestys = 13 WHERE nimi = 'Kesäsuola sorateiden kevätkunnostus';
UPDATE materiaalikoodi SET jarjestys = 14 WHERE nimi = 'Kesäsuola sorateiden pölynsidonta';
UPDATE materiaalikoodi SET jarjestys = 15 WHERE nimi = 'Kesäsuola päällystettyjen teiden pölynsidonta';
UPDATE materiaalikoodi SET jarjestys = 16 WHERE nimi = 'Hiekoitushiekka';
UPDATE materiaalikoodi SET jarjestys = 17 WHERE nimi = 'Jätteet kaatopaikalle';
UPDATE materiaalikoodi SET jarjestys = 18 WHERE nimi = 'Rikkaruohojen torjunta-aineet';
UPDATE materiaalikoodi SET jarjestys = 19 WHERE nimi = 'Sorastusmurske';
UPDATE materiaalikoodi SET jarjestys = 20 WHERE nimi = 'Reunantäyttömurske';
UPDATE materiaalikoodi SET jarjestys = 21 WHERE nimi = 'Kelirikkomurske';

-- Lisää toimenpidekoodille tieto mihin materiaalikoodiin tai materiaaliluokkaan se kuuluu
ALTER TABLE toimenpidekoodi
    ADD COLUMN materiaaliluokka_id INTEGER,
    ADD COLUMN materiaalikoodi_id INTEGER;

-- Päivitä kaikille tarvittaville toimenpidekoodeille materiaaliluokka_id:t ja materiaalikoodi_id:t, jotta
-- ne voidaan mäpätä materiaalin käyttöön ja tätä kautta saada ympäristöraportille suunnittelusarakkeeseen.
-- Päivitä ensin ne, joilla ei ole materiaalikoodia vaan pelkästään materiaaliluokka
UPDATE toimenpidekoodi SET materiaaliluokka_id = (SELECT id FROM materiaaliluokka WHERE nimi = 'Talvisuola')
WHERE nimi = 'Suolaus' AND taso = 4;
UPDATE toimenpidekoodi SET materiaaliluokka_id = (SELECT id FROM materiaaliluokka WHERE nimi = 'Formiaatti')
WHERE nimi = 'Kalium- tai natriumformiaatin käyttö liukkaudentorjuntaan (materiaali)' AND taso = 4;

-- Materiaaleihin mäpättävät toimenpidekoodit
UPDATE toimenpidekoodi SET materiaaliluokka_id = (SELECT id FROM materiaaliluokka WHERE nimi = 'Hiekoitushiekka'),
                           materiaalikoodi_id = (SELECT id FROM materiaalikoodi WHERE nimi = 'Hiekoitushiekka')
WHERE nimi = 'Liukkaudentorjunta hiekoituksella' AND taso = 4;

UPDATE toimenpidekoodi SET materiaaliluokka_id = (SELECT id FROM materiaaliluokka WHERE nimi = 'Kesäsuola'),
                           materiaalikoodi_id = (SELECT id FROM materiaalikoodi WHERE nimi = 'Kesäsuola sorateiden kevätkunnostus')
WHERE nimi = 'Sorateiden pölynsidonta' AND taso = 4;

UPDATE toimenpidekoodi SET materiaaliluokka_id = (SELECT id FROM materiaaliluokka WHERE nimi = 'Murske'),
                           materiaalikoodi_id = (SELECT id FROM materiaalikoodi WHERE nimi = 'Kelirikkomurske')
WHERE nimi = 'Liikenteen varmistaminen kelirikkokohteessa' AND taso = 4;

UPDATE toimenpidekoodi SET materiaaliluokka_id = (SELECT id FROM materiaaliluokka WHERE nimi = 'Hiekoitushiekka'),
                           materiaalikoodi_id = (SELECT id FROM materiaalikoodi WHERE nimi = 'Hiekoitushiekka')
WHERE nimi = 'Ennalta arvaamattomien kuljetusten avustaminen' AND taso = 4;

UPDATE toimenpidekoodi SET materiaaliluokka_id = (SELECT id FROM materiaaliluokka WHERE nimi = 'Murske'),
                           materiaalikoodi_id = (SELECT id FROM materiaalikoodi WHERE nimi = 'Reunantäyttömurske')
WHERE nimi = 'Reunantäyttö' AND taso = 4;

UPDATE toimenpidekoodi SET materiaaliluokka_id = (SELECT id FROM materiaaliluokka WHERE nimi = 'Murske'),
                           materiaalikoodi_id = (SELECT id FROM materiaalikoodi WHERE nimi = 'Sorastusmurske')
WHERE nimi = 'Sorastus' AND taso = 4;
