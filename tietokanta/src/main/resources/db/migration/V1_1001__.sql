INSERT INTO integraatio (jarjestelma, nimi) VALUES ('api', 'analytiikka-hae-suunnitellut-materiaalimaarat');
INSERT INTO integraatio (jarjestelma, nimi) VALUES ('api', 'analytiikka-hae-suunnitellut-tehtavamaarat');

UPDATE materiaalikoodi SET materiaaliluokka_id  = (SELECT id FROM materiaaliluokka WHERE nimi = 'Muut') WHERE nimi = 'Jätteet kaatopaikalle';
UPDATE materiaalikoodi SET materiaaliluokka_id  = (SELECT id FROM materiaaliluokka WHERE nimi = 'Muut') WHERE nimi = 'Rikkaruohojen torjunta-aineet';

UPDATE materiaalikoodi SET materiaaliluokka_id  = (SELECT id FROM materiaaliluokka WHERE nimi = 'Kesäsuola') WHERE nimi = 'Kesäsuola sorateiden pölynsidonta';
UPDATE materiaalikoodi SET materiaaliluokka_id  = (SELECT id FROM materiaaliluokka WHERE nimi = 'Kesäsuola') WHERE nimi = 'Kesäsuola sorateiden kevätkunnostus';
UPDATE materiaalikoodi SET materiaaliluokka_id  = (SELECT id FROM materiaaliluokka WHERE nimi = 'Kesäsuola') WHERE nimi = 'Kesäsuola päällystettyjen teiden pölynsidonta';

UPDATE materiaalikoodi SET materiaaliluokka_id  = (SELECT id FROM materiaaliluokka WHERE nimi = 'Talvisuola') WHERE nimi = 'Talvisuolaliuos CaCl2';
UPDATE materiaalikoodi SET materiaaliluokka_id  = (SELECT id FROM materiaaliluokka WHERE nimi = 'Talvisuola') WHERE nimi = 'Talvisuolaliuos NaCl';
UPDATE materiaalikoodi SET materiaaliluokka_id  = (SELECT id FROM materiaaliluokka WHERE nimi = 'Talvisuola') WHERE nimi = 'Talvisuola, rakeinen NaCl';

