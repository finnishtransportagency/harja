-- Joka yö siirretään raskaista toteumiin liittyvistä tauluista valittujen alkuajan ja loppuajan väliset
-- toteumat analytiikalle tehtyyn omaan tauluun.
-- Jos vahingossa siirretään sama toteuma toiseen kertaan, niin ei haittaa. Konfliktissa ei tehdä mitään erityistä.

CREATE OR REPLACE FUNCTION siirra_toteumat_analytiikalle(alkuaika TIMESTAMP WITH TIME ZONE, loppuaika TIMESTAMP WITH TIME ZONE) RETURNS VOID AS
$$
DECLARE
    muuttunut_toteuma RECORD;
BEGIN

    -- Siirrertään uudet toteumat
    INSERT INTO analytiikka_toteumat
        (SELECT t.id                                                                                       AS toteuma_tunniste_id,
                t.sopimus                                                                                AS toteuma_sopimus_id,
                t.alkanut                                                                                AS toteuma_alkanut,
                t.paattynyt                                                                              AS toteuma_paattynyt,
                u.urakkanro                                                                              AS toteuma_alueurakkanumero,
                t.suorittajan_ytunnus                                                                    AS toteuma_suorittaja_ytunnus,
                t.suorittajan_nimi                                                                       AS toteuma_suorittaja_nimi,
                t.tyyppi::toteumatyyppi                                                                  AS toteuma_toteumatyyppi, -- "yksikkohintainen","kokonaishintainen","akillinen-hoitotyo","lisatyo", "muutostyo","vahinkojen-korjaukset"
                t.lisatieto                                                                              AS toteuma_lisatieto,
                to_json(array_agg(DISTINCT (teh.id, tt.maara, teh.yksikko, tt.lisatieto, teh.tehtavaryhma, tt.id,
                                            tt.poistettu, tt.luotu, tt.muokattu)))                       AS toteumatehtavat,
                to_json(array_agg(DISTINCT (mk.id, mk.nimi, tm.maara, mk.yksikko, tm.id,
                                            tm.poistettu, tm.luotu, tm.muokattu)))                       AS toteumamateriaalit,
                t.tr_numero                                                                              AS toteuma_tiesijainti_numero,
                t.tr_alkuosa                                                                             AS toteuma_tiesijainti_aosa,
                t.tr_alkuetaisyys                                                                        AS toteuma_tiesijainti_aet,
                t.tr_loppuosa                                                                            AS toteuma_tiesijainti_losa,
                t.tr_loppuetaisyys                                                                       AS toteuma_tiesijainti_let,
                t.luotu                                                                                  AS toteuma_muutostiedot_luotu,
                t.luoja                                                                                  AS toteuma_muutostiedot_luoja,
                GREATEST(t.muokattu, MAX(tm.muokattu))                                                   AS toteuma_muutostiedot_muokattu,
                t.muokkaaja                                                                              AS toteuma_muutostiedot_muokkaaja,
                t.tyokonetyyppi                                                                          AS tyokone_tyokonetyyppi,
                t.tyokonetunniste                                                                        AS tyokone_tunnus,
                t.urakka                                                                                 AS urakkaid,
                t.poistettu                                                                              AS poistettu
           FROM toteuma t
                    LEFT JOIN toteuma_tehtava tt ON tt.toteuma = t.id
                    LEFT JOIN tehtava teh ON teh.id = tt.toimenpidekoodi
                    LEFT JOIN toteuma_materiaali tm ON tm.toteuma = t.id
                    LEFT JOIN materiaalikoodi mk ON tm.materiaalikoodi = mk.id
                    JOIN urakka u ON t.urakka = u.id
         WHERE (t.luotu BETWEEN alkuaika AND loppuaika)
            OR (tm.luotu BETWEEN alkuaika AND loppuaika)
          GROUP BY t.id, t.luotu, u.id
          ORDER BY t.luotu ASC)
        ON CONFLICT DO NOTHING;


    -- Päivitetään muokatut toteumat
    FOR muuttunut_toteuma IN
        SELECT t.id AS toteuma_tunniste_id
          FROM toteuma t
                   LEFT JOIN toteuma_tehtava tt ON tt.toteuma = t.id
                   LEFT JOIN tehtava teh ON teh.id = tt.toimenpidekoodi
                   LEFT JOIN toteuma_materiaali tm ON tm.toteuma = t.id
                   LEFT JOIN materiaalikoodi mk ON tm.materiaalikoodi = mk.id
                   JOIN urakka u ON t.urakka = u.id
         WHERE (t.muokattu BETWEEN alkuaika AND loppuaika)
            OR (tm.muokattu BETWEEN alkuaika AND loppuaika)
         GROUP BY t.id, t.luotu, u.id
        LOOP
        -- Käytetään poista - lisää uuusiksi, menetelmää, koska update lauseessa ei voi käyttää group by komentoa
        -- tehtävien ja materiaalien lisäämiseksi.
            RAISE NOTICE '******* Muokataan toteuma % ********' , muuttunut_toteuma.toteuma_tunniste_id;
            -- Poista yksittäinen toteuma
            DELETE FROM analytiikka_toteumat at WHERE at.toteuma_tunniste_id = muuttunut_toteuma.toteuma_tunniste_id;
            -- Lisätään yksittäinen toteuma takaisin
            INSERT INTO analytiikka_toteumat
                (SELECT t.id                                                                                       AS toteuma_tunniste_id,
                        t.sopimus                                                                                  AS toteuma_sopimus_id,
                        t.alkanut                                                                                  AS toteuma_alkanut,
                        t.paattynyt                                                                                AS toteuma_paattynyt,
                        u.urakkanro                                                                                AS toteuma_alueurakkanumero,
                        t.suorittajan_ytunnus                                                                      AS toteuma_suorittaja_ytunnus,
                        t.suorittajan_nimi                                                                         AS toteuma_suorittaja_nimi,
                        t.tyyppi::toteumatyyppi                                                                    AS toteuma_toteumatyyppi, -- "yksikkohintainen","kokonaishintainen","akillinen-hoitotyo","lisatyo", "muutostyo","vahinkojen-korjaukset"
                        t.lisatieto                                                                                AS toteuma_lisatieto,
                        to_json(array_agg(DISTINCT
                                          (teh.id, tt.maara, teh.yksikko, tt.lisatieto, teh.tehtavaryhma, tt.id,
                                           tt.poistettu, tt.luotu, tt.muokattu)))                                  AS toteumatehtavat,
                        to_json(array_agg(DISTINCT (mk.id, mk.nimi, tm.maara, mk.yksikko, tm.id,
                                                    tm.poistettu, tm.luotu, tm.muokattu)))                         AS toteumamateriaalit,
                        t.tr_numero                                                                                AS toteuma_tiesijainti_numero,
                        t.tr_alkuosa                                                                               AS toteuma_tiesijainti_aosa,
                        t.tr_alkuetaisyys                                                                          AS toteuma_tiesijainti_aet,
                        t.tr_loppuosa                                                                              AS toteuma_tiesijainti_losa,
                        t.tr_loppuetaisyys                                                                         AS toteuma_tiesijainti_let,
                        t.luotu                                                                                    AS toteuma_muutostiedot_luotu,
                        t.luoja                                                                                    AS toteuma_muutostiedot_luoja,
                        GREATEST(t.muokattu, MAX(tm.muokattu))                                                     AS toteuma_muutostiedot_muokattu,
                        t.muokkaaja                                                                                AS toteuma_muutostiedot_muokkaaja,
                        t.tyokonetyyppi                                                                            AS tyokone_tyokonetyyppi,
                        t.tyokonetunniste                                                                          AS tyokone_tunnus,
                        t.urakka                                                                                   AS urakkaid,
                        t.poistettu                                                                                AS poistettu
                   FROM toteuma t
                            LEFT JOIN toteuma_tehtava tt ON tt.toteuma = t.id
                            LEFT JOIN tehtava teh ON teh.id = tt.toimenpidekoodi
                            LEFT JOIN toteuma_materiaali tm ON tm.toteuma = t.id
                            LEFT JOIN materiaalikoodi mk ON tm.materiaalikoodi = mk.id
                            JOIN urakka u ON t.urakka = u.id
                  WHERE t.id = muuttunut_toteuma.toteuma_tunniste_id
                  GROUP BY t.id, t.luotu, u.id);
        END LOOP;

END;
$$ LANGUAGE plpgsql;

-- Jos toteumat pitää siirtää uusiksi kokonaisuudessaan, niin niitä ei kannata siirtää yksi päivä kerrallaan.
-- Tästä syystä funktiosta on toinen versio, joka siirtää halutun päivämäärävälin
-- Tässä funktiossa siirto tehdään toteuman aloituspäivämäärän perusteella.
DROP FUNCTION IF EXISTS siirra_toteumat_analytiikalle_pvm_valilta(alku TIMESTAMP, loppu TIMESTAMP);
CREATE OR REPLACE FUNCTION siirra_toteumat_analytiikalle_aloitusajan_mukaan(alku TIMESTAMP, loppu TIMESTAMP)
    RETURNS VOID AS
$$
DECLARE
BEGIN

    INSERT INTO analytiikka_toteumat
        (SELECT t.id                                                                                     AS toteuma_tunniste_id,
                t.sopimus                                                                                AS toteuma_sopimus_id,
                t.alkanut                                                                                AS toteuma_alkanut,
                t.paattynyt                                                                              AS toteuma_paattynyt,
                u.urakkanro                                                                              AS toteuma_alueurakkanumero,
                t.suorittajan_ytunnus                                                                    AS toteuma_suorittaja_ytunnus,
                t.suorittajan_nimi                                                                       AS toteuma_suorittaja_nimi,
                t.tyyppi::toteumatyyppi                                                                  AS toteuma_toteumatyyppi, -- "yksikkohintainen","kokonaishintainen","akillinen-hoitotyo","lisatyo", "muutostyo","vahinkojen-korjaukset"
                t.lisatieto                                                                              AS toteuma_lisatieto,
                to_json(array_agg(DISTINCT (teh.id, tt.maara, teh.yksikko, tt.lisatieto, teh.tehtavaryhma, tt.id,
                                            tt.poistettu, tt.luotu, tt.muokattu)))                       AS toteumatehtavat,
                to_json(array_agg(DISTINCT (mk.id, mk.nimi, tm.maara, mk.yksikko, tm.id,
                                            tm.poistettu, tm.luotu, tm.muokattu)))                       AS toteumamateriaalit,
                t.tr_numero                                                                              AS toteuma_tiesijainti_numero,
                t.tr_alkuosa                                                                             AS toteuma_tiesijainti_aosa,
                t.tr_alkuetaisyys                                                                        AS toteuma_tiesijainti_aet,
                t.tr_loppuosa                                                                            AS toteuma_tiesijainti_losa,
                t.tr_loppuetaisyys                                                                       AS toteuma_tiesijainti_let,
                t.luotu                                                                                  AS toteuma_muutostiedot_luotu,
                t.luoja                                                                                  AS toteuma_muutostiedot_luoja,
                t.muokattu                                                                               AS toteuma_muutostiedot_muokattu,
                t.muokkaaja                                                                              AS toteuma_muutostiedot_muokkaaja,
                t.tyokonetyyppi                                                                          AS tyokone_tyokonetyyppi,
                t.tyokonetunniste                                                                        AS tyokone_tunnus,
                t.urakka                                                                                 AS urakkaid,
                t.poistettu                                                                              AS poistettu
           FROM toteuma t
                    LEFT JOIN toteuma_tehtava tt ON tt.toteuma = t.id
                    LEFT JOIN tehtava teh ON teh.id = tt.toimenpidekoodi
                    LEFT JOIN toteuma_materiaali tm ON tm.toteuma = t.id
                    LEFT JOIN materiaalikoodi mk ON tm.materiaalikoodi = mk.id
                    JOIN urakka u ON t.urakka = u.id
          WHERE (t.alkanut BETWEEN alku AND loppu)
          GROUP BY t.id, t.alkanut, u.id
          ORDER BY t.alkanut ASC)
        ON CONFLICT DO NOTHING;
END;
$$ LANGUAGE plpgsql;

-- Jos toteumat pitää siirtää uusiksi kokonaisuudessaan, niin niitä ei kannata siirtää yksi päivä kerrallaan.
-- Tästä syystä funktiosta on toinen versio, joka siirtää halutun päivämäärävälin
-- Tässä funktiossa siirto tehdään toteuman muokkaustietojen (luontiaika, muokkausaika) perusteella.
CREATE OR REPLACE FUNCTION siirra_toteumat_analytiikalle_muokkaustietojen_mukaan(alku TIMESTAMP, loppu TIMESTAMP) RETURNS VOID AS
$$
DECLARE
    muuttunut_toteuma RECORD;
BEGIN

    -- Siirrertään uudet toteumat
    INSERT INTO analytiikka_toteumat
        (SELECT t.id                                                                                       AS toteuma_tunniste_id,
                t.sopimus                                                                                AS toteuma_sopimus_id,
                t.alkanut                                                                                AS toteuma_alkanut,
                t.paattynyt                                                                              AS toteuma_paattynyt,
                u.urakkanro                                                                              AS toteuma_alueurakkanumero,
                t.suorittajan_ytunnus                                                                    AS toteuma_suorittaja_ytunnus,
                t.suorittajan_nimi                                                                       AS toteuma_suorittaja_nimi,
                t.tyyppi::toteumatyyppi                                                                  AS toteuma_toteumatyyppi, -- "yksikkohintainen","kokonaishintainen","akillinen-hoitotyo","lisatyo", "muutostyo","vahinkojen-korjaukset"
                t.lisatieto                                                                              AS toteuma_lisatieto,
                to_json(array_agg(DISTINCT (teh.id, tt.maara, teh.yksikko, tt.lisatieto, teh.tehtavaryhma, tt.id,
                                            tt.poistettu, tt.luotu, tt.muokattu)))                       AS toteumatehtavat,
                to_json(array_agg(DISTINCT (mk.id, mk.nimi, tm.maara, mk.yksikko, tm.id,
                                            tm.poistettu, tm.luotu, tm.muokattu)))                       AS toteumamateriaalit,
                t.tr_numero                                                                              AS toteuma_tiesijainti_numero,
                t.tr_alkuosa                                                                             AS toteuma_tiesijainti_aosa,
                t.tr_alkuetaisyys                                                                        AS toteuma_tiesijainti_aet,
                t.tr_loppuosa                                                                            AS toteuma_tiesijainti_losa,
                t.tr_loppuetaisyys                                                                       AS toteuma_tiesijainti_let,
                t.luotu                                                                                  AS toteuma_muutostiedot_luotu,
                t.luoja                                                                                  AS toteuma_muutostiedot_luoja,
                GREATEST(t.muokattu, MAX(tm.muokattu))                                                   AS toteuma_muutostiedot_muokattu,
                t.muokkaaja                                                                              AS toteuma_muutostiedot_muokkaaja,
                t.tyokonetyyppi                                                                          AS tyokone_tyokonetyyppi,
                t.tyokonetunniste                                                                        AS tyokone_tunnus,
                t.urakka                                                                                 AS urakkaid,
                t.poistettu                                                                              AS poistettu
         FROM toteuma t
                  LEFT JOIN toteuma_tehtava tt ON tt.toteuma = t.id
                  LEFT JOIN tehtava teh ON teh.id = tt.toimenpidekoodi
                  LEFT JOIN toteuma_materiaali tm ON tm.toteuma = t.id
                  LEFT JOIN materiaalikoodi mk ON tm.materiaalikoodi = mk.id
                  JOIN urakka u ON t.urakka = u.id
         WHERE (t.luotu BETWEEN alku AND loppu)
            OR (tm.luotu BETWEEN alku AND loppu)
         GROUP BY t.id, t.luotu, u.id
         ORDER BY t.luotu ASC)
    ON CONFLICT DO NOTHING;


    -- Päivitetään muokatut toteumat
    FOR muuttunut_toteuma IN
        SELECT t.id AS toteuma_tunniste_id
        FROM toteuma t
                 LEFT JOIN toteuma_tehtava tt ON tt.toteuma = t.id
                 LEFT JOIN tehtava teh ON teh.id = tt.toimenpidekoodi
                 LEFT JOIN toteuma_materiaali tm ON tm.toteuma = t.id
                 LEFT JOIN materiaalikoodi mk ON tm.materiaalikoodi = mk.id
                 JOIN urakka u ON t.urakka = u.id
        WHERE (t.muokattu BETWEEN alku AND loppu)
           OR (tm.muokattu BETWEEN alku AND loppu)
        GROUP BY t.id, t.luotu, u.id
        LOOP
        -- Käytetään poista - lisää uuusiksi, menetelmää, koska update lauseessa ei voi käyttää group by komentoa
        -- tehtävien ja materiaalien lisäämiseksi.
            RAISE NOTICE '******* Muokataan toteuma % ********' , muuttunut_toteuma.toteuma_tunniste_id;
            -- Poista yksittäinen toteuma
            DELETE FROM analytiikka_toteumat at WHERE at.toteuma_tunniste_id = muuttunut_toteuma.toteuma_tunniste_id;
            -- Lisätään yksittäinen toteuma takaisin
            INSERT INTO analytiikka_toteumat
                (SELECT t.id                                                                                       AS toteuma_tunniste_id,
                        t.sopimus                                                                                  AS toteuma_sopimus_id,
                        t.alkanut                                                                                  AS toteuma_alkanut,
                        t.paattynyt                                                                                AS toteuma_paattynyt,
                        u.urakkanro                                                                                AS toteuma_alueurakkanumero,
                        t.suorittajan_ytunnus                                                                      AS toteuma_suorittaja_ytunnus,
                        t.suorittajan_nimi                                                                         AS toteuma_suorittaja_nimi,
                        t.tyyppi::toteumatyyppi                                                                    AS toteuma_toteumatyyppi, -- "yksikkohintainen","kokonaishintainen","akillinen-hoitotyo","lisatyo", "muutostyo","vahinkojen-korjaukset"
                        t.lisatieto                                                                                AS toteuma_lisatieto,
                        to_json(array_agg(DISTINCT
                                          (teh.id, tt.maara, teh.yksikko, tt.lisatieto, teh.tehtavaryhma, tt.id,
                                           tt.poistettu, tt.luotu, tt.muokattu)))                                  AS toteumatehtavat,
                        to_json(array_agg(DISTINCT (mk.id, mk.nimi, tm.maara, mk.yksikko, tm.id,
                                                    tm.poistettu, tm.luotu, tm.muokattu)))                         AS toteumamateriaalit,
                        t.tr_numero                                                                                AS toteuma_tiesijainti_numero,
                        t.tr_alkuosa                                                                               AS toteuma_tiesijainti_aosa,
                        t.tr_alkuetaisyys                                                                          AS toteuma_tiesijainti_aet,
                        t.tr_loppuosa                                                                              AS toteuma_tiesijainti_losa,
                        t.tr_loppuetaisyys                                                                         AS toteuma_tiesijainti_let,
                        t.luotu                                                                                    AS toteuma_muutostiedot_luotu,
                        t.luoja                                                                                    AS toteuma_muutostiedot_luoja,
                        GREATEST(t.muokattu, MAX(tm.muokattu))                                                     AS toteuma_muutostiedot_muokattu,
                        t.muokkaaja                                                                                AS toteuma_muutostiedot_muokkaaja,
                        t.tyokonetyyppi                                                                            AS tyokone_tyokonetyyppi,
                        t.tyokonetunniste                                                                          AS tyokone_tunnus,
                        t.urakka                                                                                   AS urakkaid,
                        t.poistettu                                                                                AS poistettu
                 FROM toteuma t
                          LEFT JOIN toteuma_tehtava tt ON tt.toteuma = t.id
                          LEFT JOIN tehtava teh ON teh.id = tt.toimenpidekoodi
                          LEFT JOIN toteuma_materiaali tm ON tm.toteuma = t.id
                          LEFT JOIN materiaalikoodi mk ON tm.materiaalikoodi = mk.id
                          JOIN urakka u ON t.urakka = u.id
                 WHERE t.id = muuttunut_toteuma.toteuma_tunniste_id
                 GROUP BY t.id, t.luotu, u.id);
        END LOOP;

END;
$$ LANGUAGE plpgsql;

-- Ja aja se tuotantoon suunnilleen seuraavasti (Tarkista ajankohdat):
-- Ensin poistetaan analytiikka_toteumat taulusta kaikki:
-- DELETE FROM analytiikka_toteumat;
-- select siirra_toteumat_analytiikalle_aloitusajan_mukaan('0001-01-01T00:00:00','2014-12-31T23:59:59');
-- select siirra_toteumat_analytiikalle_aloitusajan_mukaan('2015-01-01T00:00:00','2015-12-31T23:59:59');
-- select siirra_toteumat_analytiikalle_aloitusajan_mukaan('2016-01-01T00:00:00','2016-12-31T23:59:59');
-- select siirra_toteumat_analytiikalle_aloitusajan_mukaan('2017-01-01T00:00:00','2017-12-31T23:59:59');
-- select siirra_toteumat_analytiikalle_aloitusajan_mukaan('2018-01-01T00:00:00','2018-12-31T23:59:59');
-- select siirra_toteumat_analytiikalle_aloitusajan_mukaan('2019-01-01T00:00:00','2019-12-31T23:59:59');
-- select siirra_toteumat_analytiikalle_aloitusajan_mukaan('2020-01-01T00:00:00','2020-12-31T23:59:59');
-- select siirra_toteumat_analytiikalle_aloitusajan_mukaan('2021-01-01T00:00:00','2021-12-31T23:59:59');
-- select siirra_toteumat_analytiikalle_aloitusajan_mukaan('2022-01-01T00:00:00','2022-12-31T23:59:59');
-- select siirra_toteumat_analytiikalle_aloitusajan_mukaan('2023-01-01T00:00:00','2023-12-31T23:59:59');
-- select siirra_toteumat_analytiikalle_aloitusajan_mukaan('2024-01-01T00:00:00','2024-12-31T23:59:59');
-- Ja siirron jälkeen on tärkeää saada indeksit ja haku toimimaan nopeasti. REINDEX komentoa ei tarvita:
--ANALYSE analytiikka_toteumat;


