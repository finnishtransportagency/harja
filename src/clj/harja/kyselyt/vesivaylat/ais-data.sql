-- name: lisaa-alukselle-reittipiste<!
INSERT INTO vv_alus_sijainti (alus, sijainti, aika)
VALUES (:mmsi, :sijainti :: POINT, :aika);
