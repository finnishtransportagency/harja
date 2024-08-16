-- Luodaan rikkinäinen flyway migraatio, jotta saadaan ecr image, jossa ei ole latest tagia.
-- Käyttäjä id:llä 1 on olemassa
INSERT INTO kayttaja (id, kayttajanimi, etunimi, sukunimi, luotu)
VALUES (1, 'Integraatio', 'Integraatioetu', 'Integraatiotaka', CURRENT_TIMESTAMP);
