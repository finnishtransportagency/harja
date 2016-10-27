INSERT INTO kayttaja (kayttajanimi,etunimi,sukunimi,sahkoposti,puhelin, organisaatio) VALUES ('tero','Tero','Toripolliisi','tero.toripolliisi@example.com','0405127232', (SELECT id FROM organisaatio WHERE lyhenne='POP'));

INSERT INTO kayttaja (kayttajanimi,etunimi,sukunimi,sahkoposti,puhelin, organisaatio) VALUES ('jvh','Jalmari','Järjestelmävastuuhenkilö','jalmari@example.com', '040123456789', (SELECT id FROM organisaatio WHERE lyhenne='Livi'));
INSERT INTO kayttaja_rooli (kayttaja, rooli) VALUES
  ((SELECT id FROM kayttaja WHERE kayttajanimi='jvh'), 'jarjestelmavastuuhenkilo'),
  ((SELECT id FROM kayttaja WHERE kayttajanimi='jvh'), 'liikennepaivystaja');

INSERT INTO kayttaja (kayttajanimi,etunimi,sukunimi,sahkoposti,puhelin,organisaatio) VALUES ('antero','Antero','Asfalttimies','antero@example.com','0401111111', (SELECT id FROM organisaatio WHERE nimi='Destia Oy'));

INSERT INTO kayttaja (kayttajanimi,etunimi,sukunimi,sahkoposti,puhelin,organisaatio) values ('ulle', 'Ulle', 'Urakoitsija', 'ulle@example.org', 123123123, (SELECT id FROM organisaatio WHERE nimi='Destia Oy'));
INSERT INTO kayttaja (kayttajanimi,etunimi,sukunimi,sahkoposti,puhelin,organisaatio) values ('yit_pk','Yitin', 'Pääkäyttäjä', 'yit_pk@example.org', 43223123, (SELECT id FROM organisaatio WHERE nimi='YIT Rakennus Oy'));
INSERT INTO kayttaja (kayttajanimi,etunimi,sukunimi,sahkoposti,puhelin,organisaatio) values ('yit_pk2','Uuno', 'Urakoitsija', 'yit_pk2@example.org', 43223123, (SELECT id FROM organisaatio WHERE nimi='YIT Rakennus Oy'));
INSERT INTO kayttaja_rooli (kayttaja, rooli) VALUES ((SELECT id FROM kayttaja WHERE kayttajanimi='tero'), 'urakanvalvoja');
INSERT INTO kayttaja_rooli (kayttaja, rooli) VALUES ((SELECT id FROM kayttaja WHERE kayttajanimi='yit_pk2'), 'urakoitsijan paakayttaja');
INSERT INTO kayttaja_rooli (kayttaja, rooli) VALUES ((SELECT id FROM kayttaja WHERE kayttajanimi='yit_pk'), 'urakoitsijan paakayttaja');
INSERT INTO kayttaja_urakka_rooli (kayttaja, urakka, rooli) VALUES ((SELECT id FROM kayttaja WHERE kayttajanimi='yit_pk'), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), 'urakoitsijan paakayttaja');
INSERT INTO kayttaja_urakka_rooli (kayttaja, urakka, rooli) VALUES ((SELECT id FROM kayttaja WHERE kayttajanimi='yit_pk'), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 'urakoitsijan paakayttaja');
INSERT INTO kayttaja_urakka_rooli (kayttaja, urakka, rooli) VALUES ((SELECT id FROM kayttaja WHERE kayttajanimi='tero'), (SELECT id FROM urakka WHERE nimi='Muhoksen päällystysurakka'), 'urakanvalvoja');
INSERT INTO kayttaja_urakka_rooli (kayttaja, urakka, rooli) VALUES ((SELECT id FROM kayttaja WHERE kayttajanimi='yit_pk2'), (SELECT id FROM urakka WHERE nimi='Muhoksen päällystysurakka'), 'urakoitsijan paakayttaja');

INSERT INTO kayttaja (kayttajanimi,etunimi,sukunimi,sahkoposti,puhelin,organisaatio) values ('yit_uuvh','Yitin', 'Urakkavastaava', 'yit_uuvh@example.org', 43363123, (SELECT id FROM organisaatio WHERE nimi='YIT Rakennus Oy'));
INSERT INTO kayttaja_rooli (kayttaja, rooli) VALUES ((SELECT id FROM kayttaja WHERE kayttajanimi='yit_uuvh'), 'urakoitsijan urakan vastuuhenkilo');
INSERT INTO kayttaja_urakka_rooli (kayttaja, urakka, rooli) VALUES ((SELECT id FROM kayttaja WHERE kayttajanimi='yit_uuvh'), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), 'urakoitsijan urakan vastuuhenkilo');
INSERT INTO kayttaja_urakka_rooli (kayttaja, urakka, rooli) VALUES ((SELECT id FROM kayttaja WHERE kayttajanimi='yit_uuvh'), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 'urakoitsijan urakan vastuuhenkilo');
INSERT INTO kayttaja (kayttajanimi,etunimi,sukunimi,sahkoposti,puhelin,organisaatio, jarjestelma) values ('destia', null, null, null, null, (SELECT id FROM organisaatio WHERE nimi='Destia Oy'), true);
INSERT INTO kayttaja (kayttajanimi,etunimi,sukunimi,sahkoposti,puhelin,organisaatio, jarjestelma) values ('yit-rakennus', null, null, null, null, (SELECT id  FROM organisaatio  WHERE nimi = 'YIT Rakennus Oy'), true);
INSERT INTO kayttaja (kayttajanimi,etunimi,sukunimi,sahkoposti,puhelin,organisaatio, jarjestelma) values ('skanska', null, null, null, null, (SELECT id  FROM organisaatio  WHERE nimi = 'Skanska Asfaltti Oy'), true);
INSERT INTO kayttaja (kayttajanimi,etunimi,sukunimi,sahkoposti,puhelin,organisaatio, jarjestelma) values ('tiemies', null, null, null, null, (SELECT id  FROM organisaatio  WHERE nimi = 'Tien Merkitsijät Oy'), true);

INSERT INTO kayttaja (kayttajanimi,etunimi,sukunimi,jarjestelma) values ('AURA-SIIRTO', 'AURA', 'SIIRTO', TRUE);
INSERT INTO kayttaja (kayttajanimi, organisaatio, jarjestelma, kuvaus) VALUES ('livi', (SELECT id FROM organisaatio WHERE nimi = 'Liikennevirasto'), TRUE, 'Liikenneviraston testi järjestelmätunnus');
