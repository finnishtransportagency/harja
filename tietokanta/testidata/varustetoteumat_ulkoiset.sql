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

INSERT INTO varustetoteuma_ulkoiset (id, ulkoinen_oid, urakka_id, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, sijainti, tietolaji, lisatieto, toteuma, kuntoluokka, alkupvm, loppupvm, muokkaaja, muokattu)
VALUES  (1, '1.2.246.578.4.3.12.512.310173990', 35, 4, 421, 1921, NULL, NULL, '010100000095E5B9B848961841959807FB8DCA5B41', 'tl512', 'kansi: 600 kaivo: 1000 syvyys: 1000 materiaali: betoni', ARRAY['lisatty'::varustetoteuma_tyyppi], 'Erittäin hyvä', '2019-10-01', NULL, 'migraatio', '2021-05-28 14:01:33.000000'),
        (2, '1.2.246.578.4.3.12.512.310173991', 35, 4, 420, 5758, NULL, NULL, '0101000000993C09E30BBA1841DEF5E891CFC95B41', 'tl512', NULL, ARRAY['lisatty'::varustetoteuma_tyyppi], 'Erittäin huono', '2020-06-10', NULL, 'migraatio', '2021-05-28 14:01:33.000000'),
        (3, '1.2.246.578.4.3.12.512.310173992', 35, 4, 421, 1900, NULL, NULL, '0101000000105EC7B59C9618411F3BC7E48DCA5B41', 'tl512', NULL, ARRAY['lisatty'::varustetoteuma_tyyppi], 'Puuttuu', '2020-04-25', NULL, 'migraatio', '2021-05-28 14:01:33.000000'),
        (4, '1.2.246.578.4.3.12.512.310173993', 35, 4, 421, 1904, NULL, NULL, '01010000005B1657B68C961841BB4004E98DCA5B41', 'tl512', NULL, ARRAY['lisatty'::varustetoteuma_tyyppi], 'Tyydyttävä', '2020-09-30', NULL, 'migraatio', '2021-05-28 14:01:33.000000'),
        (5, '1.2.246.578.4.3.12.512.310173994', 35, 4, 421, 1907, NULL, NULL, '010100000092E0C2B680961841F10432EC8DCA5B41', 'tl512', NULL, ARRAY['paivitetty'::varustetoteuma_tyyppi], 'Puuttuu', '2020-01-31', NULL, 'migraatio', '2021-05-28 14:01:33.000000'),
        (6, '1.2.246.578.4.3.12.512.310173995', 35, 4, 422, 637, NULL, NULL, '01010000008C1209F0BE7F184112FC033A98CA5B41', 'tl512', NULL, ARRAY['lisatty'::varustetoteuma_tyyppi], 'Hyvä', '2019-11-30', NULL, 'migraatio', '2021-05-28 14:01:33.000000'),
        (7, '1.2.246.578.4.3.12.512.310173996', 35, 4, 422, 641, NULL, NULL, '0101000000EF866A21AF7F18418F1AA16198CA5B41', 'tl512', NULL, ARRAY['lisatty'::varustetoteuma_tyyppi], 'Puuttuu', '2020-09-30', NULL, 'migraatio', '2021-05-28 14:01:33.000000'),
        (8, '1.2.246.578.4.3.12.512.310173997', 35, 4, 422, 648, NULL, NULL, '01010000002959407E937F18411F7458A998CA5B41', 'tl506', NULL, ARRAY['paivitetty'::varustetoteuma_tyyppi], 'Erittäin hyvä', '2020-09-30', NULL, 'migraatio', '2021-05-28 14:01:33.000000'),
        (9,  '1.2.246.578.4.3.12.512.310173998', 35, 4, 422, 656, NULL, NULL, '010100000062040EF6737F18413FAD960099CA5B41', 'tl512', NULL, ARRAY['lisatty'::varustetoteuma_tyyppi], 'Puuttuu', '2020-09-30', '2020-10-14', 'migraatio', '2021-05-28 14:01:33.000000'),
        (10, '1.2.246.578.4.3.12.512.310173998', 35, 4, 422, 656, NULL, NULL, '010100000062040EF6737F18413FAD960099CA5B41', 'tl512', NULL, ARRAY['paivitetty'::varustetoteuma_tyyppi], 'Hyvä', '2020-10-15', '2020-10-24', 'migraatio', '2021-05-28 14:01:33.000000'),
        (11, '1.2.246.578.4.3.12.512.310173998', 35, 4, 422, 656, NULL, NULL, '010100000062040EF6737F18413FAD960099CA5B41', 'tl512', NULL, ARRAY['paivitetty'::varustetoteuma_tyyppi], 'Erittäin huono', '2020-10-25', NULL, 'migraatio', '2021-05-28 14:01:33.000000'),
        (12, '1.2.246.578.4.3.12.512.310173999', 33, 4, 422, 663, NULL, NULL, '01010000003990E35E587F1841FAB1ED4C99CA5B41', 'tl512', NULL, ARRAY['lisatty'::varustetoteuma_tyyppi], 'Puuttuu', '2023-09-30', NULL, 'migraatio', '2021-05-28 14:01:33.000000'),
        (13, '1.2.246.578.4.3.12.512.310174000', 33, 4, 422, 685, NULL, NULL, '010100000036A36DB7017F1841B6EB36429ACA5B41', 'tl512', NULL, ARRAY['lisatty'::varustetoteuma_tyyppi], 'Puuttuu', '2022-10-01', NULL, 'migraatio', '2021-05-28 14:01:33.000000'),
        (14, '1.2.246.578.4.3.12.512.310174001', 33, 4, 422, 2283, NULL, NULL, '01010000009D8153FE6F661841E316C055E0CA5B41', 'tl512', NULL, ARRAY['lisatty'::varustetoteuma_tyyppi], 'Puuttuu', '2024-10-01', NULL, 'migraatio', '2021-05-28 14:01:33.000000'),
        (15, '1.2.246.578.4.3.12.512.310174002', 33, 4, 422, 2317, NULL, NULL, '0101000000ED563147E86518410FCA46E2E0CA5B41', 'tl512', NULL, ARRAY['lisatty'::varustetoteuma_tyyppi], 'Puuttuu', '2025-09-30', NULL, 'migraatio', '2021-05-28 14:01:33.000000'),
        (16, '1.2.246.578.4.3.12.512.310174003', 33, 4, 422, 3846, NULL, NULL, '01010000000173C7483E4E184175AAD9A9B5CA5B41', 'tl512', NULL, ARRAY['lisatty'::varustetoteuma_tyyppi], 'Puuttuu', '2026-09-30', NULL, 'migraatio', '2021-05-28 14:01:33.000000'),
        (17, '1.2.246.578.4.3.12.512.310174004', 33, 4, 422, 3898, NULL, NULL, '0101000000C01F567B6E4D184168963E1BB5CA5B41', 'tl512', NULL, ARRAY['poistettu'::varustetoteuma_tyyppi], 'Puuttuu', '2020-09-30', '2021-06-01', 'migraatio', '2021-05-28 14:01:33.000000');