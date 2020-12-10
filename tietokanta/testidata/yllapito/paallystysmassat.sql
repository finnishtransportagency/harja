INSERT INTO pot2_mk_urakan_massa (urakka_id, tyyppi, max_raekoko, kuulamyllyluokka, litteyslukuluokka, DoP_nro, luoja, luotu)
VALUES ((SELECT id from urakka WHERE nimi = 'Utajärven päällystysurakka'),
        (SELECT koodi from pot2_mk_massatyyppi where nimi = 'AB, Asfalttibetoni'), 16,
        'AN14', 1, '1234567',
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'skanska'), now()),
       ((SELECT id from urakka WHERE nimi = 'Utajärven päällystysurakka'),
        (SELECT koodi from pot2_mk_massatyyppi where nimi = 'SMA, Kivimastiksiasfaltti'), 16,
        'AN7', 2, '987654331-2',
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'skanska'), now());

INSERT INTO pot2_mk_massan_runkoaine(pot2_massa_id, tyyppi, esiintyma, fillerityyppi,
                                 kuvaus, kuulamyllyarvo, litteysluku, massaprosentti)
VALUES ((SELECT id FROM pot2_mk_urakan_massa WHERE dop_nro = '1234567' AND urakka_id = (SELECT id from urakka WHERE nimi = 'Utajärven päällystysurakka')),
        (SELECT koodi FROM pot2_mk_runkoainetyyppi WHERE nimi = 'Kiviaines'),
        'Kaiskakallio',  NULL, 'Kelpo runkoaine tämä.', 10.0, 9.5, 52),
       ((SELECT id FROM pot2_mk_urakan_massa WHERE dop_nro = '987654331-2' AND urakka_id = (SELECT id from urakka WHERE nimi = 'Utajärven päällystysurakka')),
       (SELECT koodi FROM pot2_mk_runkoainetyyppi WHERE nimi = 'Kiviaines'),
    'Sammalkallio',  NULL, 'Jämäkkä runkoaine.', 9.2, 6.5, 85),
       ((SELECT id FROM pot2_mk_urakan_massa WHERE dop_nro = '987654331-2' AND urakka_id = (SELECT id from urakka WHERE nimi = 'Utajärven päällystysurakka')),
        (SELECT koodi FROM pot2_mk_runkoainetyyppi WHERE nimi = 'Erikseen lisättävä fillerikiviaines'),
        'Sammalkallio',  'Kalkkifilleri (KF)', 'Oiva Filleri.', null, null, 3),
       ((SELECT id FROM pot2_mk_urakan_massa WHERE dop_nro = '987654331-2' AND urakka_id = (SELECT id from urakka WHERE nimi = 'Utajärven päällystysurakka')),
        (SELECT koodi FROM pot2_mk_runkoainetyyppi WHERE nimi = 'Asfalttirouhe'),
        'Sammalkallio',  NULL, 'Rouhea aine.', 11.2, 4.5, 5);

INSERT INTO pot2_mk_massan_sideaine(pot2_massa_id, "lopputuote?", tyyppi, pitoisuus)
VALUES ((SELECT id FROM pot2_mk_urakan_massa WHERE dop_nro = '1234567' AND urakka_id = (SELECT id from urakka WHERE nimi = 'Utajärven päällystysurakka')),
        TRUE, (SELECT koodi FROM pot2_mk_sideainetyyppi where nimi = 'Bitumi, 160/220'), 4.8),
       ((SELECT id FROM pot2_mk_urakan_massa WHERE dop_nro = '987654331-2' AND urakka_id = (SELECT id from urakka WHERE nimi = 'Utajärven päällystysurakka')),
        TRUE, (SELECT koodi FROM pot2_mk_sideainetyyppi where nimi = 'Bitumi, 100/150'), 5.5);

INSERT INTO pot2_mk_massan_lisaaine(pot2_massa_id, tyyppi, pitoisuus)
VALUES ((SELECT id FROM pot2_mk_urakan_massa WHERE dop_nro = '1234567' AND urakka_id = (SELECT id from urakka WHERE nimi = 'Utajärven päällystysurakka')),
        (SELECT koodi FROM pot2_mk_lisaainetyyppi where nimi = 'Tartuke'), 0.5),
       ((SELECT id FROM pot2_mk_urakan_massa WHERE dop_nro = '987654331-2' AND urakka_id = (SELECT id from urakka WHERE nimi = 'Utajärven päällystysurakka')),
        (SELECT koodi FROM pot2_mk_lisaainetyyppi where nimi = 'Kuitu'), 0.5);