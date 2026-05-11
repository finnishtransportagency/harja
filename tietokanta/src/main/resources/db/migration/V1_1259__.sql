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

  CREATE TABLE sanktio_profiili_laji_esitystiedot (
    id                  SERIAL PRIMARY KEY,
    sanktio_profiili_id INTEGER                  NOT NULL REFERENCES sanktio_profiili (id),
    sanktio_laji_id     INTEGER                  NOT NULL REFERENCES sanktio_laji (id),
    nimi                TEXT,
    kuvaus              TEXT,
    luoja               INTEGER                  NOT NULL REFERENCES kayttaja (id),
    luotu               TIMESTAMP                NOT NULL DEFAULT CURRENT_TIMESTAMP,
    muokkaaja           INTEGER                  NOT NULL REFERENCES kayttaja (id),
    muokattu            TIMESTAMP                NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (sanktio_profiili_id, sanktio_laji_id),
    CHECK (nimi IS NOT NULL OR kuvaus IS NOT NULL),
    CHECK (nimi IS NULL OR btrim(nimi) <> '')
  );

CREATE TABLE sanktio_profiili_rivi (
    id                   SERIAL PRIMARY KEY,
    sanktio_profiili_id  INTEGER          NOT NULL REFERENCES sanktio_profiili (id),
    sanktio_laji_id      INTEGER          NOT NULL REFERENCES sanktio_laji (id),
    sanktiotyyppi_id     INTEGER          NOT NULL REFERENCES sanktiotyyppi (id),
    soveltuvuuskonteksti TEXT             NOT NULL,
    jarjestys            INTEGER          NOT NULL,
    aktiivinen           BOOLEAN          NOT NULL DEFAULT TRUE,
    voi_puolittaa_omailmoituksella BOOLEAN NOT NULL DEFAULT FALSE,
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

CREATE INDEX sanktio_profiili_laji_esitystiedot_haku_idx
  ON sanktio_profiili_laji_esitystiedot (sanktio_profiili_id, sanktio_laji_id);

CREATE INDEX sanktio_profiili_rivi_haku_idx
    ON sanktio_profiili_rivi (sanktio_profiili_id, soveltuvuuskonteksti, aktiivinen, jarjestys);

CREATE TABLE sanktio_profiili_rivi_lukittu_summa (
    id                       SERIAL PRIMARY KEY,
    sanktio_profiili_rivi_id INTEGER       NOT NULL REFERENCES sanktio_profiili_rivi (id),
    summa_euroina            NUMERIC(12,2) NOT NULL,
    jarjestys                INTEGER       NOT NULL DEFAULT 1,
    luoja                    INTEGER       NOT NULL REFERENCES kayttaja (id),
    luotu                    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    muokkaaja                INTEGER       NOT NULL REFERENCES kayttaja (id),
    muokattu                 TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (sanktio_profiili_rivi_id, jarjestys),
    CHECK (summa_euroina >= 0)
);

CREATE INDEX sanktio_profiili_rivi_lukittu_summa_haku_idx
    ON sanktio_profiili_rivi_lukittu_summa (sanktio_profiili_rivi_id, jarjestys);

COMMENT ON TABLE sanktio_laji
                IS 'Sanktioiden lajimasterdata parametrisoitua sanktio-konfiguraatiota varten.';

COMMENT ON TABLE sanktio_profiili
                IS 'Urakka- ja hoitovuosikontekstissa resolvoitava sanktioiden konfiguraatioprofiili.';

COMMENT ON TABLE sanktio_profiili_laji_esitystiedot
                IS 'Sanktiolajin profiilikohtaiset esitystiedot, kuten nimi ja kuvaus.';

COMMENT ON TABLE sanktio_profiili_rivi
                IS 'Sallitut sanktio_laji- ja sanktiotyyppi-yhdistelmät profiileittain ja soveltuvuuskonteksteittain.';

COMMENT ON TABLE sanktio_profiili_rivi_lukittu_summa
                IS 'Sanktio-profiiliriviin sidotut lukitut euromaarat.';

-- Lisätään uusien lisäksi lokaalista puuttuvat sanktiotyypit
WITH uudet_ja_puuttuvat_sanktiotyypit (nimi, toimenpidekoodi, koodi) AS (
    VALUES ('Määräpäivän ylitys', NULL, 8),
           ('Työn tekemättä jättäminen', NULL, 9),
           ('Hallinnolliset laiminlyönnit', NULL, 10),
           ('Muu sopimuksen vastainen toiminta', NULL, 11),
           ('Asiakirjamerkintöjen paikkansa pitämättömyys', NULL, 12),
           ('Talvihoito, päätiet', 618, 13),
           ('Talvihoito, muut tiet', 618, 14),
           ('Liikenneympäristön hoito', 612, 15),
           ('Sorateiden hoito ja ylläpito', 608, 16),
           ('Muut hoitourakan tehtäväkokonaisuudet', NULL, 17),
           ('Talvihoito Ise/Is/L', 618, 18),
           ('Talvihoito Ib/Ic/K1/K2', 618, 19),
           ('Talvihoito II/III', 618, 20),
           ('Hallinnollinen laiminlyonti', NULL, 21),
           ('Tekematon sohjo-ojan ja lumivallin madallus', NULL, 22),
           ('Muu toiden tekematta jattaminen', NULL, 23)
)
INSERT INTO sanktiotyyppi (nimi, toimenpidekoodi, koodi)
SELECT nimi, toimenpidekoodi, koodi
FROM uudet_ja_puuttuvat_sanktiotyypit
ON CONFLICT (koodi) DO NOTHING;

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
       ('tyon_tekematta_jattaminen', 'Työn tekemättä jättäminen', 'MHU2026 työn tekemättä jättäminen', TRUE, 11, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('asiakirjamerkintojen_paikkansa_pitamattomyys', 'Asiakirjamerkintöjen paikkansa pitämättömyys', 'MHU2026 asiakirjamerkintöjen paikkansa pitämättömyys', TRUE, 12, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('muu_sopimuksen_vastainen_toiminta', 'Muu sopimuksen vastainen toiminta', 'MHU2026 muu sopimuksen vastainen toiminta', TRUE, 13, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('talvisuolan_kokonaiskayton_ylitys', 'Talvisuolan kokonaiskäytön ylitys', 'MHU2026 talvisuolan kokonaiskäytön ylitys', TRUE, 14, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('laskutus_yli_laskutusrajan', 'Laskutus yli laskutusrajan', 'MHU2026 laskutus yli laskutusrajan', TRUE, 15, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('laskutus_ilman_laskutuskelpoisuutta', 'Laskutus ilman laskutuskelpoisuutta', 'MHU2026 laskutus ilman laskutuskelpoisuutta', TRUE, 16, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('vastuuhenkilon_tenttipistemaara_alentuminen', 'Vastuuhenkilön tenttipistemäärän alentuminen', 'MHU2026 vastuuhenkilön tenttipistemäärän alentuminen', TRUE, 17, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('vastuuhenkilon_vaihto', 'Vastuuhenkilön vaihto', 'MHU2026 vastuuhenkilön vaihto', TRUE, 18, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('yllapidon_sakko', 'Sakko', 'Ylläpidon sakko', TRUE, 1, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('yllapidon_muistutus', 'Muistutus', 'Ylläpidon muistutus', TRUE, 2, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP);

WITH integraatio AS (
    SELECT id
      FROM kayttaja
     WHERE kayttajanimi = 'Integraatio'
)
INSERT INTO sanktio_profiili (nimi, urakkatyyppi, hoitovuosi_alku, hoitovuosi_loppu, alkupvm, loppupvm, aktiivinen, luoja, luotu, muokkaaja, muokattu)
VALUES ('hoito-legacy', 'hoito', 1, 20, DATE '1900-01-01', DATE '2021-09-30', TRUE, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('teiden-hoito-legacy', 'teiden-hoito', 1, 20, DATE '1900-01-01', DATE '2021-09-30', TRUE, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('hoito-2021-ja-uudemmat', 'hoito', 1, 20, DATE '2021-10-01', NULL, TRUE, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('teiden-hoito-2021-ja-uudemmat', 'teiden-hoito', 1, 20, DATE '2021-10-01', DATE '2025-09-30', TRUE, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('teiden-hoito-mhu2025', 'teiden-hoito', 1, 20, DATE '2025-10-01', DATE '2026-09-30', TRUE, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('teiden-hoito-mhu2026', 'teiden-hoito', 1, 20, DATE '2026-10-01', NULL, TRUE, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('paallystys-oletus', 'paallystys', 1, 20, DATE '1900-01-01', NULL, TRUE, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('paikkaus-oletus', 'paikkaus', 1, 20, DATE '1900-01-01', NULL, TRUE, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('tiemerkinta-oletus', 'tiemerkinta', 1, 20, DATE '1900-01-01', NULL, TRUE, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('valaistus-oletus', 'valaistus', 1, 20, DATE '1900-01-01', NULL, TRUE, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP);

WITH esitystiedot (profiili_nimi, laji_koodi, nimi) AS (
    VALUES ('teiden-hoito-mhu2026', 'A', 'A - Tehtäväkohtainen sanktio'),
           ('teiden-hoito-mhu2026', 'B', 'B - Vakava laiminlyönti'),
           ('teiden-hoito-mhu2026', 'C', 'C - Määräpäivän ylitys')
),
integraatio AS (
    SELECT id
      FROM kayttaja
     WHERE kayttajanimi = 'Integraatio'
)
INSERT INTO sanktio_profiili_laji_esitystiedot (sanktio_profiili_id, sanktio_laji_id, nimi, luoja, luotu, muokkaaja, muokattu)
SELECT sp.id,
       sl.id,
       e.nimi,
       (SELECT id FROM integraatio),
       CURRENT_TIMESTAMP,
       (SELECT id FROM integraatio),
       CURRENT_TIMESTAMP
FROM esitystiedot e
       JOIN sanktio_profiili sp
         ON sp.nimi = e.profiili_nimi
       JOIN sanktio_laji sl
         ON sl.koodi = e.laji_koodi;

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

        ('teiden-hoito-mhu2025', 'muistutus', 13, 'urakka', 1),
        ('teiden-hoito-mhu2025', 'muistutus', 14, 'urakka', 2),
        ('teiden-hoito-mhu2025', 'muistutus', 17, 'urakka', 3),
        ('teiden-hoito-mhu2025', 'muistutus', 10, 'urakka', 4),
        ('teiden-hoito-mhu2025', 'A', 13, 'urakka', 1),
        ('teiden-hoito-mhu2025', 'A', 14, 'urakka', 2),
        ('teiden-hoito-mhu2025', 'A', 17, 'urakka', 3),
        ('teiden-hoito-mhu2025', 'B', 13, 'urakka', 1),
        ('teiden-hoito-mhu2025', 'B', 14, 'urakka', 2),
        ('teiden-hoito-mhu2025', 'B', 17, 'urakka', 3),
        ('teiden-hoito-mhu2025', 'C', 8, 'urakka', 1),
        ('teiden-hoito-mhu2025', 'C', 9, 'urakka', 2),
        ('teiden-hoito-mhu2025', 'C', 10, 'urakka', 3),
        ('teiden-hoito-mhu2025', 'C', 11, 'urakka', 4),
        ('teiden-hoito-mhu2025', 'C', 12, 'urakka', 5),
        ('teiden-hoito-mhu2025', 'arvonvahennyssanktio', 0, 'urakka', 1),
        ('teiden-hoito-mhu2025', 'pohjavesisuolan_ylitys', 7, 'urakka', 1),
        ('teiden-hoito-mhu2025', 'talvisuolan_ylitys', 7, 'urakka', 1),
        ('teiden-hoito-mhu2025', 'tenttikeskiarvo-sanktio', 0, 'urakka', 1),
        ('teiden-hoito-mhu2025', 'testikeskiarvo-sanktio', 0, 'urakka', 1),
        ('teiden-hoito-mhu2025', 'vaihtosanktio', 0, 'urakka', 1),
        ('teiden-hoito-mhu2025', 'laskutus_yli_laskutusrajan', 0, 'urakka', 1),
        ('teiden-hoito-mhu2025', 'muistutus', 13, 'laatupoikkeama', 1),
        ('teiden-hoito-mhu2025', 'muistutus', 14, 'laatupoikkeama', 2),
        ('teiden-hoito-mhu2025', 'muistutus', 17, 'laatupoikkeama', 3),
        ('teiden-hoito-mhu2025', 'muistutus', 10, 'laatupoikkeama', 4),
        ('teiden-hoito-mhu2025', 'A', 13, 'laatupoikkeama', 1),
        ('teiden-hoito-mhu2025', 'A', 14, 'laatupoikkeama', 2),
        ('teiden-hoito-mhu2025', 'A', 17, 'laatupoikkeama', 3),
        ('teiden-hoito-mhu2025', 'B', 13, 'laatupoikkeama', 1),
        ('teiden-hoito-mhu2025', 'B', 14, 'laatupoikkeama', 2),
        ('teiden-hoito-mhu2025', 'B', 17, 'laatupoikkeama', 3),
        ('teiden-hoito-mhu2025', 'C', 8, 'laatupoikkeama', 1),
        ('teiden-hoito-mhu2025', 'C', 9, 'laatupoikkeama', 2),
        ('teiden-hoito-mhu2025', 'C', 10, 'laatupoikkeama', 3),
        ('teiden-hoito-mhu2025', 'C', 11, 'laatupoikkeama', 4),
        ('teiden-hoito-mhu2025', 'C', 12, 'laatupoikkeama', 5),
        ('teiden-hoito-mhu2025', 'arvonvahennyssanktio', 0, 'laatupoikkeama', 1),

        ('teiden-hoito-mhu2026', 'muistutus', 18, 'urakka', 1),
        ('teiden-hoito-mhu2026', 'muistutus', 19, 'urakka', 2),
        ('teiden-hoito-mhu2026', 'muistutus', 20, 'urakka', 3),
        ('teiden-hoito-mhu2026', 'muistutus', 21, 'urakka', 5),
        ('teiden-hoito-mhu2026', 'muistutus', 17, 'urakka', 4),
        ('teiden-hoito-mhu2026', 'A', 18, 'urakka', 1),
        ('teiden-hoito-mhu2026', 'A', 19, 'urakka', 2),
        ('teiden-hoito-mhu2026', 'A', 20, 'urakka', 3),
        ('teiden-hoito-mhu2026', 'A', 21, 'urakka', 5),
        ('teiden-hoito-mhu2026', 'A', 17, 'urakka', 4),
        ('teiden-hoito-mhu2026', 'B', 18, 'urakka', 1),
        ('teiden-hoito-mhu2026', 'B', 19, 'urakka', 2),
        ('teiden-hoito-mhu2026', 'B', 20, 'urakka', 3),
        ('teiden-hoito-mhu2026', 'B', 21, 'urakka', 5),
        ('teiden-hoito-mhu2026', 'B', 17, 'urakka', 4),
        ('teiden-hoito-mhu2026', 'C', 18, 'urakka', 1),
        ('teiden-hoito-mhu2026', 'C', 19, 'urakka', 2),
        ('teiden-hoito-mhu2026', 'C', 20, 'urakka', 3),
        ('teiden-hoito-mhu2026', 'C', 21, 'urakka', 5),
        ('teiden-hoito-mhu2026', 'C', 17, 'urakka', 4),
        ('teiden-hoito-mhu2026', 'tyon_tekematta_jattaminen', 22, 'urakka', 1),
        ('teiden-hoito-mhu2026', 'tyon_tekematta_jattaminen', 23, 'urakka', 2),
        ('teiden-hoito-mhu2026', 'asiakirjamerkintojen_paikkansa_pitamattomyys', 0, 'urakka', 1),
        ('teiden-hoito-mhu2026', 'muu_sopimuksen_vastainen_toiminta', 0, 'urakka', 1),
        ('teiden-hoito-mhu2026', 'pohjavesisuolan_ylitys', 0, 'urakka', 1),
        ('teiden-hoito-mhu2026', 'talvisuolan_kokonaiskayton_ylitys', 0, 'urakka', 1),
        ('teiden-hoito-mhu2026', 'laskutus_yli_laskutusrajan', 0, 'urakka', 1),
        ('teiden-hoito-mhu2026', 'laskutus_ilman_laskutuskelpoisuutta', 0, 'urakka', 1),
        ('teiden-hoito-mhu2026', 'vastuuhenkilon_tenttipistemaara_alentuminen', 0, 'urakka', 1),
        ('teiden-hoito-mhu2026', 'vastuuhenkilon_vaihto', 0, 'urakka', 1),
        ('teiden-hoito-mhu2026', 'muistutus', 18, 'laatupoikkeama', 1),
        ('teiden-hoito-mhu2026', 'muistutus', 19, 'laatupoikkeama', 2),
        ('teiden-hoito-mhu2026', 'muistutus', 20, 'laatupoikkeama', 3),
        ('teiden-hoito-mhu2026', 'muistutus', 21, 'laatupoikkeama', 5),
        ('teiden-hoito-mhu2026', 'muistutus', 17, 'laatupoikkeama', 4),
        ('teiden-hoito-mhu2026', 'A', 18, 'laatupoikkeama', 1),
        ('teiden-hoito-mhu2026', 'A', 19, 'laatupoikkeama', 2),
        ('teiden-hoito-mhu2026', 'A', 20, 'laatupoikkeama', 3),
        ('teiden-hoito-mhu2026', 'A', 21, 'laatupoikkeama', 5),
        ('teiden-hoito-mhu2026', 'A', 17, 'laatupoikkeama', 4),
        ('teiden-hoito-mhu2026', 'B', 18, 'laatupoikkeama', 1),
        ('teiden-hoito-mhu2026', 'B', 19, 'laatupoikkeama', 2),
        ('teiden-hoito-mhu2026', 'B', 20, 'laatupoikkeama', 3),
        ('teiden-hoito-mhu2026', 'B', 21, 'laatupoikkeama', 5),
        ('teiden-hoito-mhu2026', 'B', 17, 'laatupoikkeama', 4),
        ('teiden-hoito-mhu2026', 'C', 18, 'laatupoikkeama', 1),
        ('teiden-hoito-mhu2026', 'C', 19, 'laatupoikkeama', 2),
        ('teiden-hoito-mhu2026', 'C', 20, 'laatupoikkeama', 3),
        ('teiden-hoito-mhu2026', 'C', 21, 'laatupoikkeama', 5),
        ('teiden-hoito-mhu2026', 'C', 17, 'laatupoikkeama', 4),
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

WITH integraatio AS (
    SELECT id
      FROM kayttaja
     WHERE kayttajanimi = 'Integraatio'
),
kohderivi AS (
    SELECT spr.id,
           i.id AS integraatio_id
      FROM sanktio_profiili_rivi spr
           JOIN sanktio_profiili sp
             ON sp.id = spr.sanktio_profiili_id
           JOIN sanktio_laji sl
             ON sl.id = spr.sanktio_laji_id
           JOIN sanktiotyyppi st
             ON st.id = spr.sanktiotyyppi_id
           CROSS JOIN integraatio i
     WHERE sp.nimi = 'teiden-hoito-mhu2026'
       AND sl.koodi = 'A'
       AND st.koodi = 18
       AND spr.soveltuvuuskonteksti = 'laatupoikkeama'
)
UPDATE sanktio_profiili_rivi spr
SET voi_puolittaa_omailmoituksella = TRUE,
       muokkaaja                    = kohderivi.integraatio_id,
       muokattu                     = CURRENT_TIMESTAMP
FROM kohderivi
WHERE spr.id = kohderivi.id;

WITH integraatio AS (
    SELECT id
      FROM kayttaja
     WHERE kayttajanimi = 'Integraatio'
),
kohderivi AS (
    SELECT spr.id,
           i.id AS integraatio_id
      FROM sanktio_profiili_rivi spr
           JOIN sanktio_profiili sp
             ON sp.id = spr.sanktio_profiili_id
           JOIN sanktio_laji sl
             ON sl.id = spr.sanktio_laji_id
           JOIN sanktiotyyppi st
             ON st.id = spr.sanktiotyyppi_id
           CROSS JOIN integraatio i
     WHERE sp.nimi = 'teiden-hoito-mhu2026'
       AND sl.koodi = 'A'
       AND st.koodi = 18
       AND spr.soveltuvuuskonteksti = 'laatupoikkeama'
),
lukitut_summat (summa_euroina, jarjestys) AS (
    VALUES (6000.00, 1),
           (12000.00, 2)
)
INSERT INTO sanktio_profiili_rivi_lukittu_summa (sanktio_profiili_rivi_id, summa_euroina, jarjestys, luoja, luotu, muokkaaja, muokattu)
SELECT kohderivi.id,
       lukitut_summat.summa_euroina,
       lukitut_summat.jarjestys,
       kohderivi.integraatio_id,
       CURRENT_TIMESTAMP,
       kohderivi.integraatio_id,
       CURRENT_TIMESTAMP
FROM kohderivi
       CROSS JOIN lukitut_summat;
