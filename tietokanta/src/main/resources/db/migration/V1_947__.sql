-- Joka yö siirretään raskaista toteumiin liittyvistä tauluista edellisen vuorokauden aikana tulleet ja muokatut
-- toteumat analytiikalle tehtyyn omaan tauluun.
-- Jos vahingossa siirretään sama toteuma toiseen kertaan, niin luovutaan yrityksestä
CREATE OR REPLACE FUNCTION siirra_toteumat_analytiikalle(ajankohta TIMESTAMP) RETURNS VOID AS
$$
BEGIN

    INSERT INTO analytiikka_toteumat (
        SELECT t.id as toteuma_tunniste_id,
               t.sopimus as toteuma_sopimus_id,
               t.alkanut as toteuma_alkanut,
               t.paattynyt as toteuma_paattynyt,
               u.urakkanro AS toteuma_alueurakkanumero,
               t.suorittajan_ytunnus as toteuma_suorittaja_ytunnus,
               t.suorittajan_nimi as toteuma_suorittaja_nimi,
               t.tyyppi::toteumatyyppi as toteuma_toteumatyyppi, -- "yksikkohintainen","kokonaishintainen","akillinen-hoitotyo","lisatyo", "muutostyo","vahinkojen-korjaukset"
               t.lisatieto as toteuma_lisatieto,
               json_agg(row_to_json(row(tt.id, tt.maara, tkoodi.yksikko, tt.lisatieto))) AS toteumatehtavat,
               json_agg(row_to_json(row(mk.nimi, tm.maara, mk.yksikko))) AS toteumamateriaalit,
               t.tr_numero as toteuma_tiesijainti_numero,
               t.tr_alkuosa as toteuma_tiesijainti_aosa,
               t.tr_alkuetaisyys as toteuma_tiesijainti_aet,
               t.tr_loppuosa as toteuma_tiesijainti_losa,
               t.tr_loppuetaisyys as toteuma_tiesijainti_let,
               t.luotu as toteuma_muutostiedot_luotu,
               t.luoja as toteuma_muutostiedot_luoja,
               t.muokattu as toteuma_muutostiedot_muokattu,
               t.muokkaaja as toteuma_muutostiedot_muokkaaja,
               t.tyokonetyyppi as tyokone_tyokonetyyppi,
               t.tyokonetunniste as tyokone_tunnus
        FROM toteuma t
                 LEFT JOIN toteuma_tehtava tt ON tt.toteuma = t.id
                 LEFT JOIN toimenpidekoodi tkoodi ON tkoodi.id = tt.toimenpidekoodi
                 LEFT JOIN toteuma_materiaali tm ON tm.toteuma = t.id
                 LEFT JOIN materiaalikoodi mk ON tm.materiaalikoodi = mk.id
                 JOIN urakka u on t.urakka = u.id
        WHERE (t.alkanut BETWEEN ajankohta - '1 day'::interval AND ajankohta)
        GROUP BY t.id, t.alkanut, u.id
        ORDER BY t.alkanut ASC
    ) ON CONFLICT DO NOTHING;

END;
$$ LANGUAGE plpgsql;
