-- Replikoidaan olemassa oleva potti vanhasta tietomallista
-- Kenties päästään tekemään niiden pohjalta aikanaan myös
-- hyviä testejä, esim. että YHA:an lähtevä hyötykuorma on identtinen jne.
DO $$
DECLARE
    urakkaid INTEGER;
    kohdeid INTEGER;
    kohdeosaid_kaista11 INTEGER;
    kohdeosaid_kaista21 INTEGER;
    massa1_id INTEGER;
    massa2_id INTEGER;
    paallystysilmoituksen_id INTEGER;
    murskeen_toimenpide_id INTEGER;
    verkon_alusta_toimenpide_id INTEGER;
    ab_alusta_toimenpide_id INTEGER;
    tas_alusta_toimenpide_id INTEGER;
    task_alusta_toimenpide_id INTEGER;
    tjyr_alusta_toimenpide_id INTEGER;
    murske1_id INTEGER;

    paikkauspot_urakkaid INTEGER;
    paikkauspot_massaid INTEGER;
    paikkauspot_murskeid INTEGER;

BEGIN
    urakkaid = (SELECT id FROM urakka where nimi = 'Utajärven päällystysurakka');
    kohdeid = (SELECT id FROM yllapitokohde WHERE nimi = 'Tärkeä kohde mt20');
    kohdeosaid_kaista11 = (SELECT id FROM yllapitokohdeosa WHERE nimi = 'Tärkeä kohdeosa kaista 11');
    kohdeosaid_kaista21 = (SELECT id FROM yllapitokohdeosa WHERE nimi = 'Tärkeä kohdeosa kaista 12');
    massa1_id = (SELECT id from pot2_mk_urakan_massa WHERE dop_nro = '1234567' and urakka_id = urakkaid);
    massa2_id = (SELECT id from pot2_mk_urakan_massa WHERE dop_nro = '987654331-2' and urakka_id = urakkaid);
    murskeen_toimenpide_id = (SELECT koodi from pot2_mk_alusta_toimenpide WHERE nimi = 'Murske');
    verkon_alusta_toimenpide_id = (SELECT koodi from pot2_mk_alusta_toimenpide WHERE nimi = 'Verkko');
    ab_alusta_toimenpide_id = (SELECT koodi from pot2_mk_alusta_toimenpide WHERE nimi = 'Asfalttibetoni');
    tas_alusta_toimenpide_id = (SELECT koodi from pot2_mk_alusta_toimenpide WHERE nimi = 'Massatasaus');
    task_alusta_toimenpide_id = (SELECT koodi from pot2_mk_alusta_toimenpide WHERE nimi = 'Kuumennustasaus');
    tjyr_alusta_toimenpide_id = (SELECT koodi from pot2_mk_alusta_toimenpide WHERE nimi = 'Tasausjyrsintä');
    murske1_id = (SELECT id from pot2_mk_urakan_murske WHERE dop_nro = '1234567-dop' and urakka_id = urakkaid);

    -- Luodaan myös ns. paikkaus POT testidataan, helpottaa asioiden testaamista
    paikkauspot_urakkaid = (SELECT id FROM urakka where nimi = 'Utajärven päällystysurakka');
    paikkauspot_massaid = (SELECT id from pot2_mk_urakan_massa WHERE dop_nro = '764567-dop' and urakka_id = paikkauspot_urakkaid);
    paikkauspot_murskeid = (SELECT id from pot2_mk_urakan_murske WHERE dop_nro = '3524534-dop' and urakka_id = paikkauspot_urakkaid);


INSERT INTO paallystysilmoitus(
    paallystyskohde,
    takuupvm,
    tila,
    paatos_tekninen_osa,
    lisatiedot,
    poistettu,
    muokkaaja,
    muokattu,
    luoja,
    luotu,
    versio)
VALUES
(kohdeid,
 '2024-12-31',
 'aloitettu' ::paallystystila,
 NULL,
 'Jouduttiin tekemään alustatöitä hieman suunniteltua enemmän joten meni pari päivää pitkäksi.',
 FALSE,
 NULL,
 NULL,
 (SELECT id FROM kayttaja WHERE kayttajanimi = 'skanska'),
 NOW(),
 2);

    paallystysilmoituksen_id = (SELECT id FROM paallystysilmoitus WHERE versio = 2 and paallystyskohde = kohdeid);

-- kulutuskerros (= järj.nro 1 DEFAULT)
    INSERT INTO pot2_paallystekerros (kohdeosa_id, toimenpide, materiaali, leveys, massamenekki, pinta_ala, kokonaismassamaara, piennar, lisatieto, pot2_id) VALUES (kohdeosaid_kaista11, 22, massa1_id, 3, 100.205239647471, 8283, 830, true, null, paallystysilmoituksen_id);
    INSERT INTO pot2_paallystekerros (kohdeosa_id, toimenpide, materiaali, leveys, massamenekki, pinta_ala, kokonaismassamaara, piennar, lisatieto, pot2_id) VALUES (kohdeosaid_kaista21, 23, massa2_id, 3, 100, 8283, 828.3, false, null, paallystysilmoituksen_id);

    INSERT INTO pot2_alusta (tr_numero, tr_alkuetaisyys, tr_alkuosa, tr_loppuetaisyys, tr_loppuosa, tr_ajorata, tr_kaista, toimenpide, pot2_id, poistettu, verkon_tyyppi, verkon_tarkoitus, verkon_sijainti, lisatty_paksuus, massamenekki, murske, kasittelysyvyys, leveys, pinta_ala, kokonaismassamaara, massa, sideaine, sideainepitoisuus, sideaine2)
    VALUES (20, 1066, 1, 3827, 1, 1, 11, murskeen_toimenpide_id, paallystysilmoituksen_id, false, null, null, null, 10, null, 1, null, null, null, null, null, null, null, null),
           (20, 1066, 1, 2000, 1, 1, 12, verkon_alusta_toimenpide_id, paallystysilmoituksen_id, false, 1, 1, 1, null, null, null, null, null, null, null, null, null, null, null),
           (20, 2000, 1, 2050, 1, 1, 12, ab_alusta_toimenpide_id, paallystysilmoituksen_id, false, null, null, null, null, 34, null, null, null, 12, 23, massa1_id, null, null, null),
           (20, 2050, 1, 2100, 1, 1, 12, tas_alusta_toimenpide_id, paallystysilmoituksen_id, false, null, null, null, null, 55, null, null, null, null, 12, massa1_id, null, null, null),                                                                            (20, 2100, 1, 2150, 1, 1, 12, task_alusta_toimenpide_id, paallystysilmoituksen_id, false, null, null, null, null, null, null, null, null, null, null, null, null, null, null),
           (20, 2150, 1, 2200, 1, 1, 12, tjyr_alusta_toimenpide_id, paallystysilmoituksen_id, false, null, null, null, null, null, null, null, null, null, null, null, null, null, null);


    -- paikkaus-POT tiedot
    INSERT INTO yllapitokohde (urakka, sopimus, kohdenumero, nimi, poistettu, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, yllapitokohdetyotyyppi, tunnus, yhaid, yllapitoluokka, lahetysaika, keskimaarainen_vuorokausiliikenne, nykyinen_paallyste, tr_ajorata, tr_kaista, lahetetty, lahetys_onnistunut, lahetysvirhe, yllapitokohdetyyppi, vuodet, yha_kohdenumero, muokattu, muokkaaja, yha_tr_osoite, velho_lahetyksen_aika, velho_lahetyksen_tila, velho_lahetyksen_vastaus, suorittava_tiemerkintaurakka, karttapaivamaara) VALUES (5, 8, '999', 'Pottilan AB-levityskohde', false, 4, 101, 1, 101, 200, 'paallystys', null, null, null, null, null, 998, null, null, '2022-11-28 09:33:49.375000', true, null, 'paallyste', '{2022}', null, '2022-11-28 10:27:23.266990', 3, null, '2022-11-28 09:33:49.407000', 'valmis', null, (SELECT id FROM urakka WHERE nimi = 'Oulun tiemerkinnän palvelusopimus 2017-2024'), '2022-12-13');

    INSERT INTO yllapitokohteen_aikataulu
    (yllapitokohde, kohde_alku, paallystys_alku, paallystys_loppu, tiemerkinta_alku, tiemerkinta_loppu,
     kohde_valmis, muokkaaja, muokattu, valmis_tiemerkintaan, tiemerkinta_takaraja, merkinta, jyrsinta, tiemerkinta_lisatieto, tiemerkinta_takaraja_kasin)
    VALUES
        ((SELECT id FROM yllapitokohde WHERE nimi = 'Pottilan AB-levityskohde'), '2022-11-01',
         '2022-11-02', '2022-11-05', '2022-11-07',
         '2022-11-09',
         '2022-11-11', (SELECT id
          FROM kayttaja
         WHERE kayttajanimi = 'jvh'), NOW(), '2022-12-06 00:00:00.000000', '2022-12-21', 'massa', 'ei jyrsintää', NULL, false);

    INSERT INTO yllapitokohdeosa (yllapitokohde, nimi, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, poistettu, sijainti, yhaid, tr_ajorata, tr_kaista, toimenpide, ulkoinen_id, paallystetyyppi, raekoko, tyomenetelma, massamaara, muokattu, keskimaarainen_vuorokausiliikenne, yllapitoluokka, nykyinen_paallyste, karttapaivamaara) VALUES ((SELECT id FROM yllapitokohde WHERE nimi = 'Pottilan AB-levityskohde'), 'Pottilan AB-levityskohde osa 1', 4, 101, 1, 101, 200, false, '0105000000010000000102000000070000006EB1DE0E038D1741DBBB6DFD627359417E8CB96B2B8D174117D9CE6761735941F7E461E1768D174188F4DBF360735941992A18D5C18D1741D712F2B56173594155302A29C68E17419E5E297F6C73594196438B6C638F174152B81EDD6C7359416C66904CE28F174159CD3A006D735941', null, 1, 11, '12', null, null, null, null, null, '2022-11-28 10:27:23.266990', null, null, null, '2022-12-13');

    INSERT INTO paikkauskohde ("luoja-id", "ulkoinen-id", nimi, poistettu, luotu, "muokkaaja-id", muokattu, "urakka-id", "yhalahetyksen-tila", tarkistettu, "tarkistaja-id", "ilmoitettu-virhe", alkupvm, loppupvm, tilattupvm, tyomenetelma, tierekisteriosoite_laajennettu, "paikkauskohteen-tila", "suunniteltu-maara", "suunniteltu-hinta", yksikko, lisatiedot, "pot?", valmistumispvm, tiemerkintapvm, "toteutunut-hinta", "tiemerkintaa-tuhoutunut?", takuuaika, "yllapitokohde-id") VALUES ((SELECT id FROM kayttaja WHERE kayttajanimi = 'skanska'), 999, 'Pottilan AB-levityskohde', false, '2022-11-28 09:09:57.746000', 3, '2022-11-28 09:09:57.746000', 5, null, null, null, null, '2022-11-28', '2022-11-30', '2022-11-28', 1, ROW(4,101,1,101,200,0,NULL,NULL,NULL,NULL)::tr_osoite_laajennettu, 'tilattu', 199, 2000, 'jm', null, true, '2022-11-25', null, 5000, null, 3, (SELECT id FROM yllapitokohde WHERE nimi = 'Pottilan AB-levityskohde'));


    INSERT INTO paallystysilmoitus (paallystyskohde, luotu, muokattu, luoja, muokkaaja, poistettu, takuupvm, paatos_tekninen_osa, kasittelyaika_tekninen_osa, tila, perustelu_tekninen_osa, asiatarkastus_pvm, asiatarkastus_tarkastaja, asiatarkastus_hyvaksytty, asiatarkastus_lisatiedot, versio, lisatiedot) VALUES ((SELECT id FROM yllapitokohde WHERE nimi = 'Pottilan AB-levityskohde'), '2022-11-28 08:47:43.112868', NULL, (SELECT id FROM kayttaja WHERE kayttajanimi = 'skanska'),NULL, false, '2025-11-05', 'hyvaksytty', '2022-11-28 09:28:36.000000', 'lukittu', null, null, null, null, null, 2, null);

    INSERT INTO pot2_paallystekerros (kohdeosa_id, toimenpide, materiaali, leveys, pinta_ala, kokonaismassamaara, piennar, lisatieto, pot2_id, jarjestysnro, velho_lahetyksen_aika, velho_rivi_lahetyksen_tila, velho_lahetyksen_vastaus, massamenekki) VALUES ((SELECT id FROM yllapitokohdeosa WHERE nimi = 'Pottilan AB-levityskohde osa 1'), 12, 3, 2.00, 398, 34, false, null, 7, 1, '2022-11-28 09:30:06.384000', 'onnistunut', '1.2.246.578.12.1.3151958991.813429633', 85.4);

    INSERT INTO pot2_alusta (tr_numero, tr_alkuetaisyys, tr_alkuosa, tr_loppuetaisyys, tr_loppuosa, tr_ajorata, tr_kaista, toimenpide, pot2_id, poistettu, verkon_tyyppi, verkon_tarkoitus, verkon_sijainti, lisatty_paksuus, massamenekki, murske, kasittelysyvyys, leveys, pinta_ala, kokonaismassamaara, massa, sideaine, sideainepitoisuus, sideaine2, velho_lahetyksen_aika, velho_rivi_lahetyksen_tila, velho_lahetyksen_vastaus) VALUES (4, 1, 101, 200, 101, 1, 11, 23, (SELECT id FROM paallystysilmoitus WHERE paallystyskohde = (SELECT id FROM yllapitokohde WHERE nimi = 'Pottilan AB-levityskohde')), false, null, null, null, 5, null, 2, null, null, null, null, null, null, null, null, null, 'ei-lahetetty', null);


END;
$$ LANGUAGE plpgsql;

--- Tuoreimman XSD-skeeman mukainen pot2-kohde

WITH urakka AS (INSERT INTO urakka (sampoid, sopimustyyppi, hallintayksikko, nimi, alkupvm, loppupvm,
                                    tyyppi, urakkanro, urakoitsija)
    VALUES ('5731290-TES2', 'kokonaisurakka' :: sopimustyyppi, (SELECT id
                                                                FROM organisaatio
                                                                WHERE lyhenne = 'POP'),
            'POT2 testipäällystysurakka',
            '2023-01-01', '2023-12-31', 'paallystys', 'testitunnus', (SELECT id
                                                                       FROM organisaatio
                                                                       WHERE ytunnus = '0651792-4')) RETURNING id),
     yhatiedot AS (
         INSERT INTO yhatiedot (urakka, yhatunnus, yhaid, elyt, vuodet, luotu, muokattu)
             SELECT id,
                    'YHA5731290',
                    5731290,
                    ARRAY ['POP'],
                    ARRAY [2023],
                    '2023-07-15T12:00:00.000',
                    '2023-07-15T12:00:00.000'
             FROM urakka),
     sopimus AS (
         INSERT INTO sopimus (nimi, alkupvm, loppupvm, sampoid, urakka, muokattu)
             SELECT 'POT2 testipäällystysurakka',
                    '2023-01-01',
                    '2023-12-31',
                    '5731290-TES2',
                    urakka.id,
                    '2023-12-16T12:00:00.000'
             FROM urakka
             RETURNING id, urakka),
     yllapitokohde AS (
         INSERT
             INTO yllapitokohde (urakka, sopimus, kohdenumero, nimi, tr_numero, tr_alkuosa, tr_alkuetaisyys,
                                 tr_loppuosa, tr_loppuetaisyys, yllapitokohdetyotyyppi, tunnus, yhaid, yllapitoluokka,
                                 keskimaarainen_vuorokausiliikenne, lahetetty, lahetys_onnistunut, yllapitokohdetyyppi,
                                 vuodet, yha_kohdenumero, yha_tr_osoite, karttapaivamaara, yotyo, luotu)
                 SELECT urakka.id,
                        sopimus.id,
                        '1',
                        'Kirkonkylä - Toppinen 2',
                        86,
                        20,
                        0,
                        20,
                        1300,
                        'paallystys',
                        'a',
                        123456,
                        '1',
                        1000,
                        '2023-12-16T12:00:00.000',
                        TRUE,
                        'paallyste',
                        ARRAY [2023],
                        '1',
                        (86, 20, 0, 20, 1300, NULL)::tr_osoite,
                        '2023-12-16T12:00:00.000',
                        FALSE,
                        '2023-12-16T12:00:00.000'
                 FROM urakka,
                      sopimus RETURNING id),
     alikohde AS (INSERT
         INTO yllapitokohdeosa (yllapitokohde, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys,
                                sijainti, yhaid, tr_ajorata, tr_kaista, muokattu, luotu)
             SELECT yllapitokohde.id,
                    tr_numero,
                    tr_alkuosa,
                    tr_alkuetaisyys,
                    tr_loppuosa,
                    tr_loppuetaisyys,
                    tierekisteriosoitteelle_viiva(tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa,
                                                  tr_loppuetaisyys),
                    alikohde.id,
                    1,
                    11,
                    '2023-12-16T12:00:00.000',
                    '2023-12-16T12:00:00.000'
             FROM yllapitokohde,
                  (SELECT 86   AS tr_numero,
                          20   AS tr_alkuosa,
                          0    AS tr_alkuetaisyys,
                          20   AS tr_loppuosa,
                          650  AS tr_loppuetaisyys,
                          123457 AS id
                   UNION ALL
                   SELECT 86   AS tr_numero,
                          20   AS tr_alkuosa,
                          650  AS tr_alkuetaisyys,
                          20   AS tr_loppuosa,
                          1300 AS tr_loppuetaisyys,
                          123458 AS id) AS alikohde RETURNING *),
     aikataulu AS (
         INSERT INTO yllapitokohteen_aikataulu (yllapitokohde, kohde_alku, paallystys_alku, paallystys_loppu,
                                                valmis_tiemerkintaan, tiemerkinta_takaraja, tiemerkinta_alku,
                                                tiemerkinta_loppu, kohde_valmis, muokattu, luotu)
             SELECT yllapitokohde.id,
                    '2023-01-01',
                    '2023-06-01',
                    '2023-08-01',
                    '2023-08-01',
                    '2023-08-01',
                    '2023-06-01',
                    '2023-08-01',
                    '2023-08-01',
                    '2021-07-15T12:00:00.000',
                    '2021-07-15T12:00:00.000'
             FROM yllapitokohde RETURNING id),
     kustannukset AS (
         INSERT INTO yllapitokohteen_kustannukset (yllapitokohde, sopimuksen_mukaiset_tyot, arvonvahennykset,
                                                   bitumi_indeksi, kaasuindeksi, toteutunut_hinta, muokattu,
                                                   maaramuutokset, maku_paallysteet)
             SELECT yllapitokohde.id,
                    100000,
                    0,
                    -100,
                    -100,
                    0,
                    '2023-12-16T12:00:00.000',
                    -1000,
                    100
             FROM yllapitokohde),
     paallystysilmoitus AS (
         INSERT INTO paallystysilmoitus (paallystyskohde, luotu, muokattu, poistettu, takuupvm, paatos_tekninen_osa,
                                         kasittelyaika_tekninen_osa, tila, versio)
             SELECT yllapitokohde.id,
                    '2023-12-16T12:00:00.000',
                    '2023-12-16T12:00:00.000',
                    FALSE,
                    '2023-08-01',
                    'hyvaksytty',
                    '2023-08-01',
                    'lukittu',
                    2
             FROM yllapitokohde RETURNING id),
     massa AS (
         INSERT INTO pot2_mk_urakan_massa (urakka_id, tyyppi, nimen_tarkenne, max_raekoko, kuulamyllyluokka, dop_nro,
                                           muokattu)
             SELECT urakka.id,
                    (SELECT koodi FROM pot2_mk_massatyyppi WHERE nimi = 'AB, Asfalttibetoni'),
                    'RAAHE',
                    16,
                    'AN10',
                    '7-23-1',
                    '2023-12-16T12:00:00.000'
             FROM urakka RETURNING id),
     murske AS (
         INSERT INTO pot2_mk_urakan_murske (id, urakka_id, nimen_tarkenne, tyyppi, esiintyma, rakeisuus, "iskunkestavyys", dop_nro, poistettu, muokkaaja, muokattu, luoja, luotu)
             SELECT massa.id,
                    urakka.id,
                    'LJYR',
                    1,
                    'Tonkkulan Kaivo',
                    '0/40'::"murskeen_rakeisuus",
                    'LA30'::"iskunkestavyys",
                    '1234567-dop', 
					false, 
					NULL, 
					NULL, 
					13, 
					'2023-12-02T12:00:00.000' 
             FROM urakka, massa RETURNING id),        
     runkoaine AS (
         INSERT INTO pot2_mk_massan_runkoaine (pot2_massa_id, tyyppi, esiintyma, fillerityyppi, kuvaus, kuulamyllyarvo,
                                               litteysluku, massaprosentti)
             SELECT massa.id,
                    (SELECT koodi FROM pot2_mk_runkoainetyyppi WHERE nimi = 'Kiviaines'),
                    'Alpua',
                    NULL,
                    NULL,
                    10,
                    20,
                    100
             FROM massa
             UNION ALL
             SELECT massa.id,
                    (SELECT koodi FROM pot2_mk_runkoainetyyppi WHERE lyhenne = 'Filleri'),
                    NULL,
                    'Kalkkifilleri (KF)'::fillerityyppi,
                    NULL,
                    NULL,
                    NULL,
                    1
             FROM massa),
     sideaine AS (
         INSERT INTO pot2_mk_massan_sideaine (pot2_massa_id, "lopputuote?", tyyppi, pitoisuus)
             SELECT massa.id,
                    TRUE,
                    (SELECT koodi FROM pot2_mk_sideainetyyppi WHERE nimi = 'Bitumi, 20/30'),
                    5.5
             FROM massa),
     lisaaine AS (
         INSERT INTO pot2_mk_massan_lisaaine (pot2_massa_id, tyyppi, pitoisuus)
             SELECT massa.id,
                    (SELECT koodi FROM pot2_mk_lisaainetyyppi WHERE nimi = 'Kuitu'),
                    0.5
             FROM massa),
     alusta AS (
         INSERT INTO pot2_alusta (tr_numero, tr_alkuetaisyys, tr_alkuosa, tr_loppuetaisyys, tr_loppuosa, tr_ajorata,
                                  tr_kaista, toimenpide, pot2_id, massamenekki, massa, lisatty_paksuus, kasittelysyvyys, verkon_tyyppi, kokonaismassamaara, murske)
             SELECT tr_numero,
                    tr_alkuetaisyys,
                    tr_alkuosa,
                    tr_loppuetaisyys,
                    tr_loppuosa,
                    tr_ajorata,
                    tr_kaista,
                    (SELECT koodi FROM pot2_mk_alusta_toimenpide WHERE lyhenne = 'TAS'),
                    paallystysilmoitus.id,
                    0.1,
                    massa.id,
                    12,
                    300,
                    5,
                    10.2,
                    murske.id
             FROM alikohde,
                  paallystysilmoitus,
                  massa,
                  murske
             RETURNING *),
     kulutuskerros AS (
         INSERT INTO pot2_paallystekerros (kohdeosa_id, toimenpide, materiaali, leveys, pinta_ala, kokonaismassamaara,
                                           piennar, pot2_id, massamenekki)
             SELECT alikohde.id,
                    (SELECT koodi FROM pot2_mk_paallystekerros_toimenpide WHERE lyhenne = 'REM'),
                    massa.id,
                    4,
                    2600,
                    260,
                    FALSE,
                    paallystysilmoitus.id,
                    90
             FROM alikohde,
                  massa,
                  paallystysilmoitus RETURNING *),
     yllapitokohde2 AS (
         INSERT INTO yllapitokohde (urakka, sopimus, kohdenumero, nimi, tr_numero, tr_alkuosa, tr_alkuetaisyys,
                                    tr_loppuosa, tr_loppuetaisyys, yllapitokohdetyotyyppi,
                                    lahetetty, lahetys_onnistunut, yllapitokohdetyyppi,
                                    vuodet, luotu, muokattu)
             SELECT urakka.id,
                    sopimus.id,
                    '2',
                    'MT 86 Paavolantie 2',
                    86,
                    20,
                    0,
                    20,
                    100,
                    'paallystys',
                    '2023-12-02T12:00:00.000',
                    TRUE,
                    'paallyste',
                    ARRAY [2023],
                    '2023-12-01T12:00:00.000',
                    '2023-12-02T12:00:00.000'
             FROM urakka,
                  sopimus RETURNING id),
     aikataulu2 AS (
         INSERT INTO yllapitokohteen_aikataulu (yllapitokohde, kohde_alku, paallystys_alku, paallystys_loppu,
                                                valmis_tiemerkintaan, tiemerkinta_takaraja, tiemerkinta_alku,
                                                tiemerkinta_loppu, kohde_valmis, muokattu, luotu)
             SELECT yllapitokohde2.id,
                    '2023-12-01',
                    '2023-12-01',
                    '2024-01-01',
                    '2024-01-01',
                    '2024-01-01',
                    '2024-01-01',
                    '2024-01-01',
                    '2024-01-01',
                    '2023-07-15T12:00:00.000',
                    '2023-07-15T12:00:00.000'
             FROM yllapitokohde2 RETURNING id),
     paikkauskohteet AS (
         INSERT INTO paikkauskohde ("ulkoinen-id", nimi, poistettu, luotu, muokattu,
                                    "urakka-id", "yhalahetyksen-tila", tarkistettu, alkupvm, loppupvm, tilattupvm,
                                    tyomenetelma, tierekisteriosoite_laajennettu, "paikkauskohteen-tila",
                                    "suunniteltu-maara", "suunniteltu-hinta", yksikko, "pot?", valmistumispvm,
                                    tiemerkintapvm, "toteutunut-hinta", "tiemerkintaa-tuhoutunut?", takuuaika,
                                    "yllapitokohde-id", "yhalahetyksen-aika")
             SELECT 1,
                    'MT 86 Paavolantie',
                    FALSE,
                    '2023-12-01T12:00:00.000'::TIMESTAMP,
                    '2023-12-02T12:00:00.000'::TIMESTAMP,
                    urakka.id,
                    'lahetetty'::lahetyksen_tila,
                    '2023-12-02T12:00:00.000'::TIMESTAMP,
                    '2023-12-01'::DATE,
                    '2023-12-02'::DATE,
                    '2023-12-01'::DATE,
                    (SELECT id FROM paikkauskohde_tyomenetelma WHERE nimi = 'AB-paikkaus levittäjällä'),
                    (86, 20, 700, 20, 800, 1, NULL, NULL, NULL, NULL)::tr_osoite_laajennettu,
                    'valmis'::paikkauskohteen_tila,
                    700,
                    1000,
                    'm2',
                    FALSE,
                    '2023-12-02'::DATE,
                    '2023-12-02'::DATE,
                    1001.10,
                    FALSE,
                    1,
                    NULL,
                    '2023-12-02T12:00:00.000'::TIMESTAMP
             FROM urakka
             UNION ALL
             SELECT 2,
                    'MT 86 Paavolantie 2',
                    FALSE,
                    '2023-12-01T12:00:00.000'::TIMESTAMP,
                    '2023-12-02T12:00:00.000'::TIMESTAMP,
                    urakka.id,
                    'lahetetty'::lahetyksen_tila,
                    '2023-12-02T12:00:00.000'::TIMESTAMP,
                    '2023-12-01'::DATE,
                    '2023-12-02'::DATE,
                    '2023-12-01'::DATE,
                    (SELECT id FROM paikkauskohde_tyomenetelma WHERE nimi = 'SMA-paikkaus levittäjällä'),
                    (86, 20, 0, 20, 100, NULL, NULL, NULL, NULL, NULL)::tr_osoite_laajennettu,
                    'valmis'::paikkauskohteen_tila,
                    100,
                    200,
                    'm2',
                    TRUE,
                    '2023-12-02'::DATE,
                    '2023-12-02'::DATE,
                    201.10,
                    FALSE,
                    1,
                    yllapitokohde2.id,
                    '2023-12-02T12:00:00.000'::TIMESTAMP
             FROM urakka,
                  yllapitokohde2 RETURNING *),
     paallystysilmoitus2 AS (
         INSERT INTO paallystysilmoitus (paallystyskohde, luotu, muokattu, poistettu, takuupvm, paatos_tekninen_osa,
                                         kasittelyaika_tekninen_osa, tila, versio)
             SELECT yllapitokohde2.id,
                    '2023-12-02T12:00:00.000',
                    '2023-12-02T12:00:00.000',
                    FALSE,
                    '2023-12-02',
                    'hyvaksytty',
                    '2023-12-02',
                    'lukittu',
                    2
             FROM yllapitokohde2 RETURNING id),
     alusta2 AS (
         INSERT INTO pot2_alusta (tr_numero, tr_alkuetaisyys, tr_alkuosa, tr_loppuetaisyys, tr_loppuosa, tr_ajorata,
                                  tr_kaista, toimenpide, pot2_id, kasittelysyvyys, leveys, pinta_ala)
             SELECT 86,
                    0,
                    20,
                    100,
                    20,
                    1,
                    11,
                    (SELECT koodi FROM pot2_mk_alusta_toimenpide WHERE nimi = 'Laatikkojyrsintä'),
                    paallystysilmoitus2.id,
                    4,
                    7,
                    700
             FROM paallystysilmoitus2
             RETURNING *),
     alikohde2 AS (
         INSERT INTO yllapitokohdeosa (yllapitokohde, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa,
                                       tr_loppuetaisyys,
                                       sijainti, yhaid, tr_ajorata, tr_kaista, muokattu, luotu)
             SELECT yllapitokohde2.id,
                    86,
                    20,
                    0,
                    20,
                    100,
                    tierekisteriosoitteelle_viiva(86, 20, 0, 20,
                                                  100),
                    NULL,
                    1,
                    11,
                    '2023-12-02T12:00:00.000',
                    '2023-12-01T12:00:00.000'
             FROM yllapitokohde2 RETURNING *),
     kulutuskerros2 AS (
         INSERT
             INTO pot2_paallystekerros (kohdeosa_id, toimenpide, materiaali, leveys, pinta_ala, kokonaismassamaara,
                                        piennar, pot2_id, massamenekki)
                 SELECT alikohde2.id,
                        (SELECT koodi FROM pot2_mk_paallystekerros_toimenpide WHERE lyhenne = 'LTA'),
                        massa.id,
                        4,
                        400,
                        46,
                        FALSE,
                        paallystysilmoitus2.id,
                        115
                 FROM alikohde2,
                      massa,
                      paallystysilmoitus2 RETURNING *),
     paikkaus AS (
         INSERT INTO paikkaus (luotu, muokattu, "urakka-id", "paikkauskohde-id", "ulkoinen-id", alkuaika, loppuaika,
                               tierekisteriosoite, tyomenetelma,
                               massatyyppi, leveys, raekoko, kuulamylly, sijainti, massamaara, "pinta-ala", lahde,
                               massamenekki)
             SELECT '2023-12-01T12:00:00'::DATE,
                    '2023-12-02T13:00:00'::TIMESTAMP,
                    urakka.id,
                    paikkauskohteet.id,
                    0,
                    '2023-12-02T12:00:00'::TIMESTAMP,
                    '2023-12-02T13:00:00'::TIMESTAMP,
                    (86, 20, 700, 20, 800, NULL)::tr_osoite,
                    paikkauskohteet.tyomenetelma,
                    'AB, Asfalttibetoni',
                    1.4,
                    16,
                    'AN14',
                    (SELECT tierekisteriosoitteelle_viiva(86, 20, 700, 20, 800)),
                    6.3,
                    140,
                    'harja-ui',
                    45
             FROM urakka,
                  paikkauskohteet
             WHERE paikkauskohteet."pot?" = FALSE)
SELECT *
FROM kulutuskerros;