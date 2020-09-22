INSERT INTO pot2_massa (urakka_id, massatyyppi, nimi, max_raekoko, asfalttiasema, kuulamyllyluokka, litteyslukuluokka, DoP_nro, luoja, luotu)
VALUES ((SELECT id from urakka WHERE nimi = 'Utajärven päällystysurakka'), 'AB-16', 'Asfalttibetoni', 16,
        'Jokimaan asema', 'AN14', 1, 20,
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'skanska'), now()),
       ((SELECT id from urakka WHERE nimi = 'Utajärven päällystysurakka'), 'SMA 16', 'Kivimastiksiasfaltti', 16,
        'Karvamaan asema', 'AN7', 2, 10,
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'skanska'), now());
