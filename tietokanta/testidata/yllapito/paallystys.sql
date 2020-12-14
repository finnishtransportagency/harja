----------------------------
-- Muhoksen päällystysurakka
----------------------------

-- Päällystyskohteet 2017


INSERT INTO yllapitokohde
(yllapitoluokka, urakka, sopimus, yha_kohdenumero, kohdenumero, nimi, yllapitokohdetyyppi, yllapitokohdetyotyyppi, yhaid,
 tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, tr_ajorata, tr_kaista,
 suorittava_tiemerkintaurakka, vuodet, keskimaarainen_vuorokausiliikenne, poistettu)
VALUES
  (8, (SELECT id
       FROM urakka
       WHERE nimi = 'Muhoksen päällystysurakka'),
      (SELECT id
       FROM sopimus
       WHERE urakka = (SELECT id
                       FROM urakka
                       WHERE nimi = 'Muhoksen päällystysurakka') AND paasopimus IS NULL),
      3, 'L03', 'Leppäjärven ramppi', 'paallyste' :: yllapitokohdetyyppi,
      'paallystys' ::yllapitokohdetyotyyppi, 1233534,
      20, 1, 0, 3, 0, 1, 11, (SELECT id
                             FROM urakka
                             WHERE nimi =
                                   'Oulun tiemerkinnän palvelusopimus 2013-2022'),
   '{2017}', 500, FALSE),
  (8, (SELECT id
       FROM urakka
       WHERE nimi = 'Muhoksen päällystysurakka'),
      (SELECT id
       FROM sopimus
       WHERE urakka = (SELECT id
                       FROM urakka
                       WHERE nimi = 'Muhoksen päällystysurakka') AND paasopimus IS NULL),
      308, '308a', 'Oulun ohitusramppi', 'paallyste' :: yllapitokohdetyyppi,
      'paallystys' ::yllapitokohdetyotyyppi, 54523243,
      20, 4, 334, 10, 10, 0, 11, (SELECT id
                                 FROM urakka
                                 WHERE nimi =
                                       'Oulun tiemerkinnän palvelusopimus 2013-2022'),
   '{2017}', 605, FALSE),
  (9, (SELECT id
       FROM urakka
       WHERE nimi = 'Muhoksen päällystysurakka'),
      (SELECT id
       FROM sopimus
       WHERE urakka = (SELECT id
                       FROM urakka
                       WHERE nimi = 'Muhoksen päällystysurakka') AND paasopimus IS NULL),
      308, '308b', 'Nakkilan ramppi', 'paallyste' :: yllapitokohdetyyppi,
      'paallystys' ::yllapitokohdetyotyyppi, 265257,
      20, 12, 1, 19, 2, null, null, (SELECT id
                               FROM urakka
                               WHERE nimi =
                                     'Oulun tiemerkinnän palvelusopimus 2013-2022'),
   '{2020}', 605, FALSE),
  (10, (SELECT id
        FROM urakka
        WHERE nimi = 'Muhoksen päällystysurakka'),
       (SELECT id
        FROM sopimus
        WHERE urakka = (SELECT id
                        FROM urakka
                        WHERE nimi = 'Muhoksen päällystysurakka') AND paasopimus IS NULL),
       310, '310', 'Oulaisten ohitusramppi', 'paallyste' :: yllapitokohdetyyppi,
       'paallystys' ::yllapitokohdetyotyyppi, 456896958,
       20, 19, 5, 21, 15, 0, 1, NULL, '{2017}', 900, FALSE),
  (2, (SELECT id
       FROM urakka
       WHERE nimi = 'Muhoksen päällystysurakka'),
      (SELECT id
       FROM sopimus
       WHERE urakka = (SELECT id
                       FROM urakka
                       WHERE nimi = 'Muhoksen päällystysurakka') AND paasopimus IS NULL),
      666, '666', 'Kuusamontien testi', 'paallyste' :: yllapitokohdetyyppi,
      'paallystys' ::yllapitokohdetyotyyppi, 456896959,
      20, 26, 1, 41, 15, 0, 1, NULL, '{2017}', 66, FALSE),
  (2, (SELECT id
       FROM urakka
       WHERE nimi = 'Muhoksen päällystysurakka'),
      (SELECT id
       FROM sopimus
       WHERE urakka = (SELECT id
                       FROM urakka
                       WHERE nimi = 'Muhoksen päällystysurakka') AND paasopimus IS NULL),
      3456, '3456', 'Ei YHA-kohde', 'paallyste' :: yllapitokohdetyyppi, 'paallystys' ::yllapitokohdetyotyyppi,
      NULL,
      20, 26, 1, 41, 15, 0, 1, NULL, '{2017}', 66, FALSE),
  (2, (SELECT id
       FROM urakka
       WHERE nimi = 'Muhoksen päällystysurakka'),
      (SELECT id
       FROM sopimus
       WHERE urakka = (SELECT id
                       FROM urakka
                       WHERE nimi = 'Muhoksen päällystysurakka') AND paasopimus IS NULL),
      3457, '3457', 'POISTETTU KOHDE EI SAA NÄKYÄ MISSÄÄN', 'paallyste' :: yllapitokohdetyyppi,
      'paallystys' ::yllapitokohdetyotyyppi, NULL,
      20, 26, 1, 41, 15, 0, 1, NULL, '{2017}', 66, TRUE);

INSERT INTO yllapitokohteen_kustannukset (yllapitokohde, sopimuksen_mukaiset_tyot, arvonvahennykset, bitumi_indeksi, kaasuindeksi)
VALUES ((SELECT id FROM yllapitokohde WHERE nimi = 'Leppäjärven ramppi'), 400, 100, 4543.95, 0);

INSERT INTO yllapitokohteen_kustannukset (yllapitokohde, sopimuksen_mukaiset_tyot, arvonvahennykset, bitumi_indeksi, kaasuindeksi)
VALUES ((SELECT id FROM yllapitokohde WHERE nimi = 'Oulun ohitusramppi'), 9000, 200, 565, 100);

INSERT INTO yllapitokohteen_kustannukset (yllapitokohde, sopimuksen_mukaiset_tyot, arvonvahennykset, bitumi_indeksi, kaasuindeksi)
VALUES ((SELECT id FROM yllapitokohde WHERE nimi = 'Nakkilan ramppi'), 500, 3457, 5, 6);

INSERT INTO yllapitokohteen_kustannukset (yllapitokohde, sopimuksen_mukaiset_tyot, arvonvahennykset, bitumi_indeksi, kaasuindeksi)
VALUES ((SELECT id FROM yllapitokohde WHERE nimi = 'Oulaisten ohitusramppi'), 500, 3457, 5, 6);

INSERT INTO yllapitokohteen_kustannukset (yllapitokohde, sopimuksen_mukaiset_tyot, arvonvahennykset, bitumi_indeksi, kaasuindeksi)
VALUES ((SELECT id FROM yllapitokohde WHERE nimi = 'Kuusamontien testi'), 500, 3457, 5, 6);

INSERT INTO yllapitokohteen_kustannukset (yllapitokohde, sopimuksen_mukaiset_tyot, arvonvahennykset, bitumi_indeksi, kaasuindeksi)
VALUES ((SELECT id FROM yllapitokohde WHERE nimi = 'Ei YHA-kohde'), 500, 3457, 5, 6);

INSERT INTO yllapitokohteen_kustannukset (yllapitokohde, sopimuksen_mukaiset_tyot, arvonvahennykset, bitumi_indeksi, kaasuindeksi)
VALUES ((SELECT id FROM yllapitokohde WHERE nimi = 'POISTETTU KOHDE EI SAA NÄKYÄ MISSÄÄN'), 500, 3457, 5, 6);

INSERT INTO yllapitokohteen_aikataulu
(yllapitokohde, kohde_alku, paallystys_alku, paallystys_loppu, tiemerkinta_alku, tiemerkinta_loppu,
 kohde_valmis, muokkaaja, muokattu, valmis_tiemerkintaan, tiemerkinta_takaraja)
VALUES
  ((SELECT id
    FROM yllapitokohde
    WHERE nimi = 'Leppäjärven ramppi'), '2017-05-19',
                                        '2017-05-19', '2017-05-21', '2017-05-22',
                                        '2017-05-23',
                                        '2017-05-24', (SELECT id
                                                       FROM kayttaja
                                                       WHERE kayttajanimi = 'jvh'), NOW(),
                                        '2017-05-21', '2017-06-04'),
  ((SELECT id
    FROM yllapitokohde
    WHERE nimi = 'Oulun ohitusramppi'), '2017-05-21',
                                        '2017-05-21', NULL, NULL, NULL,
                                        NULL, (SELECT id
                                               FROM kayttaja
                                               WHERE kayttajanimi = 'jvh'), NOW(), NULL, NULL),
  ((SELECT id
    FROM yllapitokohde
    WHERE nimi = 'Oulaisten ohitusramppi'), '2017-05-26',
                                            NULL, NULL, NULL, NULL,
                                            NULL, (SELECT id
                                                   FROM kayttaja
                                                   WHERE kayttajanimi = 'jvh'), NOW(), NULL, NULL),
  ((SELECT id
    FROM yllapitokohde
    WHERE nimi = 'Kuusamontien testi'), '2017-06-02',
                                        NULL, NULL, NULL, NULL,
                                        NULL, (SELECT id
                                               FROM kayttaja
                                               WHERE kayttajanimi = 'jvh'), NOW(), NULL, NULL);
INSERT INTO yllapitokohteen_aikataulu (yllapitokohde) VALUES ((SELECT id
                                                               FROM yllapitokohde
                                                               WHERE nimi = 'Nakkilan ramppi'));
INSERT INTO yllapitokohteen_aikataulu (yllapitokohde) VALUES ((SELECT id
                                                               FROM yllapitokohde
                                                               WHERE nimi = 'Ei YHA-kohde'));
INSERT INTO yllapitokohteen_aikataulu (yllapitokohde) VALUES ((SELECT id
                                                               FROM yllapitokohde
                                                               WHERE nimi = 'POISTETTU KOHDE EI SAA NÄKYÄ MISSÄÄN'));


INSERT INTO yllapitokohteen_tarkka_aikataulu (yllapitokohde, urakka, toimenpide, kuvaus, alku, loppu, luoja, luotu) VALUES ((SELECT id FROM yllapitokohde WHERE nimi = 'Oulun ohitusramppi'), (SELECT id FROM urakka WHERE nimi = 'Muhoksen päällystysurakka'), 'ojankaivuu'::yllapitokohteen_aikataulu_toimenpide, 'Kaivetaan syvä oja ensin', '2017-05-19', '2017-05-20', (SELECT id FROM kayttaja WHERE kayttajanimi = 'jvh'), NOW());
INSERT INTO yllapitokohteen_tarkka_aikataulu (yllapitokohde, urakka, toimenpide, kuvaus, alku, loppu, luoja, luotu) VALUES ((SELECT id FROM yllapitokohde WHERE nimi = 'Oulun ohitusramppi'), (SELECT id FROM urakka WHERE nimi = 'Muhoksen päällystysurakka'), 'muu'::yllapitokohteen_aikataulu_toimenpide, 'Siirretään iso kivi pois alta', '2017-05-22', '2017-05-22', (SELECT id FROM kayttaja WHERE kayttajanimi = 'jvh'), NOW());
INSERT INTO yllapitokohteen_tarkka_aikataulu (yllapitokohde, urakka, toimenpide, kuvaus, alku, loppu, luoja, luotu) VALUES ((SELECT id FROM yllapitokohde WHERE nimi = 'Oulaisten ohitusramppi'), (SELECT id FROM urakka WHERE nimi = 'Muhoksen päällystysurakka'), 'rp_tyot'::yllapitokohteen_aikataulu_toimenpide, NULL, '2017-05-22', '2017-05-25', (SELECT id FROM kayttaja WHERE kayttajanimi = 'jvh'), NOW());


INSERT INTO yllapitokohteen_maksuera (yllapitokohde, maksueranumero, sisalto)
VALUES ((SELECT id FROM yllapitokohde WHERE nimi = 'Leppäjärven ramppi'), 1, 'Puolet');
INSERT INTO yllapitokohteen_maksuera (yllapitokohde, maksueranumero, sisalto)
VALUES ((SELECT id FROM yllapitokohde WHERE nimi = 'Leppäjärven ramppi'), 2, 'Puolet');
INSERT INTO yllapitokohteen_maksueratunnus (yllapitokohde, maksueratunnus)
VALUES ((SELECT id FROM yllapitokohde WHERE nimi = 'Oulaisten ohitusramppi'), 'TUNNUS123');
INSERT INTO yllapitokohteen_maksuera (yllapitokohde, maksueranumero, sisalto)
VALUES ((SELECT id FROM yllapitokohde WHERE nimi = 'Oulaisten ohitusramppi'), 1, '1/3');
INSERT INTO yllapitokohteen_maksuera (yllapitokohde, maksueranumero, sisalto)
VALUES ((SELECT id FROM yllapitokohde WHERE nimi = 'Oulaisten ohitusramppi'), 2, '1/3');
INSERT INTO yllapitokohteen_maksuera (yllapitokohde, maksueranumero, sisalto)
VALUES ((SELECT id FROM yllapitokohde WHERE nimi = 'Oulaisten ohitusramppi'), 3, '1/3');

-- Päällystyskohteet 2018

INSERT INTO yllapitokohde
(yllapitoluokka, urakka, sopimus, yha_kohdenumero, kohdenumero, nimi, yllapitokohdetyyppi, yllapitokohdetyotyyppi, yhaid,
 tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys,
 suorittava_tiemerkintaurakka, vuodet, keskimaarainen_vuorokausiliikenne, poistettu)
VALUES
  (8, (SELECT id
       FROM urakka
       WHERE nimi = 'Muhoksen päällystysurakka'),
      (SELECT id
       FROM sopimus
       WHERE urakka = (SELECT id
                       FROM urakka
                       WHERE nimi = 'Muhoksen päällystysurakka') AND paasopimus IS NULL),
      3, 'L03', 'Leppäjärven ramppi 2018', 'paallyste' :: yllapitokohdetyyppi,
      'paallystys' ::yllapitokohdetyotyyppi, 34554345,
      20, 1, 0, 3, 0, (SELECT id
                       FROM urakka
                       WHERE nimi =
                             'Oulun tiemerkinnän palvelusopimus 2013-2022'),
   '{2018}', 500, FALSE),
  (8, (SELECT id
       FROM urakka
       WHERE nimi = 'Muhoksen päällystysurakka'),
      (SELECT id
       FROM sopimus
       WHERE urakka = (SELECT id
                       FROM urakka
                       WHERE nimi = 'Muhoksen päällystysurakka') AND paasopimus IS NULL),
      308, '308a', 'Oulun ohitusramppi 2018', 'paallyste' :: yllapitokohdetyyppi,
      'paallystys' ::yllapitokohdetyotyyppi, 54523345243,
      20, 4, 334, 10, 10, (SELECT id
                           FROM urakka
                           WHERE nimi =
                                 'Oulun tiemerkinnän palvelusopimus 2013-2022'),
   '{2018}', 605, FALSE),
  (9, (SELECT id
       FROM urakka
       WHERE nimi = 'Muhoksen päällystysurakka'),
      (SELECT id
       FROM sopimus
       WHERE urakka = (SELECT id
                       FROM urakka
                       WHERE nimi = 'Muhoksen päällystysurakka') AND paasopimus IS NULL),
      308, '308b', 'Nakkilan ramppi 2018', 'paallyste' :: yllapitokohdetyyppi,
      'paallystys' ::yllapitokohdetyotyyppi, 26554257,
      20, 12, 1, 19, 2, (SELECT id
                         FROM urakka
                         WHERE nimi =
                               'Oulun tiemerkinnän palvelusopimus 2013-2022'),
   '{2018}', 605, FALSE),
  (10, (SELECT id
        FROM urakka
        WHERE nimi = 'Muhoksen päällystysurakka'),
       (SELECT id
        FROM sopimus
        WHERE urakka = (SELECT id
                        FROM urakka
                        WHERE nimi = 'Muhoksen päällystysurakka') AND paasopimus IS NULL),
       310, '310', 'Oulaisten ohitusramppi 2018', 'paallyste' :: yllapitokohdetyyppi,
       'paallystys' ::yllapitokohdetyotyyppi, 45689694558,
       20, 19, 5, 21, 15, NULL, '{2018}', 900, FALSE),
  (2, (SELECT id
       FROM urakka
       WHERE nimi = 'Muhoksen päällystysurakka'),
      (SELECT id
       FROM sopimus
       WHERE urakka = (SELECT id
                       FROM urakka
                       WHERE nimi = 'Muhoksen päällystysurakka') AND paasopimus IS NULL),
      666, '666', 'Kuusamontien testi 2018', 'paallyste' :: yllapitokohdetyyppi,
      'paallystys' ::yllapitokohdetyotyyppi, 456896534959,
      20, 26, 1, 41, 15, NULL, '{2018}', 66, FALSE),
  (2, (SELECT id
       FROM urakka
       WHERE nimi = 'Muhoksen päällystysurakka'),
      (SELECT id
       FROM sopimus
       WHERE urakka = (SELECT id
                       FROM urakka
                       WHERE nimi = 'Muhoksen päällystysurakka') AND paasopimus IS NULL),
      3456, '3456', 'Ei YHA-kohde 2018', 'paallyste' :: yllapitokohdetyyppi, 'paallystys' ::yllapitokohdetyotyyppi,
      NULL,
      20, 26, 1, 41, 15, NULL, '{2018}', 66, FALSE),
  (2, (SELECT id
       FROM urakka
       WHERE nimi = 'Muhoksen päällystysurakka'),
      (SELECT id
       FROM sopimus
       WHERE urakka = (SELECT id
                       FROM urakka
                       WHERE nimi = 'Muhoksen päällystysurakka') AND paasopimus IS NULL),
      3457, '3457', 'POISTETTU KOHDE EI SAA NÄKYÄ MISSÄÄN 2018', 'paallyste' :: yllapitokohdetyyppi,
      'paallystys' ::yllapitokohdetyotyyppi, NULL,
      20, 26, 1, 41, 15, NULL, '{2018}', 66, TRUE);

INSERT INTO yllapitokohteen_aikataulu
(yllapitokohde, kohde_alku, paallystys_alku, paallystys_loppu, tiemerkinta_alku, tiemerkinta_loppu,
 kohde_valmis, muokkaaja, muokattu, valmis_tiemerkintaan, tiemerkinta_takaraja)
VALUES
  ((SELECT id
    FROM yllapitokohde
    WHERE nimi = 'Leppäjärven ramppi 2018'), '2018-05-19',
                                             '2018-05-19', '2018-05-21', '2018-05-22',
                                             '2018-05-23',
                                             '2018-05-24', (SELECT id
                                                            FROM kayttaja
                                                            WHERE kayttajanimi = 'jvh'), NOW(),
                                             '2018-05-21', '2018-06-04'),
  ((SELECT id
    FROM yllapitokohde
    WHERE nimi = 'Oulun ohitusramppi 2018'), '2018-05-21',
                                             '2018-05-21', NULL, NULL, NULL,
                                             NULL, (SELECT id
                                                    FROM kayttaja
                                                    WHERE kayttajanimi = 'jvh'), NOW(), NULL, NULL),
  ((SELECT id
    FROM yllapitokohde
    WHERE nimi = 'Oulaisten ohitusramppi 2018'), '2018-05-26',
                                                 NULL, NULL, NULL, NULL,
                                                 NULL, (SELECT id
                                                        FROM kayttaja
                                                        WHERE kayttajanimi = 'jvh'), NOW(), NULL, NULL),
  ((SELECT id
    FROM yllapitokohde
    WHERE nimi = 'Kuusamontien testi 2018'), '2018-06-02',
                                             NULL, NULL, NULL, NULL,
                                             NULL, (SELECT id
                                                    FROM kayttaja
                                                    WHERE kayttajanimi = 'jvh'), NOW(), NULL, NULL);
INSERT INTO yllapitokohteen_aikataulu (yllapitokohde) VALUES ((SELECT id
                                                               FROM yllapitokohde
                                                               WHERE nimi = 'Nakkilan ramppi 2018'));
INSERT INTO yllapitokohteen_aikataulu (yllapitokohde) VALUES ((SELECT id
                                                               FROM yllapitokohde
                                                               WHERE nimi = 'Ei YHA-kohde 2018'));
INSERT INTO yllapitokohteen_aikataulu (yllapitokohde) VALUES ((SELECT id
                                                               FROM yllapitokohde
                                                               WHERE nimi = 'POISTETTU KOHDE EI SAA NÄKYÄ MISSÄÄN 2018'));

INSERT INTO yllapitokohteen_tarkka_aikataulu (yllapitokohde, urakka, toimenpide, kuvaus, alku, loppu, luoja, luotu) VALUES ((SELECT id FROM yllapitokohde WHERE nimi = 'Oulun ohitusramppi 2018'), (SELECT id FROM urakka WHERE nimi = 'Muhoksen päällystysurakka'), 'ojankaivuu'::yllapitokohteen_aikataulu_toimenpide, 'Kaivetaan syvä oja ensin', '2018-05-19', '2018-05-20', (SELECT id FROM kayttaja WHERE kayttajanimi = 'jvh'), NOW());
INSERT INTO yllapitokohteen_tarkka_aikataulu (yllapitokohde, urakka, toimenpide, kuvaus, alku, loppu, luoja, luotu) VALUES ((SELECT id FROM yllapitokohde WHERE nimi = 'Oulun ohitusramppi 2018'), (SELECT id FROM urakka WHERE nimi = 'Muhoksen päällystysurakka'), 'muu'::yllapitokohteen_aikataulu_toimenpide, 'Siirretään iso kivi pois alta', '2018-05-22', '2018-05-22', (SELECT id FROM kayttaja WHERE kayttajanimi = 'jvh'), NOW());
INSERT INTO yllapitokohteen_tarkka_aikataulu (yllapitokohde, urakka, toimenpide, kuvaus, alku, loppu, luoja, luotu) VALUES ((SELECT id FROM yllapitokohde WHERE nimi = 'Oulaisten ohitusramppi 2018'), (SELECT id FROM urakka WHERE nimi = 'Muhoksen päällystysurakka'), 'rp_tyot'::yllapitokohteen_aikataulu_toimenpide, NULL, '2018-05-22', '2018-05-25', (SELECT id FROM kayttaja WHERE kayttajanimi = 'jvh'), NOW());

INSERT INTO yllapitokohteen_kustannukset (yllapitokohde, sopimuksen_mukaiset_tyot, arvonvahennykset, bitumi_indeksi, kaasuindeksi)
VALUES ((SELECT id FROM yllapitokohde WHERE nimi = 'Leppäjärven ramppi 2018'), 400, 100, 4543.95, 0);

INSERT INTO yllapitokohteen_kustannukset (yllapitokohde, sopimuksen_mukaiset_tyot, arvonvahennykset, bitumi_indeksi, kaasuindeksi)
VALUES ((SELECT id FROM yllapitokohde WHERE nimi = 'Oulun ohitusramppi 2018'), 9000, 200, 565, 100);

INSERT INTO yllapitokohteen_kustannukset (yllapitokohde, sopimuksen_mukaiset_tyot, arvonvahennykset, bitumi_indeksi, kaasuindeksi)
VALUES ((SELECT id FROM yllapitokohde WHERE nimi = 'Nakkilan ramppi 2018'), 500, 3457, 5, 6);

INSERT INTO yllapitokohteen_kustannukset (yllapitokohde, sopimuksen_mukaiset_tyot, arvonvahennykset, bitumi_indeksi, kaasuindeksi)
VALUES ((SELECT id FROM yllapitokohde WHERE nimi = 'Oulaisten ohitusramppi 2018'), 500, 3457, 5, 6);

INSERT INTO yllapitokohteen_kustannukset (yllapitokohde, sopimuksen_mukaiset_tyot, arvonvahennykset, bitumi_indeksi, kaasuindeksi)
VALUES ((SELECT id FROM yllapitokohde WHERE nimi = 'Kuusamontien testi 2018'), 500, 3457, 5, 6);

INSERT INTO yllapitokohteen_kustannukset (yllapitokohde, sopimuksen_mukaiset_tyot, arvonvahennykset, bitumi_indeksi, kaasuindeksi)
VALUES ((SELECT id FROM yllapitokohde WHERE nimi = 'Ei YHA-kohde 2018'), 500, 3457, 5, 6);

INSERT INTO yllapitokohteen_kustannukset (yllapitokohde, sopimuksen_mukaiset_tyot, arvonvahennykset, bitumi_indeksi, kaasuindeksi)
VALUES ((SELECT id FROM yllapitokohde WHERE nimi = 'POISTETTU KOHDE EI SAA NÄKYÄ MISSÄÄN 2018'), 500, 3457, 5, 6);


-- Testidatan kohdeosilla on hardkoodattu id, jotta päällystysilmoituksen ilmoitustiedoissa viitataan
-- oikeaa id:n (ei voi hakea id:tä nimellä, koska ilmoitustiedot ovat JSON-muodossa)
INSERT INTO yllapitokohdeosa (id, yllapitokohde, nimi, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, tr_ajorata, tr_kaista, sijainti)
VALUES (666, (SELECT id
              FROM yllapitokohde
              WHERE nimi = 'Leppäjärven ramppi'), 'Leppäjärven kohdeosa', 20, 1, 0, 3, 0, 1, 11, ST_GeomFromText(
                 'MULTILINESTRING((426938.1807 7212765.5588,426961.6821 7212765.3789,426978.403 7212763.9413,426991.616 7212762.2112,427003.7041 7212760.2768,427016.424 7212757.0822,427042.9341 7212749.4632,427062.1153 7212743.1593,427075.529 7212738.3829,427090.1869 7212730.16,427118.7481 7212720.132,427140.2329 7212713.314,427166.0599 7212706.0953,427188.8477 7212699.2648,427222.3342 7212689.9562,427258.6192 7212680.6441,427287.9058 7212673.4497,427326.0543 7212665.2298,427357.1639 7212658.2249,427400.9309 7212649.3272,427448.4773 7212639.1771,427490.2277 7212629.6559,427526.8182 7212621.5123,427559.5662 7212613.9993,427580.886 7212609.108,427590.5823 7212606.8699,427610.5222 7212602.3621,427615.8971 7212600.8797,427647.152 7212593.4942,427689.2229 7212583.0023,427709.4832 7212577.99,427746.5912 7212568.6291,427778.3041 7212561.2269,427806.2549 7212554.6179,427839.5073 7212547.4069,427860.0803 7212543.0111,427895.971 7212534.8442,427916.8012 7212530.6253,427946.201 7212523.0611,427980.9602 7212515.0097,428008.1027 7212508.8421,428033.143 7212503.2391,428036.1649 7212502.4708,428039.9377 7212501.8431,428052.579 7212499.287,428077.767 7212493.7668,428101.1832 7212489.3793,428120.8348 7212485.7869,428138.9892 7212482.5089,428171.6193 7212477.6218,428205.1349 7212472.2087,428240.3229 7212467.393,428272.6879 7212462.0223,428307.6389 7212456.075,428329.7389 7212451.821,428358.6538 7212446.268,428378.7909 7212442.4112,428418.4461 7212433.4867,428437.7511 7212429.4131,428464.0909 7212423.8512,428494.8467 7212417.3048,428518.0711 7212412.1842,428542.7189 7212407.6019,428564.4681 7212402.5379,428572.3241 7212400.806,428583.626 7212398.5822,428594.2288 7212396.7431,428638.7807 7212386.621,428659.3263 7212382.0042,428681.515 7212375.4978,428695.2712 7212370.0258,428709.2329 7212363.9952,428735.6692 7212351.8589,428756.5953 7212342.5259,428779.0443 7212332.5241,428797.3339 7212325.9032,428811.267 7212320.7802,428821.0079 7212318.4629,428841.3992 7212313.9373,428866.0309 7212310.0608,428876.2501 7212308.8679,428887.3287 7212308.2813,428901.9032 7212308.3063,428914.666 7212308.4087,428919.9582 7212308.4671,428934.4308 7212308.1693,428940.6181 7212308.3712,428950.5121 7212308.5969,428958.3771 7212308.2157,428966.164 7212308.1812,428970.8492 7212308.0621,428979.0668 7212307.8131,428990.3008 7212307.0532,429014.169 7212302.8122,429028.6351 7212299.7338,429042.9041 7212296.0717,429055.5698 7212293.2059,429061.504 7212291.6533,429068.3219 7212290.0959,429081.532 7212287.4891,429093.364 7212285.0771,429112.4159 7212280.9541,429126.4961 7212277.4772,429140.9818 7212274.5238,429162.8007 7212269.8332,429177.8368 7212266.6678,429181.459 7212266.0389,429184.8322 7212265.2218,429194.6411 7212263.3083,429203.796 7212261.3959,429205.2807 7212261.1898,429209.0762 7212260.505,429211.9081 7212259.9469,429226.8572 7212257.4551,429241.9219 7212254.095,429257.9602 7212251.219,429287.1551 7212248.0441,429367.0582 7212248.589,429438.7837 7212250.982,429485.2861 7212253.8472,429491.1869 7212253.9342,429497.1627 7212254.17,429561.947 7212258.2359,429620.4332 7212263.0933,429648.4882 7212265.3028,429676.8529 7212268.813,429697.1328 7212271.4132,429719.8528 7212274.3779,429756.8882 7212280.3639,429787.6809 7212285.2218,429809.8119 7212288.7309,429817.2111 7212290.1441,429822.4002 7212291.446,429872.386 7212302.0732,429891.6678 7212306.4672,429912.3182 7212310.8451,429976.099 7212327.326,430025.2319 7212339.7649,430078.0937 7212353.7593,430108.6828 7212361.4188,430124.4139 7212365.839,430215.3211 7212391.4653,430294.3898 7212416.1232,430353.3442 7212435.1501,430407.7669 7212454.9959,430448.0678 7212471.5649,430466.0512 7212478.8218,430476.9363 7212483.3433,430494.7208 7212490.9081,430509.801 7212497.9267,430518.1829 7212501.961,430574.9789 7212533.2201,430624.2959 7212561.9177,430639.4118 7212571.5288,430645.8289 7212575.3839,430650.8691 7212578.8262))'));

INSERT INTO yllapitokohdeosa (id, yllapitokohde, nimi, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, tr_ajorata, tr_kaista, sijainti)
VALUES (999, (SELECT id
              FROM yllapitokohde
              WHERE nimi = 'Leppäjärven ramppi'), 'Leppäjärven toisen tien kohdeosa', 4, 410, 0, 420, 0, 1, 11, ST_GeomFromText(
    'MULTILINESTRING((424494.53 7245355.801,424498.898 7245366.947,424501.919 7245380.286,424504.023 7245390.672,424506.18 7245399.286,424514.064 7245423.021,424520.137 7245441.325,424525.697 7245459.34,424529.87 7245474.561,424534.689 7245494.784,424539.63 7245519.058,424543.926 7245544.835,424546.504 7245568.281,424547.744 7245582.867,424548.336 7245593.513,424549.296 7245612.881,424549.296 7245635.957,424548.698 7245652.874,424546.827 7245678.705,424544.333 7245702.769,424541.006 7245726.832,424535.393 7245760.408,424528.714 7245795.255,424521.23 7245832.676,424509.927 7245888.158,424502.205 7245922.525,424472.443 7246067.96,424464.738 7246105.899,424453.078 7246165.087,424448.091 7246189.262,424444.856 7246204.224,424440.365 7246227.129,424437.258 7246241.631,424435.343 7246251.639,424433.062 7246262.164,424402.892 7246408.524,424395.736 7246439.858,424385.963 7246472.7,424378.186 7246495.033,424370.337 7246516.138,424362.464 7246534.712,424360.932 7246538.324,424357.692 7246545.478,424349.705 7246563.502,424341.733 7246579.879,424324.376 7246610.914,424311.213 7246631.98,424289.727 7246663.022,424270.996 7246686.933,424239.916 7246722.482,424191.308 7246774.489,424141.609 7246830.294,424117.74 7246860.34,424099.906 7246884.539,424088.625 7246901.209,424079.654 7246915.721,424070.948 7246931.698,424059.873 7246952.672,424050.834 7246973.052,424041.784 7246996.216,424033.96 7247019.071,424026.484 7247044.807,424019.136 7247075.688,424015.589 7247098.764,424012.589 7247122.042,424010.75 7247145.964,424010.189 7247168.447,424010.308 7247193.377,424013.527 7247234.523,424015.704 7247262.66,424018.765 7247287.995,424022.451 7247317.761,424025.691 7247342.836,424029.048 7247367.496,424030.005 7247375.047,424033.843 7247405.16,424039.873 7247451.585,424043.772 7247482.525,424046.277 7247501.887,424050.816 7247535.017,424055.205 7247568.557,424063.832 7247636.903,424066.994 7247658.677,424068.043 7247667.596,424070.09 7247682.897,424070.963 7247689.018,424072.54737019 7247701.75860398),(424072.556 7247701.828,424074.96 7247721.121,424078.857 7247750.944,424081.833 7247773.403,424084.914 7247797.183,424088.393 7247823.998,424091.142 7247844.77,424091.243 7247845.509,424100.3 7247908.808,424100.422 7247909.65,424103.321 7247936.729,424108.073 7247971.56,424113.698 7248018.995,424120.841 7248078.826,424128.797 7248142.841,424134.038 7248185.098,424138.02 7248221.416,424139.752 7248235.559,424143.434 7248267.169,424146.288 7248287.092,424146.972 7248292.802,424163.165 7248424.889,424180.184 7248565.099,424186.833 7248626.924,424189.03 7248642.109,424191.457 7248665.416,424192.847 7248673.97,424193.429 7248680.761,424209.735 7248810.059,424214.523 7248854.363,424236.198 7249032.005,424242.381 7249080.175,424256.406 7249194.256,424269.92 7249305.68,424283.921 7249420.539,424286.084 7249438.285,424292.782 7249492.781,424303.297 7249579.137,424319.889 7249717.252,424343.43 7249917.617,424357.841 7250028.461,424367.993 7250113.522,424374.493 7250165.699,424385.578 7250262.891,424405.762 7250428.676,424417.371 7250521.377,424422.446 7250564.86,424441.298 7250718.266,424464.218 7250902.821,424474.285 7250985.117,424488.702 7251101.918,424513.855 7251302.859,424531.885 7251448.857,424538.553 7251500.938,424562.462 7251692.761,424587.473 7251894.172,424606.421 7252047.615,424617.636 7252137.383,424620.429 7252161.043,424625.582 7252203.275,424628.409 7252226.638,424632.91 7252262.329,424646.085 7252369.001,424657.983 7252465.316,424667.133 7252539.399,424695.108 7252762.894,424711.535 7252897.041,424720.037 7252963.596,424727.577 7253023.786,424728.096 7253028.727,424729.397 7253040.557,424742.99 7253147.697,424751.18 7253212.565,424764.685 7253319.354,424774.298 7253399.515,424794.464 7253556.457,424819.179 7253755.013,424837.378 7253899.599,424844.061 7253954.668,424864.388 7254114.449,424882.81 7254265.553,424886.734 7254295.518,424895.96 7254370.088,424910.125 7254483.931,424925.019 7254603.519,424934.981 7254683.847,424942.096 7254742.067,424949.899 7254802.867,424966.113 7254934.602,424976.069 7255017.57,424990.205 7255129.398,425004.651 7255247.044,425023.51 7255401.892,425033.596 7255481.387,425045.829 7255577.138,425057.634 7255672.309,425070.051 7255766.755,425083.345 7255869.391,425101.948 7256014.843,425107.851 7256061.523,425112.005 7256100.654,425120.391 7256191.734,425124.482 7256240.333,425129.395 7256304.072,425131.133 7256333.242,425132.936 7256361.129,425136.209 7256420.715,425137.049 7256440.455,425138.591 7256468.05,425139.815 7256518.207,425139.556 7256558.965,425138.352 7256586.436,425135.354 7256623.153,425130.981 7256658.466,425127.029 7256682.666,425122.926 7256706.557,425115.013 7256740.753,425106.147 7256777.411,425098.604 7256805.59,425087.596 7256843.513,425086.036 7256848.898,425068.221 7256911.028,425048.744 7256975.212,425033.575 7257028.514,425031.317 7257036.484),(425031.317 7257036.484,425021.078 7257072.518,425006.715 7257122.914,424990.521 7257180.397,424980.126 7257216.152,424968.651 7257252.433,424945.212 7257334.084,424935.12 7257370.513,424932.233 7257378.974,424922.34 7257414.995,424891.235 7257523.008,424864.066 7257616.493,424844.211 7257686.149,424820.773 7257766.639,424800.24 7257837.745,424790.725 7257870.983,424777.843 7257916.356,424758.473 7257983.496,424740.555 7258045.896,424724.283 7258101.62,424695.325 7258201.653,424661.312 7258320.577,424644.749 7258378.72,424631.48 7258425.545,424622.279 7258460.76,424612.011 7258501.393,424609.733 7258511.115,424607.651 7258520.066,424601.274 7258552.518,424594.95 7258590.003,424590.622 7258617.051,424589.015 7258638.325,424586.292 7258673.326,424584.344 7258719.361,424584.117 7258745.481,424585.077 7258779.739,424587.933 7258818.56,424592.207 7258858.666,424594.333 7258874.527,424594.537 7258876.012,424596.754 7258892.384,424601.046 7258918.422,424606.591 7258943.441,424612.591 7258971.675,424619.277 7259000.853,424626.92 7259029.669,424632.97 7259051.106,424642.439 7259085.027,424655.159 7259127.229,424662.121 7259149.831,424681.005 7259213.066,424695.197 7259261.08,424715.793 7259329.177,424736.289 7259397.661,424750.024 7259442.575,424761.686 7259480.285,424775.311 7259525.504,424805.133 7259625.023,424816.256 7259661.562,424835.472 7259728.712,424843.875 7259756.592,424855.852 7259796.763,424876.138 7259869.929,424878.998 7259881.24,424886.665 7259914.357,424894.775 7259949.741,424906.358 7260008.376,424911.418 7260039.428,424916.008 7260075.889,424918.983 7260101.255,424921.642 7260136.847,424923.386 7260161.04,424924.672 7260184.3,424925.498 7260207.013,424925.692 7260226.253,424925.408 7260242.448,424924.967 7260266.05,424923.246 7260303.863,424920.764 7260340.118,424917.118 7260376.52,424914.454 7260397.814,424911.377 7260422.05,424905.556 7260459.053,424897.852 7260497.098,424890.467 7260528.483,424884.928 7260550.604,424876.368 7260581.381,424865.178 7260620.742,424854.118 7260653.824,424843.787 7260683.268,424831.968 7260714.483,424819.523 7260746.39,424806.138 7260779.452,424792.768 7260810.808,424782.253 7260834.593,424768.258 7260866.564,424757.757 7260888.721,424747.41 7260911.592,424730.728 7260949.509,424724.268 7260963.53,424717.786 7260977.606,424713.837 7260986.191,424707.419 7261000.78,424696.4 7261025.639,424683.871 7261051.05,424675.426 7261069.247,424668.769 7261083.849,424647.11 7261131.356,424635.946 7261156.25,424619.029 7261193.127,424608.065 7261217.384,424594.039 7261249.097,424583.143 7261273.145,424569.407184803 7261301.78852347),(424569.277 7261302.06,424549.569 7261342.604,424534.533 7261372.909,424515.903 7261409.084,424498.533 7261441.083,424483.593 7261467.9,424454.03 7261519.986,424428.459 7261562.615,424404.395 7261600.314,424377.994 7261640.403,424357.452 7261671.299,424329.736 7261710.763,424308.274 7261739.949,424294.083 7261758.611,424258.9 7261804.071,424234.799 7261833.965,424212.093 7261860.759,424187.916 7261889.025,424164.823 7261915.819,424148.783 7261933.786,424125.845 7261958.877,424107.204 7261978.739,424093.101 7261993.574,424076.751 7262010.687,424050.869 7262037.558,424018.169 7262070.392,423984.927 7262104.465,423950.678 7262139.389,423908.601 7262183.064,423875.358 7262217.447,423842.504 7262251.984,423817.785 7262278.546,423777.877 7262322.067,423746.028 7262357.999,423723.013 7262384.561,423706.817 7262403.38,423691.396 7262421.811,423677.292 7262438.771,423647.646 7262475.875,423624.862 7262504.839,423612.23 7262521.799,423581.783 7262562.908,423554.348 7262601.553,423526.447 7262641.924,423499.601 7262682.208,423474.536 7262722.259,423451.019 7262761.456,423427.503 7262801.893,423403.525 7262845.815,423382.254 7262887.412,423360.985 7262929.474,423344.59 7262963.944,423332.729 7262989.595,423324.594 7263007.387,423315.391 7263028.301,423305.957 7263050.144,423287.709 7263094.218,423274.796 7263126.904,423261.266 7263161.141,423243.328 7263206.685,423227.477 7263247.35,423224.045 7263254.993,423211.743 7263283.476,423197.009 7263316.289,423180.816 7263350.343,423165.145 7263382.705,423151.268 7263410.587,423139.548 7263433.044,423120.402 7263467.015,423103.113 7263497.283,423078.669 7263537.583,423058.586 7263569.929,423039.91 7263598.488,423013.228 7263637.077,422992.462 7263666.094,422971.155 7263694.721,422948.997 7263723.5,422917.983 7263761.764,422892.728 7263792.132,422873.512 7263814.368,422834.999 7263857.231,422793.224 7263902.32,422755.122 7263942.118,422720.457 7263978.3,422705.835 7263992.743,422694.09 7264005.097,422680.882 7264017.517,422649.351 7264050.56,422586.114 7264111.554,422545.912 7264151.323,422530.074 7264166.598,422458.044 7264238.08,422417.824 7264277.311,422356.897 7264337.411,422298.578 7264393.988,422222.346 7264470.305,422155.993 7264536.583,422085.747 7264605.496,422012.725 7264677.543,421944.489 7264744.632,421929.114 7264759.659,421924.486 7264763.941,421866.204 7264822.01,421809.116 7264877.846,421734.95 7264952.034,421662.262 7265023.638,421616.397 7265069.76,421566.471 7265121.801,421515.863 7265177.019,421502.219 7265193.296,421473.403 7265226.69,421434.933 7265272.832,421387.859 7265332.546,421374.905 7265350.476,421350.168 7265383.261,421335.663 7265403.264,421313.603 7265434.172,421275.318 7265489.09,421247.789 7265530.314,421233.089 7265553.482,421210.066 7265590.639,421184.104 7265634.005,421153.985 7265684.75,421137.261 7265714.399,421119.932 7265746.535,421097.341 7265789.377,421086.366 7265811.874,421076.735 7265831.157,421059.342 7265867.639,421046.275 7265894.42,421036.141 7265917.002,421027.848 7265935.602,421015.588 7265962.294,421007.904 7265980.331,421001.609 7265995.162,420993.103 7266014.701,420980.444 7266046.045,420963.345 7266089.301,420950.382 7266124.293,420937.496 7266159.691,420925.201 7266192.579,420914.893 7266221.919,420905.794 7266248.69,420892.91 7266284.987,420874.049 7266337.88,420856.743 7266385.038,420842.859 7266421.264,420822.552 7266472.817,420817.863 7266484.723,420816.755 7266487.211,420812.866 7266495.945,420808.16 7266506.513,420798.446 7266530.178,420786.072 7266559.712,420779.716 7266573.846,420760.349 7266615.146,420749.404 7266637.412,420727.932 7266680.108,420716.603 7266701.414,420695.594 7266739.466,420687.511 7266754.849,420664.272 7266795.3,420653.258 7266814.149,420640.524 7266836.147,420631.074 7266851.479,420627.551 7266857.184,420622.169 7266865.898,420601.739 7266898.089,420586.419 7266921.324,420568.269 7266948.229,420546.708 7266979.138,420528.127 7267005.137,420493.412 7267052.307,420477.588 7267072.472,420433.193 7267128.307,420398.94 7267169.123,420363.781 7267208.967,420332.305 7267243.848,420319.661 7267257.417,420314.74 7267262.689,420307.78 7267269.93,420298.484 7267279.714,420269.742 7267308.858,420235.251 7267342.799,420193.368 7267382.6,420148.136 7267424.112,420121.521 7267447.587,420113.483 7267455.125,420089.532 7267476.067,420062.688 7267499.834,420019.559 7267537.684,419998.536 7267556.383,419982.702 7267569.974,419967.581 7267583.18,419930.372 7267615.536,419887.263 7267653.243,419847.242 7267689.204,419821.517 7267712.909,419802.802 7267730.846,419779.555 7267754.712,419762.147 7267773.521,419734.41 7267805.528,419707.551 7267839.538,419684.486 7267871.859,419664.575 7267903.149,419641.809 7267945.073,419626.756 7267976.533,419603.143 7268033.299,419592.137 7268064.785,419585.028 7268087.338,419578.442 7268110.162,419569.496 7268145.326,419564.488 7268167.725,419558.793 7268194.584,419547.548 7268252.306,419539.849 7268292.151,419530.32 7268340.416,419523.442 7268374.27,419519.534 7268392.927,419513.368 7268417.368,419511.062 7268426.797,419506.896 7268443.12,419503.641 7268456.122,419494.115 7268489.803,419483.405 7268522.735,419469.013 7268563.741,419456.684 7268595.345,419449.612 7268612.649,419436.532 7268642.064,419421.137 7268674.919,419403.584 7268708.637,419381.85 7268746.589,419365.814 7268772.924,419349.118 7268799.034,419328.96 7268829.016,419302.366 7268866.794,419273.95 7268904.223,419233.118 7268956.683,419199.708 7269000.029,419179.286 7269027.356,419173.866 7269034.842,419165.664 7269045.675,419152.45 7269064.593,419139.079 7269084.356,419124.931 7269106.188,419108.304 7269133.004,419089.888 7269165.496,419073.323 7269195.922,419061.467 7269219.726,419050.482 7269242.836,419035.567 7269276.329,419009.8 7269341.037,418998.278 7269374.953,418987.158 7269410.076,418977.87 7269443.435,418969.028 7269478.513,418954.754 7269549.17,418948.396 7269588.177,418940.361 7269640.98,418932.7 7269694.761,418924.297 7269753.075,418916.79 7269808.312,418910.189 7269854.185,418903.145716854 7269903.96117931),(418903.104 7269904.256,418902.157 7269911.369,418896.085 7269953.769,418891.506 7269986.626,418886.254 7270022.704,418879.812 7270070.008,418872.601 7270118.959,418859.51 7270211.6,418852.787 7270259.658,418841.786 7270332.932,418825.051 7270449.852,418796.597 7270650.264,418789.47 7270700.645,418786.849 7270720.161,418768.266 7270850.736,418753.458 7270958.88,418752.074 7270968.985,418740.34 7271051.865,418732.229 7271107.072,418710.702 7271257.765,418697.134 7271355.424,418684.48 7271445.743,418658.314 7271632.997,418651.471 7271681.932,418644.63 7271728.912,418637.566 7271777.114,418629.792 7271827.426,418622.688 7271866.428,418610.625 7271925.2,418599.447 7271973.146,418587.442 7272018.235,418580.573 7272041.656,418575.542 7272057.512,418569.054 7272077.567,418556.528 7272112.073,418548.478 7272133.152,418538.069 7272157.978,418531.514 7272172.287,418523.972 7272188.483,418513.447 7272209.876,418495.615 7272242.978,418475.1 7272276.647,418455.775 7272305.986,418443.217 7272323.741,418437.439 7272331.254,418427.855 7272343.877,418398.544 7272380.362,418377.685 7272404.879,418352.671 7272433.189,418324.451 7272464.907,418316.053 7272474.366,418301.403 7272490.626,418270.347 7272525.252,418251.071 7272546.703,418231.34 7272568.786,418187.042 7272619.996,418135.249 7272681.103,418090.724 7272734.974,418053.924 7272780.65,418043.377 7272794.54,418015.63 7272830.383,417994.91 7272856.917,417962.482 7272899.442,417917.104 7272962.233,417895.417 7272992.616,417853.876 7273052.487,417820.064 7273101.002,417789.022 7273146.162,417758.492 7273190.226,417730.381 7273230.682,417720.908 7273244.147,417708.839 7273261.712,417687.44 7273292.594,417671.752 7273315.067,417625.062 7273382.6,417561.849 7273473.365,417521.534 7273531.59,417481.345 7273590.361,417455.442 7273627.358,417421.008 7273676.952,417394.477 7273715.66,417368.551 7273752.815,417322.707 7273819.384,417282.266 7273878.353,417262.71 7273907.747,417240.937 7273941.051,417215.862 7273980.194,417186.689 7274027.843,417162.719 7274069.265,417140.089 7274110.564,417118.299 7274152.243,417101.355 7274185.3,417080.375 7274228.214,417060.813 7274270.29,417046.385 7274302.707,417035.579 7274328.37,417023.026 7274358.448,417019.86 7274366.588,417016.965 7274373.664,417010.707 7274390.617,417001.549 7274412.599,416978.38 7274473.927,416946.271 7274568.447,416931.584 7274617.184,416924.747 7274641.272,416914.546 7274678.766,416900.873 7274733.084,416885.278 7274799.954,416868.934 7274885.676,416861.038 7274932.057,416852.164 7274989.647,416844.705 7275049.489,416836.172 7275134.618,416831.899 7275191.829,416829.308 7275238.471,416827.51 7275287.975,416826.198 7275326.331,416824.921 7275364.053,416823.756 7275391.801,416822.712 7275424.423,416821.264 7275472.092,416819.365 7275529.881,416817.991 7275568.072,416817.048 7275592.513,416815.59 7275637.597,416814.003 7275683.395,416812.249 7275736.712,416810.62 7275784.202,416809.033 7275836.663,416806.958 7275893.733,416805.232 7275940.393,416804.063 7275976.397,416802.266 7276030.152,416800.805 7276072.066,416799.343 7276108.049,416797.63 7276146.246,416796.252 7276174.356,416794.748 7276202.654,416793.579 7276221.783,416791.867 7276246.196,416789.637 7276270.203,416790.294 7276298.86,416787.102 7276331.296,416784.799 7276349.814,416782.323 7276372.739),(416782.323 7276372.739,416781.314 7276382.949,416780.162 7276392.716,416777.781 7276408.359,416775.217 7276425.149,416768.816 7276460.721,416765.397 7276475.053,416757.709 7276499.292,416747.552 7276522.804,416744.02 7276534.088,416739.487 7276548.383,416732.784 7276569.563,416723.644 7276596.533,416714.376 7276620.584,416704.497 7276643.3,416695.13 7276666.009,416680.763 7276696.489,416662.895 7276732.152,416644.967 7276764.228,416628.04 7276794.75,416612.494 7276817.471,416598.527 7276838.834,416590.467 7276849.614,416572.666 7276873.742,416553.592 7276898.403,416536.253 7276919.449,416522.455 7276937.616,416508.523 7276954.809,416495.747 7276970.742,416469.922 7277001.326,416418.71 7277063.51,416390.713 7277097.63,416368.347 7277124.653,416345.818 7277152.037,416324.012 7277178.534,416297.986 7277210.13,416271.125 7277242.633,416253.012 7277264.791,416237.683 7277283.653,416219.236 7277305.952,416202.605 7277326.25,416175.596 7277359.028,416148.912 7277391.42,416139.32 7277403.241,416134.673 7277408.98,416126.901 7277419.07,416116.452 7277431.879,416101.678 7277449.843,416088.012 7277466.365,416071.752 7277486.055,416049.894 7277513.113,416031.369 7277535.565,416020.825 7277548.05,415989.592 7277585.83,415953.401 7277630.169,415912.735 7277680.669,415886.506 7277713.441,415835.479 7277778.141,415776.09 7277855.321,415726.556 7277921.541,415676.735 7277990.189,415641.634 7278040.472,415602.478 7278097.423,415558.761 7278163.878,415511.649 7278238.357,415476.072 7278296.296,415463.359 7278317,415417.66 7278390.668,415372.7 7278461.616,415332.527 7278522.038,415285.197 7278589.623,415257.015 7278627.334,415240.062 7278648.051,415217.921 7278676.092,415180.322 7278719.933,415148.639 7278755.267,415115.447 7278790.935,415089.849 7278816.786,415001.22 7278901.888,414915.363 7278978.766,414830.05 7279054.008,414777.595 7279100.755,414707.906 7279161.894,414705.864 7279163.667,414704.047005476 7279165.37276812),(414703.822 7279165.584,414609.882 7279248.575,414527.252 7279321.65,414451.115 7279389.205,414377.08 7279452.965,414283.214 7279536.886,414276.208 7279542.945,414203.443 7279608.589,414132.892 7279669.574,413884.552 7279893.023,413805.749 7279971.028,413766.788 7280012.2,413706.307 7280076.586,413606.164 7280196.925,413600.283 7280203.542,413547.295 7280273.555,413491.5 7280343.936,413424.274 7280429.768,413385.064 7280481.18,413319.155 7280563.706,413268.013 7280629.797,413190.39 7280730.073,413119.258 7280821.993,413042.494 7280924.628,413028.141 7280945.069,413000.787 7280984.863,412976.201 7281021.838,412923.09 7281107.771,412865.635 7281207.731,412814.759 7281305.41,412814.079 7281306.718,412759.913 7281414.639,412637.648 7281660.15,412600.653 7281734.776,412600.536 7281735.01,412540.267 7281856.461,412511.456 7281914.053,412409.161 7282122.248,412374.036 7282192.154,412325.374 7282292.672,412271.386 7282401.486,412218.172 7282508.506,412149.352 7282647.806,412126.229 7282697.144,412097.226 7282756.023,412058.142 7282825.285,412035.029 7282859.707,412011.237 7282892.272,411993.13 7282915.608,411975.569 7282936.114,411950.382 7282963.4,411936.404 7282977.863,411910.836 7283002.658,411874.49 7283033.381,411842.853 7283057.878,411799.322 7283087.469,411763.353 7283108.673,411732.228 7283124.712,411702.578 7283137.883,411663.033 7283155.134,411630.045741014 7283168.31224486),(411629.766 7283168.424,411606.063 7283176.708,411605.968 7283176.744,411592.444 7283181.846,411588.942 7283183.063,411569.614 7283189.699,411558.674 7283193.586,411475.911 7283224.53,411454.378 7283232.637,411432.328 7283241.134,411311.676 7283287.745,411275.957 7283301.287,411234.45 7283317.136,411194.89 7283332.757,411157.544 7283346.353,411122.813 7283360.188,411084.528 7283374.954,411052.137 7283387.359,411010.001 7283398.738,411008.115 7283399.223,411000.297 7283401.168,410998.502 7283401.614,410990.729 7283403.433,410965.247 7283409.894,410937.847 7283415.166,410908.589 7283421.124,410898.322 7283422.987,410885.227 7283425.326,410858.1 7283429.336,410826.465 7283431.337,410809.948 7283432.236,410792.441 7283433.429,410773.663 7283434.038,410753.159 7283433.432,410732.287 7283432.818,410707.497 7283432.008,410680.879 7283430.118))'));

INSERT INTO yllapitokohdeosa (id, yllapitokohde, nimi, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, tr_ajorata, tr_kaista, sijainti)
VALUES (667, (SELECT id
              FROM yllapitokohde
              WHERE nimi = 'Oulun ohitusramppi'), 'Oulun kohdeosa', 20, 4, 334, 10, 10, 0, 11, ST_GeomFromText(
                 'MULTILINESTRING((436072.124951972 7216477.52566194,436104.5012 7216502.842,436160.7231 7216547.1962,436209.9699 7216586.1392,436292.3231 7216651.5738,436337.442 7216687.5628,436395.232 7216733.5357,436432.8231 7216763.1058,436446.197 7216773.7061,436458.2231 7216783.135,436478.5852 7216794.933,436498.412 7216809.4438,436514.9929 7216822.1102,436536.8648 7216839.5243,436564.4618 7216861.4349,436582.7442 7216875.9713,436594.5708 7216885.2209,436598.8398 7216888.665,436602.4989 7216891.7857,436614.2648 7216901.0318,436631.7188 7216915.2889,436639.4539 7216921.416,436661.4092 7216938.8051,436694.6318 7216965.8113,436717.157 7216984.9007,436737.3518 7217003.2177,436744.9982 7217013.2058,436755.8272 7217022.7127,436772.7911 7217037.0943,436782.5261 7217046.0449,436806.1531 7217068.4927,436823.251 7217085.7847,436844.0407 7217107.9598,436871.2399 7217138.9229,436899.0221 7217173.794,436925.4322 7217210.0123,436946.2279 7217240.8378,436963.3502 7217268.0691,436980.4993 7217297.7928,436997.5293 7217330.3538,437019.2302 7217373.29,437034.8249 7217405.3501,437046.539 7217429.905,437060.9717 7217461.0402,437069.892 7217480.8782,437116.3098 7217579.0352,437147.2479 7217644.5848,437174.693 7217702.5398,437210.4557 7217779.7408,437234.7122 7217831.3477,437253.9529 7217873.1738,437275.0631 7217918.1087,437305.4241 7217982.8989,437335.7553 7218046.9089,437363.1402 7218106.9329,437392.1868 7218166.9878,437402.761 7218188.5637,437403.8657 7218190.7911,437405.145 7218193.2728,437443.0177 7218273.7048,437464.9641 7218320.7283,437491.9887 7218377.0872,437505.5943 7218406.0551,437516.655 7218430.4909,437525.5949 7218440.5779,437531.6499 7218451.9888,437540.5547 7218469.7722,437562.9251 7218516.988,437565.726 7218522.7131,437568.8021 7218528.3709,437577.365 7218546.7201,437595.7338 7218586.2437,437607.9148 7218613.5739,437614.594 7218630.4562,437617.6218 7218645.3487,437628.4788 7218667.837,437641.0898 7218694.7033,437656.535 7218727.9998,437684.9771 7218788.0779,437710.757 7218843.145,437720.735 7218864.4458,437744.7449 7218915.9658,437760.9251 7218950.1627,437770.9263 7218963.4091,437776.7872 7218973.9237,437783.9869 7218987.9843,437799.4982 7219019.6108,437805.561 7219031.9079,437810.4148 7219042.3218,437818.4697 7219059.2958,437826.3418 7219076.4908,437829.9187 7219084.2873,437843.6011 7219114.4922,437863.7149 7219159.7273,437884.1378 7219205.3673,437898.4622 7219236.113,437920.3049 7219283.0061,437935.089 7219314.7892,437950.0929 7219347.034,437972.926 7219395.522,438019.4987 7219491.1317,438054.9112 7219563.8107,438063.9262 7219582.388,438066.4061 7219587.2382,438068.9843 7219592.6161,438070.0878 7219595.0472,438071.2069 7219597.6938,438074.8481 7219605.0722,438097.1071 7219652.5781,438104.557 7219668.5111,438111.2088 7219683.4042,438117.6271 7219698.4433,438119.49 7219703.0678,438120.7383 7219713.6139,438134.6618 7219743.4317,438149.9969 7219776.1589,438165.7131 7219809.5221,438186.2051 7219852.675,438208.0811 7219896.2401,438223.7782 7219925.2528,438230.433 7219937.6148,438250.464 7219971.477,438274.714 7220009.4731,438308.0373 7220058.0778,438342.5671 7220106.533,438380.2779 7220158.1239,438421.4328 7220214.8038,438457.8542 7220264.845,438499.7303 7220322.504,438519.1592 7220348.7628,438524.4751 7220357.5782,438525.3089 7220358.7961,438542.5801 7220381.1111,438582.177 7220435.373,438617.3138 7220482.0922,438651.8419 7220524.7259,438676.5933 7220554.256,438706.9709 7220585.2811,438733.4399 7220610.93,438772.4002 7220646.1531,438807.7978 7220675.0067,438838.419 7220697.8922,438838.8728 7220698.2311,438862.5469 7220714.2028,438866.854 7220717.0049,438867.2488 7220717.267,438923.0157 7220752.312,438972.4679 7220780.3741,439016.1462 7220805.6097,439082.6599 7220843.3121),(439082.6599 7220843.3121,439094.633 7220850.1652,439143.6969 7220877.5121,439216.4581 7220918.7063,439285.4363 7220958.8332,439337.4 7220988.1192,439349.1408 7220994.7329,439400.7989 7221023.8992,439518.5641 7221090.6821,439580.0448 7221125.6492,439625.9439 7221151.7608,439633.6248 7221156.1298,439694.5349 7221190.4977,439697.7992 7221192.3392,439709.4698 7221198.343,439726.6463 7221208.2871,439742.6751 7221217.6492,439747.1567 7221220.1762,439808.8173 7221254.503,439815.5888 7221258.4343,439851.654 7221279.367,439869.6399 7221289.8071,439936.8081 7221328.279,439936.8969 7221328.3302,440010.116 7221370.8048,440063.6162 7221404.6052,440102.028 7221432.1861,440129.653 7221453.3558,440184.6153 7221499.5962,440231.4101 7221540.171,440234.348 7221542.7599,440255.9012 7221561.7552,440275.9132 7221579.8,440337.105 7221633.0662,440425.5211 7221710.7222,440489.9938 7221767.3051,440542.8288 7221813.8438,440587.6279 7221853.5241,440646.045 7221904.9982,440700.039 7221952.8013,440727.664 7221976.8261,440763.2527 7222007.7773,440783.921 7222026.161,440797.4628 7222037.9191,440824.8269 7222061.6789,440836.9198 7222072.636,440856.1849 7222090.09,440877.591 7222109.5171,440878.5832 7222110.4182,440910.819 7222139.0759,440950.7018 7222173.7612,440995.1108 7222213.0002,441041.6162 7222254.1009,441095.5208 7222301.6687,441150.5468 7222350.3139,441205.3293 7222398.5178,441260.8401 7222448.2392,441322.3893 7222502.9568,441377.3688 7222550.6747,441418.3718 7222586.3892,441443.7438 7222609.1848,441474.4347 7222635.9527,441513.2902 7222669.5768,441551.4542 7222702.7232,441584.2932 7222729.4578,441613.4041 7222752.0021,441642.1809 7222771.8169,441681.6027 7222798.0101,441717.1469 7222820.7873,441717.9038 7222821.2733,441751.6499 7222843.5501,441791.1539 7222868.716,441840.6758 7222900.5617,441879.7963 7222926.1731,441930.6057 7222959.2689,441981.0591 7222991.3927,442041.0991 7223030.839,442091.2142 7223063.2117,442130.5627 7223087.844,442178.0901 7223117.0842,442213.4198 7223137.8251,442235.764 7223150.6558,442255.624 7223161.676,442281.775 7223175.6669,442296.795 7223183.2013,442309.6841 7223185.8128,442318.68 7223189.8072,442341.6912 7223199.7912,442375.0121 7223213.5468,442417.348 7223230.2611,442450.7332 7223241.9037,442485.7681 7223253.4188,442491.7809 7223255.43,442498.095 7223257.3072,442509.6589 7223260.8591,442521.570937664 7223264.30015035),(442521.7982 7223264.3658,442534.9029 7223267.879,442585.8862 7223280.0272,442630.8759 7223289.792,442685.3082 7223301.2928,442729.7898 7223310.4269,442748.1968 7223314.5422,442759.5642 7223318.854,442780.9031 7223322.7311,442812.2223 7223327.6641,442839.5221 7223332.0189,442862.9341 7223335.4451,442883.5017 7223338.4979,442906.4891 7223341.9111,442924.114 7223344.9621,442941.5413 7223347.9203,442963.5043 7223351.8492,442994.646 7223358.0781,443023.6098 7223364.93,443050.7042 7223372.4049,443077.7979 7223380.1912,443101.1521 7223387.7149,443116.1149 7223393.6627,443123.6071 7223396.6763,443150.8283 7223406.8948,443172.3172 7223415.7722,443198.2032 7223427.4708,443222.6752 7223439.114,443233.3828 7223444.6253,443295.004 7223479.335,443320.1479 7223494.5831,443355.2239 7223517.0351,443404.613 7223548.87,443444.5261 7223574.114,443469.62 7223589.6777,443488.4641 7223600.8468,443510.9369 7223614.4112,443527.0551 7223623.2809,443545.2869 7223632.6413,443558.3773 7223638.5081,443568.3422 7223643.4459,443581.473 7223650.2037,443612.3742 7223662.3138,443636.0351 7223672.552,443658.8837 7223681.386,443686.0543 7223690.6987,443712.5209 7223699.915,443745.329 7223711.4122,443777.6327 7223722.5099,443800.439 7223729.606,443832.8398 7223740.1998,443864.3347 7223750.4958,443915.1412 7223768.0409,443950.8211 7223779.1749,444000.4431 7223795.8338,444062.6271 7223816.0638,444130.3028 7223838.835,444219.4788 7223867.439,444312.0542 7223898.2729,444405.5848 7223929.526,444487.8541 7223956.509,444529.9041 7223971.1252,444615.9403 7223998.1171,444682.0818 7224019.9788,444728.4008 7224035.293,444774.5178 7224050.3029,444819.118 7224065.1132,444828.797 7224068.278,444865.9432 7224080.4262,444910.9478 7224095.336,444967.4818 7224114.1747,445035.9501 7224137.2479,445105.6311 7224159.7117,445166.0088 7224179.9631,445209.8687 7224195.054,445245.1651 7224206.7412,445300.9909 7224224.7717,445344.883 7224239.2801,445381.7957 7224251.2681,445402.5313 7224258.2951,445431.454 7224268.0939,445489.5043 7224286.8307,445539.6658 7224303.2521,445602.8742 7224323.398,445665.577 7224344.0508,445699.8609 7224354.9311,445723.3003 7224362.924,445751.6173 7224371.6859,445796.4188 7224386.0913,445825.2712 7224395.7351,445869.6701 7224410.2412,445918.2129 7224426.1593,445953.913 7224437.3391,445986.2763 7224448.3218,446015.0858 7224457.089,446049.2959 7224468.5231,446104.7501 7224486.4077,446175.2011 7224509.2438,446251.8239 7224534.2162,446272.937 7224541.0341,446297.3007 7224548.9021,446317.2519 7224555.3901,446439.3009 7224595.087,446502.068 7224615.7982,446569.8658 7224638.0429,446635.438 7224658.461,446671.7992 7224670.6271,446716.2148 7224684.282,446774.1978 7224701.9773,446830.5602 7224719.1139,446858.6081 7224727.7298,446858.913 7224727.8239,446859.5627 7224728.0312,446898.3932 7224740.4259,446954.5698 7224757.7341,447066.025 7224792.5778,447086.0632 7224798.7061,447096.1221 7224801.7857,447108.9481 7224805.7248,447123.7501 7224810.226,447157.4909 7224820.7102,447203.247 7224834.8142,447212.9648 7224837.8099,447258.2451 7224852.0801,447303.2699 7224865.0043,447303.5689 7224865.0972,447356.146 7224881.506,447414.382 7224899.524,447526.4167 7224934.3481,447588.6038 7224953.5531,447656.4242 7224975.2207,447722.6598 7224995.3982,447771.1919 7225010.56,447794.8713 7225017.6709,447827.013 7225027.8371,447883.884 7225045.0738,447959.5611 7225068.3809,448027.952 7225089.5322,448095.5979 7225110.6263,448152.3588 7225128.3102,448153.1188 7225128.5472,448208.5539 7225145.158,448260.2008 7225161.1422,448310.822 7225177.0549,448367.7717 7225194.2939,448424.1771 7225211.362,448478.81 7225228.6582,448515.6602 7225239.7952,448559.1229 7225253.303,448591.9548 7225263.4311,448610.6852 7225270.5808,448618.746 7225272.8088,448627.4602 7225275.0272,448659.5001 7225285.4638,448682.7197 7225293.8082,448707.0948 7225303.0471,448737.6159 7225314.4877,448772.4311 7225329.93,448812.6188 7225347.0428,448849.703 7225364.2729,448903.8178 7225389.2899,448964.9418 7225416.5331,448998.2877 7225433.0652,449029.4098 7225447.5039,449062.913 7225462.5472,449105.4531 7225482.5418,449147.8872 7225502.0249,449191.1808 7225521.6849,449229.611 7225539.2801,449230.5841 7225539.7262,449267.6082 7225557.1242,449299.096 7225570.9829,449302.5901 7225572.85,449305.5751 7225574.1078,449307.9781 7225575.119,449328.2652 7225584.9862,449375.8641 7225606.979,449425.6111 7225629.453,449521.9462 7225673.4672,449577.9799 7225699.1828,449633.0131 7225724.0521,449685.7022 7225748.398,449685.9487 7225748.5153,449727.751 7225767.7322,449782.6478 7225793.6271,449820.1269 7225809.9918,449863.4061 7225830.239,449905.9838 7225850.1771,449953.2753 7225871.7721,449988.0452 7225887.306,450029.122 7225906.324,450080.318 7225929.9689,450124.2041 7225949.8188,450170.6928 7225971.2041,450216.2763 7225992.4793,450257.6539 7226011.5002,450305.4463 7226033.4013,450346.9228 7226052.6229,450397.4208 7226075.6579,450430.89 7226090.3748,450448.5262 7226098.2993,450583.7049 7226157.1661,450695.9189 7226207.3193,450764.0781 7226237.1472,450783.3129 7226245.5428,450819.0661 7226261.1488,450874.0552 7226285.3529,450935.4358 7226312.876,450946.2517 7226317.7352,450960.8012 7226324.3638,450973.9029 7226330.3217,450991.2182 7226337.9872,451099.9707 7226385.6342,451175.2118 7226418.951,451240.8209 7226446.788,451348.5848 7226494.0461,451457.2921 7226541.237,451543.6999 7226577.3648,451609.5008 7226604.8093,451632.0219 7226614.0297,451697.8728 7226640.8078,451752.0519 7226662.831,451797.3548 7226679.4721,451830.6418 7226690.8431,451886.5231 7226708.9647,451932.9671 7226722.6387,451988.4809 7226738.9487,452054.6337 7226757.7332,452116.6689 7226776.1783,452172.4501 7226792.1172,452252.006 7226815.69,452287.7478 7226826.4958,452357.0297 7226846.827,452403.553 7226860.5111,452449.3592 7226874.2399,452505.6567 7226890.535,452564.4723 7226908.2291,452654.4939 7226934.4408,452757.2159 7226965.059,452758.1521 7226965.3312,452850.2558 7226992.0229,452928.9053 7227015.3039,452998.4218 7227035.3183,453072.5069 7227058.1871,453260.4342 7227112.723,453564.9129 7227201.949,453769.0328 7227260.627,453836.2237 7227280.823,453936.801 7227309.3562,454204.4069 7227386.484,454343.0321 7227426.5228,454486.0811 7227468.3381,454623.1328 7227508.4442,454721.3422 7227536.445,454825.0098 7227566.476,454893.7212 7227586.298,454951.382 7227603.1821,454997.991 7227616.525,455059.5318 7227634.8348,455156.2749 7227662.4592,455297.9613 7227704.3651,455377.2248 7227726.7539,455508.473154967 7227764.8910053),(455508.49 7227764.8959,455820.8092 7227855.9818,455952.3901 7227894.3858,456095.8667 7227935.3179,456192.031 7227963.121,456231.8822 7227974.645,456317.533 7227999.6073,456412.5103 7228027.9612,456505.6503 7228058.0452,456640.365 7228103.6591,456890.7271 7228188.0467,456983.1828 7228219.8912,457029.3582 7228235.4228,457064.1859 7228247.6413,457088.5198 7228255.9988,457187.7011 7228289.013,457309.067 7228330.2929,457403.2528 7228361.982,457493.8277 7228392.9522,457559.0217 7228415.7228,457565.711 7228417.9972,457725.743 7228472.4009,457837.2333 7228510.2611,457971.6342 7228555.7279,458083.9613 7228593.5798,458222.571 7228640.6063,458385.7742 7228695.9759,458622.1371 7228776.2102,458722.4857 7228810.4387,458788.3319 7228832.5887,458895.1911 7228868.9779,459002.9503 7228905.2069,459115.2482 7228943.708,459197.7599 7228971.514,459317.5833 7229011.4349,459429.2249 7229049.93,459452.4088 7229057.5377,459550.6848 7229090.7568,459635.359 7229119.5258,459730.7091 7229152.412,459802.1457 7229176.2879,459858.2598 7229195.3118,459901.6183 7229209.2211,459960.8501 7229227.771,459994.071 7229236.5638,460044.379 7229250.42,460077.5993 7229259.3772,460144.6258 7229276.6008,460194.5372 7229290.0372,460276.5241 7229311.4422,460310.5633 7229320.4017,460389.5969 7229340.7228,460459.1462 7229358.9749,460512.8572 7229373.5899,460513.3182 7229373.7668,460530.7591 7229378.084,460623.7818 7229401.4972,460663.4418 7229412.4947,460705.711 7229423.6662,460806.6712 7229450.3508,460895.715707064 7229473.83001087),(460895.7388 7229473.8361,460905.516260949 7229475.93401739))'));
INSERT INTO yllapitokohdeosa (id, yllapitokohde, nimi, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, tr_ajorata, tr_kaista, sijainti)
VALUES (668, (SELECT id
              FROM yllapitokohde
              WHERE nimi = 'Nakkilan ramppi'), 'Nakkilan kohdeosa', 20, 12, 1, 14, 2, 0, 1, ST_GeomFromText(
                 'MULTILINESTRING((474515.874868717 7235208.68684686,474654.2953 7235286.9223,474770.2672 7235352.3551,474816.5641 7235379.0838,474851.8379 7235400.1368,474871.8558 7235412.1051,474905.3262 7235433.3308,474938.8239 7235457.4712,474970.5321 7235481.144,475015.0102 7235516.522,475064.2319 7235555.7413,475102.1201 7235585.228,475114.4291 7235595.2751,475152.3722 7235626.0172,475220.0639 7235679.2351,475310.6633 7235751.9981,475415.7299 7235835.8819,475462.0042 7235872.4557,475537.2262 7235932.407,475599.1232 7235981.8741,475640.9022 7236015.008,475708.3558 7236068.7959,475791.753 7236135.0672,475858.9707 7236189.5853,475909.7421 7236229.6818,475958.881 7236268.7397,476007.1999 7236307.0782,476055.4931 7236345.6841,476125.3498 7236401.6922,476203.191 7236463.5487,476261.8678 7236510.4311,476281.5201 7236526.1318,476348.1708 7236579.1532,476400.552 7236621.009,476476.8519 7236681.7423,476551.4498 7236741.107,476604.8041 7236783.6042,476691.4823 7236852.6932,476738.6732 7236890.2932,476816.1827 7236950.7709,476900.911 7237020.0052,476918.402 7237033.9222,477072.8589 7237156.5447,477223.3601 7237277.3347,477412.5899 7237427.4077,477599.656 7237577.8202,477776.9639 7237717.197,477938.708 7237847.6457,478046.1551 7237931.816,478130.6988 7237999.1879,478243.6697 7238090.4019,478306.737 7238139.4741,478311.109 7238142.7348,478315.1808 7238145.9371,478340.707 7238165.6019,478379.9621 7238192.0607,478404.9749 7238208.1658,478448.6877 7238231.9292,478484.4272 7238250.3701,478527.8768 7238269.7442,478569.9601 7238287.4799,478632.2281 7238308.047,478723.3277 7238330.0808,478773.9568 7238338.0857,478840.8428 7238344.7882,478914.2078 7238349.2322,478963.3861 7238349.7039),(478963.3861 7238349.7039,478986.6581 7238347.1948,479057.4539 7238342.6757,479192.8113 7238337.059,479384.5329 7238328.4329,479428.9472 7238326.0251,479507.7718 7238321.7103,479525.8958 7238321.1558,479533.6458 7238321.207,479537.8302 7238321.151,479686.3089 7238315.3259,479795.7273 7238310.5132,479829.7861 7238308.6711,479835.5082 7238308.6937,479891.9427 7238306.0852,479912.605 7238305.4182,479948.4273 7238303.192,480057.8808 7238298.5609,480183.4781 7238294.2127,480329.4603 7238288.2089,480452.4919 7238281.4523,480592.7668 7238275.7278,480656.9299 7238272.9692,480796.156 7238265.835,480903.8639 7238262.8542,480903.9878 7238262.8482,480997.5173 7238258.4733,481081.2218 7238255.9469,481118.025 7238253.7058,481197.217 7238248.5691,481293.0882 7238244.6271,481357.1208 7238241.6237,481395.588 7238239.0747,481455.0872 7238237.4268,481494.9331 7238235.4228,481558.3029 7238233.2132,481559.597 7238232.6999,481659.7771 7238229.0598,481900.827 7238218.07,482171.0093 7238206.4971,482384.3443 7238197.419,482609.2278 7238187.0598,482873.8261 7238174.3322,483101.032316641 7238165.85985888),(483101.1948 7238165.8538,483196.7057 7238161.8618,483205.2258 7238161.5068,483258.4902 7238158.3998,483300.0709 7238156.8168,483407.2118 7238151.4651,483613.2459 7238143.543,483875.0367 7238132.0541,484022.519 7238125.6619,484107.5499 7238121.8772,484180.2801 7238118.52,484269.2077 7238113.6561,484320.985 7238111.1482,484321.272 7238111.1678,484403.7932 7238108.7213,484468.4232 7238105.6512,484544.5808 7238101.3691,484620.268 7238098.1793,484695.5817 7238095.3212,484747.2792 7238095.0181,484756.5741 7238095.136,484810.652 7238095.8459,484841.4947 7238095.5171,484876.5672 7238095.691,484916.6328 7238095.5207,484923.9992 7238095.943,484982.1031 7238095.9108,485089.3829 7238096.345,485102.3071 7238096.4361,485168.6058 7238096.1991,485201.0149 7238096.0651,485267.6228 7238097.2961,485304.6248 7238098.2007,485343.2403 7238097.4932,485378.9547 7238097.682,485418.906 7238097.252,485456.4041 7238097.682,485495.1911 7238097.9518,485502.5307 7238098.062,485518.7782 7238098.2019,485542.9852 7238097.4313,485570.7567 7238097.6832,485603.6548 7238097.495,485661.038 7238097.579,485715.93 7238098.0149,485770.2313 7238098.1721,485902.7788 7238098.6403,486108.0303 7238099.0309,486299.3278 7238099.1042,486368.2661 7238099.5783,486427.2568 7238099.5789,486476.4892 7238100.0851,486597.2018 7238099.396,486769.1889 7238099.3019,487099.8281 7238100.53,487303.4841 7238100.6967,487410.1218 7238101.6609,487466.0882 7238102.1677,487580.199 7238102.434,487638.0367 7238105.8078,487688.864 7238110.8659,487736.5242 7238118.2919,487788.2657 7238128.4771,487847.6781 7238145.0902,487893.5313 7238160.4878,487954.9697 7238181.3562,488011.325 7238201.7302,488102.1751 7238235.1238,488201.9079 7238270.9711,488243.9019 7238286.243,488317.9602 7238313.373,488394.1732 7238340.7288,488476.2221 7238369.2019,488532.9408 7238389.7927,488561.6532 7238400.1513,488614.1982 7238418.8572,488693.6183 7238447.3987,488726.929 7238458.8352,488766.1603 7238473.5533,488817.6338 7238491.9149,488894.465 7238519.291,488945.2721 7238539.1749,488988.6061 7238556.0357,489021.9223 7238572.1051,489053.2463 7238587.6462,489092.599 7238608.0828,489128.3152 7238629.352,489130.4003 7238631.0582,489178.1349 7238664.7871,489214.7849 7238690.8201,489247.686 7238717.6238,489290.9069 7238756.618,489361.0839 7238833.4498,489393.546 7238871.7562,489394.7758 7238873.3427,489425.0659 7238916.946,489448.5839 7238953.6097,489468.7091 7238989.0121,489493.7177 7239032.4903,489526.5622 7239101.6882,489563.4189 7239175.1098,489594.8001 7239233.7849,489642.2608 7239331.2307,489679.75 7239399.5389,489701.9762 7239438.7773,489724.882 7239472.421,489741.4641 7239497.441,489764.6087 7239528.1992,489781.441 7239549.0938,489809.01 7239580.6137,489831.4631 7239606.084,489895.3969 7239668.1799,489938.3188 7239703.2922,489975.9617 7239731.2441,490016.3078 7239759.0782,490068.9141 7239791.4873,490107.5998 7239811.4712,490152.2321 7239833.2359,490256.222 7239872.619,490315.5051 7239889.4328,490364.2081 7239901.3291,490370.596 7239902.7191,490426.999 7239912.3451,490477.5208 7239918.0923,490573.099 7239921.917,490625.4403 7239920.5198,490668.4509 7239916.8368,490708.5957 7239912.067,490753.1399 7239904.03,490797.3238 7239894.6839,490846.6801 7239882.9942,490953.8539 7239857.577,490974.4697 7239852.4832,491002.8219 7239845.477,491131.9949 7239813.5331,491307.1892 7239770.9983,491355.8499 7239759.0079,491386.271 7239750.9,491402.4988 7239747.4427,491485.5268 7239726.1069,491638.1893 7239688.5349,491666.623 7239681.2047,491740.1429 7239664.1563,491793.281 7239650.3357,491848.6132 7239635.5778,491931.3202 7239605.8112,491962.316 7239592.8608,491999.0429 7239575.586,492008.3717 7239571.3802,492046.394 7239554.237,492092.9869 7239531.5641,492143.4182 7239507.609,492228.2918 7239469.5891,492263.8491 7239452.1083,492271.9171 7239448.4628,492287.1312 7239440.5461,492314.1279 7239428.3961,492328.0812 7239421.4042,492336.7758 7239416.5272,492359.7399 7239406.199,492570.199 7239312.0978,492635.6509 7239283.5092,492852.5372 7239187.8929,492854.2077 7239187.2062,492855.9289 7239186.4987,492917.5609 7239161.6228,492965.6748 7239143.341,493010.4358 7239130.568,493060.586 7239118.3817,493097.3909 7239111.0522,493122.517 7239107.1262,493144.184 7239104.208,493172.9703 7239100.4179,493194.1043 7239097.6301,493240.0492 7239095.0447,493256.5903 7239094.1198,493313.7591 7239095.6897,493339.6552 7239097.2358,493378.2188 7239100.7978,493433.2002 7239108.0369,493490.9348 7239119.0058,493539.3853 7239130.8301,493578.5528 7239143.5071,493619.2562 7239157.741,493623.8807 7239159.3228,493633.4639 7239162.9599,493641.0811 7239165.868,493642.5152 7239166.4081,493668.4011 7239177.4331,493713.3771 7239198.5028,493760.484 7239223.9182,493801.7252 7239248.0491,493848.7892 7239278.718,493887.1938 7239309.628,493924.0601 7239339.5977,493960.2837 7239372.6542,493990.6572 7239402.5733,494025.5968 7239441.493,494061.4828 7239487.734,494117.7411 7239570.0449,494160.1168 7239642.9413,494169.5147 7239665.024,494197.8252 7239716.8132,494261.1438 7239848.3071,494297.5461 7239922.5381,494340.9808 7240012.368,494378.236 7240083.8999,494419.1711 7240169.6669,494464.7301 7240261.0131,494481.4051 7240299.158,494513.0948 7240361.3962,494548.4853 7240432.9002,494619.1638 7240575.2101,494663.0981 7240665.5408,494706.1171 7240752.7008,494752.6612 7240847.0069,494806.1352 7240957.4651,494829.4418 7241004.6453,494859.2191 7241064.9241,494915.7799 7241179.4917,494954.7509 7241256.9078,495004.4592 7241355.5549,495056.932 7241466.511,495103.1063 7241559.3401,495153.2201 7241649.1438,495211.5158 7241742.971,495262.006 7241814.0188,495310.1837 7241876.5489,495320.1891 7241889.2122,495350.4352 7241923.224,495370.5817 7241947.5472,495389.1101 7241969.026,495426.4671 7242010.8669,495477.17 7242069.2061,495524.5092 7242124.1082,495566.6622 7242172.4628,495621.6561 7242234.2472,495637.9738 7242251.2087,495648.8439 7242265.6242,495673.3767 7242291.83,495705.4458 7242328.9518,495727.725 7242356.3879,495746.958 7242379.4652,495768.2611 7242411.5289,495797.1618 7242456.0362,495844.6201 7242529.1791,495882.625 7242589.857,495916.6862 7242643.384,495950.4443 7242696.2893,496011.8898 7242791.5262,496064.5282 7242871.8331,496114.4069 7242949.9471,496160.3423 7243020.9097,496209.3222 7243096.2318,496248.8691 7243157.2348,496298.7417 7243235.9671,496357.7568 7243327.1602,496398.8111 7243391.8891,496440.1768 7243456.3118,496483.375 7243522.9179,496521.38 7243583.5958,496574.3227 7243664.2147,496621.7751 7243737.9752,496662.2361 7243800.2248,496673.186 7243817.3418,496706.9637 7243868.3918,496752.9057 7243938.7368,496807.6578 7244023.7022,496851.13 7244093.7119,496894.3538 7244157.8452,496945.6421 7244238.2873,496980.1548 7244295.739,497001.4722 7244328.5191,497011.022 7244340.0271,497046.1682 7244396.5641,497122.9143 7244516.0093,497167.9969 7244584.2353,497211.7919 7244654.594,497238.084 7244693.5941,497270.7069 7244739.5522,497337.9592 7244829.2599,497417.4698 7244932.656,497473.2861 7245004.852,497526.0782 7245074.1899,497587.5911 7245153.9477,497646.4608 7245228.6969,497648.8008 7245232.858,497651.9209 7245237.5379,497675.2358 7245267.0889,497698.923 7245298.5987,497741.8211 7245354.7039,497771.5001 7245394.4033,497790.1 7245415.3139),(497790.1 7245415.3139,497810.5169 7245442.715,497847.7108 7245491.5668,497874.7318 7245525.9419,497920.6327 7245585.8318,497959.0498 7245635.3323,498000.7699 7245688.9337,498021.1368 7245716.9607,498056.3891 7245760.2263,498092.692 7245807.5088,498126.2582 7245852.8701,498152.1883 7245894.0268,498186.761 7245949.0052,498218.1368 7246008.2382,498254.8578 7246076.8829,498295.7149 7246153.6712,498322.0939 7246201.955,498340.1811 7246235.1472,498360.897 7246274.0533,498383.3978 7246316.3647,498400.211 7246349.1639,498423.0292 7246391.174,498439.0289 7246420.644,498465.2471 7246468.5728,498494.5337 7246524.6631,498529.4591 7246590.7891,498577.4849 7246678.2701,498599.897 7246721.039,498636.2868 7246790.6038,498673.0738 7246860.804,498674.8748 7246864.1749,498704.1781 7246915.3417,498727.2673 7246959.1552,498737.3632 7246978.9599,498752.4481 7247010.3208,498760.7311 7247027.1608,498774.0639 7247054.0259,498789.9772 7247087.071,498797.729 7247106.5017,498806.386 7247127.373,498813.771 7247143.3382,498818.0048 7247152.726,498834.4881 7247192.3812,498848.8601 7247231.0961,498863.6948 7247268.1899,498876.4523 7247303.6357,498892.3031 7247349.6462,498899.9471 7247373.2762,499038.5341 7247793.1628,499049.365 7247825.962,499066.1031 7247879.5068,499069.5431 7247888.8059,499077.9559 7247906.036,499082.2338 7247914.7532,499095.3802 7247949.7101,499101.549 7247968.195,499106.8012 7247983.9738,499107.7023 7247986.6812,499119.8588 7248024.0263,499138.9381 7248082.3058,499152.1881 7248122.8729,499163.4251 7248156.9752,499165.4667 7248163.1731,499178.678 7248204.1333,499185.3387 7248224.9701,499213.8987 7248315.0263,499229.8698 7248364.1497,499246.8027 7248415.282,499262.0812 7248462.58,499263.5379 7248467.0938,499274.7862 7248508.0378,499276.8379 7248516.144,499276.8481 7248526.9158,499285.333 7248555.4389,499285.8642 7248557.5168,499290.3422 7248575.0358,499290.4518 7248575.4402,499297.0101 7248599.6192,499301.8972 7248617.6408,499303.4779 7248623.5262,499313.8108 7248662.051,499321.2583 7248691.8111,499324.7583 7248706.4928,499331.1278 7248723.6503,499343.9341 7248776.5299,499348.623 7248795.7807,499357.0192 7248835.2353,499367.3997 7248896.364,499369.5062 7248935.0122,499379.5021 7248993.8361,499381.072 7249003.072,499399.1603 7249121.5262,499401.5312 7249140.1249,499412.3311 7249171.599,499412.9117 7249174.1331,499426.9502 7249232.5151,499432.5961 7249256.0551,499438.0651 7249276.0629,499443.5669 7249297.1481,499458.8757 7249347.312,499475.4561 7249397.801,499479.2153 7249410.3703,499486.7139 7249439.7158,499562.4987 7249622.6172,499569.8581 7249634.8041,499580.2821 7249652.4338,499595.1532 7249679.8051,499616.3051 7249716.7768,499623.3023 7249729.109,499636.08 7249751.7629,499647.3498 7249771.0959,499655.1242 7249783.8468,499671.9892 7249811.6731,499686.6542 7249835.0202,499716.668 7249881.2307,499747.45 7249926.8553,499754.8951 7249943.55,499819.1892 7250027.0098,499830.889 7250041.7713,499844.3903 7250058.1533,499903.0182 7250124.2919,499911.795 7250132.4838,499921.695 7250141.9859,499942.4198 7250160.531,499958.7637 7250175.8452,499999.5088 7250214.4118,500019.6292 7250233.3112,500036.141666038 7250248.45710631),(500036.4829 7250248.7701,500057.8658 7250269.0709,500077.845 7250287.1062,500088.1 7250296.2999,500096.941 7250307.0592,500136.3348 7250336.5167,500238.5613 7250415.726,500245.2577 7250420.2171,500259.3647 7250429.9158,500273.48 7250438.8903,500274.1352 7250439.3203,500286.1613 7250447.0578,500303.7023 7250458.3668,500326.564 7250472.2743,500333.6041 7250476.5587,500376.1299 7250502.481,500403.0122 7250517.875,500417.1228 7250525.9317,500462.3441 7250550.324,500503.5729 7250572.6622,500546.1398 7250595.0159,500572.588 7250609.4219,500595.1263 7250621.6809,500632.818 7250641.3027,500673.3833 7250663.1859,500699.81 7250678.434,500795.5287 7250729.2882,500863.2092 7250765.706,500926.2169 7250799.3961,500971.3358 7250823.783,501044.1738 7250862.9291,501130.0861 7250909.1468,501184.1658 7250938.2113,501253.6442 7250976.0632,501307.6197 7251005.0478,501339.1039 7251021.2512,501388.0803 7251047.769,501424.5892 7251066.9341,501457.9798 7251084.9843,501500.0548 7251107.2701,501549.2533 7251133.7867,501602.2359 7251162.3122,501652.9912 7251189.9437,501698.4049 7251215.57,501749.8278 7251245.2068,501788.1169 7251269.717,501827.0718 7251294.8948,501852.4491 7251312.942,501872.7338 7251328.691,501903.6332 7251351.7123,501952.2921 7251388.9698,501989.3948 7251419.7531,502042.7831 7251465.9761,502097.7198 7251517.2353,502140.0348 7251557.1502,502182.1688 7251598.3581,502230.2363 7251643.371,502302.5972 7251712.4951,502320.7939 7251730.0658,502336.9199 7251745.3419,502405.617 7251812.8318,502460.5501 7251863.7288,502496.0072 7251897.5017,502531.6949 7251932.1632,502554.8061 7251953.791,502580.5831 7251980.4559,502605.9432 7252002.9382,502663.3699 7252058.1793,502704.0257 7252096.7309,502745.1663 7252137.5517,502774.1593 7252165.0891,502828.582 7252216.764,502853.5133 7252239.7091,502861.3657 7252246.9909,502882.5968 7252267.6282,502908.8549 7252292.793,502941.6178 7252323.9163,502975.5051 7252356.1431,503013.8859 7252393.4531,503045.2998 7252423.0279,503074.9139 7252450.1687,503107.9102 7252482.4039,503136.4077 7252509.3309,503169.1593 7252538.8962,503203.043 7252570.8991,503238.4751 7252601.5549,503269.8497 7252626.2319,503305.9311 7252654.6561,503326.9888 7252670.2913,503355.6602 7252691.2049,503381.8731 7252710.8048,503411.654 7252731.264,503444.1167 7252753.0388,503487.3078 7252779.6221,503527.8088 7252804.0019,503567.8452 7252826.1591,503602.0558 7252843.6881,503644.7729 7252866.9358,503687.4691 7252887.5111,503731.4951 7252907.4087,503774.847 7252926.4219,503832.2481 7252948.435,503879.5938 7252965.8551,503908.1717 7252975.4167,503922.9118 7252980.8602,503958.1683 7252990.3659,503990.9728 7252999.4457,504027.7908 7253009.3838,504063.0378 7253017.5531,504092.0331 7253023.7701,504128.6701 7253031.999,504171.2633 7253040.1081,504231.2509 7253051.6357,504263.4682 7253056.7891,504326.1609 7253065.2978,504369.1489 7253069.9009,504436.7043 7253075.3771,504483.399 7253079.0421,504537.4388 7253081.5762,504577.8117 7253083.0628,504637.0912 7253081.6549,504683.833 7253079.9331,504733.4538 7253076.4062,504761.9131 7253073.8822),(504761.9131 7253073.8822,504763.907240227 7253073.72921387))'));
INSERT INTO yllapitokohdeosa (id, yllapitokohde, nimi, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, tr_ajorata, tr_kaista, sijainti)
VALUES (669, (SELECT id
              FROM yllapitokohde
              WHERE nimi = 'Oulaisten ohitusramppi'), 'Oulaisten kohdeosa', 20, 19, 5, 21, 15, 0, 11, ST_GeomFromText(
                 'MULTILINESTRING((504766.898450568 7253073.49973469,504804.1901 7253070.6388,504860.7157 7253067.5002,504914.7829 7253063.046,504972.6313 7253058.1148,505020.4778 7253055.4938,505076.77 7253050.7978,505137.0489 7253043.844,505158.3359 7253042.342,505209.0829 7253037.5919,505264.471 7253031.3451,505310.9508 7253024.9499,505348.088 7253019.5232,505393.2057 7253009.7989,505443.6591 7252999.1402,505491.8677 7252986.2732,505551.6332 7252970.8631,505601.1569 7252955.5358,505633.1128 7252945.8937,505654.4362 7252939.7059,505683.176 7252931.1608,505731.8558 7252915.3529,505764.2577 7252904.5971,505846.9862 7252878.6629,505891.9121 7252864.6548,505936.2788 7252849.6908,505988.7041 7252832.2099,506053.4592 7252811.5631,506125.187 7252788.9289,506181.1439 7252770.6548,506236.2873 7252753.3991,506296.2892 7252733.9738,506346.6639 7252719.9752,506381.6399 7252710.3771,506422.656 7252700.7517,506439.9778 7252697.0581,506469.0077 7252691.9797,506541.5271 7252678.9299,506599.971 7252669.6362,506635.0458 7252665.1963,506682.7513 7252660.4437,506721.9122 7252657.4499,506756.1842 7252655.3839,506783.1189 7252654.7168,506812.6592 7252653.8128,506850.7279 7252653.275,506886.3513 7252653.2041,506927.0821 7252651.5318,506978.3799 7252646.8477,506992.1558 7252645.8353,507009.5521 7252644.0861,507038.788 7252639.9827,507061.988 7252636.8251,507091.9923 7252631.4031,507122.8053 7252624.693,507147.6418 7252620.2977,507174.52 7252614.0158,507198.7288 7252607.9852,507243.8073 7252595.9561,507302.5633 7252579.7861,507363.6509 7252562.6947,507467.1357 7252531.5929,507520.9558 7252518.0308,507619.6541 7252489.5679,507679.6322 7252471.479,507729.1708 7252457.7371,507789.4478 7252441.1758,507814.9401 7252434.0702,507845.2808 7252424.9492,507882.3258 7252414.54,507933.1412 7252400.2663,507977.8039 7252388.1151,508098.2967 7252353.7198,508204.5039 7252324.3159,508236.6777 7252314.8459,508305.0347 7252297.0298,508371.1048 7252282.1777,508434.3787 7252268.543,508454.9981 7252264.9071,508494.0263 7252257.9897,508524.5588 7252252.9412,508542.3469 7252250.5351,508565.6863 7252246.5163,508599.9279 7252241.9418,508641.9541 7252236.4001,508687.7632 7252230.597,508730.6869 7252225.9362,508758.4989 7252223.8749,508810.5543 7252219.3451,508857.4909 7252215.0893,508918.666 7252209.5768,508985.399 7252203.3413,509054.5749 7252196.4131,509102.643 7252191.3312,509146.1843 7252183.9672,509173.4978 7252179.638,509174.054 7252179.5499,509176.2832 7252179.102,509241.068 7252166.073,509304.202 7252152.4258,509354.2218 7252141.67,509424.4768 7252126.254,509455.2333 7252119.3479,509501.0121 7252109.3318,509519.6751 7252105.36,509542.4552 7252100.7343,509572.457 7252095.089,509598.0172 7252090.6032,509612.9193 7252087.7398,509643.2118 7252083.1498,509696.3362 7252077.5111,509740.4152 7252074.217,509780.9132 7252072.3452,509816.589 7252070.6098,509838.5859 7252070.6348,509864.0901 7252071.2381,509892.453 7252072.048,509898.4771 7252072.2458,509903.6447 7252072.4298,509941.2679 7252075.3933,509991.894 7252080.3251,510046.2792 7252087.096,510081.9299 7252093.079,510118.209 7252097.847,510144.8072 7252101.5097,510185.8209 7252105.996,510239.3438 7252114.587,510310.7072 7252125.8919,510382.756 7252138.9721,510421.9509 7252146.7942,510440.0928 7252150.4188,510509.9352 7252165.5251,510546.749 7252173.1703,510599.1862 7252184.443,510642.0283 7252193.586,510690.446 7252203.7862,510729.4932 7252212.0769,510773.901 7252221.8721,510828.5679 7252233.5688,510888.3721 7252246.7712,510935.0162 7252257.6568,510977.557 7252270.0641,510980.7938 7252271.0009,511033.4817 7252285.1662,511081.4998 7252299.8229,511125.2882 7252314.5219,511185.8488 7252336.6243,511255.3582 7252362.864,511302.7551 7252382.2018,511363.3812 7252410.7588,511388.2208 7252423.2,511428.502 7252443.5002,511460.9581 7252460.5398,511493.4172 7252477.802,511504.7823 7252483.8678,511526.9942 7252495.7212,511558.1151 7252512.7738,511602.682 7252538.5978,511650.868 7252569.951,511693.9031 7252598.461,511723.0497 7252618.4283,511753.0938 7252639.277,511776.024 7252655.6191,511801.2918 7252672.7331,511816.7751 7252683.8557,511839.8739 7252700.3211,511878.4369 7252724.8123,511930.6852 7252755.9129,511972.4178 7252779.6501,512016.2199 7252802.0479,512050.0298 7252818.3793,512071.5997 7252828.2137,512085.1511 7252834.5207,512106.1868 7252843.2367,512134.209 7252853.7299,512159.5482 7252863.4928,512175.3109 7252869.2561,512187.206 7252873.824,512205.492 7252879.751,512239.8212 7252890.583,512263.7251 7252897.6189,512275.2718 7252901.066,512309.869 7252910.4622,512344.892 7252918.819,512402.514 7252930.8273,512434.2478 7252937.1622,512486.09 7252944.6192,512533.1832 7252949.9292,512550.2691 7252951.1853,512574.7037 7252953.4073,512593.5168 7252954.5609,512642.165 7252957.2433,512694.9642 7252957.5589,512745.1537 7252957.1289,512764.4028 7252956.0301,512801.119 7252955.1201,512840.5021 7252954.2482,512846.0932 7252954.1291,512876.8049 7252953.3227,512925.0988 7252951.33,512929.7709 7252951.1948,512938.8651 7252951.464,512949.5268 7252950.3967,512983.9888 7252949.4689,513027.3389 7252948.0973,513088.3437 7252946.2677,513148.3778 7252944.4638,513209.712 7252942.7891,513256.1441 7252940.9953,513291.7448 7252939.956,513328.7981 7252938.7172,513370.2412 7252937.4231,513413.362 7252936.279,513466.7521 7252934.3149,513485.2781 7252934.5019,513507.1821 7252933.2202,513535.7052 7252931.8469,513558.0268 7252931.5562,513641.0988 7252929.0859,513665.8419 7252928.0472,513715.0708 7252926.578,513762.0347 7252925.316,513810.4459 7252923.5388,513878.648 7252921.6777,513936.0361 7252920.8707,513991.448 7252918.0537,514021.2831 7252917.9412,514063.987 7252915.472,514136.529 7252913.0511,514154.584 7252912.284,514196.8508 7252912.3531,514245.4507 7252911.8683,514280.4517 7252911.9791,514295.6081 7252912.3102,514323.046 7252913.5871,514356.7749 7252914.858,514380.6193 7252916.4231,514394.5851 7252917.6678,514431.103 7252922.1881,514488.1188 7252929.3128,514541.7423 7252936.9919,514568.0779 7252941.3282,514581.2528 7252943.0481,514588.8343 7252944.029),(514588.8343 7252944.029,514603.508136803 7252947.14003093))'));
INSERT INTO yllapitokohdeosa (id, yllapitokohde, nimi, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, tr_ajorata, tr_kaista, sijainti)
VALUES (670, (SELECT id
              FROM yllapitokohde
              WHERE nimi = 'Kuusamontien testi'), 'Kuusamontien testiosa', 20, 26, 1, 41, 15, 0, 11, ST_GeomFromText(
                 'MULTILINESTRING((537468.712715562 7262592.05471069,537493.8809 7262605.3289,537528.2012 7262620.5359,537558.8462 7262633.2809,537577.3193 7262641.2357,537594.4481 7262648.414,537629.2168 7262661.5062,537658.0478 7262671.3609,537692.3758 7262682.2381,537719.1682 7262691.592,537748.4911 7262700.034,537781.7471 7262708.6857,537808.1101 7262714.8789,537842.4768 7262722.9148,537879.695 7262729.7268,537925.5733 7262736.3381,537963.2888 7262741.2628,538001.7839 7262746.8288,538027.4049 7262749.6982,538087.9702 7262758.796,538129.3579 7262764.0578,538139.274 7262765.318,538191.1441 7262771.379,538237.1909 7262777.2041,538263.2788 7262780.5518,538294.5581 7262784.1281,538317.8153 7262787.2798,538339.1291 7262790.6488,538361.2118 7262792.563,538385.5749 7262795.4151,538403.1832 7262797.2292,538419.4068 7262799.8443,538455.715 7262804.1192,538485.8908 7262807.8373,538534.7731 7262813.5427,538578.1541 7262818.7008,538621.5328 7262823.8602,538647.4652 7262826.0089,538656.5868 7262827.7688,538664.681 7262829.032,538689.5629 7262832.008,538722.2721 7262835.143,538761.5212 7262839.1088,538797.0028 7262843.937,538819.3708 7262846.5908,538844.7583 7262849.3918,538886.5051 7262854.1658,538903.952 7262856.1978,538915.3992 7262857.6539,538925.0062 7262859.1047,538967.7238 7262864.0348,539005.9801 7262868.8189,539044.8701 7262874.326,539085.9487 7262880.982,539119.6091 7262887.4902,539166.9429 7262897.2681,539235.3249 7262912.5162,539290.0472 7262923.8628,539353.2312 7262938.5338,539424.5988 7262954.4733,539474.4292 7262965.7222,539513.262 7262976.5238,539566.4078 7262986.5459,539601.9061 7262993.6378,539611.6131 7262996.2231,539642.672 7263003.8749,539706.183 7263017.9211,539763.7062 7263031.3748,539811.339 7263041.588,539872.5308 7263055.3353,539942.4857 7263071.0491,540057.84 7263096.9863,540174.7428 7263123.6619,540197.2382 7263128.7837,540249.1227 7263140.5972,540317.9901 7263156.4367,540353.0667 7263164.444,540376.4918 7263169.7921,540411.7941 7263177.8518,540598.9812 7263220.3931,540682.326 7263239.6982,540775.2039 7263261.3092,540926.3573 7263296.1482,540940.1909 7263299.3499,541078.4361 7263331.3427,541088.3057 7263333.6273,541222.6059 7263364.7071,541247.0238 7263370.3577,541252.896 7263371.7091,541300.1441 7263382.9431,541330.508 7263389.0351,541371.3389 7263399.0029,541402.3193 7263405.7113,541458.381 7263419.9701,541491.2213 7263428.7999,541515.9721 7263435.8948,541526.2502 7263438.9988,541571.8111 7263453.3637,541625.7841 7263472.5341,541686.7503 7263494.7562,541755.841 7263523.7802,541812.7609 7263548.0402,541857.507 7263567.6383,541891.6748 7263583.6493,541922.9863 7263597.2453,541942.4801 7263606.617,541955.3299 7263612.2498,541979.8538 7263623.0002,542072.2678 7263663.4458,542159.1842 7263701.5163,542179.6821 7263710.6289,542198.0229 7263718.7833,542247.0833 7263740.5819,542301.3589 7263764.6151,542356.3248 7263788.6971,542421.2639 7263817.1493,542496.3549 7263849.4327,542554.211 7263876.0351,542623.2553 7263906.6308,542649.894 7263918.271,542674.2529 7263929.2292,542694.5579 7263938.3651,542749.8692 7263963.266,542792.5303 7263983.858,542806.1739 7263991.2132,542813.1789 7263995.1171,542850.1178 7264016.4851,542882.266 7264037.3457,542936.6989 7264075.6908,542950.9703 7264086.7658,542952.9308 7264088.288,542997.5662 7264125.6742,543003.0483 7264130.5018,543040.3648 7264164.8471,543070.0367 7264192.1052,543079.06 7264200.3519),(543079.06 7264200.3519,543097.8731 7264216.2622,543139.1668 7264250.5122,543166.94 7264271.5491,543198.2831 7264293.6187,543233.1959 7264315.708,543262.0781 7264334.2531,543309.6978 7264358.6573,543343.5023 7264375.3252,543382.4899 7264392.4231,543428.0311 7264412.092,543476.6358 7264428.658,543513.7123 7264442.0027,543541.1032 7264451.0909,543622.734 7264478.869,543669.0762 7264494.8317,543716.447 7264511.2001,543747.6322 7264521.8951,543774.611 7264531.53,543798.5483 7264541.3288,543801.1372 7264542.421,543823.7559 7264551.9547,543858.3441 7264567.1123,543901.7728 7264588.5357,543928.3741 7264602.972,543945.4928 7264612.9423,543973.7503 7264629.6292,543995.9283 7264643.4379,544007.7721 7264651.0771,544034.3942 7264668.2458,544057.7091 7264683.211,544080.3332 7264697.7331,544136.5349 7264734.8829,544214.7412 7264785.3231,544282.9219 7264828.4392,544419.0208 7264916.8999,544503.35 7264971.531,544594.5938 7265030.05,544631.5988 7265054.6662,544695.348 7265094.5752,544719.4878 7265110.0538,544734.5512 7265120.0359,544790.143 7265155.05,544821.5051 7265175.4181,544878.6519 7265211.2558,544937.0989 7265248.6688,544988.9643 7265282.6329,545033.8462 7265314.584,545067.7757 7265339.9447,545123.6867 7265384.7152,545164.4259 7265419.9532,545195.523 7265448.269,545222.0081 7265473.2879,545270.5003 7265521.4989,545311.866 7265568.0889,545348.1402 7265611.221,545382.7863 7265654.9672,545414.4122 7265692.4379,545416.614 7265695.047,545446.5718 7265731.6709,545474.086 7265767.8892,545513.8282 7265818.7559,545553.1601 7265866.1617,545588.8263 7265912.3503,545628.363 7265962.4022,545659.7472 7266000.0421,545688.8878 7266034.4261,545704.763 7266051.6139,545720.6752 7266068.807,545753.681 7266100.5408,545781.3882 7266125.5578,545813.7782 7266152.4062,545847.999 7266178.232,545893.0161 7266211.3778,545924.1793 7266231.5071,545956.1519 7266248.5788,545990.9748 7266265.445,546025.5952 7266282.5149,546069.3753 7266301.8157,546121.3008 7266323.7561,546160.806 7266339.804,546209.6757 7266359.7112,546263.6357 7266381.2412,546309.4532 7266400.336,546363.2107 7266422.6808,546407.1963 7266441.3712,546445.3382 7266458.2612,546486.5068 7266477.9903,546535.3789 7266503.8161,546586.6839 7266533.0961,546639.4039 7266565.0151,546686.2452 7266594.2897,546744.2371 7266630.0709,546787.1138 7266655.4637,546809.125 7266668.4981,546877.8638 7266712.0019,546921.6641 7266737.621,546968.3011 7266766.4889,547020.0081 7266798.8129,547076.9888 7266832.157,547137.8191 7266870.377,547202.707 7266909.6147,547247.9248 7266937.4672,547305.7142 7266973.6539,547350.3239 7267000.6929,547393.7002 7267025.9511,547440.9822 7267051.5453,547482.7588 7267071.478,547521.4952 7267088.3651,547575.6523 7267106.2813,547629.6063 7267123.1832,547689.445 7267139.6849,547736.0993 7267152.1142,547801.6191 7267168.2158,547880.1232 7267186.5637,547945.2428 7267203.0702,548009.3558 7267218.7608,548076.888 7267234.6747,548149.8439 7267252.2318,548231.838 7267273.0638,548298.0159 7267288.3411,548335.9929 7267298.8402,548343.7631 7267300.7222,548387.243 7267311.2308,548432.437 7267326.4021,548481.2341 7267344.8137,548521.7833 7267362.5721,548554.2948 7267377.8649,548587.203 7267394.3672,548615.4843 7267410.237,548632.5292 7267420.6051,548648.5652 7267431.368,548671.4108 7267447.4058,548691.2209 7267461.087,548738.1241 7267492.5653,548767.5822 7267513.4729,548810.4737 7267542.915,548858.9712 7267577.2197,548894.8602 7267602.8341,548934.1432 7267629.8391,549005.2928 7267678.9733,549076.2238 7267730.7208,549170.6097 7267797.9081,549252.7688 7267856.7677,549344.3568 7267920.7182,549422.304 7267976.9359,549507.267 7268038.4351,549585.3138 7268091.4059,549659.5573 7268144.3331,549682.4851 7268160.7187,549705.7041 7268177.9089,549737.28 7268199.3032,549767.663 7268220.081,549811.9238 7268252.2341,549862.7457 7268288.2689,549906.0077 7268320.2099,549943.535 7268346.2769,549981.4572 7268372.7489,550020.6932 7268401.1159,550053.2542 7268423.9228,550129.092 7268477.4677,550194.392 7268524.8837,550245.8161 7268560.725,550281.7497 7268585.9731,550338.117 7268627.0768,550404.0292 7268673.5,550464.373 7268716.853,550520.1488 7268757.1497,550536.3373 7268768.5041,550583.4829 7268801.5403,550645.0261 7268845.1078,550697.6288 7268882.7637,550752.8151 7268922.0527,550764.2551 7268930.3161,550800.3972 7268956.4331,550897.5769 7269024.4697,550948.8729 7269060.1937,551006.978 7269103.542,551119.5862 7269182.6167,551151.0853 7269205.18,551180.0979 7269225.9662,551239.509 7269268.6171,551292.0027 7269306.2201,551342.0833 7269342.2228,551390.149 7269375.421,551463.763 7269428.8271,551527.318 7269474.0288,551573.7787 7269507.3271,551623.8479 7269538.7142,551656.6263 7269560.1079,551689.3981 7269579.2938,551720.7632 7269597.6811,551751.5172 7269612.4563,551781.5709 7269628.2648,551812.126 7269643.442,551840.4663 7269656.6181,551869.6087 7269669.5912,551895.9407 7269681.9693,551930.2931 7269695.7791,552011.4892 7269731.1011,552065.7529 7269754.6483,552112.9843 7269775.4029,552171.4687 7269800.5462,552227.942 7269824.2881,552272.5612 7269844.0458,552359.9088 7269882.3378,552430.0269 7269912.8661,552513.0519 7269949.0677,552570.0522 7269973.8769,552647.9989 7270007.763,552715.2708 7270036.2027,552785.3639 7270065.2493,552812.9519 7270077.7298,552876.068 7270105.0671,552961.9851 7270142.3687,553084.7011 7270195.4628,553180.3811 7270236.7022,553183.8193 7270238.2161,553262.2252 7270272.7389,553365.467 7270317.1622,553423.5608 7270342.1733,553459.6153 7270357.5029,553498.2849 7270374.8492,553534.1369 7270391.5879,553570.896 7270407.0588,553617.2197 7270429.204,553689.6527 7270462.7929,553755.9997 7270494.3402,553814.536 7270522.045,553897.315 7270561.1708,553926.7023 7270575.1569,553967.9149 7270594.3708,553997.6172 7270608.7923,554025.2267 7270622.2579,554062.1061 7270639.5612,554093.6141 7270654.175,554122.8667 7270668.4339,554153.9192 7270683.1871,554178.9928 7270695.3883,554191.5352 7270701.2611,554213.0343 7270711.4171,554238.5701 7270723.1758,554267.6827 7270736.9791,554296.4971 7270750.6239,554324.2578 7270764.0949,554348.746 7270775.5272,554368.8979 7270785.1991,554385.162 7270793.2719,554417.4038 7270808.8082,554446.9661 7270822.7723,554478.1537 7270838.1317,554499.6629 7270847.8358,554534.8801 7270865.2511,554565.5013 7270879.0889,554577.5048 7270884.8051,554592.8208 7270892.0971,554623.7219 7270906.8473,554654.177 7270921.2842,554688.524 7270937.1707,554718.8183 7270952.0579,554753.0039 7270968.3929,554786.6018 7270983.9602,554816.1599 7270998.0749,554845.2631 7271011.88,554869.2391 7271023.1313,554899.3928 7271037.5622,554925.9613 7271050.4013,554950.59 7271062.2898,554979.1202 7271075.1741,555000.3138 7271085.4737,555022.1011 7271096.2402,555040.4657 7271104.8151,555059.7172 7271114.3148,555061.2632 7271115.0932,555081.9488 7271125.394,555101.6147 7271134.3048,555103.0089 7271134.9361,555121.5087 7271144.2679,555149.4279 7271157.4398,555170.1688 7271167.7293,555192.5589 7271178.5089,555215.844 7271189.7632,555237.0411 7271199.9121,555260.3328 7271210.8638,555286.5791 7271224.601,555309.1049 7271235.723,555316.4249 7271239.3261,555348.0759 7271254.2449,555376.8687 7271268.7962,555403.4378 7271281.634,555431.3463 7271295.2592,555456.2728 7271307.305,555474.934 7271316.1877,555510.0029 7271333.4488,555544.0343 7271349.9321,555574.7669 7271365.4321,555606.4148 7271380.5009,555638.9591 7271396.0438,555667.7592 7271410.2931,555692.6851 7271422.2079,555722.2659 7271436.4417,555747.7891 7271448.9681,555776.0198 7271462.4313,555803.338 7271476.4871,555835.3201 7271491.9573,555863.842 7271506.3287,555886.5078 7271517.9171,555915.4811 7271532.4451,555940.84 7271546.1757,555972.0443 7271563.6,556001.2868 7271580.9998,556028.2638 7271598.3741,556049.9862 7271613.1213,556074.2439 7271630.614,556095.7823 7271648.3789,556119.2562 7271668.2789,556144.0618 7271690.61,556169.3058 7271714.3049,556192.5951 7271737.2232,556218.3988 7271764.6993,556240.4381 7271791.226,556260.0933 7271815.0073,556277.7772 7271839.5211,556293.0789 7271861.138,556308.6551 7271885.0217,556328.5212 7271916.6572,556343.7913 7271940.9917,556355.8531 7271959.105,556371.2912 7271983.6682,556383.7622 7272002.0073,556409.5707 7272035.5217,556442.6278 7272075.5391,556468.2588 7272104.7489,556500.5 7272137.2979,556526.866 7272160.7183,556548.0792 7272179.7559,556566.2902 7272194.8187,556578.949861897 7272203.30686028),(556579.0321 7272203.362,556596.7881 7272216.696,556617.5761 7272231.9762,556651.863 7272255.935,556684.013 7272276.2822,556717.665 7272295.2603,556754.7951 7272314.8208,556772.8269 7272322.9353,556784.6362 7272328.234,556816.1412 7272341.7889,556852.3351 7272356.3717,556886.2492 7272368.854,556920.3211 7272382.2398,556949.5791 7272393.2148,556955.6008 7272395.4701,556985.5359 7272406.915,557015.586 7272418.9739,557062.0497 7272438.0758,557141.5562 7272470.37,557175.9288 7272483.6051,557212.4241 7272498.0348,557257.0993 7272516.0398,557288.9051 7272529.5923,557311.7829 7272540.631,557341.0248 7272554.8023,557364.6637 7272566.8927,557387.4021 7272579.5942,557407.8119 7272593.7989,557414.2559 7272598.4621,557433.6812 7272613.1468,557476.1707 7272644.759,557499.7072 7272664.7049,557536.3013 7272695.4458,557562.5767 7272718.8489,557591.4512 7272747.2201,557619.2739 7272776.6562,557621.035 7272778.5703,557640.7593 7272800.12,557653.8342 7272813.9357,557672.238409084 7272834.34909095),(557672.3888 7272834.5159,557688.9608 7272852.0872,557713.147 7272879.4299,557739.295 7272906.7613,557745.4382 7272913.1659,557760.8828 7272929.2859,557776.0821 7272945.497,557783.6897 7272953.6162,557804.673 7272975.9938,557825.9631 7272999.2747,557845.4217 7273019.5481,557867.7731 7273043.4288,557888.7558 7273065.8069,557908.9827 7273088.038,557932.39 7273111.609,557948.479 7273129.3971,557957.6512 7273139.7342,557979.161 7273162.241,558006.6853 7273191.739,558034.8111 7273221.5431,558066.5473 7273255.3,558090.9121 7273281.302,558116.3342 7273307.7627,558142.347 7273336.7992,558166.713 7273362.65,558194.3892 7273391.846,558221.611 7273421.0397,558252.2918 7273454.0349,558278.1599 7273482.0112,558305.6872 7273510.9047,558333.6558 7273541.7672,558357.2721 7273566.5537,558385.5451 7273597.2673,558386.1859 7273597.9539,558411.4131 7273624.9411,558430.2149 7273644.8619,558453.6787 7273669.6479,558477.8959 7273695.3462,558505.4203 7273724.693,558536.397 7273758.7488,558566.3292 7273790.3783,558594.1537 7273820.0288,558628.1428 7273856.6729,558660.3268 7273891.3398,558694.1712 7273926.9202,558725.6137 7273960.9129,558753.5287 7273990.857,558781.52 7274019.9792,558806.4918 7274047.2629,558829.6572 7274072.7171,558850.8692 7274095.8849,558870.4291 7274116.6193,558891.4958 7274138.8771,558919.3322 7274168.3012,558950.923 7274202.9008,558973.344 7274226.3801,558998.7368 7274254.0819,559019.4932 7274277.248,559042.3681 7274300.8821,559067.4887 7274328.6202,559105.3978 7274370.08,559137.901 7274404.2311,559161.824 7274429.6889,559187.5467 7274458.0393,559218.2371 7274491.2702,559248.7731 7274524.9532,559281.879 7274559.5629,559307.9067 7274587.4601,559333.1828 7274614.5937,559361.616 7274645.6878,559385.8391 7274671.6042,559413.5201 7274701.6328,559423.401 7274711.721,559447.2341 7274737.3282,559468.804 7274760.6479,559494.2951 7274788.3872,559520.24 7274816.1271,559551.7659 7274849.965,559584.4972 7274885.4758,559633.0668 7274938.0588,559679.2202 7274988.6581,559733.6781 7275046.276,559775.1599 7275090.9351,559797.6351 7275115.3208,559816.1861 7275135.5913,559832.0917 7275152.3187,559856.677 7275179.1439,559887.1481 7275211.7608,559915.9642 7275241.6412,559951.4083 7275280.6562,559983.5351 7275315.7102,560018.3812 7275352.7492,560060.1632 7275397.8651,560088.5058 7275427.9777,560089.429 7275428.9729,560109.5862 7275450.7912,560134.3621 7275476.536,560171.725 7275515.925,560219.4721 7275562.465,560253.1361 7275594.8193,560286.9168 7275624.5329,560313.0439 7275647.282,560344.2643 7275673.2448,560396.8771 7275712.8548,560429.0903 7275736.3722,560479.4221 7275769.9069,560551.7509 7275816.8822,560618.9883 7275859.794,560678.712 7275897.5089,560749.6961 7275942.0579,560798.7891 7275973.5689,560812.6621 7275982.1622,560821.4353 7275987.5579,560907.6471 7276043.0229),(560907.6471 7276043.0229,560925.9421 7276054.304,561203.0197 7276227.493,561284.2229 7276274.766,561354.983 7276316.5301,561414.9921 7276351.817,561507.5889 7276406.332,561542.4672 7276426.9008,561618.2901 7276471.3932,561669.0412 7276502.3349,561731.4439 7276538.7158,561792.0288 7276574.9162,561865.5851 7276619.2353,561926.0092 7276655.2362,561988.0111 7276691.43,562058.7801 7276734.988,562120.9748 7276770.5762,562184.6091 7276808.1631,562249.8549 7276845.7309,562297.9909 7276870.5848,562360.1499 7276903.3441,562423.8783 7276932.4478,562541.9662 7276983.9041,562645.2521 7277029.6882,562668.3878 7277040.3088,562675.3493 7277043.7791,562718.1402 7277065.14,562752.6962 7277082.98,562848.9808 7277136.937,562900.39 7277169.2329,562907.1913 7277173.5871,563020.588 7277246.1582,563130.9658 7277314.4908,563203.3571 7277360.911,563203.9789 7277361.3142,563300.694 7277423.1707,563350.9907 7277459.2967,563417.6861 7277517.0391,563498.5868 7277600.995,563553.2168 7277670.1721,563566.2952 7277688.1943,563649.3602 7277815.3049,563660.0451 7277831.0259,563675.8143 7277854.0573,563735.6691 7277946.8799,563853.0692 7278125.2919,563951.5471 7278274.6062,564019.0281 7278377.2209,564059.4558 7278438.6992,564171.5268 7278608.9598,564203.7709 7278660.0921,564395.6861 7278953.983,564755.008 7279502.562,564796.9359 7279566.8567,564818.51 7279599.9477,564832.1917 7279620.931,564876.113 7279687.8801,564974.4153 7279838.376,565046.4689 7279949.3148,565054.687 7279961.8972,565066.2182 7279979.3429,565106.8032 7280041.6627,565142.3372 7280095.7079,565184.4842 7280158.9907,565234.9251 7280233.3522,565263.3082 7280276.0227,565293.6048 7280320.0589,565344.2083 7280394.0982,565392.3919 7280465.7171,565428.8002 7280518.5937,565477.4722 7280589.112,565546.8762 7280691.5219,565603.1202 7280774.2729,565648.9561 7280841.9159,565691.9829 7280905.4703,565717.3048 7280942.4432),(565717.3048 7280942.4432,565735.5372 7280968.2177,565744.5617 7280981.4451,565777.3949 7281029.836,565811.7199 7281080.3251,565860.9309 7281149.7029,565876.8388 7281173.0118,565903.4168 7281213.0792,565946.7681 7281276.9553,565988.8622 7281338.6027,566021.6251 7281387.3611,566039.3519 7281413.1702,566062.3631 7281448.3772,566094.6608 7281499.1963,566107.9799 7281520.9592,566123.7931 7281547.6152,566144.883 7281581.9813,566165.1541 7281616.342,566189.485 7281659.2752,566222.205 7281721.5659,566222.3551 7281721.872,566244.1889 7281766.4507,566244.6188 7281767.3381,566292.575 7281871.3929,566314.9418 7281920.5509,566328.564 7281952.2692,566367.5338 7282037.1303,566500.2582 7282333.7899,566623.8973 7282604.685,566666.1021 7282703.0802,566679.6308 7282734.2428,566694.5502 7282767.5458,566709.3421 7282799.0497,566722.7558 7282829.8413,566749.98 7282889.8742,566773.479 7282944.0962,566788.493 7282977.5981,566815.0781 7283036.2219,566843.3278 7283099.8502,566864.9532 7283147.5991,566878.1817 7283176.5981,566897.6422 7283219.2222,566918.2562 7283265.724,566935.4339 7283304.225,566957.8257 7283354.1013,566976.406 7283395.2401,566976.78 7283395.9827,567019.1742 7283488.6713,567049.3768 7283555.6698,567072.594 7283607.1839,567079.0499 7283621.024,567086.8249 7283638.2952,567105.0877 7283678.2667,567138.0578 7283754.3219,567170.3877 7283828.5798,567197.8561 7283887.8247,567215.0302 7283925.9512,567228.3749 7283953.5702,567245.649 7283987.6832,567260.625 7284015.4172,567277.2101 7284040.1323,567289.1153 7284058.6083,567302.781 7284077.7001,567315.5611 7284095.6699,567345.6951 7284135.4788,567377.0209 7284172.214,567391.0368 7284187.4162,567425.6232 7284221.4542,567464.0219 7284255.1009,567468.7768 7284259.218,567532.6761 7284308.4212,567566.2251 7284331.4848,567611.2159 7284362.4092,567701.265 7284425.3562,567715.3928 7284434.7457,567765.948 7284471.0641,567862.6578 7284538.8452,567913.958 7284582.053,567973.5299 7284637.9467,568006.8663 7284673.7719,568037.615 7284711.6929,568085.9767 7284778.0268,568136.3818 7284860.9917,568189.801 7284951.8311,568231.2447 7285022.4089,568271.5903 7285090.511,568305.6979 7285148.042,568333.6272 7285194.941,568349.5512 7285220.9377,568366.4401 7285251.422,568390.1939 7285291.5293,568439.1488 7285374.328,568477.7911 7285439.1629,568505.5578 7285486.2227,568543.742 7285550.4352,568576.6663 7285606.2111,568589.2612 7285627.5487,568613.6088 7285668.7471,568617.4632 7285675.2673,568649.1749 7285728.6978,568703.3177 7285820.8319,568741.8653 7285886.684,568765.232 7285926.908,568804.9969 7285992.5957,568821.233 7286019.8389,568831.847 7286039.4191,568855.2608 7286078.7998,568880.8353 7286121.558,568900.3571 7286156.1188,568938.3853 7286218.852,568976.0782 7286284.3878,569013.188 7286346.984,569077.9931 7286456.1981,569109.2582 7286508.9038,569163.9388 7286603.0467,569214.1991 7286687.5333,569249.5938 7286747.2678,569285.064 7286808.1339,569328.8132 7286882.9682,569361.0817 7286935.0718,569389.709 7286983.6062,569408.7651 7287015.1268,569426.0941 7287042.6999,569443.8292 7287072.2009,569459.901 7287099.25,569487.1948 7287146.476,569504.7543 7287176.4493,569522.6383 7287206.2742,569541.3102 7287237.8478,569555.148 7287261.5849,569569.2657 7287285.3739,569575.6751 7287295.9951,569587.1551 7287315.0399,569602.8803 7287342.8739,569618.5321 7287368.3132,569631.345 7287388.5479,569651.0002 7287422.2642,569664.3473 7287446.4862,569687.3882 7287486.6929,569713.7351 7287529.2623,569728.823 7287557.0731,569759.421 7287609.8402,569778.7761 7287643.0658,569800.1471 7287677.8011,569813.948 7287702.3512,569827.3963 7287727.0729,569832.7402 7287736.5458,569849.7952 7287764.2357,569871.386 7287801.6898,569884.0047 7287822.8732,569905.038 7287857.1291,569905.524 7287857.932,569923.2419 7287887.9112,569939.0022 7287914.7899,569951.2802 7287936.5993,569964.451 7287956.535,570008.8719 7288033.212,570016.7612 7288046.6787,570023.6388 7288058.7477,570045.4868 7288097.1207,570061.446 7288122.8912,570072.2018 7288142.7322,570081.9529 7288159.5079,570100.5998 7288192.1141,570108.3307 7288207.8517,570115.5001 7288219.0792,570128.3171 7288239.0971,570139.0139 7288256.6298,570157.9592 7288282.181,570172.7452 7288304.518,570192.7827 7288330.5958,570212.573726137 7288355.54585841),(570212.7048 7288355.7111,570229.141 7288374.7231,570252.2439 7288401.2302,570276.0817 7288425.0519,570299.9177 7288448.8749,570323.0582 7288468.7379,570341.0661 7288483.0867,570360.7392 7288499.4229,570388.3052 7288521.0518,570416.6121 7288540.3223,570468.9998 7288573.5729,570498.066 7288590.4308,570520.842 7288604.726,570546.9911 7288619.7043,570587.9679 7288644.3193,570614.674 7288660.1689,570640.1502 7288675.009,570665.4608 7288690.001,570709.1141 7288715.373,570763.9293 7288747.7749,570797.0429 7288766.4612,570843.4679 7288794.0272,570876.0771 7288813.3281,570939.94 7288850.1181,570970.5369 7288868.0551,571016.6593 7288895.29,571071.9313 7288926.7778,571092.3143 7288939.8592,571123.534 7288958.1423,571148.8792 7288972.3392,571166.4268 7288982.6823,571200.0931 7289002.6371,571256.9481 7289035.768,571295.4581 7289058.5201,571332.1469 7289079.2699,571369.4491 7289100.3599,571401.9022 7289120.5028,571444.2709 7289145.209,571460.7351 7289154.5247,571474.6229 7289162.3837,571491.4772 7289171.921,571536.299 7289198.5103,571595.4772 7289233.9818,571639.2531 7289259.24,571687.6232 7289288.087,571744.056 7289321.1828,571796.0971 7289352.9332,571863.9651 7289391.4051,571925.4827 7289429.1522,571966.7913 7289452.8447,571993.8779 7289468.1381,572031.8739 7289491.179,572062.0818 7289507.7522,572114.9668 7289538.5903,572168.448 7289570.2561,572209.4623 7289595.2648,572222.1268 7289602.9451,572249.5821 7289617.3011,572271.3748 7289630.2437,572297.7509 7289647.2207,572341.1742 7289672.9548,572358.82 7289683.7529,572381.3202 7289697.3668,572410.1679 7289717.1357,572429.6111 7289730.1528,572443.9051 7289741.0802,572454.802 7289751.1772,572477.6869 7289770.9772,572481.776 7289774.4999,572499.0061 7289789.7933,572515.5948 7289805.0949,572539.3642 7289828.6249,572572.8888 7289861.1471,572601.226 7289889.8989,572616.3568 7289904.2602,572629.2858 7289917.1743,572643.0592 7289930.9519,572663.3678 7289951.3742,572686.6601 7289974.925,572733.0612 7290021.0409,572767.2511 7290054.3177,572791.5862 7290077.9042,572839.453 7290125.6013,572888.1208 7290173.2859,572915.0979 7290200.2152,572949.4568 7290234.8083,572976.3129 7290261.0182,572996.3398 7290280.5722,573023.9278 7290307.5653,573072.6141 7290356.3713,573126.648 7290408.9448,573160.0927 7290442.4872,573209.3431 7290491.3099,573255.344 7290537.1459,573294.845 7290576.2539,573323.595 7290604.4017,573374.8482 7290656.2171,573424.7822 7290704.8302,573467.1162 7290747.1589,573526.2088 7290805.5897,573578.1492 7290857.554,573637.9081 7290917.5762,573691.104 7290969.5851,573728.6051 7291006.951,573759.1061 7291037.5907,573804.5752 7291081.4292,573819.1032 7291094.8799,573840.5987 7291113.0587,573865.5907 7291133.4768,573905.1001 7291162.4073,573947.0947 7291190.0912,573993.9199 7291217.0909,574040.7302 7291241.9697,574091.866 7291266.0613,574162.348 7291297.44,574213.3677 7291320.9563,574276.8918 7291350.1648,574329.4129 7291374.4231,574378.1808 7291396.7,574438.705 7291424.4232,574501.833 7291453.3972,574541.4948 7291471.9512,574578.5112 7291489.2808,574628.4321 7291512.6332,574652.7321 7291524.9779,574671.756 7291534.2758,574686.8153 7291542.2801,574704.0829 7291552.946,574719.68 7291562.267,574737.3901 7291572.8162,574759.0512 7291587.5241,574781.7961 7291602.0521,574797.8941 7291612.883,574815.7239 7291625.0419,574849.495 7291647.2372,574871.7719 7291662.1267,574890.3849 7291674.8818,574911.5689 7291688.6833,574932.8458 7291703.0779,574954.6218 7291717.7209,574981.9038 7291735.8372,575007.184 7291752.7129,575036.47 7291772.5689,575070.2573 7291794.1543,575095.2898 7291811.5297,575125.5751 7291831.6322,575161.8131 7291855.7458,575184.2603 7291871.023,575200.1099 7291881.8068,575234.5492 7291904.3421,575267.3341 7291926.351,575295.5422 7291946.5732,575320.5032 7291963.7771,575343.7181 7291980.5897,575362.8849 7291994.3543,575368.6797 7291998.3588,575370.6832 7291999.8,575380.364 7292007.8621,575382.6289 7292009.7441,575399.8679 7292023.165,575413.1382 7292034.5997,575429.1241 7292047.7538,575442.2878 7292059.3517,575459.0021 7292073.6809,575459.958 7292074.4997,575477.4591 7292090.7728,575478.4441 7292091.6917,575490.3517 7292102.597,575502.1241 7292114.052,575532.3398 7292143.1069,575544.6041 7292155.3992,575555.8792 7292167.7988,575567.152 7292179.4532,575579.6831 7292194.0891,575596.7042 7292213.3929,575609.2597 7292228.3218,575622.2691 7292243.6187,575633.9808 7292259.4897,575643.7771 7292271.1139,575650.3819 7292279.2641,575693.3282 7292335.776,575727.5918 7292380.8532,575750.3517 7292410.5317,575774.3581 7292443.1731,575802.4017 7292479.4849,575822.9729 7292507.896,575841.9909 7292532.548,575861.3263 7292558.4798,575878.8327 7292581.6762,575918.2373 7292636.43,575938.3171 7292663.4249,575961.4641 7292693.9169,576026.9273 7292780.5742,576048.5371 7292809.3981,576098.4038 7292876.5771,576120.0619 7292905.5171,576150.1787 7292944.9668,576170.6433 7292972.0868,576210.0222 7293025.2898,576237.6728 7293062.6968,576258.587 7293090.4802,576286.5437 7293128.222,576306.5521 7293154.8393,576318.6181 7293171.1993,576330.7342 7293187.4419,576342.2737 7293203.3261,576355.9543 7293222.166,576383.0689 7293257.4629,576420.6617 7293307.8537,576438.27 7293330.7589,576459.479 7293359.0372,576477.8163 7293383.8101,576506.4108 7293421.5828,576530.5899 7293454.6541,576542.7822 7293470.8623,576570.2029 7293507.3718,576590.5162 7293534.3262,576609.9243 7293560.1121,576629.1417 7293586.5298,576653.0558 7293618.4661,576672.8581 7293646.0339,576693.331 7293672.9948,576718.6059 7293706.5992,576746.5382 7293744.821,576766.0749 7293771.2542,576790.5857 7293804.0272,576819.1428 7293842.5997,576842.0092 7293873.0417,576868.7718 7293908.9628,576901.4192 7293952.3832,576920.5813 7293979.92,576936.781 7294002.5048,576950.872 7294025.6268,576962.935 7294044.3249,576978.9829 7294072.9773,576987.1153 7294090.0192,577006.8169 7294132.364,577017.6811 7294158.0951,577034.0661 7294195.2252,577052.8941 7294238.474,577068.0511 7294273.6263,577083.379 7294308.498,577104.8733 7294357.1391,577122.0658 7294396.8128,577134.117 7294423.9321,577150.5091 7294462.2301,577161.219 7294485.6677,577176.6178 7294521.353,577195.3719 7294563.7722,577200.7159 7294577.3331,577212.0613 7294603.3138,577233.5788 7294651.4772,577252.9672 7294697.2929,577264.9129 7294724.9578,577284.7659 7294769.954,577299.1939 7294802.799,577313.6231 7294835.1403,577330.0313 7294873.1178,577358.1047 7294939.1807,577375.8381 7294979.3851,577395.1991 7295024.1592,577409.5878 7295056.6541,577431.8373 7295108.7893,577448.4688 7295146.2112,577456.9371 7295166.1397,577471.3651 7295198.6322,577482.7009 7295225.645,577496.5721 7295257.829,577513.2888 7295294.4189,577537.2279 7295349.3931,577547.4089 7295372.689,577561.3027 7295405.4721,577579.5322 7295447.9402,577605.3931 7295506.0452,577623.6339 7295548.6348,577641.752 7295589.2382,577658.5961 7295628.2098,577681.1428 7295679.2563,577697.9322 7295716.842,577721.9957 7295770.5262,577739.2139 7295808.7831,577760.3902 7295857.6588,577778.5899 7295898.0519,577796.0951 7295939.3593,577815.873 7295982.5629,577831.9197 7296019.1498,577851.5488 7296064.7392,577871.8519 7296109.7307,577889.8831 7296150.2728,577928.0631 7296239.3171,577970.3788 7296338.0643,577983.8431 7296370.125,577995.8812 7296399.2787,578013.8819 7296442.8498,578030.5897 7296481.3503,578047.232 7296518.6132,578071.336 7296566.2543,578102.186 7296617.6152,578128.6198 7296657.2532,578143.0091 7296676.9817,578168.5937 7296708.9001,578200.0642 7296746.1332,578222.3542 7296769.5488,578249.8249 7296796.7979,578277.5541 7296822.4677,578302.6932 7296843.6672,578326.381 7296863.3879,578348.0891 7296879.4132,578378.481 7296901.3012,578405.629 7296918.8541,578432.7043 7296934.9711,578487.2652 7296965.6471,578538.4671 7296994.2369,578601.6588 7297029.1563,578672.0491 7297068.5561,578715.376 7297092.6547,578785.843 7297131.7603,578831.3259 7297157.1031,578897.5198 7297193.0861,578947.4729 7297220.9267,578994.5649 7297246.818,579024.0307 7297262.9201,579052.6538 7297279.2527,579104.8111 7297307.5447,579163.7148 7297340.2242,579213.385 7297366.5908,579268.8767 7297397.1013,579331.1191 7297430.6121,579374.1322 7297455.03,579394.1078 7297466.3742,579433.6308 7297487.3963,579502.8752 7297525.1559,579571.8832 7297562.8267,579635.3959 7297597.9861,579699.4042 7297632.7637,579728.736 7297648.5949,579780.3132 7297677.031,579783.8299 7297678.969,579920.1253 7297753.5901,579982.7631 7297788.1592,580052.1892 7297825.384,580122.259 7297863.6058,580189.4642 7297900.3732,580203.290955327 7297907.43320612),(580203.392 7297907.4848,580262.8561 7297940.415,580299.6408 7297960.5597,580349.1549 7297987.9769,580390.1109 7298010.2299,580425.6621 7298028.9501,580499.0647 7298069.5541,580550.861 7298098.0051,580588.0489 7298118.001,580605.2123 7298127.2429,580616.9019 7298133.5439,580646.2618 7298151.014,580672.6111 7298167.7658,580704.5598 7298189.7759,580724.405 7298205.155,580756.1959 7298229.9231,580780.3821 7298251.6908,580806.9297 7298275.874,580842.7222 7298312.1137,580870.9779 7298343.9207,580891.5568 7298369.2963,580912.6217 7298395.9237,580943.1322 7298438.206,580971.183 7298483.2832,580988.6078 7298513.512,581012.5112 7298559.2699,581032.0109 7298600.6112,581051.7472 7298644.9023,581068.6378 7298682.9912,581075.934 7298700.428,581091.1821 7298734.2069,581110.7402 7298779.2103,581130.7051 7298823.8247,581145.8609 7298858.6042,581168.878 7298910.6232,581189.0567 7298956.1179,581208.9192 7299001.6061,581228.7221 7299046.3772,581249.2659 7299091.6903,581264.458 7299126.5042,581283.8702 7299171.0252,581303.27 7299216.0232,581323.1652 7299260.3191,581342.6298 7299305.7959,581362.5251 7299350.0912,581382.3953 7299394.477,581400.4723 7299436.4049,581420.5033 7299481.4988,581429.3211 7299502.0283,581440.489 7299526.8899,581461.1448 7299573.7728,581499.6858 7299661.5337,581534.8112 7299739.9759,581573.5803 7299829.0161,581593.3141 7299873.3858,581615.9977 7299924.9909,581636.3968 7299971.1312,581656.4921 7300016.785,581688.8661 7300090.125,581717.0622 7300154.5168,581741.8797 7300211.0133,581761.9292 7300255.3871,581782.4027 7300301.8961,581801.706 7300346.7059,581822.061 7300392.7462,581846.2168 7300448.1081,581866.4372 7300493.5867,581889.633 7300542.1968,581908.0108 7300581.9063,581920.4329 7300606.5028,581927.5058 7300619.8338,581949.0311 7300654.3321,581963.6193 7300677.918,581978.1098 7300698.195,581998.7763 7300726.914,582021.024 7300754.804,582041.1229 7300777.3388,582065.1739 7300803.9579,582091.575 7300830.627,582106.1739 7300844.535,582158.455 7300892.2012,582199.3771 7300929.9072,582248.6577 7300974.0642,582297.6329 7301018.051,582346.081 7301061.0211,582387.5033 7301099.786,582415.1318 7301124.974,582435.9609 7301143.3118,582457.4909 7301162.7032,582493.0809 7301195.3363,582530.1371 7301228.9901,582557.0528 7301253.0911,582593.1841 7301286.3132,582615.2722 7301306.0958,582638.6681 7301327.8468,582669.2971 7301356.1198,582688.0769 7301374.0229,582707.6159 7301394.9168,582727.8203 7301419.2811,582760.4313 7301459.5153,582794.6473 7301504.6949,582814.9457 7301530.8811,582830.3433 7301550.173,582849.2308 7301575.6629,582872.058 7301603.9728,582891.1123 7301628.8617,582904.1473 7301645.538,582915.6999 7301661.5031,582920.6978 7301668.19,582926.8249 7301675.5101,582962.9688 7301721.785,582996.9037 7301765.5007,583031.4997 7301810.0521,583064.5979 7301854.6082,583096.4072 7301895.909,583129.9491 7301938.3848,583162.7048 7301982.583,583186.4033 7302012.8832,583209.1471 7302042.7231,583247.5898 7302092.5779,583265.307 7302115.9381,583271.9612 7302125.38,583274.7312 7302128.8492,583277.376 7302132.0789,583286.9889 7302144.5338,583297.2748 7302157.3788,583312.1001 7302174.9692,583331.4081 7302196.8399,583356.3667 7302223.1779,583371.2081 7302238.4081,583391.0378 7302256.986,583408.602 7302273.1131,583423.4761 7302285.512,583439.0571 7302298.0182,583457.1019 7302311.9602,583476.5379 7302326.5871,583484.7942 7302332.4462,583499.0929 7302343.4843,583534.503 7302368.6663,583561.7397 7302389.272,583597.07 7302414.451,583626.795 7302435.943,583656.2811 7302457.5052,583683.781 7302477.6368,583715.9542 7302501.0369,583752.3887 7302527.7608,583771.312 7302541.6409,583800.1299 7302562.1507,583826.8508 7302581.8721,583857.9032 7302605.0482,583888.4268 7302627.4162,583907.4382 7302641.4779,583919.9521 7302650.4423,583934.3521 7302660.9342,583957.6527 7302677.4473,583980.3268 7302694.5321,583989.5079 7302701.038,583998.1263 7302706.6868,584025.4868 7302726.9388,584042.1148 7302739.6403,584103.9791 7302785.7168,584165.0268 7302830.9168,584202.4862 7302858.4018,584231.4798 7302879.0319,584280.075 7302915.6492,584298.621128379 7302929.22223896),(584298.6469 7302929.2411,584351.1543 7302968.6623,584393.4538 7302999.7969,584447.7598 7303040.9673,584513.9973 7303090.504,584577.4641 7303136.8891,584651.1431 7303191.1588,584703.2068 7303229.6801,584758.315 7303270.6521,584830.9451 7303324.0999,584884.8319 7303364.1399,584921.174 7303390.8668,584974.3229 7303430.2987,585027.1269 7303468.826,585089.3758 7303515.0949,585151.4902 7303561.3448,585171.1943 7303575.8812,585250.5453 7303635.8403,585288.4901 7303663.8232,585351.8968 7303711.3952,585381.1763 7303733.2051,585394.864 7303743.5148,585438.6602 7303776.3891,585507.1601 7303827.4523,585543.3492 7303854.8861,585559.9897 7303867.7448,585622.284 7303914.0149,585670.8702 7303950.419,585726.945 7303992.2778,585780.3231 7304032.718,585810.8622 7304054.986,585838.3698 7304076.1628,585875.41 7304104.0499,585934.9111 7304148.2439,585949.2849 7304159.2099,585985.0231 7304186.4817,586007.1321 7304203.5558,586068.2221 7304249.2578,586119.7438 7304287.3169,586128.6808 7304294.1182,586156.136 7304314.5691,586191.2049 7304340.841,586252.5849 7304385.6103,586283.5778 7304406.8229,586333.7667 7304438.0611,586361.543 7304452.9971,586386.3408 7304465.9452,586414.8312 7304479.8473,586432.4007 7304487.5937,586467.8692 7304502.4862,586534.1013 7304528.5199,586600.0832 7304553.3541,586680.514 7304584.1278,586701.6843 7304591.7229,586739.1771 7304605.8561,586795.0821 7304626.6417,586832.7322 7304640.975,586866.922 7304653.8933,586892.1909 7304663.1102,586924.2022 7304675.3018,586953.2643 7304686.2631,586972.7712 7304693.5748,587002.4508 7304704.8773,587031.202 7304715.511,587065.5598 7304728.275,587078.618 7304733.2753,587121.3767 7304749.7109,587160.3971 7304764.1782,587200.0208 7304779.303,587241.9219 7304796.579,587284.1679 7304816.8732,587313.1168 7304832.3327,587348.328 7304852.8009,587372.6202 7304868.2187,587401.4089 7304887.4648,587430.412 7304909.0889,587446.8822 7304920.934,587466.6488 7304936.9491,587484.6001 7304952.0072,587509.2402 7304974.078,587530.4837 7304994.3449,587553.9219 7305018.6562,587571.579 7305037.5539,587594.76 7305064.2248,587611.1158 7305084.2832,587630.045 7305106.9781,587652.3731 7305135.9181,587670.4477 7305158.2611,587679.075 7305169.0371,587693.0551 7305187.4119,587707.1359 7305205.3191,587721.5198 7305223.4747,587737.5439 7305243.9131,587753.3262 7305264.4998,587767.3981 7305282.5642,587783.2691 7305302.9169,587803.1959 7305328.106,587819.9752 7305349.3668,587835.6157 7305369.5521,587857.5639 7305397.3552,587878.6532 7305424.116,587898.9611 7305451.3711,587917.6259 7305474.9052,587937.9708 7305500.6852,587959.982 7305529.263,587979.8421 7305554.5409,587998.6868 7305578.8087,588008.0758 7305590.6657,588022.4567 7305609.405,588033.689 7305623.8222,588061.3598 7305658.0389,588080.853 7305683.9159,588098.855 7305706.9848,588118.9658 7305732.38,588133.869 7305752.548,588152.513 7305776.1852,588166.679 7305793.8149,588188.6128 7305821.5357,588215.3742 7305856.3711,588234.9728 7305881.138,588256.4439 7305908.2973,588278.9912 7305936.8972,588299.6028 7305963.6449,588317.9901 7305987.1951,588339.304 7306014.2722,588363.9982 7306045.9898,588374.3121 7306058.9712,588391.1813 7306080.9729,588405.651 7306099.1619,588415.4038 7306112.525,588430.8431 7306131.92,588448.2328 7306154.0313,588462.7001 7306172.3769,588477.8571 7306191.7623,588491.9301 7306210.0989,588506.8233 7306230.2651,588520.6688 7306248.0461,588537.9299 7306268.7358,588550.09 7306284.1179,588565.6341 7306303.8261,588584.1041 7306328.2071,588599.3391 7306347.5931,588625.1172 7306380.359,588648.509 7306410.0291,588668.733 7306436.0181,588695.2818 7306469.7333,588709.141 7306487.2528,588718.6152 7306500.638,588739.4722 7306526.9819,588766.0859 7306561.3403,588788.0227 7306588.8247,588806.4928 7306612.2188,588829.7291 7306641.858,588849.1908 7306666.9268,588874.6342 7306699.5009,588956.187 7306803.1608,588968.6437 7306819.4172,588983.0318 7306837.8402,588994.4112 7306852.7328,589002.2642 7306862.5112,589015.9632 7306880.5048,589035.8162 7306905.7398,589050.2871 7306923.9288,589060.748 7306937.3848,589074.6019 7306954.8508,589085.5239 7306968.9483,589104.8152 7306993.6639,589126.6662 7307021.542,589142.0578 7307041.0102,589154.3591 7307057.1832,589165.1137 7307071.75,589176.8301 7307085.6247,589186.368 7307097.9587,589194.5051 7307109.2392,589205.7469 7307122.3463,589222.8442 7307144.3039,589241.129 7307167.7641,589265.6422 7307198.9678,589282.3393 7307220.1763,589313.8253 7307260.5658,589329.4438 7307280.9542,589352.4347 7307310.3819,589370.576 7307333.3639,589397.824 7307367.9581,589420.5029 7307397.2191,589454.7409 7307440.446,589475.7671 7307467.9328,589499.6889 7307497.8608,589524.7209 7307530.1049,589544.6089 7307555.2661,589571.172 7307589.0533,589596.9079 7307621.8001,589624.3751 7307656.9517,589643.8069 7307681.5483,589662.5498 7307705.4939,589677.2053 7307724.5821,589697.9361 7307750.5568,589731.198 7307792.1018,589757.6551 7307826.8288,589778.4419 7307853.514,589799.4478 7307880.4559),(589799.4478 7307880.4559,589809.2049 7307892.8149,589822.185 7307909.266,589850.0453 7307944.429,589871.4508 7307971.5257,589890.1401 7307994.6042,589909.0413 7308018.554,589944.7611 7308065.9092,589961.1789 7308086.9658,590026.0578 7308171.5029,590114.2207 7308283.512,590181.2949 7308370.585,590242.9489 7308449.01,590302.3928 7308525.2248,590382.9248 7308626.5769,590430.9917 7308686.8742,590489.9937 7308752.6221,590571.0379 7308839.1008,590671.3639 7308945.4907,590711.5349 7308987.9261,590750.9031 7309028.6527,590822.444 7309104.4929,590886.005 7309173.8612,590945.3 7309235.1883,591012.9882 7309306.7851,591094.1318 7309393.6658,591154.388 7309458.425,591231.5861 7309539.2578,591268.9991 7309578.7528,591305.645 7309617.2712,591338.7301 7309653.4287,591370.1237 7309688.3499,591451.6051 7309774.0019,591499.6541 7309825.033,591552.806 7309881.0673,591591.7019 7309921.6998,591618.3281 7309949.5172,591668.3139 7310004.4372,591705.3082 7310042.8692,591774.1768 7310116.5142,591856.0578 7310203.7683,591955.7578 7310310.0332,592032.4342 7310391.683,592086.8879 7310448.5172,592157.76 7310524.1638,592212.5752 7310582.5369,592235.5971 7310606.3509,592283.9332 7310659.1758,592305.1458 7310682.6699,592354.7957 7310737.7037,592393.105 7310781.7703,592454.3648 7310869.2233,592493.6079 7310942.387,592534.9879 7311036.845,592556.2303 7311097.835,592583.9547 7311191.1459,592608.397 7311269.9138,592648.7318 7311373.7733,592673.0717 7311431.9272,592704.7709 7311500.1222,592731.6032 7311554.3663,592756.522 7311602.2361,592798.8388 7311675.0508,592849.8728 7311759.7488,592903.1401 7311837.6532,592960.5978 7311918.1798,593000.6551 7311978.6362,593042.4329 7312053.5211,593067.2802 7312103.4998,593102.4658 7312173.7519,593142.0252 7312252.6348,593177.3108 7312323.3371,593227.8958 7312421.428,593291.3502 7312550.3747,593340.2288 7312647.9879,593387.8997 7312743.0998,593407.6919 7312784.568,593439.2422 7312848.0599,593560.8309 7313090.6838,593595.9742 7313160.3779,593637.5578 7313241.0052,593721.0278 7313409.9818,593765.784 7313499.259,593840.9232 7313650.5999,593868.494 7313705.0489,593897.2118 7313763.273,593958.2399 7313883.795,594010.758370829 7313978.69349416),(594010.8063 7313978.7801,594019.016208715 7313991.33388026))'));

-- Määrämuutokset

INSERT INTO yllapitokohteen_maaramuutos (yllapitokohde, tyon_tyyppi, tyo,
                                         yksikko, tilattu_maara, toteutunut_maara, yksikkohinta, luoja)
VALUES ((SELECT id
         FROM yllapitokohde
         WHERE nimi = 'Leppäjärven ramppi'), 'ajoradan_paallyste' ::maaramuutos_tyon_tyyppi,
        'Testityö', 'kg', 100, 120, 2, (SELECT id
                                        FROM kayttaja
                                        WHERE kayttajanimi = 'jvh'));
INSERT INTO yllapitokohteen_maaramuutos (yllapitokohde, tyon_tyyppi, tyo,
                                         yksikko, tilattu_maara, toteutunut_maara, yksikkohinta, luoja)
VALUES ((SELECT id
         FROM yllapitokohde
         WHERE nimi = 'Leppäjärven ramppi'), 'ajoradan_paallyste' ::maaramuutos_tyon_tyyppi,
        'Testityö 2', 'kg', 90, 130, 3, (SELECT id
                                         FROM kayttaja
                                         WHERE kayttajanimi = 'jvh'));
INSERT INTO yllapitokohteen_maaramuutos (yllapitokohde, tyon_tyyppi, tyo,
                                         yksikko, tilattu_maara, ennustettu_maara, toteutunut_maara, yksikkohinta, luoja)
VALUES ((SELECT id
         FROM yllapitokohde
         WHERE nimi = 'Leppäjärven ramppi'), 'ajoradan_paallyste' ::maaramuutos_tyon_tyyppi,
        'Järjestelmän luoma työ', 'kg', 1, 1, 4, 5, (SELECT id
                                                     FROM kayttaja
                                                     WHERE jarjestelma IS TRUE LIMIT 1));
INSERT INTO yllapitokohteen_maaramuutos (yllapitokohde, tyon_tyyppi, tyo,
                                         yksikko, tilattu_maara, ennustettu_maara, yksikkohinta, luoja)
VALUES ((SELECT id
         FROM yllapitokohde
         WHERE nimi = 'Leppäjärven ramppi'), 'ajoradan_paallyste' ::maaramuutos_tyon_tyyppi,
        'Käyttäjän luoma ennustettu työ', 'kg', 25, 30, 6, (SELECT id
                                                            FROM kayttaja
                                                            WHERE kayttajanimi = 'jvh'));

INSERT INTO yllapitokohteen_maaramuutos (yllapitokohde, tyon_tyyppi, tyo,
                                         yksikko, tilattu_maara, ennustettu_maara, yksikkohinta, luoja,
                                         poistettu)
VALUES ((SELECT id
         FROM yllapitokohde
         WHERE nimi = 'Leppäjärven ramppi'), 'ajoradan_paallyste' ::maaramuutos_tyon_tyyppi,
        'POISTETTU TYÖ EI SAA NÄKYÄ TAI TULLA LASKUIHIN', 'kg', 99999, 1, 9999, (SELECT id
                                                                                 FROM kayttaja
                                                                                 WHERE kayttajanimi = 'jvh'), true);

-- Päällystysilmoitukset

-- Leppäjärven ilmoitustiedoissa on kirjattu tietoja olemattomalle kohdeosalle (osa on ehkä myöhemmin poistettu)
-- Tällainen tapaus ei saa aiheuttaa ongelmia.
INSERT INTO paallystysilmoitus (versio, paallystyskohde, tila, takuupvm, ilmoitustiedot) VALUES (1, (SELECT id
                                                                                          FROM yllapitokohde
                                                                                          WHERE nimi =
                                                                                                'Leppäjärven ramppi'),
                                                                                         'aloitettu' ::paallystystila,
                                                                                         '2005-12-20 00:00:00+02', '{
    "osoitteet": [
      {
        "rc%": 12,
        "leveys": 12,
        "km-arvo": "12",
        "raekoko": 1,
        "esiintyma": "12",
        "muotoarvo": "12",
        "pinta-ala": 12,
        "pitoisuus": 12,
        "kuulamylly": 2,
        "lisaaineet": "12",
        "kohdeosa-id": 666,
        "massamenekki": 1,
        "tyomenetelma": 21,
        "sideainetyyppi": 2,
        "paallystetyyppi": 2,
        "kokonaismassamaara": 12
      },
      {
        "rc%": 12,
        "leveys": 12,
        "km-arvo": "12",
        "raekoko": 1,
        "esiintyma": "12",
        "muotoarvo": "12",
        "pinta-ala": 12,
        "pitoisuus": 12,
        "kuulamylly": 2,
        "lisaaineet": "12",
        "kohdeosa-id": 3925485,
        "massamenekki": 1,
        "tyomenetelma": 21,
        "sideainetyyppi": 2,
        "paallystetyyppi": 2,
        "kokonaismassamaara": 12
      }
    ],
    "alustatoimet": [
      {
        "tr-alkuosa": 22,
        "tr-alkuetaisyys": 3,
        "tr-loppuosa": 5,
        "tr-loppuetaisyys": 4785,
        "kasittelymenetelma": 13,
        "paksuus": 30,
        "verkkotyyppi": 1,
        "verkon-tarkoitus": 1,
        "verkon-sijainti": 1,
        "tekninen-toimenpide": 2
      }
    ]
  }');
INSERT INTO paallystysilmoitus (versio, paallystyskohde, tila, takuupvm, ilmoitustiedot) VALUES (1, (SELECT id
                                                                                          FROM yllapitokohde
                                                                                          WHERE
                                                                                            nimi = 'Nakkilan ramppi'),
                                                                                         'valmis' ::paallystystila,
                                                                                         '2023-12-31 00:00:00+02', '{
    "osoitteet": [
      {
        "rc%": 0,
        "leveys": 5,
        "km-arvo": "AN7",
        "raekoko": 16,
        "esiintyma": "Haarumäki",
        "muotoarvo": "FI10",
        "pinta-ala": 15337,
        "pitoisuus": 5.22,
        "kuulamylly": 3,
        "lisaaineet": "B650/900, 180 g/m2",
        "kohdeosa-id": 668,
        "massamenekki": 39,
        "tyomenetelma": 31,
        "sideainetyyppi": 4,
        "paallystetyyppi": 12,
        "kokonaismassamaara": 646
      }
    ],
    "alustatoimet": [
      {
        "tr-numero": 20,
        "tr-alkuosa": 12,
        "tr-alkuetaisyys": 3,
        "tr-loppuosa": 13,
        "tr-loppuetaisyys": 20,
        "tr-ajorata": 0,
        "tr-kaista": 1,
        "kasittelymenetelma": 42,
        "paksuus": 4,
        "verkkotyyppi": 1,
        "verkon-tarkoitus": 1,
        "verkon-sijainti": 1,
        "tekninen-toimenpide": 2
      }
    ]
  }');
INSERT INTO paallystysilmoitus (versio, paallystyskohde, tila, takuupvm, ilmoitustiedot, paatos_tekninen_osa, perustelu_tekninen_osa, kasittelyaika_tekninen_osa)
VALUES (1, (SELECT id
         FROM yllapitokohde
         WHERE nimi = 'Oulaisten ohitusramppi'), 'valmis' ::paallystystila, '2005-12-20 00:00:00+02', '{
  "osoitteet": [
    {
      "rc%": 12,
      "leveys": 12,
      "km-arvo": "12",
      "raekoko": 1,
      "esiintyma": "12",
      "muotoarvo": "12",
      "pinta-ala": 12,
      "pitoisuus": 12,
      "kuulamylly": 2,
      "lisaaineet": "12",
      "kohdeosa-id": 669,
      "massamenekki": 1,
      "tyomenetelma": 21,
      "sideainetyyppi": 2,
      "paallystetyyppi": 2,
      "kokonaismassamaara": 12
    }
  ],
  "alustatoimet": [
    {
      "tr-alkuosa": 22,
      "tr-alkuetaisyys": 3,
      "tr-loppuosa": 5,
      "tr-loppuetaisyys": 4785,
      "kasittelymenetelma": 13,
      "paksuus": 30,
      "verkkotyyppi": 1,
      "verkon-tarkoitus": 1,
      "verkon-sijainti": 1,
      "tekninen-toimenpide": 2
    }
  ]
}', 'hylatty' ::paallystysilmoituksen_paatostyyppi, 'Ei tässä ole mitään järkeä', '2005-12-20 00:00:00+02');
INSERT INTO paallystysilmoitus (versio, paallystyskohde, tila, takuupvm, ilmoitustiedot, paatos_tekninen_osa, perustelu_tekninen_osa, kasittelyaika_tekninen_osa)
VALUES (1, (SELECT id
         FROM yllapitokohde
         WHERE nimi = 'Oulun ohitusramppi'), 'lukittu' ::paallystystila, '2005-12-20 00:00:00+02', '{
  "osoitteet": [
    {
      "rc%": 12,
      "leveys": 12,
      "km-arvo": "12",
      "raekoko": 1,
      "esiintyma": "12",
      "muotoarvo": "12",
      "pinta-ala": 12,
      "pitoisuus": 12,
      "kuulamylly": 2,
      "lisaaineet": "12",
      "kohdeosa-id": 667,
      "massamenekki": 1,
      "tyomenetelma": 21,
      "sideainetyyppi": 2,
      "paallystetyyppi": 2,
      "kokonaismassamaara": 12
    }
  ],
  "alustatoimet": [
    {
      "tr-alkuosa": 22,
      "tr-alkuetaisyys": 3,
      "tr-loppuosa": 5,
      "tr-loppuetaisyys": 4785,
      "kasittelymenetelma": 13,
      "paksuus": 30,
      "verkkotyyppi": 1,
      "verkon-tarkoitus": 1,
      "verkon-sijainti": 1,
      "tekninen-toimenpide": 2
    }
  ]
}', 'hyvaksytty' ::paallystysilmoituksen_paatostyyppi, 'Tekninen osa ok!', '2005-12-20 00:00:00+02');

----------------------------
-- Tienpäällystysurakka
----------------------------

INSERT INTO yllapitokohde (urakka, sopimus, kohdenumero, nimi, yllapitokohdetyyppi, yllapitokohdetyotyyppi,
                           tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys)
VALUES
  ((SELECT id
    FROM urakka
    WHERE nimi = 'Tienpäällystysurakka KAS ELY 1 2015'), (SELECT id
                                                          FROM sopimus
                                                          WHERE urakka = (SELECT id
                                                                          FROM urakka
                                                                          WHERE nimi =
                                                                                'Tienpäällystysurakka KAS ELY 1 2015')
                                                                AND paasopimus IS NULL), '1501',
                                                         'Vt13 Hartikkala - Pelkola',
                                                         'paallyste' :: yllapitokohdetyyppi,
                                                         'paallystys' ::yllapitokohdetyotyyppi,
                                                         13, 239, 0, 239, 4894);
INSERT INTO yllapitokohteen_aikataulu (yllapitokohde) VALUES ((SELECT id
                                                               FROM yllapitokohde
                                                               WHERE nimi = 'Vt13 Hartikkala - Pelkola'));
INSERT INTO yllapitokohdeosa (yllapitokohde, nimi, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, tr_ajorata, tr_kaista, sijainti)
VALUES ((SELECT id
         FROM yllapitokohde
         WHERE nimi = 'Vt13 Hartikkala - Pelkola'), 'Vt13 Hartikkala - Pelkola', 13, 239, 0, 239, 222, 1, 11,
        'MULTILINESTRING((569679.576280243 6770940.38019019,569685.927911525 6770936.54598464,569694.917866912 6770931.11985125,569702.825096966 6770926.21423598,569717.059540404 6770917.1194623,569768.068529403 6770886.26298144,569772.905655448 6770883.31437137,569800.582449535 6770866.39038579,569825.147450518 6770851.51214363,569826.659573521 6770850.53304547,569830.600979599 6770848.01502418,569849.985812847 6770836.16460137,569852.934422925 6770834.4297395,569853.208975388 6770834.26715203,569869.210775049 6770824.97723093),(570700.430827977 6770091.60690774,570705.758098659 6770086.85732879,570844.195073798 6769973.54994409,570872.894391503 6769951.86928975))');
INSERT INTO yllapitokohdeosa (yllapitokohde, nimi, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, tr_ajorata, tr_kaista, sijainti)
VALUES ((SELECT id
         FROM yllapitokohde
         WHERE nimi = 'Vt13 Hartikkala - Pelkola'), 'Vt13 Hartikkala - Pelkola', 13, 239, 222, 239, 820, 1, 11,
        'MULTILINESTRING((569869.210775049 6770824.97723093,569872.124508548 6770823.28564906,569918.85619648 6770796.53077951,569962.043715846 6770770.06058692,569985.755877074 6770755.92441063,570007.509246432 6770743.31881966,570092.85754233 6770692.64036563,570152.581934252 6770655.3316041,570259.021934027 6770569.17811313,570337.970358406 6770487.07441987,570348.766508366 6770474.12501744),(570872.894391503 6769951.86928975,570913.557984301 6769921.15032602,571038.051744102 6769821.61642248,571042.671491299 6769817.89894639,571157.696466689 6769725.89230659,571340.874536167 6769579.60903354))');
INSERT INTO yllapitokohdeosa (yllapitokohde, nimi, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, tr_ajorata, tr_kaista, sijainti)
VALUES ((SELECT id
         FROM yllapitokohde
         WHERE nimi = 'Vt13 Hartikkala - Pelkola'), 'Vt13 Hartikkala - Pelkola', 13, 239, 820, 239, 870, 1, 11,
        'MULTILINESTRING((570348.766508366 6770474.12501744,570380.784445928 6770435.72121979),(571340.874536167 6769579.60903354,571371.85691699 6769554.86696651,571373.690046018 6769553.31672776,571379.770800303 6769548.19579434))');
INSERT INTO yllapitokohdeosa (yllapitokohde, nimi, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, tr_ajorata, tr_kaista, sijainti)
VALUES ((SELECT id
         FROM yllapitokohde
         WHERE nimi = 'Vt13 Hartikkala - Pelkola'), 'Vt13 Hartikkala - Pelkola', 13, 239, 870, 239, 1275, 1, 11,
        'MULTILINESTRING((570380.784445928 6770435.72121979,570380.996481422 6770435.46689459,570381.23768261 6770435.1726887,570498.097573703 6770293.76209003,570611.87068515 6770173.67844084,570652.178907897 6770135.64614894),(571379.770800303 6769548.19579434,571411.39783173 6769521.56095359,571569.835447595 6769389.46608079,571570.855639286 6769388.70257481,571697.443978293 6769304.28692351,571698.415334188 6769303.70923177,571700.883529032 6769302.3581588))');
INSERT INTO yllapitokohdeosa (yllapitokohde, nimi, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, tr_ajorata, tr_kaista, sijainti)
VALUES ((SELECT id
         FROM yllapitokohde
         WHERE nimi = 'Vt13 Hartikkala - Pelkola'), 'Vt13 Hartikkala - Pelkola', 13, 239, 870, 239, 1275, 1, 11,
        'MULTILINESTRING((570380.784445928 6770435.72121979,570380.996481422 6770435.46689459,570381.23768261 6770435.1726887,570498.097573703 6770293.76209003,570611.87068515 6770173.67844084,570652.178907897 6770135.64614894),(571379.770800303 6769548.19579434,571411.39783173 6769521.56095359,571569.835447595 6769389.46608079,571570.855639286 6769388.70257481,571697.443978293 6769304.28692351,571698.415334188 6769303.70923177,571700.883529032 6769302.3581588))');

INSERT INTO yllapitokohde (urakka, sopimus, kohdenumero, nimi, yllapitokohdetyyppi, yllapitokohdetyotyyppi,
                           tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys)
VALUES
  ((SELECT id
    FROM urakka
    WHERE nimi = 'Tienpäällystysurakka KAS ELY 1 2015'), (SELECT id
                                                          FROM sopimus
                                                          WHERE urakka = (SELECT id
                                                                          FROM urakka
                                                                          WHERE nimi =
                                                                                'Tienpäällystysurakka KAS ELY 1 2015')
                                                                AND paasopimus IS NULL), '1502',
                                                         'Vt 13 Kähärilä - Liikka', 'paallyste' :: yllapitokohdetyyppi,
                                                         'paallystys' ::yllapitokohdetyotyyppi,
                                                         13, 241, 0, 241, 4723);
INSERT INTO yllapitokohteen_aikataulu (yllapitokohde) VALUES ((SELECT id
                                                               FROM yllapitokohde
                                                               WHERE nimi = 'Vt 13 Kähärilä - Liikka'));
INSERT INTO yllapitokohdeosa (yllapitokohde, nimi, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, tr_ajorata, tr_kaista, sijainti)
VALUES ((SELECT id
         FROM yllapitokohde
         WHERE nimi = 'Vt 13 Kähärilä - Liikka'), 'Vt 13 Kähärilä - Liikka', 13, 241, 0, 241, 30, 1, 11,
        'MULTILINESTRING((578249.322868685 6763497.87157121,578262.945673555 6763491.3180456,578275.84176723 6763483.88913412),(581383.200687944 6760054.36044461,581382.427437082 6760078.6053179,581386.126450856 6760082.99815731,581391.183933542 6760078.6196113,581393.197140761 6760055.39623812),(581397.589195136 6759849.00348945,581396.515373133 6759862.00903338,581395.753058267 6759871.11750491,581399.465769886 6759877.98846319,581404.941334631 6759871.56834268,581407.346106159 6759850.14085703))');

INSERT INTO yllapitokohde (urakka, sopimus, kohdenumero, nimi, yllapitokohdetyyppi, yllapitokohdetyotyyppi,
                           tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys) VALUES
  ((SELECT id
    FROM urakka
    WHERE nimi = 'Tienpäällystysurakka KAS ELY 1 2015'), (SELECT id
                                                          FROM sopimus
                                                          WHERE urakka = (SELECT id
                                                                          FROM urakka
                                                                          WHERE nimi =
                                                                                'Tienpäällystysurakka KAS ELY 1 2015')
                                                                AND paasopimus IS NULL), '1503',
                                                         'Mt 387 Mattila - Hanhi-Kemppi',
                                                         'paallyste' :: yllapitokohdetyyppi,
                                                         'paallystys' ::yllapitokohdetyotyyppi,
                                                         387, 1, 2413, 2, 1988);
INSERT INTO yllapitokohteen_aikataulu (yllapitokohde) VALUES ((SELECT id
                                                               FROM yllapitokohde
                                                               WHERE nimi = 'Mt 387 Mattila - Hanhi-Kemppi'));

INSERT INTO yllapitokohde (urakka, sopimus, kohdenumero, nimi, yllapitokohdetyyppi, yllapitokohdetyotyyppi,
                           tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys)
VALUES
  ((SELECT id
    FROM urakka
    WHERE nimi = 'Tienpäällystysurakka KAS ELY 1 2015'), (SELECT id
                                                          FROM sopimus
                                                          WHERE urakka = (SELECT id
                                                                          FROM urakka
                                                                          WHERE nimi =
                                                                                'Tienpäällystysurakka KAS ELY 1 2015')
                                                                AND paasopimus IS NULL), '1504',
                                                         'Mt 408 Pallo - Kivisalmi', 'paallyste' :: yllapitokohdetyyppi,
                                                         'paallystys' ::yllapitokohdetyotyyppi,
                                                         408, 1, 1989, 2, 127);
INSERT INTO yllapitokohteen_aikataulu (yllapitokohde) VALUES ((SELECT id
                                                               FROM yllapitokohde
                                                               WHERE nimi = 'Mt 408 Pallo - Kivisalmi'));

INSERT INTO yllapitokohde (urakka, sopimus, kohdenumero, nimi, yllapitokohdetyyppi, yllapitokohdetyotyyppi,
                           tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys)
VALUES
  ((SELECT id
    FROM urakka
    WHERE nimi = 'Tienpäällystysurakka KAS ELY 1 2015'), (SELECT id
                                                          FROM sopimus
                                                          WHERE urakka = (SELECT id
                                                                          FROM urakka
                                                                          WHERE nimi =
                                                                                'Tienpäällystysurakka KAS ELY 1 2015')
                                                                AND paasopimus IS NULL), '1505',
                                                         'Kt 62 Sotkulampi - Rajapatsas',
                                                         'paallyste' :: yllapitokohdetyyppi,
                                                         'paallystys' ::yllapitokohdetyotyyppi,
                                                         62, 24, 0, 24, 4240);
INSERT INTO yllapitokohteen_aikataulu (yllapitokohde) VALUES ((SELECT id
                                                               FROM yllapitokohde
                                                               WHERE nimi = 'Kt 62 Sotkulampi - Rajapatsas'));

INSERT INTO yllapitokohde (urakka, sopimus, kohdenumero, nimi, yllapitokohdetyyppi, yllapitokohdetyotyyppi,
                           tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys)
VALUES
  ((SELECT id
    FROM urakka
    WHERE nimi = 'Tienpäällystysurakka KAS ELY 1 2015'), (SELECT id
                                                          FROM sopimus
                                                          WHERE urakka = (SELECT id
                                                                          FROM urakka
                                                                          WHERE nimi =
                                                                                'Tienpäällystysurakka KAS ELY 1 2015')
                                                                AND paasopimus IS NULL), '1506',
                                                         'Kt 62 Haloniemi - Syyspohja',
                                                         'paallyste' :: yllapitokohdetyyppi,
                                                         'paallystys' ::yllapitokohdetyotyyppi,
                                                         62, 19, 7800, 22, 2800);
INSERT INTO yllapitokohteen_aikataulu (yllapitokohde) VALUES ((SELECT id
                                                               FROM yllapitokohde
                                                               WHERE nimi = 'Kt 62 Haloniemi - Syyspohja'));

INSERT INTO yllapitokohde (urakka, sopimus, kohdenumero, nimi, yllapitokohdetyyppi, yllapitokohdetyotyyppi,
                           tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys)
VALUES
  ((SELECT id
    FROM urakka
    WHERE nimi = 'Tienpäällystysurakka KAS ELY 1 2015'), (SELECT id
                                                          FROM sopimus
                                                          WHERE urakka = (SELECT id
                                                                          FROM urakka
                                                                          WHERE nimi =
                                                                                'Tienpäällystysurakka KAS ELY 1 2015')
                                                                AND paasopimus IS NULL), '1507',
                                                         'Mt 387 Raippo - Koskenkylä',
                                                         'paallyste' :: yllapitokohdetyyppi,
                                                         'paallystys' ::yllapitokohdetyotyyppi,
                                                         387, 3, 5955, 7, 55);
INSERT INTO yllapitokohteen_aikataulu (yllapitokohde) VALUES ((SELECT id
                                                               FROM yllapitokohde
                                                               WHERE nimi = 'Mt 387 Raippo - Koskenkylä'));



-- 2018 kohteiden alikohteet
INSERT INTO yllapitokohdeosa (id, yllapitokohde, nimi, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, tr_ajorata, tr_kaista, sijainti)
VALUES (10001, (SELECT id
                FROM yllapitokohde
                WHERE nimi = 'Leppäjärven ramppi 2018'), 'Leppäjärven kohdeosa 2018', 20, 1, 0, 3, 0, 1, 11, ST_GeomFromText(
                   'MULTILINESTRING((426938.1807 7212765.5588,426961.6821 7212765.3789,426978.403 7212763.9413,426991.616 7212762.2112,427003.7041 7212760.2768,427016.424 7212757.0822,427042.9341 7212749.4632,427062.1153 7212743.1593,427075.529 7212738.3829,427090.1869 7212730.16,427118.7481 7212720.132,427140.2329 7212713.314,427166.0599 7212706.0953,427188.8477 7212699.2648,427222.3342 7212689.9562,427258.6192 7212680.6441,427287.9058 7212673.4497,427326.0543 7212665.2298,427357.1639 7212658.2249,427400.9309 7212649.3272,427448.4773 7212639.1771,427490.2277 7212629.6559,427526.8182 7212621.5123,427559.5662 7212613.9993,427580.886 7212609.108,427590.5823 7212606.8699,427610.5222 7212602.3621,427615.8971 7212600.8797,427647.152 7212593.4942,427689.2229 7212583.0023,427709.4832 7212577.99,427746.5912 7212568.6291,427778.3041 7212561.2269,427806.2549 7212554.6179,427839.5073 7212547.4069,427860.0803 7212543.0111,427895.971 7212534.8442,427916.8012 7212530.6253,427946.201 7212523.0611,427980.9602 7212515.0097,428008.1027 7212508.8421,428033.143 7212503.2391,428036.1649 7212502.4708,428039.9377 7212501.8431,428052.579 7212499.287,428077.767 7212493.7668,428101.1832 7212489.3793,428120.8348 7212485.7869,428138.9892 7212482.5089,428171.6193 7212477.6218,428205.1349 7212472.2087,428240.3229 7212467.393,428272.6879 7212462.0223,428307.6389 7212456.075,428329.7389 7212451.821,428358.6538 7212446.268,428378.7909 7212442.4112,428418.4461 7212433.4867,428437.7511 7212429.4131,428464.0909 7212423.8512,428494.8467 7212417.3048,428518.0711 7212412.1842,428542.7189 7212407.6019,428564.4681 7212402.5379,428572.3241 7212400.806,428583.626 7212398.5822,428594.2288 7212396.7431,428638.7807 7212386.621,428659.3263 7212382.0042,428681.515 7212375.4978,428695.2712 7212370.0258,428709.2329 7212363.9952,428735.6692 7212351.8589,428756.5953 7212342.5259,428779.0443 7212332.5241,428797.3339 7212325.9032,428811.267 7212320.7802,428821.0079 7212318.4629,428841.3992 7212313.9373,428866.0309 7212310.0608,428876.2501 7212308.8679,428887.3287 7212308.2813,428901.9032 7212308.3063,428914.666 7212308.4087,428919.9582 7212308.4671,428934.4308 7212308.1693,428940.6181 7212308.3712,428950.5121 7212308.5969,428958.3771 7212308.2157,428966.164 7212308.1812,428970.8492 7212308.0621,428979.0668 7212307.8131,428990.3008 7212307.0532,429014.169 7212302.8122,429028.6351 7212299.7338,429042.9041 7212296.0717,429055.5698 7212293.2059,429061.504 7212291.6533,429068.3219 7212290.0959,429081.532 7212287.4891,429093.364 7212285.0771,429112.4159 7212280.9541,429126.4961 7212277.4772,429140.9818 7212274.5238,429162.8007 7212269.8332,429177.8368 7212266.6678,429181.459 7212266.0389,429184.8322 7212265.2218,429194.6411 7212263.3083,429203.796 7212261.3959,429205.2807 7212261.1898,429209.0762 7212260.505,429211.9081 7212259.9469,429226.8572 7212257.4551,429241.9219 7212254.095,429257.9602 7212251.219,429287.1551 7212248.0441,429367.0582 7212248.589,429438.7837 7212250.982,429485.2861 7212253.8472,429491.1869 7212253.9342,429497.1627 7212254.17,429561.947 7212258.2359,429620.4332 7212263.0933,429648.4882 7212265.3028,429676.8529 7212268.813,429697.1328 7212271.4132,429719.8528 7212274.3779,429756.8882 7212280.3639,429787.6809 7212285.2218,429809.8119 7212288.7309,429817.2111 7212290.1441,429822.4002 7212291.446,429872.386 7212302.0732,429891.6678 7212306.4672,429912.3182 7212310.8451,429976.099 7212327.326,430025.2319 7212339.7649,430078.0937 7212353.7593,430108.6828 7212361.4188,430124.4139 7212365.839,430215.3211 7212391.4653,430294.3898 7212416.1232,430353.3442 7212435.1501,430407.7669 7212454.9959,430448.0678 7212471.5649,430466.0512 7212478.8218,430476.9363 7212483.3433,430494.7208 7212490.9081,430509.801 7212497.9267,430518.1829 7212501.961,430574.9789 7212533.2201,430624.2959 7212561.9177,430639.4118 7212571.5288,430645.8289 7212575.3839,430650.8691 7212578.8262))'));

INSERT INTO yllapitokohdeosa (id, yllapitokohde, nimi, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, tr_ajorata, tr_kaista, sijainti)
VALUES (10003, (SELECT id
                FROM yllapitokohde
                WHERE nimi = 'Oulun ohitusramppi 2018'), 'Oulun kohdeosa 2018', 20, 4, 334, 10, 10, 1, 11, ST_GeomFromText(
                   'MULTILINESTRING((436072.124951972 7216477.52566194,436104.5012 7216502.842,436160.7231 7216547.1962,436209.9699 7216586.1392,436292.3231 7216651.5738,436337.442 7216687.5628,436395.232 7216733.5357,436432.8231 7216763.1058,436446.197 7216773.7061,436458.2231 7216783.135,436478.5852 7216794.933,436498.412 7216809.4438,436514.9929 7216822.1102,436536.8648 7216839.5243,436564.4618 7216861.4349,436582.7442 7216875.9713,436594.5708 7216885.2209,436598.8398 7216888.665,436602.4989 7216891.7857,436614.2648 7216901.0318,436631.7188 7216915.2889,436639.4539 7216921.416,436661.4092 7216938.8051,436694.6318 7216965.8113,436717.157 7216984.9007,436737.3518 7217003.2177,436744.9982 7217013.2058,436755.8272 7217022.7127,436772.7911 7217037.0943,436782.5261 7217046.0449,436806.1531 7217068.4927,436823.251 7217085.7847,436844.0407 7217107.9598,436871.2399 7217138.9229,436899.0221 7217173.794,436925.4322 7217210.0123,436946.2279 7217240.8378,436963.3502 7217268.0691,436980.4993 7217297.7928,436997.5293 7217330.3538,437019.2302 7217373.29,437034.8249 7217405.3501,437046.539 7217429.905,437060.9717 7217461.0402,437069.892 7217480.8782,437116.3098 7217579.0352,437147.2479 7217644.5848,437174.693 7217702.5398,437210.4557 7217779.7408,437234.7122 7217831.3477,437253.9529 7217873.1738,437275.0631 7217918.1087,437305.4241 7217982.8989,437335.7553 7218046.9089,437363.1402 7218106.9329,437392.1868 7218166.9878,437402.761 7218188.5637,437403.8657 7218190.7911,437405.145 7218193.2728,437443.0177 7218273.7048,437464.9641 7218320.7283,437491.9887 7218377.0872,437505.5943 7218406.0551,437516.655 7218430.4909,437525.5949 7218440.5779,437531.6499 7218451.9888,437540.5547 7218469.7722,437562.9251 7218516.988,437565.726 7218522.7131,437568.8021 7218528.3709,437577.365 7218546.7201,437595.7338 7218586.2437,437607.9148 7218613.5739,437614.594 7218630.4562,437617.6218 7218645.3487,437628.4788 7218667.837,437641.0898 7218694.7033,437656.535 7218727.9998,437684.9771 7218788.0779,437710.757 7218843.145,437720.735 7218864.4458,437744.7449 7218915.9658,437760.9251 7218950.1627,437770.9263 7218963.4091,437776.7872 7218973.9237,437783.9869 7218987.9843,437799.4982 7219019.6108,437805.561 7219031.9079,437810.4148 7219042.3218,437818.4697 7219059.2958,437826.3418 7219076.4908,437829.9187 7219084.2873,437843.6011 7219114.4922,437863.7149 7219159.7273,437884.1378 7219205.3673,437898.4622 7219236.113,437920.3049 7219283.0061,437935.089 7219314.7892,437950.0929 7219347.034,437972.926 7219395.522,438019.4987 7219491.1317,438054.9112 7219563.8107,438063.9262 7219582.388,438066.4061 7219587.2382,438068.9843 7219592.6161,438070.0878 7219595.0472,438071.2069 7219597.6938,438074.8481 7219605.0722,438097.1071 7219652.5781,438104.557 7219668.5111,438111.2088 7219683.4042,438117.6271 7219698.4433,438119.49 7219703.0678,438120.7383 7219713.6139,438134.6618 7219743.4317,438149.9969 7219776.1589,438165.7131 7219809.5221,438186.2051 7219852.675,438208.0811 7219896.2401,438223.7782 7219925.2528,438230.433 7219937.6148,438250.464 7219971.477,438274.714 7220009.4731,438308.0373 7220058.0778,438342.5671 7220106.533,438380.2779 7220158.1239,438421.4328 7220214.8038,438457.8542 7220264.845,438499.7303 7220322.504,438519.1592 7220348.7628,438524.4751 7220357.5782,438525.3089 7220358.7961,438542.5801 7220381.1111,438582.177 7220435.373,438617.3138 7220482.0922,438651.8419 7220524.7259,438676.5933 7220554.256,438706.9709 7220585.2811,438733.4399 7220610.93,438772.4002 7220646.1531,438807.7978 7220675.0067,438838.419 7220697.8922,438838.8728 7220698.2311,438862.5469 7220714.2028,438866.854 7220717.0049,438867.2488 7220717.267,438923.0157 7220752.312,438972.4679 7220780.3741,439016.1462 7220805.6097,439082.6599 7220843.3121),(439082.6599 7220843.3121,439094.633 7220850.1652,439143.6969 7220877.5121,439216.4581 7220918.7063,439285.4363 7220958.8332,439337.4 7220988.1192,439349.1408 7220994.7329,439400.7989 7221023.8992,439518.5641 7221090.6821,439580.0448 7221125.6492,439625.9439 7221151.7608,439633.6248 7221156.1298,439694.5349 7221190.4977,439697.7992 7221192.3392,439709.4698 7221198.343,439726.6463 7221208.2871,439742.6751 7221217.6492,439747.1567 7221220.1762,439808.8173 7221254.503,439815.5888 7221258.4343,439851.654 7221279.367,439869.6399 7221289.8071,439936.8081 7221328.279,439936.8969 7221328.3302,440010.116 7221370.8048,440063.6162 7221404.6052,440102.028 7221432.1861,440129.653 7221453.3558,440184.6153 7221499.5962,440231.4101 7221540.171,440234.348 7221542.7599,440255.9012 7221561.7552,440275.9132 7221579.8,440337.105 7221633.0662,440425.5211 7221710.7222,440489.9938 7221767.3051,440542.8288 7221813.8438,440587.6279 7221853.5241,440646.045 7221904.9982,440700.039 7221952.8013,440727.664 7221976.8261,440763.2527 7222007.7773,440783.921 7222026.161,440797.4628 7222037.9191,440824.8269 7222061.6789,440836.9198 7222072.636,440856.1849 7222090.09,440877.591 7222109.5171,440878.5832 7222110.4182,440910.819 7222139.0759,440950.7018 7222173.7612,440995.1108 7222213.0002,441041.6162 7222254.1009,441095.5208 7222301.6687,441150.5468 7222350.3139,441205.3293 7222398.5178,441260.8401 7222448.2392,441322.3893 7222502.9568,441377.3688 7222550.6747,441418.3718 7222586.3892,441443.7438 7222609.1848,441474.4347 7222635.9527,441513.2902 7222669.5768,441551.4542 7222702.7232,441584.2932 7222729.4578,441613.4041 7222752.0021,441642.1809 7222771.8169,441681.6027 7222798.0101,441717.1469 7222820.7873,441717.9038 7222821.2733,441751.6499 7222843.5501,441791.1539 7222868.716,441840.6758 7222900.5617,441879.7963 7222926.1731,441930.6057 7222959.2689,441981.0591 7222991.3927,442041.0991 7223030.839,442091.2142 7223063.2117,442130.5627 7223087.844,442178.0901 7223117.0842,442213.4198 7223137.8251,442235.764 7223150.6558,442255.624 7223161.676,442281.775 7223175.6669,442296.795 7223183.2013,442309.6841 7223185.8128,442318.68 7223189.8072,442341.6912 7223199.7912,442375.0121 7223213.5468,442417.348 7223230.2611,442450.7332 7223241.9037,442485.7681 7223253.4188,442491.7809 7223255.43,442498.095 7223257.3072,442509.6589 7223260.8591,442521.570937664 7223264.30015035),(442521.7982 7223264.3658,442534.9029 7223267.879,442585.8862 7223280.0272,442630.8759 7223289.792,442685.3082 7223301.2928,442729.7898 7223310.4269,442748.1968 7223314.5422,442759.5642 7223318.854,442780.9031 7223322.7311,442812.2223 7223327.6641,442839.5221 7223332.0189,442862.9341 7223335.4451,442883.5017 7223338.4979,442906.4891 7223341.9111,442924.114 7223344.9621,442941.5413 7223347.9203,442963.5043 7223351.8492,442994.646 7223358.0781,443023.6098 7223364.93,443050.7042 7223372.4049,443077.7979 7223380.1912,443101.1521 7223387.7149,443116.1149 7223393.6627,443123.6071 7223396.6763,443150.8283 7223406.8948,443172.3172 7223415.7722,443198.2032 7223427.4708,443222.6752 7223439.114,443233.3828 7223444.6253,443295.004 7223479.335,443320.1479 7223494.5831,443355.2239 7223517.0351,443404.613 7223548.87,443444.5261 7223574.114,443469.62 7223589.6777,443488.4641 7223600.8468,443510.9369 7223614.4112,443527.0551 7223623.2809,443545.2869 7223632.6413,443558.3773 7223638.5081,443568.3422 7223643.4459,443581.473 7223650.2037,443612.3742 7223662.3138,443636.0351 7223672.552,443658.8837 7223681.386,443686.0543 7223690.6987,443712.5209 7223699.915,443745.329 7223711.4122,443777.6327 7223722.5099,443800.439 7223729.606,443832.8398 7223740.1998,443864.3347 7223750.4958,443915.1412 7223768.0409,443950.8211 7223779.1749,444000.4431 7223795.8338,444062.6271 7223816.0638,444130.3028 7223838.835,444219.4788 7223867.439,444312.0542 7223898.2729,444405.5848 7223929.526,444487.8541 7223956.509,444529.9041 7223971.1252,444615.9403 7223998.1171,444682.0818 7224019.9788,444728.4008 7224035.293,444774.5178 7224050.3029,444819.118 7224065.1132,444828.797 7224068.278,444865.9432 7224080.4262,444910.9478 7224095.336,444967.4818 7224114.1747,445035.9501 7224137.2479,445105.6311 7224159.7117,445166.0088 7224179.9631,445209.8687 7224195.054,445245.1651 7224206.7412,445300.9909 7224224.7717,445344.883 7224239.2801,445381.7957 7224251.2681,445402.5313 7224258.2951,445431.454 7224268.0939,445489.5043 7224286.8307,445539.6658 7224303.2521,445602.8742 7224323.398,445665.577 7224344.0508,445699.8609 7224354.9311,445723.3003 7224362.924,445751.6173 7224371.6859,445796.4188 7224386.0913,445825.2712 7224395.7351,445869.6701 7224410.2412,445918.2129 7224426.1593,445953.913 7224437.3391,445986.2763 7224448.3218,446015.0858 7224457.089,446049.2959 7224468.5231,446104.7501 7224486.4077,446175.2011 7224509.2438,446251.8239 7224534.2162,446272.937 7224541.0341,446297.3007 7224548.9021,446317.2519 7224555.3901,446439.3009 7224595.087,446502.068 7224615.7982,446569.8658 7224638.0429,446635.438 7224658.461,446671.7992 7224670.6271,446716.2148 7224684.282,446774.1978 7224701.9773,446830.5602 7224719.1139,446858.6081 7224727.7298,446858.913 7224727.8239,446859.5627 7224728.0312,446898.3932 7224740.4259,446954.5698 7224757.7341,447066.025 7224792.5778,447086.0632 7224798.7061,447096.1221 7224801.7857,447108.9481 7224805.7248,447123.7501 7224810.226,447157.4909 7224820.7102,447203.247 7224834.8142,447212.9648 7224837.8099,447258.2451 7224852.0801,447303.2699 7224865.0043,447303.5689 7224865.0972,447356.146 7224881.506,447414.382 7224899.524,447526.4167 7224934.3481,447588.6038 7224953.5531,447656.4242 7224975.2207,447722.6598 7224995.3982,447771.1919 7225010.56,447794.8713 7225017.6709,447827.013 7225027.8371,447883.884 7225045.0738,447959.5611 7225068.3809,448027.952 7225089.5322,448095.5979 7225110.6263,448152.3588 7225128.3102,448153.1188 7225128.5472,448208.5539 7225145.158,448260.2008 7225161.1422,448310.822 7225177.0549,448367.7717 7225194.2939,448424.1771 7225211.362,448478.81 7225228.6582,448515.6602 7225239.7952,448559.1229 7225253.303,448591.9548 7225263.4311,448610.6852 7225270.5808,448618.746 7225272.8088,448627.4602 7225275.0272,448659.5001 7225285.4638,448682.7197 7225293.8082,448707.0948 7225303.0471,448737.6159 7225314.4877,448772.4311 7225329.93,448812.6188 7225347.0428,448849.703 7225364.2729,448903.8178 7225389.2899,448964.9418 7225416.5331,448998.2877 7225433.0652,449029.4098 7225447.5039,449062.913 7225462.5472,449105.4531 7225482.5418,449147.8872 7225502.0249,449191.1808 7225521.6849,449229.611 7225539.2801,449230.5841 7225539.7262,449267.6082 7225557.1242,449299.096 7225570.9829,449302.5901 7225572.85,449305.5751 7225574.1078,449307.9781 7225575.119,449328.2652 7225584.9862,449375.8641 7225606.979,449425.6111 7225629.453,449521.9462 7225673.4672,449577.9799 7225699.1828,449633.0131 7225724.0521,449685.7022 7225748.398,449685.9487 7225748.5153,449727.751 7225767.7322,449782.6478 7225793.6271,449820.1269 7225809.9918,449863.4061 7225830.239,449905.9838 7225850.1771,449953.2753 7225871.7721,449988.0452 7225887.306,450029.122 7225906.324,450080.318 7225929.9689,450124.2041 7225949.8188,450170.6928 7225971.2041,450216.2763 7225992.4793,450257.6539 7226011.5002,450305.4463 7226033.4013,450346.9228 7226052.6229,450397.4208 7226075.6579,450430.89 7226090.3748,450448.5262 7226098.2993,450583.7049 7226157.1661,450695.9189 7226207.3193,450764.0781 7226237.1472,450783.3129 7226245.5428,450819.0661 7226261.1488,450874.0552 7226285.3529,450935.4358 7226312.876,450946.2517 7226317.7352,450960.8012 7226324.3638,450973.9029 7226330.3217,450991.2182 7226337.9872,451099.9707 7226385.6342,451175.2118 7226418.951,451240.8209 7226446.788,451348.5848 7226494.0461,451457.2921 7226541.237,451543.6999 7226577.3648,451609.5008 7226604.8093,451632.0219 7226614.0297,451697.8728 7226640.8078,451752.0519 7226662.831,451797.3548 7226679.4721,451830.6418 7226690.8431,451886.5231 7226708.9647,451932.9671 7226722.6387,451988.4809 7226738.9487,452054.6337 7226757.7332,452116.6689 7226776.1783,452172.4501 7226792.1172,452252.006 7226815.69,452287.7478 7226826.4958,452357.0297 7226846.827,452403.553 7226860.5111,452449.3592 7226874.2399,452505.6567 7226890.535,452564.4723 7226908.2291,452654.4939 7226934.4408,452757.2159 7226965.059,452758.1521 7226965.3312,452850.2558 7226992.0229,452928.9053 7227015.3039,452998.4218 7227035.3183,453072.5069 7227058.1871,453260.4342 7227112.723,453564.9129 7227201.949,453769.0328 7227260.627,453836.2237 7227280.823,453936.801 7227309.3562,454204.4069 7227386.484,454343.0321 7227426.5228,454486.0811 7227468.3381,454623.1328 7227508.4442,454721.3422 7227536.445,454825.0098 7227566.476,454893.7212 7227586.298,454951.382 7227603.1821,454997.991 7227616.525,455059.5318 7227634.8348,455156.2749 7227662.4592,455297.9613 7227704.3651,455377.2248 7227726.7539,455508.473154967 7227764.8910053),(455508.49 7227764.8959,455820.8092 7227855.9818,455952.3901 7227894.3858,456095.8667 7227935.3179,456192.031 7227963.121,456231.8822 7227974.645,456317.533 7227999.6073,456412.5103 7228027.9612,456505.6503 7228058.0452,456640.365 7228103.6591,456890.7271 7228188.0467,456983.1828 7228219.8912,457029.3582 7228235.4228,457064.1859 7228247.6413,457088.5198 7228255.9988,457187.7011 7228289.013,457309.067 7228330.2929,457403.2528 7228361.982,457493.8277 7228392.9522,457559.0217 7228415.7228,457565.711 7228417.9972,457725.743 7228472.4009,457837.2333 7228510.2611,457971.6342 7228555.7279,458083.9613 7228593.5798,458222.571 7228640.6063,458385.7742 7228695.9759,458622.1371 7228776.2102,458722.4857 7228810.4387,458788.3319 7228832.5887,458895.1911 7228868.9779,459002.9503 7228905.2069,459115.2482 7228943.708,459197.7599 7228971.514,459317.5833 7229011.4349,459429.2249 7229049.93,459452.4088 7229057.5377,459550.6848 7229090.7568,459635.359 7229119.5258,459730.7091 7229152.412,459802.1457 7229176.2879,459858.2598 7229195.3118,459901.6183 7229209.2211,459960.8501 7229227.771,459994.071 7229236.5638,460044.379 7229250.42,460077.5993 7229259.3772,460144.6258 7229276.6008,460194.5372 7229290.0372,460276.5241 7229311.4422,460310.5633 7229320.4017,460389.5969 7229340.7228,460459.1462 7229358.9749,460512.8572 7229373.5899,460513.3182 7229373.7668,460530.7591 7229378.084,460623.7818 7229401.4972,460663.4418 7229412.4947,460705.711 7229423.6662,460806.6712 7229450.3508,460895.715707064 7229473.83001087),(460895.7388 7229473.8361,460905.516260949 7229475.93401739))'));

INSERT INTO yllapitokohdeosa (id, yllapitokohde, nimi, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, tr_ajorata, tr_kaista, sijainti)
VALUES (10004, (SELECT id
                FROM yllapitokohde
                WHERE nimi = 'Nakkilan ramppi 2018'), 'Nakkilan kohdeosa 2018', 20, 12, 1, 19, 2, 1, 11, ST_GeomFromText(
                   'MULTILINESTRING((474515.874868717 7235208.68684686,474654.2953 7235286.9223,474770.2672 7235352.3551,474816.5641 7235379.0838,474851.8379 7235400.1368,474871.8558 7235412.1051,474905.3262 7235433.3308,474938.8239 7235457.4712,474970.5321 7235481.144,475015.0102 7235516.522,475064.2319 7235555.7413,475102.1201 7235585.228,475114.4291 7235595.2751,475152.3722 7235626.0172,475220.0639 7235679.2351,475310.6633 7235751.9981,475415.7299 7235835.8819,475462.0042 7235872.4557,475537.2262 7235932.407,475599.1232 7235981.8741,475640.9022 7236015.008,475708.3558 7236068.7959,475791.753 7236135.0672,475858.9707 7236189.5853,475909.7421 7236229.6818,475958.881 7236268.7397,476007.1999 7236307.0782,476055.4931 7236345.6841,476125.3498 7236401.6922,476203.191 7236463.5487,476261.8678 7236510.4311,476281.5201 7236526.1318,476348.1708 7236579.1532,476400.552 7236621.009,476476.8519 7236681.7423,476551.4498 7236741.107,476604.8041 7236783.6042,476691.4823 7236852.6932,476738.6732 7236890.2932,476816.1827 7236950.7709,476900.911 7237020.0052,476918.402 7237033.9222,477072.8589 7237156.5447,477223.3601 7237277.3347,477412.5899 7237427.4077,477599.656 7237577.8202,477776.9639 7237717.197,477938.708 7237847.6457,478046.1551 7237931.816,478130.6988 7237999.1879,478243.6697 7238090.4019,478306.737 7238139.4741,478311.109 7238142.7348,478315.1808 7238145.9371,478340.707 7238165.6019,478379.9621 7238192.0607,478404.9749 7238208.1658,478448.6877 7238231.9292,478484.4272 7238250.3701,478527.8768 7238269.7442,478569.9601 7238287.4799,478632.2281 7238308.047,478723.3277 7238330.0808,478773.9568 7238338.0857,478840.8428 7238344.7882,478914.2078 7238349.2322,478963.3861 7238349.7039),(478963.3861 7238349.7039,478986.6581 7238347.1948,479057.4539 7238342.6757,479192.8113 7238337.059,479384.5329 7238328.4329,479428.9472 7238326.0251,479507.7718 7238321.7103,479525.8958 7238321.1558,479533.6458 7238321.207,479537.8302 7238321.151,479686.3089 7238315.3259,479795.7273 7238310.5132,479829.7861 7238308.6711,479835.5082 7238308.6937,479891.9427 7238306.0852,479912.605 7238305.4182,479948.4273 7238303.192,480057.8808 7238298.5609,480183.4781 7238294.2127,480329.4603 7238288.2089,480452.4919 7238281.4523,480592.7668 7238275.7278,480656.9299 7238272.9692,480796.156 7238265.835,480903.8639 7238262.8542,480903.9878 7238262.8482,480997.5173 7238258.4733,481081.2218 7238255.9469,481118.025 7238253.7058,481197.217 7238248.5691,481293.0882 7238244.6271,481357.1208 7238241.6237,481395.588 7238239.0747,481455.0872 7238237.4268,481494.9331 7238235.4228,481558.3029 7238233.2132,481559.597 7238232.6999,481659.7771 7238229.0598,481900.827 7238218.07,482171.0093 7238206.4971,482384.3443 7238197.419,482609.2278 7238187.0598,482873.8261 7238174.3322,483101.032316641 7238165.85985888),(483101.1948 7238165.8538,483196.7057 7238161.8618,483205.2258 7238161.5068,483258.4902 7238158.3998,483300.0709 7238156.8168,483407.2118 7238151.4651,483613.2459 7238143.543,483875.0367 7238132.0541,484022.519 7238125.6619,484107.5499 7238121.8772,484180.2801 7238118.52,484269.2077 7238113.6561,484320.985 7238111.1482,484321.272 7238111.1678,484403.7932 7238108.7213,484468.4232 7238105.6512,484544.5808 7238101.3691,484620.268 7238098.1793,484695.5817 7238095.3212,484747.2792 7238095.0181,484756.5741 7238095.136,484810.652 7238095.8459,484841.4947 7238095.5171,484876.5672 7238095.691,484916.6328 7238095.5207,484923.9992 7238095.943,484982.1031 7238095.9108,485089.3829 7238096.345,485102.3071 7238096.4361,485168.6058 7238096.1991,485201.0149 7238096.0651,485267.6228 7238097.2961,485304.6248 7238098.2007,485343.2403 7238097.4932,485378.9547 7238097.682,485418.906 7238097.252,485456.4041 7238097.682,485495.1911 7238097.9518,485502.5307 7238098.062,485518.7782 7238098.2019,485542.9852 7238097.4313,485570.7567 7238097.6832,485603.6548 7238097.495,485661.038 7238097.579,485715.93 7238098.0149,485770.2313 7238098.1721,485902.7788 7238098.6403,486108.0303 7238099.0309,486299.3278 7238099.1042,486368.2661 7238099.5783,486427.2568 7238099.5789,486476.4892 7238100.0851,486597.2018 7238099.396,486769.1889 7238099.3019,487099.8281 7238100.53,487303.4841 7238100.6967,487410.1218 7238101.6609,487466.0882 7238102.1677,487580.199 7238102.434,487638.0367 7238105.8078,487688.864 7238110.8659,487736.5242 7238118.2919,487788.2657 7238128.4771,487847.6781 7238145.0902,487893.5313 7238160.4878,487954.9697 7238181.3562,488011.325 7238201.7302,488102.1751 7238235.1238,488201.9079 7238270.9711,488243.9019 7238286.243,488317.9602 7238313.373,488394.1732 7238340.7288,488476.2221 7238369.2019,488532.9408 7238389.7927,488561.6532 7238400.1513,488614.1982 7238418.8572,488693.6183 7238447.3987,488726.929 7238458.8352,488766.1603 7238473.5533,488817.6338 7238491.9149,488894.465 7238519.291,488945.2721 7238539.1749,488988.6061 7238556.0357,489021.9223 7238572.1051,489053.2463 7238587.6462,489092.599 7238608.0828,489128.3152 7238629.352,489130.4003 7238631.0582,489178.1349 7238664.7871,489214.7849 7238690.8201,489247.686 7238717.6238,489290.9069 7238756.618,489361.0839 7238833.4498,489393.546 7238871.7562,489394.7758 7238873.3427,489425.0659 7238916.946,489448.5839 7238953.6097,489468.7091 7238989.0121,489493.7177 7239032.4903,489526.5622 7239101.6882,489563.4189 7239175.1098,489594.8001 7239233.7849,489642.2608 7239331.2307,489679.75 7239399.5389,489701.9762 7239438.7773,489724.882 7239472.421,489741.4641 7239497.441,489764.6087 7239528.1992,489781.441 7239549.0938,489809.01 7239580.6137,489831.4631 7239606.084,489895.3969 7239668.1799,489938.3188 7239703.2922,489975.9617 7239731.2441,490016.3078 7239759.0782,490068.9141 7239791.4873,490107.5998 7239811.4712,490152.2321 7239833.2359,490256.222 7239872.619,490315.5051 7239889.4328,490364.2081 7239901.3291,490370.596 7239902.7191,490426.999 7239912.3451,490477.5208 7239918.0923,490573.099 7239921.917,490625.4403 7239920.5198,490668.4509 7239916.8368,490708.5957 7239912.067,490753.1399 7239904.03,490797.3238 7239894.6839,490846.6801 7239882.9942,490953.8539 7239857.577,490974.4697 7239852.4832,491002.8219 7239845.477,491131.9949 7239813.5331,491307.1892 7239770.9983,491355.8499 7239759.0079,491386.271 7239750.9,491402.4988 7239747.4427,491485.5268 7239726.1069,491638.1893 7239688.5349,491666.623 7239681.2047,491740.1429 7239664.1563,491793.281 7239650.3357,491848.6132 7239635.5778,491931.3202 7239605.8112,491962.316 7239592.8608,491999.0429 7239575.586,492008.3717 7239571.3802,492046.394 7239554.237,492092.9869 7239531.5641,492143.4182 7239507.609,492228.2918 7239469.5891,492263.8491 7239452.1083,492271.9171 7239448.4628,492287.1312 7239440.5461,492314.1279 7239428.3961,492328.0812 7239421.4042,492336.7758 7239416.5272,492359.7399 7239406.199,492570.199 7239312.0978,492635.6509 7239283.5092,492852.5372 7239187.8929,492854.2077 7239187.2062,492855.9289 7239186.4987,492917.5609 7239161.6228,492965.6748 7239143.341,493010.4358 7239130.568,493060.586 7239118.3817,493097.3909 7239111.0522,493122.517 7239107.1262,493144.184 7239104.208,493172.9703 7239100.4179,493194.1043 7239097.6301,493240.0492 7239095.0447,493256.5903 7239094.1198,493313.7591 7239095.6897,493339.6552 7239097.2358,493378.2188 7239100.7978,493433.2002 7239108.0369,493490.9348 7239119.0058,493539.3853 7239130.8301,493578.5528 7239143.5071,493619.2562 7239157.741,493623.8807 7239159.3228,493633.4639 7239162.9599,493641.0811 7239165.868,493642.5152 7239166.4081,493668.4011 7239177.4331,493713.3771 7239198.5028,493760.484 7239223.9182,493801.7252 7239248.0491,493848.7892 7239278.718,493887.1938 7239309.628,493924.0601 7239339.5977,493960.2837 7239372.6542,493990.6572 7239402.5733,494025.5968 7239441.493,494061.4828 7239487.734,494117.7411 7239570.0449,494160.1168 7239642.9413,494169.5147 7239665.024,494197.8252 7239716.8132,494261.1438 7239848.3071,494297.5461 7239922.5381,494340.9808 7240012.368,494378.236 7240083.8999,494419.1711 7240169.6669,494464.7301 7240261.0131,494481.4051 7240299.158,494513.0948 7240361.3962,494548.4853 7240432.9002,494619.1638 7240575.2101,494663.0981 7240665.5408,494706.1171 7240752.7008,494752.6612 7240847.0069,494806.1352 7240957.4651,494829.4418 7241004.6453,494859.2191 7241064.9241,494915.7799 7241179.4917,494954.7509 7241256.9078,495004.4592 7241355.5549,495056.932 7241466.511,495103.1063 7241559.3401,495153.2201 7241649.1438,495211.5158 7241742.971,495262.006 7241814.0188,495310.1837 7241876.5489,495320.1891 7241889.2122,495350.4352 7241923.224,495370.5817 7241947.5472,495389.1101 7241969.026,495426.4671 7242010.8669,495477.17 7242069.2061,495524.5092 7242124.1082,495566.6622 7242172.4628,495621.6561 7242234.2472,495637.9738 7242251.2087,495648.8439 7242265.6242,495673.3767 7242291.83,495705.4458 7242328.9518,495727.725 7242356.3879,495746.958 7242379.4652,495768.2611 7242411.5289,495797.1618 7242456.0362,495844.6201 7242529.1791,495882.625 7242589.857,495916.6862 7242643.384,495950.4443 7242696.2893,496011.8898 7242791.5262,496064.5282 7242871.8331,496114.4069 7242949.9471,496160.3423 7243020.9097,496209.3222 7243096.2318,496248.8691 7243157.2348,496298.7417 7243235.9671,496357.7568 7243327.1602,496398.8111 7243391.8891,496440.1768 7243456.3118,496483.375 7243522.9179,496521.38 7243583.5958,496574.3227 7243664.2147,496621.7751 7243737.9752,496662.2361 7243800.2248,496673.186 7243817.3418,496706.9637 7243868.3918,496752.9057 7243938.7368,496807.6578 7244023.7022,496851.13 7244093.7119,496894.3538 7244157.8452,496945.6421 7244238.2873,496980.1548 7244295.739,497001.4722 7244328.5191,497011.022 7244340.0271,497046.1682 7244396.5641,497122.9143 7244516.0093,497167.9969 7244584.2353,497211.7919 7244654.594,497238.084 7244693.5941,497270.7069 7244739.5522,497337.9592 7244829.2599,497417.4698 7244932.656,497473.2861 7245004.852,497526.0782 7245074.1899,497587.5911 7245153.9477,497646.4608 7245228.6969,497648.8008 7245232.858,497651.9209 7245237.5379,497675.2358 7245267.0889,497698.923 7245298.5987,497741.8211 7245354.7039,497771.5001 7245394.4033,497790.1 7245415.3139),(497790.1 7245415.3139,497810.5169 7245442.715,497847.7108 7245491.5668,497874.7318 7245525.9419,497920.6327 7245585.8318,497959.0498 7245635.3323,498000.7699 7245688.9337,498021.1368 7245716.9607,498056.3891 7245760.2263,498092.692 7245807.5088,498126.2582 7245852.8701,498152.1883 7245894.0268,498186.761 7245949.0052,498218.1368 7246008.2382,498254.8578 7246076.8829,498295.7149 7246153.6712,498322.0939 7246201.955,498340.1811 7246235.1472,498360.897 7246274.0533,498383.3978 7246316.3647,498400.211 7246349.1639,498423.0292 7246391.174,498439.0289 7246420.644,498465.2471 7246468.5728,498494.5337 7246524.6631,498529.4591 7246590.7891,498577.4849 7246678.2701,498599.897 7246721.039,498636.2868 7246790.6038,498673.0738 7246860.804,498674.8748 7246864.1749,498704.1781 7246915.3417,498727.2673 7246959.1552,498737.3632 7246978.9599,498752.4481 7247010.3208,498760.7311 7247027.1608,498774.0639 7247054.0259,498789.9772 7247087.071,498797.729 7247106.5017,498806.386 7247127.373,498813.771 7247143.3382,498818.0048 7247152.726,498834.4881 7247192.3812,498848.8601 7247231.0961,498863.6948 7247268.1899,498876.4523 7247303.6357,498892.3031 7247349.6462,498899.9471 7247373.2762,499038.5341 7247793.1628,499049.365 7247825.962,499066.1031 7247879.5068,499069.5431 7247888.8059,499077.9559 7247906.036,499082.2338 7247914.7532,499095.3802 7247949.7101,499101.549 7247968.195,499106.8012 7247983.9738,499107.7023 7247986.6812,499119.8588 7248024.0263,499138.9381 7248082.3058,499152.1881 7248122.8729,499163.4251 7248156.9752,499165.4667 7248163.1731,499178.678 7248204.1333,499185.3387 7248224.9701,499213.8987 7248315.0263,499229.8698 7248364.1497,499246.8027 7248415.282,499262.0812 7248462.58,499263.5379 7248467.0938,499274.7862 7248508.0378,499276.8379 7248516.144,499276.8481 7248526.9158,499285.333 7248555.4389,499285.8642 7248557.5168,499290.3422 7248575.0358,499290.4518 7248575.4402,499297.0101 7248599.6192,499301.8972 7248617.6408,499303.4779 7248623.5262,499313.8108 7248662.051,499321.2583 7248691.8111,499324.7583 7248706.4928,499331.1278 7248723.6503,499343.9341 7248776.5299,499348.623 7248795.7807,499357.0192 7248835.2353,499367.3997 7248896.364,499369.5062 7248935.0122,499379.5021 7248993.8361,499381.072 7249003.072,499399.1603 7249121.5262,499401.5312 7249140.1249,499412.3311 7249171.599,499412.9117 7249174.1331,499426.9502 7249232.5151,499432.5961 7249256.0551,499438.0651 7249276.0629,499443.5669 7249297.1481,499458.8757 7249347.312,499475.4561 7249397.801,499479.2153 7249410.3703,499486.7139 7249439.7158,499562.4987 7249622.6172,499569.8581 7249634.8041,499580.2821 7249652.4338,499595.1532 7249679.8051,499616.3051 7249716.7768,499623.3023 7249729.109,499636.08 7249751.7629,499647.3498 7249771.0959,499655.1242 7249783.8468,499671.9892 7249811.6731,499686.6542 7249835.0202,499716.668 7249881.2307,499747.45 7249926.8553,499754.8951 7249943.55,499819.1892 7250027.0098,499830.889 7250041.7713,499844.3903 7250058.1533,499903.0182 7250124.2919,499911.795 7250132.4838,499921.695 7250141.9859,499942.4198 7250160.531,499958.7637 7250175.8452,499999.5088 7250214.4118,500019.6292 7250233.3112,500036.141666038 7250248.45710631),(500036.4829 7250248.7701,500057.8658 7250269.0709,500077.845 7250287.1062,500088.1 7250296.2999,500096.941 7250307.0592,500136.3348 7250336.5167,500238.5613 7250415.726,500245.2577 7250420.2171,500259.3647 7250429.9158,500273.48 7250438.8903,500274.1352 7250439.3203,500286.1613 7250447.0578,500303.7023 7250458.3668,500326.564 7250472.2743,500333.6041 7250476.5587,500376.1299 7250502.481,500403.0122 7250517.875,500417.1228 7250525.9317,500462.3441 7250550.324,500503.5729 7250572.6622,500546.1398 7250595.0159,500572.588 7250609.4219,500595.1263 7250621.6809,500632.818 7250641.3027,500673.3833 7250663.1859,500699.81 7250678.434,500795.5287 7250729.2882,500863.2092 7250765.706,500926.2169 7250799.3961,500971.3358 7250823.783,501044.1738 7250862.9291,501130.0861 7250909.1468,501184.1658 7250938.2113,501253.6442 7250976.0632,501307.6197 7251005.0478,501339.1039 7251021.2512,501388.0803 7251047.769,501424.5892 7251066.9341,501457.9798 7251084.9843,501500.0548 7251107.2701,501549.2533 7251133.7867,501602.2359 7251162.3122,501652.9912 7251189.9437,501698.4049 7251215.57,501749.8278 7251245.2068,501788.1169 7251269.717,501827.0718 7251294.8948,501852.4491 7251312.942,501872.7338 7251328.691,501903.6332 7251351.7123,501952.2921 7251388.9698,501989.3948 7251419.7531,502042.7831 7251465.9761,502097.7198 7251517.2353,502140.0348 7251557.1502,502182.1688 7251598.3581,502230.2363 7251643.371,502302.5972 7251712.4951,502320.7939 7251730.0658,502336.9199 7251745.3419,502405.617 7251812.8318,502460.5501 7251863.7288,502496.0072 7251897.5017,502531.6949 7251932.1632,502554.8061 7251953.791,502580.5831 7251980.4559,502605.9432 7252002.9382,502663.3699 7252058.1793,502704.0257 7252096.7309,502745.1663 7252137.5517,502774.1593 7252165.0891,502828.582 7252216.764,502853.5133 7252239.7091,502861.3657 7252246.9909,502882.5968 7252267.6282,502908.8549 7252292.793,502941.6178 7252323.9163,502975.5051 7252356.1431,503013.8859 7252393.4531,503045.2998 7252423.0279,503074.9139 7252450.1687,503107.9102 7252482.4039,503136.4077 7252509.3309,503169.1593 7252538.8962,503203.043 7252570.8991,503238.4751 7252601.5549,503269.8497 7252626.2319,503305.9311 7252654.6561,503326.9888 7252670.2913,503355.6602 7252691.2049,503381.8731 7252710.8048,503411.654 7252731.264,503444.1167 7252753.0388,503487.3078 7252779.6221,503527.8088 7252804.0019,503567.8452 7252826.1591,503602.0558 7252843.6881,503644.7729 7252866.9358,503687.4691 7252887.5111,503731.4951 7252907.4087,503774.847 7252926.4219,503832.2481 7252948.435,503879.5938 7252965.8551,503908.1717 7252975.4167,503922.9118 7252980.8602,503958.1683 7252990.3659,503990.9728 7252999.4457,504027.7908 7253009.3838,504063.0378 7253017.5531,504092.0331 7253023.7701,504128.6701 7253031.999,504171.2633 7253040.1081,504231.2509 7253051.6357,504263.4682 7253056.7891,504326.1609 7253065.2978,504369.1489 7253069.9009,504436.7043 7253075.3771,504483.399 7253079.0421,504537.4388 7253081.5762,504577.8117 7253083.0628,504637.0912 7253081.6549,504683.833 7253079.9331,504733.4538 7253076.4062,504761.9131 7253073.8822),(504761.9131 7253073.8822,504763.907240227 7253073.72921387))'));

INSERT INTO yllapitokohdeosa (id, yllapitokohde, nimi, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, tr_ajorata, tr_kaista, sijainti)
VALUES (10005, (SELECT id
                FROM yllapitokohde
                WHERE nimi = 'Oulaisten ohitusramppi 2018'), 'Oulaisten kohdeosa 2018', 20, 19, 5, 21, 15, 1, 11, ST_GeomFromText(
                   'MULTILINESTRING((504766.898450568 7253073.49973469,504804.1901 7253070.6388,504860.7157 7253067.5002,504914.7829 7253063.046,504972.6313 7253058.1148,505020.4778 7253055.4938,505076.77 7253050.7978,505137.0489 7253043.844,505158.3359 7253042.342,505209.0829 7253037.5919,505264.471 7253031.3451,505310.9508 7253024.9499,505348.088 7253019.5232,505393.2057 7253009.7989,505443.6591 7252999.1402,505491.8677 7252986.2732,505551.6332 7252970.8631,505601.1569 7252955.5358,505633.1128 7252945.8937,505654.4362 7252939.7059,505683.176 7252931.1608,505731.8558 7252915.3529,505764.2577 7252904.5971,505846.9862 7252878.6629,505891.9121 7252864.6548,505936.2788 7252849.6908,505988.7041 7252832.2099,506053.4592 7252811.5631,506125.187 7252788.9289,506181.1439 7252770.6548,506236.2873 7252753.3991,506296.2892 7252733.9738,506346.6639 7252719.9752,506381.6399 7252710.3771,506422.656 7252700.7517,506439.9778 7252697.0581,506469.0077 7252691.9797,506541.5271 7252678.9299,506599.971 7252669.6362,506635.0458 7252665.1963,506682.7513 7252660.4437,506721.9122 7252657.4499,506756.1842 7252655.3839,506783.1189 7252654.7168,506812.6592 7252653.8128,506850.7279 7252653.275,506886.3513 7252653.2041,506927.0821 7252651.5318,506978.3799 7252646.8477,506992.1558 7252645.8353,507009.5521 7252644.0861,507038.788 7252639.9827,507061.988 7252636.8251,507091.9923 7252631.4031,507122.8053 7252624.693,507147.6418 7252620.2977,507174.52 7252614.0158,507198.7288 7252607.9852,507243.8073 7252595.9561,507302.5633 7252579.7861,507363.6509 7252562.6947,507467.1357 7252531.5929,507520.9558 7252518.0308,507619.6541 7252489.5679,507679.6322 7252471.479,507729.1708 7252457.7371,507789.4478 7252441.1758,507814.9401 7252434.0702,507845.2808 7252424.9492,507882.3258 7252414.54,507933.1412 7252400.2663,507977.8039 7252388.1151,508098.2967 7252353.7198,508204.5039 7252324.3159,508236.6777 7252314.8459,508305.0347 7252297.0298,508371.1048 7252282.1777,508434.3787 7252268.543,508454.9981 7252264.9071,508494.0263 7252257.9897,508524.5588 7252252.9412,508542.3469 7252250.5351,508565.6863 7252246.5163,508599.9279 7252241.9418,508641.9541 7252236.4001,508687.7632 7252230.597,508730.6869 7252225.9362,508758.4989 7252223.8749,508810.5543 7252219.3451,508857.4909 7252215.0893,508918.666 7252209.5768,508985.399 7252203.3413,509054.5749 7252196.4131,509102.643 7252191.3312,509146.1843 7252183.9672,509173.4978 7252179.638,509174.054 7252179.5499,509176.2832 7252179.102,509241.068 7252166.073,509304.202 7252152.4258,509354.2218 7252141.67,509424.4768 7252126.254,509455.2333 7252119.3479,509501.0121 7252109.3318,509519.6751 7252105.36,509542.4552 7252100.7343,509572.457 7252095.089,509598.0172 7252090.6032,509612.9193 7252087.7398,509643.2118 7252083.1498,509696.3362 7252077.5111,509740.4152 7252074.217,509780.9132 7252072.3452,509816.589 7252070.6098,509838.5859 7252070.6348,509864.0901 7252071.2381,509892.453 7252072.048,509898.4771 7252072.2458,509903.6447 7252072.4298,509941.2679 7252075.3933,509991.894 7252080.3251,510046.2792 7252087.096,510081.9299 7252093.079,510118.209 7252097.847,510144.8072 7252101.5097,510185.8209 7252105.996,510239.3438 7252114.587,510310.7072 7252125.8919,510382.756 7252138.9721,510421.9509 7252146.7942,510440.0928 7252150.4188,510509.9352 7252165.5251,510546.749 7252173.1703,510599.1862 7252184.443,510642.0283 7252193.586,510690.446 7252203.7862,510729.4932 7252212.0769,510773.901 7252221.8721,510828.5679 7252233.5688,510888.3721 7252246.7712,510935.0162 7252257.6568,510977.557 7252270.0641,510980.7938 7252271.0009,511033.4817 7252285.1662,511081.4998 7252299.8229,511125.2882 7252314.5219,511185.8488 7252336.6243,511255.3582 7252362.864,511302.7551 7252382.2018,511363.3812 7252410.7588,511388.2208 7252423.2,511428.502 7252443.5002,511460.9581 7252460.5398,511493.4172 7252477.802,511504.7823 7252483.8678,511526.9942 7252495.7212,511558.1151 7252512.7738,511602.682 7252538.5978,511650.868 7252569.951,511693.9031 7252598.461,511723.0497 7252618.4283,511753.0938 7252639.277,511776.024 7252655.6191,511801.2918 7252672.7331,511816.7751 7252683.8557,511839.8739 7252700.3211,511878.4369 7252724.8123,511930.6852 7252755.9129,511972.4178 7252779.6501,512016.2199 7252802.0479,512050.0298 7252818.3793,512071.5997 7252828.2137,512085.1511 7252834.5207,512106.1868 7252843.2367,512134.209 7252853.7299,512159.5482 7252863.4928,512175.3109 7252869.2561,512187.206 7252873.824,512205.492 7252879.751,512239.8212 7252890.583,512263.7251 7252897.6189,512275.2718 7252901.066,512309.869 7252910.4622,512344.892 7252918.819,512402.514 7252930.8273,512434.2478 7252937.1622,512486.09 7252944.6192,512533.1832 7252949.9292,512550.2691 7252951.1853,512574.7037 7252953.4073,512593.5168 7252954.5609,512642.165 7252957.2433,512694.9642 7252957.5589,512745.1537 7252957.1289,512764.4028 7252956.0301,512801.119 7252955.1201,512840.5021 7252954.2482,512846.0932 7252954.1291,512876.8049 7252953.3227,512925.0988 7252951.33,512929.7709 7252951.1948,512938.8651 7252951.464,512949.5268 7252950.3967,512983.9888 7252949.4689,513027.3389 7252948.0973,513088.3437 7252946.2677,513148.3778 7252944.4638,513209.712 7252942.7891,513256.1441 7252940.9953,513291.7448 7252939.956,513328.7981 7252938.7172,513370.2412 7252937.4231,513413.362 7252936.279,513466.7521 7252934.3149,513485.2781 7252934.5019,513507.1821 7252933.2202,513535.7052 7252931.8469,513558.0268 7252931.5562,513641.0988 7252929.0859,513665.8419 7252928.0472,513715.0708 7252926.578,513762.0347 7252925.316,513810.4459 7252923.5388,513878.648 7252921.6777,513936.0361 7252920.8707,513991.448 7252918.0537,514021.2831 7252917.9412,514063.987 7252915.472,514136.529 7252913.0511,514154.584 7252912.284,514196.8508 7252912.3531,514245.4507 7252911.8683,514280.4517 7252911.9791,514295.6081 7252912.3102,514323.046 7252913.5871,514356.7749 7252914.858,514380.6193 7252916.4231,514394.5851 7252917.6678,514431.103 7252922.1881,514488.1188 7252929.3128,514541.7423 7252936.9919,514568.0779 7252941.3282,514581.2528 7252943.0481,514588.8343 7252944.029),(514588.8343 7252944.029,514603.508136803 7252947.14003093))'));

-- Jostain syystä geometria ei toimi tämän kohteen kanssa. Aiheuttaa laskutusyhteenvedon kaatumisen.
INSERT INTO yllapitokohdeosa (id, yllapitokohde, nimi, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, tr_ajorata, tr_kaista, sijainti)
VALUES (10006, (SELECT id
                FROM yllapitokohde
                WHERE nimi = 'Kuusamontien testi 2018'), 'Kuusamontien testiosa 2018', 20, 26, 1, 41, 15, 1, 11, null);

----------------------------
-- Utajärven päällystysurakka
----------------------------

-- Päällystyskohteet 2019


INSERT INTO yllapitokohde
(urakka, sopimus, yha_kohdenumero, kohdenumero, nimi, yllapitokohdetyyppi, yllapitokohdetyotyyppi, yhaid,
 tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, tr_ajorata, tr_kaista,
 suorittava_tiemerkintaurakka, vuodet, poistettu)
VALUES
  ((SELECT id
    FROM urakka
    WHERE nimi = 'Utajärven päällystysurakka'),
   (SELECT id
    FROM sopimus
    WHERE urakka = (SELECT id
                    FROM urakka
                    WHERE nimi = 'Utajärven päällystysurakka') AND paasopimus IS NULL),
   111, 'L11', 'Ouluntie', 'paallyste' :: yllapitokohdetyyppi,
   'paallystys' ::yllapitokohdetyotyyppi, 13371,
   22, 12, 4336, 12, 9477, NULL, NULL, NULL, ARRAY[(SELECT date_part('year', now()))::INTEGER], FALSE),
  ((SELECT id
    FROM urakka
    WHERE nimi = 'Utajärven päällystysurakka'),
   (SELECT id
    FROM sopimus
    WHERE urakka = (SELECT id
                    FROM urakka
                    WHERE nimi = 'Utajärven päällystysurakka') AND paasopimus IS NULL),
   112, 'L12', 'Kirkkotie', 'paallyste' :: yllapitokohdetyyppi,
   'paallystys' ::yllapitokohdetyotyyppi, 13372,
   18642, 1, 13, 1, 493, NULL, NULL, NULL, ARRAY[(SELECT date_part('year', now()))::INTEGER], FALSE),
  ((SELECT id
    FROM urakka
    WHERE nimi = 'Utajärven päällystysurakka'),
   (SELECT id
    FROM sopimus
    WHERE urakka = (SELECT id
                    FROM urakka
                    WHERE nimi = 'Utajärven päällystysurakka') AND paasopimus IS NULL),
   113, 'L13', 'Puolangalle menevä (EI SAA NÄKYÄ)', 'paallyste' :: yllapitokohdetyyppi,
   'paallystys' ::yllapitokohdetyotyyppi, 13373,
   837, 1, 136, 1, 546, NULL, NULL, NULL, (SELECT ARRAY[date_part('year', now())]), TRUE),
  ((SELECT id
    FROM urakka
    WHERE nimi = 'Utajärven päällystysurakka'),
   (SELECT id
    FROM sopimus
    WHERE urakka = (SELECT id
                    FROM urakka
                    WHERE nimi = 'Utajärven päällystysurakka') AND paasopimus IS NULL),
   115, 'L15', 'Puolangantie', 'paallyste' :: yllapitokohdetyyppi,
   'paallystys' ::yllapitokohdetyotyyppi, 13375,
   837, 2, 0, 2, 1000, NULL, NULL, NULL, (SELECT ARRAY[date_part('year', now())]), FALSE),
  ((SELECT id
    FROM urakka
    WHERE nimi = 'Utajärven päällystysurakka'),
    (SELECT id
     FROM sopimus
     WHERE urakka = (SELECT id
                     FROM urakka
                     WHERE nimi = 'Utajärven päällystysurakka') AND paasopimus IS NULL),
    114, 'L14', 'Ouluntie 2', 'paallyste' :: yllapitokohdetyyppi,
    'paallystys' ::yllapitokohdetyotyyppi, 13374,
    22, 13, 0, 13, 3888, NULL, NULL, NULL, (SELECT ARRAY[date_part('year', now())]), FALSE),
  ((SELECT id
      FROM urakka
     WHERE nimi = 'Utajärven päällystysurakka'),
   (SELECT id
      FROM sopimus
     WHERE urakka = (SELECT id
                       FROM urakka
                      WHERE nimi = 'Utajärven päällystysurakka') AND paasopimus IS NULL),
   116, 'L42', 'Tärkeä kohde mt20', 'paallyste' :: yllapitokohdetyyppi,
   'paallystys' ::yllapitokohdetyotyyppi, 13376,
   20, 1, 1066, 1, 3827, NULL, NULL, NULL, '{2021}', FALSE),
  ((SELECT id
      FROM urakka
     WHERE nimi = 'Utajärven päällystysurakka'),
   (SELECT id
      FROM sopimus
     WHERE urakka = (SELECT id
                       FROM urakka
                      WHERE nimi = 'Utajärven päällystysurakka') AND paasopimus IS NULL),
   117, 'L43', 'Aloittamaton kohde mt20', 'paallyste' :: yllapitokohdetyyppi,
   'paallystys' ::yllapitokohdetyotyyppi, 13377,
   20, 3, 1, 3, 5000, NULL, NULL, NULL, '{2021}', FALSE);

INSERT INTO yllapitokohteen_aikataulu
(yllapitokohde, kohde_alku, paallystys_alku, paallystys_loppu, tiemerkinta_alku, tiemerkinta_loppu,
 kohde_valmis, muokkaaja, muokattu, valmis_tiemerkintaan, tiemerkinta_takaraja)
VALUES
  ((SELECT id
    FROM yllapitokohde
    WHERE nimi = 'Ouluntie'), make_date((SELECT date_part('year', now())::INT), 5, 19),
   make_date((SELECT date_part('year', now())::INT), 5, 19), make_date((SELECT date_part('year', now())::INT), 5, 21), make_date((SELECT date_part('year', now())::INT), 5, 22),
   make_date((SELECT date_part('year', now())::INT), 5, 23),
   make_date((SELECT date_part('year', now())::INT), 5, 24), (SELECT id
                  FROM kayttaja
                  WHERE kayttajanimi = 'jvh'), NOW(),
   make_date((SELECT date_part('year', now())::INT), 5, 21), make_date((SELECT date_part('year', now())::INT), 6, 4)),
  ((SELECT id
    FROM yllapitokohde
    WHERE nimi = 'Kirkkotie'), make_date((SELECT date_part('year', now())::INT), 6, 19),
   make_date((SELECT date_part('year', now())::INT), 6, 19), make_date((SELECT date_part('year', now())::INT), 6, 21), make_date((SELECT date_part('year', now())::INT), 6, 22),
   make_date((SELECT date_part('year', now())::INT), 6, 23),
   make_date((SELECT date_part('year', now())::INT), 6, 24), (SELECT id
                  FROM kayttaja
                  WHERE kayttajanimi = 'jvh'), NOW(),
   make_date((SELECT date_part('year', now())::INT), 6, 21), make_date((SELECT date_part('year', now())::INT), 7, 04)),
  ((SELECT id
    FROM yllapitokohde
    WHERE nimi = 'Puolangalle menevä (EI SAA NÄKYÄ)'), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL),
  ((SELECT id
    FROM yllapitokohde
    WHERE nimi = 'Puolangantie'), make_date((SELECT date_part('year', now())::INT), 6, 19),
   make_date((SELECT date_part('year', now())::INT), 6, 19), make_date((SELECT date_part('year', now())::INT), 6, 21), make_date((SELECT date_part('year', now())::INT), 6, 22),
   make_date((SELECT date_part('year', now())::INT), 6, 23),
   make_date((SELECT date_part('year', now())::INT), 6, 24), (SELECT id
                  FROM kayttaja
                  WHERE kayttajanimi = 'jvh'), NOW(),
   make_date((SELECT date_part('year', now())::INT), 6, 21), make_date((SELECT date_part('year', now())::INT), 7, 04)),
  ((SELECT id
    FROM yllapitokohde
    WHERE nimi = 'Ouluntie 2'), make_date((SELECT date_part('year', now())::INT), 5, 19),
                              make_date((SELECT date_part('year', now())::INT), 5, 19), make_date((SELECT date_part('year', now())::INT), 5, 21), make_date((SELECT date_part('year', now())::INT), 5, 22),
                              make_date((SELECT date_part('year', now())::INT), 5, 23),
                              make_date((SELECT date_part('year', now())::INT), 5, 24), (SELECT id
                                             FROM kayttaja
                                             WHERE kayttajanimi = 'jvh'), NOW(),
                              make_date((SELECT date_part('year', now())::INT), 5, 21), make_date((SELECT date_part('year', now())::INT), 6, 4)),
  ((SELECT id
      FROM yllapitokohde
     WHERE nimi = 'Tärkeä kohde mt20'), make_date(2021, 6, 19),
   make_date(2021, 6, 19), make_date(2021, 6, 21), make_date(2021, 6, 22),
   make_date(2021, 6, 23),
   make_date(2021, 6, 24),
   (SELECT id
     FROM kayttaja
    WHERE kayttajanimi = 'jvh'), NOW(),
   make_date(2021, 6, 21), make_date(2021, 7, 4)),
  ((SELECT id
      FROM yllapitokohde
     WHERE nimi = 'Aloittamaton kohde mt20'), make_date(2021, 6, 19),
   make_date(2021, 6, 19), NULL, NULL, NULL, NULL,
   (SELECT id
      FROM kayttaja
     WHERE kayttajanimi = 'jvh'), NOW(),
   NULL, NULL);

INSERT INTO yllapitokohdeosa (yllapitokohde, nimi, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, tr_ajorata, tr_kaista, sijainti, yllapitoluokka, keskimaarainen_vuorokausiliikenne, poistettu, yhaid)
VALUES ((SELECT id
              FROM yllapitokohde
              WHERE nimi = 'Ouluntie'), 'Ouluntien kohdeosa', 22, 12, 4336, 12, 9477, 0, 11, ST_GeomFromText(
                 'MULTILINESTRING((468134.81260847015 7183376.276973084,468143.51099999994 7183378.708999999,468197.51999999955 7183396.298,468253.1339999996 7183416.984999999,468284.8540000003 7183430.048999999,468340.56900000013 7183455.2250000015,468381.2000000002 7183475.642000001,468409.7110000001 7183490.482999999,468434.4910000004 7183504.710000001,468465.29899999965 7183522.960999999,468495.33800000045 7183541.5940000005,468520.0020000003 7183558.127999999,468544.36899999995 7183575.403999999,468579.3320000004 7183600.857000001,468599.9160000002 7183616.870000001,468634.23599999957 7183644.43,468648.05599999987 7183656.019000001,468687.6059999997 7183691.311000001,468716.9620000003 7183719.441,468741.16700000037 7183743.66,468792.42399999965 7183795.397,468837.108 7183840.914999999,468860.11000000034 7183863.484000001,468885.57799999975 7183887.870000001,468902.0650000004 7183903.118999999,468923.91500000004 7183921.763,468950.38800000027 7183942.388,468973.5039999997 7183959.991,468998.49700000044 7183976.743999999,469033.73900000006 7184000.386999998,469072.2790000001 7184022.664000001,469110.05700000003 7184041.866999999,469159.90500000026 7184064.447999999,469199.9129999997 7184080.022,469237.63900000043 7184093.629000001,469290.25100000016 7184108.640000001,469334.91500000004 7184118.855,469380.84700000007 7184127.316,469423.9840000002 7184133.0249999985,469476.44400000013 7184137.300000001,469513.5959999999 7184138.379000001,469558.5630000001 7184138.363000002,469600.5480000004 7184135.66,469631.8849999998 7184133.219999999,469668.00100000016 7184128.260000002,469715.91700000037 7184120.072999999,469751.8150000004 7184112.971999999,469810.24899999984 7184100.938000001,469858.44099999964 7184091.872000001,469904.82100000046 7184083.903999999,469953.94400000013 7184077.035999998,469995.98599999957 7184072.41,470049.88900000043 7184067.888,470087.99899999984 7184066.044,470136.1749999998 7184064.745000001,470180.74899999984 7184065.1000000015,470205.01499999966 7184065.984999999,470229.32600000035 7184067.362,470288.7460000003 7184072.155000001,470356.67700000014 7184080.089000002,470447.8300000001 7184094.013999999,470462.91700000037 7184096.024,470504.7419999996 7184101.934,470527.7280000001 7184104.372000001,470542.7429999998 7184105.545000002,470578.08800000045 7184106.954999998,470637.6639999999 7184106.125999998,470711.80599999987 7184098.800000001,470758.56099999975 7184090.381999999,470806.25 7184078.951000001,470822.3420000002 7184074.079,470843.42899999954 7184067.322999999,470894.5070000002 7184048.486000001,470930.625 7184033.357999999,470958.7259999998 7184019.487,471000.1059999997 7183996.16,471035.45100000035 7183974.18,471057.1200000001 7183959.219999999,471088.91700000037 7183935.306000002,471105.2000000002 7183922.447000001,471150.5480000004 7183882.269000001,471197.5599999996 7183835.749000002,471227.6509999996 7183804.182,471262.11000000034 7183765.978,471286.0630000001 7183737.793000001,471317.69099999964 7183700.118000001,471347.05900000036 7183663.607000001,471366.73500000034 7183637.191,471380.62200000044 7183617.726,471399.074 7183588.322000001,471422.81099999975 7183549.294,471457.38800000027 7183488.469999999,471481.4840000002 7183442.875,471510.4349999996 7183381.166000001,471514.7549999999 7183371.383000001,471526.31400000025 7183345.140000001,471564.0039999997 7183246.386999998,471613.9280000003 7183112.289000001,471689.68599999975 7182907.864999998,471768.00100000016 7182697.888999999,471822.33999999985 7182552.612,471872.4139999999 7182417.454,471933.1789620049 7182254.283865866))'), 2, 100, FALSE, 1231231),
      ((SELECT id
             FROM yllapitokohde
             WHERE nimi = 'Kirkkotie'), 'Kirkkotien kohdeosa', 18642, 1, 13, 1, 493, 0, 11, ST_GeomFromText(
             'MULTILINESTRING((471625.9775238041 7183117.168444241,471664.159 7183132.629999999,471694.99700000044 7183139.478,471713.3289999999 7183140.567000002,471733.5800000001 7183141.305,471752.41700000037 7183140.903000001,471767.02300000004 7183138.855,471783.01099999994 7183135.147999998,471803.7999999998 7183128.613000002,471831.01499999966 7183116.688000001,471862.45799999963 7183101.9690000005,471888.83100000024 7183085.998,471899.0650000004 7183077.710000001,471912.5049999999 7183063.421999998,471945.79200000037 7183022.590999998,471972.80399999954 7182989.798999999,471996.6179999998 7182959.302000001,472015.95461845584 7182930.261998949))'), 8, 2, FALSE, 1231232),
      ((SELECT id
             FROM yllapitokohde
             WHERE nimi = 'Puolangantie'), 'Puolangantien kohdeosa', 837, 2, 0, 2, 1000, 0, 11, ST_GeomFromText(
             'MULTILINESTRING((473846.98 7182870.803,473960.292 7182931.312,474183.474 7183050.716,474330.801 7183128.039,474404.246 7183166.586,474452.892 7183194.074,474453.6 7183194.541,474489.012 7183219.83,474518.267 7183248.087,474546.19 7183280.333,474567.13 7183306.317,474574.948 7183316.489,474650.223 7183414.291,474660.147240582 7183426.73039112))'), 8, 2, FALSE, 1231234),
      ((SELECT id
              FROM yllapitokohde
              WHERE nimi = 'Puolangalle menevä (EI SAA NÄKYÄ)'), 'Puolangalle menevän kohdeosa', 837, 1, 136, 1, 546, 1, 11, ST_GeomFromText(
                 'MULTILINESTRING((472232.2118663851 7181868.216653759,472234.14499999955 7181870.535,472262.98000000045 7181905.555,472277.3269999996 7181922.000999998,472277.70799999963 7181922.467,472310.926 7181963.146000002,472331.7450000001 7181989.028999999,472334.2709999997 7181992.050000001,472334.983 7181992.888999999,472384.61099999957 7182049.7179999985,472416.6579999998 7182084.666999999,472433.176 7182102.636,472459.9689999996 7182122.691,472485.9369999999 7182140.669,472511.26800000016 7182155.986000001,472516.87106485467 7182159.262039106))'), 8, 2, TRUE, 1231233),
       ((SELECT id
           FROM yllapitokohde
          WHERE nimi = 'Tärkeä kohde mt20'), 'Tärkeä kohdeosa kaista 11', 20, 1, 1066, 1, 3827, 1, 11, ST_GeomFromText(
                'MULTILINESTRING((471625.9775238041 7183117.168444241,471664.159 7183132.629999999,471694.99700000044 7183139.478,471713.3289999999 7183140.567000002,471733.5800000001 7183141.305,471752.41700000037 7183140.903000001,471767.02300000004 7183138.855,471783.01099999994 7183135.147999998,471803.7999999998 7183128.613000002,471831.01499999966 7183116.688000001,471862.45799999963 7183101.9690000005,471888.83100000024 7183085.998,471899.0650000004 7183077.710000001,471912.5049999999 7183063.421999998,471945.79200000037 7183022.590999998,471972.80399999954 7182989.798999999,471996.6179999998 7182959.302000001,472015.95461845584 7182930.261998949))'), 8, 2, FALSE, 1231238),
       ((SELECT id
           FROM yllapitokohde
          WHERE nimi = 'Tärkeä kohde mt20'), 'Tärkeä kohdeosa kaista 12', 20, 1, 1066, 1, 3827, 1, 12, ST_GeomFromText(
                'MULTILINESTRING((471625.9775238041 7183117.168444241,471664.159 7183132.629999999,471694.99700000044 7183139.478,471713.3289999999 7183140.567000002,471733.5800000001 7183141.305,471752.41700000037 7183140.903000001,471767.02300000004 7183138.855,471783.01099999994 7183135.147999998,471803.7999999998 7183128.613000002,471831.01499999966 7183116.688000001,471862.45799999963 7183101.9690000005,471888.83100000024 7183085.998,471899.0650000004 7183077.710000001,471912.5049999999 7183063.421999998,471945.79200000037 7183022.590999998,471972.80399999954 7182989.798999999,471996.6179999998 7182959.302000001,472015.95461845584 7182930.261998949))'), 8, 2, FALSE, 1231239),
       ((SELECT id
           FROM yllapitokohde
          WHERE nimi = 'Aloittamaton kohde mt20'), 'Kohdeosa kaista 11', 20, 1, 3, 1, 5000, 1, 11, ST_GeomFromText(
                'MULTILINESTRING((471625.9775238041 7183117.168444241,471664.159 7183132.629999999,471694.99700000044 7183139.478,471713.3289999999 7183140.567000002,471733.5800000001 7183141.305,471752.41700000037 7183140.903000001,471767.02300000004 7183138.855,471783.01099999994 7183135.147999998,471803.7999999998 7183128.613000002,471831.01499999966 7183116.688000001,471862.45799999963 7183101.9690000005,471888.83100000024 7183085.998,471899.0650000004 7183077.710000001,471912.5049999999 7183063.421999998,471945.79200000037 7183022.590999998,471972.80399999954 7182989.798999999,471996.6179999998 7182959.302000001,472015.95461845584 7182930.261998949))'), 8, 2, FALSE, 1231240),
       ((SELECT id
           FROM yllapitokohde
          WHERE nimi = 'Aloittamaton kohde mt20'), 'Kohdeosa kaista 12', 20, 1, 3, 1, 5000, 1, 12, ST_GeomFromText(
                'MULTILINESTRING((471625.9775238041 7183117.168444241,471664.159 7183132.629999999,471694.99700000044 7183139.478,471713.3289999999 7183140.567000002,471733.5800000001 7183141.305,471752.41700000037 7183140.903000001,471767.02300000004 7183138.855,471783.01099999994 7183135.147999998,471803.7999999998 7183128.613000002,471831.01499999966 7183116.688000001,471862.45799999963 7183101.9690000005,471888.83100000024 7183085.998,471899.0650000004 7183077.710000001,471912.5049999999 7183063.421999998,471945.79200000037 7183022.590999998,471972.80399999954 7182989.798999999,471996.6179999998 7182959.302000001,472015.95461845584 7182930.261998949))'), 8, 2, FALSE, 1231241);


DO $$
DECLARE
BEGIN
 FOR i IN 0..32 LOOP
   INSERT INTO yllapitokohdeosa (yllapitokohde, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys,
                                 tr_ajorata, tr_kaista, sijainti)
     VALUES ((SELECT id
               FROM yllapitokohde
               WHERE nimi = 'Ouluntie 2'), 22, 13, 0+i*5, 13, 0+(i+1)*5, 1, 11,
               (SELECT tierekisteriosoitteelle_viiva_ajr AS geom
                FROM tierekisteriosoitteelle_viiva_ajr(22, 13, 0+i*5, 13, 0+(i+1)*5, 1))
            );
 END LOOP;
END;
$$ LANGUAGE plpgsql;

INSERT INTO yllapitokohteen_kustannukset (yllapitokohde, sopimuksen_mukaiset_tyot, arvonvahennykset, bitumi_indeksi, kaasuindeksi)
VALUES ((SELECT id FROM yllapitokohde WHERE nimi = 'Ouluntie'), 400, 100, 4543.95, 0),
       ((SELECT id FROM yllapitokohde WHERE nimi = 'Puolangantie'), 400, 100, 4543.95, 0),
       ((SELECT id FROM yllapitokohde WHERE nimi = 'Kirkkotie'), 200, 10, 4543.95, 0);

INSERT INTO paallystysilmoitus (versio, paallystyskohde, tila, takuupvm, ilmoitustiedot, paatos_tekninen_osa, perustelu_tekninen_osa, kasittelyaika_tekninen_osa)
VALUES (1, (SELECT id
         FROM yllapitokohde
         WHERE nimi = 'Ouluntie'), 'aloitettu' ::paallystystila, '2022-12-31'::DATE,
         '{
            "osoitteet": [
              {
                "rc%": 12,
                "leveys": 12,
                "km-arvo": "12",
                "raekoko": 1,
                "esiintyma": "12",
                "muotoarvo": "12",
                "pinta-ala": 12,
                "pitoisuus": 12,
                "kuulamylly": 2,
                "lisaaineet": "12",
                "kohdeosa-id": 667,
                "massamenekki": 1,
                "tyomenetelma": 21,
                "sideainetyyppi": 2,
                "paallystetyyppi": 2,
                "kokonaismassamaara": 12
              }
            ],
            "alustatoimet": [
              {
                "tr-numero": 22,
                "tr-ajorata": 0,
                "tr-kaista": 11,
                "tr-alkuosa": 12,
                "tr-alkuetaisyys": 4336,
                "tr-loppuosa": 12,
                "tr-loppuetaisyys": 9477,
                "kasittelymenetelma": 13,
                "paksuus": 30,
                "verkkotyyppi": 1,
                "verkon-tarkoitus": 1,
                "verkon-sijainti": 1,
                "tekninen-toimenpide": 2
              }
            ]
          }',
        NULL, 'Tekninen osa ok!', make_date((SELECT date_part('year', now())::INT), 5, 21));
