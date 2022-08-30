-- generoitu ajamalla import 2021-12-17

-- Jos tarvitsee manuaalisesti testata Velho hakua, niin tässä on joitain päivämääriä
-- joilla nyt tuli kohtuullinen määrä tuloksia.

INSERT INTO varustetoteuma_ulkoiset_viimeisin_hakuaika_kohdeluokalle
(kohdeluokka, viimeisin_hakuaika)
VALUES ('varusteet/kaiteet', DATE('2021-06-19')), -- 29 kpl
       ('varusteet/tienvarsikalusteet', DATE('2021-08-18')), -- 8 kpl
       ('varusteet/liikennemerkit', DATE('2021-06-19')), -- 3 kpl
       ('varusteet/rumpuputket', DATE('2021-06-07')), -- 39 kpl
       ('varusteet/kaivot', DATE('2021-05-28')), -- 375 kpl
       ('varusteet/reunapaalut', DATE('2021-05-15')), -- 2 kpl
       ('tiealueen-poikkileikkaus/luiskat', DATE('2021-06-18')), -- 54 (tl514) + 2 (518) *
       ('varusteet/aidat', DATE('2021-05-31')), -- 1 kpl
       ('varusteet/portaat', DATE('2021-05-31')), -- 1 kpl
       ('tiealueen-poikkileikkaus/erotusalueet', DATE('2021-06-29')), -- 2 (TL518) + 91 (TL165)
       ('varusteet/puomit-sulkulaitteet-pollarit', DATE('2021-06-17')), -- 2 kpl
       ('varusteet/reunatuet', DATE('2021-06-07')), -- 28 kpl
       ('ymparisto/viherkuviot', DATE('2022-07-02')); -- **

-- * Velhon migraatiostatus TL514 (ja TL518) on 2021-11-30 vielä kesken.
-- ** ei löytynyt pienempää joukkoa antavaa jalkeen rajausta

DO
$$
    DECLARE
        oulun_mhu_2019_2024_id INT;
        kemin_mhu_testiurakka INT;
    BEGIN
        oulun_mhu_2019_2024_id = (SELECT id FROM urakka WHERE nimi = 'Oulun MHU 2019-2024');
        kemin_mhu_testiurakka = (SELECT id FROM urakka WHERE nimi = 'Kemin MHU testiurakka (5. hoitovuosi)');

        INSERT INTO varustetoteuma_ulkoiset (ulkoinen_oid, urakka_id, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, sijainti,
                                             tietolaji, lisatieto, toteuma, kuntoluokka, alkupvm, loppupvm, muokkaaja, muokattu)
        VALUES ('1.2.246.578.4.3.12.512.310173990', oulun_mhu_2019_2024_id, 4, 421, 1921, NULL, NULL, '010100000095E5B9B848961841959807FB8DCA5B41', 'tl512', 'kansi: 600 kaivo: 1000 syvyys: 1000 materiaali: betoni', 'lisatty', 'Erittäin hyvä', '2019-10-01', NULL, 'migraatio', '2021-05-28 14:01:33.000000'),
               ('1.2.246.578.4.3.12.512.310173991', oulun_mhu_2019_2024_id, 4, 420, 5758, NULL, NULL, '0101000000993C09E30BBA1841DEF5E891CFC95B41', 'tl512', NULL, 'lisatty', 'Erittäin huono', '2020-06-10', NULL, 'migraatio', '2021-05-28 14:01:33.000000'),
               ('1.2.246.578.4.3.12.512.310173992', oulun_mhu_2019_2024_id, 4, 421, 1900, NULL, NULL, '0101000000105EC7B59C9618411F3BC7E48DCA5B41', 'tl512', NULL, 'lisatty', 'Puuttuu', '2020-04-25', NULL, 'migraatio', '2021-05-28 14:01:33.000000'),
               ('1.2.246.578.4.3.12.512.310173993', oulun_mhu_2019_2024_id, 4, 421, 1904, NULL, NULL, '01010000005B1657B68C961841BB4004E98DCA5B41', 'tl512', NULL, 'korjaus', 'Tyydyttävä', '2020-09-30', NULL, 'migraatio', '2021-05-28 14:01:33.000000'),
               ('1.2.246.578.4.3.12.512.310173994', oulun_mhu_2019_2024_id, 4, 421, 1907, NULL, NULL, '010100000092E0C2B680961841F10432EC8DCA5B41', 'tl512', NULL, 'paivitetty', 'Puuttuu', '2020-01-31', NULL, 'migraatio', '2021-05-28 14:01:33.000000'),
               ('1.2.246.578.4.3.12.512.310173995', oulun_mhu_2019_2024_id, 4, 422, 637, NULL, NULL, '01010000008C1209F0BE7F184112FC033A98CA5B41', 'tl512', NULL, 'lisatty', 'Hyvä', '2019-11-30', NULL, 'migraatio', '2021-05-28 14:01:33.000000'),
               ('1.2.246.578.4.3.12.512.310173996', oulun_mhu_2019_2024_id, 4, 422, 641, NULL, NULL, '0101000000EF866A21AF7F18418F1AA16198CA5B41', 'tl512', NULL, 'lisatty', 'Puuttuu', '2020-09-30', NULL, 'migraatio', '2021-05-28 14:01:33.000000'),
               ('1.2.246.578.4.3.12.512.310173997', oulun_mhu_2019_2024_id, 4, 422, 648, NULL, NULL, '01010000002959407E937F18411F7458A998CA5B41', 'tl506', NULL, 'tarkastus', 'Erittäin hyvä', '2020-09-30', NULL, 'migraatio', '2021-05-28 14:01:33.000000'),
               ('1.2.246.578.4.3.12.512.310173998', oulun_mhu_2019_2024_id, 4, 422, 656, NULL, NULL, '010100000062040EF6737F18413FAD960099CA5B41', 'tl512', NULL, 'lisatty', 'Puuttuu', '2020-09-30', '2020-10-14', 'migraatio', '2021-05-28 14:01:33.000000'),
               ('1.2.246.578.4.3.12.512.310173998', oulun_mhu_2019_2024_id, 4, 422, 656, NULL, NULL, '010100000062040EF6737F18413FAD960099CA5B41', 'tl512', NULL, 'paivitetty', 'Hyvä', '2020-10-15', '2020-10-24', 'migraatio', '2021-05-28 14:01:33.000000'),
               ('1.2.246.578.4.3.12.512.310173998', oulun_mhu_2019_2024_id, 4, 422, 656, NULL, NULL, '010100000062040EF6737F18413FAD960099CA5B41', 'tl512', NULL, 'puhdistus', 'Erittäin huono', '2020-10-25', NULL, 'migraatio', '2021-05-28 14:01:33.000000'),
               ('1.2.246.578.4.3.12.512.310173999', kemin_mhu_testiurakka, 4, 422, 663, NULL, NULL, '01010000003990E35E587F1841FAB1ED4C99CA5B41', 'tl512', NULL, 'lisatty', 'Puuttuu', '2023-09-30', NULL, 'migraatio', '2021-05-28 14:01:33.000000'),
               ('1.2.246.578.4.3.12.512.310174000', kemin_mhu_testiurakka, 4, 422, 685, NULL, NULL, '010100000036A36DB7017F1841B6EB36429ACA5B41', 'tl512', NULL, 'lisatty', 'Puuttuu', '2022-10-01', NULL, 'migraatio', '2021-05-28 14:01:33.000000'),
               ('1.2.246.578.4.3.12.512.310174001', kemin_mhu_testiurakka, 4, 422, 2283, NULL, NULL, '01010000009D8153FE6F661841E316C055E0CA5B41', 'tl512', NULL, 'lisatty', 'Puuttuu', '2024-10-01', NULL, 'migraatio', '2021-05-28 14:01:33.000000'),
               ('1.2.246.578.4.3.12.512.310174002', kemin_mhu_testiurakka, 4, 422, 2317, NULL, NULL, '0101000000ED563147E86518410FCA46E2E0CA5B41', 'tl512', NULL, 'lisatty', 'Puuttuu', '2025-09-30', NULL, 'migraatio', '2021-05-28 14:01:33.000000'),
               ('1.2.246.578.4.3.12.512.310174003', kemin_mhu_testiurakka, 4, 422, 3846, NULL, NULL, '01010000000173C7483E4E184175AAD9A9B5CA5B41', 'tl512', NULL, 'lisatty', 'Puuttuu', '2026-09-30', NULL, 'migraatio', '2021-05-28 14:01:33.000000'),
               ('1.2.246.578.4.3.12.512.310174004', kemin_mhu_testiurakka, 4, 422, 3898, NULL, NULL, '0101000000C01F567B6E4D184168963E1BB5CA5B41', 'tl512', NULL, 'poistettu', 'Puuttuu', '2020-09-30', '2021-06-01', 'migraatio', '2021-05-28 14:01:33.000000'),
               ('1.2.246.578.4.3.11.503.295749434', kemin_mhu_testiurakka, 9, 206, 10352, null, null, '0101000000DBEAFC068AEB14418D174FFEEA0A5A41', 'tl503', null, 'lisatty', 'Tyydyttävä', '2020-05-21', null, 'migraatio', '2022-04-01 19:12:05.000000'),
               ('1.2.246.578.4.3.11.503.295749435', kemin_mhu_testiurakka, 9, 206, 10352, null, null, '0101000000DBEAFC068AEB14418D174FFEEA0A5A41', 'tl503', null, 'lisatty', 'Tyydyttävä', '2020-05-21', null, 'migraatio', '2022-04-01 19:12:05.000000'),
               ('1.2.246.578.4.3.11.503.295751776', kemin_mhu_testiurakka, 9, 206, 10352, null, null, '0101000000DBEAFC068AEB14418D174FFEEA0A5A41', 'tl503', null, 'lisatty', 'Hyvä', '2020-05-21', null, 'migraatio', '2022-04-01 19:12:05.000000'),
               ('1.2.246.578.4.3.11.503.295752863', kemin_mhu_testiurakka, 9, 208, 8690, null, null, '01010000003C394EE9E53E15413E08D23033115A41', 'tl503', null, 'lisatty', 'Puuttuu', '2020-05-21', null, 'migraatio', '2022-04-01 19:12:05.000000'),
               ('1.2.246.578.4.3.11.503.295752875', kemin_mhu_testiurakka, 9, 208, 10290, null, null, '01010000002CB9AF28144D154121CD912A7C125A41', 'tl503', null, 'lisatty', 'Puuttuu', '2020-05-21', null, 'migraatio', '2022-04-01 19:12:05.000000'),
               ('1.2.246.578.4.3.11.503.295753976', kemin_mhu_testiurakka, 9, 212, 4264, null, null, '01010000000D5B9EB56D901641AE83E999D41B5A41', 'tl503', null, 'lisatty', 'Puuttuu', '2020-05-21', null, 'migraatio', '2022-04-01 19:12:05.000000'),
               ('1.2.246.578.4.3.11.503.295753978', kemin_mhu_testiurakka, 9, 212, 4565, null, null, '010100000068D4D797629416417CA65CF2AB1B5A41', 'tl503', null, 'lisatty', 'Puuttuu', '2020-05-21', null, 'migraatio', '2022-04-01 19:12:05.000000'),
               ('1.2.246.578.4.3.11.503.296385570', kemin_mhu_testiurakka, 377, 3, 689, null, null, '0101000000F40004A541591F41798C7FC8A5D65941', 'tl503', null, 'lisatty', 'Tyydyttävä', '2020-07-16', null, 'migraatio', '2022-04-01 19:12:05.000000');
    END
$$;

