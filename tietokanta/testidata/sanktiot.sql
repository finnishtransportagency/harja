-----------------------------------------
-- Oulun alueurakka 2014-2019
-----------------------------------------

-- Sakkoryhmä A, Sanktiotyyppi Talvihoito, päätiet

INSERT INTO laatupoikkeama (lahde, kohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu, tarkastuspiste,
                            luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka, kuvaus,
                            tr_numero, tr_alkuosa, tr_loppuosa, tr_loppuetaisyys, sijainti, tr_alkuetaisyys)
VALUES ('harja-ui'::LAHDE, 'Testikohde', 'tilaaja'::OSAPUOLI, 'puhelin'::LAATUPOIKKEAMAN_KASITTELYTAPA, '',
        'hylatty'::LAATUPOIKKEAMAN_PAATOSTYYPPI, 'Ei tässä ole mitään järkeä', 123, 1, NOW(), '2015-10-11 06:06.37',
        '2015-10-11 06:06.37', FALSE, FALSE, (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'),
        'Sanktion sisältävä laatupoikkeama 1', 1, 2, 3, 4, POINT(418237, 7207744)::GEOMETRY, 5);
INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi, tyyppi, suorasanktio,
                     luoja)
VALUES ('A'::SANKTIOLAJI, 1000, '2016-10-12 06:06.37',
        (SELECT indeksi FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'),
        (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Sanktion sisältävä laatupoikkeama 1'),
        (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Oulu Talvihoito TP 2014-2019'),
        (SELECT id
           FROM sanktiotyyppi
                -- 13, Talvihoito, päätiet
          WHERE koodi = 13), FALSE, 2);

-- Sakkoryhmä B, Sanktiotyyppi Talvihoito, päätiet

INSERT INTO laatupoikkeama (lahde, kohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu, tarkastuspiste,
                            luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka, kuvaus,
                            tr_numero, tr_alkuosa, tr_loppuosa, tr_loppuetaisyys, sijainti, tr_alkuetaisyys)
VALUES ('harja-ui'::LAHDE, 'Testikohde', 'tilaaja'::OSAPUOLI, 'puhelin'::LAATUPOIKKEAMAN_KASITTELYTAPA, '',
        'hylatty'::LAATUPOIKKEAMAN_PAATOSTYYPPI, 'Ei tässä ole mitään järkeä', 123, 1, NOW(), '2015-10-11 06:06.37',
        '2015-10-11 06:06.37', FALSE, FALSE, (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'),
        'Sanktion sisältävä laatupoikkeama 2', 1, 2, 3, 4, POINT(418237, 7207744)::GEOMETRY, 5);
INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi, tyyppi, suorasanktio,
                     luoja)
VALUES ('B'::SANKTIOLAJI, 666.666, '2016-10-12 06:06.37',
        (SELECT indeksi FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'),
        (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Sanktion sisältävä laatupoikkeama 2'),
        (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Oulu Talvihoito TP 2014-2019'),
        (SELECT id
           FROM sanktiotyyppi
                -- 13, Talvihoito, päätiet
          WHERE koodi = 13), FALSE, 2);

-- Sakkoryhmä B, Sanktiotyyppi Liikenneympäristön hoito

INSERT INTO laatupoikkeama (lahde, kohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu, tarkastuspiste,
                            luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka, kuvaus,
                            tr_numero, tr_alkuosa, tr_loppuosa, tr_loppuetaisyys, sijainti, tr_alkuetaisyys)
VALUES ('harja-ui'::LAHDE, 'Testikohde', 'tilaaja'::OSAPUOLI, 'puhelin'::LAATUPOIKKEAMAN_KASITTELYTAPA, '',
        'hylatty'::LAATUPOIKKEAMAN_PAATOSTYYPPI, 'Ei tässä ole mitään järkeä', 123, 1, NOW(), '2015-10-11 06:06.37',
        '2015-10-11 06:06.37', FALSE, FALSE, (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'),
        'Sanktion sisältävä laatupoikkeama 666', 1, 2, 3, 4, POINT(418237, 7207744)::GEOMETRY, 5);
INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi, tyyppi, suorasanktio,
                     luoja)
VALUES ('B'::SANKTIOLAJI, 100, '2016-10-12 06:06.37',
        (SELECT indeksi FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'),
        (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Sanktion sisältävä laatupoikkeama 666'),
        (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Oulu Liikenneympäristön hoito TP 2014-2019'),
        (SELECT id
           FROM sanktiotyyppi
                -- 15, Liikenneympäristön hoito
          WHERE koodi = 15), FALSE, 2);

INSERT INTO laatupoikkeama (lahde, kohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu, tarkastuspiste,
                            luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka, kuvaus,
                            tr_numero, tr_alkuosa, tr_loppuosa, tr_loppuetaisyys, sijainti, tr_alkuetaisyys)
VALUES ('harja-ui'::LAHDE, 'Testikohde', 'tilaaja'::OSAPUOLI, 'puhelin'::LAATUPOIKKEAMAN_KASITTELYTAPA, '',
        'hylatty'::LAATUPOIKKEAMAN_PAATOSTYYPPI, 'Ei tässä ole mitään järkeä', 123, 1, NOW(), '2015-10-11 06:06.37',
        '2015-10-11 06:06.37', FALSE, FALSE, (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'),
        'Sanktion sisältävä laatupoikkeama 667', 1, 2, 3, 4, POINT(418237, 7207744)::GEOMETRY, 5);
INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi, tyyppi, suorasanktio,
                     luoja)
VALUES ('B'::SANKTIOLAJI, 10, '2016-10-12 06:06.37',
        (SELECT indeksi FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'),
        (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Sanktion sisältävä laatupoikkeama 667'),
        (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Oulu Liikenneympäristön hoito TP 2014-2019'),
        (SELECT id
           FROM sanktiotyyppi
                -- 15, Liikenneympäristön hoito
          WHERE koodi = 15), FALSE, 2);

-- Sakkoryhmä B, Sanktiotyyppi Muu tuote

INSERT INTO laatupoikkeama (lahde, kohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu, tarkastuspiste,
                            luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka, kuvaus,
                            tr_numero, tr_alkuosa, tr_loppuosa, tr_loppuetaisyys, sijainti, tr_alkuetaisyys)
VALUES ('harja-ui'::LAHDE, 'Testikohde', 'tilaaja'::OSAPUOLI, 'puhelin'::LAATUPOIKKEAMAN_KASITTELYTAPA, '',
        'hylatty'::LAATUPOIKKEAMAN_PAATOSTYYPPI, 'Ei tässä ole mitään järkeä', 123, 1, NOW(), '2015-10-11 06:06.37',
        '2015-10-11 06:06.37', FALSE, FALSE, (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'),
        'Sanktion sisältävä laatupoikkeama 4', 1, 2, 3, 4, POINT(418237, 7207744)::GEOMETRY, 5);
INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi, tyyppi, suorasanktio,
                     luoja)
VALUES ('B'::SANKTIOLAJI, 1, '2016-10-12 06:06.37',
        (SELECT indeksi FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'),
        (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Sanktion sisältävä laatupoikkeama 4'),
        (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Oulu Liikenneympäristön hoito TP 2014-2019'),
        (SELECT id
           FROM sanktiotyyppi
                -- 1, Muut tuote
          WHERE koodi = 1), FALSE, 2);

-- Ryhmä C

INSERT INTO laatupoikkeama (lahde, kohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu, tarkastuspiste,
                            luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka, kuvaus,
                            tr_numero, tr_alkuosa, tr_loppuosa, tr_loppuetaisyys, sijainti, tr_alkuetaisyys)
VALUES ('harja-ui'::LAHDE, 'Testikohde', 'tilaaja'::OSAPUOLI, 'puhelin'::LAATUPOIKKEAMAN_KASITTELYTAPA, '',
        'hylatty'::LAATUPOIKKEAMAN_PAATOSTYYPPI, 'Ei tässä ole mitään järkeä', 123, 1, NOW(), '2015-10-11 06:06.37',
        '2015-10-11 06:06.37', FALSE, FALSE, (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'),
        'Sanktion sisältävä laatupoikkeama 5', 1, 2, 3, 4, POINT(418237, 7207744)::GEOMETRY, 5);
INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi, tyyppi, suorasanktio,
                     luoja)
VALUES ('C'::SANKTIOLAJI, 123, '2016-10-12 06:06.37',
        (SELECT indeksi FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'),
        (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Sanktion sisältävä laatupoikkeama 5'),
        (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Oulu Liikenneympäristön hoito TP 2014-2019'),
        (SELECT id
           FROM sanktiotyyppi
                -- 8, Määräpäivän ylitys
          WHERE koodi = 8), FALSE, 2);

-- Myöhästynyt homma Ryhmä C

INSERT INTO laatupoikkeama (lahde, kohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu, tarkastuspiste,
                            luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka, kuvaus,
                            tr_numero, tr_alkuosa, tr_loppuosa, tr_loppuetaisyys, sijainti, tr_alkuetaisyys)
VALUES ('harja-ui'::LAHDE, 'Testikohde', 'tilaaja'::OSAPUOLI, 'puhelin'::LAATUPOIKKEAMAN_KASITTELYTAPA, '',
        'hylatty'::LAATUPOIKKEAMAN_PAATOSTYYPPI, 'Ei tässä ole mitään järkeä', 123, 1, NOW(), '2019-10-11 06:06.37',
        '2019-10-11 06:06.37', FALSE, FALSE, (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'),
        'Sanktion sisältävä laatupoikkeama 5b', 1, 2, 3, 4, POINT(418237, 7207744)::GEOMETRY, 5);
INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi, tyyppi, suorasanktio,
                     luoja)
VALUES ('C'::SANKTIOLAJI, 777, '2019-10-12 06:06.37',
        (SELECT indeksi FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'),
        (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Sanktion sisältävä laatupoikkeama 5b'),
        (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Oulu Liikenneympäristön hoito TP 2014-2019'),
        (SELECT id
           FROM sanktiotyyppi
                -- 8, Määräpäivän ylitys
          WHERE koodi = 8), FALSE, 2);

-- Muistutus Talvihoito

INSERT INTO laatupoikkeama (lahde, kohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu, tarkastuspiste,
                            luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka, kuvaus,
                            tr_numero, tr_alkuosa, tr_loppuosa, tr_loppuetaisyys, sijainti, tr_alkuetaisyys)
VALUES ('harja-ui'::LAHDE, 'Testikohde', 'tilaaja'::OSAPUOLI, 'puhelin'::LAATUPOIKKEAMAN_KASITTELYTAPA, '',
        'hylatty'::LAATUPOIKKEAMAN_PAATOSTYYPPI, 'Ei tässä ole mitään järkeä', 123, 1, NOW(), '2015-10-11 06:06.37',
        '2015-10-11 06:06.37', FALSE, FALSE, (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'),
        'Sanktion sisältävä laatupoikkeama 6', 1, 2, 3, 4, POINT(418237, 7207744)::GEOMETRY, 5);
INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi, tyyppi, suorasanktio,
                     luoja)
VALUES ('muistutus'::SANKTIOLAJI, NULL, '2016-10-12 06:06.37',
        (SELECT indeksi FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'),
        (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Sanktion sisältävä laatupoikkeama 6'),
        (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Oulu Talvihoito TP 2014-2019'),
        (SELECT id
           FROM sanktiotyyppi
                -- 13, Talvihoito, päätiet
          WHERE koodi = 13), FALSE, 2);

-- Muistutus Muu

INSERT INTO laatupoikkeama (lahde, kohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu, tarkastuspiste,
                            luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka, kuvaus,
                            tr_numero, tr_alkuosa, tr_loppuosa, tr_loppuetaisyys, sijainti, tr_alkuetaisyys)
VALUES ('harja-ui'::LAHDE, 'Testikohde', 'tilaaja'::OSAPUOLI, 'puhelin'::LAATUPOIKKEAMAN_KASITTELYTAPA, '',
        'hylatty'::LAATUPOIKKEAMAN_PAATOSTYYPPI, 'Ei tässä ole mitään järkeä', 123, 1, NOW(), '2015-10-11 06:06.37',
        '2015-10-11 06:06.37', FALSE, FALSE, (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'),
        'Sanktion sisältävä laatupoikkeama 7', 1, 2, 3, 4, POINT(418237, 7207744)::GEOMETRY, 5);
INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi, tyyppi, suorasanktio,
                     luoja)
VALUES ('muistutus'::SANKTIOLAJI, NULL, '2016-10-12 06:06.37',
        (SELECT indeksi FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'),
        (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Sanktion sisältävä laatupoikkeama 7'),
        (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Oulu Liikenneympäristön hoito TP 2014-2019'),
        (SELECT id
           FROM sanktiotyyppi
                -- 1, Muut tuote
          WHERE koodi = 1), FALSE, 2);

-- Arvonvahennyssanktio
INSERT INTO laatupoikkeama (lahde, kohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu,
                            luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka, kuvaus)
VALUES ('harja-ui'::LAHDE, 'Testikohde', 'tilaaja'::OSAPUOLI, 'puhelin'::LAATUPOIKKEAMAN_KASITTELYTAPA, '',
        'sanktio'::LAATUPOIKKEAMAN_PAATOSTYYPPI, 'Vahvat perusteet', 1, NOW(), '2015-10-11 06:06.37',
        '2015-10-11 06:06.37', FALSE, FALSE, (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'),
        'Arvonvähennyksen sisältävä laatupoikkeama 123');
INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi, tyyppi, suorasanktio, luoja)
VALUES ('arvonvahennyssanktio'::SANKTIOLAJI, 1000, '2015-10-12 06:06.37', 'MAKU 2015',
        (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Arvonvähennyksen sisältävä laatupoikkeama 123'),
        (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Oulu Liikenneympäristön hoito TP 2014-2019'),
        (SELECT id FROM sanktiotyyppi WHERE koodi = 8), FALSE, 1);


-----------------------------------------
-- Pudasjärven alueurakka 2007-2012
-----------------------------------------

-- Sakkoryhmä A, Sanktiotyyppi Talvihoito, päätiet

INSERT INTO laatupoikkeama (lahde, kohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu, tarkastuspiste,
                            luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka, kuvaus,
                            tr_numero, tr_alkuosa, tr_loppuosa, tr_loppuetaisyys, sijainti, tr_alkuetaisyys)
VALUES ('harja-ui'::LAHDE, 'Testikohde', 'tilaaja'::OSAPUOLI, 'puhelin'::LAATUPOIKKEAMAN_KASITTELYTAPA, '',
        'hylatty'::LAATUPOIKKEAMAN_PAATOSTYYPPI, 'Ei tässä ole mitään järkeä', 123, 1, NOW(), '2011-10-11 06:06.37',
        '2011-10-11 06:06.37', FALSE, FALSE, (SELECT id FROM urakka WHERE nimi = 'Pudasjärven alueurakka 2007-2012'),
        'Sanktion sisältävä laatupoikkeama 100', 1, 2, 3, 4, POINT(418237, 7207744)::GEOMETRY, 5);
INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi, tyyppi, suorasanktio,
                     luoja)
VALUES ('A'::SANKTIOLAJI, 10000, '2011-10-12 06:06.37', 'MAKU 2010',
        (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Sanktion sisältävä laatupoikkeama 100'),
        (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Pudasjärvi Talvihoito TP'),
        (SELECT id
           FROM sanktiotyyppi
                -- 13, Talvihoito, päätiet
          WHERE koodi = 13), FALSE, 2);

-- Sakkoryhmä B, Sanktiotyyppi Talvihoito, päätiet

INSERT INTO laatupoikkeama (lahde, kohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu, tarkastuspiste,
                            luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka, kuvaus,
                            tr_numero, tr_alkuosa, tr_loppuosa, tr_loppuetaisyys, sijainti, tr_alkuetaisyys)
VALUES ('harja-ui'::LAHDE, 'Testikohde', 'tilaaja'::OSAPUOLI, 'puhelin'::LAATUPOIKKEAMAN_KASITTELYTAPA, '',
        'hylatty'::LAATUPOIKKEAMAN_PAATOSTYYPPI, 'Ei tässä ole mitään järkeä', 123, 1, NOW(), '2011-10-11 06:06.37',
        '2011-10-11 06:06.37', FALSE, FALSE, (SELECT id FROM urakka WHERE nimi = 'Pudasjärven alueurakka 2007-2012'),
        'Sanktion sisältävä laatupoikkeama 200', 1, 2, 3, 4, POINT(418237, 7207744)::GEOMETRY, 5);
INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi, tyyppi, suorasanktio,
                     luoja)
VALUES ('B'::SANKTIOLAJI, 6660, '2011-10-12 06:06.37', 'MAKU 2010',
        (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Sanktion sisältävä laatupoikkeama 200'),
        (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Pudasjärvi Talvihoito TP'),
        (SELECT id
           FROM sanktiotyyppi
                -- 13, Talvihoito, päätiet
          WHERE koodi = 13), FALSE, 2);

-- Sakkoryhmä B, Sanktiotyyppi Liikenneympäristön hoito

INSERT INTO laatupoikkeama (lahde, kohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu, tarkastuspiste,
                            luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka, kuvaus,
                            tr_numero, tr_alkuosa, tr_loppuosa, tr_loppuetaisyys, sijainti, tr_alkuetaisyys)
VALUES ('harja-ui'::LAHDE, 'Testikohde', 'tilaaja'::OSAPUOLI, 'puhelin'::LAATUPOIKKEAMAN_KASITTELYTAPA, '',
        'hylatty'::LAATUPOIKKEAMAN_PAATOSTYYPPI, 'Ei tässä ole mitään järkeä', 123, 1, NOW(), '2011-10-11 06:06.37',
        '2011-10-11 06:06.37', FALSE, FALSE, (SELECT id FROM urakka WHERE nimi = 'Pudasjärven alueurakka 2007-2012'),
        'Sanktion sisältävä laatupoikkeama 66600', 1, 2, 3, 4, POINT(418237, 7207744)::GEOMETRY, 5);
INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi, tyyppi, suorasanktio,
                     luoja)
VALUES ('B'::SANKTIOLAJI, 1000, '2011-10-12 06:06.37', 'MAKU 2010',
        (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Sanktion sisältävä laatupoikkeama 66600'),
        (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Pudasjärvi Liikenneympäristön hoito TP'),
        (SELECT id
           FROM sanktiotyyppi
                -- 15, Liikenneympäristön hoito
          WHERE koodi = 15), FALSE, 2);

INSERT INTO laatupoikkeama (lahde, kohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu, tarkastuspiste,
                            luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka, kuvaus,
                            tr_numero, tr_alkuosa, tr_loppuosa, tr_loppuetaisyys, sijainti, tr_alkuetaisyys)
VALUES ('harja-ui'::LAHDE, 'Testikohde', 'tilaaja'::OSAPUOLI, 'puhelin'::LAATUPOIKKEAMAN_KASITTELYTAPA, '',
        'hylatty'::LAATUPOIKKEAMAN_PAATOSTYYPPI, 'Ei tässä ole mitään järkeä', 123, 1, NOW(), '2011-10-11 06:06.37',
        '2011-10-11 06:06.37', FALSE, FALSE, (SELECT id FROM urakka WHERE nimi = 'Pudasjärven alueurakka 2007-2012'),
        'Sanktion sisältävä laatupoikkeama 66700', 1, 2, 3, 4, POINT(418237, 7207744)::GEOMETRY, 5);
INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi, tyyppi, suorasanktio,
                     luoja)
VALUES ('B'::SANKTIOLAJI, 100, '2011-10-12 06:06.37', 'MAKU 2010',
        (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Sanktion sisältävä laatupoikkeama 66700'),
        (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Pudasjärvi Liikenneympäristön hoito TP'),
        (SELECT id
           FROM sanktiotyyppi
                -- 15, Liikenneympäristön hoito
          WHERE koodi = 15), FALSE, 2);

-- Sakkoryhmä B, Sanktiotyyppi Muu tuote

INSERT INTO laatupoikkeama (lahde, kohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu, tarkastuspiste,
                            luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka, kuvaus,
                            tr_numero, tr_alkuosa, tr_loppuosa, tr_loppuetaisyys, sijainti, tr_alkuetaisyys)
VALUES ('harja-ui'::LAHDE, 'Testikohde', 'tilaaja'::OSAPUOLI, 'puhelin'::LAATUPOIKKEAMAN_KASITTELYTAPA, '',
        'hylatty'::LAATUPOIKKEAMAN_PAATOSTYYPPI, 'Ei tässä ole mitään järkeä', 123, 1, NOW(), '2011-10-11 06:06.37',
        '2011-10-11 06:06.37', FALSE, FALSE, (SELECT id FROM urakka WHERE nimi = 'Pudasjärven alueurakka 2007-2012'),
        'Sanktion sisältävä laatupoikkeama 400', 1, 2, 3, 4, POINT(418237, 7207744)::GEOMETRY, 5);
INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi, tyyppi, suorasanktio,
                     luoja)
VALUES ('B'::SANKTIOLAJI, 10, '2011-10-12 06:06.37', 'MAKU 2010',
        (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Sanktion sisältävä laatupoikkeama 400'),
        (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Pudasjärvi Liikenneympäristön hoito TP'),
        (SELECT id
           FROM sanktiotyyppi
                -- 1, Muut tuote
          WHERE koodi = 1), FALSE, 2);

-- Ryhmä C

INSERT INTO laatupoikkeama (lahde, kohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu, tarkastuspiste,
                            luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka, kuvaus,
                            tr_numero, tr_alkuosa, tr_loppuosa, tr_loppuetaisyys, sijainti, tr_alkuetaisyys)
VALUES ('harja-ui'::LAHDE, 'Testikohde', 'tilaaja'::OSAPUOLI, 'puhelin'::LAATUPOIKKEAMAN_KASITTELYTAPA, '',
        'hylatty'::LAATUPOIKKEAMAN_PAATOSTYYPPI, 'Ei tässä ole mitään järkeä', 123, 1, NOW(), '2011-10-11 06:06.37',
        '2011-10-11 06:06.37', FALSE, FALSE, (SELECT id FROM urakka WHERE nimi = 'Pudasjärven alueurakka 2007-2012'),
        'Sanktion sisältävä laatupoikkeama 500', 1, 2, 3, 4, POINT(418237, 7207744)::GEOMETRY, 5);
INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi, tyyppi, suorasanktio,
                     luoja)
VALUES ('C'::SANKTIOLAJI, 1230, '2011-10-12 06:06.37', 'MAKU 2010',
        (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Sanktion sisältävä laatupoikkeama 500'),
        (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Pudasjärvi Liikenneympäristön hoito TP'),
        (SELECT id
           FROM sanktiotyyppi
                -- 8, Määräpäivän ylitys
          WHERE koodi = 8), FALSE, 2);

-- Muistutus Talvihoito

INSERT INTO laatupoikkeama (lahde, kohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu, tarkastuspiste,
                            luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka, kuvaus,
                            tr_numero, tr_alkuosa, tr_loppuosa, tr_loppuetaisyys, sijainti, tr_alkuetaisyys)
VALUES ('harja-ui'::LAHDE, 'Testikohde', 'tilaaja'::OSAPUOLI, 'puhelin'::LAATUPOIKKEAMAN_KASITTELYTAPA, '',
        'hylatty'::LAATUPOIKKEAMAN_PAATOSTYYPPI, 'Ei tässä ole mitään järkeä', 123, 1, NOW(), '2011-10-11 06:06.37',
        '2011-10-11 06:06.37', FALSE, FALSE, (SELECT id FROM urakka WHERE nimi = 'Pudasjärven alueurakka 2007-2012'),
        'Sanktion sisältävä laatupoikkeama 600', 1, 2, 3, 4, POINT(418237, 7207744)::GEOMETRY, 5);
INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi, tyyppi, suorasanktio,
                     luoja)
VALUES ('muistutus'::SANKTIOLAJI, NULL, '2011-10-12 06:06.37', 'MAKU 2010',
        (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Sanktion sisältävä laatupoikkeama 600'),
        (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Pudasjärvi Talvihoito TP'),
        (SELECT id
           FROM sanktiotyyppi
                -- 13, Talvihoito, päätiet
          WHERE koodi = 13), FALSE, 2);

-- Muistutus Muu

INSERT INTO laatupoikkeama (lahde, kohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu, tarkastuspiste,
                            luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka, kuvaus,
                            tr_numero, tr_alkuosa, tr_loppuosa, tr_loppuetaisyys, sijainti, tr_alkuetaisyys)
VALUES ('harja-ui'::LAHDE, 'Testikohde', 'tilaaja'::OSAPUOLI, 'puhelin'::LAATUPOIKKEAMAN_KASITTELYTAPA, '',
        'hylatty'::LAATUPOIKKEAMAN_PAATOSTYYPPI, 'Ei tässä ole mitään järkeä', 123, 1, NOW(), '2011-10-11 06:06.37',
        '2011-10-11 06:06.37', FALSE, FALSE, (SELECT id FROM urakka WHERE nimi = 'Pudasjärven alueurakka 2007-2012'),
        'Sanktion sisältävä laatupoikkeama 700', 1, 2, 3, 4, POINT(418237, 7207744)::GEOMETRY, 5);
INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi, tyyppi, suorasanktio,
                     luoja)
VALUES ('muistutus'::SANKTIOLAJI, NULL, '2011-10-12 06:06.37', 'MAKU 2010',
        (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Sanktion sisältävä laatupoikkeama 700'),
        (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Pudasjärvi Liikenneympäristön hoito TP'),
        (SELECT id
           FROM sanktiotyyppi
                -- 1, Muut tuote
          WHERE koodi = 1), FALSE, 2);

-----------------------------------------
-- Vantaan alueurakka 2009-2019
-----------------------------------------

-- Sakkoryhmä A, Sanktiotyyppi Talvihoito, päätiet

INSERT INTO laatupoikkeama (lahde, kohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu, tarkastuspiste,
                            luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka, kuvaus,
                            tr_numero, tr_alkuosa, tr_loppuosa, tr_loppuetaisyys, sijainti, tr_alkuetaisyys)
VALUES ('harja-ui'::LAHDE, 'Testikohde', 'tilaaja'::OSAPUOLI, 'puhelin'::LAATUPOIKKEAMAN_KASITTELYTAPA, '',
        'hylatty'::LAATUPOIKKEAMAN_PAATOSTYYPPI, 'Ei tässä ole mitään järkeä', 123, 1, NOW(), '2015-10-11 06:06.37',
        '2015-10-11 06:06.37', FALSE, FALSE, (SELECT id FROM urakka WHERE nimi = 'Vantaan alueurakka 2009-2019'),
        'Sanktion sisältävä laatupoikkeama 999', 1, 2, 3, 4, POINT(418237, 7207744)::GEOMETRY, 5);
INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi, tyyppi, suorasanktio,
                     luoja)
VALUES ('A'::SANKTIOLAJI, 2.5, '2016-10-12 06:06.37', 'MAKU 2010',
        (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Sanktion sisältävä laatupoikkeama 999'),
        (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Vantaa Talvihoito TP 2014-2019'),
        (SELECT id
           FROM sanktiotyyppi
                -- 13, Talvihoito, päätiet
          WHERE koodi = 13), FALSE, 2);

-- Sakkoryhmä B, Sanktiotyyppi Talvihoito, päätiet

INSERT INTO laatupoikkeama (lahde, kohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu, tarkastuspiste,
                            luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka, kuvaus,
                            tr_numero, tr_alkuosa, tr_loppuosa, tr_loppuetaisyys, sijainti, tr_alkuetaisyys)
VALUES ('harja-ui'::LAHDE, 'Testikohde', 'tilaaja'::OSAPUOLI, 'puhelin'::LAATUPOIKKEAMAN_KASITTELYTAPA, '',
        'hylatty'::LAATUPOIKKEAMAN_PAATOSTYYPPI, 'Ei tässä ole mitään järkeä', 123, 1, NOW(), '2015-10-11 06:06.37',
        '2015-10-11 06:06.37', FALSE, FALSE, (SELECT id FROM urakka WHERE nimi = 'Vantaan alueurakka 2009-2019'),
        'Sanktion sisältävä laatupoikkeama 9990', 1, 2, 3, 4, POINT(418237, 7207744)::GEOMETRY, 5);
INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi, tyyppi, suorasanktio,
                     luoja)
VALUES ('B'::SANKTIOLAJI, 2, '2016-10-12 06:06.37', 'MAKU 2010',
        (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Sanktion sisältävä laatupoikkeama 9990'),
        (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Vantaa Talvihoito TP 2014-2019'),
        (SELECT id
           FROM sanktiotyyppi
                -- 13, Talvihoito, päätiet
          WHERE koodi = 13), FALSE, 2);

-----------------------------------------
-- Espoon alueurakka 2014-2019
-----------------------------------------

-- Sakkoryhmä A, Sanktiotyyppi Talvihoito, päätiet

INSERT INTO laatupoikkeama (lahde, kohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu, tarkastuspiste,
                            luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka, kuvaus,
                            tr_numero, tr_alkuosa, tr_loppuosa, tr_loppuetaisyys, sijainti, tr_alkuetaisyys)
VALUES ('harja-ui'::LAHDE, 'Testikohde', 'tilaaja'::OSAPUOLI, 'puhelin'::LAATUPOIKKEAMAN_KASITTELYTAPA, '',
        'hylatty'::LAATUPOIKKEAMAN_PAATOSTYYPPI, 'Ei tässä ole mitään järkeä', 123, 1, NOW(), '2015-10-11 06:06.37',
        '2015-10-11 06:06.37', FALSE, FALSE, (SELECT id FROM urakka WHERE nimi = 'Espoon alueurakka 2014-2019'),
        'Sanktion sisältävä laatupoikkeama 6767', 1, 2, 3, 4, POINT(418237, 7207744)::GEOMETRY, 5);
INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi, tyyppi, suorasanktio,
                     luoja)
VALUES ('A'::SANKTIOLAJI, 1, '2016-10-12 06:06.37', 'MAKU 2010',
        (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Sanktion sisältävä laatupoikkeama 6767'),
        (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Espoo Talvihoito TP 2014-2019'),
        (SELECT id
           FROM sanktiotyyppi
                -- 13, Talvihoito, päätiet
          WHERE koodi = 13), FALSE, 2);

-- Sakkoryhmä B, Sanktiotyyppi Talvihoito, päätiet

INSERT INTO laatupoikkeama (lahde, kohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu, tarkastuspiste,
                            luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka, kuvaus,
                            tr_numero, tr_alkuosa, tr_loppuosa, tr_loppuetaisyys, sijainti, tr_alkuetaisyys)
VALUES ('harja-ui'::LAHDE, 'Testikohde', 'tilaaja'::OSAPUOLI, 'puhelin'::LAATUPOIKKEAMAN_KASITTELYTAPA, '',
        'hylatty'::LAATUPOIKKEAMAN_PAATOSTYYPPI, 'Ei tässä ole mitään järkeä', 123, 1, NOW(), '2015-10-11 06:06.37',
        '2015-10-11 06:06.37', FALSE, FALSE, (SELECT id FROM urakka WHERE nimi = 'Espoon alueurakka 2014-2019'),
        'Sanktion sisältävä laatupoikkeama 3424', 1, 2, 3, 4, POINT(418237, 7207744)::GEOMETRY, 5);
INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi, tyyppi, suorasanktio,
                     luoja)
VALUES ('B'::SANKTIOLAJI, 1.5, '2016-10-12 06:06.37', 'MAKU 2010',
        (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Sanktion sisältävä laatupoikkeama 3424'),
        (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Espoo Talvihoito TP 2014-2019'),
        (SELECT id
           FROM sanktiotyyppi
                -- 13, Talvihoito, päätiet
          WHERE koodi = 13), FALSE, 2);

-----------------------------------------
-- Ylläpito
-----------------------------------------

-- Ylläpitourakkaan kohteeseen liitetty sanktio
INSERT INTO laatupoikkeama (lahde, yllapitokohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu,
                            tarkastuspiste, luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu,
                            urakka, kuvaus)
VALUES ('harja-ui'::LAHDE, (SELECT id FROM yllapitokohde WHERE nimi = 'Oulaisten ohitusramppi'), 'tilaaja'::OSAPUOLI,
        'puhelin'::LAATUPOIKKEAMAN_KASITTELYTAPA, '', 'hylatty'::LAATUPOIKKEAMAN_PAATOSTYYPPI,
        'Ei tässä ole mitään järkeä', 123, 1, NOW(), '2017-01-2 06:06.37', '2017-01-04 16:06.37', FALSE, FALSE,
        (SELECT id FROM urakka WHERE nimi = 'Muhoksen päällystysurakka'), 'Ylläpitokohteeseen linkattu suorasanktio');
INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi, tyyppi, suorasanktio,
                     luoja)
VALUES ('yllapidon_sakko'::SANKTIOLAJI, 1500, '2017-01-4 06:06.37', NULL,
        (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Ylläpitokohteeseen linkattu suorasanktio'),
        (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Muhos Ajoradan päällyste TP'),
        (SELECT id
           FROM sanktiotyyppi
                -- 3, Ylläpidon sakko
          WHERE koodi = 3), TRUE, 2);

INSERT INTO laatupoikkeama (lahde, yllapitokohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu,
                            tarkastuspiste, luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu,
                            urakka, kuvaus)
VALUES ('harja-ui'::LAHDE, (SELECT id FROM yllapitokohde WHERE nimi = 'Oulaisten ohitusramppi'), 'tilaaja'::OSAPUOLI,
        'puhelin'::LAATUPOIKKEAMAN_KASITTELYTAPA, '', 'hylatty'::LAATUPOIKKEAMAN_PAATOSTYYPPI,
        'Ei tässä ole mitään järkeä', 123, 1, NOW(), '2017-01-3 12:06.37', '2017-01-05 13:06.37', FALSE, FALSE,
        (SELECT id FROM urakka WHERE nimi = 'Muhoksen päällystysurakka'), 'Ylläpitokohteeseen linkattu suorasanktio 2');
INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi, tyyppi, suorasanktio,
                     luoja)
VALUES ('yllapidon_sakko'::SANKTIOLAJI, 1500, '2017-01-5 06:06.37', NULL,
        (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Ylläpitokohteeseen linkattu suorasanktio 2'),
        (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Muhos Ajoradan päällyste TP'),
        (SELECT id
           FROM sanktiotyyppi
                -- 3, Ylläpidon sakko
          WHERE koodi = 3), TRUE, 2);

INSERT INTO laatupoikkeama (lahde, yllapitokohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu,
                            tarkastuspiste, luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu,
                            urakka, kuvaus)
VALUES ('harja-ui'::LAHDE, (SELECT id FROM yllapitokohde WHERE nimi = 'Leppäjärven ramppi'), 'tilaaja'::OSAPUOLI,
        'puhelin'::LAATUPOIKKEAMAN_KASITTELYTAPA, '', 'hylatty'::LAATUPOIKKEAMAN_PAATOSTYYPPI,
        'Ei tässä ole mitään järkeä', 123, 1, NOW(), '2017-01-3 12:06.37', '2017-01-05 13:06.37', FALSE, FALSE,
        (SELECT id FROM urakka WHERE nimi = 'Muhoksen päällystysurakka'), 'Ylläpitokohteeseen linkattu suorasanktio 3');
INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi, tyyppi, suorasanktio,
                     luoja)
VALUES ('yllapidon_bonus'::SANKTIOLAJI, -2000, '2017-01-6 06:06.37', NULL,
        (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Ylläpitokohteeseen linkattu suorasanktio 3'),
        (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Muhos Ajoradan päällyste TP'),
        (SELECT id
           FROM sanktiotyyppi
                -- 4, Ylläpidon bonus
          WHERE koodi = 4), TRUE, 2);

INSERT INTO laatupoikkeama (lahde, yllapitokohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu,
                            tarkastuspiste, luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu,
                            urakka, kuvaus)
VALUES ('harja-ui'::LAHDE, (SELECT id FROM yllapitokohde WHERE nimi = 'Nakkilan ramppi'), 'tilaaja'::OSAPUOLI,
        'puhelin'::LAATUPOIKKEAMAN_KASITTELYTAPA, '', 'hylatty'::LAATUPOIKKEAMAN_PAATOSTYYPPI,
        'Ei tässä ole mitään järkeä', 123, 1, NOW(), '2017-01-12 16:06.37', '2017-01-14 16:06.37', FALSE, FALSE,
        (SELECT id FROM urakka WHERE nimi = 'Muhoksen päällystysurakka'),
        'Ylläpitokohteeseen linkattu suorasanktio muistutus 1');
INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi, tyyppi, suorasanktio,
                     luoja)
VALUES ('yllapidon_muistutus'::SANKTIOLAJI, NULL, '2017-01-15 03:06.37', NULL,
        (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Ylläpitokohteeseen linkattu suorasanktio muistutus 1'),
        (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Muhos Ajoradan päällyste TP'),
        (SELECT id
           FROM sanktiotyyppi
                -- 5, Ylläpidon muistutus
          WHERE koodi = 5), TRUE, 2);

INSERT INTO laatupoikkeama (lahde, yllapitokohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu,
                            tarkastuspiste, luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu,
                            urakka, kuvaus)
VALUES ('harja-ui'::LAHDE, (SELECT id FROM yllapitokohde WHERE nimi = 'Oulaisten ohitusramppi'), 'tilaaja'::OSAPUOLI,
        'puhelin'::LAATUPOIKKEAMAN_KASITTELYTAPA, '', 'hylatty'::LAATUPOIKKEAMAN_PAATOSTYYPPI,
        'Ei tässä ole mitään järkeä', 123, 1, NOW(), '2017-01-12 16:06.37', '2017-01-14 16:06.37', FALSE, FALSE,
        (SELECT id FROM urakka WHERE nimi = 'Muhoksen päällystysurakka'),
        'Ylläpitokohteeseen linkattu suorasanktio muistutus 2');
INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi, tyyppi, suorasanktio,
                     luoja)
VALUES ('yllapidon_muistutus'::SANKTIOLAJI, NULL, '2017-01-16 06:06.37', NULL,
        (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Ylläpitokohteeseen linkattu suorasanktio muistutus 2'),
        (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Muhos Ajoradan päällyste TP'),
        (SELECT id
           FROM sanktiotyyppi
                -- 5, Ylläpidon muistutus
          WHERE koodi = 5), TRUE, 2);

-- Ylläpitourakan sanktioita ilman ylläpitokohdetta
INSERT INTO laatupoikkeama (lahde, yllapitokohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu,
                            tarkastuspiste, luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu,
                            urakka, kuvaus)
VALUES ('harja-ui'::LAHDE, NULL, 'tilaaja'::OSAPUOLI, 'puhelin'::LAATUPOIKKEAMAN_KASITTELYTAPA, '',
        'hylatty'::LAATUPOIKKEAMAN_PAATOSTYYPPI, 'Ei tässä ole mitään järkeä', 123, 1, NOW(), '2017-01-2 06:06.37',
        '2017-01-04 16:06.37', FALSE, FALSE, (SELECT id FROM urakka WHERE nimi = 'Muhoksen päällystysurakka'),
        'Ylläpitokohteeton suorasanktio');
INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi, tyyppi, suorasanktio,
                     luoja)
VALUES ('yllapidon_sakko'::SANKTIOLAJI, 1500, '2017-01-4 06:06.37', NULL,
        (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Ylläpitokohteeton suorasanktio'),
        (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Muhos Ajoradan päällyste TP'),
        (SELECT id
           FROM sanktiotyyppi
                -- 3, Ylläpidon sakko
          WHERE koodi = 3), TRUE, 2);


INSERT INTO laatupoikkeama (lahde, yllapitokohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu,
                            tarkastuspiste, luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu,
                            urakka, kuvaus)
VALUES ('harja-ui'::LAHDE, NULL, 'tilaaja'::OSAPUOLI, 'puhelin'::LAATUPOIKKEAMAN_KASITTELYTAPA, '',
        'hylatty'::LAATUPOIKKEAMAN_PAATOSTYYPPI, 'Ei tässä ole mitään järkeä', 123, 1, NOW(), '2017-01-3 12:06.37',
        '2017-01-05 13:06.37', FALSE, FALSE, (SELECT id FROM urakka WHERE nimi = 'Porintien päällystysurakka'),
        'Ylläpitokohteeseeton suorasanktio 4');
INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi, tyyppi, suorasanktio,
                     luoja)
VALUES ('yllapidon_sakko'::SANKTIOLAJI, 1500, '2017-01-5 06:06.37', NULL,
        (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Ylläpitokohteeseeton suorasanktio 4'),
        (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Porintien Ajoradan päällyste TP'),
        (SELECT id
           FROM sanktiotyyppi
                -- 3, Ylläpidon sakko
          WHERE koodi = 3), TRUE, 2);

INSERT INTO laatupoikkeama (lahde, yllapitokohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu,
                            tarkastuspiste, luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu,
                            urakka, kuvaus)
VALUES ('harja-ui'::LAHDE, NULL, 'tilaaja'::OSAPUOLI, 'puhelin'::LAATUPOIKKEAMAN_KASITTELYTAPA, '',
        'hylatty'::LAATUPOIKKEAMAN_PAATOSTYYPPI, 'Ei tässä ole mitään järkeä', 123, 1, NOW(), '2017-01-3 12:06.37',
        '2017-01-05 13:06.37', FALSE, FALSE, (SELECT id FROM urakka WHERE nimi = 'Porintien päällystysurakka'),
        'Ylläpitokohteeseeton suorasanktio 5');
INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi, tyyppi, suorasanktio,
                     luoja)
VALUES ('yllapidon_sakko'::SANKTIOLAJI, 1500, '2017-01-5 06:06.37', NULL,
        (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Ylläpitokohteeseeton suorasanktio 5'),
        (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Porintien Ajoradan päällyste TP'),
        (SELECT id
           FROM sanktiotyyppi
                -- 3, Ylläpidon sakko
          WHERE koodi = 3), TRUE, 2);

INSERT INTO laatupoikkeama (kohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu, tarkastuspiste, luoja,
                            luotu, muokkaaja, muokattu, poistettu, aika, kasittelyaika, selvitys_pyydetty,
                            selvitys_annettu, urakka, kuvaus, tr_numero, tr_alkuosa, tr_loppuosa, tr_loppuetaisyys,
                            sijainti, tr_alkuetaisyys, ulkoinen_id, lahde, yllapitokohde, "sisaltaa-poikkeamaraportin?")
VALUES (NULL, 'tilaaja', 'kommentit', NULL, 'sanktio', 'Rikkuri', NULL,
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'jvh'), '2022-06-27 08:42:19.598631',
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'jvh'), '2022-06-27 08:42:19.598631', FALSE,
        '2022-06-27 08:42:04.000000', '2022-06-27 08:42:06.000000', FALSE, FALSE,
        (SELECT id FROM urakka WHERE nimi = 'Utajärven päällystysurakka'), 'kohdistamaton sanktio', NULL, NULL, NULL,
        NULL, NULL, NULL, NULL, 'harja-ui', NULL, FALSE);
INSERT INTO sanktio (maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi, tyyppi, suorasanktio, muokattu,
                     muokkaaja, luotu, luoja, ulkoinen_id, vakiofraasi, sakkoryhma, poistettu)
VALUES (1300, '2022-06-27', NULL, (SELECT id FROM laatupoikkeama WHERE kuvaus = 'kohdistamaton sanktio'), 97, 4, TRUE,
        NULL, NULL, '2022-06-27 08:42:19.598631', 3, NULL, NULL, 'yllapidon_sakko', FALSE);

-----------------------------------------
-- Vesiväylät
-----------------------------------------

INSERT INTO laatupoikkeama (lahde, kohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu, tarkastuspiste,
                            luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka, kuvaus)
VALUES ('harja-ui'::LAHDE, 'Testikohde', 'tilaaja'::OSAPUOLI, 'puhelin'::LAATUPOIKKEAMAN_KASITTELYTAPA, '',
        'hylatty'::LAATUPOIKKEAMAN_PAATOSTYYPPI, 'Ei tässä ole mitään järkeä', 123, 1, NOW(), '2015-10-11 06:06.37',
        '2017-06-20 06:06.37', FALSE, FALSE,
        (SELECT id FROM urakka WHERE nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
        'Virtuaalinen vesiväylien laatupoikkeama 1, koska tietomalli');
INSERT INTO sanktio (laatupoikkeama, sakkoryhma, maara, perintapvm, toimenpideinstanssi, tyyppi, suorasanktio, luoja)
VALUES ((SELECT id FROM laatupoikkeama WHERE kuvaus = 'Virtuaalinen vesiväylien laatupoikkeama 1, koska tietomalli'),
        'vesivayla_sakko'::SANKTIOLAJI, 30, '2017-6-20 06:06.37', (SELECT id
                                                                     FROM toimenpideinstanssi
                                                                    WHERE nimi =
                                                                          'Väylänhoito, Itäinen Suomenlahti, sopimuksen kok.hintaiset työt, rann kmrk, TP'),
        (SELECT id
           FROM sanktiotyyppi
                -- 6, Vesiväylän sakko
          WHERE koodi = 6), TRUE, 2);

INSERT INTO laatupoikkeama (lahde, kohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu, tarkastuspiste,
                            luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka, kuvaus)
VALUES ('harja-ui'::LAHDE, 'Testikohde', 'tilaaja'::OSAPUOLI, 'puhelin'::LAATUPOIKKEAMAN_KASITTELYTAPA, '',
        'hylatty'::LAATUPOIKKEAMAN_PAATOSTYYPPI, 'Ei tässä ole mitään järkeä', 123, 1, NOW(), '2015-10-11 06:06.37',
        '2017-06-20 06:06.37', FALSE, FALSE,
        (SELECT id FROM urakka WHERE nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
        'Virtuaalinen vesiväylien laatupoikkeama 2, koska tietomalli');
INSERT INTO sanktio (laatupoikkeama, sakkoryhma, maara, perintapvm, toimenpideinstanssi, tyyppi, suorasanktio, luoja)
VALUES ((SELECT id FROM laatupoikkeama WHERE kuvaus = 'Virtuaalinen vesiväylien laatupoikkeama 2, koska tietomalli'),
        'vesivayla_sakko'::SANKTIOLAJI, 30, '2017-6-20 01:06.37', (SELECT id
                                                                     FROM toimenpideinstanssi
                                                                    WHERE nimi =
                                                                          'Väylänhoito, Itäinen Suomenlahti, sopimuksen kok.hintaiset työt, rann kmrk, TP'),
        (SELECT id
           FROM sanktiotyyppi
                -- 6, Vesiväylän sakko
          WHERE koodi = 6), TRUE, 2);
INSERT INTO laatupoikkeama (lahde, kohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu, tarkastuspiste,
                            luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka, kuvaus)
VALUES ('harja-ui'::LAHDE, 'Testikohde', 'tilaaja'::OSAPUOLI, 'puhelin'::LAATUPOIKKEAMAN_KASITTELYTAPA, '',
        'hylatty'::LAATUPOIKKEAMAN_PAATOSTYYPPI, 'Ei tässä ole mitään järkeä', 123, 1, NOW(), '2015-10-11 06:06.37',
        '2017-06-20 06:06.37', FALSE, FALSE,
        (SELECT id FROM urakka WHERE nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
        'Virtuaalinen vesiväylien laatupoikkeama 3, koska tietomalli');
INSERT INTO sanktio (laatupoikkeama, sakkoryhma, maara, perintapvm, toimenpideinstanssi, tyyppi, suorasanktio, luoja)
VALUES ((SELECT id FROM laatupoikkeama WHERE kuvaus = 'Virtuaalinen vesiväylien laatupoikkeama 3, koska tietomalli'),
        'vesivayla_sakko'::SANKTIOLAJI, 666, '2017-6-20 06:06.37', (SELECT id
                                                                      FROM toimenpideinstanssi
                                                                     WHERE nimi =
                                                                           'Väylänhoito, Itäinen Suomenlahti, sopimuksen kok.hintaiset työt, rann kmrk, TP'),
        (SELECT id
           FROM sanktiotyyppi
                -- 6, Vesiväylän sakko
          WHERE koodi = 6), TRUE, 2);
INSERT INTO sanktio (laatupoikkeama, sakkoryhma, maara, perintapvm, toimenpideinstanssi, tyyppi, suorasanktio, luoja)
VALUES ((SELECT id FROM laatupoikkeama WHERE kuvaus = 'Virtuaalinen vesiväylien laatupoikkeama 3, koska tietomalli'),
        'vesivayla_sakko'::SANKTIOLAJI, 666, '2017-6-20 06:06.37', (SELECT id
                                                                      FROM toimenpideinstanssi
                                                                     WHERE nimi =
                                                                           'Väylänhoito, Itäinen Suomenlahti, sopimuksen kok.hintaiset työt, rann kmrk, TP'),
        (SELECT id
           FROM sanktiotyyppi
                -- 6, Vesiväylän sakko
          WHERE koodi = 6), TRUE, 2);


-- MHU urakkaan joka alkanut 2021 sanktio
INSERT INTO laatupoikkeama (lahde, kohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu, tarkastuspiste,
                            luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka, kuvaus,
                            tr_numero, tr_alkuosa, tr_loppuosa, tr_loppuetaisyys, sijainti, tr_alkuetaisyys)
VALUES ('harja-ui'::LAHDE, 'Testikohde', 'tilaaja'::OSAPUOLI, 'puhelin'::LAATUPOIKKEAMAN_KASITTELYTAPA, '',
        'hylatty'::LAATUPOIKKEAMAN_PAATOSTYYPPI, 'Ei tässä ole mitään järkeä', 123, 1, NOW(), '2022-09-07 06:06.37',
        '2022-09-08 06:06.37', FALSE, FALSE, (SELECT id FROM urakka WHERE nimi = 'Iin MHU 2021-2026'),
        'Sanktion sisältävä laatupoikkeama Iin MHU', 1, 2, 3, 4, POINT(418237, 7207744)::GEOMETRY, 5);
INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi, tyyppi, suorasanktio,
                     luoja)
VALUES ('A'::SANKTIOLAJI, 1000, '2022-09-09'::DATE, (SELECT indeksi FROM urakka WHERE nimi = 'Iin MHU 2021-2026'),
        (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Sanktion sisältävä laatupoikkeama Iin MHU'),
        (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Iin MHU 2021-2026 Talvihoito TP'),
        (SELECT id
           FROM sanktiotyyppi
                -- 13, Talvihoito, päätiet
          WHERE koodi = 13), FALSE, 2);


-- MHU urakkaan joka alkanut 2019 sanktio (Sisältää testiesimerkkejä "vanhoista kirjauksista")
INSERT INTO laatupoikkeama (lahde, kohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu, tarkastuspiste,
                            luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka, kuvaus,
                            tr_numero, tr_alkuosa, tr_loppuosa, tr_loppuetaisyys, sijainti, tr_alkuetaisyys)
VALUES ('harja-ui'::LAHDE, 'Testikohde', 'tilaaja'::OSAPUOLI, 'puhelin'::LAATUPOIKKEAMAN_KASITTELYTAPA, '',
        'sanktio'::LAATUPOIKKEAMAN_PAATOSTYYPPI, 'Talvihoito mennyt pahasti loskaksi', 123, 1, NOW(), '2022-02-22 06:06.37',
        '2022-09-08 06:06.37', FALSE, FALSE, (SELECT id FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'),
        'Sanktion sisältävä laatupoikkeama Oulun MHU 2019-2024', 1, 2, 3, 4, POINT(418237, 7207744)::GEOMETRY, 5);
INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi, tyyppi, suorasanktio,
                     luoja)
VALUES ('A'::SANKTIOLAJI, 1000, '2022-02-22'::DATE, (SELECT indeksi FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'),
        (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Sanktion sisältävä laatupoikkeama Oulun MHU 2019-2024'),
        (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Oulu MHU Talvihoito TP'),
        (SELECT id
           FROM sanktiotyyppi
                -- 1, Muu tuote
                -- Tämä (koodi = 1) on vanha poistetuksi merkitty sanktiotyyppi. Vanhojen sanktiokirjausten sanktiotyyppien
                -- tulisi näkyä oikein UI:n puolella, mutta poistettuja sanktiotyyppejä ei saisi enää pystyä valitsemaan
                -- uuteen sanktioon.
          WHERE koodi = 1), FALSE, 2);

-----------------------------------------
-- Kanavat
-----------------------------------------
INSERT INTO laatupoikkeama (lahde, kohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu, tarkastuspiste,
                            luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka, kuvaus)
VALUES ('harja-ui'::LAHDE, 'Testikohde', 'tilaaja'::OSAPUOLI, 'puhelin'::LAATUPOIKKEAMAN_KASITTELYTAPA, '',
        'hylatty'::LAATUPOIKKEAMAN_PAATOSTYYPPI, 'Ei tässä ole mitään järkeä', 123, 1, NOW(), '2017-10-11 06:06.37',
        '2017-10-19 06:06.37', FALSE, FALSE, (SELECT id FROM urakka WHERE nimi = 'Saimaan kanava'),
        'Virtuaalinen kanavien laatupoikkeama 1, koska tietomalli');
INSERT INTO sanktio (laatupoikkeama, sakkoryhma, maara, perintapvm, toimenpideinstanssi, tyyppi, suorasanktio, luoja)
VALUES ((SELECT id FROM laatupoikkeama WHERE kuvaus = 'Virtuaalinen kanavien laatupoikkeama 1, koska tietomalli'),
        'vesivayla_sakko'::SANKTIOLAJI, 5000, '2017-10-20 06:06.37',
        (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Saimaan kanava, sopimukseen kuuluvat työt, TP'),
        (SELECT id
           FROM sanktiotyyppi
                -- 6, Vesiväylän sakko
          WHERE koodi = 6), TRUE, 2);

