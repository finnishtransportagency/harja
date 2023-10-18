CREATE TABLE velho_nimikkeisto
(
    id           SERIAL PRIMARY KEY,
    versio       INT,
    tyyppi_avain TEXT,
    kohdeluokka  TEXT,
    nimiavaruus  TEXT,
    nimi         TEXT,
    otsikko      TEXT,
    UNIQUE (versio, tyyppi_avain, nimiavaruus, nimi)
);

INSERT INTO integraatio (jarjestelma, nimi)
VALUES ('velho', 'nimikkeiston-tuonti');
INSERT INTO integraatio (jarjestelma, nimi)
VALUES ('velho', 'varustetoteuman-historian-haku');


-- testi, älä commitoi
INSERT INTO velho_nimikkeisto (versio, tyyppi_avain, kohdeluokka, nimiavaruus, nimi, otsikko)
VALUES (1, 'tienvarsikalustetyyppi', 'tienvarsikalusteet', 'varusteet', 'tvkttest', 'Testikalustetyyppi'),
       (1, 'kuntoluokka', '', 'kohdeluokka', 'kltest', 'Testikuntoluokka'),
       (1, 'varustetoimenpide', '', 'varustetoimenpide', 'vtptest', 'Testivarustetoimenpide')
