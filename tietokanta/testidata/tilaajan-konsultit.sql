
-- Tee organisaatio, joka on tyyppiä: tilaajan-konsultti
INSERT INTO organisaatio (nimi, ytunnus, tyyppi, harjassa_luotu, luotu)
VALUES ('Esimerkkillinen Konsultti Oy', '1759662-9', 'tilaajan-konsultti', true, '2022-01-13'::DATE);

-- Tehdään tilaajan-konsultti organisaatiolle käyttäjä, joka on tyyppiä järjestelmä.
INSERT INTO kayttaja (kayttajanimi, etunimi, sukunimi, sahkoposti, puhelin, organisaatio, jarjestelma) VALUES
('KariKonsultti','Kari','Konsultti','black.pyramid@example.com','0401111111',
 (SELECT id FROM organisaatio WHERE nimi = 'Esimerkkillinen Konsultti Oy'), true);

-- Anna KariKonsultille lisäoikeudet oulun MH-urakkaan, jotta tarkastuksia voidaan lisätä
INSERT INTO kayttajan_lisaoikeudet_urakkaan (kayttaja, urakka) VALUES
((SELECT id FROM kayttaja WHERE kayttajanimi = 'KariKonsultti'),
 (SELECT id FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'));
