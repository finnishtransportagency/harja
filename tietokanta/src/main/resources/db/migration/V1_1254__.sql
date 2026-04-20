CREATE TABLE sanktio_laji (
    id          SERIAL PRIMARY KEY,
    koodi       TEXT                     NOT NULL UNIQUE,
    nimi        TEXT                     NOT NULL,
    kuvaus      TEXT,
    aktiivinen  BOOLEAN                  NOT NULL DEFAULT TRUE,
    jarjestys   INTEGER                  NOT NULL,
    luoja       INTEGER                  NOT NULL REFERENCES kayttaja (id),
    luotu       TIMESTAMP                NOT NULL DEFAULT CURRENT_TIMESTAMP,
    muokkaaja   INTEGER                  NOT NULL REFERENCES kayttaja (id),
    muokattu    TIMESTAMP                NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sanktio_profiili (
    id                 SERIAL PRIMARY KEY,
    nimi               TEXT              NOT NULL UNIQUE,
    urakkatyyppi       TEXT              NOT NULL,
    hoitovuosi_alku    INTEGER           NOT NULL,
    hoitovuosi_loppu   INTEGER           NOT NULL,
    alkupvm            DATE              NOT NULL,
    loppupvm           DATE,
    aktiivinen         BOOLEAN           NOT NULL DEFAULT TRUE,
    luoja              INTEGER           NOT NULL REFERENCES kayttaja (id),
    luotu              TIMESTAMP         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    muokkaaja          INTEGER           NOT NULL REFERENCES kayttaja (id),
    muokattu           TIMESTAMP         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (hoitovuosi_alku <= hoitovuosi_loppu),
    CHECK (loppupvm IS NULL OR alkupvm <= loppupvm)
);

CREATE TABLE sanktio_profiili_rivi (
    id                   SERIAL PRIMARY KEY,
    sanktio_profiili_id  INTEGER          NOT NULL REFERENCES sanktio_profiili (id),
    sanktio_laji_id      INTEGER          NOT NULL REFERENCES sanktio_laji (id),
    sanktiotyyppi_id     INTEGER          NOT NULL REFERENCES sanktiotyyppi (id),
    soveltuvuuskonteksti TEXT             NOT NULL,
    jarjestys            INTEGER          NOT NULL,
    aktiivinen           BOOLEAN          NOT NULL DEFAULT TRUE,
    lisametatiedot       JSONB,
    luoja                INTEGER          NOT NULL REFERENCES kayttaja (id),
    luotu                TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    muokkaaja            INTEGER          NOT NULL REFERENCES kayttaja (id),
    muokattu             TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (sanktio_profiili_id, sanktio_laji_id, sanktiotyyppi_id, soveltuvuuskonteksti),
    CHECK (soveltuvuuskonteksti IN ('urakka', 'laatupoikkeama'))
);

CREATE INDEX sanktio_profiili_haku_idx
    ON sanktio_profiili (urakkatyyppi, aktiivinen, alkupvm, loppupvm, hoitovuosi_alku, hoitovuosi_loppu);

CREATE INDEX sanktio_profiili_rivi_haku_idx
    ON sanktio_profiili_rivi (sanktio_profiili_id, soveltuvuuskonteksti, aktiivinen, jarjestys);

COMMENT ON TABLE sanktio_laji
                IS 'Sanktioiden lajimasterdata parametrisoitua sanktio-konfiguraatiota varten.';

COMMENT ON TABLE sanktio_profiili
                IS 'Urakka- ja hoitovuosikontekstissa resolvoitava sanktioiden konfiguraatioprofiili.';

COMMENT ON TABLE sanktio_profiili_rivi
                IS 'Sallitut sanktio_laji- ja sanktiotyyppi-yhdistelmät profiileittain ja soveltuvuuskonteksteittain.';

WITH integraatio AS (
    SELECT id
      FROM kayttaja
     WHERE kayttajanimi = 'Integraatio'
)
INSERT INTO sanktio_laji (koodi, nimi, kuvaus, aktiivinen, jarjestys, luoja, luotu, muokkaaja, muokattu)
VALUES ('muistutus', 'Muistutus', 'Hoidon muistutus', TRUE, 1, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('A', 'A-ryhmä (tehtäväkohtainen sanktio)', 'A-ryhmän sanktio', TRUE, 2, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('B', 'B-ryhmä (vakava laiminlyönti)', 'B-ryhmän sanktio', TRUE, 3, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('C', 'C-ryhmä (määräpäivän ylitys, hallinnollinen laiminlyönti jne.)', 'C-ryhmän sanktio', TRUE, 4, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('arvonvahennyssanktio', 'Arvonvähennys', 'Arvonvähennys', TRUE, 5, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('pohjavesisuolan_ylitys', 'Pohjavesialueen suolankäytön ylitys', 'Pohjavesialueen suolankäytön ylitys', TRUE, 6, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('talvisuolan_ylitys', 'Talvisuolan kokonaiskäytön ylitys', 'Talvisuolan kokonaiskäytön ylitys', TRUE, 7, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('tenttikeskiarvo-sanktio', 'Vastuuhenkilön tenttipistemäärän alentuminen', 'Tenttikeskiarvoon liittyvä sanktio', TRUE, 8, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('testikeskiarvo-sanktio', 'Vastuuhenkilön testipistemäärän alentuminen', 'Testikeskiarvoon liittyvä sanktio', TRUE, 9, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('vaihtosanktio', 'Vastuuhenkilön vaihto', 'Vastuuhenkilön vaihtoon liittyvä sanktio', TRUE, 10, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('yllapidon_sakko', 'Sakko', 'Ylläpidon sakko', TRUE, 1, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('yllapidon_muistutus', 'Muistutus', 'Ylläpidon muistutus', TRUE, 2, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP);

WITH integraatio AS (
    SELECT id
      FROM kayttaja
     WHERE kayttajanimi = 'Integraatio'
)
INSERT INTO sanktio_profiili (nimi, urakkatyyppi, hoitovuosi_alku, hoitovuosi_loppu, alkupvm, loppupvm, aktiivinen, luoja, luotu, muokkaaja, muokattu)
VALUES ('hoito-legacy', 'hoito', 1, 20, DATE '1900-01-01', DATE '2020-12-31', TRUE, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('teiden-hoito-legacy', 'teiden-hoito', 1, 20, DATE '1900-01-01', DATE '2020-12-31', TRUE, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('hoito-2021-ja-uudemmat', 'hoito', 1, 20, DATE '2021-01-01', NULL, TRUE, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('teiden-hoito-2021-ja-uudemmat', 'teiden-hoito', 1, 20, DATE '2021-01-01', NULL, TRUE, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('paallystys-oletus', 'paallystys', 1, 20, DATE '1900-01-01', NULL, TRUE, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('paikkaus-oletus', 'paikkaus', 1, 20, DATE '1900-01-01', NULL, TRUE, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('tiemerkinta-oletus', 'tiemerkinta', 1, 20, DATE '1900-01-01', NULL, TRUE, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('valaistus-oletus', 'valaistus', 1, 20, DATE '1900-01-01', NULL, TRUE, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP);

WITH profiilirivit (profiili_nimi, laji_koodi, sanktiotyyppi_koodi, soveltuvuuskonteksti, jarjestys) AS (
    VALUES
        ('hoito-legacy', 'muistutus', 13, 'urakka', 1),
        ('hoito-legacy', 'muistutus', 14, 'urakka', 2),
        ('hoito-legacy', 'muistutus', 15, 'urakka', 3),
        ('hoito-legacy', 'muistutus', 16, 'urakka', 4),
        ('hoito-legacy', 'muistutus', 10, 'urakka', 5),
        ('hoito-legacy', 'A', 13, 'urakka', 1),
        ('hoito-legacy', 'A', 14, 'urakka', 2),
        ('hoito-legacy', 'A', 15, 'urakka', 3),
        ('hoito-legacy', 'A', 16, 'urakka', 4),
        ('hoito-legacy', 'B', 13, 'urakka', 1),
        ('hoito-legacy', 'B', 14, 'urakka', 2),
        ('hoito-legacy', 'B', 15, 'urakka', 3),
        ('hoito-legacy', 'B', 16, 'urakka', 4),
        ('hoito-legacy', 'C', 8, 'urakka', 1),
        ('hoito-legacy', 'C', 9, 'urakka', 2),
        ('hoito-legacy', 'C', 10, 'urakka', 3),
        ('hoito-legacy', 'C', 11, 'urakka', 4),
        ('hoito-legacy', 'C', 12, 'urakka', 5),
        ('hoito-legacy', 'arvonvahennyssanktio', 0, 'urakka', 1),
        ('hoito-legacy', 'pohjavesisuolan_ylitys', 7, 'urakka', 1),
        ('hoito-legacy', 'talvisuolan_ylitys', 7, 'urakka', 1),
        ('hoito-legacy', 'tenttikeskiarvo-sanktio', 0, 'urakka', 1),
        ('hoito-legacy', 'testikeskiarvo-sanktio', 0, 'urakka', 1),
        ('hoito-legacy', 'vaihtosanktio', 0, 'urakka', 1),
        ('hoito-legacy', 'muistutus', 13, 'laatupoikkeama', 1),
        ('hoito-legacy', 'muistutus', 14, 'laatupoikkeama', 2),
        ('hoito-legacy', 'muistutus', 15, 'laatupoikkeama', 3),
        ('hoito-legacy', 'muistutus', 16, 'laatupoikkeama', 4),
        ('hoito-legacy', 'muistutus', 10, 'laatupoikkeama', 5),
        ('hoito-legacy', 'A', 13, 'laatupoikkeama', 1),
        ('hoito-legacy', 'A', 14, 'laatupoikkeama', 2),
        ('hoito-legacy', 'A', 15, 'laatupoikkeama', 3),
        ('hoito-legacy', 'A', 16, 'laatupoikkeama', 4),
        ('hoito-legacy', 'B', 13, 'laatupoikkeama', 1),
        ('hoito-legacy', 'B', 14, 'laatupoikkeama', 2),
        ('hoito-legacy', 'B', 15, 'laatupoikkeama', 3),
        ('hoito-legacy', 'B', 16, 'laatupoikkeama', 4),
        ('hoito-legacy', 'C', 8, 'laatupoikkeama', 1),
        ('hoito-legacy', 'C', 9, 'laatupoikkeama', 2),
        ('hoito-legacy', 'C', 10, 'laatupoikkeama', 3),
        ('hoito-legacy', 'C', 11, 'laatupoikkeama', 4),
        ('hoito-legacy', 'C', 12, 'laatupoikkeama', 5),
        ('hoito-legacy', 'arvonvahennyssanktio', 0, 'laatupoikkeama', 1),

        ('teiden-hoito-legacy', 'muistutus', 13, 'urakka', 1),
        ('teiden-hoito-legacy', 'muistutus', 14, 'urakka', 2),
        ('teiden-hoito-legacy', 'muistutus', 15, 'urakka', 3),
        ('teiden-hoito-legacy', 'muistutus', 16, 'urakka', 4),
        ('teiden-hoito-legacy', 'muistutus', 10, 'urakka', 5),
        ('teiden-hoito-legacy', 'A', 13, 'urakka', 1),
        ('teiden-hoito-legacy', 'A', 14, 'urakka', 2),
        ('teiden-hoito-legacy', 'A', 15, 'urakka', 3),
        ('teiden-hoito-legacy', 'A', 16, 'urakka', 4),
        ('teiden-hoito-legacy', 'B', 13, 'urakka', 1),
        ('teiden-hoito-legacy', 'B', 14, 'urakka', 2),
        ('teiden-hoito-legacy', 'B', 15, 'urakka', 3),
        ('teiden-hoito-legacy', 'B', 16, 'urakka', 4),
        ('teiden-hoito-legacy', 'C', 8, 'urakka', 1),
        ('teiden-hoito-legacy', 'C', 9, 'urakka', 2),
        ('teiden-hoito-legacy', 'C', 10, 'urakka', 3),
        ('teiden-hoito-legacy', 'C', 11, 'urakka', 4),
        ('teiden-hoito-legacy', 'C', 12, 'urakka', 5),
        ('teiden-hoito-legacy', 'arvonvahennyssanktio', 0, 'urakka', 1),
        ('teiden-hoito-legacy', 'pohjavesisuolan_ylitys', 7, 'urakka', 1),
        ('teiden-hoito-legacy', 'talvisuolan_ylitys', 7, 'urakka', 1),
        ('teiden-hoito-legacy', 'tenttikeskiarvo-sanktio', 0, 'urakka', 1),
        ('teiden-hoito-legacy', 'testikeskiarvo-sanktio', 0, 'urakka', 1),
        ('teiden-hoito-legacy', 'vaihtosanktio', 0, 'urakka', 1),
        ('teiden-hoito-legacy', 'muistutus', 13, 'laatupoikkeama', 1),
        ('teiden-hoito-legacy', 'muistutus', 14, 'laatupoikkeama', 2),
        ('teiden-hoito-legacy', 'muistutus', 15, 'laatupoikkeama', 3),
        ('teiden-hoito-legacy', 'muistutus', 16, 'laatupoikkeama', 4),
        ('teiden-hoito-legacy', 'muistutus', 10, 'laatupoikkeama', 5),
        ('teiden-hoito-legacy', 'A', 13, 'laatupoikkeama', 1),
        ('teiden-hoito-legacy', 'A', 14, 'laatupoikkeama', 2),
        ('teiden-hoito-legacy', 'A', 15, 'laatupoikkeama', 3),
        ('teiden-hoito-legacy', 'A', 16, 'laatupoikkeama', 4),
        ('teiden-hoito-legacy', 'B', 13, 'laatupoikkeama', 1),
        ('teiden-hoito-legacy', 'B', 14, 'laatupoikkeama', 2),
        ('teiden-hoito-legacy', 'B', 15, 'laatupoikkeama', 3),
        ('teiden-hoito-legacy', 'B', 16, 'laatupoikkeama', 4),
        ('teiden-hoito-legacy', 'C', 8, 'laatupoikkeama', 1),
        ('teiden-hoito-legacy', 'C', 9, 'laatupoikkeama', 2),
        ('teiden-hoito-legacy', 'C', 10, 'laatupoikkeama', 3),
        ('teiden-hoito-legacy', 'C', 11, 'laatupoikkeama', 4),
        ('teiden-hoito-legacy', 'C', 12, 'laatupoikkeama', 5),
        ('teiden-hoito-legacy', 'arvonvahennyssanktio', 0, 'laatupoikkeama', 1),

        ('hoito-2021-ja-uudemmat', 'muistutus', 13, 'urakka', 1),
        ('hoito-2021-ja-uudemmat', 'muistutus', 14, 'urakka', 2),
        ('hoito-2021-ja-uudemmat', 'muistutus', 17, 'urakka', 3),
        ('hoito-2021-ja-uudemmat', 'muistutus', 10, 'urakka', 4),
        ('hoito-2021-ja-uudemmat', 'A', 13, 'urakka', 1),
        ('hoito-2021-ja-uudemmat', 'A', 14, 'urakka', 2),
        ('hoito-2021-ja-uudemmat', 'A', 17, 'urakka', 3),
        ('hoito-2021-ja-uudemmat', 'B', 13, 'urakka', 1),
        ('hoito-2021-ja-uudemmat', 'B', 14, 'urakka', 2),
        ('hoito-2021-ja-uudemmat', 'B', 17, 'urakka', 3),
        ('hoito-2021-ja-uudemmat', 'C', 8, 'urakka', 1),
        ('hoito-2021-ja-uudemmat', 'C', 9, 'urakka', 2),
        ('hoito-2021-ja-uudemmat', 'C', 10, 'urakka', 3),
        ('hoito-2021-ja-uudemmat', 'C', 11, 'urakka', 4),
        ('hoito-2021-ja-uudemmat', 'C', 12, 'urakka', 5),
        ('hoito-2021-ja-uudemmat', 'arvonvahennyssanktio', 0, 'urakka', 1),
        ('hoito-2021-ja-uudemmat', 'pohjavesisuolan_ylitys', 7, 'urakka', 1),
        ('hoito-2021-ja-uudemmat', 'talvisuolan_ylitys', 7, 'urakka', 1),
        ('hoito-2021-ja-uudemmat', 'tenttikeskiarvo-sanktio', 0, 'urakka', 1),
        ('hoito-2021-ja-uudemmat', 'testikeskiarvo-sanktio', 0, 'urakka', 1),
        ('hoito-2021-ja-uudemmat', 'vaihtosanktio', 0, 'urakka', 1),
        ('hoito-2021-ja-uudemmat', 'muistutus', 13, 'laatupoikkeama', 1),
        ('hoito-2021-ja-uudemmat', 'muistutus', 14, 'laatupoikkeama', 2),
        ('hoito-2021-ja-uudemmat', 'muistutus', 17, 'laatupoikkeama', 3),
        ('hoito-2021-ja-uudemmat', 'muistutus', 10, 'laatupoikkeama', 4),
        ('hoito-2021-ja-uudemmat', 'A', 13, 'laatupoikkeama', 1),
        ('hoito-2021-ja-uudemmat', 'A', 14, 'laatupoikkeama', 2),
        ('hoito-2021-ja-uudemmat', 'A', 17, 'laatupoikkeama', 3),
        ('hoito-2021-ja-uudemmat', 'B', 13, 'laatupoikkeama', 1),
        ('hoito-2021-ja-uudemmat', 'B', 14, 'laatupoikkeama', 2),
        ('hoito-2021-ja-uudemmat', 'B', 17, 'laatupoikkeama', 3),
        ('hoito-2021-ja-uudemmat', 'C', 8, 'laatupoikkeama', 1),
        ('hoito-2021-ja-uudemmat', 'C', 9, 'laatupoikkeama', 2),
        ('hoito-2021-ja-uudemmat', 'C', 10, 'laatupoikkeama', 3),
        ('hoito-2021-ja-uudemmat', 'C', 11, 'laatupoikkeama', 4),
        ('hoito-2021-ja-uudemmat', 'C', 12, 'laatupoikkeama', 5),
        ('hoito-2021-ja-uudemmat', 'arvonvahennyssanktio', 0, 'laatupoikkeama', 1),

        ('teiden-hoito-2021-ja-uudemmat', 'muistutus', 13, 'urakka', 1),
        ('teiden-hoito-2021-ja-uudemmat', 'muistutus', 14, 'urakka', 2),
        ('teiden-hoito-2021-ja-uudemmat', 'muistutus', 17, 'urakka', 3),
        ('teiden-hoito-2021-ja-uudemmat', 'muistutus', 10, 'urakka', 4),
        ('teiden-hoito-2021-ja-uudemmat', 'A', 13, 'urakka', 1),
        ('teiden-hoito-2021-ja-uudemmat', 'A', 14, 'urakka', 2),
        ('teiden-hoito-2021-ja-uudemmat', 'A', 17, 'urakka', 3),
        ('teiden-hoito-2021-ja-uudemmat', 'B', 13, 'urakka', 1),
        ('teiden-hoito-2021-ja-uudemmat', 'B', 14, 'urakka', 2),
        ('teiden-hoito-2021-ja-uudemmat', 'B', 17, 'urakka', 3),
        ('teiden-hoito-2021-ja-uudemmat', 'C', 8, 'urakka', 1),
        ('teiden-hoito-2021-ja-uudemmat', 'C', 9, 'urakka', 2),
        ('teiden-hoito-2021-ja-uudemmat', 'C', 10, 'urakka', 3),
        ('teiden-hoito-2021-ja-uudemmat', 'C', 11, 'urakka', 4),
        ('teiden-hoito-2021-ja-uudemmat', 'C', 12, 'urakka', 5),
        ('teiden-hoito-2021-ja-uudemmat', 'arvonvahennyssanktio', 0, 'urakka', 1),
        ('teiden-hoito-2021-ja-uudemmat', 'pohjavesisuolan_ylitys', 7, 'urakka', 1),
        ('teiden-hoito-2021-ja-uudemmat', 'talvisuolan_ylitys', 7, 'urakka', 1),
        ('teiden-hoito-2021-ja-uudemmat', 'tenttikeskiarvo-sanktio', 0, 'urakka', 1),
        ('teiden-hoito-2021-ja-uudemmat', 'testikeskiarvo-sanktio', 0, 'urakka', 1),
        ('teiden-hoito-2021-ja-uudemmat', 'vaihtosanktio', 0, 'urakka', 1),
        ('teiden-hoito-2021-ja-uudemmat', 'muistutus', 13, 'laatupoikkeama', 1),
        ('teiden-hoito-2021-ja-uudemmat', 'muistutus', 14, 'laatupoikkeama', 2),
        ('teiden-hoito-2021-ja-uudemmat', 'muistutus', 17, 'laatupoikkeama', 3),
        ('teiden-hoito-2021-ja-uudemmat', 'muistutus', 10, 'laatupoikkeama', 4),
        ('teiden-hoito-2021-ja-uudemmat', 'A', 13, 'laatupoikkeama', 1),
        ('teiden-hoito-2021-ja-uudemmat', 'A', 14, 'laatupoikkeama', 2),
        ('teiden-hoito-2021-ja-uudemmat', 'A', 17, 'laatupoikkeama', 3),
        ('teiden-hoito-2021-ja-uudemmat', 'B', 13, 'laatupoikkeama', 1),
        ('teiden-hoito-2021-ja-uudemmat', 'B', 14, 'laatupoikkeama', 2),
        ('teiden-hoito-2021-ja-uudemmat', 'B', 17, 'laatupoikkeama', 3),
        ('teiden-hoito-2021-ja-uudemmat', 'C', 8, 'laatupoikkeama', 1),
        ('teiden-hoito-2021-ja-uudemmat', 'C', 9, 'laatupoikkeama', 2),
        ('teiden-hoito-2021-ja-uudemmat', 'C', 10, 'laatupoikkeama', 3),
        ('teiden-hoito-2021-ja-uudemmat', 'C', 11, 'laatupoikkeama', 4),
        ('teiden-hoito-2021-ja-uudemmat', 'C', 12, 'laatupoikkeama', 5),
        ('teiden-hoito-2021-ja-uudemmat', 'arvonvahennyssanktio', 0, 'laatupoikkeama', 1),

        ('paallystys-oletus', 'yllapidon_sakko', 3, 'urakka', 1),
        ('paallystys-oletus', 'yllapidon_muistutus', 5, 'urakka', 1),
        ('paallystys-oletus', 'yllapidon_sakko', 3, 'laatupoikkeama', 1),
        ('paallystys-oletus', 'yllapidon_muistutus', 5, 'laatupoikkeama', 1),
        ('paikkaus-oletus', 'yllapidon_sakko', 3, 'urakka', 1),
        ('paikkaus-oletus', 'yllapidon_muistutus', 5, 'urakka', 1),
        ('paikkaus-oletus', 'yllapidon_sakko', 3, 'laatupoikkeama', 1),
        ('paikkaus-oletus', 'yllapidon_muistutus', 5, 'laatupoikkeama', 1),
        ('tiemerkinta-oletus', 'yllapidon_sakko', 3, 'urakka', 1),
        ('tiemerkinta-oletus', 'yllapidon_muistutus', 5, 'urakka', 1),
        ('tiemerkinta-oletus', 'yllapidon_sakko', 3, 'laatupoikkeama', 1),
        ('tiemerkinta-oletus', 'yllapidon_muistutus', 5, 'laatupoikkeama', 1),
        ('valaistus-oletus', 'yllapidon_sakko', 3, 'urakka', 1),
        ('valaistus-oletus', 'yllapidon_muistutus', 5, 'urakka', 1),
        ('valaistus-oletus', 'yllapidon_sakko', 3, 'laatupoikkeama', 1),
        ('valaistus-oletus', 'yllapidon_muistutus', 5, 'laatupoikkeama', 1)
),
integraatio AS (
    SELECT id
      FROM kayttaja
     WHERE kayttajanimi = 'Integraatio'
)
INSERT INTO sanktio_profiili_rivi (sanktio_profiili_id, sanktio_laji_id, sanktiotyyppi_id, soveltuvuuskonteksti, jarjestys, aktiivinen, lisametatiedot, luoja, luotu, muokkaaja, muokattu)
SELECT sp.id,
       sl.id,
       st.id,
       pr.soveltuvuuskonteksti,
       pr.jarjestys,
       TRUE,
       NULL,
       (SELECT id FROM integraatio),
       CURRENT_TIMESTAMP,
       (SELECT id FROM integraatio),
       CURRENT_TIMESTAMP
  FROM profiilirivit pr
       JOIN sanktio_profiili sp
         ON sp.nimi = pr.profiili_nimi
       JOIN sanktio_laji sl
         ON sl.koodi = pr.laji_koodi
       JOIN sanktiotyyppi st
         ON st.koodi = pr.sanktiotyyppi_koodi;
