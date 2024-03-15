-- name: tuhoa-tieturvallisuusverkko!
DELETE FROM tieturvallisuusverkko;

-- name: lisaa-tieturvallisuusverkko!
INSERT INTO tieturvallisuusverkko (aosa, tie, let, losa, aet, tenluokka,
                                   geometria, pituus, luotu, vaylan_luonne, paavaylan_luonne)
VALUES (:aosa, :tie, :let, :losa, :aet, :tenluokka,
        ST_GeomFromText(:the_geom) :: GEOMETRY, :pituus, NOW(), :vaylan_luo, :paavaylan_);

-- name: hae-tieturvallisuusgeometriat
SELECT tie, aosa, losa, aet, let, geometria FROM tieturvallisuusverkko;

-- name: hae-geometriaa-leikkaavat-tieturvallisuusgeometriat-tienumerolla
SELECT ST_Intersection(:saatugeometria::GEOMETRY, ulompi.u) AS leikkaus
FROM (SELECT ST_Union(sisempi.geom) u
      FROM (SELECT geometria::geometry geom
            FROM tieturvallisuusverkko
            WHERE tie = :tie
              AND ST_Intersects(:saatugeometria::GEOMETRY, geometria::geometry)
           ) AS sisempi
     ) AS ulompi;

-- name: hae-urakan-tieturvallisuusverkko-kartalle
SELECT
    ST_INTERSECTION(geometria, u.alue) AS sijainti
FROM tieturvallisuusverkko
         LEFT JOIN urakka u ON u.id = :urakka AND u.tyyppi IN ('teiden-hoito', 'hoito')
WHERE
    ST_Intersects(geometria, (SELECT alue FROM urakka WHERE id = :urakka));
