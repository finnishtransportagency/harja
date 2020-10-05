INSERT INTO pot2_massa (urakka_id, massatyyppi, nimi, max_raekoko, kuulamyllyluokka, litteyslukuluokka, DoP_nro, luoja, luotu)
VALUES ((SELECT id from urakka WHERE nimi = 'Utajärven päällystysurakka'), 'AB-16', 'Asfalttibetoni', 16,
        'AN14', 1, '1234567',
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'skanska'), now()),
       ((SELECT id from urakka WHERE nimi = 'Utajärven päällystysurakka'), 'SMA 16', 'Kivimastiksiasfaltti', 16,
        'AN7', 2, '987654331-2',
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'skanska'), now());

INSERT INTO pot2_massa_runkoaine(pot2_massa_id, tyyppi, esiintyma, fillerityyppi,
                                 kuvaus, kuulamyllyarvo, litteysluku, massaprosentti)
VALUES ((SELECT id FROM pot2_massa WHERE nimi = 'Asfalttibetoni' AND urakka_id = (SELECT id from urakka WHERE nimi = 'Utajärven päällystysurakka')),
        (SELECT koodi FROM pot2_runkoainetyyppi WHERE nimi = 'Kiviaines'),
        'Kaiskakallio',  NULL, 'Kelpo runkoaine tämä.', 10.0, 9.5, 52.1),
       ((SELECT id FROM pot2_massa WHERE nimi = 'Kivimastiksiasfaltti' AND urakka_id = (SELECT id from urakka WHERE nimi = 'Utajärven päällystysurakka')),
       (SELECT koodi FROM pot2_runkoainetyyppi WHERE nimi = 'Kiviaines'),
    'Sammalkallio',  NULL, 'Jämäkkä runkoaine.', 9.2, 6.5, 85.0),
       ((SELECT id FROM pot2_massa WHERE nimi = 'Kivimastiksiasfaltti' AND urakka_id = (SELECT id from urakka WHERE nimi = 'Utajärven päällystysurakka')),
        (SELECT koodi FROM pot2_runkoainetyyppi WHERE nimi = 'Erikseen lisättävä fillerikiviaines'),
        'Sammalkallio',  'Kalkkifilleri (KF)', 'Oiva Filleri.', 8.2, 6.5, 3.0),
       ((SELECT id FROM pot2_massa WHERE nimi = 'Kivimastiksiasfaltti' AND urakka_id = (SELECT id from urakka WHERE nimi = 'Utajärven päällystysurakka')),
        (SELECT koodi FROM pot2_runkoainetyyppi WHERE nimi = 'Asfalttirouhe'),
        'Sammalkallio',  NULL, 'Oiva Filleri.', 11.2, 4.5, 5.0);

INSERT INTO pot2_massa_sideaine(pot2_massa_id, "lopputuote?", tyyppi, pitoisuus)
VALUES ((SELECT id FROM pot2_massa WHERE nimi = 'Asfalttibetoni' AND urakka_id = (SELECT id from urakka WHERE nimi = 'Utajärven päällystysurakka')),
        TRUE, (SELECT koodi FROM pot2_sideainetyyppi where nimi = 'Bitumi, 160/220'), 4.8),
       ((SELECT id FROM pot2_massa WHERE nimi = 'Kivimastiksiasfaltti' AND urakka_id = (SELECT id from urakka WHERE nimi = 'Utajärven päällystysurakka')),
        TRUE, (SELECT koodi FROM pot2_sideainetyyppi where nimi = 'Bitumi, 100/150'), 5.5);

INSERT INTO pot2_massa_lisaaine(pot2_massa_id, tyyppi, pitoisuus)
VALUES ((SELECT id FROM pot2_massa WHERE nimi = 'Asfalttibetoni' AND urakka_id = (SELECT id from urakka WHERE nimi = 'Utajärven päällystysurakka')),
        (SELECT koodi FROM pot2_lisaainetyyppi where nimi = 'Tartuke'), 0.5),
       ((SELECT id FROM pot2_massa WHERE nimi = 'Kivimastiksiasfaltti' AND urakka_id = (SELECT id from urakka WHERE nimi = 'Utajärven päällystysurakka')),
        (SELECT koodi FROM pot2_lisaainetyyppi where nimi = 'Kuitu'), 0.5);