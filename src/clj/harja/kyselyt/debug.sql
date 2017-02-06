-- name: hae-toteuman-reitti-ja-pisteet
SELECT t.reitti,
       rp.id AS reittipiste_id,
       rp.aika AS reittipiste_aika,
       rp.sijainti AS reittipiste_sijainti
  FROM toteuma t
       LEFT JOIN reittipiste rp ON rp.toteuma = t.id
 WHERE t.id = :toteuma-id
