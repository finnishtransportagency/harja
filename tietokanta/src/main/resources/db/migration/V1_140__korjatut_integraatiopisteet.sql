-- Korjatut integraatiopisteet järjestelmittäin
DELETE FROM integraatio;

-- Sampo:
INSERT INTO integraatio (jarjestelma, nimi) VALUES ('sampo', 'sisaanluku');
INSERT INTO integraatio (jarjestelma, nimi) VALUES ('sampo', 'maksuera-lähetys');
INSERT INTO integraatio (jarjestelma, nimi) VALUES ('sampo', 'kustannussuunnitelma-lahetys');

-- API:
INSERT INTO integraatio (jarjestelma, nimi) VALUES ('api', 'lisaa-tiestotarkastus');
INSERT INTO integraatio (jarjestelma, nimi) VALUES ('api', 'lisaa-talvihoitotarkastus');
INSERT INTO integraatio (jarjestelma, nimi) VALUES ('api', 'lisaa-soratietarkastus');
INSERT INTO integraatio (jarjestelma, nimi) VALUES ('api', 'hae-urakka');
INSERT INTO integraatio (jarjestelma, nimi) VALUES ('api', 'hae-urakka-ytunnuksella');
INSERT INTO integraatio (jarjestelma, nimi) VALUES ('api', 'lisaa-havainto');
INSERT INTO integraatio (jarjestelma, nimi) VALUES ('api', 'lisaa-reittitoteuma');
INSERT INTO integraatio (jarjestelma, nimi) VALUES ('api', 'lisaa-pistetoteuma');
INSERT INTO integraatio (jarjestelma, nimi) VALUES ('api', 'lisaa-varustetoteuma');

