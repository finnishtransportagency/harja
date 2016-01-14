
-- Luodaan Liikennevirasto
INSERT INTO organisaatio (tyyppi, nimi, lyhenne, ytunnus) VALUES ('liikennevirasto','Liikennevirasto','Livi', '1010547-1');

-- Luodaan hallintayksikot (ELY-keskukset)
\i testidata/elyt.sql

-- Urakoitsijoita, hoidon alueurakat
INSERT INTO organisaatio (tyyppi, ytunnus, nimi, katuosoite, postinumero, sampoid) VALUES ('urakoitsija', '1565583-5', 'YIT Rakennus Oy', 'Panuntie 11, PL 36', 00621, 'YITR-424342');
INSERT INTO organisaatio (tyyppi, ytunnus, nimi) VALUES ('urakoitsija', '1765515-0', 'NCC Roads Oy');
INSERT INTO organisaatio (tyyppi, ytunnus, nimi) VALUES ('urakoitsija', '2163026-3', 'Destia Oy');
INSERT INTO organisaatio (tyyppi, ytunnus, nimi) VALUES ('urakoitsija', '0171337-9', 'Savon Kuljetus Oy');
INSERT INTO organisaatio (tyyppi, ytunnus, nimi) VALUES ('urakoitsija', '2050797-6', 'TSE-Tienvieri Oy');
INSERT INTO organisaatio (tyyppi, ytunnus, nimi, sampoid) VALUES ('urakoitsija', '2138243-1', 'Lemminkäinen Infra Oy','TESTIURAKOITSIJA');
INSERT INTO organisaatio (tyyppi, ytunnus, nimi) VALUES ('urakoitsija', '08851029', 'Carement Oy');

-- Urakoitsijoita, päällystys
INSERT INTO organisaatio (tyyppi, ytunnus, nimi) VALUES ('urakoitsija', '0651792-4', 'Skanska Asfaltti Oy');

-- Urakoitsijoita, tiemerkintä
INSERT INTO organisaatio (tyyppi, ytunnus, nimi) VALUES ('urakoitsija', '1234567-8', 'Tien Merkitsijät Oy');

-- Urakoitsijoita, valaistus
INSERT INTO organisaatio (tyyppi, ytunnus, nimi) VALUES ('urakoitsija', '2234567-8', 'Lampunvaihtajat Oy');

-- Urakoitsijoita, testi
INSERT INTO organisaatio (tyyppi, ytunnus, nimi, sampoid) VALUES ('urakoitsija', '6458856-1', 'Testi Oy', 'TESTIORGANISAATI');

-- Luodaan hoidon alueurakoita ja ylläpitourakoita
\i testidata/urakat.sql

-- Luodaan sopimuksia urakoille, kaikilla urakoilla on oltava ainakin yksi sopimus
\i testidata/sopimukset.sql

-- Luodaan sanktiotyypit
\i testidata/sanktiot.sql

-- Testikäyttäjiä
\i testidata/kayttajat.sql

-- Luodaan yhteyshenkilöpooliin henkilöitä
\i testidata/yhteyshenkilot.sql

-- Tehdään pari hanketta
INSERT INTO hanke (nimi,alkupvm,loppupvm,alueurakkanro, sampoid) values ('Oulun alueurakka','2010-10-01', '2015-09-30', '1238', 'oulu1');
INSERT INTO hanke (nimi,alkupvm,loppupvm,alueurakkanro, sampoid) values ('Pudasjärven alueurakka','2007-10-01', '2012-09-30', '1229', 'pudis2');
INSERT INTO hanke (nimi,alkupvm,loppupvm,alueurakkanro, sampoid) values ('Oulun alueurakka','2014-10-01', '2019-09-30', '1238', 'oulu2');

-- Liitetään urakat niihin
UPDATE urakka SET hanke=(SELECT id FROM hanke WHERE sampoid='oulu1') WHERE tyyppi='hoito' AND nimi LIKE 'Oulun%2005%';
UPDATE urakka SET hanke=(SELECT id FROM hanke WHERE sampoid='pudis2') WHERE tyyppi='hoito' AND nimi LIKE 'Pudas%';
UPDATE urakka SET hanke=(SELECT id FROM hanke WHERE sampoid='oulu2') WHERE tyyppi='hoito' AND nimi LIKE 'Oulun%2014%';

-- Ladataan alueurakoiden geometriat
\i testidata/alueurakat.sql

-- Lisätään ELY numerot hallintayksiköille

UPDATE organisaatio SET elynumero=1 WHERE lyhenne='UUD';
UPDATE organisaatio SET elynumero=2 WHERE lyhenne='VAR';
UPDATE organisaatio SET elynumero=3 WHERE lyhenne='KAS';
UPDATE organisaatio SET elynumero=4 WHERE lyhenne='PIR';
UPDATE organisaatio SET elynumero=8 WHERE lyhenne='POS';
UPDATE organisaatio SET elynumero=9 WHERE lyhenne='KES';
UPDATE organisaatio SET elynumero=10 WHERE lyhenne='EPO';
UPDATE organisaatio SET elynumero=12 WHERE lyhenne='POP';
UPDATE organisaatio SET elynumero=14 WHERE lyhenne='LAP';

-- Lisätään indeksejä
\i testidata/indeksit.sql

-- Suunnitellut työt
\i testidata/suunnitellut_tyot.sql

\i testidata/pohjavesialueet.sql

-- Materiaalin käytöt
INSERT INTO materiaalin_kaytto (alkupvm, loppupvm, maara, materiaali, urakka, sopimus, pohjavesialue, luotu, muokattu, luoja, muokkaaja, poistettu) VALUES ('20051001', '20100930', 15, 1, 1, 1, null, '2004-10-19 10:23:54+02', '2004-10-19 10:23:54+02', 1, 1, false);

-- Toteumat
\i testidata/toteumat.sql

-- Sillat
\i testidata/sillat.sql

-- Maksuerät Oulun alueurakalle
\i testidata/maksuerat.sql

INSERT INTO erilliskustannus (tyyppi,sopimus,urakka,toimenpideinstanssi,pvm,rahasumma,indeksin_nimi,lisatieto,luotu,luoja) VALUES ('muu', (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010') AND paasopimus IS null), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010'), (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP'), '2005-10-15', -20000, 'MAKU 2005', 'Urakoitsija maksaa tilaajalle', '2005-10-13', (SELECT ID FROM kayttaja WHERE kayttajanimi = 'yit_uuvh'));
INSERT INTO erilliskustannus (tyyppi,sopimus,urakka,toimenpideinstanssi,pvm,rahasumma,indeksin_nimi,lisatieto,luotu,luoja) VALUES ('muu', (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010') AND paasopimus IS null), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010'), (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP'), '2005-10-15', 5200, 'MAKU 2005', 'Vahingot on nyt korjattu, lasku tulossa.', '2005-10-13', (SELECT ID FROM kayttaja WHERE kayttajanimi = 'yit_uuvh'));
INSERT INTO erilliskustannus (tyyppi,sopimus,urakka,toimenpideinstanssi,pvm,rahasumma,indeksin_nimi,lisatieto,luotu,luoja) VALUES ('muu', (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010') AND paasopimus IS null), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010'), (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP'), '2005-11-18', -65200, 'MAKU 2005', 'Urakoitsija maksaa tilaajalle.', '2005-10-13', (SELECT ID FROM kayttaja WHERE kayttajanimi = 'yit_uuvh'));
INSERT INTO erilliskustannus (tyyppi,sopimus,urakka,toimenpideinstanssi,pvm,rahasumma,indeksin_nimi,lisatieto,luotu,luoja) VALUES ('asiakastyytyvaisyysbonus', (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010') AND paasopimus IS null), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010'), (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP'), '2005-10-15', 10000, 'MAKU 2005', 'Asiakkaat erittäin tyytyväisiä, tyytyväisyysindeksi 0,92.', '2005-10-13', (SELECT ID FROM kayttaja WHERE kayttajanimi = 'yit_uuvh'));
INSERT INTO erilliskustannus (tyyppi,sopimus,urakka,toimenpideinstanssi,pvm,rahasumma,indeksin_nimi,lisatieto,luotu,luoja) VALUES ('muu', (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010') AND paasopimus IS null), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010'), (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP'), '2005-10-15', 20000, 'MAKU 2005', 'Muun erilliskustannuksen lisätieto', '2005-10-13', (SELECT ID FROM kayttaja WHERE kayttajanimi = 'yit_uuvh'));

INSERT INTO erilliskustannus (tyyppi,sopimus,urakka,toimenpideinstanssi,pvm,rahasumma,indeksin_nimi,lisatieto,luotu,luoja) VALUES ('asiakastyytyvaisyysbonus', (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS null), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP 2014-2019'), '2016-01-15', 20000, 'MAKU 2005', 'As.tyyt. bonuksen lisätieto', NOW(), (SELECT ID FROM kayttaja WHERE kayttajanimi = 'yit_uuvh'));
INSERT INTO erilliskustannus (tyyppi,sopimus,urakka,toimenpideinstanssi,pvm,rahasumma,indeksin_nimi,lisatieto,luotu,luoja) VALUES ('muu', (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS null), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP 2014-2019'), '2016-01-17', 10000, 'MAKU 2005', 'Muun erilliskustannuksen lisätieto', NOW(), (SELECT ID FROM kayttaja WHERE kayttajanimi = 'yit_uuvh'));
INSERT INTO erilliskustannus (tyyppi,sopimus,urakka,toimenpideinstanssi,pvm,rahasumma,indeksin_nimi,lisatieto,luotu,luoja) VALUES ('muu', (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS null), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP 2014-2019'), '2016-01-19', -2000, 'MAKU 2005', 'Tilaaja maksaa urakoitsijalle korvausta 2ke', NOW(), (SELECT ID FROM kayttaja WHERE kayttajanimi = 'yit_uuvh'));

INSERT INTO erilliskustannus (tyyppi,sopimus,urakka,toimenpideinstanssi,pvm,rahasumma,indeksin_nimi,lisatieto,luotu,luoja) VALUES ('asiakastyytyvaisyysbonus', (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012') AND paasopimus IS null), (SELECT id FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'), (SELECT id FROM toimenpideinstanssi WHERE nimi='Pudasjärvi Talvihoito TP'), '2012-01-15', 20000, 'MAKU 2005', 'As.tyyt. bonuksen lisätieto', NOW(), (SELECT ID FROM kayttaja WHERE kayttajanimi = 'jvh'));
INSERT INTO erilliskustannus (tyyppi,sopimus,urakka,toimenpideinstanssi,pvm,rahasumma,indeksin_nimi,lisatieto,luotu,luoja) VALUES ('muu', (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012') AND paasopimus IS null), (SELECT id FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'), (SELECT id FROM toimenpideinstanssi WHERE nimi='Pudasjärvi Talvihoito TP'), '2012-01-19', 10000, 'MAKU 2005', 'Muun erilliskustannuksen lisätieto', NOW(), (SELECT ID FROM kayttaja WHERE kayttajanimi = 'jvh'));

INSERT INTO muutoshintainen_tyo (alkupvm, loppupvm, yksikko, yksikkohinta, tehtava, urakka, sopimus) VALUES ('2005-10-01', '2010-09-30', 'tiekm', 2, (SELECT id FROM toimenpidekoodi WHERE taso=4 AND nimi='Is 1-ajorat.'), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010'), (select id from sopimus where urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010') AND paasopimus IS null));
INSERT INTO muutoshintainen_tyo (alkupvm, loppupvm, yksikko, yksikkohinta, tehtava, urakka, sopimus) VALUES ('2005-10-01', '2010-09-30', 'tiekm', 2.5, (SELECT id FROM toimenpidekoodi WHERE taso=4 AND nimi='Is 2-ajorat.'), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010'), (select id from sopimus where urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010') AND paasopimus IS null));
INSERT INTO muutoshintainen_tyo (alkupvm, loppupvm, yksikko, yksikkohinta, tehtava, urakka, sopimus) VALUES ('2005-10-01', '2010-09-30', 'tiekm', 3.5, (SELECT id FROM toimenpidekoodi WHERE taso=4 AND nimi='I ohituskaistat'), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010'), (select id from sopimus where urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010') AND paasopimus IS null));
INSERT INTO muutoshintainen_tyo (alkupvm, loppupvm, yksikko, yksikkohinta, tehtava, urakka, sopimus) VALUES ('2005-10-01', '2010-09-30', 'tiekm', 4.5, (SELECT id FROM toimenpidekoodi WHERE taso=4 AND nimi='I rampit'), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010'), (select id from sopimus where urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010') AND paasopimus IS null));

-- Päällystyskohteet & -ilmoitukset
\i testidata/yllapito/paallystys.sql

-- Paikkauskohteet & -ilmoitukset
\i testidata/yllapito/paikkaus.sql

-- Päivitä päällystys & paikkausurakoiden geometriat kohdeluetteloiden perusteella
SELECT paivita_paallystys_ja_paikkausurakoiden_geometriat();


-- Ilmoitukset ja kuittaukset
\i testidata/ilmoitukset.sql


-- Turvallisuuspoikkeama
INSERT INTO turvallisuuspoikkeama
(urakka, tapahtunut, paattynyt, kasitelty, tyontekijanammatti, tyotehtava, kuvaus, vammat, sairauspoissaolopaivat,
sairaalavuorokaudet, luotu, luoja, sijainti, tr_numero, tr_alkuosa, tr_loppuosa, tr_alkuetaisyys, tr_loppuetaisyys, tyyppi)
VALUES
((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010'), '2005-10-01 10:00.00', '2005-10-01 12:20.00', '2005-10-06 09:00.00',
'Trukkikuski', 'Lastaus', 'Sepolla oli kiire lastata laatikot, ja torni kaatui päälle. Ehti onneksi pois alta niin ei henki lähtenyt.',
'Murtunut peukalo', 7, 1, NOW(), (SELECT id FROM kayttaja WHERE kayttajanimi='jvh'), ST_MakePoint(435847, 7216217)::GEOMETRY, 6, 6, 6, 6, 6,
ARRAY['turvallisuuspoikkeama']::turvallisuuspoikkeamatyyppi[]);

INSERT INTO turvallisuuspoikkeama
(urakka, tapahtunut, paattynyt, kasitelty, tyontekijanammatti, tyotehtava, kuvaus, vammat, sairauspoissaolopaivat,
 sairaalavuorokaudet, luotu, luoja, sijainti, tr_numero, tr_alkuosa, tr_loppuosa, tr_alkuetaisyys, tr_loppuetaisyys, tyyppi)
VALUES
  ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), '2015-10-01 20:00.00', '2015-10-01 22:20.00', '2015-10-06 23:00.00',
                                                                    'Trukkikuski', 'Lastaus', 'Sepolla oli kiire lastata laatikot, ja torni kaatui päälle. Ehti onneksi pois alta niin ei henki lähtenyt.',
                                                                    'Murtunut niska', 7, 1, NOW(), (SELECT id FROM kayttaja WHERE kayttajanimi='jvh'), ST_MakePoint(435847, 7216217)::GEOMETRY, 6, 6, 6, 6, 6,
   ARRAY['turvallisuuspoikkeama']::turvallisuuspoikkeamatyyppi[]);

INSERT INTO turvallisuuspoikkeama
(urakka, tapahtunut, paattynyt, kasitelty, tyontekijanammatti, tyotehtava, kuvaus, vammat, sairauspoissaolopaivat,
 sairaalavuorokaudet, luotu, luoja, sijainti, tr_numero, tr_alkuosa, tr_loppuosa, tr_alkuetaisyys, tr_loppuetaisyys, tyyppi)
VALUES
  ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), '2015-10-03 10:00.00', '2015-10-03 12:20.00', '2015-10-06 23:00.00',
                                                                    'Trukkikuskina ajaminen', 'Lastauksen tekeminen', 'Matilla oli kiire lastata laatikot, ja torni kaatui päälle. Ehti onneksi pois alta niin ei henki lähtenyt.',
                                                                    'Murtunut käsi', 1, 2, NOW(), (SELECT id FROM kayttaja WHERE kayttajanimi='jvh'), ST_MakePoint(435837, 7216227)::GEOMETRY, 6, 6, 6, 6, 6,
   ARRAY['prosessipoikkeama']::turvallisuuspoikkeamatyyppi[]);

INSERT INTO turvallisuuspoikkeama
(urakka, tapahtunut, paattynyt, kasitelty, tyontekijanammatti, tyotehtava, kuvaus, vammat, sairauspoissaolopaivat,
 sairaalavuorokaudet, luotu, luoja, sijainti, tr_numero, tr_alkuosa, tr_loppuosa, tr_alkuetaisyys, tr_loppuetaisyys, tyyppi)
VALUES
  ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), '2015-10-05 10:00.00', '2015-10-05 12:20.00', '2015-10-07 23:00.00',
                                                                    'Trukkikuskina toimiminen', 'Lastailu', 'Pentillä oli kiire lastata laatikot, ja torni kaatui päälle. Ehti onneksi pois alta niin ei henki lähtenyt.',
                                                                    'Murtunut peukalo', null, null, NOW(), (SELECT id FROM kayttaja WHERE kayttajanimi='jvh'), ST_MakePoint(435817, 7216257)::GEOMETRY, 6, 6, 6, 6, 6,
   ARRAY['prosessipoikkeama', 'turvallisuuspoikkeama', 'tyoturvallisuuspoikkeama']::turvallisuuspoikkeamatyyppi[]);

INSERT INTO turvallisuuspoikkeama
(urakka, tapahtunut, paattynyt, kasitelty, tyontekijanammatti, tyotehtava, kuvaus, vammat, sairauspoissaolopaivat,
 sairaalavuorokaudet, luotu, luoja, sijainti, tr_numero, tr_alkuosa, tr_loppuosa, tr_alkuetaisyys, tr_loppuetaisyys, tyyppi)
VALUES
  ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), '2015-11-01 20:00.00', '2015-11-01 22:20.00', '2015-11-06 23:00.00',
                                                                    'Trukkikuskeilu', 'Lastaaminen', 'Jormalla oli kiire lastata laatikot, ja torni kaatui päälle. Ehti onneksi pois alta niin ei henki lähtenyt.',
                                                                    'Murtunut jalka', 4, 3, NOW(), (SELECT id FROM kayttaja WHERE kayttajanimi='jvh'), ST_MakePoint(435887, 7216237)::GEOMETRY, 6, 6, 6, 6, 6,
   ARRAY['tyoturvallisuuspoikkeama']::turvallisuuspoikkeamatyyppi[]);


INSERT INTO turvallisuuspoikkeama
(urakka, tapahtunut, paattynyt, kasitelty, tyontekijanammatti, tyotehtava, kuvaus, vammat, sairauspoissaolopaivat,
 sairaalavuorokaudet, luotu, luoja, sijainti, tr_numero, tr_alkuosa, tr_loppuosa, tr_alkuetaisyys, tr_loppuetaisyys, tyyppi)
VALUES
  ((SELECT id FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'), '2012-10-01 10:00.00', '2012-10-01 12:20.00', '2012-10-06 09:00.00',
                                                                    'Trukkikuski', 'Lastaus', 'Sepolla oli kiire lastata laatikot, ja torni kaatui päälle. Ehti onneksi pois alta niin ei henki lähtenyt.',
                                                                    'Murtunut peukalo', 7, 1, NOW(), (SELECT id FROM kayttaja WHERE kayttajanimi='jvh'), ST_MakePoint(227110, 6820660) :: GEOMETRY, 6, 6, 6, 6, 6,
   ARRAY['turvallisuuspoikkeama']::turvallisuuspoikkeamatyyppi[]);



INSERT INTO korjaavatoimenpide
(turvallisuuspoikkeama, kuvaus, vastaavahenkilo)
VALUES
((SELECT id FROM turvallisuuspoikkeama WHERE tyontekijanammatti='Trukkikuski' AND tapahtunut = '2005-10-01 10:00.00'), 'Pidetään huoli että ei kenenkään tarvi liikaa kiirehtiä',
'Tomi Työnjohtaja');

-- Havainnot

INSERT INTO laatupoikkeama (kohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu, tarkastuspiste, luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka, kuvaus, tr_numero, tr_alkuosa, tr_loppuosa, tr_loppuetaisyys, sijainti, tr_alkuetaisyys)
VALUES ('Testikohde', 'tilaaja'::osapuoli, 'puhelin'::laatupoikkeaman_kasittelytapa, '', 'hylatty'::laatupoikkeaman_paatostyyppi, 'Ei tässä ole mitään järkeä', 123, 1, NOW(), '2005-10-11 06:06.37', '2005-10-11 06:06.37', false, false, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010'), 'Testihavainto 1', 1, 2, 3, 4, point(418237, 7207744)::GEOMETRY, 5);
INSERT INTO laatupoikkeama (kohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu, tarkastuspiste, luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka, kuvaus, tr_numero, tr_alkuosa, tr_loppuosa, tr_loppuetaisyys, sijainti, tr_alkuetaisyys)
VALUES ('Testikohde', 'tilaaja'::osapuoli, 'puhelin'::laatupoikkeaman_kasittelytapa, '', 'hylatty'::laatupoikkeaman_paatostyyppi, 'Tämä ei ollut asiallinen havainto', 123, 1, NOW(), '2005-10-11 06:06.37', '2005-10-12 06:06.37', true, true, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010'), 'Testihavainto 2', 1, 2, 3, 4, point(418437, 7204744)::GEOMETRY, 5);

-- Sanktiot

INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi, tyyppi, suorasanktio, luoja) VALUES ('A'::sanktiolaji, 1000, '2005-10-12 06:06.37', 'Testi-indeksi', 1, (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Oulu Talvihoito TP'), 1, true, 2);

-- Tarkastukset

INSERT INTO tarkastus (urakka, sopimus, aika, tr_numero, tr_alkuosa, tr_loppuosa, tr_loppuetaisyys, sijainti, tarkastaja, tyyppi, havainnot, luotu, luoja, tr_alkuetaisyys) VALUES ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010'), (SELECT id FROM sopimus WHERE nimi = 'Oulun alueurakka pääsopimus' AND urakka = 1), '2005-10-01 10:00.00', 1 ,2, 3, 4, point(430768.8350704433, 7203153.238678749)::GEOMETRY, 'Ismo', 'pistokoe'::tarkastustyyppi, 'jotain havaintoja siellä oli', NOW(), 1, 3);
INSERT INTO tarkastus (urakka, sopimus, aika, tr_numero, tr_alkuosa, tr_loppuosa, tr_loppuetaisyys, sijainti, tarkastaja, tyyppi, havainnot, luotu, luoja, tr_alkuetaisyys) VALUES ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010'), (SELECT id FROM sopimus WHERE nimi = 'Oulun alueurakka pääsopimus' AND urakka = 1), '2005-10-03 10:00.00', 1 ,2, 3, 4, point(430080.9018158768, 7204538.659816418)::GEOMETRY, 'Matti', 'pistokoe'::tarkastustyyppi, 'havaittiin kaikenlaista', NOW(), 1, 3);
INSERT INTO tarkastus (urakka, sopimus, aika, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, sijainti, tarkastaja, tyyppi, havainnot, luotu, luoja) VALUES ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS NULL), '2015-12-28 10:00.02', 4 ,364, 8012, null, null, point(429000, 7202314)::GEOMETRY, 'Matti', 'talvihoito'::tarkastustyyppi, 'järjestelmän raportoima testitarkastus', NOW(), (SELECT id from kayttaja WHERE kayttajanimi = 'fastroi'));
INSERT INTO tarkastus (urakka, sopimus, aika, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, sijainti, tarkastaja, tyyppi, havainnot, luotu, luoja) VALUES ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS NULL), '2016-01-4 06:02.20', 4 ,364, 5, null, null, point(430750.5220656799, 7198888.689460491)::GEOMETRY, 'Matti', 'talvihoito'::tarkastustyyppi, 'järjestelmän raportoima testitarkastus', NOW(), (SELECT id from kayttaja WHERE kayttajanimi = 'fastroi'));
INSERT INTO tarkastus (urakka, sopimus, aika, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, sijainti, tarkastaja, tyyppi, havainnot, luotu, luoja) VALUES ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS NULL), '2015-11-23 11:00.30', 4 ,364, 8012, null, null, point(430999.34049970115, 7202184.240103625)::GEOMETRY, 'Matti', 'talvihoito'::tarkastustyyppi, 'järjestelmän raportoima testitarkastus', NOW(), (SELECT id from kayttaja WHERE kayttajanimi = 'fastroi'));
INSERT INTO tarkastus (urakka, sopimus, aika, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, sijainti, tarkastaja, tyyppi, havainnot, luotu, luoja) VALUES ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS NULL), '2015-10-23 10:00.02', 4 ,364, 8012, null, null, point(430999.3404997012, 7201565.577905941)::GEOMETRY, 'Matti', 'talvihoito'::tarkastustyyppi, 'järjestelmän raportoima testitarkastus', NOW(), (SELECT id from kayttaja WHERE kayttajanimi = 'fastroi'));
INSERT INTO tarkastus (urakka, sopimus, aika, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, sijainti, tarkastaja, tyyppi, havainnot, luotu, luoja) VALUES ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS NULL), '2016-01-02 16:02.00', 4 ,364, 8012, null, null, point(430877.5189858716, 7200994.6888509365)::GEOMETRY, 'Matti', 'talvihoito'::tarkastustyyppi, 'Urakoitsija on kirjannut tämän tarkastuksen Harjaan käsin', NOW(), (SELECT id from kayttaja WHERE kayttajanimi = 'yit_uuvh'));


-- Tyokoneseurannan havainnot

INSERT INTO tyokonehavainto (jarjestelma, organisaatio, viestitunniste, lahetysaika, tyokoneid, tyokonetyyppi, sijainti, suunta,
urakkaid, tehtavat) VALUES (
  'Urakoitsijan järjestelmä 1',
  (SELECT id FROM organisaatio WHERE nimi='Destia Oy'),
  123,
  current_timestamp,
  31337,
  'aura-auto',
  ST_MakePoint(429493,7207739)::POINT,
  45,
  (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'),
  ARRAY['harjaus', 'suolaus']::suoritettavatehtava[]
);

INSERT INTO tyokonehavainto (jarjestelma, organisaatio, viestitunniste, lahetysaika, tyokoneid, tyokonetyyppi, sijainti, suunta,
urakkaid, tehtavat) VALUES (
  'Urakoitsijan järjestelmä 1',
  (SELECT id FROM organisaatio WHERE nimi='NCC Roads Oy'),
  123,
  current_timestamp,
  31338,
  'aura-auto',
  ST_MakePoint(427861,7211247)::POINT,
  45,
  (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'),
  ARRAY['pistehiekoitus']::suoritettavatehtava[]
);

INSERT INTO tyokonehavainto (jarjestelma, organisaatio, viestitunniste, lahetysaika, tyokoneid, tyokonetyyppi, sijainti, suunta,
urakkaid, tehtavat) VALUES (
  'Urakoitsijan järjestelmä 1',
  (SELECT id FROM organisaatio WHERE nimi='Destia Oy'),
  123,
  current_timestamp,
  31339,
  'aura-auto',
  ST_MakePoint(499399,7249077)::POINT,
  45,
  (SELECT id FROM urakka WHERE nimi = 'Pudasjärven alueurakka 2007-2012'),
  ARRAY['muu']::suoritettavatehtava[]
);

-- hieman hoitoluokkadataa testausta varten
INSERT INTO hoitoluokka (ajorata, aosa, tie, piirinro, let, losa, aet, osa, hoitoluokka, geometria, tietolajitunniste) VALUES (0,801,70816,12,1710,801,0,801,7,ST_GeomFromText('MULTILINESTRING((429451.2124 7199520.6102,429449.505 7199521.6673,429440.5079 7199523.6547,429425.8351 7199523.5332,429414.6011 7199519.5185,429408.1148 7199516.9618,429402.1896 7199514.6903,429391.0467 7199514.8601,429378.936 7199515.034,429367.9027 7199511.4445,429352.9893 7199509.8717,429340.7607 7199509.7674,429325.0809 7199509.6519,429297.0533 7199509.4357,429245.0896 7199508.9075,429203.4416 7199510.4529,429185.9626 7199511.0908,429175.1097 7199511.46,429164.186 7199511.8495,429163.9722 7199511.8543,429127.7205 7199513.124,429097.0326 7199516.1685,429067.7311 7199519.7788,429028.7161 7199523.937,429002.5032 7199526.988,428986.7864 7199529.3238,428972.0898 7199531.8776,428959.8278 7199535.383,428958.5837 7199535.7868,428935.3939 7199544.4855,428935.2753 7199544.526,428916.3783 7199549.9087,428894.5153 7199554.0353,428873.7905 7199561.3118,428862.4296 7199566.3104,428848.2196 7199572.3112,428803.3681 7199591.2262,428767.5476 7199605.5726,428756.8978 7199609.7879,428733.0934 7199619.6432,428710.3663 7199629.7534,428703.1707 7199632.7658,428690.4651 7199638.2455,428667.7141 7199649.3473,428655.186 7199655.9628,428646.3949 7199660.2657,428641.724 7199662.6568,428614.7053 7199677.4118,428588.9015 7199691.2222,428562.1818 7199705.5031,428550.6238 7199710.5445,428540.1979 7199714.1393,428533.4514 7199717.3279,428521.3205 7199724.9439,428505.6115 7199732.8696,428481.9851 7199739.4898,428465.1564 7199744.695,428448.6528 7199752.5844,428428.1823 7199763.7452,428410.7128 7199772.0312,428405.4195 7199775.2603,428399.2614 7199778.1737,428397.2174 7199778.9515,428395.9721 7199781.9532,428393.0872 7199784.3771,428388.8629 7199787.8796,428384.3772 7199791.0521,428380.144 7199794.1436,428376.2853 7199797.0017,428372.3219 7199799.7878,428371.4012 7199800.4268,428368.256 7199802.6143,428364.2134 7199805.383,428359.5495 7199808.7467,428354.9167 7199812.0723,428351.5375 7199814.3962,428349.8128 7199815.5772,428344.2205 7199818.2715,428336.5372 7199818.7545,428328.7401 7199819.147,428321.1527 7199819.6651,428313.5891 7199820.8556,428306.2119 7199822.8829,428298.9807 7199825.3551,428292.1841 7199828.078,428285.6526 7199830.2905,428269.2754 7199839.1119,428258.1283 7199847.3407,428247.9163 7199857.4908,428240.2336 7199869.5991,428231.3359 7199890.2215,428224.6186 7199899.5777,428216.7191 7199908.906,428211.1292 7199916.628,428190.7778 7199924.0874,428179.4979 7199929.741,428165.9323 7199935.3369,428155.9323 7199941.427,428137.8815 7199955.1481,428121.6299 7199971.7797,428113.4761 7199979.4142,428099.1654 7199990.6541,428088.3816 7200000.0896,428078.069 7200006.6729,428060.0235 7200016.3019,428051.5398 7200021.4403,428041.4784 7200029.9056,428033.5629 7200039.833,428026.683 7200047.979,428019.4779 7200057.1232,428013.3812 7200069.4739,428005.1958 7200085.6505,428000.2825 7200094.7583,427991.0495 7200112.5858,427985.4578 7200128.4193,427984.0392 7200133.425,427967.0366 7200140.8427,427958.7166 7200147.4718,427953.0702 7200154.7847))'), 'soratie'::hoitoluokan_tietolajitunniste);

-- Refreshaa Viewit. Nämä kannattanee pitää viimeisenä just in case

SELECT paivita_urakoiden_alueet();
SELECT paivita_pohjavesialueet();

INSERT INTO lampotilat (urakka, alkupvm, loppupvm, keskilampotila, pitka_keskilampotila)
VALUES ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010'), '2006-10-01', '2007-09-30', -7.2, -9.0),
 ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010'), '2007-10-01', '2008-09-30', -11.2, -9.0),
 ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010'), '2009-10-01', '2010-09-30', -6.2, -9.0),
 ((SELECT id FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'), '2011-10-01', '2012-09-30', -8.2, -9.0),
 ((SELECT id FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'), '2007-10-01', '2008-09-30', -13.2, -9.0),
 ((SELECT id FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'), '2008-10-01', '2009-09-30', -5.2, -9.0),
 ((SELECT id FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'), '2009-10-01', '2010-09-30', -5.2, -9.0),
  ((SELECT id FROM urakka WHERE nimi='Porin alueurakka 2007-2012'), '2009-10-01', '2010-09-30', 1.2, -3.0);
-- Luodaan testidataa laskutusyhteenvetoraporttia varten
\i testidata/laskutusyhteenveto.sql
-- ****
