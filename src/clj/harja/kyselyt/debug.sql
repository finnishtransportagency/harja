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
WHERE t.tyokoneid = :tyokoneid;-- name: hae-urakan-tierekisteriosoitteita
-- name: seuraava-vapaa-ulkoinen-id
select (COALESCE(t.ulkoinen_id, 0) + 1) as ulkoinen_id
from toteuma t
where t.ulkoinen_id is not null
order by t.ulkoinen_id desc
limit 1;
select tr.id, tr."tr-numero" as tie, tr."tr-osa" as osa, tr."tr-alkuetaisyys" as aet, tr."tr-loppuetaisyys" as let
from tr_osoitteet tr,
     urakka u
WHERE u.id = :urakka-id
  and st_within(
    tr_osoitteelle_viiva3(tr."tr-numero", tr."tr-osa", tr."tr-alkuetaisyys", tr."tr-osa", tr."tr-loppuetaisyys"),
    u.alue)
order by tr."tr-numero" asc, tr."tr-osa" asc
limit 200;
