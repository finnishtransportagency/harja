WITH urakka AS (INSERT INTO urakka (sampoid, sopimustyyppi, hallintayksikko, nimi, alkupvm, loppupvm,
                                    tyyppi, urakkanro, urakoitsija)
    VALUES ('5731289-TES2', 'kokonaisurakka' :: sopimustyyppi, (SELECT id
                                                                FROM organisaatio
                                                                WHERE lyhenne = 'POP'),
            'Analytiikan testipäällystysurakka',
            '2023-01-01', '2023-12-31', 'paallystys', 'analytiikka1', (SELECT id
                                                                       FROM organisaatio
                                                                       WHERE ytunnus = '0651792-4')) RETURNING id),
     yhatiedot AS (
         INSERT INTO yhatiedot (urakka, yhatunnus, yhaid, elyt, vuodet, luotu, muokattu)
             SELECT id,
                    'YHA5731289',
                    5731289,
                    ARRAY ['POP'],
                    ARRAY [2023],
                    '2022-07-15T12:00:00.000',
                    '2022-07-15T12:00:00.000'
             FROM urakka),
     sopimus AS (
         INSERT INTO sopimus (nimi, alkupvm, loppupvm, sampoid, urakka, muokattu)
             SELECT 'Analytiikan testipäällystysurakka',
                    '2023-01-01',
                    '2023-12-31',
                    '5731289-TES2',
                    urakka.id,
                    '2022-07-15T12:00:00.000'
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
                        'Kirkonkylä - Toppinen',
                        86,
                        20,
                        0,
                        20,
                        1300,
                        'paallystys',
                        'ANALYTIIKKA1',
                        1234,
                        '1',
                        1000,
                        '2022-07-15T12:00:00.000',
                        TRUE,
                        'paallyste',
                        ARRAY [2023],
                        '1',
                        (86, 20, 0, 20, 1300, NULL)::tr_osoite,
                        '2022-07-15T12:00:00.000',
                        FALSE,
                        '2022-07-15T12:00:00.000'
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
                    1,
                    '2022-07-15T12:00:00.000',
                    '2022-07-15T12:00:00.000'
             FROM yllapitokohde,
                  (SELECT 86   AS tr_numero,
                          20   AS tr_alkuosa,
                          0    AS tr_alkuetaisyys,
                          20   AS tr_loppuosa,
                          300  AS tr_loppuetaisyys,
                          1235 AS id
                   UNION ALL
                   SELECT 86   AS tr_numero,
                          20   AS tr_alkuosa,
                          300  AS tr_alkuetaisyys,
                          20   AS tr_loppuosa,
                          650  AS tr_loppuetaisyys,
                          1236 AS id
                   UNION ALL
                   SELECT 86   AS tr_numero,
                          20   AS tr_alkuosa,
                          650  AS tr_alkuetaisyys,
                          20   AS tr_loppuosa,
                          1300 AS tr_loppuetaisyys,
                          1237 AS id) AS alikohde RETURNING *),
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
                    '2022-07-15T12:00:00.000',
                    '2022-07-15T12:00:00.000'
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
                    '2022-07-15T12:00:00.000',
                    -1000,
                    100
             FROM yllapitokohde),
     paallystysilmoitus AS (
         INSERT INTO paallystysilmoitus (paallystyskohde, luotu, muokattu, poistettu, takuupvm, paatos_tekninen_osa,
                                         kasittelyaika_tekninen_osa, tila, versio)
             SELECT yllapitokohde.id,
                    '2023-12-15T12:00:00.000',
                    '2023-12-15T12:00:00.000',
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
                    '2022-07-15T12:00:00.000'
             FROM urakka RETURNING id),
     massa2 AS (
         INSERT INTO pot2_mk_urakan_massa (urakka_id, tyyppi, nimen_tarkenne, max_raekoko, kuulamyllyluokka, dop_nro,
                                           muokattu)
             SELECT urakka.id,
                    (SELECT koodi FROM pot2_mk_massatyyppi WHERE nimi = 'SMA, Kivimastiksiasfaltti'),
                    'RAAHE',
                    16,
                    'AN7',
                    '1234563-1',
                    '2022-07-15T12:00:00.000'
             FROM urakka RETURNING id),
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
     runkoaine2 AS (
         INSERT INTO pot2_mk_massan_runkoaine (pot2_massa_id, tyyppi, esiintyma, fillerityyppi, kuvaus, kuulamyllyarvo,
                                               litteysluku, massaprosentti)
             SELECT massa2.id,
                    (SELECT koodi FROM pot2_mk_runkoainetyyppi WHERE nimi = 'Kiviaines'),
                    'Alpua',
                    NULL::fillerityyppi,
                    NULL,
                    10,
                    20,
                    85
             FROM massa2
             UNION ALL
             SELECT massa2.id,
                    (SELECT koodi FROM pot2_mk_runkoainetyyppi WHERE nimi = 'Asfalttirouhe'),
                    'Alpua',
                    NULL::fillerityyppi,
                    NULL,
                    10,
                    20,
                    10
             FROM massa2
             UNION ALL
             SELECT massa2.id,
                    (SELECT koodi FROM pot2_mk_runkoainetyyppi WHERE lyhenne = 'Filleri'),
                    NULL,
                    'Kalkkifilleri (KF)'::fillerityyppi,
                    NULL,
                    NULL,
                    NULL,
                    5
             FROM massa2
     ),
     sideaine AS (
         INSERT INTO pot2_mk_massan_sideaine (pot2_massa_id, "lopputuote?", tyyppi, pitoisuus)
             SELECT massa.id,
                    TRUE,
                    (SELECT koodi FROM pot2_mk_sideainetyyppi WHERE nimi = 'Bitumi, 20/30'),
                    5.5
             FROM massa
            union all
             SELECT massa2.id,
                    TRUE,
                    (SELECT koodi FROM pot2_mk_sideainetyyppi WHERE nimi = 'Bitumi, 20/30'),
                    5.5
             FROM massa2),
     lisaaine AS (
         INSERT INTO pot2_mk_massan_lisaaine (pot2_massa_id, tyyppi, pitoisuus)
             SELECT massa.id,
                    (SELECT koodi FROM pot2_mk_lisaainetyyppi WHERE nimi = 'Kuitu'),
                    0.5
             FROM massa
                union all
             SELECT massa2.id,
                    (SELECT koodi FROM pot2_mk_lisaainetyyppi WHERE nimi = 'Kuitu'),
                    0.5
             FROM massa2
                ),
     alusta AS (
         INSERT INTO pot2_alusta (tr_numero, tr_alkuetaisyys, tr_alkuosa, tr_loppuetaisyys, tr_loppuosa, tr_ajorata,
                                  tr_kaista, toimenpide, pot2_id, massamenekki, massa)
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
                    massa.id
             FROM alikohde,
                  paallystysilmoitus,
                  massa
             RETURNING *),
     kulutuskerros_mp AS (
         INSERT INTO pot2_paallystekerros (kohdeosa_id, toimenpide, materiaali, leveys, pinta_ala, kokonaismassamaara,
                                           piennar, pot2_id, massamenekki)
             SELECT alikohde.id,
                    (SELECT koodi FROM pot2_mk_paallystekerros_toimenpide WHERE lyhenne = 'MP'),
                    massa2.id,
                    4,
                    2600,
                    260,
                    FALSE,
                    paallystysilmoitus.id,
                    100
             FROM alikohde,
                  massa2,
                  paallystysilmoitus
             WHERE tr_alkuetaisyys = 0
             RETURNING *),
     kulutuskerros_rem AS (
         INSERT INTO pot2_paallystekerros (kohdeosa_id, toimenpide, materiaali, leveys, pinta_ala, kokonaismassamaara,
                                           piennar, pot2_id, massamenekki)
             SELECT alikohde.id,
                    (SELECT koodi FROM pot2_mk_paallystekerros_toimenpide WHERE lyhenne = 'REM'),
                    massa.id,
                    4,
                    1400,
                    28,
                    FALSE,
                    paallystysilmoitus.id,
                    20
             FROM alikohde,
                  paallystysilmoitus,
                  massa
             WHERE tr_alkuetaisyys = 300
             RETURNING *),
     kulutuskerros_kar AS (
         INSERT INTO pot2_paallystekerros (kohdeosa_id, toimenpide, materiaali, leveys, pinta_ala, kokonaismassamaara,
                                           piennar, pot2_id, massamenekki)
             SELECT alikohde.id,
                    (SELECT koodi FROM pot2_mk_paallystekerros_toimenpide WHERE lyhenne = 'KAR'),
                    NULL,
                    4,
                    1200,
                    120,
                    FALSE,
                    paallystysilmoitus.id,
                    100
             FROM alikohde,
                  paallystysilmoitus
             WHERE tr_alkuetaisyys = 650
             RETURNING *),
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
                    '2023-11-02T12:00:00.000',
                    TRUE,
                    'paallyste',
                    ARRAY [2023],
                    '2023-11-01T12:00:00.000',
                    '2023-11-02T12:00:00.000'
             FROM urakka,
                  sopimus RETURNING id),
     aikataulu2 AS (
         INSERT INTO yllapitokohteen_aikataulu (yllapitokohde, kohde_alku, paallystys_alku, paallystys_loppu,
                                                valmis_tiemerkintaan, tiemerkinta_takaraja, tiemerkinta_alku,
                                                tiemerkinta_loppu, kohde_valmis, muokattu, luotu)
             SELECT yllapitokohde2.id,
                    '2023-11-01',
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
                    '2023-11-01T12:00:00.000'::TIMESTAMP,
                    '2023-11-02T12:00:00.000'::TIMESTAMP,
                    urakka.id,
                    'lahetetty'::lahetyksen_tila,
                    '2023-11-02T12:00:00.000'::TIMESTAMP,
                    '2023-11-01'::DATE,
                    '2023-11-02'::DATE,
                    '2023-11-01'::DATE,
                    (SELECT id FROM paikkauskohde_tyomenetelma WHERE nimi = 'AB-paikkaus levittäjällä'),
                    (86, 20, 700, 20, 800, 1, NULL, NULL, NULL, NULL)::tr_osoite_laajennettu,
                    'valmis'::paikkauskohteen_tila,
                    700,
                    1000,
                    'm2',
                    FALSE,
                    '2023-11-02'::DATE,
                    '2023-11-02'::DATE,
                    1001.10,
                    FALSE,
                    1,
                    NULL,
                    '2023-11-02T12:00:00.000'::TIMESTAMP
             FROM urakka
             UNION ALL
             SELECT 2,
                    'MT 86 Paavolantie 2',
                    FALSE,
                    '2023-11-01T12:00:00.000'::TIMESTAMP,
                    '2023-11-02T12:00:00.000'::TIMESTAMP,
                    urakka.id,
                    'lahetetty'::lahetyksen_tila,
                    '2023-11-02T12:00:00.000'::TIMESTAMP,
                    '2023-11-01'::DATE,
                    '2023-11-02'::DATE,
                    '2023-11-01'::DATE,
                    (SELECT id FROM paikkauskohde_tyomenetelma WHERE nimi = 'SMA-paikkaus levittäjällä'),
                    (86, 20, 0, 20, 100, NULL, NULL, NULL, NULL, NULL)::tr_osoite_laajennettu,
                    'valmis'::paikkauskohteen_tila,
                    100,
                    200,
                    'm2',
                    TRUE,
                    '2023-11-02'::DATE,
                    '2023-11-02'::DATE,
                    201.10,
                    FALSE,
                    1,
                    yllapitokohde2.id,
                    '2023-11-02T12:00:00.000'::TIMESTAMP
             FROM urakka,
                  yllapitokohde2 RETURNING *),
     paallystysilmoitus2 AS (
         INSERT INTO paallystysilmoitus (paallystyskohde, luotu, muokattu, poistettu, takuupvm, paatos_tekninen_osa,
                                         kasittelyaika_tekninen_osa, tila, versio)
             SELECT yllapitokohde2.id,
                    '2023-11-02T12:00:00.000',
                    '2023-11-02T12:00:00.000',
                    FALSE,
                    '2023-11-02',
                    'hyvaksytty',
                    '2023-11-02',
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
                    '2023-11-02T12:00:00.000',
                    '2023-11-01T12:00:00.000'
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
                      paallystysilmoitus2 RETURNING *)
INSERT
INTO paikkaus (luotu, muokattu, "urakka-id", "paikkauskohde-id", "ulkoinen-id", alkuaika, loppuaika,
               tierekisteriosoite, tyomenetelma,
               massatyyppi, leveys, raekoko, kuulamylly, sijainti, massamaara, "pinta-ala", lahde,
               massamenekki)
SELECT '2023-11-01T12:00:00'::DATE,
       '2023-11-02T13:00:00'::TIMESTAMP,
       urakka.id,
       paikkauskohteet.id,
       0,
       '2023-11-02T12:00:00'::TIMESTAMP,
       '2023-11-02T13:00:00'::TIMESTAMP,
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
WHERE paikkauskohteet."pot?" = FALSE;

-- Hoitourakan tekemiä paikkauksia
WITH urakka AS (SELECT id
                FROM urakka
                WHERE nimi = 'Raahen MHU 2023-2028'),
     kulu AS (
         INSERT INTO kulu (tyyppi, kokonaissumma, erapaiva, urakka, luotu, muokattu, poistettu, koontilaskun_kuukausi)
             SELECT 'laskutettava'::laskutyyppi,
                    1000,
                    '2023-11-01',
                    urakka.id,
                    '2023-11-01T12:00:00.000',
                    '2023-11-01T12:00:00.000',
                    FALSE,
                    'lokakuu/1-hoitovuosi'
             FROM urakka RETURNING id)
INSERT
INTO kulu_kohdistus (rivi, kulu, summa, toimenpideinstanssi, tehtavaryhma, maksueratyyppi,
                     suoritus_alku, suoritus_loppu, luotu, muokattu)
SELECT 0,
       kulu.id,
       1000,
       (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Raahen MHU 2023-2028 Päällystepaikkaukset TP'),
       (SELECT id FROM tehtavaryhma WHERE nimi = 'Kuumapäällyste (Y1)'),
       'kokonaishintainen'::maksueratyyppi,
       '2023-11-01T12:00:00.000',
       '2023-11-01T13:00:00.000',
       '2023-11-01T14:00:00.000',
       '2023-11-01T14:00:00.000'
FROM kulu;

WITH urakka AS (SELECT id
                FROM urakka
                WHERE nimi = 'Raahen MHU 2023-2028')
INSERT
INTO toteuma (urakka, sopimus, luotu, alkanut, paattynyt, muokattu, ulkoinen_id, tyyppi,
              tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, lahde)
SELECT 42,
       (SELECT id FROM sopimus WHERE nimi = 'Raahen MHU 23 pääsopimus'),
       '2023-11-01T13:00:00.000'::TIMESTAMP,
       '2023-11-01T12:00:00.000'::TIMESTAMP,
       '2023-11-01T13:00:00.000'::TIMESTAMP,
       '2023-11-01T13:00:00.000'::TIMESTAMP,
       NULL,
       'kokonaishintainen'::toteumatyyppi,
       86,
       20,
       200,
       20,
       300,
       'harja-ui'
FROM urakka;

WITH urakka AS (SELECT id
                FROM urakka
                WHERE nimi = 'Raahen MHU 2023-2028')
INSERT
INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, muokattu, indeksi, urakka_id)
SELECT (SELECT id FROM toteuma WHERE urakka = 42 AND luotu = '2023-11-01T13:00:00.000'::TIMESTAMP),
       '2023-11-01T13:00:00.000',
       (SELECT id FROM tehtava WHERE nimi = 'Kuumapäällyste'),
       10,
       '2023-11-01T13:00:00.000',
       TRUE,
       urakka.id
FROM urakka;
