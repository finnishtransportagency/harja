-- Korjatut integraatiopisteet järjestelmittäin
-- Sampo:

UPDATE integraatio
SET nimi = 'maksuera-lahetys'
WHERE jarjestelma = 'sampo' AND nimi = 'maksuera-lähetys';

-- T-LOIK:
INSERT INTO integraatio (jarjestelma, nimi) VALUES ('tloik', 'ilmoituksen-kirjaus');