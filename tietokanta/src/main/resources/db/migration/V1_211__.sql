-- Tuo materiaalikoodit Aurasta

INSERT INTO materiaalikoodi (nimi, yksikko, urakkatyyppi, kohdistettava, materiaalityyppi) VALUES ('Talvisuolaliuos NaCl', 't', 'hoito'::urakkatyyppi, false, 'talvisuola'::materiaalityyppi);

-- Testidatassa olleet materiaalit nyt kiinteäksi osaksi Harjaa
INSERT INTO materiaalikoodi (nimi, yksikko, urakkatyyppi, kohdistettava, materiaalityyppi) VALUES ('Talvisuolaliuos NaCl', 't', 'hoito'::urakkatyyppi, false, 'talvisuola'::materiaalityyppi);
INSERT INTO materiaalikoodi (nimi, yksikko, urakkatyyppi, kohdistettava, materiaalityyppi) VALUES ('Talvisuolaliuos CaCl2', 't', 'hoito'::urakkatyyppi, false, 'talvisuola'::materiaalityyppi);
INSERT INTO materiaalikoodi (nimi, yksikko, urakkatyyppi, kohdistettava, materiaalityyppi) VALUES ('Erityisalueet NaCl', 't', 'hoito'::urakkatyyppi, true, 'talvisuola'::materiaalityyppi);
INSERT INTO materiaalikoodi (nimi, yksikko, urakkatyyppi, kohdistettava, materiaalityyppi) VALUES ('Erityisalueet NaCl-liuos', 't', 'hoito'::urakkatyyppi, true, 'talvisuola'::materiaalityyppi);
INSERT INTO materiaalikoodi (nimi, yksikko, urakkatyyppi, kohdistettava, materiaalityyppi) VALUES ('Hiekoitushiekka', 't', 'hoito'::urakkatyyppi, false, 'muu'::materiaalityyppi);
INSERT INTO materiaalikoodi (nimi, yksikko, urakkatyyppi, kohdistettava, materiaalityyppi) VALUES ('Kaliumformiaatti', 't', 'hoito'::urakkatyyppi, false, 'talvisuola'::materiaalityyppi);
INSERT INTO materiaalikoodi (nimi, yksikko, urakkatyyppi, kohdistettava, materiaalityyppi) VALUES ('Talvisuola NaCl, rakeinen', 't', 'hoito'::urakkatyyppi, false, 'talvisuola'::materiaalityyppi);
-- Lisäksi puuttuvat materiaalit Aurasta
INSERT INTO materiaalikoodi (nimi, yksikko, urakkatyyppi, kohdistettava, materiaalityyppi) VALUES ('Talvisuola', 't', 'hoito'::urakkatyyppi, false, 'talvisuola'::materiaalityyppi);
INSERT INTO materiaalikoodi (nimi, yksikko, urakkatyyppi, kohdistettava, materiaalityyppi) VALUES ('Hiekoitushiekan suola', 't', 'hoito'::urakkatyyppi, false, 'talvisuola'::materiaalityyppi);
INSERT INTO materiaalikoodi (nimi, yksikko, urakkatyyppi, kohdistettava, materiaalityyppi) VALUES ('Rikkaruohojen torjunta-aineet', 'l', 'hoito'::urakkatyyppi, false, 'muu'::materiaalityyppi);
INSERT INTO materiaalikoodi (nimi, yksikko, urakkatyyppi, kohdistettava, materiaalityyppi) VALUES ('Kesäsuola (sorateiden kevätkunnostus)', 't', 'hoito'::urakkatyyppi, false, 'muu'::materiaalityyppi);
INSERT INTO materiaalikoodi (nimi, yksikko, urakkatyyppi, kohdistettava, materiaalityyppi) VALUES ('Kesäsuola (pölynsidonta)', 't', 'hoito'::urakkatyyppi, false, 'muu'::materiaalityyppi);
INSERT INTO materiaalikoodi (nimi, yksikko, urakkatyyppi, kohdistettava, materiaalityyppi) VALUES ('Murskeet', 't', 'hoito'::urakkatyyppi, false, 'muu'::materiaalityyppi);
INSERT INTO materiaalikoodi (nimi, yksikko, urakkatyyppi, kohdistettava, materiaalityyppi) VALUES ('Jätteet kaatopaikalle', 't', 'hoito'::urakkatyyppi, false, 'muu'::materiaalityyppi);