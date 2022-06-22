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

-- name: hae-elyn-hoitoluokat
SELECT geometria, hoitoluokka FROM hoitoluokka hl WHERE st_intersects((SELECT alue FROM organisaatio WHERE id = :alue), hl.geometria);