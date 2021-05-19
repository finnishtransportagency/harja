INSERT INTO kayttaja (kayttajanimi,etunimi,sukunimi,sahkoposti,puhelin, organisaatio) VALUES ('tero','Tero','Toripolliisi','tero.toripolliisi@example.com','0405127232', (SELECT id FROM organisaatio WHERE lyhenne='POP'));

INSERT INTO kayttaja (kayttajanimi,etunimi,sukunimi,sahkoposti,puhelin, organisaatio) VALUES ('jvh','Jalmari','Järjestelmävastuuhenkilö','jalmari@example.com', '040123456789', (SELECT id FROM organisaatio WHERE lyhenne='Livi'));
INSERT INTO kayttaja_rooli (kayttaja, rooli) VALUES
  ((SELECT id FROM kayttaja WHERE kayttajanimi='jvh'), 'jarjestelmavastuuhenkilo'),
  ((SELECT id FROM kayttaja WHERE kayttajanimi='jvh'), 'liikennepaivystaja');

INSERT INTO kayttaja (kayttajanimi,etunimi,sukunimi,sahkoposti,puhelin,organisaatio) VALUES ('antero','Antero','Asfalttimies','antero@example.com','0401111111', (SELECT id FROM organisaatio WHERE nimi='Skanska Asfaltti Oy'));

INSERT INTO kayttaja (kayttajanimi,etunimi,sukunimi,sahkoposti,puhelin,organisaatio) values ('ulle', 'Ulle', 'Urakoitsija', 'ulle@example.org', 123123123, (SELECT id FROM organisaatio WHERE nimi='Destia Oy'));
INSERT INTO kayttaja (kayttajanimi,etunimi,sukunimi,sahkoposti,puhelin,organisaatio) values ('yit_pk','Yitin', 'Pääkäyttäjä', 'yit_pk@example.org', 43223123, (SELECT id FROM organisaatio WHERE nimi='YIT Rakennus Oy'));
INSERT INTO kayttaja (kayttajanimi,etunimi,sukunimi,sahkoposti,puhelin,organisaatio) values ('yit_pk2','Uuno', 'Urakoitsija', 'yit_pk2@example.org', 43223123, (SELECT id FROM organisaatio WHERE nimi='YIT Rakennus Oy'));

INSERT INTO kayttaja (kayttajanimi,etunimi,sukunimi,sahkoposti,puhelin,organisaatio) values ('yit_uuvh','Yitin', 'Urakkavastaava', 'yit_uuvh@example.org', 43363123, (SELECT id FROM organisaatio WHERE nimi='YIT Rakennus Oy'));
INSERT INTO kayttaja (kayttajanimi,etunimi,sukunimi,sahkoposti,puhelin,organisaatio, jarjestelma) values ('destia', null, null, null, null, (SELECT id FROM organisaatio WHERE nimi='Destia Oy'), true);
INSERT INTO kayttaja (kayttajanimi,etunimi,sukunimi,sahkoposti,puhelin,organisaatio, jarjestelma) values ('yit-rakennus', null, null, null, null, (SELECT id  FROM organisaatio  WHERE nimi = 'YIT Rakennus Oy'), true);
INSERT INTO kayttaja (kayttajanimi,etunimi,sukunimi,sahkoposti,puhelin,organisaatio, jarjestelma) values ('skanska', null, null, null, null, (SELECT id  FROM organisaatio  WHERE nimi = 'Skanska Asfaltti Oy'), true);
INSERT INTO kayttaja (kayttajanimi,etunimi,sukunimi,sahkoposti,puhelin,organisaatio, jarjestelma) values ('tiemies', null, null, null, null, (SELECT id  FROM organisaatio  WHERE nimi = 'Tien Merkitsijät Oy'), true);
INSERT INTO kayttaja (kayttajanimi,etunimi,sukunimi,sahkoposti,puhelin,organisaatio, jarjestelma) values ('carement', null, null, null, null, (SELECT id  FROM organisaatio  WHERE nimi = 'Carement Oy'), true);

INSERT INTO kayttaja (kayttajanimi,etunimi,sukunimi,jarjestelma) values ('AURA-SIIRTO', 'AURA', 'SIIRTO', TRUE);
INSERT INTO kayttaja (kayttajanimi, organisaatio, jarjestelma, kuvaus) VALUES ('livi', (SELECT id FROM organisaatio WHERE nimi = 'Liikennevirasto'), TRUE, 'Liikenneviraston testi järjestelmätunnus');

INSERT INTO kayttaja (kayttajanimi,etunimi,sukunimi,sahkoposti,puhelin, organisaatio) VALUES ('oletus-kaytto-oikeudet','Testi','Kayttaja','testi.kayttaja@example.com', '893123456789', (SELECT id FROM organisaatio WHERE lyhenne='Livi'));
INSERT INTO kayttaja_rooli (kayttaja, rooli) VALUES
  ((SELECT id FROM kayttaja WHERE kayttajanimi='oletus-kaytto-oikeudet'), 'jarjestelmavastuuhenkilo'),
  ((SELECT id FROM kayttaja WHERE kayttajanimi='oletus-kaytto-oikeudet'), 'liikennepaivystaja');

INSERT INTO kayttaja (kayttajanimi,etunimi,sukunimi,sahkoposti,puhelin, organisaatio) VALUES ('seppo','Seppo','Taalasmalli','seppo@example.com', '040123456789', NULL);

-- Kemin alueurakan Urakan laadunvalvoja, jonka käyttöoikeuksia on rajoitettu
INSERT INTO kayttaja (kayttajanimi,etunimi,sukunimi,sahkoposti,puhelin, organisaatio) VALUES
  ('KeminLaatu','Keppi','Laatujärvi','keppi.laatujarvi@example.com', '123123123',
   (SELECT id  FROM organisaatio  WHERE nimi = 'Kemin Alueurakoitsija Oy'));
-- Kemin alueurakan Urakan urakanvalvoja ELY-keskusksesta, jonka käyttöoikeuksia on rajoitettu
INSERT INTO kayttaja (kayttajanimi,etunimi,sukunimi,sahkoposti,puhelin, organisaatio) VALUES
('ELYKeminLaatu','ELYKeppi','ELYLappi','elykeppi@example.com', '123123123',
 (SELECT id  FROM organisaatio  WHERE lyhenne = 'LAP'));
-- Lapin elyn tilaajakäyttäjä, joka vio tilata testeissä paikkauskohteita lapin alueella
INSERT INTO kayttaja (kayttajanimi,etunimi,sukunimi,sahkoposti,puhelin, organisaatio) VALUES
('tilaaja','ELY','tilaaja','tilaaja@example.org', '434532345',
 (SELECT id  FROM organisaatio  WHERE lyhenne = 'LAP'));