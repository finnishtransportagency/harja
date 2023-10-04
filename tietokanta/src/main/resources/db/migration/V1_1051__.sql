CREATE TABLE velho_nimikkeisto
(id SERIAL PRIMARY KEY,
 versio INT,
 nimikkeisto TEXT,
 nimiavaruus TEXT,
 nimi TEXT,
 otsikko TEXT,
 UNIQUE(versio, nimikkeisto, nimiavaruus, nimi));

INSERT INTO integraatio (jarjestelma, nimi) VALUES ('velho', 'nimikkeiston-tuonti')
