-- name: tuhoa-tieturvallisuusverkko!
DELETE FROM tieturvallisuusverkko;

-- name: lisaa-tieturvallisuusverkko!
INSERT INTO tieturvallisuusverkko (tasoluokka, aosa, tie, let, losa, aet, tenluokka,
                                   geometria, ely, pituus, luonne, luotu)
VALUES (:tasoluokka, :aosa, :tie, :let, :losa, :aet, :tenluokka,
        ST_GeomFromText(:the_geom) :: GEOMETRY, :ely, :pituus, :luonne, NOW());

-- name: hae-tieturvallisuusgeometriat
SELECT tie, aosa, losa, aet, let, geometria FROM tieturvallisuusverkko;

-- name: hae-geometriaa-leikkaavat-tieturvallisuusgeometriat-tienumerolla
-- SELECT ST_Union(leikkaukset.leikkaus) AS result
-- FROM (
-- SELECT ST_Intersection(t.geometria::geometry, :saatugeometria::geometry) AS leikkaus
-- FROM tieturvallisuusverkko t
-- WHERE t.tie = :tie AND ST_Intersects(:saatugeometria ::geometry, t.geometria::geometry)
--      ) AS leikaukset;
SELECT ST_Union(geometria) as unioni, ST_AsGeoJSON(ST_Union(geometria)) as jsoni
FROM tieturvallisuusverkko
WHERE tie = :tie AND ST_Intersects(geometria, :saatugeometria);

-- SELECT geometria
-- -- SELECT ST_AsGeoJSON(geometria)
-- FROM tieturvallisuusverkko
-- WHERE tie = :tie
--   AND ST_Intersects(geometria, :saatu-geometria);

