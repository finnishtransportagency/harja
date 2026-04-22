-- Sanktio-konfiguraation paikallinen testidata.
-- Ajetaan testidata.sql:sta sen jalkeen, kun sanktiotyypit on luotu.

WITH integraatio AS (
    SELECT id
      FROM kayttaja
     WHERE kayttajanimi = 'Integraatio'
)
INSERT INTO sanktio_laji (koodi, nimi, kuvaus, aktiivinen, jarjestys, luoja, luotu, muokkaaja, muokattu)
VALUES ('muistutus', 'Muistutus', 'Hoidon muistutus', TRUE, 1, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('A', 'A-ryhma (tehtavakohtainen sanktio)', 'A-ryhman sanktio', TRUE, 2, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('B', 'B-ryhma (vakava laiminlyonti)', 'B-ryhman sanktio', TRUE, 3, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('C', 'C-ryhma (maarapaivan ylitys, hallinnollinen laiminlyonti jne.)', 'C-ryhman sanktio', TRUE, 4, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('arvonvahennyssanktio', 'Arvonvahennys', 'Arvonvahennys', TRUE, 5, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('pohjavesisuolan_ylitys', 'Pohjavesialueen suolankayton ylitys', 'Pohjavesialueen suolankayton ylitys', TRUE, 6, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('talvisuolan_ylitys', 'Talvisuolan kokonaiskayton ylitys', 'Talvisuolan kokonaiskayton ylitys', TRUE, 7, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('tenttikeskiarvo-sanktio', 'Vastuuhenkilon tenttipistemaaran alentuminen', 'Tenttikeskiarvoon liittyva sanktio', TRUE, 8, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('testikeskiarvo-sanktio', 'Vastuuhenkilon testipistemaaran alentuminen', 'Testikeskiarvoon liittyva sanktio', TRUE, 9, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('vaihtosanktio', 'Vastuuhenkilon vaihto', 'Vastuuhenkilon vaihtoon liittyva sanktio', TRUE, 10, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('yllapidon_sakko', 'Sakko', 'Yllapidon sakko', TRUE, 1, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('yllapidon_muistutus', 'Muistutus', 'Yllapidon muistutus', TRUE, 2, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP);

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
       ('teiden-hoito-mhu2026', 'teiden-hoito', 1, 20, DATE '2026-10-01', NULL, TRUE, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
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

        ('teiden-hoito-mhu2026', 'muistutus', 18, 'urakka', 1),
        ('teiden-hoito-mhu2026', 'muistutus', 19, 'urakka', 2),
        ('teiden-hoito-mhu2026', 'muistutus', 20, 'urakka', 3),
        ('teiden-hoito-mhu2026', 'muistutus', 21, 'urakka', 4),
        ('teiden-hoito-mhu2026', 'muistutus', 17, 'urakka', 5),
        ('teiden-hoito-mhu2026', 'A', 18, 'urakka', 1),
        ('teiden-hoito-mhu2026', 'A', 19, 'urakka', 2),
        ('teiden-hoito-mhu2026', 'A', 20, 'urakka', 3),
        ('teiden-hoito-mhu2026', 'A', 21, 'urakka', 4),
        ('teiden-hoito-mhu2026', 'A', 17, 'urakka', 5),
        ('teiden-hoito-mhu2026', 'B', 18, 'urakka', 1),
        ('teiden-hoito-mhu2026', 'B', 19, 'urakka', 2),
        ('teiden-hoito-mhu2026', 'B', 20, 'urakka', 3),
        ('teiden-hoito-mhu2026', 'B', 21, 'urakka', 4),
        ('teiden-hoito-mhu2026', 'B', 17, 'urakka', 5),
        ('teiden-hoito-mhu2026', 'C', 18, 'urakka', 1),
        ('teiden-hoito-mhu2026', 'C', 19, 'urakka', 2),
        ('teiden-hoito-mhu2026', 'C', 20, 'urakka', 3),
        ('teiden-hoito-mhu2026', 'C', 21, 'urakka', 4),
        ('teiden-hoito-mhu2026', 'C', 17, 'urakka', 5),
        ('teiden-hoito-mhu2026', 'muistutus', 18, 'laatupoikkeama', 1),
        ('teiden-hoito-mhu2026', 'muistutus', 19, 'laatupoikkeama', 2),
        ('teiden-hoito-mhu2026', 'muistutus', 20, 'laatupoikkeama', 3),
        ('teiden-hoito-mhu2026', 'muistutus', 21, 'laatupoikkeama', 4),
        ('teiden-hoito-mhu2026', 'muistutus', 17, 'laatupoikkeama', 5),
        ('teiden-hoito-mhu2026', 'A', 18, 'laatupoikkeama', 1),
        ('teiden-hoito-mhu2026', 'A', 19, 'laatupoikkeama', 2),
        ('teiden-hoito-mhu2026', 'A', 20, 'laatupoikkeama', 3),
        ('teiden-hoito-mhu2026', 'A', 21, 'laatupoikkeama', 4),
        ('teiden-hoito-mhu2026', 'A', 17, 'laatupoikkeama', 5),
        ('teiden-hoito-mhu2026', 'B', 18, 'laatupoikkeama', 1),
        ('teiden-hoito-mhu2026', 'B', 19, 'laatupoikkeama', 2),
        ('teiden-hoito-mhu2026', 'B', 20, 'laatupoikkeama', 3),
        ('teiden-hoito-mhu2026', 'B', 21, 'laatupoikkeama', 4),
        ('teiden-hoito-mhu2026', 'B', 17, 'laatupoikkeama', 5),
        ('teiden-hoito-mhu2026', 'C', 18, 'laatupoikkeama', 1),
        ('teiden-hoito-mhu2026', 'C', 19, 'laatupoikkeama', 2),
        ('teiden-hoito-mhu2026', 'C', 20, 'laatupoikkeama', 3),
        ('teiden-hoito-mhu2026', 'C', 21, 'laatupoikkeama', 4),
        ('teiden-hoito-mhu2026', 'C', 17, 'laatupoikkeama', 5),

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
