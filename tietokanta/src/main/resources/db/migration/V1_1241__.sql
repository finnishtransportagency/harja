-- Lisätään miamin integraatiokysely käyttäjien roolien hakemista varten
INSERT INTO integraatio (jarjestelma, nimi) VALUES ('miam', 'hae-kayttajan-roolit') ON CONFLICT DO NOTHING;
