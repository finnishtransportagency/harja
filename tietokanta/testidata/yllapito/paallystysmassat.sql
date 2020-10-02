INSERT INTO pot2_massa (urakka_id, massatyyppi, nimi, max_raekoko, kuulamyllyluokka, litteyslukuluokka, DoP_nro, luoja, luotu)
VALUES ((SELECT id from urakka WHERE nimi = 'Utajärven päällystysurakka'), 'AB-16', 'Asfalttibetoni', 16,
        'AN14', 1, 20,
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'skanska'), now()),
       ((SELECT id from urakka WHERE nimi = 'Utajärven päällystysurakka'), 'SMA 16', 'Kivimastiksiasfaltti', 16,
        'AN7', 2, 10,
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'skanska'), now());
