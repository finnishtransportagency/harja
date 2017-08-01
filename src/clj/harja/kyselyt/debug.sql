-- name: hae-toteuman-reitti-ja-pisteet
SELECT t.reitti,
       (rp.rp).aika AS reittipiste_aika,
       (rp.rp).sijainti AS reittipiste_sijainti
  FROM toteuma t
       LEFT JOIN LATERAL
       (SELECT unnest(reittipisteet) AS rp
          FROM toteuman_reittipisteet rp
         WHERE toteuma = t.id) rp ON true
 WHERE t.id = :toteuma-id
