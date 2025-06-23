-- Lisätään uusi integraatio taitorakennerekisterin siltatarkastuksille
INSERT INTO INTEGRAATIO (jarjestelma, nimi) VALUES ('trex', 'hae-siltatarkastukset');

-- Lisätään uusi oikeustyyppi taitorakennerekisterille
ALTER TYPE apioikeus ADD VALUE 'taitorakenne';
