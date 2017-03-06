-- name: hae-toteuman-reitti-ja-pisteet
SELECT t.reitti,
       rp.id AS reittipiste_id,
       rp.aika AS reittipiste_aika,
       rp.sijainti AS reittipiste_sijainti
  FROM toteuma t
       LEFT JOIN reittipiste rp ON rp.toteuma = t.id
 WHERE t.id = :toteuma-id

-- name: hae-toteuman-reitti-ja-pisteet-wkt
-- single?: true
SELECT st_astext(st_collect(t.reitti, st_collect((SELECT array_agg(rp.sijainti)::geometry[]
                                                    FROM reittipiste rp
						   WHERE rp.toteuma=t.id))))
  FROM toteuma t
 WHERE t.id = :toteuma-id
