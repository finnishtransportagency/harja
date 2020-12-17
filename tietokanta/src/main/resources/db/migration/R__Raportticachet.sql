DROP MATERIALIZED VIEW raportti_pohjavesialueiden_suolatoteumat;
CREATE MATERIALIZED VIEW raportti_pohjavesialueiden_suolatoteumat AS
SELECT t.urakka                             AS "urakka-id",
       date_trunc('day', rp.aika)           AS paiva,
       SUM(rp.maara)                        AS yhteensa,
       pva_k.tie,
       pva_k.alkuosa,
       pva_k.alkuet,
       pva_k.loppuosa,
       pva_k.loppuet,
       (array_agg(pva_k.pituus))[1]         AS pituus, -- Tuossa pituudessa on vain yksi arvo
       (array_agg(pva_k.tunnus))[1]         AS tunnus,
       (array_agg(pva_k.talvisuolaraja))[1] AS kayttoraja
  FROM suolatoteuma_reittipiste rp
           LEFT JOIN toteuma t ON t.id = rp.toteuma
           JOIN LATERAL (SELECT *
                           FROM pohjavesialue_kooste pva_k
                          WHERE pva_k.tunnus = rp.pohjavesialue
                          ORDER BY ST_Distance84(pva_k.alue, ST_Point(rp.sijainti[0], rp.sijainti[1]))
                          LIMIT 1) AS pva_k ON TRUE
 WHERE rp.pohjavesialue IS NOT NULL
 GROUP BY t.urakka, paiva, pva_k.tie, pva_k.alkuosa, pva_k.alkuet, pva_k.loppuosa, pva_k.loppuet
WITH NO DATA;


DROP MATERIALIZED VIEW raportti_toteutuneet_materiaalit;
CREATE MATERIALIZED VIEW raportti_toteutuneet_materiaalit AS
SELECT SUM(tm.maara)                AS kokonaismaara,
       t.urakka                     AS "urakka-id",
       mk.id                        AS "materiaali-id",
       date_trunc('day', t.alkanut) AS paiva
    FROM toteuma_materiaali tm
             JOIN toteuma t ON t.id = tm.toteuma AND t.poistettu IS NOT TRUE
             LEFT JOIN materiaalikoodi mk ON mk.id = tm.materiaalikoodi
    WHERE tm.poistettu IS NOT TRUE
    GROUP BY "urakka-id", paiva, "materiaali-id"
WITH NO DATA;