-- Siirrä kaikki toteumat analytiikka_toteumat tauluun
-- Siirrertään uudet toteumat
INSERT INTO analytiikka_toteumat (
    SELECT t.id                                                                       as toteuma_tunniste_id,
           t.sopimus                                                                  as toteuma_sopimus_id,
           t.alkanut                                                                  as toteuma_alkanut,
           t.paattynyt                                                                as toteuma_paattynyt,
           u.urakkanro                                                                AS toteuma_alueurakkanumero,
           t.suorittajan_ytunnus                                                      as toteuma_suorittaja_ytunnus,
           t.suorittajan_nimi                                                         as toteuma_suorittaja_nimi,
           t.tyyppi::toteumatyyppi                                                    as toteuma_toteumatyyppi, -- "yksikkohintainen","kokonaishintainen","akillinen-hoitotyo","lisatyo", "muutostyo","vahinkojen-korjaukset"
           t.lisatieto                                                                as toteuma_lisatieto,
           to_json(array_agg(DISTINCT (tkoodi.id, tt.maara, tkoodi.yksikko, tt.lisatieto, tkoodi.tehtavaryhma))) AS toteumatehtavat,
           to_json(array_agg(DISTINCT (mk.id, mk.nimi, tm.maara, mk.yksikko)))          AS toteumamateriaalit,
           t.tr_numero                                                                as toteuma_tiesijainti_numero,
           t.tr_alkuosa                                                               as toteuma_tiesijainti_aosa,
           t.tr_alkuetaisyys                                                          as toteuma_tiesijainti_aet,
           t.tr_loppuosa                                                              as toteuma_tiesijainti_losa,
           t.tr_loppuetaisyys                                                         as toteuma_tiesijainti_let,
           t.luotu                                                                    as toteuma_muutostiedot_luotu,
           t.luoja                                                                    as toteuma_muutostiedot_luoja,
           t.muokattu                                                                 as toteuma_muutostiedot_muokattu,
           t.muokkaaja                                                                as toteuma_muutostiedot_muokkaaja,
           t.tyokonetyyppi                                                            as tyokone_tyokonetyyppi,
           t.tyokonetunniste                                                          as tyokone_tunnus,
           t.urakka                                                                   as urakkaid,
           t.poistettu                                                                as poistettu
    FROM toteuma t
             LEFT JOIN toteuma_tehtava tt ON tt.toteuma = t.id
             LEFT JOIN tehtava tkoodi ON tkoodi.id = tt.toimenpidekoodi
             LEFT JOIN toteuma_materiaali tm ON tm.toteuma = t.id
             LEFT JOIN materiaalikoodi mk ON tm.materiaalikoodi = mk.id
             JOIN urakka u on t.urakka = u.id
    WHERE (t.alkanut BETWEEN '2000-01-01T00:00:00' AND '2100-01-01T00:00:00')
    GROUP BY t.id, t.alkanut, u.id
    ORDER BY t.alkanut ASC
)
ON CONFLICT DO NOTHING;
