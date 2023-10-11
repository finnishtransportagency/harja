CREATE TABLE velho_nimikkeisto
(id SERIAL PRIMARY KEY,
 versio INT,
 tyyppi_avain TEXT,
 kohdeluokka TEXT,
 nimiavaruus TEXT,
 nimi TEXT,
 otsikko TEXT,
 UNIQUE(versio, tyyppi_avain, nimiavaruus, nimi));

INSERT INTO integraatio (jarjestelma, nimi) VALUES ('velho', 'nimikkeiston-tuonti')
