-- Urakan massoja
DO $$
DECLARE
    utajarven_urakan_id INTEGER;
    muhoksen_urakan_id INTEGER;
    oulu_paallystysurakka_id INTEGER;
    kayttaja_id INTEGER;


BEGIN
    utajarven_urakan_id = (SELECT id FROM urakka where nimi = 'Utajärven päällystysurakka');
    muhoksen_urakan_id = (SELECT id FROM urakka where nimi = 'Muhoksen päällystysurakka');
    oulu_paallystysurakka_id = (SELECT id FROM urakka where nimi = 'Aktiivinen Oulu Päällystys Testi');
    kayttaja_id = (SELECT id FROM kayttaja WHERE kayttajanimi = 'skanska');

INSERT INTO pot2_mk_urakan_massa (urakka_id, tyyppi, max_raekoko, kuulamyllyluokka, litteyslukuluokka, DoP_nro, luoja, luotu)
VALUES (utajarven_urakan_id,
        (SELECT koodi from pot2_mk_massatyyppi where nimi = 'AB, Asfalttibetoni'), 16,
        'AN14', 'FI15', '1234567',
        kayttaja_id, now()),
       (utajarven_urakan_id,
        (SELECT koodi from pot2_mk_massatyyppi where nimi = 'SMA, Kivimastiksiasfaltti'), 16,
        'AN7', 'FI20', '987654331-2',
        kayttaja_id, now()),
       (muhoksen_urakan_id,
        (SELECT koodi from pot2_mk_massatyyppi where nimi = 'PAB-B, Pehmeät asfalttibetonit'), 11,
        'AN14', 'FI15', '764567-dop',
        kayttaja_id, now()),
       (oulu_paallystysurakka_id,
        (SELECT koodi from pot2_mk_massatyyppi where nimi = 'AB, Asfalttibetoni'), 16,
        'AN14', 'FI15', '34567',
        kayttaja_id, now());

INSERT INTO pot2_mk_massan_runkoaine(pot2_massa_id, tyyppi, esiintyma, fillerityyppi,
                                 kuvaus, kuulamyllyarvo, litteysluku, massaprosentti)
VALUES ((SELECT id FROM pot2_mk_urakan_massa WHERE dop_nro = '1234567' AND urakka_id = utajarven_urakan_id),
        (SELECT koodi FROM pot2_mk_runkoainetyyppi WHERE nimi = 'Kiviaines'),
        'Kaiskakallio',  NULL, 'Kelpo runkoaine tämä.', 10.0, 9.5, 52),
       ((SELECT id FROM pot2_mk_urakan_massa WHERE dop_nro = '987654331-2' AND urakka_id = utajarven_urakan_id),
       (SELECT koodi FROM pot2_mk_runkoainetyyppi WHERE nimi = 'Kiviaines'),
    'Sammalkallio',  NULL, 'Jämäkkä runkoaine.', 9.2, 6.5, 85),
       ((SELECT id FROM pot2_mk_urakan_massa WHERE dop_nro = '987654331-2' AND urakka_id = utajarven_urakan_id),
        (SELECT koodi FROM pot2_mk_runkoainetyyppi WHERE nimi = 'Erikseen lisättävä fillerikiviaines'),
        'Sammalkallio',  'Kalkkifilleri (KF)', 'Oiva Filleri.', null, null, 3),
       ((SELECT id FROM pot2_mk_urakan_massa WHERE dop_nro = '987654331-2' AND urakka_id = utajarven_urakan_id),
        (SELECT koodi FROM pot2_mk_runkoainetyyppi WHERE nimi = 'Asfalttirouhe'),
        'Sammalkallio',  NULL, 'Rouhea aine.', 11.2, 4.5, 5),
       ((SELECT id FROM pot2_mk_urakan_massa WHERE dop_nro = '764567-dop' AND urakka_id = muhoksen_urakan_id),
        (SELECT koodi FROM pot2_mk_runkoainetyyppi WHERE nimi = 'Kiviaines'),
        'Suolakallio',  NULL, 'Kelpo runkoaine tämä.', 12.0, 8.5, 55),
       ((SELECT id FROM pot2_mk_urakan_massa WHERE dop_nro = '34567' AND urakka_id = oulu_paallystysurakka_id),
        (SELECT koodi FROM pot2_mk_runkoainetyyppi WHERE nimi = 'Kiviaines'),
        'Kolokallio',  NULL, 'Kelpo runkoaine tämäkin.', 12.0, 8.5, 55);

INSERT INTO pot2_mk_massan_sideaine(pot2_massa_id, "lopputuote?", tyyppi, pitoisuus)
VALUES ((SELECT id FROM pot2_mk_urakan_massa WHERE dop_nro = '1234567' AND urakka_id = utajarven_urakan_id),
        TRUE, (SELECT koodi FROM pot2_mk_sideainetyyppi where nimi = 'Bitumi, 160/220'), 4.8),
       ((SELECT id FROM pot2_mk_urakan_massa WHERE dop_nro = '987654331-2' AND urakka_id = utajarven_urakan_id),
        TRUE, (SELECT koodi FROM pot2_mk_sideainetyyppi where nimi = 'Bitumi, 100/150'), 5.5),
       ((SELECT id FROM pot2_mk_urakan_massa WHERE dop_nro = '764567-dop' AND urakka_id = muhoksen_urakan_id),
        TRUE, (SELECT koodi FROM pot2_mk_sideainetyyppi where nimi = 'Bitumi, 330/430'), 2.8),
       ((SELECT id FROM pot2_mk_urakan_massa WHERE dop_nro = '34567' AND urakka_id = oulu_paallystysurakka_id),
        TRUE, (SELECT koodi FROM pot2_mk_sideainetyyppi where nimi = 'Bitumi, 160/220'), 3.8);

INSERT INTO pot2_mk_massan_lisaaine(pot2_massa_id, tyyppi, pitoisuus)
VALUES ((SELECT id FROM pot2_mk_urakan_massa WHERE dop_nro = '1234567' AND urakka_id = utajarven_urakan_id),
        (SELECT koodi FROM pot2_mk_lisaainetyyppi where nimi = 'Tartuke'), 0.5),
       ((SELECT id FROM pot2_mk_urakan_massa WHERE dop_nro = '987654331-2' AND urakka_id = utajarven_urakan_id),
        (SELECT koodi FROM pot2_mk_lisaainetyyppi where nimi = 'Kuitu'), 0.5),
       ((SELECT id FROM pot2_mk_urakan_massa WHERE dop_nro = '764567-dop' AND urakka_id = muhoksen_urakan_id),
        (SELECT koodi FROM pot2_mk_lisaainetyyppi where nimi = 'Tartuke'), 0.3),
       ((SELECT id FROM pot2_mk_urakan_massa WHERE dop_nro = '34567' AND urakka_id = oulu_paallystysurakka_id),
        (SELECT koodi FROM pot2_mk_lisaainetyyppi where nimi = 'Tartuke'), 0.2);

-- Urakan murskeita
INSERT INTO pot2_mk_urakan_murske(urakka_id, nimen_tarkenne, tyyppi, esiintyma,
                                  rakeisuus, iskunkestavyys, dop_nro, luoja, luotu)
VALUES (utajarven_urakan_id, 'LJYR', (SELECT koodi FROM pot2_mk_mursketyyppi WHERE nimi = 'Kalliomurske'), 'Kankkulan Kaivo', '0/40', 'LA30', '1234567-dop', kayttaja_id, NOW()),
       (muhoksen_urakan_id, 'Brenkku', (SELECT koodi FROM pot2_mk_mursketyyppi WHERE nimi = 'Kalliomurske'), 'Brenkkulan Kaivo', '0/40', 'LA30', '3524534-dop', kayttaja_id, NOW()),
       (muhoksen_urakan_id, 'Bemi 1', (SELECT koodi FROM pot2_mk_mursketyyppi WHERE nimi = '(UUSIO) Betonimurske I'), 'Brenkkulan Kaivo', '0/45', 'LA40', 'ETR-444', kayttaja_id, NOW());

END;
$$ LANGUAGE plpgsql;