INSERT INTO integraatio (jarjestelma, nimi) VALUES ('api', 'poista-tiestotarkastus');
INSERT INTO integraatio (jarjestelma, nimi) VALUES ('api', 'poista-talvihoitotarkastus');
INSERT INTO integraatio (jarjestelma, nimi) VALUES ('api', 'poista-soratietarkastus');
INSERT INTO integraatio (jarjestelma, nimi) VALUES ('api', 'poista-siltatarkastus');
INSERT INTO integraatio (jarjestelma, nimi) VALUES ('api', 'poista-reittitoteuma');
INSERT INTO integraatio (jarjestelma, nimi) VALUES ('api', 'poista-pistetoteuma');
INSERT INTO integraatio (jarjestelma, nimi) VALUES ('api', 'poista-varustetoteuma');


ALTER TABLE tarkastus ADD COLUMN poistettu boolean default false;
