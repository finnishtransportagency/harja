ALTER TABLE integraatio ADD CONSTRAINT uniikki_jarjestelman_integraatio UNIQUE (jarjestelma, nimi);
INSERT INTO integraatio (jarjestelma, nimi) VALUES (''tloik'', ''toimenpiteen-lahetys'');
INSERT INTO integraatio (jarjestelma, nimi) VALUES (''api'', ''hae-paivystajatiedot'');
