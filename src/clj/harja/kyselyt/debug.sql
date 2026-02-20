-- name: hae-toteuman-reitti-ja-pisteet
SELECT t.reitti,
       (rp.rp).aika AS reittipiste_aika,
       (rp.rp).sijainti AS reittipiste_sijainti
  FROM toteuma t
       LEFT JOIN LATERAL
       (SELECT unnest(reittipisteet) AS rp
          FROM toteuman_reittipisteet rp
         WHERE toteuma = t.id) rp ON true
 WHERE t.id = :toteuma-id;

-- name: hae-tyokonehavainto-reitti
SELECT ST_Simplify(t.sijainti,0.6,true) as sijainti
  FROM tyokonehavainto t
WHERE t.tyokoneid = :tyokoneid;

-- name: seuraava-vapaa-ulkoinen-id
select (COALESCE(t.ulkoinen_id, 0) + 1) as ulkoinen_id
from toteuma t
where t.ulkoinen_id is not null
order by t.ulkoinen_id desc
limit 1;

-- name: hae-urakan-tierekisteriosoitteita
select tr.id, tr."tr-numero" as tie, tr."tr-osa" as osa, tr."tr-alkuetaisyys" as aet, tr."tr-loppuetaisyys" as let
from tr_osoitteet tr,
     urakka u
WHERE u.id = :urakka-id
  and st_within(
    tieosoitteelle_viiva(tr."tr-numero", tr."tr-osa", tr."tr-alkuetaisyys", tr."tr-osa", tr."tr-loppuetaisyys"),
    u.alue)
order by tr."tr-numero" asc, tr."tr-osa" asc
limit 200;

-- name: paivita-toteuma-tehtavat
select paivita_raportti_toteuma_maarat();

-- name: paivita-toteuma-materiaalit
select paivita_raportti_toteutuneet_materiaalit();

-- name: paivita-pohjavesialuekooste
SELECT paivita_pohjavesialue_kooste();

-- name: paivita-materiaalin-kaytto-urakalle
select paivita_urakan_materiaalin_kaytto_hoitoluokittain(:urakka-id::INT, :alkupvm::DATE, :loppupvm::DATE);

-- name: paivita-pohjavesialueiden-suolatoteumat
SELECT paivita_raportti_pohjavesialueiden_suolatoteumat();

-- name: hae-suolapoikkeamat
-- Hakee toteumat joissa on poikkeamia suolamäärissä (kokonaismäärä vs reittipisteet vs suolapisteet)
-- Rajataan vain suola-materiaaleihin ja vain poikkeaviin toteumiin
WITH toteumat AS (
    SELECT id,
           alkanut
      FROM toteuma
     WHERE urakka = :urakka-id::INT
       AND alkanut >= :alkupvm::DATE
       AND alkanut < (:loppupvm::DATE + INTERVAL '1 day')
       AND poistettu IS NOT TRUE
),
suolamateriaalikoodit AS (
    SELECT id
      FROM materiaalikoodi
     WHERE materiaalityyppi IN ('talvisuola', 'erityisalue', 'kesasuola')
),
toteuma_kokonaismaarat AS (
    SELECT tm.toteuma,
           SUM(tm.maara) AS kokonaismaara
      FROM toteuma_materiaali tm
           JOIN toteumat t
             ON tm.toteuma = t.id
           JOIN suolamateriaalikoodit sm
             ON tm.materiaalikoodi = sm.id
     GROUP BY tm.toteuma
),
toteuma_reittipistesummat AS (
    SELECT tr.toteuma,
           SUM((m).maara) AS reittipistesumma
      FROM toteuman_reittipisteet tr
           JOIN toteumat t
             ON tr.toteuma = t.id
           CROSS JOIN LATERAL unnest(reittipisteet) AS rp
           CROSS JOIN LATERAL unnest((rp).materiaalit) AS m
           JOIN suolamateriaalikoodit sm
             ON (m).materiaalikoodi = sm.id
     GROUP BY tr.toteuma
),
toteuma_suolapistesummat AS (
    SELECT srp.toteuma,
           SUM(srp.maara) AS suolapistesumma
      FROM suolatoteuma_reittipiste srp
           JOIN toteumat t
             ON srp.toteuma = t.id
           JOIN suolamateriaalikoodit sm
             ON srp.materiaalikoodi = sm.id
     GROUP BY srp.toteuma
),
toteuma_rtm AS (
    SELECT rtm."urakka-id",
           rtm.paiva,
           SUM(rtm.kokonaismaara) AS rtm_suola_maara
      FROM raportti_toteutuneet_materiaalit rtm
           JOIN suolamateriaalikoodit sm
             ON rtm."materiaali-id" = sm.id
     WHERE rtm."urakka-id" = :urakka-id::INT
       AND rtm.paiva >= :alkupvm::DATE
       AND rtm.paiva < (:loppupvm::DATE + INTERVAL '1 day')
     GROUP BY rtm."urakka-id", rtm.paiva
)
SELECT t.id AS "toteuma-id",
       t.alkanut,
       COALESCE(tk.kokonaismaara, 0)    AS kokonaismaara,
       COALESCE(tr.reittipistesumma, 0) AS reittipistesumma,
       COALESCE(ts.suolapistesumma, 0)  AS suolapistesumma,
       (COALESCE(tk.kokonaismaara, 0) - COALESCE(tr.reittipistesumma, 0)) AS delta1,
       (COALESCE(tr.reittipistesumma, 0) - COALESCE(ts.suolapistesumma, 0)) AS delta2,
       (rtm.rtm_suola_maara IS NOT NULL) AS rtm_loytyy,
       rtm.rtm_suola_maara
  FROM toteumat t
       LEFT JOIN toteuma_kokonaismaarat tk
         ON t.id = tk.toteuma
       LEFT JOIN toteuma_reittipistesummat tr
         ON t.id = tr.toteuma
       LEFT JOIN toteuma_suolapistesummat ts
         ON t.id = ts.toteuma
       LEFT JOIN toteuma_rtm rtm
         ON rtm."urakka-id" = :urakka-id::INT
        AND rtm.paiva = t.alkanut::date
 WHERE ((COALESCE(tk.kokonaismaara, 0) - COALESCE(tr.reittipistesumma, 0)) <> 0
        OR (COALESCE(tr.reittipistesumma, 0) - COALESCE(ts.suolapistesumma, 0)) <> 0)
 ORDER BY t.alkanut DESC
 LIMIT 500;
