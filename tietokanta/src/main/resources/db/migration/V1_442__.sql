INSERT INTO integraatio (jarjestelma, nimi) VALUES ('api', 'hae-yhteystiedot');
INSERT INTO kayttaja (kayttajanimi, organisaatio, jarjestelma, kuvaus) VALUES
('livi', (SELECT id
FROM organisaatio
WHERE nimi = 'Liikennevirasto'), TRUE, 'Liikenneviraston testi järjestelmätunnus')