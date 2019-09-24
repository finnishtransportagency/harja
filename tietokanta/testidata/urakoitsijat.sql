-- Urakoitsijoita, hoidon alueurakat
INSERT INTO organisaatio (tyyppi, ytunnus, nimi, katuosoite, postinumero, sampoid) VALUES ('urakoitsija', '1565583-5', 'YIT Rakennus Oy', 'Panuntie 11, PL 36', 00621, 'YITR-424342');
INSERT INTO organisaatio (tyyppi, ytunnus, nimi) VALUES ('urakoitsija', '1765515-0', 'NCC Roads Oy');
INSERT INTO organisaatio (tyyppi, ytunnus, nimi) VALUES ('urakoitsija', '2163026-3', 'Destia Oy');
INSERT INTO organisaatio (tyyppi, ytunnus, nimi) VALUES ('urakoitsija', '0171337-9', 'Savon Kuljetus Oy');
INSERT INTO organisaatio (tyyppi, ytunnus, nimi) VALUES ('urakoitsija', '2050797-6', 'TSE-Tienvieri Oy');
INSERT INTO organisaatio (tyyppi, ytunnus, nimi, sampoid) VALUES ('urakoitsija', '2138243-1', 'Lemminkäinen Infra Oy','TESTIURAKOITSIJA');
INSERT INTO organisaatio (tyyppi, ytunnus, nimi) VALUES ('urakoitsija', '0885102-9', 'Carement Oy');

-- Urakoitsijoita, päällystys
INSERT INTO organisaatio (tyyppi, ytunnus, nimi) VALUES ('urakoitsija', '0651792-4', 'Skanska Asfaltti Oy');

-- Urakoitsijoita, tiemerkintä
INSERT INTO organisaatio (tyyppi, ytunnus, nimi) VALUES ('urakoitsija', '1234567-8', 'Tien Merkitsijät Oy');

-- Urakoitsijoita, valaistus
INSERT INTO organisaatio (tyyppi, ytunnus, nimi) VALUES ('urakoitsija', '2234567-8', 'Lampunvaihtajat Oy');

-- Urakoitsijoita, testi
INSERT INTO organisaatio (tyyppi, ytunnus, nimi, sampoid) VALUES ('urakoitsija', '6458856-1', 'Testi Oy', 'TESTIORGANISAATI');


-- Aliurakoitsijoita (MHU)
INSERT INTO aliurakoitsija (nimi) VALUES ('Kaarinan Kadunkiillotus Oy');
INSERT INTO aliurakoitsija (nimi) VALUES ('Tiinan Tietyö');
INSERT INTO aliurakoitsija (nimi) VALUES ('Alin Urakka Ky');