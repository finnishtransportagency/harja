-- Urakan massoja
DO $$
DECLARE
    urakan_id INTEGER;
    kayttaja_id INTEGER;


BEGIN
    urakan_id = (SELECT id FROM urakka where nimi = 'Utajärven päällystysurakka');
    kayttaja_id = (SELECT id FROM kayttaja WHERE kayttajanimi = 'skanska');

INSERT INTO pot2_mk_urakan_massa (urakka_id, tyyppi, max_raekoko, kuulamyllyluokka, litteyslukuluokka, DoP_nro, luoja, luotu)
VALUES (urakan_id,
        (SELECT koodi from pot2_mk_massatyyppi where nimi = 'AB, Asfalttibetoni'), 16,
        'AN14', 1, '1234567',
        kayttaja_id, now()),
       (urakan_id,
        (SELECT koodi from pot2_mk_massatyyppi where nimi = 'SMA, Kivimastiksiasfaltti'), 16,
        'AN7', 2, '987654331-2',
        kayttaja_id, now());

INSERT INTO pot2_mk_massan_runkoaine(pot2_massa_id, tyyppi, esiintyma, fillerityyppi,
                                 kuvaus, kuulamyllyarvo, litteysluku, massaprosentti)
VALUES ((SELECT id FROM pot2_mk_urakan_massa WHERE dop_nro = '1234567' AND urakan_id = urakan_id),
        (SELECT koodi FROM pot2_mk_runkoainetyyppi WHERE nimi = 'Kiviaines'),
        'Kaiskakallio',  NULL, 'Kelpo runkoaine tämä.', 10.0, 9.5, 52),
       ((SELECT id FROM pot2_mk_urakan_massa WHERE dop_nro = '987654331-2' AND urakan_id = urakan_id),
       (SELECT koodi FROM pot2_mk_runkoainetyyppi WHERE nimi = 'Kiviaines'),
    'Sammalkallio',  NULL, 'Jämäkkä runkoaine.', 9.2, 6.5, 85),
       ((SELECT id FROM pot2_mk_urakan_massa WHERE dop_nro = '987654331-2' AND urakan_id = urakan_id),
        (SELECT koodi FROM pot2_mk_runkoainetyyppi WHERE nimi = 'Erikseen lisättävä fillerikiviaines'),
        'Sammalkallio',  'Kalkkifilleri (KF)', 'Oiva Filleri.', null, null, 3),
       ((SELECT id FROM pot2_mk_urakan_massa WHERE dop_nro = '987654331-2' AND urakan_id = urakan_id),
        (SELECT koodi FROM pot2_mk_runkoainetyyppi WHERE nimi = 'Asfalttirouhe'),
        'Sammalkallio',  NULL, 'Rouhea aine.', 11.2, 4.5, 5);

INSERT INTO pot2_mk_massan_sideaine(pot2_massa_id, "lopputuote?", tyyppi, pitoisuus)
VALUES ((SELECT id FROM pot2_mk_urakan_massa WHERE dop_nro = '1234567' AND urakan_id = urakan_id),
        TRUE, (SELECT koodi FROM pot2_mk_sideainetyyppi where nimi = 'Bitumi, 160/220'), 4.8),
       ((SELECT id FROM pot2_mk_urakan_massa WHERE dop_nro = '987654331-2' AND urakan_id = urakan_id),
        TRUE, (SELECT koodi FROM pot2_mk_sideainetyyppi where nimi = 'Bitumi, 100/150'), 5.5);

INSERT INTO pot2_mk_massan_lisaaine(pot2_massa_id, tyyppi, pitoisuus)
VALUES ((SELECT id FROM pot2_mk_urakan_massa WHERE dop_nro = '1234567' AND urakan_id = urakan_id),
        (SELECT koodi FROM pot2_mk_lisaainetyyppi where nimi = 'Tartuke'), 0.5),
       ((SELECT id FROM pot2_mk_urakan_massa WHERE dop_nro = '987654331-2' AND urakan_id = urakan_id),
        (SELECT koodi FROM pot2_mk_lisaainetyyppi where nimi = 'Kuitu'), 0.5);

-- Urakan murskeita
INSERT INTO pot2_mk_urakan_murske(urakka_id, nimen_tarkenne, tyyppi, esiintyma,
                                  rakeisuus, iskunkestavyys, dop_nro, luoja, luotu)
VALUES (urakan_id, 'LJYR', (SELECT koodi FROM pot2_mk_mursketyyppi WHERE nimi = 'Kalliomurske'), 'Kankkulan Kaivo', '0/40', 'LA30', '1234567-dop', kayttaja_id, NOW());

END;
$$ LANGUAGE plpgsql;