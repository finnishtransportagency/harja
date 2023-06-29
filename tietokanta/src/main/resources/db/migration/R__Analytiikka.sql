-- Joka yö siirretään raskaista toteumiin liittyvistä tauluista edellisen vuorokauden aikana tulleet ja muokatut
-- toteumat analytiikalle tehtyyn omaan tauluun.
-- Jos vahingossa siirretään sama toteuma toiseen kertaan, niin luovutaan yrityksestä
DROP FUNCTION IF EXISTS siirra_toteumat_analytiikalle(TIMESTAMP with time zone);
DROP FUNCTION IF EXISTS siirra_toteumat_analytiikalle(TIMESTAMP);
CREATE OR REPLACE FUNCTION siirra_toteumat_analytiikalle(ajankohta TIMESTAMP with time zone) RETURNS VOID AS
$$
DECLARE
    muuttunut_toteuma RECORD;
BEGIN

    -- Siirrertään uudet toteumat
    INSERT INTO analytiikka_toteumat (
        SELECT t.id                                                                              AS toteuma_tunniste_id,
               t.sopimus                                                                         AS toteuma_sopimus_id,
               t.alkanut                                                                         AS toteuma_alkanut,
               t.paattynyt                                                                       AS toteuma_paattynyt,
               u.urakkanro                                                                       AS toteuma_alueurakkanumero,
               t.suorittajan_ytunnus                                                             AS toteuma_suorittaja_ytunnus,
               t.suorittajan_nimi                                                                AS toteuma_suorittaja_nimi,
               t.tyyppi::toteumatyyppi                                                           AS toteuma_toteumatyyppi, -- "yksikkohintainen","kokonaishintainen","akillinen-hoitotyo","lisatyo", "muutostyo","vahinkojen-korjaukset"
               t.lisatieto                                                                       AS toteuma_lisatieto,
               json_agg(row_to_json(row (tt.id, tt.maara, tkoodi.yksikko, tt.lisatieto)))        AS toteumatehtavat,
               json_agg(row_to_json(row (mk.nimi, tm.maara, mk.yksikko)))                        AS toteumamateriaalit,
               t.tr_numero                                                                       AS toteuma_tiesijainti_numero,
               t.tr_alkuosa                                                                      AS toteuma_tiesijainti_aosa,
               t.tr_alkuetaisyys                                                                 AS toteuma_tiesijainti_aet,
               t.tr_loppuosa                                                                     AS toteuma_tiesijainti_losa,
               t.tr_loppuetaisyys                                                                AS toteuma_tiesijainti_let,
               t.luotu                                                                           AS toteuma_muutostiedot_luotu,
               t.luoja                                                                           AS toteuma_muutostiedot_luoja,
               GREATEST(t.muokattu, MAX(tm.muokattu))                                                 AS toteuma_muutostiedot_muokattu,
               t.muokkaaja                                                                       AS toteuma_muutostiedot_muokkaaja,
               t.tyokonetyyppi                                                                   AS tyokone_tyokonetyyppi,
               t.tyokonetunniste                                                                 AS tyokone_tunnus,
               t.urakka                                                                          AS urakkaid,
               t.poistettu                                                                       AS poistettu
        FROM toteuma t
                 LEFT JOIN toteuma_tehtava tt ON tt.toteuma = t.id
                 LEFT JOIN tehtava tkoodi ON tkoodi.id = tt.toimenpidekoodi
                 LEFT JOIN toteuma_materiaali tm ON tm.toteuma = t.id
                 LEFT JOIN materiaalikoodi mk ON tm.materiaalikoodi = mk.id
                 JOIN urakka u on t.urakka = u.id
        WHERE (t.luotu BETWEEN ajankohta - '1 day'::interval AND ajankohta) OR (tm.luotu BETWEEN ajankohta - '1 day'::interval AND ajankohta)
        GROUP BY t.id, t.luotu, u.id
        ORDER BY t.luotu ASC
    )
    ON CONFLICT DO NOTHING;


    -- Päivitetään muokatut toteumat
    FOR muuttunut_toteuma IN
        SELECT t.id AS toteuma_tunniste_id
        FROM toteuma t
                 LEFT JOIN toteuma_tehtava tt ON tt.toteuma = t.id
                 LEFT JOIN tehtava tkoodi ON tkoodi.id = tt.toimenpidekoodi
                 LEFT JOIN toteuma_materiaali tm ON tm.toteuma = t.id
                 LEFT JOIN materiaalikoodi mk ON tm.materiaalikoodi = mk.id
                 JOIN urakka u on t.urakka = u.id
        WHERE (t.muokattu BETWEEN ajankohta - '1 day'::interval AND ajankohta) OR (tm.muokattu BETWEEN ajankohta - '1 day'::interval AND ajankohta)
        GROUP BY t.id, t.luotu, u.id
        LOOP
        -- Käytetään poista - lisää uuusiksi, menetelmää, koska update lauseessa ei voi käyttää group by komentoa
        -- tehtävien ja materiaalien lisäämiseksi.
            RAISE NOTICE '******* Muokataan toteuma % ********' , muuttunut_toteuma.toteuma_tunniste_id;
            -- Poista yksittäinen toteuma
            DELETE FROM analytiikka_toteumat at WHERE at.toteuma_tunniste_id = muuttunut_toteuma.toteuma_tunniste_id;
            -- Lisätään yksittäinen toteuma takaisin
            INSERT INTO analytiikka_toteumat (
                SELECT t.id                                                                              AS toteuma_tunniste_id,
                       t.sopimus                                                                         AS toteuma_sopimus_id,
                       t.alkanut                                                                         AS toteuma_alkanut,
                       t.paattynyt                                                                       AS toteuma_paattynyt,
                       u.urakkanro                                                                       AS toteuma_alueurakkanumero,
                       t.suorittajan_ytunnus                                                             AS toteuma_suorittaja_ytunnus,
                       t.suorittajan_nimi                                                                AS toteuma_suorittaja_nimi,
                       t.tyyppi::toteumatyyppi                                                           AS toteuma_toteumatyyppi, -- "yksikkohintainen","kokonaishintainen","akillinen-hoitotyo","lisatyo", "muutostyo","vahinkojen-korjaukset"
                       t.lisatieto                                                                       AS toteuma_lisatieto,
                       json_agg(row_to_json(row (tt.id, tt.maara, tkoodi.yksikko, tt.lisatieto)))        AS toteumatehtavat,
                       json_agg(row_to_json(row (mk.nimi, tm.maara, mk.yksikko)))                        AS toteumamateriaalit,
                       t.tr_numero                                                                       AS toteuma_tiesijainti_numero,
                       t.tr_alkuosa                                                                      AS toteuma_tiesijainti_aosa,
                       t.tr_alkuetaisyys                                                                 AS toteuma_tiesijainti_aet,
                       t.tr_loppuosa                                                                     AS toteuma_tiesijainti_losa,
                       t.tr_loppuetaisyys                                                                AS toteuma_tiesijainti_let,
                       t.luotu                                                                           AS toteuma_muutostiedot_luotu,
                       t.luoja                                                                           AS toteuma_muutostiedot_luoja,
                       GREATEST(t.muokattu, MAX(tm.muokattu))                                            AS toteuma_muutostiedot_muokattu,
                       t.muokkaaja                                                                       AS toteuma_muutostiedot_muokkaaja,
                       t.tyokonetyyppi                                                                   AS tyokone_tyokonetyyppi,
                       t.tyokonetunniste                                                                 AS tyokone_tunnus,
                       t.urakka                                                                          AS urakkaid,
                       t.poistettu                                                                       AS poistettu
                FROM toteuma t
                         LEFT JOIN toteuma_tehtava tt ON tt.toteuma = t.id
                         LEFT JOIN tehtava tkoodi ON tkoodi.id = tt.toimenpidekoodi
                         LEFT JOIN toteuma_materiaali tm ON tm.toteuma = t.id
                         LEFT JOIN materiaalikoodi mk ON tm.materiaalikoodi = mk.id
                         JOIN urakka u on t.urakka = u.id
                WHERE t.id = muuttunut_toteuma.toteuma_tunniste_id
                GROUP BY t.id, t.luotu, u.id);
        END LOOP;

END;
$$ LANGUAGE plpgsql;
