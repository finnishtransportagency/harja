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

DELETE FROM koodisto_konversio_koodit WHERE koodisto_konversio_id IN ('v/vtykl', 'v/vtlm', 'v/vtlmln', 'v/vtp');
